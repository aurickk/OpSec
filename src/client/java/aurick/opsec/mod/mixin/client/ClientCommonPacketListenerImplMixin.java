package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.detection.LocalUrlDetector;
import aurick.opsec.mod.detection.TrackPackDetector;
import aurick.opsec.mod.util.ServerAddressTracker;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

 /**
 * Mixin to intercept resource pack requests.
 * 
 * LOCAL URL BLOCKING:
 * Instead of intercepting packets and sending manual FAILED_DOWNLOAD responses (which causes
 * texture reloads), we redirect local URLs to a guaranteed-to-fail address (0.0.0.0:0).
 * This makes Minecraft's normal download flow fail at the HTTP level, exactly like vanilla
 * behavior when a local service doesn't exist. No texture reloads occur because the failure
 * happens at the same point in the code path as natural failures.
 * 
 * PORT SCAN DETECTION:
 * Detection always occurs for local URL probes, regardless of protection settings.
 * This ensures full visibility into scanning attempts even when protection is off.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    
    /**
     * URL that is guaranteed to fail immediately at the HTTP level.
     * 0.0.0.0:0 is an invalid address that will cause immediate connection failure,
     * matching vanilla behavior when a local service doesn't exist.
     */
    @Unique
    private static final String FAIL_URL = "http://0.0.0.0:0/opsec-blocked";
    
    /**
     * Count of blocked local URLs for logging.
     */
    @Unique
    private static int opsec$localUrlBlockCount = 0;
    
    /**
     * Redirect the URL from the packet for local URLs (only when protection is enabled).
     * This makes Minecraft try to download from an invalid address, causing a natural
     * HTTP failure without texture reloads.
     */
    @Redirect(
        method = "handleResourcePackPush",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/ClientboundResourcePackPushPacket;url()Ljava/lang/String;")
    )
    private String opsec$redirectLocalUrl(ClientboundResourcePackPushPacket packet) {
        String originalUrl = packet.url();
        
        if (OpsecConfig.getInstance().shouldBlockLocalPackUrls() && ServerAddressTracker.shouldBlockLocalUrl(originalUrl)) {
            opsec$localUrlBlockCount++;
            String reason = LocalUrlDetector.getBlockReason(originalUrl);
            
            Opsec.LOGGER.info("[OpSec] Blocking local port scan #{}: ORIGINAL URL = \"{}\" (reason: {})", 
                opsec$localUrlBlockCount, originalUrl, reason);
            
            return FAIL_URL;
        }
        
        return originalUrl;
    }
    
    /**
     * Handle resource pack pushes:
     * - ALWAYS detect local URL probes (port scans) - even if protection is off
     * - Track packs for fingerprinting detection
     * - Optionally fake accept packs
     */
    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void onResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        String url = packet.url();
        boolean isLocalUrlProbe = ServerAddressTracker.shouldBlockLocalUrl(url);
        boolean protectionEnabled = OpsecConfig.getInstance().shouldBlockLocalPackUrls();
        
        if (isLocalUrlProbe) {
            PrivacyLogger.alertLocalPortScanDetected(url, protectionEnabled);
            TrackPackDetector.recordRequest(url, packet.hash());
            
            if (protectionEnabled) {
                return;
            }
        }
        
        boolean suspicious = TrackPackDetector.recordRequest(url, packet.hash());
        
        if (suspicious && TrackPackDetector.consumeNotifySuspiciousOnce()) {
            PrivacyLogger.alertTrackPackDetected(url);
        }
        
        if (TrackPackDetector.isFingerprinting() && TrackPackDetector.consumeNotifyPatternOnce()) {
            PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER,
                "Resource pack fingerprinting pattern detected!");
            PrivacyLogger.toast(PrivacyLogger.AlertType.DANGER, "Resource Pack Fingerprinting Detected");
        }
    }
    
}


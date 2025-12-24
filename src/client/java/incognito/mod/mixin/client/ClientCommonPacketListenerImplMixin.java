package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.detection.LocalUrlDetector;
import incognito.mod.detection.TrackPackDetector;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin to intercept resource pack requests.
 * 
 * LOCAL URL SPOOFING:
 * Instead of intercepting packets and sending manual FAILED_DOWNLOAD responses (which causes
 * texture reloads), we redirect local URLs to a guaranteed-to-fail address (0.0.0.0:0).
 * This makes Minecraft's normal download flow fail at the HTTP level, exactly like vanilla
 * behavior when a local service doesn't exist. No texture reloads occur because the failure
 * happens at the same point in the code path as natural failures.
 * 
 * FAKE PACK ACCEPT:
 * When enabled, we send ACCEPTED/DOWNLOADED/SUCCESSFULLY_LOADED without actually downloading
 * the pack. This prevents resource pack fingerprinting while allowing connection to servers
 * that require packs.
 * 
 * @see <a href="https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/misc/ServerSpoof.java">Meteor Client ServerSpoof</a>
 * @see <a href="https://github.com/NikOverflow/ExploitPreventer">ExploitPreventer</a>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    
    @Shadow @Final protected Connection connection;
    
    /**
     * URL that is guaranteed to fail immediately at the HTTP level.
     * 0.0.0.0:0 is an invalid address that will cause immediate connection failure,
     * matching vanilla behavior when a local service doesn't exist.
     */
    @Unique
    private static final String FAIL_URL = "http://0.0.0.0:0/incognito-blocked";
    
    /**
     * Count of spoofed local URLs for logging.
     */
    @Unique
    private static int incognito$localUrlSpoofCount = 0;
    
    /**
     * Redirect the URL from the packet for local URLs.
     * This makes Minecraft try to download from an invalid address, causing a natural
     * HTTP failure without texture reloads.
     */
    @Redirect(
        method = "handleResourcePackPush",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/ClientboundResourcePackPushPacket;url()Ljava/lang/String;")
    )
    private String incognito$redirectLocalUrl(ClientboundResourcePackPushPacket packet) {
        String originalUrl = packet.url();
        
        // Check if this is a local URL that should be spoofed
        if (IncognitoConfig.getInstance().shouldSpoofLocalPackUrls() && LocalUrlDetector.isLocalUrl(originalUrl)) {
            incognito$localUrlSpoofCount++;
            String reason = LocalUrlDetector.getBlockReason(originalUrl);
            
            // Log the EXACT original URL from the server packet (before any parsing)
            Incognito.LOGGER.info("[Incognito] Blocking local port scan #{}: ORIGINAL URL = \"{}\" (reason: {})", 
                incognito$localUrlSpoofCount, originalUrl, reason);
            
            // Queue alert for summary (shown after player finishes loading)
            PrivacyLogger.alertLocalPackBlocked(originalUrl);
            
            // Return the fail URL - Minecraft will try to download from this and fail naturally
            return FAIL_URL;
        }
        
        return originalUrl;
    }
    
    /**
     * Handle non-local URL pack pushes for detection and fake accept.
     */
    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void onResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        String url = packet.url();
        UUID packId = packet.id();
        
        // Skip local URL handling here - it's handled by the @Redirect above
        // Local URLs will be redirected to FAIL_URL and fail naturally
        if (IncognitoConfig.getInstance().shouldSpoofLocalPackUrls() && LocalUrlDetector.isLocalUrl(url)) {
            // Don't cancel - let the packet flow through with the redirected URL
            // Record for detection purposes
            TrackPackDetector.recordRequest(url, packet.hash());
            return;
        }
        
        // Record the request for detection/logging
        boolean suspicious = TrackPackDetector.recordRequest(url, packet.hash());
        
        // Alert if suspicious (for logging purposes)
        if (suspicious && TrackPackDetector.consumeNotifySuspiciousOnce()) {
            PrivacyLogger.alertTrackPackDetected(url);
        }
        
        // Check for fingerprinting pattern
        if (TrackPackDetector.isFingerprinting() && TrackPackDetector.consumeNotifyPatternOnce()) {
            PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER,
                "Resource pack fingerprinting pattern detected!");
        }
        
        // If fake pack accept is enabled, intercept ALL resource packs
        if (IncognitoConfig.getInstance().shouldFakePackAccept()) {
            // Fake accept the resource pack
            fakeAcceptResourcePack(packId, url);
            ci.cancel();
        }
    }
    
    /**
     * Fake accepting a resource pack by sending the full sequence of responses:
     * ACCEPTED -> DOWNLOADED -> SUCCESSFULLY_LOADED
     * 
     * This makes the server think we accepted and loaded the pack without actually downloading it.
     * Based on Meteor Client's ServerSpoof module.
     */
    @Unique
    private void fakeAcceptResourcePack(UUID packId, String url) {
        try {
            // Step 1: Tell server we accepted the pack
            connection.send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.ACCEPTED));
            
            // Step 2: Tell server we downloaded the pack
            connection.send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.DOWNLOADED));
            
            // Step 3: Tell server we successfully loaded the pack
            connection.send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            
            Incognito.LOGGER.info("[Incognito] Fake accepted resource pack: {}", url);
            
            if (TrackPackDetector.consumeNotifyBlockedOnce()) {
                PrivacyLogger.alertTrackPackBlocked(url);
            }
        } catch (Exception e) {
            Incognito.LOGGER.error("[Incognito] Failed to fake accept resource pack: {}", e.getMessage());
        }
    }
    
}

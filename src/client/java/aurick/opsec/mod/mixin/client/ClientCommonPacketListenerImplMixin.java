package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.TrackPackDetector;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept resource pack requests for fingerprinting detection.
 *
 * <p>PORT SCAN DETECTION:
 * Detection of local address probes now happens at the HTTP level in HttpUtilMixin,
 * which blocks connections to local/private addresses during redirect following.
 * This mixin focuses solely on fingerprinting pattern detection via TrackPackDetector.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {

    /**
     * Handle resource pack pushes for fingerprinting detection.
     * Tracks packs and alerts on suspicious or fingerprinting patterns.
     */
    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void onResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        String url = packet.url();

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

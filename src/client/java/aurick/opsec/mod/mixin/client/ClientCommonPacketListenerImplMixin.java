package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.TrackPackDetector;
import aurick.opsec.mod.protection.PackStripHandler;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts server resource-pack push/pop to fire fingerprint detection and notify
 * {@link PackStripHandler}. Never cancels — vanilla's download/status path runs as
 * usual; the actual stripping happens in {@code DownloadedPackSourceMixin}.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
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

        PackStripHandler.onPackPush(packet.id(), url, packet.required());
    }

    @Inject(method = "handleResourcePackPop", at = @At("HEAD"))
    private void opsec$onResourcePackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
        PackStripHandler.onPop(packet.id());
    }
}

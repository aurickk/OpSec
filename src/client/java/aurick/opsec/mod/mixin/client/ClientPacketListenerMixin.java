package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.protection.PackStripHandler;
import aurick.opsec.mod.protection.PackStripOverlay;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks server connection events for OpSec.
 * Handles secure chat enforcement detection with on-the-fly approach:
 *
 * 1. Login: enforcesSecureChat flag enables signing immediately.
 * 2. sendChat: WrapOperation on Encoder.pack() prevents signing chain advancement
 *    when not signing — keeps the chain clean for later enabling.
 * 3. System chat: Detects signing-related errors, suppresses the error, enables signing,
 *    and automatically resends the message signed — fully transparent to the user.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Unique
    private static volatile ScheduledExecutorService opsec$scheduler;

    @Unique
    private static volatile ScheduledFuture<?> opsec$pendingTask;

    /** Stores the last message sent unsigned, for automatic resend if the server rejects it. */
    @Unique
    private String opsec$lastUnsignedMessage;

    /** True only while waiting for a server response after sending an unsigned message. */
    @Unique
    private boolean opsec$awaitingSigningResponse;

    @Unique
    private static final Set<String> SIGNING_ERROR_KEYS = Set.of(
            "multiplayer.disconnect.unsigned_chat",
            "chat.disabled.missingProfileKey",
            "chat.disabled.profile",
            "chat.disabled.chain_broken",
            "chat.disabled.expiredProfileKey",
            "chat.disabled.invalid_signature",
            "multiplayer.disconnect.chat_validation_failed"
    );

    @Unique
    private static synchronized ScheduledExecutorService opsec$getScheduler() {
        if (opsec$scheduler == null || opsec$scheduler.isShutdown()) {
            opsec$scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OpSec-Scheduler");
            t.setDaemon(true);
            return t;
        });
        }
        return opsec$scheduler;
    }

    @Unique
    private static synchronized void opsec$shutdownScheduler() {
        if (opsec$pendingTask != null) {
            opsec$pendingTask.cancel(false);
            opsec$pendingTask = null;
        }
        if (opsec$scheduler != null && !opsec$scheduler.isShutdown()) {
            opsec$scheduler.shutdownNow();
            opsec$scheduler = null;
        }
    }

    /**
     * Wrap sub-packet handle() calls inside handleBundlePacket so each sub-packet
     * gets its own packet name context. Without this, all sub-packets inherit the
     * bundle's name since they bypass PacketProcessor$ListenerAndPacket.
     */
    @WrapOperation(
        method = "handleBundlePacket",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private <T extends PacketListener> void opsec$wrapBundleSubPacketHandle(
            Packet<T> instance, T listener, Operation<Void> original) {
        TranslationProtectionHandler.clearDedup();
        PacketContext.setPacketName(instance);
        original.call(instance, listener);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener)(Object)this;
        String serverAddress = listener.getConnection().getRemoteAddress().toString();

        if (serverAddress.startsWith("/")) {
            serverAddress = serverAddress.substring(1);
        }
        int colonIndex = serverAddress.lastIndexOf(':');
        if (colonIndex > 0) {
            serverAddress = serverAddress.substring(0, colonIndex);
        }

        OpsecConfig.getInstance().setCurrentServer(serverAddress);

        // Login packet flag: enable signing immediately if server declares it
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (packet.enforcesSecureChat()) {
            Opsec.LOGGER.debug("[OpSec] Server enforces secure chat");

            if (settings.isOnDemand()) {
                settings.setTempSign(true);

                if (!settings.isSigningToastShown()) {
                    settings.setSigningToastShown(true);
                    PrivacyLogger.alertSecureChatRequired(serverAddress);
                }
            }
        }

        // Schedule port scan summary after 2 seconds
        opsec$pendingTask = opsec$getScheduler().schedule(() -> {
            Minecraft.getInstance().execute(PrivacyLogger::showPortScanSummary);
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * Capture the message text when sending unsigned, so we can resend it signed
     * if the server rejects it.
     */
    @Inject(method = "sendChat", at = @At("HEAD"))
    private void opsec$captureUnsignedMessage(String message, CallbackInfo ci) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            opsec$lastUnsignedMessage = message;
            opsec$awaitingSigningResponse = true;
        } else {
            opsec$lastUnsignedMessage = null;
            opsec$awaitingSigningResponse = false;
        }
    }

    /**
     * Wrap the signing encoder call in sendChat() to prevent chain advancement when not signing.
     * Returns null instead of calling the encoder, keeping the chain clean.
     */
    @WrapOperation(
            method = "sendChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/SignedMessageChain$Encoder;pack(Lnet/minecraft/network/chat/SignedMessageBody;)Lnet/minecraft/network/chat/MessageSignature;")
    )
    private MessageSignature opsec$skipSigningEncoder(SignedMessageChain.Encoder encoder, SignedMessageBody body, Operation<MessageSignature> original) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            Opsec.LOGGER.debug("[OpSec] Skipping message signing (chain preserved)");
            return null;
        }
        return original.call(encoder, body);
    }

    /**
     * Detect signing-related error messages from the server.
     * When detected: suppress the error, enable signing, and automatically resend
     * the original message — fully transparent to the user.
     * The signing chain is clean because the WrapOperation above prevented advancement.
     */
    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void opsec$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!opsec$awaitingSigningResponse) return;

        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isOnDemand() || settings.isTempSign()) return;

        Component content = packet.content();
        if (content == null) return;

        if (!opsec$isSigningErrorMessage(content)) return;

        opsec$awaitingSigningResponse = false;

        // Enable signing
        Opsec.LOGGER.info("[OpSec] Detected signing error - enabling signing and resending");
        settings.setTempSign(true);

        // Suppress the error message from showing in chat
        ci.cancel();

        // Resend the captured message, now signed
        String message = opsec$lastUnsignedMessage;
        opsec$lastUnsignedMessage = null;
        if (message != null) {
            ((ClientPacketListener) (Object) this).sendChat(message);
        }

        if (!settings.isSigningToastShown()) {
            settings.setSigningToastShown(true);
            PrivacyLogger.alertSecureChatRequired(OpsecConfig.getInstance().getCurrentServer());
        }
    }

    @Unique
    private static boolean opsec$isSigningErrorMessage(Component component) {
        // Match only on TranslatableContents with a known signing-error key.
        // The previous keyword-substring fallback let a hostile server force the
        // client to resend its last unsigned chat as signed by sending any system
        // chat containing "secure chat" / "unsigned_chat" / "invalid signature"
        // within the awaitingSigningResponse window — defeating ON_DEMAND mode.
        return opsec$hasSigningTranslationKey(component);
    }

    @Unique
    private static boolean opsec$hasSigningTranslationKey(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (SIGNING_ERROR_KEYS.contains(translatable.getKey())) {
                return true;
            }
        }

        for (Component sibling : component.getSiblings()) {
            if (opsec$hasSigningTranslationKey(sibling)) {
                return true;
            }
        }

        return false;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        opsec$shutdownScheduler();
        PrivacyLogger.resetPortScanTracking();
        PrivacyLogger.clearCooldowns();
        OpsecConfig.getInstance().setCurrentServer(null);
        OpsecConfig.getInstance().getSettings().resetSessionState();
        opsec$lastUnsignedMessage = null;
        opsec$awaitingSigningResponse = false;
        PackStripHandler.clearAll();
        PackStripOverlay.clearQueue();
    }
}

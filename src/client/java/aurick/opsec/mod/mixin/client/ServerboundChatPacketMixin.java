package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

/**
 * Mixin to strip signatures from chat packets when in ON_DEMAND mode
 * and the server doesn't require signing.
 * 
 * This allows the key to always be sent (so we CAN sign when required),
 * but signatures are stripped when not needed for privacy.
 */
@Mixin(ServerboundChatPacket.class)
public class ServerboundChatPacketMixin {
    
    @Final
    @Nullable
    @Mutable
    @Shadow
    private MessageSignature signature;
    
    /**
     * Strip signature from packet on creation when ON_DEMAND and server doesn't require signing.
     */
    @Inject(method = "<init>(Ljava/lang/String;Ljava/time/Instant;JLnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/network/chat/LastSeenMessages$Update;)V", at = @At("TAIL"))
    private void opsec$stripSignatureOnInit(String message, Instant timeStamp, long salt, 
            MessageSignature signature, LastSeenMessages.Update lastSeenMessages, CallbackInfo ci) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            Opsec.LOGGER.debug("[OpSec] ON_DEMAND mode - stripping chat signature");
            this.signature = null;
        }
    }
    
    /**
     * Return null signature when ON_DEMAND and server doesn't require signing.
     */
    @Inject(method = "signature", at = @At("HEAD"), cancellable = true)
    private void opsec$stripSignatureOnGet(CallbackInfoReturnable<MessageSignature> info) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            Opsec.LOGGER.debug("[OpSec] ON_DEMAND mode - returning null signature");
            info.setReturnValue(null);
        }
    }
}


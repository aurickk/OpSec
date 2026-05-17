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
 * Strips signatures from outbound chat packets when the user has signing
 * mode set to OFF.
 */
@Mixin(ServerboundChatPacket.class)
public class ServerboundChatPacketMixin {
    
    @Final
    @Nullable
    @Mutable
    @Shadow
    private MessageSignature signature;
    
    /**
     * Strip signature from packet on construction when signing is OFF.
     */
    @Inject(method = "<init>(Ljava/lang/String;Ljava/time/Instant;JLnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/network/chat/LastSeenMessages$Update;)V", at = @At("TAIL"))
    private void opsec$stripSignatureOnInit(String message, Instant timeStamp, long salt, 
            MessageSignature signature, LastSeenMessages.Update lastSeenMessages, CallbackInfo ci) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            Opsec.LOGGER.debug("[OpSec] signing OFF —stripping chat signature");
            this.signature = null;
        }
    }
    
    /**
     * Return null from the signature accessor when signing is OFF.
     */
    @Inject(method = "signature", at = @At("HEAD"), cancellable = true)
    private void opsec$stripSignatureOnGet(CallbackInfoReturnable<MessageSignature> info) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            Opsec.LOGGER.debug("[OpSec] signing OFF —returning null signature");
            info.setReturnValue(null);
        }
    }
}


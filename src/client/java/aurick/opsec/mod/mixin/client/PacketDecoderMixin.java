package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.detection.PacketContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps packet deserialization to set the PacketContext ThreadLocal.
 * Components constructed during StreamCodec.decode() will read this flag
 * in their constructors and tag themselves as packet-origin.
 */
@Mixin(PacketDecoder.class)
public class PacketDecoderMixin {

    @WrapOperation(
        method = "decode",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object opsec$wrapDecode(StreamCodec instance, Object buffer, Operation<Object> original) {
        PacketContext.setProcessingPacket(true);
        try {
            return original.call(instance, buffer);
        } finally {
            PacketContext.setProcessingPacket(false);
        }
    }
}

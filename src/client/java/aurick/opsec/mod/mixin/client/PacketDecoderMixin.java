package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.detection.PacketContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
            Object packet = original.call(instance, buffer);
            PacketContext.setPacketName(packet);
            return packet;
        } finally {
            PacketContext.setProcessingPacket(false);
        }
    }
}

package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.detection.PacketContext;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps packet handler execution to set the PacketContext ThreadLocal.
 * Components constructed during Packet.handle() (lazy deserialization,
 * e.g., sign/anvil screen constructors) will read this flag in their
 * constructors and tag themselves as packet-origin.
 */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public class PacketProcessorMixin {

    @WrapOperation(
        method = "handle",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private <T extends PacketListener> void opsec$wrapHandle(Packet<?> instance, T listener,
            Operation<Void> original) {
        PacketContext.setProcessingPacket(true);
        try {
            original.call(instance, listener);
        } finally {
            PacketContext.setProcessingPacket(false);
        }
    }
}

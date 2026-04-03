package aurick.opsec.mod.mixin.client;

//? if >=1.21.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public class PacketProcessorMixin {

    @WrapOperation(
        method = "handle",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private <T extends PacketListener> void opsec$wrapHandle(Packet<?> instance, T listener,
            Operation<Void> original) {
        TranslationProtectionHandler.clearDedup();
        PacketContext.setPacketName(instance);
        PacketContext.setProcessingPacket(true);
        try {
            original.call(instance, listener);
        } finally {
            PacketContext.setProcessingPacket(false);
        }
    }
}
//?} else {
/*
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.network.protocol.PacketUtils;

@Mixin(PacketUtils.class)
public class PacketProcessorMixin {
}
*///?}

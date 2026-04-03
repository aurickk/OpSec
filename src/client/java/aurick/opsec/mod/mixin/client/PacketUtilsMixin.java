package aurick.opsec.mod.mixin.client;

//? if <1.21.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketUtils.class)
public class PacketUtilsMixin {

    @WrapOperation(
        method = "method_11072",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private static <T extends PacketListener> void opsec$wrapHandleOnGameThread(
            Packet<?> instance, T listener, Operation<Void> original) {
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

@Mixin(net.minecraft.network.protocol.PacketUtils.class)
public class PacketUtilsMixin {
}
*///?}

package aurick.opsec.mod.mixin.client;

//? if >=1.20.2 {
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.protection.ClientSpoofer;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to log when configuration finishes.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ChannelRegistrationMixin {

    @Inject(method = "handleConfigurationFinished", at = @At("HEAD"))
    private void onConfigurationFinished(ClientboundFinishConfigurationPacket packet, CallbackInfo ci) {
        OpsecConfig config = OpsecConfig.getInstance();

        if (!config.shouldSpoofChannels()) {
            return;
        }

        String brand = config.getEffectiveBrand();
        Opsec.LOGGER.debug("[OpSec] Configuration finished - advertising brand '{}'", brand);

        if (ClientSpoofer.isVanillaMode()) {
            Opsec.LOGGER.debug("[OpSec] VANILLA MODE - all custom payloads blocked");
        } else if (ClientSpoofer.isFabricMode()) {
            Opsec.LOGGER.debug("[OpSec] FABRIC MODE - non-whitelisted mod channels blocked");
        }
    }
}
//?} else {
/*
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;

// 1.20.1: configuration phase doesn't exist (introduced 1.20.2). Channel
// registration on 1.20.1 happens via ClientboundCustomPayloadPacket during
// the play-join sequence, intercepted by ClientConnectionMixin.send() on the
// outgoing side. This mixin is purely a debug-log entry point so an empty
// no-op else is acceptable degradation.
@Mixin(PacketUtils.class)
public abstract class ChannelRegistrationMixin {
}
*///?}

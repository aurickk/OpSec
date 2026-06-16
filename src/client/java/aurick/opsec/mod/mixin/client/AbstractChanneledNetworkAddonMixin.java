package aurick.opsec.mod.mixin.client;

//? if >=1.20.5 {
import aurick.opsec.mod.protection.ClientSpoofer;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientConfigurationNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inbound dispatch stage of the channel-fingerprint defense; see {@link PayloadTypeRegistryImplMixin}
 * (decode stage). Once a non-whitelisted payload decodes as a vanilla DiscardedPayload, Fabric still
 * has a registered receiver for the channel and dispatches it, casting to the expected type and
 * throwing a ClassCastException ("server sent an invalid packet"). Making {@code handle} return
 * false ("no handler") for those channels lets Fabric fall through to vanilla's silent-ignore path.
 *
 * <p>Scoped to the client-receiving addons ({@code instanceof}): the base class is also extended by
 * the integrated server's addons, whose C2S dispatch must be left alone. Gated {@code >=1.20.5}
 * like {@link PayloadTypeRegistryImplMixin}.
 */
@Mixin(AbstractChanneledNetworkAddon.class)
public class AbstractChanneledNetworkAddonMixin {

    @Inject(method = "handle(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)Z",
            at = @At("HEAD"), cancellable = true)
    private void opsec$dropInboundModChannel(CustomPacketPayload payload, CallbackInfoReturnable<Boolean> cir) {
        // Only the client's receiving addons; never the integrated server's C2S dispatch.
        Object self = this;
        if (!(self instanceof ClientConfigurationNetworkAddon) && !(self instanceof ClientPlayNetworkAddon)) {
            return;
        }

        //? if >=1.21.11 {
        /*Identifier id = payload.type().id();*/
        //?} else {
        ResourceLocation id = payload.type().id();
        //?}

        // Returning false means "no handler" -> Fabric falls through to vanilla's silent ignore.
        if (ClientSpoofer.shouldDropInboundChannel(id)) {
            cir.setReturnValue(false);
        }
    }
}
//?} else {
/*public class AbstractChanneledNetworkAddonMixin {}
*///?}

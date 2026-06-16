package aurick.opsec.mod.mixin.client;

//? if >=1.20.5 {
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.protection.ClientSpoofer;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.protocol.PacketFlow;
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
 * Inbound half of the channel-fingerprint defense (decode stage); see also
 * {@link AbstractChanneledNetworkAddonMixin} (dispatch stage) and the outbound
 * {@link ClientConnectionMixin}.
 *
 * <p>Fabric resolves the codec for an inbound custom payload via this registry; when the lookup
 * returns null it falls back to vanilla's DiscardedPayload (silently ignored). Returning null for
 * non-whitelisted channels while spoofing makes us decode like vanilla instead of running Fabric's
 * strict codec (which throws a DecoderException on a malformed probe, e.g. empty
 * {@code fabric:registry/sync}, betraying that Fabric is present).
 *
 * <p>Gated {@code >=1.20.5}: the typed payload system doesn't exist on earlier MC, where unknown
 * channels are already ignored.
 */
@Mixin(PayloadTypeRegistryImpl.class)
public class PayloadTypeRegistryImplMixin {

    @Inject(method =
        //? if >=1.21.11 {
        /*"get(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;"*/
        //?} else {
        "get(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;"
        //?}
        , at = @At("HEAD"), cancellable = true)
    private void opsec$gateInboundChannel(
            //? if >=1.21.11 {
            /*Identifier id,*/
            //?} else {
            ResourceLocation id,
            //?}
            CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> cir) {
        // Act only during inbound decode. The same get(id) is called by Fabric's
        // assertPayloadType when mods register receivers at init; nulling it there crashes
        // registration. PacketContext is true only inside StreamCodec.decode (PacketDecoderMixin).
        if (!PacketContext.isProcessingPacket()) return;

        // Inbound (client-decoded) registries only; never touch outbound (C2S) encoding.
        PayloadTypeRegistryImpl<?> self = (PayloadTypeRegistryImpl<?>) (Object) this;
        //? if >=26.1 {
        /*if (self.getFlow() != PacketFlow.CLIENTBOUND) return;*/
        //?} else {
        if (self.getSide() != PacketFlow.CLIENTBOUND) return;
        //?}

        // Returning null falls back to vanilla's DiscardedPayload (silent ignore).
        if (ClientSpoofer.shouldDropInboundChannel(id)) {
            cir.setReturnValue(null);
        }
    }
}
//?} else {
/*public class PayloadTypeRegistryImplMixin {}
*///?}

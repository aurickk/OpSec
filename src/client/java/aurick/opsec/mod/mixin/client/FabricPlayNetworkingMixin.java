package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.protection.ChannelFilterHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Filters Fabric's advertised play channel list when spoofing.
 * Also tracks registered channels for whitelist support.
 */
@Mixin(ClientPlayNetworking.class)
public class FabricPlayNetworkingMixin {
    
    @Inject(method = "getGlobalReceivers", at = @At("RETURN"), cancellable = true, remap = false)
    //? if >=1.21.11 {
    /*private static void opsec$filterGlobalReceivers(CallbackInfoReturnable<Set<Identifier>> cir) {
        Set<Identifier> original = cir.getReturnValue();
        ChannelFilterHelper.trackChannels(original);
        Set<Identifier> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getGlobalReceivers", ChannelFilterHelper.playLogged);*/
    //?} else {
    private static void opsec$filterGlobalReceivers(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> original = cir.getReturnValue();
        ChannelFilterHelper.trackChannels(original);
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getGlobalReceivers", ChannelFilterHelper.playLogged);
    //?}
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
    
    @Inject(method = "getReceived", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    //? if >=1.21.11 {
    /*private static void opsec$filterReceived(CallbackInfoReturnable<Set<Identifier>> cir) {
        Set<Identifier> original = cir.getReturnValue();
        ChannelFilterHelper.trackChannels(original);
        Set<Identifier> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getReceived", ChannelFilterHelper.playLogged);*/
    //?} else {
    private static void opsec$filterReceived(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> original = cir.getReturnValue();
        ChannelFilterHelper.trackChannels(original);
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getReceived", ChannelFilterHelper.playLogged);
    //?}
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
    
    @Inject(method = "getSendable", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    //? if >=1.21.11 {
    /*private static void opsec$filterSendable(CallbackInfoReturnable<Set<Identifier>> cir) {
        Set<Identifier> original = cir.getReturnValue();
        ChannelFilterHelper.trackChannels(original);
        Set<Identifier> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getSendable", ChannelFilterHelper.playLogged);*/
    //?} else {
    private static void opsec$filterSendable(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> original = cir.getReturnValue();
        ChannelFilterHelper.trackChannels(original);
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getSendable", ChannelFilterHelper.playLogged);
    //?}
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
}

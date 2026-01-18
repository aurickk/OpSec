package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.protection.ChannelFilterHelper;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Filters Fabric's advertised play channel list when spoofing.
 * Also tracks registered channels for whitelist support.
 */
@Mixin(ClientPlayNetworking.class)
public class FabricPlayNetworkingMixin {
    
    @Unique
    private static final AtomicBoolean opsec$logged = new AtomicBoolean(false);
    
    @Inject(method = "getGlobalReceivers", at = @At("RETURN"), cancellable = true, remap = false)
    //? if >=1.21.11 {
    /*private static void opsec$filterGlobalReceivers(CallbackInfoReturnable<Set<Identifier>> cir) {
        Set<Identifier> original = cir.getReturnValue();
        opsec$trackChannels(original);
        Set<Identifier> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getGlobalReceivers", opsec$logged);*/
    //?} else {
    private static void opsec$filterGlobalReceivers(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> original = cir.getReturnValue();
        opsec$trackChannels(original);
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            original, "play.getGlobalReceivers", opsec$logged);
    //?}
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
    
    @Inject(method = "getReceived", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    //? if >=1.21.11 {
    /*private static void opsec$filterReceived(CallbackInfoReturnable<Set<Identifier>> cir) {
        Set<Identifier> filtered = ChannelFilterHelper.filterChannels(
            cir.getReturnValue(), "play.getReceived", opsec$logged);*/
    //?} else {
    private static void opsec$filterReceived(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            cir.getReturnValue(), "play.getReceived", opsec$logged);
    //?}
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
    
    @Inject(method = "getSendable", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    //? if >=1.21.11 {
    /*private static void opsec$filterSendable(CallbackInfoReturnable<Set<Identifier>> cir) {
        Set<Identifier> filtered = ChannelFilterHelper.filterChannels(
            cir.getReturnValue(), "play.getSendable", opsec$logged);*/
    //?} else {
    private static void opsec$filterSendable(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            cir.getReturnValue(), "play.getSendable", opsec$logged);
    //?}
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
    
    @Unique
    //? if >=1.21.11 {
    /*private static void opsec$trackChannels(Set<Identifier> channels) {
        if (channels == null || channels.isEmpty()) return;
        for (Identifier channel : channels) {*/
    //?} else {
    private static void opsec$trackChannels(Set<ResourceLocation> channels) {
        if (channels == null || channels.isEmpty()) return;
        for (ResourceLocation channel : channels) {
    //?}
            String modId = channel.getNamespace();
            if ("minecraft".equals(modId) || "fabric".equals(modId) || 
                modId.startsWith("fabric-") || "c".equals(modId)) {
                continue;
            }
            ModRegistry.recordChannel(modId, channel);
        }
    }
}

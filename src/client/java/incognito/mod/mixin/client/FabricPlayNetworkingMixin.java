package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.protection.ClientSpoofer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Filters Fabric's advertised play channel list when spoofing.
 * Intercepts multiple methods that could expose channel information.
 */
@Mixin(ClientPlayNetworking.class)
public class FabricPlayNetworkingMixin {
    
    @Unique
    private static boolean incognito$logged = false;
    
    /**
     * Filter getGlobalReceivers - returns all globally registered receivers
     */
    @Inject(method = "getGlobalReceivers", at = @At("RETURN"), cancellable = true, remap = false)
    private static void incognito$filterGlobalReceivers(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        filterChannelSet(cir, "getGlobalReceivers");
    }
    
    /**
     * Filter getReceived - returns channels the server can send to this client
     */
    @Inject(method = "getReceived", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void incognito$filterReceived(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        filterChannelSet(cir, "getReceived");
    }
    
    /**
     * Filter getSendable - returns channels this client can send to the server
     */
    @Inject(method = "getSendable", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void incognito$filterSendable(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        filterChannelSet(cir, "getSendable");
    }
    
    @Unique
    private static void filterChannelSet(CallbackInfoReturnable<Set<ResourceLocation>> cir, String methodName) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        // Only filter/lie about channels when channel spoofing is enabled.
        // Brand-only spoofing should not touch channels.
        if (!config.shouldSpoofBrand() || !config.shouldSpoofChannels()) {
            return;
        }
        
        Set<ResourceLocation> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        
        // VANILLA MODE: Return empty set
        if (ClientSpoofer.isVanillaMode()) {
            if (!incognito$logged) {
                incognito$logged = true;
                Incognito.LOGGER.info("[Incognito] VANILLA MODE - Returning empty channel set for {}", methodName);
            }
            cir.setReturnValue(Collections.emptySet());
            return;
        }
        
        // FABRIC MODE: Filter to only fabric:* channels
        if (ClientSpoofer.isFabricMode()) {
            Set<ResourceLocation> filtered = new HashSet<>();
            for (ResourceLocation id : original) {
                if (incognito$isAllowedFabricChannel(id)) {
                    filtered.add(id);
                } else {
                    Incognito.LOGGER.debug("[Incognito] FABRIC MODE - Filtering {} channel: {}", methodName, id);
                }
            }
            if (!incognito$logged) {
                incognito$logged = true;
                Incognito.LOGGER.info("[Incognito] FABRIC MODE - Filtering {} channels: {} -> {}", 
                    methodName, original.size(), filtered.size());
            }
            cir.setReturnValue(filtered);
        }
    }
    
    @Unique
    private static boolean incognito$isAllowedFabricChannel(ResourceLocation id) {
        String ns = id.getNamespace();
        // Allow minecraft (for game functionality)
        if ("minecraft".equals(ns)) {
            return true;
        }
        // Allow fabric namespaces
        return "fabric".equals(ns) || ns.startsWith("fabric-");
    }
}

package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.tracking.ModRegistry;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared helper for filtering channel sets in Fabric networking mixins.
 * Eliminates code duplication between FabricConfigNetworkingMixin and FabricPlayNetworkingMixin.
 */
public final class ChannelFilterHelper {
    
    private ChannelFilterHelper() {}
    
    /**
     * Filter a channel set based on current spoofing configuration.
     * 
     * @param original The original channel set
     * @param methodName The method name for logging
     * @param logged AtomicBoolean to track if we've already logged (for deduplication)
     * @return The filtered set, or null if no filtering should occur
     */
    //? if >=1.21.11 {
    /*public static Set<Identifier> filterChannels(Set<Identifier> original, 
                                                        String methodName, 
                                                        AtomicBoolean logged) {*/
    //?} else {
    public static Set<ResourceLocation> filterChannels(Set<ResourceLocation> original, 
                                                        String methodName, 
                                                        AtomicBoolean logged) {
    //?}
        OpsecConfig config = OpsecConfig.getInstance();
        
        // Only filter channels when channel spoofing is enabled.
        // Brand-only mode should not modify channels.
        if (!config.shouldSpoofBrand() || !config.shouldSpoofChannels()) {
            return null;
        }
        
        if (original == null || original.isEmpty()) {
            return null;
        }
        
        // VANILLA MODE: Return empty set
        if (ClientSpoofer.isVanillaMode()) {
            if (logged.compareAndSet(false, true)) {
                Opsec.LOGGER.debug("[OpSec] VANILLA MODE - Returning empty channel set for {}", methodName);
            }
            return Collections.emptySet();
        }
        
        // FABRIC MODE: Filter to only fabric:* channels
        if (ClientSpoofer.isFabricMode()) {
            //? if >=1.21.11 {
            /*Set<Identifier> filtered = new HashSet<>();
            for (Identifier id : original) {*/
            //?} else {
            Set<ResourceLocation> filtered = new HashSet<>();
            for (ResourceLocation id : original) {
            //?}
                if (isAllowedFabricChannel(id)) {
                    filtered.add(id);
                } else if (Opsec.LOGGER.isDebugEnabled()) {
                    Opsec.LOGGER.debug("[OpSec] FABRIC MODE - Filtering {} channel: {}", methodName, id);
                }
            }
            if (logged.compareAndSet(false, true)) {
                Opsec.LOGGER.debug("[OpSec] FABRIC MODE - Filtering {} channels: {} -> {}", 
                    methodName, original.size(), filtered.size());
            }
            return filtered;
        }
        
        return null;
    }
    
    /**
     * Check if a channel is allowed in Fabric mode.
     * Uses ModRegistry.isWhitelistedChannel which handles:
     * - minecraft:* channels
     * - fabric:* and fabric-*:* channels
     * - c:* channels
     * - Whitelisted mod channels
     */
    //? if >=1.21.11 {
    /*public static boolean isAllowedFabricChannel(Identifier id) {*/
    //?} else {
    public static boolean isAllowedFabricChannel(ResourceLocation id) {
    //?}
        return ModRegistry.isWhitelistedChannel(id);
    }
}

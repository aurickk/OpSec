package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.tracking.ModIdResolver;
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

    /** Tracks whether play channel filtering has been logged this session */
    public static final AtomicBoolean playLogged = new AtomicBoolean(false);

    /** Tracks whether config channel filtering has been logged this session */
    public static final AtomicBoolean configLogged = new AtomicBoolean(false);

    /**
     * Reset channel filter logging flags. Called on server reconnect
     * so debug logs appear for each new connection.
     */
    public static void resetLogging() {
        playLogged.set(false);
        configLogged.set(false);
    }

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
     * - Whitelisted mod channels (including fabric API modules via DEFAULT_FABRIC_MODS)
     */
    //? if >=1.21.11 {
    /*public static boolean isAllowedFabricChannel(Identifier id) {*/
    //?} else {
    public static boolean isAllowedFabricChannel(ResourceLocation id) {
    //?}
        return ModRegistry.isWhitelistedChannel(id);
    }

    /**
     * Record channels from a set into ModRegistry, skipping minecraft namespace channels.
     * Called from mixin injection points that intercept Fabric's channel advertisement.
     *
     * <p>Resolves channel namespaces to actual mod IDs before recording, so that
     * channels with non-modId namespaces (e.g., "jm" for JourneyMap) are correctly
     * attributed to their owning mod.</p>
     */
    //? if >=1.21.11 {
    /*public static void trackChannels(Set<Identifier> channels) {
        if (channels == null || channels.isEmpty()) return;
        for (Identifier channel : channels) {*/
    //?} else {
    public static void trackChannels(Set<ResourceLocation> channels) {
        if (channels == null || channels.isEmpty()) return;
        for (ResourceLocation channel : channels) {
    //?}
            String namespace = channel.getNamespace();
            if ("minecraft".equals(namespace) || "c".equals(namespace)) continue;

            // Try to resolve namespace to actual mod ID
            Set<String> resolvedModIds = ModRegistry.resolveModIdsForNamespace(namespace);
            if (!resolvedModIds.isEmpty()) {
                for (String modId : resolvedModIds) {
                    ModRegistry.recordChannel(modId, channel);
                }
            } else {
                // Namespace is not a known mod ID and has no cached mapping.
                // Try stack-trace resolution (works at mixin intercept time).
                String stackModId = ModIdResolver.getModIdFromStacktrace();
                if (stackModId != null && !stackModId.equals(namespace)) {
                    ModRegistry.recordNamespaceMapping(namespace, stackModId);
                    ModRegistry.recordChannel(stackModId, channel);
                } else {
                    // Fall back to namespace as mod ID (backwards compat)
                    ModRegistry.recordChannel(namespace, channel);
                }
            }
        }
    }
}

package aurick.opsec.mod.tracking;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.protection.ChannelFilterHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified registry for tracking mod information including translation keys,
 * keybinds, and network channels. This provides a single source of truth
 * for all mod-related tracking used by the whitelist system.
 */
public class ModRegistry {
    
    /** Registry of all tracked mods */
    private static final Map<String, ModInfo> registry = new ConcurrentHashMap<>();
    
    /** Vanilla translation keys (always whitelisted) */
    private static final Set<String> vanillaTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** Vanilla keybinds (always whitelisted) */
    private static final Set<String> vanillaKeybinds = ConcurrentHashMap.newKeySet();
    
    /** Server resource pack translation keys (whitelisted for vanilla resolution) */
    private static final Set<String> serverPackKeys = ConcurrentHashMap.newKeySet();
    
    /** All known translation keys for fast lookup */
    private static final Set<String> allKnownTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** All known keybinds for fast lookup */
    private static final Set<String> allKnownKeybinds = ConcurrentHashMap.newKeySet();
    
    /** Maps channel namespaces to their owning Fabric mod IDs (e.g., "jm" -> "journeymap") */
    private static final Map<String, Set<String>> namespaceToModIds = new ConcurrentHashMap<>();

    /** Reverse index: translation key -> mod ID for O(1) lookup (P1/A4/A11) */
    private static final Map<String, String> translationKeyToModId = new ConcurrentHashMap<>();

    /** Reverse index: keybind name -> mod ID for O(1) lookup (P1/A4/A11) */
    private static final Map<String, String> keybindToModId = new ConcurrentHashMap<>();

    /** Reverse index: channel -> mod ID for O(1) lookup (P7) */
    //? if >=1.21.11 {
    /*private static final Map<Identifier, String> channelToModId = new ConcurrentHashMap<>();*/
    //?} else {
    private static final Map<ResourceLocation, String> channelToModId = new ConcurrentHashMap<>();
    //?}

    /** Fabric API modules with production translation keys - auto-whitelisted in Fabric mode */
    public static final Set<String> DEFAULT_FABRIC_MODS = Set.of(
        "fabric",
        "fabric-resource-loader-v0",
        "fabric-resource-loader-v1",
        "fabric-item-group-api-v1",
        "fabric-creative-tab-api-v1",
        "fabric-registry-sync-v0",
        "fabric-convention-tags-v2",
        "fabric-data-attachment-api-v1",
        "fabric-screen-handler-api-v1"
    );

    private static volatile boolean initialized = false;
    
    private ModRegistry() {}
    
    /**
     * Information about a tracked mod.
     */
    public static class ModInfo {
        private final String modId;
        private final String displayName;
        private final Set<String> translationKeys = ConcurrentHashMap.newKeySet();
        private final Set<String> keybinds = ConcurrentHashMap.newKeySet();
        //? if >=1.21.11 {
        /*private final Set<Identifier> channels = ConcurrentHashMap.newKeySet();*/
        //?} else {
        private final Set<ResourceLocation> channels = ConcurrentHashMap.newKeySet();
        //?}
        
        public ModInfo(String modId, String displayName) {
            this.modId = modId;
            this.displayName = displayName;
        }
        
        public String getModId() { return modId; }
        public String getDisplayName() { return displayName; }
        public Set<String> getTranslationKeys() { return Collections.unmodifiableSet(translationKeys); }
        public Set<String> getKeybinds() { return Collections.unmodifiableSet(keybinds); }
        //? if >=1.21.11 {
        /*public Set<Identifier> getChannels() { return Collections.unmodifiableSet(channels); }*/
        //?} else {
        public Set<ResourceLocation> getChannels() { return Collections.unmodifiableSet(channels); }
        //?}
        
        public boolean hasTranslationKeys() { return !translationKeys.isEmpty(); }
        public boolean hasKeybinds() { return !keybinds.isEmpty(); }
        public boolean hasChannels() { return !channels.isEmpty(); }
        
        /**
         * Check if this mod has any trackable content (translation keys, channels, or keybinds).
         */
        public boolean hasTrackableContent() {
            return hasTranslationKeys() || hasChannels() || hasKeybinds();
        }
    }
    
    // ==================== MOD INFO MANAGEMENT ====================
    
    /**
     * Get or create ModInfo for a mod ID.
     */
    public static ModInfo getOrCreateModInfo(String modId) {
        if (modId == null) return null;
        
        return registry.computeIfAbsent(modId, id -> {
            String displayName = resolveDisplayName(id);
            return new ModInfo(id, displayName);
        });
    }
    
    /**
     * Get ModInfo for a mod ID, or null if not tracked.
     */
    public static ModInfo getModInfo(String modId) {
        return modId == null ? null : registry.get(modId);
    }
    
    /**
     * Get all tracked mods.
     */
    public static Collection<ModInfo> getAllMods() {
        return Collections.unmodifiableCollection(registry.values());
    }
    
    /**
     * Resolve display name from Fabric mod metadata.
     */
    private static String resolveDisplayName(String modId) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        return container.map(c -> c.getMetadata().getName()).orElse(modId);
    }
    
    // ==================== TRANSLATION KEY TRACKING ====================
    
    /**
     * Record a translation key from a mod's language file.
     */
    public static void recordTranslationKey(String modId, String key) {
        if (modId == null || key == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.translationKeys.add(key);
        allKnownTranslationKeys.add(key);
        translationKeyToModId.put(key, modId);
    }
    
    /**
     * Record a vanilla translation key.
     */
    public static void recordVanillaTranslationKey(String key) {
        if (key == null) return;

        vanillaTranslationKeys.add(key);
        allKnownTranslationKeys.add(key);
    }

    /**
     * Remove a vanilla translation key (e.g., when deprecated/renamed by Minecraft).
     */
    public static void removeVanillaTranslationKey(String key) {
        if (key == null) return;
        vanillaTranslationKeys.remove(key);
        allKnownTranslationKeys.remove(key);
        translationKeyToModId.remove(key);
    }
    
    /**
     * Record a server resource pack translation key.
     */
    public static void recordServerPackKey(String key) {
        if (key == null) return;

        serverPackKeys.add(key);
        allKnownTranslationKeys.add(key);
    }
    
    /**
     * Check if a translation key is from vanilla.
     */
    public static boolean isVanillaTranslationKey(String key) {
        return key != null && vanillaTranslationKeys.contains(key);
    }
    
    /**
     * Get the mod ID that owns a translation key.
     */
    public static String getModForTranslationKey(String key) {
        if (key == null) return null;
        return translationKeyToModId.get(key);
    }
    
    // ==================== AUTO MODE HELPER ====================

    /**
     * Check if a mod is effectively whitelisted, considering AUTO mode.
     * In AUTO mode, any mod with registered network channels is whitelisted.
     * In CUSTOM mode, delegates to manual whitelist check.
     * Default Fabric API mods are always whitelisted in Fabric mode.
     */
    private static boolean isModEffectivelyWhitelisted(String modId, SpoofSettings settings) {
        if (modId == null) return false;
        if (settings.isFabricMode() && DEFAULT_FABRIC_MODS.contains(modId)) {
            return true;
        }
        if (settings.getWhitelistMode() == SpoofSettings.WhitelistMode.AUTO) {
            ModInfo info = getModInfo(modId);
            return info != null && info.hasChannels();
        }
        return settings.isModWhitelisted(modId);
    }

    // ==================== CENTRALIZED WHITELIST CHECK ====================

    /**
     * Centralized whitelist check for both translation keys and keybind keys.
     * Servers can abuse either mechanism, so we use the same logic for both.
     * 
     * @param key The translation key or keybind key to check
     * @param source "translation" or "keybind" for logging purposes
     * @return true if the key should be allowed
     */
    public static boolean isWhitelistedKey(String key, String source) {
        if (key == null) return false;
        
        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        
        // Default Fabric API module keys always allowed in Fabric mode
        if (settings.isFabricMode() && isDefaultFabricModKey(key)) {
            Opsec.LOGGER.debug("[Whitelist] ALLOWED {} '{}' - default Fabric API mod in Fabric mode", source, key);
            return true;
        }

        // Forge loader keys always allowed in Forge mode
        if (settings.isForgeMode() && isForgeKey(key)) {
            Opsec.LOGGER.debug("[Whitelist] ALLOWED {} '{}' - Forge key in Forge mode", source, key);
            return true;
        }

        // Whitelist must be enabled for mod-specific checks
        if (!settings.isWhitelistEnabled()) {
            Opsec.LOGGER.debug("[Whitelist] {} '{}' - whitelist NOT enabled", source, key);
            return false;
        }

        // Try to find the mod that owns this key
        // Check keybind tracking first (for actual keybinds)
        String modId = getModForKeybind(key);
        if (modId != null && isModEffectivelyWhitelisted(modId, settings)) {
            Opsec.LOGGER.debug("[Whitelist] ALLOWED {} '{}' via keybind tracking (mod: {})", source, key, modId);
            return true;
        }

        // Check translation tracking (for translation keys or keybinds with translation-style names)
        modId = getModForTranslationKey(key);
        if (modId != null && isModEffectivelyWhitelisted(modId, settings)) {
            Opsec.LOGGER.debug("[Whitelist] ALLOWED {} '{}' via translation tracking (mod: {})", source, key, modId);
            return true;
        }

        Opsec.LOGGER.debug("[Whitelist] BLOCKED {} '{}' - modId: '{}', not whitelisted", source, key, modId);
        return false;
    }
    
    /**
     * Check if a translation key is from a whitelisted mod.
     * Delegates to centralized whitelist check.
     */
    public static boolean isWhitelistedTranslationKey(String key) {
        return isWhitelistedKey(key, "translation");
    }
    
    /**
     * Check if a keybind is from a whitelisted mod.
     * Delegates to centralized whitelist check.
     */
    public static boolean isWhitelistedKeybind(String keybindName) {
        return isWhitelistedKey(keybindName, "keybind");
    }
    
    /**
     * Check if a key belongs to one of the default-whitelisted Fabric API modules.
     * Used in Fabric mode to auto-allow Fabric API keys without manual whitelisting.
     */
    private static boolean isDefaultFabricModKey(String key) {
        if (key == null) return false;
        String modId = getModForTranslationKey(key);
        if (modId == null) modId = getModForKeybind(key);
        return modId != null && DEFAULT_FABRIC_MODS.contains(modId);
    }
    
    /**
     * Check if a key is from Forge, FML, or NeoForge.
     * Unified check for both translation keys and keybinds.
     */
    private static boolean isForgeKey(String key) {
        if (key == null) return false;
        
        return key.startsWith("forge.") || 
               key.startsWith("forgemod.") ||
               key.startsWith("fml.") ||
               key.startsWith("neoforge.") ||
               key.startsWith("key.forge") ||
               key.startsWith("key.neoforge") ||
               key.startsWith("category.forge") ||
               key.startsWith("category.neoforge") ||
               key.startsWith("pack.source.forge") ||  // pack.source.forgemod
               key.equals("pack.source.forgemod") ||
               key.equals("pack.source.mod");  // Generic mod source used by Forge
    }
    
    
    /**
     * Check if a translation key is from a server resource pack.
     */
    public static boolean isServerPackTranslationKey(String key) {
        return key != null && serverPackKeys.contains(key);
    }

    /**
     * Clear translation key caches. Called on language reload.
     * Also clears server pack translations so that keys from unloaded
     * (popped) resource packs are no longer whitelisted.
     */
    public static void clearTranslationKeys() {
        for (ModInfo info : registry.values()) {
            info.translationKeys.clear();
        }
        vanillaTranslationKeys.clear();
        serverPackKeys.clear();
        allKnownTranslationKeys.clear();
        translationKeyToModId.clear();
        Opsec.LOGGER.debug("[ModRegistry] Cleared translation key cache (including server pack keys)");
    }
    
    // ==================== KEYBIND TRACKING ====================
    
    /**
     * Record a keybind registered by a mod.
     */
    public static void recordKeybind(String modId, String keybindName) {
        if (modId == null || keybindName == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.keybinds.add(keybindName);
        allKnownKeybinds.add(keybindName);
        keybindToModId.put(keybindName, modId);

        Opsec.LOGGER.debug("[ModRegistry] Recorded keybind '{}' from mod '{}'", keybindName, modId);
    }
    
    /**
     * Record a vanilla keybind.
     */
    public static void recordVanillaKeybind(String keybindName) {
        if (keybindName == null) return;
        
        vanillaKeybinds.add(keybindName);
        allKnownKeybinds.add(keybindName);
    }
    
    /**
     * Check if a keybind is from vanilla.
     */
    public static boolean isVanillaKeybind(String keybindName) {
        return keybindName != null && vanillaKeybinds.contains(keybindName);
    }
    
    /**
     * Get the mod ID that owns a keybind.
     */
    public static String getModForKeybind(String keybindName) {
        if (keybindName == null) return null;
        return keybindToModId.get(keybindName);
    }
    
    
    // ==================== NAMESPACE RESOLUTION ====================

    /**
     * Resolve a channel namespace to the mod ID(s) that own it.
     * First checks if the namespace is itself a Fabric mod ID, then falls back
     * to the cached namespace-to-modId mapping table.
     *
     * @param namespace The channel namespace (e.g., "jm", "journeymap")
     * @return Set of mod IDs that own this namespace, or empty set if unknown
     */
    public static Set<String> resolveModIdsForNamespace(String namespace) {
        if (namespace == null) return Set.of();

        // If the namespace IS a registered Fabric mod ID, return it directly
        if (FabricLoader.getInstance().getModContainer(namespace).isPresent()) {
            return Set.of(namespace);
        }

        // Check cached namespace-to-modId mappings
        Set<String> mapped = namespaceToModIds.get(namespace);
        if (mapped != null && !mapped.isEmpty()) {
            return Collections.unmodifiableSet(mapped);
        }

        return Set.of();
    }

    /**
     * Record a mapping from a channel namespace to its owning mod ID.
     * Skips identity mappings (where namespace equals modId).
     *
     * @param namespace The channel namespace (e.g., "jm")
     * @param modId The owning mod ID (e.g., "journeymap")
     */
    public static void recordNamespaceMapping(String namespace, String modId) {
        if (namespace == null || modId == null || namespace.equals(modId)) return;

        namespaceToModIds.computeIfAbsent(namespace, k -> ConcurrentHashMap.newKeySet()).add(modId);
        Opsec.LOGGER.debug("[ModRegistry] Mapped namespace '{}' to mod '{}'", namespace, modId);
    }

    // ==================== CHANNEL TRACKING ====================

    /**
     * Record a network channel registered by a mod.
     */
    //? if >=1.21.11 {
    /*public static void recordChannel(String modId, Identifier channel) {*/
    //?} else {
    public static void recordChannel(String modId, ResourceLocation channel) {
    //?}
        if (modId == null || channel == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.channels.add(channel);
        channelToModId.put(channel, modId);

        Opsec.LOGGER.debug("[ModRegistry] Recorded channel '{}' from mod '{}'", channel, modId);
    }
    
    /**
     * Check if a channel is from a whitelisted mod.
     * Uses exact matching via tracked channel ownership, direct namespace match,
     * and namespace-to-modId alias resolution. No fuzzy matching.
     */
    //? if >=1.21.11 {
    /*public static boolean isWhitelistedChannel(Identifier channel) {*/
    //?} else {
    public static boolean isWhitelistedChannel(ResourceLocation channel) {
    //?}
        if (channel == null) return false;

        String namespace = channel.getNamespace();

        // Always allow core channels (minecraft)
        if ("minecraft".equals(namespace)) {
            return true;
        }

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        // Default Fabric API module channels always allowed in Fabric mode
        // This check MUST come before the whitelist-enabled guard, so that
        // Fabric's own channels pass through even when whitelist mode is OFF or CUSTOM.
        if (settings.isFabricMode()) {
            // Check 1a: Does a DEFAULT_FABRIC_MODS mod own this channel? (O(1) reverse index)
            String fabricOwner = channelToModId.get(channel);
            if (fabricOwner != null && DEFAULT_FABRIC_MODS.contains(fabricOwner)) {
                return true;
            }
            // Check 1b: Is the namespace itself a DEFAULT_FABRIC_MODS entry? (e.g., "fabric")
            if (DEFAULT_FABRIC_MODS.contains(namespace)) {
                return true;
            }
            // Check 1c: Resolve namespace to mod ID(s) — handles aliases
            Set<String> resolvedIds = resolveModIdsForNamespace(namespace);
            for (String resolvedId : resolvedIds) {
                if (DEFAULT_FABRIC_MODS.contains(resolvedId)) {
                    return true;
                }
            }
        }

        if (!settings.isWhitelistEnabled()) {
            return false;
        }

        // Check 2: Does a whitelisted mod own this channel? (O(1) reverse index)
        String channelOwner = channelToModId.get(channel);
        if (channelOwner != null && isModEffectivelyWhitelisted(channelOwner, settings)) {
            return true;
        }

        // Check 3: Is the namespace itself whitelisted? (exact match, backwards compat)
        // Handles users who whitelisted "jm" directly instead of "journeymap"
        if (isModEffectivelyWhitelisted(namespace, settings)) {
            return true;
        }

        // Check 4: Resolve namespace to mod ID(s) via alias table (exact match)
        // Handles: user whitelisted "journeymap" but channel namespace is "jm"
        Set<String> resolvedModIds = resolveModIdsForNamespace(namespace);
        for (String resolvedModId : resolvedModIds) {
            if (isModEffectivelyWhitelisted(resolvedModId, settings)) {
                return true;
            }
        }

        return false;
    }
    
    // ==================== INITIALIZATION ====================
    
    /**
     * Mark initialization as complete.
     */
    public static void markInitialized() {
        initialized = true;
        Opsec.LOGGER.debug("[ModRegistry] Initialized with {} mods, {} translation keys, {} keybinds",
            registry.size(), allKnownTranslationKeys.size(), allKnownKeybinds.size());
    }
    
    /**
     * Check if registry has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    // ==================== STATISTICS ====================
    
    public static int getVanillaKeyCount() {
        return vanillaTranslationKeys.size();
    }
    
    public static int getServerPackKeyCount() {
        return serverPackKeys.size();
    }
    
    public static int getTranslationKeyCount() {
        return allKnownTranslationKeys.size();
    }
    
    public static int getKeybindCount() {
        return allKnownKeybinds.size();
    }
    
}

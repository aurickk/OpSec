package aurick.opsec.mod.tracking;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
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

import static aurick.opsec.mod.config.OpsecConstants.Channels.*;

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
    
    /** Server resource pack translation keys (session whitelist) */
    private static final Set<String> serverPackTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** All known translation keys for fast lookup */
    private static final Set<String> allKnownTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** All known keybinds for fast lookup */
    private static final Set<String> allKnownKeybinds = ConcurrentHashMap.newKeySet();
    
    /** Recently logged keys for deduplication (cleared periodically) */
    private static final Map<String, Long> recentlyLoggedKeys = new ConcurrentHashMap<>();
    private static final long LOG_DEDUP_MS = 1000; // 1 second deduplication window
    
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
         * Check if this mod has any trackable content (translation keys or channels).
         */
        public boolean hasTrackableContent() {
            return hasTranslationKeys() || hasChannels();
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
     * Get all tracked mod IDs.
     */
    public static Set<String> getAllModIds() {
        return Collections.unmodifiableSet(registry.keySet());
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
     * Record a server resource pack translation key.
     */
    public static void recordServerPackTranslationKey(String key) {
        if (key == null) return;
        
        serverPackTranslationKeys.add(key);
        allKnownTranslationKeys.add(key);
    }
    
    /**
     * Check if a translation key is from vanilla.
     */
    public static boolean isVanillaTranslationKey(String key) {
        return key != null && vanillaTranslationKeys.contains(key);
    }
    
    /**
     * Check if a translation key is from a server resource pack.
     */
    public static boolean isServerPackTranslationKey(String key) {
        return key != null && serverPackTranslationKeys.contains(key);
    }
    
    /**
     * Check if a translation key is known (vanilla or any mod).
     */
    public static boolean isKnownTranslationKey(String key) {
        return key != null && allKnownTranslationKeys.contains(key);
    }
    
    /**
     * Get the mod ID that owns a translation key.
     */
    public static String getModForTranslationKey(String key) {
        if (key == null) return null;
        
        for (ModInfo info : registry.values()) {
            // Skip fabric-resource-loader-v0 - it incorrectly captures all mod keys
            if ("fabric-resource-loader-v0".equals(info.modId)) {
                continue;
            }
            
            if (info.translationKeys.contains(key)) {
                return info.modId;
            }
        }
        
        return null;
    }
    
    // ==================== CENTRALIZED WHITELIST CHECK ====================
    
    /**
     * Check if a key should be logged (deduplication).
     * Returns true if the key hasn't been logged recently.
     */
    private static boolean shouldLogKey(String key) {
        if (key == null) return false;
        
        long now = System.currentTimeMillis();
        Long lastLogged = recentlyLoggedKeys.get(key);
        
        if (lastLogged != null && (now - lastLogged) < LOG_DEDUP_MS) {
            return false; // Already logged recently
        }
        
        recentlyLoggedKeys.put(key, now);
        
        // Periodically clean up old entries (every 100 entries)
        if (recentlyLoggedKeys.size() > 100) {
            recentlyLoggedKeys.entrySet().removeIf(e -> (now - e.getValue()) > LOG_DEDUP_MS);
        }
        
        return true;
    }
    
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
        
        // Fabric loader keys always allowed in Fabric mode
        if (settings.isFabricMode() && isFabricKey(key)) {
            if (shouldLogKey(key)) {
                Opsec.LOGGER.info("[Whitelist] ALLOWED {} '{}' - Fabric key in Fabric mode", source, key);
            }
            return true;
        }
        
        // Forge loader keys always allowed in Forge mode
        if (settings.isForgeMode() && isForgeKey(key)) {
            if (shouldLogKey(key)) {
                Opsec.LOGGER.info("[Whitelist] ALLOWED {} '{}' - Forge key in Forge mode", source, key);
            }
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
        if (modId != null && settings.isModWhitelisted(modId)) {
            if (shouldLogKey(key)) {
                Opsec.LOGGER.info("[Whitelist] ALLOWED {} '{}' via keybind tracking (mod: {})", source, key, modId);
            }
            return true;
        }
        
        // Check translation tracking (for translation keys or keybinds with translation-style names)
        modId = getModForTranslationKey(key);
        if (modId != null && settings.isModWhitelisted(modId)) {
            if (shouldLogKey(key)) {
                Opsec.LOGGER.info("[Whitelist] ALLOWED {} '{}' via translation tracking (mod: {})", source, key, modId);
            }
            return true;
        }
        
        if (shouldLogKey(key)) {
            Opsec.LOGGER.info("[Whitelist] BLOCKED {} '{}' - modId: '{}', not whitelisted", source, key, modId);
        }
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
     * Check if a key is from Fabric or Fabric API.
     * Unified check for both translation keys and keybinds.
     */
    private static boolean isFabricKey(String key) {
        if (key == null) return false;
        
        return key.startsWith("fabric.") ||           // fabric.gui.creativeTabPage
               key.startsWith("fabric-") ||           // fabric-registry-sync-v0.*, fabric-data-attachment-api-v1.*
               key.startsWith("fabricloader.") ||     // Fabric Loader keys
               key.startsWith("key.fabric") ||        // Fabric keybinds
               key.startsWith("category.fabric") ||   // Fabric keybind categories
               key.startsWith("pack.source.fabric") || // pack.source.fabricmod
               key.startsWith("pack.source.builtin") || // pack.source.builtinMod
               key.startsWith("pack.name.fabric") ||  // pack.name.fabricMod
               key.startsWith("pack.description.mod") || // pack.description.modResources
               key.startsWith("commands.datapack.fabric") || // commands.datapack.fabric.internal
               key.startsWith("tag.");                // Convention tags (tag.block.c.*, tag.item.c.*, etc.)
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
     * Clear translation key caches. Called on language reload.
     */
    public static void clearTranslationKeys() {
        for (ModInfo info : registry.values()) {
            info.translationKeys.clear();
        }
        vanillaTranslationKeys.clear();
        allKnownTranslationKeys.clear();
        Opsec.LOGGER.debug("[ModRegistry] Cleared translation key cache");
    }
    
    /**
     * Clear server pack translation keys. Called on disconnect.
     */
    public static void clearServerPackKeys() {
        serverPackTranslationKeys.clear();
        Opsec.LOGGER.debug("[ModRegistry] Cleared server pack translation keys");
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
     * Check if a keybind is known.
     */
    public static boolean isKnownKeybind(String keybindName) {
        return keybindName != null && allKnownKeybinds.contains(keybindName);
    }
    
    /**
     * Get the mod ID that owns a keybind.
     */
    public static String getModForKeybind(String keybindName) {
        if (keybindName == null) return null;
        
        for (ModInfo info : registry.values()) {
            if (info.keybinds.contains(keybindName)) {
                return info.modId;
            }
        }
        return null;
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
        
        Opsec.LOGGER.debug("[ModRegistry] Recorded channel '{}' from mod '{}'", channel, modId);
    }
    
    /**
     * Check if a channel is from a whitelisted mod.
     * Also checks the channel namespace as a fallback with fuzzy matching.
     */
    //? if >=1.21.11 {
    /*public static boolean isWhitelistedChannel(Identifier channel) {*/
    //?} else {
    public static boolean isWhitelistedChannel(ResourceLocation channel) {
    //?}
        if (channel == null) return false;
        
        String namespace = channel.getNamespace();
        
        // Always allow core channels
        if (MINECRAFT.equals(namespace) || FABRIC_NAMESPACE.equals(namespace) 
                || namespace.startsWith(FABRIC_NAMESPACE + "-") || COMMON.equals(namespace)) {
            return true;
        }
        
        OpsecConfig config = OpsecConfig.getInstance();
        if (!config.getSettings().isWhitelistEnabled()) {
            return false;
        }
        
        // Check if any whitelisted mod owns this channel (exact match from tracking)
        for (ModInfo info : registry.values()) {
            if (info.channels.contains(channel) && config.getSettings().isModWhitelisted(info.modId)) {
                return true;
            }
        }
        
        // Fallback: channel namespace is typically the mod ID (exact match)
        if (config.getSettings().isModWhitelisted(namespace)) {
            return true;
        }
        
        // Fuzzy match: check if namespace matches any whitelisted mod ID
        // (handles cases like xaerominimap vs xaeros_minimap)
        String normalizedNamespace = normalizeModId(namespace);
        for (String whitelistedMod : config.getSettings().getWhitelistedMods()) {
            if (normalizeModId(whitelistedMod).equals(normalizedNamespace)) {
                return true;
            }
            // Also check if the namespace starts with or is contained in the mod ID
            if (normalizedNamespace.startsWith(normalizeModId(whitelistedMod)) ||
                normalizeModId(whitelistedMod).startsWith(normalizedNamespace)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Normalize a mod ID for fuzzy matching.
     * Removes common separators and converts to lowercase.
     */
    private static String normalizeModId(String modId) {
        if (modId == null) return "";
        return modId.toLowerCase()
            .replace("-", "")
            .replace("_", "")
            .replace(".", "");
    }
    
    /**
     * Check if a channel namespace (mod ID) is whitelisted.
     */
    public static boolean isWhitelistedChannelNamespace(String namespace) {
        if (namespace == null) return false;
        
        // Always allow core channels
        if (MINECRAFT.equals(namespace) || FABRIC_NAMESPACE.equals(namespace) 
                || namespace.startsWith(FABRIC_NAMESPACE + "-") || COMMON.equals(namespace)) {
            return true;
        }
        
        OpsecConfig config = OpsecConfig.getInstance();
        if (!config.getSettings().isWhitelistEnabled()) {
            return false;
        }
        
        return config.getSettings().isModWhitelisted(namespace);
    }
    
    // ==================== WHITELIST HELPERS ====================
    
    /**
     * Check if a mod is whitelisted.
     */
    public static boolean isWhitelistedMod(String modId) {
        if (modId == null) return false;
        
        OpsecConfig config = OpsecConfig.getInstance();
        if (!config.getSettings().isWhitelistEnabled()) {
            return false;
        }
        
        return config.getSettings().isModWhitelisted(modId);
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
        return serverPackTranslationKeys.size();
    }
    
    public static int getTranslationKeyCount() {
        return allKnownTranslationKeys.size();
    }
    
    public static int getKeybindCount() {
        return allKnownKeybinds.size();
    }
    
    /**
     * Debug: dump registry stats to log.
     */
    public static void dumpStats() {
        Opsec.LOGGER.debug("[ModRegistry] Stats: {} vanilla keys, {} server pack keys, {} total keys, {} keybinds",
            vanillaTranslationKeys.size(), 
            serverPackTranslationKeys.size(),
            allKnownTranslationKeys.size(),
            allKnownKeybinds.size());
        
        for (ModInfo info : registry.values()) {
            if (info.hasTrackableContent()) {
                Opsec.LOGGER.debug("[ModRegistry]   {}: {} keys, {} keybinds, {} channels", 
                    info.modId, info.translationKeys.size(), info.keybinds.size(), info.channels.size());
            }
        }
    }
}

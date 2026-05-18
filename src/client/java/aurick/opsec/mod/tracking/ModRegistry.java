package aurick.opsec.mod.tracking;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.util.KeybindDefaults;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private static final Map<String, ModInfo> registry =
        new ConcurrentHashMap<>();

    /**
     * Vanilla translation keys (always whitelisted).
     * Volatile + non-final so {@link #commitTranslationRebuild()} can atomically
     * swap in a freshly-built set at language reload, instead of clearing this
     * set at HEAD and repopulating piecemeal — which left a window where mid-
     * reload translation probes saw an empty set.
     */
    private static volatile Set<String> vanillaTranslationKeys =
        ConcurrentHashMap.newKeySet();

    /** Server resource pack translation keys (whitelisted for vanilla resolution). Same atomic-swap rationale as {@link #vanillaTranslationKeys}. */
    private static volatile Set<String> serverPackKeys =
        ConcurrentHashMap.newKeySet();

    /** Maps channel namespaces to their owning Fabric mod IDs (e.g., "jm" -> "journeymap") */
    private static final Map<String, Set<String>> namespaceToModIds =
        new ConcurrentHashMap<>();

    /** Reverse index: translation key -> mod ID for O(1) lookup (P1/A4/A11). Same atomic-swap rationale as {@link #vanillaTranslationKeys}. */
    private static volatile Map<String, String> translationKeyToModId =
        new ConcurrentHashMap<>();

    /** Reverse index: keybind name -> mod ID for O(1) lookup (P1/A4/A11) */
    private static final Map<String, String> keybindToModId =
        new ConcurrentHashMap<>();

    /** Reverse index: channel -> mod ID for O(1) lookup (P7) */
    //? if >=1.21.11 {
    /*private static final Map<Identifier, String> channelToModId = new ConcurrentHashMap<>();*/
    //?} else {
    private static final Map<ResourceLocation, String> channelToModId =
        new ConcurrentHashMap<>();
    //?}

    /** Transitive required-dep closure of all whitelisted mods. Prevents the "depender resolves, required dep doesn't" fingerprint. Volatile + atomic-swap for lock-free reads. */
    private static volatile Set<String> dependencyClosure =
        ConcurrentHashMap.newKeySet();

    /** modId → seed that pulled it into {@link #dependencyClosure}. First walker wins. Atomically swapped with the closure. */
    private static volatile Map<String, String> implicitProvider =
        new ConcurrentHashMap<>();

    /** Platform mods skipped during dep walks — their presence is implied by the loader/brand. */
    public static final Set<String> PLATFORM_MODS =
        Set.of("minecraft", "java", "fabricloader", "opsec");

    private ModRegistry() {}

    /**
     * Information about a tracked mod.
     */
    public static class ModInfo {

        private final String modId;
        private final String displayName;
        private final Set<String> translationKeys =
            ConcurrentHashMap.newKeySet();
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

        public String getModId() {
            return modId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Set<String> getTranslationKeys() {
            return Collections.unmodifiableSet(translationKeys);
        }

        public Set<String> getKeybinds() {
            return Collections.unmodifiableSet(keybinds);
        }

        //? if >=1.21.11 {
        /*public Set<Identifier> getChannels() { return Collections.unmodifiableSet(channels); }*/
        //?} else {
        public Set<ResourceLocation> getChannels() { return Collections.unmodifiableSet(channels); }
        //?}

        public boolean hasTranslationKeys() {
            return !translationKeys.isEmpty();
        }

        public boolean hasKeybinds() {
            return !keybinds.isEmpty();
        }

        public boolean hasChannels() {
            return !channels.isEmpty();
        }

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
        Optional<ModContainer> container =
            FabricLoader.getInstance().getModContainer(modId);
        return container.map(c -> c.getMetadata().getName()).orElse(modId);
    }

    // ==================== TRANSLATION KEY TRACKING ====================

    /**
     * Staging buffer for an in-progress language reload. While a rebuild is
     * active on the current thread, the public record/remove methods write here
     * instead of the live volatile fields, so concurrent readers never observe
     * a partially-populated registry.
     */
    private static final class TranslationRebuildStaging {
        final Set<String> vanilla = ConcurrentHashMap.newKeySet();
        final Set<String> serverPack = ConcurrentHashMap.newKeySet();
        final Map<String, String> translationKeyToModId = new ConcurrentHashMap<>();
    }

    /**
     * Per-thread active staging. Set by {@link #beginTranslationRebuild()},
     * consumed by {@link #commitTranslationRebuild()}. Other threads see
     * {@code null} and write straight to live (the normal startup-path
     * record* callers).
     */
    private static final ThreadLocal<TranslationRebuildStaging> rebuildStaging = new ThreadLocal<>();

    /**
     * Begin a language-reload rebuild on the current thread. Subsequent
     * record/remove calls on this thread route to a fresh staging buffer;
     * the live fields stay readable in their pre-reload state. Pair with
     * {@link #commitTranslationRebuild()} (success) or
     * {@link #abortTranslationRebuild()} (exception).
     *
     * <p>Defensive: if a prior rebuild was never committed (loadFrom threw),
     * we discard the orphaned staging here rather than leaking it.</p>
     */
    public static void beginTranslationRebuild() {
        rebuildStaging.remove();
        rebuildStaging.set(new TranslationRebuildStaging());
        // Per-ModInfo translationKeys is not atomically swapped — it's read
        // only by /opsec info and the whitelist UI, not by the render hot path.
        // Clear in place so the rebuild repopulates each mod's set cleanly.
        for (ModInfo info : registry.values()) {
            info.translationKeys.clear();
        }
    }

    /**
     * Atomically replace the live translation-key state with the staged one.
     * No-op if no rebuild is active.
     */
    public static void commitTranslationRebuild() {
        TranslationRebuildStaging s = rebuildStaging.get();
        if (s == null) return;
        // Three volatile writes — each is independently atomic; readers always
        // see a fully-populated set/map (either the old or the new one).
        vanillaTranslationKeys = s.vanilla;
        serverPackKeys = s.serverPack;
        translationKeyToModId = s.translationKeyToModId;
        rebuildStaging.remove();
        Opsec.LOGGER.debug(
            "[ModRegistry] Committed translation rebuild: {} vanilla, {} server pack",
            s.vanilla.size(), s.serverPack.size()
        );
    }

    /**
     * Discard the in-progress staging without affecting live state.
     * Call from exception paths so a half-built rebuild doesn't leak.
     */
    public static void abortTranslationRebuild() {
        rebuildStaging.remove();
    }

    /**
     * Record a translation key from a mod's language file.
     */
    public static void recordTranslationKey(String modId, String key) {
        if (modId == null || key == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.translationKeys.add(key);
        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.translationKeyToModId.put(key, modId);
        } else {
            translationKeyToModId.put(key, modId);
        }
    }

    /**
     * Record a vanilla translation key.
     */
    public static void recordVanillaTranslationKey(String key) {
        if (key == null) return;

        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.vanilla.add(key);
        } else {
            vanillaTranslationKeys.add(key);
        }
    }

    /**
     * Remove a vanilla translation key (e.g., when deprecated/renamed by Minecraft).
     */
    public static void removeVanillaTranslationKey(String key) {
        if (key == null) return;
        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.vanilla.remove(key);
            s.translationKeyToModId.remove(key);
        } else {
            vanillaTranslationKeys.remove(key);
            translationKeyToModId.remove(key);
        }
    }

    /**
     * Record a server resource pack translation key.
     */
    public static void recordServerPackKey(String key) {
        if (key == null) return;

        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.serverPack.add(key);
        } else {
            serverPackKeys.add(key);
        }
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

    // ==================== DEPENDENCY CLOSURE ====================

    /** Recompute the closure from the current whitelist. Only DEPENDS edges — RECOMMENDS/SUGGESTS aren't guaranteed loaded and would themselves leak a "claimed-but-absent" fingerprint. */
    public static void rebuildDependencyClosure() {
        Set<String> seeds = collectWhitelistSeeds();
        Set<String> closure = ConcurrentHashMap.newKeySet();
        Map<String, String> providers = new ConcurrentHashMap<>();
        for (String seed : seeds) {
            walkDependencies(seed, seed, closure, providers);
            walkUpToJijHost(seed, closure, providers);
        }
        dependencyClosure = closure;
        implicitProvider = providers;
        Opsec.LOGGER.debug(
            "[ModRegistry] Dep closure rebuilt: {} seeds -> {} mods",
            seeds.size(), closure.size()
        );
    }

    private static Set<String> collectWhitelistSeeds() {
        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        Set<String> seeds = new HashSet<>();
        switch (settings.getWhitelistMode()) {
            case AUTO -> {
                for (ModInfo info : registry.values()) {
                    if (info.hasChannels()) seeds.add(info.getModId());
                }
            }
            case CUSTOM -> seeds.addAll(settings.getWhitelistedMods());
            default -> {}
        }
        return seeds;
    }

    /** Whether the given mod is in the transitive dep closure of any whitelisted mod. */
    public static boolean isInDependencyClosure(String modId) {
        return modId != null && dependencyClosure.contains(modId);
    }

    /** Seed that pulled {@code modId} into the closure, or null if it's a seed itself or not in the closure. */
    public static String getRequiringMod(String modId) {
        return modId == null ? null : implicitProvider.get(modId);
    }

    public static String getModDisplayName(String modId) {
        if (modId == null) return null;
        return FabricLoader.getInstance().getModContainer(modId)
            .map(c -> c.getMetadata().getName()).orElse(modId);
    }

    /** Display name of the requiring mod, with a localized fallback for unattributed entries. */
    public static String resolveRequiringModName(String modId) {
        String requiringId = getRequiringMod(modId);
        return requiringId != null
            ? getModDisplayName(requiringId)
            : OpsecLang.tr(OpsecStrings.WHITELIST_REQUIRING_FALLBACK);
    }

    public static Collection<? extends ModContainer> getContainedMods(String modId) {
        if (modId == null) return List.of();
        return FabricLoader.getInstance().getModContainer(modId)
            .map(ModContainer::getContainedMods)
            .orElseGet(List::of);
    }

    /** Trackable-content counts including JIJ descendants — meta jars (e.g. fabric-api) need this to register as "has content". */
    public record ContentCounts(int translationKeys, int keybinds, int channels) {
        public boolean isEmpty() {
            return translationKeys == 0 && keybinds == 0 && channels == 0;
        }
    }

    public static ContentCounts aggregateContent(String modId) {
        if (modId == null) return new ContentCounts(0, 0, 0);
        int tk = 0, kb = 0, ch = 0;
        ModInfo info = registry.get(modId);
        if (info != null) {
            tk = info.getTranslationKeys().size();
            kb = info.getKeybinds().size();
            ch = info.getChannels().size();
        }
        for (ModContainer child : getContainedMods(modId)) {
            ContentCounts sub = aggregateContent(child.getMetadata().getId());
            tk += sub.translationKeys();
            kb += sub.keybinds();
            ch += sub.channels();
        }
        return new ContentCounts(tk, kb, ch);
    }

    /** Does this mod or any JIJ descendant have any trackable content? */
    public static boolean hasTrackableContentIncludingJij(String modId) {
        return !aggregateContent(modId).isEmpty();
    }

    // Channels pre-stringified so callers avoid the Stonecutter Identifier/ResourceLocation split.
    public static List<String> aggregateAllTranslationKeys(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> out.addAll(info.getTranslationKeys()));
        return out;
    }

    public static List<String> aggregateAllKeybinds(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> out.addAll(info.getKeybinds()));
        return out;
    }

    public static List<String> aggregateAllChannelIds(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> info.getChannels().forEach(c -> out.add(c.toString())));
        return out;
    }

    private static void aggregateRecursive(String modId, java.util.function.Consumer<ModInfo> sink) {
        if (modId == null) return;
        ModInfo info = registry.get(modId);
        if (info != null) sink.accept(info);
        for (ModContainer child : getContainedMods(modId)) {
            aggregateRecursive(child.getMetadata().getId(), sink);
        }
    }

    private static void walkDependencies(String seedRoot, String modId, Set<String> closure, Map<String, String> providers) {
        if (modId == null || PLATFORM_MODS.contains(modId)) return;
        Optional<ModContainer> container =
            FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) return;
        String canonicalId = container.get().getMetadata().getId();
        if (PLATFORM_MODS.contains(canonicalId)) return;
        if (!closure.add(canonicalId)) return;
        // ModInfo entries are keyed by namespace, which may be a "provides" alias rather than canonical id.
        for (String alias : container.get().getMetadata().getProvides()) {
            closure.add(alias);
            if (!alias.equals(seedRoot)) providers.putIfAbsent(alias, seedRoot);
        }
        if (!canonicalId.equals(seedRoot)) providers.putIfAbsent(canonicalId, seedRoot);

        for (ModContainer contained : container.get().getContainedMods()) {
            walkDependencies(seedRoot, contained.getMetadata().getId(), closure, providers);
        }
        for (ModDependency dep : container.get().getMetadata().getDependencies()) {
            if (dep.getKind() != ModDependency.Kind.DEPENDS) continue;
            walkDependencies(seedRoot, dep.getModId(), closure, providers);
        }
    }

    /** Walk a seed's JIJ host chain so co-shipped siblings of a tracked JIJ child also land in the closure. */
    private static void walkUpToJijHost(String originalSeed, Set<String> closure, Map<String, String> providers) {
        Optional<ModContainer> current = FabricLoader.getInstance().getModContainer(originalSeed);
        while (current.isPresent()) {
            Optional<ModContainer> host = current.get().getContainingMod();
            if (host.isEmpty()) break;
            String hostId = host.get().getMetadata().getId();
            // Host attributed to the seed; siblings get attributed to the host via the descending walk.
            providers.putIfAbsent(hostId, originalSeed);
            walkDependencies(hostId, hostId, closure, providers);
            current = host;
        }
    }

    // ==================== AUTO MODE HELPER ====================

    /** Direct membership (AUTO channels / CUSTOM toggle) OR transitive via the dep/JIJ-host closure. */
    private static boolean isModEffectivelyWhitelisted(
        String modId,
        SpoofSettings settings
    ) {
        if (modId == null) return false;
        if (settings.getWhitelistMode() == SpoofSettings.WhitelistMode.AUTO) {
            ModInfo info = getModInfo(modId);
            if (info != null && info.hasChannels()) return true;
        } else if (settings.isModWhitelisted(modId)) {
            return true;
        }
        return dependencyClosure.contains(modId);
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

        // Whitelist must be enabled for mod-specific checks
        if (!settings.isWhitelistEnabled()) {
            Opsec.LOGGER.debug(
                "[Whitelist] {} '{}' - whitelist NOT enabled",
                source,
                key
            );
            return false;
        }

        // Try to find the mod that owns this key
        // Check keybind tracking first (for actual keybinds)
        String modId = getModForKeybind(key);
        if (modId != null && isModEffectivelyWhitelisted(modId, settings)) {
            Opsec.LOGGER.debug(
                "[Whitelist] ALLOWED {} '{}' via keybind tracking (mod: {})",
                source,
                key,
                modId
            );
            return true;
        }

        // Check translation tracking (for translation keys or keybinds with translation-style names)
        modId = getModForTranslationKey(key);
        if (modId != null && isModEffectivelyWhitelisted(modId, settings)) {
            Opsec.LOGGER.debug(
                "[Whitelist] ALLOWED {} '{}' via translation tracking (mod: {})",
                source,
                key,
                modId
            );
            return true;
        }

        Opsec.LOGGER.debug(
            "[Whitelist] BLOCKED {} '{}' - modId: '{}', not whitelisted",
            source,
            key,
            modId
        );
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
        translationKeyToModId.clear();
        Opsec.LOGGER.debug(
            "[ModRegistry] Cleared translation key cache (including server pack keys)"
        );
    }

    // ==================== KEYBIND TRACKING ====================

    /**
     * Record a keybind registered by a mod.
     */
    public static void recordKeybind(String modId, String keybindName) {
        if (modId == null || keybindName == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.keybinds.add(keybindName);
        keybindToModId.put(keybindName, modId);

        Opsec.LOGGER.debug(
            "[ModRegistry] Recorded keybind '{}' from mod '{}'",
            keybindName,
            modId
        );
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
        if (
            namespace == null || modId == null || namespace.equals(modId)
        ) return;

        namespaceToModIds
            .computeIfAbsent(namespace, k -> ConcurrentHashMap.newKeySet())
            .add(modId);
        Opsec.LOGGER.debug(
            "[ModRegistry] Mapped namespace '{}' to mod '{}'",
            namespace,
            modId
        );
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

        Opsec.LOGGER.debug(
            "[ModRegistry] Recorded channel '{}' from mod '{}'",
            channel,
            modId
        );
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

        if (!settings.isWhitelistEnabled()) {
            return false;
        }

        String channelOwner = channelToModId.get(channel);
        if (channelOwner != null && isModEffectivelyWhitelisted(channelOwner, settings)) {
            return true;
        }

        if (isModEffectivelyWhitelisted(namespace, settings)) {
            return true;
        }

        // Alias resolution: user whitelisted "journeymap" but channel namespace is "jm" (or vice versa).
        Set<String> resolvedModIds = resolveModIdsForNamespace(namespace);
        for (String resolvedModId : resolvedModIds) {
            if (isModEffectivelyWhitelisted(resolvedModId, settings)) {
                return true;
            }
        }

        return false;
    }

    // ==================== STATISTICS ====================

    public static int getVanillaKeyCount() {
        return vanillaTranslationKeys.size();
    }

    public static int getServerPackKeyCount() {
        return serverPackKeys.size();
    }

    public static int getTranslationKeyCount() {
        java.util.HashSet<String> all = new java.util.HashSet<>(
            vanillaTranslationKeys
        );
        all.addAll(serverPackKeys);
        for (ModInfo info : registry.values()) {
            all.addAll(info.translationKeys);
        }
        return all.size();
    }

    public static int getKeybindCount() {
        int count = KeybindDefaults.size();
        for (ModInfo info : registry.values()) count += info.keybinds.size();
        return count;
    }
}

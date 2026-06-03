package aurick.opsec.mod.tracking;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConstants;
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

    /** Server pack translations: key → value. Returned at block time so a mod lang beating the pack in {@code ClientLanguage.storage} can't expose us. Atomic-swapped per reload. */
    private static volatile Map<String, String> serverPackTranslations =
        new ConcurrentHashMap<>();

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
        Set.of("minecraft", "java", "fabricloader");

    /** Real-vanilla {@code KnownPack} namespace ({@code KnownPack.VANILLA_NAMESPACE}). Never strip these. */
    private static final String VANILLA_PACK_NAMESPACE = "minecraft";
    /** Fabric mod-pack namespace — Fabric's confusingly-named {@code ModResourcePackCreator.VANILLA} constant. */
    private static final String FABRIC_PACK_NAMESPACE = "vanilla";

    /** Min mod-id length for {@code minecraft:}-channel path attribution (avoids spurious short matches). */
    private static final int MIN_MOD_ID_LEN_FOR_PATH_ATTRIBUTION = 3;

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
        /** Namespace-qualified shaders the mod ships, e.g. "meteor-client:shaders/blur.vert". */
        private final Set<String> shaders = ConcurrentHashMap.newKeySet();
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

        public boolean hasShaders() {
            return !shaders.isEmpty();
        }

        public Set<String> getShaders() {
            return Collections.unmodifiableSet(shaders);
        }

        /**
         * Check if this mod has any trackable content (translation keys, channels, keybinds, or shaders).
         */
        public boolean hasTrackableContent() {
            return hasTranslationKeys() || hasChannels() || hasKeybinds() || hasShaders();
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
        final Map<String, String> serverPackTranslations = new ConcurrentHashMap<>();
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
        serverPackTranslations = s.serverPackTranslations;
        translationKeyToModId = s.translationKeyToModId;
        rebuildStaging.remove();
        Opsec.LOGGER.debug(
            "[ModRegistry] Committed translation rebuild: {} vanilla, {} server pack",
            s.vanilla.size(), s.serverPackTranslations.size()
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

    /** Record a server pack translation key and its pack-defined value. */
    public static void recordServerPackTranslation(String key, String value) {
        if (key == null || value == null) return;

        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.serverPackTranslations.put(key, value);
        } else {
            serverPackTranslations.put(key, value);
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
    public record ContentCounts(int translationKeys, int keybinds, int channels, int knownPacks, int shaders) {
        public boolean isEmpty() {
            return translationKeys == 0 && keybinds == 0 && channels == 0 && knownPacks == 0 && shaders == 0;
        }
    }

    public static ContentCounts aggregateContent(String modId) {
        if (modId == null) return new ContentCounts(0, 0, 0, 0, 0);
        int tk = 0, kb = 0, ch = 0, sh = 0;
        ModInfo info = registry.get(modId);
        if (info != null) {
            tk = info.getTranslationKeys().size();
            kb = info.getKeybinds().size();
            ch = info.getChannels().size();
            sh = info.getShaders().size();
        }
        int kp = getKnownPackStrings(modId).size();
        for (ModContainer child : getContainedMods(modId)) {
            ContentCounts sub = aggregateContent(child.getMetadata().getId());
            tk += sub.translationKeys();
            kb += sub.keybinds();
            ch += sub.channels();
            kp += sub.knownPacks();
            sh += sub.shaders();
        }
        return new ContentCounts(tk, kb, ch, kp, sh);
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

    public static List<String> aggregateAllShaderPaths(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> out.addAll(info.getShaders()));
        return out;
    }

    /**
     * Known-pack triples for this mod and every JIJ descendant unioned.
     *
     * <p>Walks {@code getContainedMods} directly rather than via
     * {@link #aggregateRecursive} — known-pack strings aren't stored on
     * {@link ModInfo}, they're computed from filesystem inspection + builtin
     * registrations.</p>
     */
    public static List<String> aggregateAllKnownPackStrings(String modId) {
        if (modId == null || !KNOWN_PACKS_HOOK_PRESENT) return List.of();
        List<String> out = new java.util.ArrayList<>();
        aggregateKnownPacksRecursive(modId, out);
        return out;
    }

    private static void aggregateKnownPacksRecursive(String modId, List<String> out) {
        out.addAll(getKnownPackStrings(modId));
        for (ModContainer child : getContainedMods(modId)) {
            aggregateKnownPacksRecursive(child.getMetadata().getId(), out);
        }
    }

    private static void aggregateRecursive(String modId, java.util.function.Consumer<ModInfo> sink) {
        if (modId == null) return;
        ModInfo info = registry.get(modId);
        if (info != null) sink.accept(info);
        for (ModContainer child : getContainedMods(modId)) {
            aggregateRecursive(child.getMetadata().getId(), sink);
        }
    }

    /** modId → set of builtin pack ids registered with non-null SERVER_DATA. Captured by {@code ResourceLoaderBuiltinPackMixin}. */
    private static final Map<String, Set<String>> builtinPackIds = new ConcurrentHashMap<>();

    /** Reverse index: {@code "vanilla:id:version"} triple → owning modId. Mirrors {@link #channelToModId}. */
    private static final Map<String, String> knownPackToModId = new ConcurrentHashMap<>();

    /**
     * True iff Fabric's modded known-packs machinery is on the classpath.
     * Probes {@code ModPackResourcesUtil} (the impl class) rather than
     * {@code KnownPacksManagerMixin} itself — Sponge Mixin's classloader
     * rejects {@code Class.forName} on registered mixin classes with
     * {@code IllegalClassLoadError}. Both ship together in
     * fabric-resource-loader-v1 from MC 1.21.11.
     */
    private static final boolean KNOWN_PACKS_HOOK_PRESENT = probeKnownPacksHook();

    private static boolean probeKnownPacksHook() {
        try {
            Class.forName("net.fabricmc.fabric.impl.resource.pack.ModPackResourcesUtil",
                    false, ModRegistry.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return whether mod packs would actually be exposed in the known-packs handshake on this client. */
    public static boolean isKnownPacksHookPresent() {
        return KNOWN_PACKS_HOOK_PRESENT;
    }

    /**
     * Caller must filter out resource-only builtin packs ({@code dataPack == null})
     * — those don't ride the known-packs handshake. The mixin handles that.
     */
    public static void recordBuiltinPack(String registeringModId, String packIdToString) {
        if (registeringModId == null || packIdToString == null) return;
        builtinPackIds.computeIfAbsent(registeringModId, k -> ConcurrentHashMap.newKeySet())
                .add(packIdToString);
        // Mirror to the reverse index for any registrations that arrive after the eager indexer ran.
        FabricLoader.getInstance().getModContainer(registeringModId).ifPresent(c -> {
            String version = c.getMetadata().getVersion().getFriendlyString();
            knownPackToModId.put(formatKnownPackTriple(packIdToString, version), registeringModId);
        });
    }

    /**
     * The {@code (namespace, id, version)} triples this mod would advertise
     * in {@code ServerboundSelectKnownPacks}. Server match is strict byte
     * equality on the full triple — version comes from {@code fabric.mod.json}.
     */
    public static List<String> getKnownPackStrings(String modId) {
        if (modId == null) return List.of();
        if (!KNOWN_PACKS_HOOK_PRESENT) return List.of();
        Optional<ModContainer> opt = FabricLoader.getInstance().getModContainer(modId);
        if (opt.isEmpty()) return List.of();
        ModContainer container = opt.get();
        if ("builtin".equals(container.getMetadata().getType())) return List.of();

        List<String> out = new java.util.ArrayList<>();
        collectKnownPackTriples(container, out::add);
        return out;
    }

    /** Single source of truth for the triple shape — display and indexer share this so they can't drift. */
    private static void collectKnownPackTriples(ModContainer container, java.util.function.Consumer<String> sink) {
        String modId = container.getMetadata().getId();
        String version = container.getMetadata().getVersion().getFriendlyString();

        if (hasServerDataContent(container)) {
            sink.accept(formatKnownPackTriple(modId, version));
        }

        Set<String> builtins = builtinPackIds.get(modId);
        if (builtins != null) {
            for (String packId : builtins) {
                sink.accept(formatKnownPackTriple(packId, version));
            }
        }
    }

    private static String formatKnownPackTriple(String id, String version) {
        return FABRIC_PACK_NAMESPACE + ":" + id + ":" + version;
    }

    /** Populates {@link #knownPackToModId}. Call once at client start. No-op when the Fabric hook is absent. */
    public static void indexKnownPackOwners() {
        if (!KNOWN_PACKS_HOOK_PRESENT) return;
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if ("builtin".equals(container.getMetadata().getType())) continue;
            String modId = container.getMetadata().getId();
            collectKnownPackTriples(container, triple -> knownPackToModId.put(triple, modId));
        }
        Opsec.LOGGER.debug("[OpSec] Indexed {} known-pack triples across all mods", knownPackToModId.size());
    }

    public static String resolveModForKnownPack(String namespace, String id, String version) {
        if (!FABRIC_PACK_NAMESPACE.equals(namespace)) return null;
        return knownPackToModId.get(formatKnownPackTriple(id, version));
    }

    /**
     * True iff the mod ships any {@code data/<namespace>/} directory in any of
     * its root paths. This matches Fabric's
     * {@code ModNioPackResources.readNamespaces(...).get(SERVER_DATA).isEmpty()}
     * check that gates whether the mod gets enumerated in the known-packs
     * repository at all.
     */
    /** modId → {@link #computeServerDataContent} result. Per-session immutable. */
    private static final Map<String, Boolean> serverDataContentCache = new ConcurrentHashMap<>();

    /**
     * True iff the mod ships any {@code data/<namespace>/} directory in any of
     * its root paths. Matches Fabric's
     * {@code ModNioPackResources.readNamespaces(...).get(SERVER_DATA).isEmpty()}
     * check that gates known-packs participation.
     */
    private static boolean hasServerDataContent(ModContainer container) {
        return serverDataContentCache.computeIfAbsent(
                container.getMetadata().getId(),
                id -> computeServerDataContent(container));
    }

    private static boolean computeServerDataContent(ModContainer container) {
        for (java.nio.file.Path root : container.getRootPaths()) {
            java.nio.file.Path dataDir = root.resolve("data");
            if (!java.nio.file.Files.isDirectory(dataDir)) continue;
            try (var stream = java.nio.file.Files.list(dataDir)) {
                if (stream.anyMatch(java.nio.file.Files::isDirectory)) return true;
            } catch (java.io.IOException ignored) {
                // permission / FS error → treat as no data, same as Fabric would
            }
        }
        return false;
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

    /** Mirrors {@link #isWhitelistedChannel} for known packs. Real-vanilla namespace always allowed; unattributed mod packs blocked. */
    public static boolean isWhitelistedKnownPack(String namespace, String id, String version) {
        if (VANILLA_PACK_NAMESPACE.equals(namespace)) return true;
        String modId = resolveModForKnownPack(namespace, id, version);
        if (modId == null) return false;
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        return isModEffectivelyWhitelisted(modId, settings);
    }

    /** namespace → installed modId that ships {@code assets/<ns>/shaders/}. {@code ""} means none. Per-session immutable, lazily filled. */
    private static final Map<String, String> shaderOwnerByNamespace = new ConcurrentHashMap<>();

    /**
     * The installed mod whose jar ships {@code assets/<namespace>/shaders/}, or {@code null} if none.
     * Resolves by asset ownership (not channel/keybind heuristics), so a non-null result also
     * guarantees a jar shader exists for the strip to fall through to. Cached per namespace.
     */
    public static String resolveShaderOwnerMod(String namespace) {
        if (namespace == null) return null;
        String owner = shaderOwnerByNamespace.computeIfAbsent(namespace, ns -> {
            String rel = "assets/" + ns + "/shaders";
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                if (PLATFORM_MODS.contains(id)) continue;
                for (java.nio.file.Path root : mod.getRootPaths()) {
                    if (java.nio.file.Files.exists(root.resolve(rel))) return id;
                }
            }
            return "";
        });
        return owner.isEmpty() ? null : owner;
    }

    /** Record a shader a mod ships under {@code assets/<namespace>/shaders/}; seeds the owner cache. */
    public static void recordShader(String modId, String namespace, String shaderPath) {
        if (modId == null || namespace == null || shaderPath == null) return;
        getOrCreateModInfo(modId).shaders.add(namespace + ":" + shaderPath);
        shaderOwnerByNamespace.putIfAbsent(namespace, modId);
    }

    /**
     * Whether a server pack may override {@code assets/<namespace>/shaders/*}. Stripped only when an
     * installed mod owns those shaders and isn't whitelisted, so OpSec acts only on a mod the client
     * has. Vanilla allowed; whitelist-disabled strips owned shaders (mirrors {@link #isWhitelistedChannel}).
     */
    public static boolean isServerPackShaderAllowed(String namespace) {
        if (namespace == null) return true;
        if (VANILLA_PACK_NAMESPACE.equals(namespace)) return true;

        String modId = resolveShaderOwnerMod(namespace);
        if (modId == null) return true; // client has no mod with shaders here → nothing to override

        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isWhitelistEnabled()) return false; // installed mod, whitelist off → strip
        return isModEffectivelyWhitelisted(modId, settings);
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
        return key != null && serverPackTranslations.containsKey(key);
    }

    public static String getServerPackTranslation(String key) {
        return key == null ? null : serverPackTranslations.get(key);
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
        serverPackTranslations.clear();
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

        // Canonicalize provides aliases (e.g. "fabric" → "fabric-api") via container metadata.
        var container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            return Set.of(container.get().getMetadata().getId());
        }

        // Check cached namespace-to-modId mappings
        Set<String> mapped = namespaceToModIds.get(namespace);
        if (mapped != null && !mapped.isEmpty()) {
            return Collections.unmodifiableSet(mapped);
        }

        return Set.of();
    }

    /**
     * Single-result variant: returns the canonical mod id that owns this
     * channel namespace, or {@code namespace} itself when nothing matches.
     * Used by the channel scanner to avoid creating orphan ModInfo entries
     * for namespaces (like {@code "fabric"}) that are shared by multiple JIJ
     * submodules of a single umbrella mod.
     */
    public static String resolveOwningModForNamespace(String namespace) {
        if (namespace == null) return null;
        Set<String> resolved = resolveModIdsForNamespace(namespace);
        if (!resolved.isEmpty()) return resolved.iterator().next();
        return namespace;
    }

    /** Whether {@code path} is one of the {@code minecraft:} channels a stock client uses. */
    public static boolean isVanillaMinecraftChannel(String path) {
        return path != null && OpsecConstants.Channels.VANILLA_PATHS.contains(path);
    }

    /**
     * Owning mod for a channel, across every namespace. Non-{@code minecraft:} delegates to
     * {@link #resolveOwningModForNamespace}; {@code minecraft:} attributes masquerading channels
     * by path. {@code null} = genuine vanilla channel or no attributable owner.
     */
    public static String resolveOwningModForChannel(String namespace, String path) {
        if (namespace == null) return null;
        if (!"minecraft".equals(namespace)) {
            return resolveOwningModForNamespace(namespace);
        }
        if (isVanillaMinecraftChannel(path)) return null;
        return resolveModForMinecraftChannelPath(path);
    }

    /**
     * Attribute a {@code minecraft:}-masquerading channel by matching its path against installed
     * mod ids on a token boundary, longest match wins ({@code fancymenu_packet_bridge} → {@code fancymenu}).
     * {@code null} when nothing matches — left unattributed, which blocks it in spoof modes.
     */
    public static String resolveModForMinecraftChannelPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String best = null;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (id.length() < MIN_MOD_ID_LEN_FOR_PATH_ATTRIBUTION) continue;
            // path begins with the mod id, ending at a token boundary (end-of-string or _ / . -).
            boolean match = path.startsWith(id)
                && (path.length() == id.length() || "_/.-".indexOf(path.charAt(id.length())) >= 0);
            if (match && (best == null || id.length() > best.length())) {
                best = id;
            }
        }
        return best;
    }

    /**
     * Synthesize the equivalent of a dropped {@code provides} alias by
     * inspecting JIJ structure. Fabric API on older MC declared
     * {@code "provides": ["fabric"]}; on newer it doesn't, but its JIJ tree
     * still contains 40+ children whose ids all start with {@code "fabric-"}
     * and that all register channels under the {@code "fabric:"} namespace.
     * Without an alias, those channels can't be attributed to any real mod —
     * they end up as an orphan {@code ModInfo} keyed by the bare namespace.
     */
    public static void inferJijNamespaceAliases() {
        for (ModContainer top : FabricLoader.getInstance().getAllMods()) {
            if (top.getContainingMod().isPresent()) continue;
            String topId = top.getMetadata().getId();
            if (PLATFORM_MODS.contains(topId)) continue;

            Map<String, Integer> prefixCounts = new java.util.HashMap<>();
            for (ModContainer child : top.getContainedMods()) {
                String childId = child.getMetadata().getId();
                int dash = childId.indexOf('-');
                if (dash > 0) {
                    prefixCounts.merge(childId.substring(0, dash), 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> e : prefixCounts.entrySet()) {
                String prefix = e.getKey();
                // Structural guard: only treat the prefix as the umbrella's own namespace
                // when it's also a prefix of the umbrella's id — prevents two unrelated
                // umbrellas with `common-*` children from racing to claim "common".
                if (e.getValue() >= MIN_CHILDREN_FOR_PREFIX_ALIAS
                        && topId.startsWith(prefix)
                        && !FabricLoader.getInstance().getModContainer(prefix).isPresent()) {
                    recordNamespaceMapping(prefix, topId);
                }
            }
        }
    }

    private static final int MIN_CHILDREN_FOR_PREFIX_ALIAS = 2;

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
        String path = channel.getPath();

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        // Vanilla minecraft: channels always pass; masquerading ones gate on their owner's
        // whitelist (tracked ownership, else by path). Unattributable ⇒ blocked.
        if ("minecraft".equals(namespace)) {
            if (isVanillaMinecraftChannel(path)) return true;
            if (!settings.isWhitelistEnabled()) return false;
            String owner = channelToModId.get(channel);
            if (owner == null) owner = resolveModForMinecraftChannelPath(path);
            return owner != null && isModEffectivelyWhitelisted(owner, settings);
        }

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
        return serverPackTranslations.size();
    }

    public static int getTranslationKeyCount() {
        java.util.HashSet<String> all = new java.util.HashSet<>(
            vanillaTranslationKeys
        );
        all.addAll(serverPackTranslations.keySet());
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

    public static int getKnownPackCount() {
        return knownPackToModId.size();
    }

    public static int getShaderCount() {
        int count = 0;
        for (ModInfo info : registry.values()) count += info.shaders.size();
        return count;
    }
}

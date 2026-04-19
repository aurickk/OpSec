package aurick.opsec.mod.protection;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
//? if >=1.21.11 {
/*import net.minecraft.util.Util;*/
//?} else {
import net.minecraft.Util;
//?}

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordination state for the Bypass Server Pack Requirement feature. */
public final class PackStripHandler {
    private PackStripHandler() {}

    private static final Set<UUID> loadForReal = ConcurrentHashMap.newKeySet();
    // Tracked for overlay eligibility (required-only) and rearrange-on-open logic.
    private static final Set<UUID> requiredPacks = ConcurrentHashMap.newKeySet();
    // Packs we wrap with LangOnlyPackResources. In MANUAL this is required-only; in
    // ASK / ALWAYS_ON we also wrap optional packs so they can be stripped by default.
    private static final Set<UUID> wrappedPacks = ConcurrentHashMap.newKeySet();
    // Non-wrapped optional server packs the user explicitly unselected in pack-select.
    // Persists so PackRepository.rebuildSelected (called from commit and post-commit
    // reload) consistently skips auto-preserve for these packs.
    private static final Set<UUID> userUnselectedOptional = ConcurrentHashMap.newKeySet();
    // Gate so fingerprint-burst pushes (servers pushing 24+ packs) don't spawn 24 overlays.
    private static final AtomicBoolean overlayShownThisSession = new AtomicBoolean(false);

    public static void onPackPush(UUID id, String url, boolean required) {
        OpsecConfig config = OpsecConfig.getInstance();
        if (!config.shouldStripPack()) return;

        // Initial loadForReal state is mode-dependent:
        //   MANUAL     → pack applies fully like vanilla (user must opt in to strip).
        //   ASK / ALWAYS_ON → pack starts stripped.
        SpoofSettings.StripMode mode = config.getPackStripMode();
        if (mode == SpoofSettings.StripMode.MANUAL) {
            loadForReal.add(id);
        } else {
            loadForReal.remove(id);
        }

        if (required) {
            requiredPacks.add(id);
        } else {
            requiredPacks.remove(id);
        }

        // Wrap with LangOnlyPackResources if:
        //   MANUAL     → required packs only (optional follows vanilla toggle).
        //   ASK / ALWAYS_ON → all server packs (strip everything by default).
        boolean shouldWrap = required || mode != SpoofSettings.StripMode.MANUAL;
        if (shouldWrap) {
            wrappedPacks.add(id);
        } else {
            wrappedPacks.remove(id);
        }

        // Same-UUID re-push: drop any persisted "user unselected" opt-out so the
        // freshly-pushed pack enters as selected (matches vanilla first-push flow).
        userUnselectedOptional.remove(id);

        if (required && mode == SpoofSettings.StripMode.ASK
                && overlayShownThisSession.compareAndSet(false, true)) {
            if (!isHttpUrl(url)) return; // vanilla will reject with INVALID_URL
            PackStripOverlay.enqueue(id, required);
        }
    }

    private static boolean isHttpUrl(String url) {
        try {
            Util.parseAndValidateUntrustedUri(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void onPop(Optional<UUID> maybeId) {
        if (maybeId == null || maybeId.isEmpty()) {
            loadForReal.clear();
            requiredPacks.clear();
            wrappedPacks.clear();
            userUnselectedOptional.clear();
            return;
        }
        UUID id = maybeId.get();
        loadForReal.remove(id);
        requiredPacks.remove(id);
        wrappedPacks.remove(id);
        userUnselectedOptional.remove(id);
    }

    public static void clearAll() {
        loadForReal.clear();
        requiredPacks.clear();
        wrappedPacks.clear();
        userUnselectedOptional.clear();
        overlayShownThisSession.set(false);
    }

    public static void markLoadForReal(UUID id) {
        if (id != null) loadForReal.add(id);
    }

    public static void clearLoadForReal(UUID id) {
        if (id != null) loadForReal.remove(id);
    }

    public static boolean isLoadForReal(UUID id) {
        return id != null && loadForReal.contains(id);
    }

    public static boolean isRequired(UUID id) {
        return id != null && requiredPacks.contains(id);
    }

    /** True iff the pack was wrapped with {@link LangOnlyPackResources} at push time. */
    public static boolean isWrapped(UUID id) {
        return id != null && wrappedPacks.contains(id);
    }

    public static void markUserUnselectedOptional(UUID id) {
        if (id != null) userUnselectedOptional.add(id);
    }

    public static void clearUserUnselectedOptional(UUID id) {
        if (id != null) userUnselectedOptional.remove(id);
    }

    public static boolean isUserUnselectedOptional(UUID id) {
        return id != null && userUnselectedOptional.contains(id);
    }

    // Vanilla format: "server/<serial>/<uuid>" (DownloadedPackSource#loadRequestedPacks, "server/%08X/%s").
    private static final String SERVER_PACK_PREFIX = "server/";

    /** Parses a downloaded-server pack id back into its UUID. Empty for any other shape. */
    public static Optional<UUID> packIdToUuid(String packId) {
        if (packId == null || !packId.startsWith(SERVER_PACK_PREFIX)) return Optional.empty();
        int lastSlash = packId.lastIndexOf('/');
        if (lastSlash < SERVER_PACK_PREFIX.length() - 1) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(packId.substring(lastSlash + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

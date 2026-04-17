package aurick.opsec.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically extracts keybind defaults from the running client.
 * Uses vanilla translation keys whitelist to identify vanilla keybinds.
 * Automatically adapts to different Minecraft versions and keybind changes.
 *
 * Stores the {@link InputConstants.Key} reference rather than a resolved display
 * string, so {@link #getDefault(String)} re-resolves through the current
 * {@link net.minecraft.locale.Language} instance on every call. This matches
 * vanilla's late-binding behavior and prevents detection via resource-pack
 * override of translatable display keys or mid-session language change.
 */
public final class KeybindDefaults {

    private KeybindDefaults() {}

    private static final Map<String, InputConstants.Key> defaultKeys = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    /**
     * Initialize by scanning all keybinds from the client.
     * Uses vanilla translation keys whitelist to identify vanilla keybinds.
     * Call this after Minecraft is fully loaded.
     */
    public static void initialize() {
        if (initialized) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;

            KeyMapping[] keyMappings = mc.options.keyMappings;
            if (keyMappings == null) return;

            int vanillaCount = 0;
            for (KeyMapping mapping : keyMappings) {
                if (mapping == null) continue;

                String keyName = mapping.getName();

                // Vanilla keybind = exists in vanilla translation keys whitelist
                // This whitelist is populated from the minecraft/vanilla language pack
                if (ModRegistry.isVanillaTranslationKey(keyName)) {
                    InputConstants.Key defaultKey = mapping.getDefaultKey();
                    if (defaultKey != null) {
                        defaultKeys.put(keyName, defaultKey);
                        vanillaCount++;
                    }
                }
            }

            initialized = true;
            Opsec.LOGGER.debug("[OpSec] Loaded {} vanilla keybind defaults from translation whitelist", vanillaCount);

        } catch (RuntimeException e) {
            Opsec.LOGGER.error("[OpSec] Failed to initialize keybind defaults: {}", e.getMessage());
        }
    }

    /**
     * Get the vanilla default display string for a keybind key, resolved against
     * the current {@link net.minecraft.locale.Language} instance. Returns null if
     * the key is not a known vanilla keybind.
     */
    public static String getDefault(String key) {
        if (!initialized) {
            initialize();
        }
        InputConstants.Key defaultKey = defaultKeys.get(key);
        return defaultKey == null ? null : defaultKey.getDisplayName().getString();
    }

    /**
     * Check if a keybind key is a known vanilla keybind.
     */
    public static boolean hasDefault(String key) {
        if (!initialized) {
            initialize();
        }
        return defaultKeys.containsKey(key);
    }

    /**
     * Check if a keybind is vanilla (by name).
     */
    public static boolean isVanillaKeybind(String keyName) {
        return hasDefault(keyName);
    }

    /**
     * Clear the cache so the next {@link #getDefault(String)} / {@link #hasDefault(String)}
     * call re-populates from the current {@code KeyMapping[]} and current
     * {@link ModRegistry} whitelist state. Invoked from
     * {@code ClientLanguageMixin.opsec$onLoadStart} on every language / resource-pack
     * reload — keeps the vanilla-keybind membership set in sync with freshly
     * cleared {@code ModRegistry.vanillaTranslationKeys}.
     */
    public static synchronized void reset() {
        defaultKeys.clear();
        initialized = false;
        Opsec.LOGGER.debug("[OpSec] KeybindDefaults cache reset");
    }

}

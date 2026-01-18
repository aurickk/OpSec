package aurick.opsec.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically extracts keybind defaults from the running client.
 * Uses vanilla translation keys whitelist to identify vanilla keybinds.
 * Automatically adapts to different Minecraft versions and keybind changes.
 */
public final class KeybindDefaults {
    
    private KeybindDefaults() {}
    
    // Dynamically populated maps
    private static final Map<String, String> dynamicDefaults = new ConcurrentHashMap<>();
    private static final Set<String> vanillaKeybinds = ConcurrentHashMap.newKeySet();
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
                if (ModTracker.isVanillaTranslationKey(keyName)) {
                    InputConstants.Key defaultKey = mapping.getDefaultKey();
                    String defaultValue = getDisplayName(defaultKey);
                    
                    vanillaKeybinds.add(keyName);
                    dynamicDefaults.put(keyName, defaultValue);
                    vanillaCount++;
                }
            }
            
            initialized = true;
            Opsec.LOGGER.debug("[OpSec] Loaded {} vanilla keybind defaults from translation whitelist", vanillaCount);
            
        } catch (RuntimeException e) {
            Opsec.LOGGER.error("[OpSec] Failed to initialize keybind defaults: {}", e.getMessage());
        }
    }
    
    /**
     * Get the display name for a key.
     */
    private static String getDisplayName(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            return "Not Bound";
        }
        return key.getDisplayName().getString();
    }
    
    /**
     * Get the vanilla default value for a keybind key.
     * Returns null if the key is not a known vanilla keybind.
     */
    public static String getDefault(String key) {
        // Ensure initialized
        if (!initialized) {
            initialize();
        }
        return dynamicDefaults.get(key);
    }
    
    /**
     * Get the vanilla default value for a keybind key, with fallback.
     */
    public static String getDefaultOrElse(String key, String fallback) {
        String defaultVal = getDefault(key);
        return defaultVal != null ? defaultVal : fallback;
    }
    
    /**
     * Check if a keybind key is a known vanilla keybind.
     */
    public static boolean hasDefault(String key) {
        // Ensure initialized
        if (!initialized) {
            initialize();
        }
        return vanillaKeybinds.contains(key);
    }
    
    /**
     * Check if a keybind is vanilla (by name).
     */
    public static boolean isVanillaKeybind(String keyName) {
        return hasDefault(keyName);
    }
    
    /**
     * Get count of known vanilla keybinds.
     */
    public static int getVanillaKeybindCount() {
        if (!initialized) {
            initialize();
        }
        return vanillaKeybinds.size();
    }
    
    /**
     * Check if a value looks like an unbound keybind message.
     */
    public static boolean isUnboundMessage(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        return lower.contains("not bound") || lower.equals("none");
    }
    
    /**
     * Force re-initialization (useful if keybinds change at runtime).
     */
    public static void reinitialize() {
        initialized = false;
        dynamicDefaults.clear();
        vanillaKeybinds.clear();
        initialize();
    }
}

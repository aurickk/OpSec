package incognito.mod.util;

import java.util.Map;

/**
 * Centralized keybind defaults for vanilla Minecraft.
 * Used for spoofing keybind values in translation exploit protection.
 */
public final class KeybindDefaults {
    
    private KeybindDefaults() {}
    
    /**
     * Maps keybind keys to their vanilla default display values.
     */
    public static final Map<String, String> KEY_TO_DEFAULT = Map.ofEntries(
        // Movement
        Map.entry("key.forward", "W"),
        Map.entry("key.back", "S"),
        Map.entry("key.left", "A"),
        Map.entry("key.right", "D"),
        Map.entry("key.jump", "Space"),
        Map.entry("key.sneak", "Left Shift"),
        Map.entry("key.sprint", "Left Control"),
        
        // Actions
        Map.entry("key.attack", "Left Button"),
        Map.entry("key.use", "Right Button"),
        Map.entry("key.pickItem", "Middle Button"),
        
        // Inventory/GUI
        Map.entry("key.inventory", "E"),
        Map.entry("key.drop", "Q"),
        Map.entry("key.chat", "T"),
        Map.entry("key.command", "/"),
        Map.entry("key.playerlist", "Tab"),
        Map.entry("key.screenshot", "F2"),
        Map.entry("key.togglePerspective", "F5"),
        Map.entry("key.smoothCamera", "Not bound"),
        Map.entry("key.fullscreen", "F11"),
        Map.entry("key.spectatorOutlines", "Not bound"),
        Map.entry("key.swapOffhand", "F"),
        Map.entry("key.saveToolbarActivator", "C"),
        Map.entry("key.loadToolbarActivator", "X"),
        Map.entry("key.advancements", "L"),
        Map.entry("key.socialInteractions", "P"),
        
        // Hotbar
        Map.entry("key.hotbar.1", "1"),
        Map.entry("key.hotbar.2", "2"),
        Map.entry("key.hotbar.3", "3"),
        Map.entry("key.hotbar.4", "4"),
        Map.entry("key.hotbar.5", "5"),
        Map.entry("key.hotbar.6", "6"),
        Map.entry("key.hotbar.7", "7"),
        Map.entry("key.hotbar.8", "8"),
        Map.entry("key.hotbar.9", "9")
    );
    
    /**
     * Maps resolved keybind values back to their keybind keys.
     * Used for detecting resolved keybind values in text.
     */
    public static final Map<String, String> VALUE_TO_KEY = Map.ofEntries(
        Map.entry("W", "key.forward"),
        Map.entry("S", "key.back"),
        Map.entry("A", "key.left"),
        Map.entry("D", "key.right"),
        Map.entry("Space", "key.jump"),
        Map.entry("Left Shift", "key.sneak"),
        Map.entry("Left Control", "key.sprint"),
        Map.entry("Left Button", "key.attack"),
        Map.entry("Right Button", "key.use"),
        Map.entry("Middle Button", "key.pickItem"),
        Map.entry("E", "key.inventory"),
        Map.entry("Q", "key.drop"),
        Map.entry("T", "key.chat"),
        Map.entry("/", "key.command"),
        Map.entry("Tab", "key.playerlist"),
        Map.entry("F2", "key.screenshot"),
        Map.entry("F5", "key.togglePerspective"),
        Map.entry("F11", "key.fullscreen"),
        Map.entry("F", "key.swapOffhand"),
        Map.entry("L", "key.advancements"),
        Map.entry("P", "key.socialInteractions"),
        Map.entry("1", "key.hotbar.1"),
        Map.entry("2", "key.hotbar.2"),
        Map.entry("3", "key.hotbar.3"),
        Map.entry("4", "key.hotbar.4"),
        Map.entry("5", "key.hotbar.5"),
        Map.entry("6", "key.hotbar.6"),
        Map.entry("7", "key.hotbar.7"),
        Map.entry("8", "key.hotbar.8"),
        Map.entry("9", "key.hotbar.9"),
        Map.entry("Not bound", "key.unknown")
    );
    
    /**
     * Get the vanilla default value for a keybind key.
     */
    public static String getDefault(String key) {
        return KEY_TO_DEFAULT.get(key);
    }
    
    /**
     * Get the vanilla default value for a keybind key, with fallback.
     */
    public static String getDefaultOrElse(String key, String fallback) {
        return KEY_TO_DEFAULT.getOrDefault(key, fallback);
    }
    
    /**
     * Check if a keybind key has a known default.
     */
    public static boolean hasDefault(String key) {
        return KEY_TO_DEFAULT.containsKey(key);
    }
    
    /**
     * Get the keybind key for a resolved value.
     */
    public static String getKeyForValue(String value) {
        return VALUE_TO_KEY.get(value);
    }
    
    /**
     * Check if a value is a known keybind default (longer than 2 chars to avoid false positives).
     */
    public static boolean isKnownValue(String value) {
        return value != null && value.length() > 2 && VALUE_TO_KEY.containsKey(value);
    }
}


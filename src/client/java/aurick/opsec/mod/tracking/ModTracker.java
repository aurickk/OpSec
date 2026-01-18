package aurick.opsec.mod.tracking;

//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.Set;

/**
 * Legacy compatibility wrapper for ModRegistry.
 * Delegates all calls to the unified ModRegistry.
 * 
 * @deprecated Use {@link ModRegistry} directly instead.
 */
@Deprecated
public class ModTracker {
    
    private ModTracker() {}
    
    // ==================== TRANSLATION KEY TRACKING ====================
    
    public static void recordTranslationKey(String modId, String key) {
        ModRegistry.recordTranslationKey(modId, key);
    }
    
    public static void recordVanillaTranslationKey(String key) {
        ModRegistry.recordVanillaTranslationKey(key);
    }
    
    public static void recordServerPackTranslationKey(String key) {
        ModRegistry.recordServerPackTranslationKey(key);
    }
    
    public static boolean isServerPackTranslationKey(String key) {
        return ModRegistry.isServerPackTranslationKey(key);
    }
    
    public static void clearServerPackKeys() {
        ModRegistry.clearServerPackKeys();
    }
    
    public static int getServerPackKeyCount() {
        return ModRegistry.getServerPackKeyCount();
    }
    
    public static int getVanillaKeyCount() {
        return ModRegistry.getVanillaKeyCount();
    }
    
    public static boolean isKnownTranslationKey(String key) {
        return ModRegistry.isKnownTranslationKey(key);
    }
    
    public static boolean isVanillaTranslationKey(String key) {
        return ModRegistry.isVanillaTranslationKey(key);
    }
    
    public static String getModForTranslationKey(String key) {
        return ModRegistry.getModForTranslationKey(key);
    }
    
    public static boolean isWhitelistedTranslationKey(String key) {
        return ModRegistry.isWhitelistedTranslationKey(key);
    }
    
    public static int getTranslationKeyCount() {
        return ModRegistry.getTranslationKeyCount();
    }
    
    public static void clearTranslationKeys() {
        ModRegistry.clearTranslationKeys();
    }
    
    // ==================== KEYBIND TRACKING ====================
    
    public static void recordKeybind(String modId, String keybindName) {
        ModRegistry.recordKeybind(modId, keybindName);
    }
    
    public static void recordVanillaKeybind(String keybindName) {
        ModRegistry.recordVanillaKeybind(keybindName);
    }
    
    public static boolean isKnownKeybind(String keybindName) {
        return ModRegistry.isKnownKeybind(keybindName);
    }
    
    public static boolean isVanillaKeybind(String keybindName) {
        return ModRegistry.isVanillaKeybind(keybindName);
    }
    
    public static String getModForKeybind(String keybindName) {
        return ModRegistry.getModForKeybind(keybindName);
    }
    
    public static boolean isWhitelistedKeybind(String keybindName) {
        return ModRegistry.isWhitelistedKeybind(keybindName);
    }
    
    public static int getKeybindCount() {
        return ModRegistry.getKeybindCount();
    }
    
    // ==================== CHANNEL TRACKING ====================
    
    //? if >=1.21.11 {
    /*public static boolean isWhitelistedChannel(Identifier channel) {*/
    //?} else {
    public static boolean isWhitelistedChannel(ResourceLocation channel) {
    //?}
        return ModRegistry.isWhitelistedChannel(channel);
    }
    
    public static boolean isWhitelistedChannelNamespace(String namespace) {
        return ModRegistry.isWhitelistedChannelNamespace(namespace);
    }
    
    // ==================== INITIALIZATION ====================
    
    public static void markInitialized() {
        ModRegistry.markInitialized();
    }
    
    public static boolean isInitialized() {
        return ModRegistry.isInitialized();
    }
    
    public static Set<String> getKnownMods() {
        return ModRegistry.getAllModIds();
    }
    
    public static void dumpStats() {
        ModRegistry.dumpStats();
    }
}


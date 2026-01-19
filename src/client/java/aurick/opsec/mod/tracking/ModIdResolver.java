package aurick.opsec.mod.tracking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import aurick.opsec.mod.Opsec;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Resolves mod IDs from classes and stack traces.
 * Used to determine which mod registered a translation key or keybind.
 * Based on ExploitPreventer's Utils class.
 */
public class ModIdResolver {
    
    /** Classes to skip when walking the stack trace */
    private static final Set<String> SKIP_CLASS_PREFIXES = Set.of(
        "java.",
        "jdk.",
        "sun.",
        "aurick.opsec.mod.tracking.",
        "net.fabricmc.fabric.",
        "org.spongepowered.",
        "com.llamalad7.mixinextras."
    );
    
    private ModIdResolver() {}
    
    /**
     * Get mod ID from a JAR or development directory path.
     * Reads the fabric.mod.json to extract the mod ID.
     */
    public static String getModIdFromPath(Path path) {
        if (path == null) return null;
        
        try {
            String pathStr = path.toString();
            
            // Development environment - build/classes/java/main/
            if (pathStr.contains("build") && pathStr.contains("classes")) {
                Path modJsonPath = findFabricModJson(path);
                if (modJsonPath != null) {
                    try (InputStream inputStream = new FileInputStream(modJsonPath.toFile());
                         InputStreamReader reader = new InputStreamReader(inputStream)) {
                        JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                        if (jsonObject.has("id")) {
                            return jsonObject.get("id").getAsString();
                        }
                    }
                }
                return null;
            }
            
            // JAR file
            if (pathStr.endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(path.toFile())) {
                    JarEntry jarEntry = jarFile.getJarEntry("fabric.mod.json");
                    if (jarEntry != null) {
                        try (InputStream inputStream = jarFile.getInputStream(jarEntry);
                             InputStreamReader reader = new InputStreamReader(inputStream)) {
                            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                            if (jsonObject.has("id")) {
                                return jsonObject.get("id").getAsString();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            Opsec.LOGGER.debug("[ModIdResolver] Failed to read mod ID from path {}: {}", path, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Find fabric.mod.json relative to a class directory in development.
     */
    private static Path findFabricModJson(Path classPath) {
        // Try common development paths
        Path current = classPath;
        for (int i = 0; i < 10 && current != null; i++) {
            Path modJson = current.resolve("fabric.mod.json");
            if (modJson.toFile().exists()) {
                return modJson;
            }
            
            // Also check resources directory
            Path resourcesJson = current.resolve("resources/main/fabric.mod.json");
            if (resourcesJson.toFile().exists()) {
                return resourcesJson;
            }
            resourcesJson = current.resolve("../resources/main/fabric.mod.json");
            if (resourcesJson.toFile().exists()) {
                return resourcesJson.normalize();
            }
            
            current = current.getParent();
        }
        return null;
    }
    
    /**
     * Get mod ID from a class.
     * Uses the class's code source location to find the mod.
     */
    public static String getModIdFromClass(Class<?> clazz) {
        if (clazz == null) return null;
        
        try {
            // Fall back to code source analysis
            if (clazz.getProtectionDomain().getCodeSource() != null) {
                Path path = Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
                return getModIdFromPath(path);
            }
        } catch (URISyntaxException | SecurityException e) {
            Opsec.LOGGER.debug("[ModIdResolver] Failed to get mod ID from class {}: {}", 
                clazz.getName(), e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get mod ID from the current stack trace.
     * Walks up the stack to find the first class that belongs to a mod.
     */
    public static String getModIdFromStacktrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            
            // Skip framework/system classes
            boolean skip = false;
            for (String prefix : SKIP_CLASS_PREFIXES) {
                if (className.startsWith(prefix)) {
                    skip = true;
                    break;
                }
            }
            if (skip) continue;
            
            // Skip Thread class
            if (className.equals(Thread.class.getName())) continue;
            
            // Skip this class
            if (className.equals(ModIdResolver.class.getName())) continue;
            
            try {
                Class<?> clazz = Class.forName(className);
                String modId = getModIdFromClass(clazz);
                if (modId != null) {
                    return modId;
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // Class not loadable, skip
            }
        }
        
        return null;
    }
    
    /**
     * Check if a class name belongs to Minecraft/vanilla.
     */
    public static boolean isMinecraftClass(String className) {
        return className != null && (
            className.startsWith("net.minecraft.") ||
            className.startsWith("com.mojang.")
        );
    }
    
    /**
     * Check if a class name belongs to Fabric API.
     */
    public static boolean isFabricClass(String className) {
        return className != null && (
            className.startsWith("net.fabricmc.fabric.") ||
            className.startsWith("net.fabricmc.api.")
        );
    }
    
    /**
     * Infer mod ID from a translation key based on common naming patterns.
     * Most mods prefix their translation keys with their mod ID or a variant.
     * 
     * @param key The translation key
     * @return Inferred mod ID or null if cannot be determined
     */
    public static String inferModIdFromTranslationKey(String key) {
        if (key == null || key.isEmpty()) return null;
        
        // Skip vanilla prefixes
        if (isVanillaKeyPrefix(key)) return null;
        
        // Common pattern: mod_id.something or modId.something
        int dotIndex = key.indexOf('.');
        if (dotIndex > 0 && dotIndex < key.length() - 1) {
            String prefix = key.substring(0, dotIndex);
            
            // Skip common vanilla/framework prefixes
            if (isFrameworkPrefix(prefix)) return null;
            
            // Convert camelCase to mod-id format
            String modId = normalizeToModId(prefix);
            
            // Verify this is a known mod
            if (net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(modId).isPresent()) {
                return modId;
            }
            
            // Try with underscores instead of hyphens
            String altModId = modId.replace("-", "_");
            if (net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(altModId).isPresent()) {
                return altModId;
            }
        }
        
        // Pattern: gui.modname_something or key.modname.something
        if (key.startsWith("gui.") || key.startsWith("key.") || key.startsWith("text.") || 
            key.startsWith("option.") || key.startsWith("tag.") || key.startsWith("cloth_config.")) {
            return inferFromSecondaryPrefix(key);
        }
        
        return null;
    }
    
    /**
     * Check if key starts with a vanilla prefix.
     */
    private static boolean isVanillaKeyPrefix(String key) {
        return key.startsWith("block.minecraft.") ||
               key.startsWith("item.minecraft.") ||
               key.startsWith("entity.minecraft.") ||
               key.startsWith("biome.minecraft.") ||
               key.startsWith("effect.minecraft.") ||
               key.startsWith("enchantment.minecraft.") ||
               key.startsWith("key.keyboard.") ||
               key.startsWith("key.mouse.") ||
               key.startsWith("argument.") ||
               key.startsWith("commands.") ||
               key.startsWith("death.") ||
               key.startsWith("chat.") ||
               key.startsWith("narrator.") ||
               key.startsWith("accessibility.");
    }
    
    /**
     * Check if prefix is a framework/common prefix.
     */
    private static boolean isFrameworkPrefix(String prefix) {
        return prefix.equals("gui") || prefix.equals("key") || prefix.equals("text") ||
               prefix.equals("option") || prefix.equals("tag") || prefix.equals("block") ||
               prefix.equals("item") || prefix.equals("entity") || prefix.equals("biome") ||
               prefix.equals("effect") || prefix.equals("enchantment") || prefix.equals("death") ||
               prefix.equals("argument") || prefix.equals("commands") || prefix.equals("chat") ||
               prefix.equals("narrator") || prefix.equals("accessibility") || prefix.equals("container") ||
               prefix.equals("filled_map") || prefix.equals("merchant") || prefix.equals("record") ||
               prefix.equals("stat") || prefix.equals("subtitles") || prefix.equals("title") ||
               prefix.equals("translation") || prefix.equals("tutorial");
    }
    
    /**
     * Infer mod ID from secondary prefix in keys like gui.modname_something.
     */
    private static String inferFromSecondaryPrefix(String key) {
        String[] parts = key.split("\\.");
        if (parts.length >= 2) {
            String secondPart = parts[1];
            
            // Handle underscore pattern: gui.xaero_something -> xaerominimap or similar
            int underscoreIdx = secondPart.indexOf('_');
            if (underscoreIdx > 0) {
                String modPrefix = secondPart.substring(0, underscoreIdx);
                return findModByPrefix(modPrefix);
            }
            
            // Try the second part directly
            return findModByPrefix(secondPart);
        }
        return null;
    }
    
    /**
     * Find a mod by prefix matching.
     */
    private static String findModByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return null;
        
        String normalizedPrefix = prefix.toLowerCase();
        
        // Check for exact match first
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(normalizedPrefix).isPresent()) {
            return normalizedPrefix;
        }
        
        // Search through all mods for prefix match
        for (var container : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            String modId = container.getMetadata().getId();
            if (modId.startsWith(normalizedPrefix) || modId.contains(normalizedPrefix)) {
                return modId;
            }
        }
        
        return null;
    }
    
    /**
     * Normalize a camelCase or mixed string to mod-id format.
     */
    private static String normalizeToModId(String input) {
        if (input == null || input.isEmpty()) return input;
        
        // Convert to lowercase and handle camelCase
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replace("_", "-");
    }
}


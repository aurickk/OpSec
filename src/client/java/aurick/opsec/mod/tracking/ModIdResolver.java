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
}


package aurick.opsec.mod.tracking;

import aurick.opsec.mod.Opsec;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves mod IDs from classes and stack traces.
 * Used to determine which mod registered a translation key or keybind.
 *
 * Uses FabricLoader's mod origin paths to build a lookup map at startup,
 * so only classes from known mods are matched — no hardcoded skip lists needed.
 */
public class ModIdResolver {

    /** Lazily initialized map from code source path -> mod ID */
    private static volatile Map<Path, String> modPathMap;

    private ModIdResolver() {}

    /**
     * Build the mod path map from FabricLoader's mod list.
     * Maps each mod's origin paths (JARs/directories) to its mod ID.
     * Built once lazily and shared across all callers (thread-safe).
     */
    private static Map<Path, String> getModPathMap() {
        if (modPathMap == null) {
            synchronized (ModIdResolver.class) {
                if (modPathMap == null) {
                    Map<Path, String> map = new HashMap<>();
                    for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                        String modId = mod.getMetadata().getId();
                        // Skip opsec and system mods (not real user-installed mods)
                        if ("opsec".equals(modId) || "minecraft".equals(modId)
                                || "java".equals(modId) || "fabricloader".equals(modId)) continue;

                        try {
                            for (Path path : mod.getOrigin().getPaths()) {
                                map.put(path.toAbsolutePath().normalize(), modId);
                            }
                        } catch (Exception e) {
                            // Non-PATH origins (NESTED, UNKNOWN) don't support getPaths()
                            Opsec.LOGGER.debug("[ModIdResolver] Could not get paths for mod {}: {}", modId, e.getMessage());
                        }
                    }
                    modPathMap = Map.copyOf(map);
                }
            }
        }
        return modPathMap;
    }

    /**
     * Get mod ID from a class using its code source location.
     */
    public static String getModIdFromClass(Class<?> clazz) {
        if (clazz == null) return null;

        Path path = getCodeSourcePath(clazz);
        if (path == null) return null;

        return getModPathMap().get(path);
    }

    /**
     * Get mod ID from the current stack trace.
     * Walks up the stack to find the first class that belongs to a known mod.
     */
    public static String getModIdFromStacktrace() {
        Map<Path, String> pathMap = getModPathMap();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stackTrace) {
            try {
                Class<?> clazz = Class.forName(element.getClassName());

                Path path = getCodeSourcePath(clazz);
                if (path == null) continue;

                String modId = pathMap.get(path);
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
     * Get the normalized absolute path to a class's code source (JAR or directory).
     */
    private static Path getCodeSourcePath(Class<?> clazz) {
        try {
            if (clazz.getProtectionDomain() == null) return null;
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) return null;

            return Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | SecurityException e) {
            Opsec.LOGGER.debug("[ModIdResolver] Failed to get code source for {}: {}",
                clazz.getName(), e.getMessage());
            return null;
        }
    }
}

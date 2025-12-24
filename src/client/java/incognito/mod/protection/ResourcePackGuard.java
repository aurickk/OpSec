package incognito.mod.protection;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.detection.TrackPackDetector;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Guards against resource pack-based fingerprinting attacks.
 */
public class ResourcePackGuard {
    
    public static boolean isSuspiciousUrl(String url) {
        return TrackPackDetector.isSuspiciousUrl(url);
    }
    
    public static void clearCache() {
        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
            Path downloadsPath = gameDir.resolve("downloads").normalize();
            
            if (!downloadsPath.startsWith(gameDir)) {
                Incognito.LOGGER.error("[Incognito] Security: downloads path outside game directory");
                return;
            }
            
            if (!Files.exists(downloadsPath)) {
                Incognito.LOGGER.debug("[Incognito] No resource pack cache to clear");
                return;
            }
            
            int deleted = 0;
            int failed = 0;
            var files = Files.walk(downloadsPath)
                .filter(Files::isRegularFile)
                .toList();
            
            for (Path file : files) {
                try {
                    Files.delete(file);
                    deleted++;
                } catch (IOException e) {
                    failed++;
                    Incognito.LOGGER.warn("[Incognito] Failed to delete cache file {}: {}", file.getFileName(), e.getMessage());
                }
            }
            
            if (deleted > 0) {
                Incognito.LOGGER.info("[Incognito] Cleared {} files from resource pack cache{}", deleted, 
                    failed > 0 ? " (" + failed + " failed)" : "");
            }
        } catch (IOException e) {
            Incognito.LOGGER.error("[Incognito] Failed to clear resource pack cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Clear all resource pack caches for all accounts.
     */
    public static void clearAllCaches() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        Path downloadsPath = gameDir.resolve("downloads").normalize();

        if (!downloadsPath.startsWith(gameDir)) {
            Incognito.LOGGER.error("[Incognito] Security: downloads path outside game directory");
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.DANGER, "Security error: invalid cache path");
            return;
        }

        if (!Files.exists(downloadsPath)) {
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.INFO, "No resource pack cache folder found.");
            return;
        }

        try {
            int deleted = 0;

            for (Path p : Files.walk(downloadsPath).sorted(Comparator.reverseOrder()).toList()) {
                if (p.equals(downloadsPath)) continue;
                try {
                    if (Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    Incognito.LOGGER.warn("[Incognito] Failed to delete cache entry {}: {}", p.getFileName(), e.getMessage());
                }
            }

            Incognito.LOGGER.info("[Incognito] Cleared {} entries from resource pack cache (all accounts)", deleted);
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.SUCCESS,
                "Cleared resource pack cache for all accounts (" + deleted + " entries).");
        } catch (IOException e) {
            Incognito.LOGGER.error("[Incognito] Failed to clear resource pack cache (all accounts)", e);
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.DANGER, "Failed to clear resource pack cache (see logs).");
        }
    }
    
    public static void onServerJoin() {
        TrackPackDetector.reset();
    }
}

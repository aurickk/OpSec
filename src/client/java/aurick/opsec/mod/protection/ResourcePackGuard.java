package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.TrackPackDetector;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Manages resource pack cache and provides utilities for cache clearing.
 * Delegates suspicious URL detection to TrackPackDetector.
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
                Opsec.LOGGER.error("[OpSec] Security: downloads path outside game directory");
                return;
            }
            
            if (!Files.exists(downloadsPath)) {
                Opsec.LOGGER.debug("[OpSec] No resource pack cache to clear");
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
                    Opsec.LOGGER.warn("[OpSec] Failed to delete cache file {}: {}", file.getFileName(), e.getMessage());
                }
            }
            
            if (deleted > 0) {
                Opsec.LOGGER.info("[OpSec] Cleared {} files from resource pack cache{}", deleted, 
                    failed > 0 ? " (" + failed + " failed)" : "");
            }
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to clear resource pack cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Clear all resource pack caches for all accounts.
     */
    public static void clearAllCaches() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        Path downloadsPath = gameDir.resolve("downloads").normalize();

        if (!downloadsPath.startsWith(gameDir)) {
            Opsec.LOGGER.error("[OpSec] Security: downloads path outside game directory");
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
                    Opsec.LOGGER.warn("[OpSec] Failed to delete cache entry {}: {}", p.getFileName(), e.getMessage());
                }
            }

            Opsec.LOGGER.info("[OpSec] Cleared {} entries from resource pack cache (all accounts)", deleted);
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.SUCCESS,
                "Cleared resource pack cache for all accounts (" + deleted + " entries).");
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to clear resource pack cache (all accounts)", e);
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.DANGER, "Failed to clear resource pack cache (see logs).");
        }
    }
    
    public static void onServerJoin() {
        TrackPackDetector.reset();
    }
}

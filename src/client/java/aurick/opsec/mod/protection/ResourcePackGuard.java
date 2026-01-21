package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.TrackPackDetector;
import aurick.opsec.mod.mixin.client.DownloadedPackSourceAccessor;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.DownloadedPackSource;

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
     * Clears both 'downloads' and 'server-resource-packs' folders,
     * and resets the in-memory download state.
     */
    public static void clearAllCaches() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        int totalDeleted = 0;
        
        // Clear downloads folder (new cache location)
        totalDeleted += clearCacheFolder(gameDir, "downloads");
        
        // Clear server-resource-packs folder (legacy/alternative cache location)
        totalDeleted += clearCacheFolder(gameDir, "server-resource-packs");
        
        // Try to reset the in-memory download state
        boolean memoryCleared = clearDownloadQueueState();

        if (totalDeleted > 0 || memoryCleared) {
            String message = "Cleared resource pack cache";
            if (totalDeleted > 0) {
                message += " (" + totalDeleted + " files)";
            }
            if (memoryCleared) {
                message += " and reset download state";
            }
            Opsec.LOGGER.info("[OpSec] {}", message);
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.SUCCESS, message + ".");
        } else {
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.INFO, "No resource pack cache to clear.");
        }
    }
    
    /**
     * Attempts to clear the in-memory download queue state.
     * This simulates what happens when disconnecting from a server.
     * @return true if successfully cleared, false otherwise
     */
    private static boolean clearDownloadQueueState() {
        try {
            Minecraft mc = Minecraft.getInstance();
            DownloadedPackSource packSource = ((MinecraftAccessor) mc).opsec$getDownloadedPackSource();
            
            if (packSource != null) {
                ((DownloadedPackSourceAccessor) packSource).opsec$invokeCleanupAfterDisconnect();
                Opsec.LOGGER.debug("[OpSec] Cleared download queue state via cleanupAfterDisconnect");
                return true;
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Could not clear download queue state: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Helper to clear a specific cache folder.
     * @return number of entries deleted
     */
    private static int clearCacheFolder(Path gameDir, String folderName) {
        Path cachePath = gameDir.resolve(folderName).normalize();

        if (!cachePath.startsWith(gameDir)) {
            Opsec.LOGGER.error("[OpSec] Security: {} path outside game directory", folderName);
            return 0;
        }

        if (!Files.exists(cachePath)) {
            return 0;
        }

        int deleted = 0;
        try {
            for (Path p : Files.walk(cachePath).sorted(Comparator.reverseOrder()).toList()) {
                if (p.equals(cachePath)) continue;
                try {
                    if (Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    Opsec.LOGGER.warn("[OpSec] Failed to delete cache entry {}: {}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to walk cache folder {}: {}", folderName, e.getMessage());
        }
        
        return deleted;
    }
    
    public static void onServerJoin() {
        TrackPackDetector.reset();
    }
}

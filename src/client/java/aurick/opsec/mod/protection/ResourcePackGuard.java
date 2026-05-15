package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
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
 */
public class ResourcePackGuard {

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

        Opsec.LOGGER.info("[OpSec] Clear pack cache: {} files removed, memoryCleared={}", totalDeleted, memoryCleared);
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
}

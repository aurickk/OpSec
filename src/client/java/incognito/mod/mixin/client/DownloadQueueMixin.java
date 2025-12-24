package incognito.mod.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Mixin to isolate resource pack cache per-account at download time.
 * <p>
 * This prevents servers from fingerprinting users across accounts by storing
 * each account's resource pack cache in a separate folder. This hooks ALL
 * Path.resolve(String) calls in DownloadQueue, ensuring isolation works even
 * when switching accounts without restarting.
 * <p>
 * Based on approach from Meteor Client / LiquidBounce:
 * @see <a href="https://github.com/MeteorDevelopment/meteor-client/commit/e241e7d">Meteor Client commit</a>
 * @see <a href="https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloader.java">LiquidBounce MixinDownloader</a>
 */
@Mixin(DownloadQueue.class)
public class DownloadQueueMixin {
    @Unique
    private static boolean incognito$loggedOnce = false;
    
    @Unique
    private static UUID incognito$lastLoggedAccountId = null;

    /**
     * Intercept ALL Path.resolve(String) calls in DownloadQueue.
     * When a UUID is being resolved to a path, we prepend the current account's UUID.
     * <p>
     * This uses "*" to target all methods, ensuring we catch the path resolution
     * regardless of the method name (which may vary between Minecraft versions).
     */
    @ModifyExpressionValue(
        method = "*",
        at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"),
        require = 0  // Graceful fallback for version compatibility
    )
    private Path incognito$isolatePackPath(Path original) {
        if (!IncognitoConfig.getInstance().shouldIsolatePackCache()) {
            return original;
        }

        // Guard against early initialization when Minecraft/User isn't ready yet
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return original;
        }
        
        var user = mc.getUser();
        if (user == null) {
            return original;
        }

        UUID accountId = user.getProfileId();
        if (accountId == null) {
            if (!incognito$loggedOnce) {
                Incognito.LOGGER.warn("[Incognito] Failed to isolate resource pack cache - account UUID is null");
                incognito$loggedOnce = true;
            }
            return original;
        }

        // Get the parent directory and the pack ID that was resolved
        Path parent = original.getParent();
        String packIdStr = original.getFileName().toString();
        
        // Check if this looks like a UUID path segment (pack ID)
        if (parent != null && isUuidLike(packIdStr)) {
            Path isolatedPath = parent.resolve(accountId.toString()).resolve(packIdStr);
            
            // Log once per account
            if (incognito$lastLoggedAccountId == null || !incognito$lastLoggedAccountId.equals(accountId)) {
                incognito$lastLoggedAccountId = accountId;
                Incognito.LOGGER.info("[Incognito] Resource pack cache isolated per-account: {}", 
                    parent.resolve(accountId.toString()));
            }
            
            return isolatedPath;
        }

        return original;
    }
    
    /**
     * Check if a string looks like a UUID (pack ID format).
     * This helps us only modify path resolutions that are for pack storage.
     */
    @Unique
    private static boolean isUuidLike(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // UUID format: 8-4-4-4-12 hex characters with dashes, or 32 hex without
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            // Check for UUID without dashes (32 hex chars)
            return str.length() == 32 && str.matches("[0-9a-fA-F]+");
        }
    }
}


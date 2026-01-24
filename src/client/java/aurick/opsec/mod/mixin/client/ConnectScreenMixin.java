package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.TrackPackDetector;
import aurick.opsec.mod.protection.ResourcePackGuard;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.tracking.ModRegistry;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resets detection state when connecting to a new server.
 * Also ensures profile keys are available for ON_DEMAND chat signing mode.
 */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void opsec$resetState(CallbackInfo ci) {
        TrackPackDetector.reset();
        ResourcePackGuard.onServerJoin();
        TranslationProtectionHandler.clearCache();
        ModRegistry.clearServerPackKeys();  // Clear server pack whitelist for new server
        
        // Ensure profile keys are available for ON_DEMAND mode
        // This runs BEFORE the login phase, so keys will be ready if server requires secure chat
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (settings.isOnDemand()) {
            opsec$ensureProfileKeysReady();
        }
    }
    
    /**
     * Ensures profile keys are initialized before connecting.
     * For ON_DEMAND mode, keys must be available before the login phase
     * in case the server requires secure chat.
     */
    @Unique
    private static void opsec$ensureProfileKeysReady() {
        try {
            Minecraft mc = Minecraft.getInstance();
            User user = mc.getUser();
            
            if (user == null || user.getAccessToken() == null || user.getAccessToken().isEmpty()) {
                Opsec.LOGGER.debug("[OpSec] Cannot ensure profile keys - no valid session");
                return;
            }
            
            MinecraftAccessor accessor = (MinecraftAccessor) mc;
            
            // Create fresh authentication services and profile key manager
            YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(mc.getProxy());
            UserApiService userApiService = authService.createUserApiService(user.getAccessToken());
            accessor.opsec$setUserApiService(userApiService);
            
            ProfileKeyPairManager profileKeyPairManager = ProfileKeyPairManager.create(
                    userApiService,
                    user,
                    mc.gameDirectory.toPath()
            );
            accessor.opsec$setProfileKeyPairManager(profileKeyPairManager);
            
            Opsec.LOGGER.debug("[OpSec] Profile keys ready for ON_DEMAND mode");
        } catch (Exception e) {
            Opsec.LOGGER.warn("[OpSec] Could not initialize profile keys: {}", e.getMessage());
        }
    }
}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.detection.TrackPackDetector;
import aurick.opsec.mod.protection.ResourcePackGuard;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.tracking.ModTracker;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resets detection state when connecting to a new server.
 */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void opsec$resetState(CallbackInfo ci) {
        TrackPackDetector.reset();
        ResourcePackGuard.onServerJoin();
        TranslationProtectionHandler.clearCache();
        ModTracker.clearServerPackKeys();  // Clear server pack whitelist for new server
    }
}

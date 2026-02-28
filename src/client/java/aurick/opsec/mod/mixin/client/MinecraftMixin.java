package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.detection.ExploitContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Core Minecraft client hooks for:
 * - Telemetry blocking
 * - Exploit context cleanup (detection is in screen-specific mixins)
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    
    @Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
    private void opsec$disableTelemetry(CallbackInfoReturnable<Boolean> info) {
        if (OpsecConfig.getInstance().shouldDisableTelemetry()) {
            Opsec.LOGGER.debug("[OpSec] Blocking telemetry");
            info.setReturnValue(false);
        }
    }
    
    /**
     * Handle exploit context cleanup when screens change.
     * Context DETECTION is handled by screen-specific mixins (SignEditScreenMixin, etc.)
     * This only handles CLEANUP when leaving exploitable screens.
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void opsec$onSetScreen(Screen screen, CallbackInfo ci) {
        // Only handle cleanup - detection is in screen constructor mixins
        if (screen == null && ExploitContext.isInExploitableContext()) {
            // Closing to null while in exploitable context - defer cleanup
            // This protects packet serialization that happens after screen closes
            opsec$scheduleContextCleanup();
        } else if (screen != null && !opsec$isExploitableScreen(screen)) {
            // Switching to a non-exploitable screen - clear immediately
            ExploitContext.exitContext();
        }
        // If switching to another exploitable screen, context will be set by that screen's mixin
    }
    
    /**
     * Check if a screen is one of the exploitable screen types.
     */
    @Unique
    private boolean opsec$isExploitableScreen(Screen screen) {
        return screen instanceof AbstractSignEditScreen 
            || screen instanceof AnvilScreen;
    }
    
    /**
     * Schedule context cleanup after a brief delay.
     * This ensures packet serialization completes before context is cleared.
     *
     * Captures the current context source at deferral time and only clears
     * if it still matches — prevents a race where a new exploitable screen
     * opens before the deferred cleanup fires.
     */
    @Unique
    private void opsec$scheduleContextCleanup() {
        PrivacyLogger.ExploitSource contextAtClose = ExploitContext.getSource();
        Minecraft.getInstance().execute(() -> {
            if (ExploitContext.getSource() == contextAtClose) {
                ExploitContext.exitContext();
            }
        });
    }
}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.ExploitContext;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets exploit context at the very start of anvil screen construction.
 * Uses static injection to run BEFORE super() and any content processing.
 *
 * This ensures keybind/translation resolution that happens during
 * screen initialization is already protected.
 *
 * Context cleanup is handled by {@link MinecraftMixin#opsec$onSetScreen}
 * with deferred scheduling to avoid premature cleanup during packet serialization.
 */
@Mixin(AnvilScreen.class)
public class AnvilScreenMixin {
    
    /**
     * Enter ANVIL context at constructor HEAD (before super()).
     * Must be static for injection before super() invocation.
     */
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void opsec$enterAnvilContext(CallbackInfo ci) {
        ExploitContext.enterContext(PrivacyLogger.ExploitSource.ANVIL);
    }
}


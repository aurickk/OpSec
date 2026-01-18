package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.ExploitContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets exploit context at the very start of sign screen construction.
 * Uses static injection to run BEFORE super() and any content processing.
 * 
 * This ensures keybind/translation resolution that happens during
 * screen initialization is already protected.
 */
@Mixin(targets = {
    "net.minecraft.client.gui.screens.inventory.SignEditScreen",
    "net.minecraft.client.gui.screens.inventory.HangingSignEditScreen"
})
public class SignEditScreenMixin {
    
    /**
     * Enter SIGN context at constructor HEAD (before super()).
     * Must be static for injection before super() invocation.
     */
    @Inject(method = "<init>", at = @At("HEAD"), require = 0)
    private static void opsec$enterSignContext(CallbackInfo ci) {
        ExploitContext.enterContext(PrivacyLogger.ExploitSource.SIGN);
    }
}


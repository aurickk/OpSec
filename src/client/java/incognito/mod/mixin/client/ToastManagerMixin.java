package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide the "Chat messages can't be verified" warning toast.
 * 
 * This toast appears when connecting to servers that don't enforce chat signing.
 * When chat signing is intentionally disabled, this warning is unnecessary and annoying.
 * 
 * Based on No Chat Reports by Aizistral-Studios:
 * https://github.com/Aizistral-Studios/No-Chat-Reports
 */
@Mixin(ToastManager.class)
public class ToastManagerMixin {
    
    /**
     * Block the unsecure server warning toast.
     */
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void incognito$blockWarningToast(Toast toast, CallbackInfo info) {
        if (IncognitoConfig.getInstance().shouldHideWarningToast()) {
            if (toast instanceof SystemToast systemToast) {
                if (systemToast.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) {
                    Incognito.LOGGER.debug("[Incognito] Blocking UNSECURE_SERVER_WARNING toast");
                    info.cancel();
                }
            }
        }
    }
}

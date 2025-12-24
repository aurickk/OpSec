package incognito.mod.mixin.client;

import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to hide the gray bar indicator beside system messages.
 * 
 * When chat signing is disabled, all messages may appear as system messages
 * with a gray bar on the left. This removes that indicator for cleaner chat.
 * 
 * Based on No Chat Reports by Aizistral-Studios:
 * https://github.com/Aizistral-Studios/No-Chat-Reports
 */
@Mixin(GuiMessageTag.class)
public class GuiMessageTagMixin {
    
    /**
     * Hide system message indicator by returning null.
     * A null GuiMessageTag means no indicator is shown.
     */
    @Inject(method = "system", at = @At("HEAD"), cancellable = true)
    private static void incognito$hideSystemIndicator(CallbackInfoReturnable<GuiMessageTag> info) {
        if (IncognitoConfig.getInstance().shouldHideSystemMsgIndicators()) {
            info.setReturnValue(null);
        }
    }
    
    /**
     * Hide singleplayer system message indicator as well.
     */
    @Inject(method = "systemSinglePlayer", at = @At("HEAD"), cancellable = true)
    private static void incognito$hideSingleplayerSystemIndicator(CallbackInfoReturnable<GuiMessageTag> info) {
        if (IncognitoConfig.getInstance().shouldHideSystemMsgIndicators()) {
            info.setReturnValue(null);
        }
    }
}


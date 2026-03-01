package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.ExploitContext;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets exploit context at the very start of sign screen construction.
 *
 * IMPORTANT: We target AbstractSignEditScreen because that's where
 * the messages[] array is populated by calling component.getString().
 * Setting context HERE ensures LanguageMixin blocks translation resolution
 * before the component is converted to a string.
 *
 * Context cleanup is handled by {@link MinecraftMixin#opsec$onSetScreen}
 * with deferred scheduling to avoid premature cleanup during packet serialization.
 */
@Mixin(AbstractSignEditScreen.class)
public class SignEditScreenMixin {
    
    /**
     * Enter SIGN context at AbstractSignEditScreen constructor HEAD.
     * This runs BEFORE messages[] is populated, so when getString() is called
     * on TranslatableContents, LanguageMixin will block resolution.
     */
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void opsec$enterSignContext(CallbackInfo ci) {
        ExploitContext.enterContext(PrivacyLogger.ExploitSource.SIGN);
    }
}


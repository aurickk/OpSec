package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.ExploitContext;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 * After entering context, we scan sign content. If no exploitable content
 * (TranslatableContents/KeybindContents) is found, alerts are suppressed
 * to avoid false positives for benign sign screens (e.g., search prompts).
 * Protection (blocking/spoofing) continues regardless.
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
     *
     * Also scans sign content to suppress alerts for benign signs.
     */
    @Inject(method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;ZZ)V", at = @At("HEAD"))
    private static void opsec$enterSignContext(SignBlockEntity sign, boolean isFrontText, boolean isFiltered, CallbackInfo ci) {
        TranslationProtectionHandler.clearDedup();
        ExploitContext.enterContext(PrivacyLogger.ExploitSource.SIGN);

        // Suppress alerts if sign has no exploitable content
        boolean hasExploitable = false;
        for (boolean side : new boolean[]{true, false}) {
            SignText text = sign.getText(side);
            for (int i = 0; i < 4; i++) {
                if (opsec$hasExploitableContent(text.getMessage(i, false))) {
                    hasExploitable = true;
                    break;
                }
            }
            if (hasExploitable) break;
        }
        ExploitContext.setSuppressAlerts(!hasExploitable);
    }

    /**
     * Recursively check if a component tree contains TranslatableContents or KeybindContents.
     */
    @Unique
    private static boolean opsec$hasExploitableContent(Component component) {
        if (component == null) return false;
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents || contents instanceof KeybindContents) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (opsec$hasExploitableContent(sibling)) return true;
        }
        return false;
    }
}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.detection.ExploitContext;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets exploit context at the very start of anvil screen construction.
 * Uses static injection to run BEFORE super() and any content processing.
 *
 * After entering context, we scan the title and slot items. If no exploitable
 * content (TranslatableContents/KeybindContents) is found, alerts are suppressed
 * to avoid false positives for benign anvil screens (e.g., search prompts).
 * Protection (blocking/spoofing) continues regardless.
 *
 * Context cleanup is handled by {@link MinecraftMixin#opsec$onSetScreen}
 * with deferred scheduling to avoid premature cleanup during packet serialization.
 */
@Mixin(AnvilScreen.class)
public class AnvilScreenMixin {

    /**
     * Enter ANVIL context at constructor HEAD (before super()).
     * Must be static for injection before super() invocation.
     *
     * Also scans title and items to suppress alerts for benign anvils.
     */
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void opsec$enterAnvilContext(AnvilMenu menu, Inventory playerInventory, Component title, CallbackInfo ci) {
        TranslationProtectionHandler.clearDedup();
        ExploitContext.enterContext(PrivacyLogger.ExploitSource.ANVIL);

        // Check title for exploitable content — if found, keep alerts enabled
        if (opsec$hasExploitableContent(title)) {
            return;
        }

        // Check slot items for exploitable content in hover names
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && opsec$hasExploitableContent(stack.getHoverName())) {
                return;
            }
        }

        // No exploitable content found — suppress alerts
        ExploitContext.setSuppressAlerts(true);
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

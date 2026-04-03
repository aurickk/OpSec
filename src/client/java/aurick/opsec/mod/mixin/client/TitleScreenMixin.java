package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.JarIntegrityChecker;
import aurick.opsec.mod.config.TamperWarningScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Polls the jar integrity check on the title screen and shows the tamper
 * warning as soon as the async check completes with a mismatch.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique private boolean opsec$integrityHandled = false;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void opsec$pollIntegrityCheck(CallbackInfo ci) {
        if (opsec$integrityHandled || !JarIntegrityChecker.isCheckComplete()) {
            return;
        }
        opsec$integrityHandled = true;
        if (this.minecraft != null && JarIntegrityChecker.isTamperDetected()) {
            JarIntegrityChecker.markShown();
            this.minecraft.setScreen(new TamperWarningScreen(this));
        }
    }
}

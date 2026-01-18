package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an OpSec settings button to the multiplayer server list screen.
 * Button is positioned at the top-right of the footer area.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    
    @Unique private static final int BUTTON_WIDTH = 70;
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int MARGIN = 7;
    
    @Unique private Button opsec$settingsButton;
    
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void opsec$addSettingsButton(CallbackInfo ci) {
        this.opsec$settingsButton = Button.builder(
            Component.literal("OpSec"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new OpsecConfigScreen(this));
                }
            }
        )
        .tooltip(Tooltip.create(Component.literal("Open OpSec settings")))
        .build();
        
        opsec$updateButtonPosition();
        this.addRenderableWidget(this.opsec$settingsButton);
    }
    
    @Inject(method = "repositionElements", at = @At("TAIL"), require = 0)
    private void opsec$onRepositionElements(CallbackInfo ci) {
        opsec$updateButtonPosition();
    }
    
    @Unique
    private void opsec$updateButtonPosition() {
        if (this.opsec$settingsButton != null) {
            this.opsec$settingsButton.setX(this.width - BUTTON_WIDTH - MARGIN);
            this.opsec$settingsButton.setY(this.height - 56);
            this.opsec$settingsButton.setWidth(BUTTON_WIDTH);
            this.opsec$settingsButton.setHeight(BUTTON_HEIGHT);
        }
    }
}


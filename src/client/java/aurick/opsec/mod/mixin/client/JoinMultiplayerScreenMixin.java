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
 * Button is positioned in the header area, aligned with the server list's left edge.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_PADDING = 8;
    /** Half of ServerSelectionList.getRowWidth() (305), used to align with the list's left edge. */
    @Unique private static final int SERVER_LIST_HALF_ROW_WIDTH = 152;
    
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
    
    //? if >=1.21.9 {
    /*@Inject(method = "repositionElements", at = @At("TAIL"))
    private void opsec$onRepositionElements(CallbackInfo ci) {
        opsec$updateButtonPosition();
    }
    *///?}
    
    @Unique
    private void opsec$updateButtonPosition() {
        if (this.opsec$settingsButton != null) {
            int textWidth = this.font.width(this.opsec$settingsButton.getMessage());
            this.opsec$settingsButton.setX(this.width / 2 - SERVER_LIST_HALF_ROW_WIDTH);
            this.opsec$settingsButton.setY(6);
            this.opsec$settingsButton.setWidth(textWidth + BUTTON_PADDING);
            this.opsec$settingsButton.setHeight(BUTTON_HEIGHT);
        }
    }
}


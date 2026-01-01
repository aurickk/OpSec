package incognito.mod.mixin.client;

import incognito.mod.config.IncognitoConfigScreen;
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
 * Adds an Incognito settings button to the multiplayer server list screen.
 * Button is positioned at the top-right of the footer area.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    
    @Unique private static final int BUTTON_WIDTH = 70;
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int MARGIN = 7;
    
    @Unique private Button incognito$settingsButton;
    
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void incognito$addSettingsButton(CallbackInfo ci) {
        this.incognito$settingsButton = Button.builder(
            Component.literal("Incognito"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new IncognitoConfigScreen(this));
                }
            }
        )
        .tooltip(Tooltip.create(Component.literal("Open Incognito settings")))
        .build();
        
        incognito$updateButtonPosition();
        this.addRenderableWidget(this.incognito$settingsButton);
    }
    
    @Inject(method = "repositionElements", at = @At("TAIL"), require = 0)
    private void incognito$onRepositionElements(CallbackInfo ci) {
        incognito$updateButtonPosition();
    }
    
    @Unique
    private void incognito$updateButtonPosition() {
        if (this.incognito$settingsButton != null) {
            this.incognito$settingsButton.setX(this.width - BUTTON_WIDTH - MARGIN);
            this.incognito$settingsButton.setY(this.height - 56);
            this.incognito$settingsButton.setWidth(BUTTON_WIDTH);
            this.incognito$settingsButton.setHeight(BUTTON_HEIGHT);
        }
    }
}


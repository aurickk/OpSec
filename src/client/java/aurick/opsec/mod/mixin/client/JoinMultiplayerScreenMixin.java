package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfigScreen;
import aurick.opsec.mod.config.UpdateChecker;
import aurick.opsec.mod.config.UpdateScreen;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an OpSec settings button to the multiplayer server list screen.
 * Aligned with the list's left edge, or its right edge when Wurst is installed
 * (Wurst's own "Last Server" button occupies the top-left header slot).
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_PADDING = 8;
    /** Half of ServerSelectionList.getRowWidth() (305), used to align with the list's left edge. */
    @Unique private static final int SERVER_LIST_HALF_ROW_WIDTH = 152;
    /** Wurst parks its "Last Server" button at the top-left header slot; right-align to dodge it. */
    @Unique private static final boolean WURST_LOADED = FabricLoader.getInstance().isModLoaded("wurst");
    
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
        .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_SETTINGS_TOOLTIP)))
        .build();
        
        opsec$updateButtonPosition();
        this.addRenderableWidget(this.opsec$settingsButton);

        // Show update screen if an update is available
        if (this.minecraft != null && UpdateChecker.isUpdateAvailable()) {
            UpdateChecker.markShown();
            this.minecraft.setScreen(new UpdateScreen(this));
        }
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
            int buttonWidth = textWidth + BUTTON_PADDING;
            int x = WURST_LOADED
                ? this.width / 2 + SERVER_LIST_HALF_ROW_WIDTH - buttonWidth
                : this.width / 2 - SERVER_LIST_HALF_ROW_WIDTH;
            this.opsec$settingsButton.setX(x);
            this.opsec$settingsButton.setY(6);
            this.opsec$settingsButton.setWidth(buttonWidth);
            //? if >=1.20.2 {
            this.opsec$settingsButton.setHeight(BUTTON_HEIGHT);
            //?}
            // 1.20.1: AbstractWidget.setHeight didn't exist; Button defaults to height=20
            // (matches BUTTON_HEIGHT) — height isn't user-resizable on this era.
        }
    }
}


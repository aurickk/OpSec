package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Native Minecraft-style screen that notifies the user when a new version of OpSec is available.
 * Provides three options: Download (opens browser), Don't Show Again (persists to config), Cancel (session dismiss).
 *
 * All text is rendered via StringWidget (not raw drawCenteredString) for compatibility
 * with the stratum-based rendering pipeline in 1.21.9+.
 */
public class UpdateScreen extends Screen {

    private final Screen parent;

    public UpdateScreen(Screen parent) {
        super(Component.literal("OpSec Update Available"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Text labels (using StringWidget for cross-version rendering compatibility)
        addCenteredStringWidget(this.title, centerX, centerY - 50);
        addCenteredStringWidget(Component.literal("A new version of OpSec is available!"), centerX, centerY - 28);
        addCenteredStringWidget(Component.literal("\u00A7cCurrent: " + UpdateChecker.getCurrentVersion()), centerX, centerY - 14);
        addCenteredStringWidget(Component.literal("\u00A7aLatest: " + UpdateChecker.getLatestVersion()), centerX, centerY);

        // Buttons stacked vertically, centered horizontally
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonSpacing = 24;
        int firstButtonY = centerY + 15;

        // Download button (green text)
        this.addRenderableWidget(Button.builder(Component.literal("\u00A7aDownload"), button -> {
            try {
                Desktop.getDesktop().browse(URI.create(UpdateChecker.getReleaseUrl()));
            } catch (Exception e) {
                Opsec.LOGGER.warn("[OpSec] Failed to open release URL: {}", e.getMessage());
            }
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, firstButtonY, buttonWidth, buttonHeight).build());

        // Don't Show Again button (red text)
        this.addRenderableWidget(Button.builder(Component.literal("\u00A7cDon't Show Again"), button -> {
            OpsecConfig.getInstance().getSettings().setDismissUpdateNotification(true);
            OpsecConfig.getInstance().save();
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, firstButtonY + buttonSpacing, buttonWidth, buttonHeight).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, firstButtonY + buttonSpacing * 2, buttonWidth, buttonHeight).build());
    }

    private void addCenteredStringWidget(Component text, int centerX, int y) {
        int textWidth = this.font.width(text);
        this.addRenderableWidget(new StringWidget(centerX - textWidth / 2, y, textWidth, 10, text, this.font));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

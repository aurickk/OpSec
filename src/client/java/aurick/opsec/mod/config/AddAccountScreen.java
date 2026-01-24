package aurick.opsec.mod.config;

import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.accounts.SessionAccount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Screen for adding a new account via session token.
 */
public class AddAccountScreen extends Screen {
    
    private final Screen parent;
    private EditBox tokenInput;
    private EditBox refreshTokenInput;
    private Button addButton;
    private Button cancelButton;
    private Component statusMessage;
    private boolean isValidating = false;
    private StringWidget titleLabel;
    private StringWidget statusLabel;
    
    public AddAccountScreen(Screen parent) {
        super(Component.literal("Add Account"));
        this.parent = parent;
        this.statusMessage = Component.literal("");
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Title label - always visible (centered manually)
        int titleWidth = this.font.width(this.title);
        this.titleLabel = new StringWidget(centerX - titleWidth / 2, centerY - 75, titleWidth, 20, this.title, this.font);
        this.addRenderableWidget(this.titleLabel);
        
        // Token input field
        this.tokenInput = new EditBox(
                this.font,
                centerX - 150,
                centerY - 50,
                300,
                20,
                Component.literal("Session Token")
        );
        this.tokenInput.setMaxLength(2000);
        this.tokenInput.setHint(Component.literal("Session/Access token (required)"));
        this.addRenderableWidget(this.tokenInput);
        
        // Refresh token input field (optional)
        this.refreshTokenInput = new EditBox(
                this.font,
                centerX - 150,
                centerY - 20,
                300,
                20,
                Component.literal("Refresh Token")
        );
        this.refreshTokenInput.setMaxLength(2000);
        this.refreshTokenInput.setHint(Component.literal("Refresh token (optional, for auto-renewal)"));
        this.addRenderableWidget(this.refreshTokenInput);
        
        // Status label - updated dynamically (centered manually, width updated in render)
        this.statusLabel = new StringWidget(centerX - 150, centerY + 45, 300, 20, Component.literal(""), this.font);
        this.addRenderableWidget(this.statusLabel);
        
        // Add button
        this.addButton = Button.builder(Component.literal("Add Account"), button -> {
            addAccount();
        }).bounds(centerX - 105, centerY + 15, 100, 20).build();
        this.addRenderableWidget(this.addButton);
        
        // Cancel button
        this.cancelButton = Button.builder(Component.literal("Cancel"), button -> {
            this.onClose();
        }).bounds(centerX + 5, centerY + 15, 100, 20).build();
        this.addRenderableWidget(this.cancelButton);
        
        // Focus the token input
        this.setInitialFocus(this.tokenInput);
    }
    
    private void addAccount() {
        String token = tokenInput.getValue().trim();
        String refreshToken = refreshTokenInput.getValue().trim();
        
        if (token.isEmpty()) {
            statusMessage = Component.literal("\u00A7cPlease enter a session token");
            if (statusLabel != null) {
                statusLabel.setMessage(statusMessage);
            }
            return;
        }
        
        if (isValidating) {
            return;
        }
        
        isValidating = true;
        addButton.active = false;
        statusMessage = Component.literal("\u00A7eValidating token...");
        if (statusLabel != null) {
            statusLabel.setMessage(statusMessage);
        }
        
        // Validate in background thread
        CompletableFuture.runAsync(() -> {
            SessionAccount account = refreshToken.isEmpty() 
                    ? new SessionAccount(token)
                    : new SessionAccount(token, refreshToken);
            boolean valid = account.fetchInfo();
            
            // Update UI on main thread
            Minecraft.getInstance().execute(() -> {
                isValidating = false;
                addButton.active = true;
                
                if (valid) {
                    // Add to account manager
                    AccountManager.getInstance().add(account);
                    
                    // Login immediately
                    if (account.login()) {
                        AccountManager.getInstance().setActiveAccountUuid(account.getUuid());
                    }
                    
                    String successMsg = "\u00A7aAdded: " + account.getUsername();
                    if (account.hasRefreshToken()) {
                        successMsg += " \u00A77(with refresh)";
                    }
                    statusMessage = Component.literal(successMsg);
                    if (statusLabel != null) {
                        statusLabel.setMessage(statusMessage);
                    }
                    
                    // Return to config screen after short delay
                    Minecraft.getInstance().execute(() -> {
                        this.onClose();
                    });
                } else {
                    statusMessage = Component.literal("\u00A7cInvalid or expired token");
                    if (statusLabel != null) {
                        statusLabel.setMessage(statusMessage);
                    }
                }
            });
        });
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.6
        /*this.renderBackground(graphics, mouseX, mouseY, partialTick);*/
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // Update status label position to keep it centered
        if (statusLabel != null && statusMessage != null && !statusMessage.getString().isEmpty()) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int statusWidth = this.font.width(statusMessage);
            statusLabel.setX(centerX - statusWidth / 2);
            statusLabel.setY(centerY + 45);
            statusLabel.setWidth(statusWidth);
        }
    }
    
    @Override
    public void onClose() {
        // Get the parent's parent (the screen before OpsecConfigScreen)
        Screen grandParent = null;
        if (parent instanceof OpsecConfigScreen configScreen) {
            grandParent = configScreen.getParent();
        }
        // Create a fresh config screen with the Accounts tab selected (index 4)
        //? if >=1.21.6 {
        this.minecraft.setScreen(new OpsecConfigScreen(grandParent, 4, 0));
        //?} else
        /*this.minecraft.setScreen(new OpsecConfigScreen(grandParent, 4));*/
    }
    
    // keyPressed signature changed in 1.21.9 to use KeyEvent
    //? if <1.21.9 {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key to submit
        if (keyCode == 257 && !isValidating) { // GLFW_KEY_ENTER
            addAccount();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}
}

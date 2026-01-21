package aurick.opsec.mod.config;

import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.accounts.SessionAccount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Screen for adding a new account via session token.
 */
public class AddAccountScreen extends Screen {
    
    private final Screen parent;
    private EditBox tokenInput;
    private Button addButton;
    private Button cancelButton;
    private Component statusMessage;
    private boolean isValidating = false;
    
    public AddAccountScreen(Screen parent) {
        super(Component.literal("Add Account"));
        this.parent = parent;
        this.statusMessage = Component.literal("\u00A77Paste your session token below");
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Token input field
        this.tokenInput = new EditBox(
                this.font,
                centerX - 150,
                centerY - 30,
                300,
                20,
                Component.literal("Session Token")
        );
        this.tokenInput.setMaxLength(2000);
        this.tokenInput.setHint(Component.literal("Paste session token here..."));
        this.addRenderableWidget(this.tokenInput);
        
        // Add button
        this.addButton = Button.builder(Component.literal("Add Account"), button -> {
            addAccount();
        }).bounds(centerX - 105, centerY + 10, 100, 20).build();
        this.addRenderableWidget(this.addButton);
        
        // Cancel button
        this.cancelButton = Button.builder(Component.literal("Cancel"), button -> {
            this.onClose();
        }).bounds(centerX + 5, centerY + 10, 100, 20).build();
        this.addRenderableWidget(this.cancelButton);
        
        // Focus the token input
        this.setInitialFocus(this.tokenInput);
    }
    
    private void addAccount() {
        String token = tokenInput.getValue().trim();
        
        if (token.isEmpty()) {
            statusMessage = Component.literal("\u00A7cPlease enter a session token");
            return;
        }
        
        if (isValidating) {
            return;
        }
        
        isValidating = true;
        addButton.active = false;
        statusMessage = Component.literal("\u00A7eValidating token...");
        
        // Validate in background thread
        CompletableFuture.runAsync(() -> {
            SessionAccount account = new SessionAccount(token);
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
                    
                    statusMessage = Component.literal("\u00A7aAdded: " + account.getUsername());
                    
                    // Return to config screen after short delay
                    Minecraft.getInstance().execute(() -> {
                        this.onClose();
                    });
                } else {
                    statusMessage = Component.literal("\u00A7cInvalid or expired token");
                }
            });
        });
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.6
        /*this.renderBackground(graphics, mouseX, mouseY, partialTick);*/
        super.render(graphics, mouseX, mouseY, partialTick);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 60, 0xFFFFFF);
        
        // Status message
        graphics.drawCenteredString(this.font, statusMessage, centerX, centerY + 40, 0xFFFFFF);
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

package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.accounts.SessionAccount;
import aurick.opsec.mod.mixin.MeteorMixinCanceller;
import aurick.opsec.mod.protection.ResourcePackGuard;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
//? if <1.21.6
/*import net.minecraft.client.gui.components.tabs.GridLayoutTab;*/
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class OpsecConfigScreen extends Screen {
    private static final Function<Boolean, Component> COLORED_BOOL_TO_TEXT = b -> 
        Boolean.TRUE.equals(b) 
            ? Component.literal("\u00A7aON") 
            : Component.literal("\u00A7cOFF");
    
    // Track which account is currently logging in (null = none)
    private static volatile String loggingInAccountUuid = null;
    // Track if we're currently logging out
    private static volatile boolean isLoggingOut = false;
    // Animation ticks for loading indicators - volatile for thread safety
    private static volatile int animationTicks = 0;
    
    // Helper method to create CycleButton builder with values in correct order
    // API changed in 1.21.11 - builder() now requires a default value parameter and doesn't have withInitialValue()
    // IMPORTANT: withValues() must be called BEFORE withInitialValue() for the initial value to work (pre-1.21.11)
    //? if >=1.21.11 {
    /*private static <T> CycleButton.Builder<T> cycleBuilder(Function<T, Component> valueToText, List<T> values, T initialValue) {
        return CycleButton.<T>builder(valueToText, initialValue).withValues(values);
    }*/
    //?} else {
    private static <T> CycleButton.Builder<T> cycleBuilder(Function<T, Component> valueToText, List<T> values, T initialValue) {
        return CycleButton.<T>builder(valueToText).withValues(values).withInitialValue(initialValue);
    }
    //?}
    
    private final Screen parent;
    private final OpsecConfig config;
    
    public Screen getParent() {
        return parent;
    }
    
    private TabNavigationBar tabWidget;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    
    private Button doneButton;
    private Button resetButton;
    private int currentTab = 0;
    //? if >=1.21.6
    private double scrollOffset = 0;
    private List<Tab> tabs;
    
    public OpsecConfigScreen(Screen parent) {
        //? if >=1.21.6 {
        this(parent, 0, 0);
        //?} else
        /*this(parent, 0);*/
    }
    
    public OpsecConfigScreen(Screen parent, int initialTab) {
        //? if >=1.21.6 {
        this(parent, initialTab, 0);
    }
    
    public OpsecConfigScreen(Screen parent, int initialTab, double scrollOffset) {
        //?}
        super(Component.translatable("opsec.config.title"));
        this.parent = parent;
        this.config = OpsecConfig.getInstance();
        this.currentTab = initialTab;
        //? if >=1.21.6
        this.scrollOffset = scrollOffset;
    }
    
    @Override
    protected void init() {
        // Build tabs with current settings
        SpoofSettings settings = config.getSettings();
        
        this.tabs = List.of(
            createIdentityTab(settings),
            createProtectionTab(settings),
            createWhitelistTab(settings),
            createMiscTab(settings),
            createAccountsTab()
        );
        
        this.tabWidget = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(this.tabs.toArray(new Tab[0]))
                .build();
        this.addRenderableWidget(this.tabWidget);
        this.tabWidget.selectTab(this.currentTab, false);
        
        // Create footer buttons
        // Accounts tab is index 4 - reset button doesn't apply to accounts
        boolean isAccountsTab = this.currentTab == 4;
        this.resetButton = Button.builder(Component.translatable("controls.reset"), button -> {
            SpoofSettings defaults = new SpoofSettings();
            settings.copyFrom(defaults);
            config.save();
            refreshScreen();
        }).width(150)
          .tooltip(Tooltip.create(Component.literal(isAccountsTab 
                  ? "Reset is disabled on Accounts tab" 
                  : "Reset all settings to defaults")))
          .build();
        
        // Disable reset button on Accounts tab
        this.resetButton.active = !isAccountsTab;
        
        this.doneButton = Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(150)
                .build();
        
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(this.resetButton);
        footer.addChild(this.doneButton);
        
        this.layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            this.addRenderableWidget(widget);
        });
        
        this.repositionElements();
    }
    
    private Tab createIdentityTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Client Brand Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lClient Brand"));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isSpoofBrand())
                .withTooltip(v -> Tooltip.create(Component.literal("Replace your client brand with a spoofed value")))
                .create(0, 0, 210, 20, Component.translatable("opsec.option.spoofBrand"), 
                    (button, value) -> { 
                        settings.setSpoofBrand(value);
                        config.save();
                        refreshScreen();
                }));
        
        if (settings.isSpoofBrand()) {
            // When whitelist is enabled, show greyed-out forced Fabric indicator
            if (settings.isWhitelistEnabled()) {
                widgets.add(createSectionHeader("\u00A77Brand: Fabric (forced by whitelist)"));
            } else {
                widgets.add(cycleBuilder(BrandType::getDisplayName, List.of(BrandType.values()), BrandType.fromString(settings.getCustomBrand()))
                        .withTooltip(v -> Tooltip.create(Component.literal("Select the brand to appear as")))
                        .create(0, 0, 210, 20, Component.translatable("opsec.option.brandType"),
                        (button, value) -> { settings.setCustomBrand(value.getValue()); config.save(); }));
            }
            
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isSpoofChannels())
                .withTooltip(v -> Tooltip.create(Component.literal("Replace/block mod channels to appear as clean instance")))
                    .create(0, 0, 210, 20, Component.translatable("opsec.option.spoofChannels"),
                        (button, value) -> { 
                            settings.setSpoofChannels(value); 
                            config.save();
                            refreshScreen();
                    }));
            
            if (settings.isSpoofChannels()) {
                widgets.add(createSectionHeader("\u00A7e\u26A0 May break mods if not whitelisted"));
            }
        }
        
        return new WidgetTab(Component.translatable("opsec.tab.identity"), widgets);
    }
    
    private Tab createProtectionTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Resource Pack Protection Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lResource Pack Protection"));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isIsolatePackCache())
                .withTooltip(v -> Tooltip.create(Component.literal("Store packs per-account to prevent fingerprinting")))
                .create(0, 0, 210, 20, Component.translatable("opsec.option.isolatePackCache"),
                (button, value) -> { settings.setIsolatePackCache(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isBlockLocalPackUrls())
            .withTooltip(v -> Tooltip.create(Component.literal("Block local URL resource pack requests")))
                .create(0, 0, 210, 20, Component.translatable("opsec.option.blockLocalPackUrls"),
                (button, value) -> { settings.setBlockLocalPackUrls(value); config.save(); }));
        
        widgets.add(Button.builder(Component.translatable("opsec.option.clearCache"), button -> {
                ResourcePackGuard.clearAllCaches();
            }).size(210, 20)
          .tooltip(Tooltip.create(Component.literal("Deletes all cached server resource packs\nAlso resets download queue state")))
          .build());
        
        // Translation Exploit Protection Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lTranslation Exploit Protection"));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isTranslationProtectionEnabled())
            .withTooltip(v -> Tooltip.create(Component.literal("Mask translation key values to appear as default vanilla client")))
                .create(0, 0, 210, 20, Component.literal("Spoof Translation Keys"),
                    (button, value) -> { 
                        settings.setTranslationProtection(value); 
                        config.save();
                        refreshScreen();
                }));
        
        // Only show sub-options when translation protection is enabled
        if (settings.isTranslationProtectionEnabled()) {
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isFakeDefaultKeybinds())
                .withTooltip(v -> Tooltip.create(Component.literal(
                    "Spoof vanilla keybinds to default values when enabled")))
                    .create(0, 0, 210, 20, Component.literal("Fake Default Keybinds"),
                        (button, value) -> { 
                            settings.setFakeDefaultKeybinds(value); 
                            config.save();
                    }));
            
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isMeteorFix())
                .withTooltip(v -> Tooltip.create(Component.literal(
                    "Blacklist a Meteor Client mixin to allow OpSec's proper protection handling")))
                    .create(0, 0, 210, 20, Component.literal("Meteor Fix"),
                        (button, value) -> { 
                            settings.setMeteorFix(value); 
                            config.save();
                            refreshScreen();
                    }));
            
            // Show warning only when setting differs from what was applied at startup
            if (MeteorMixinCanceller.needsRestart(settings.isMeteorFix())) {
                widgets.add(createSectionHeader("\u00A7e\u26A0 Requires game restart to take effect"));
            }
        }
        
        // Privacy & Security Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lPrivacy & Security"));
        
        widgets.add(cycleBuilder(SigningModeDisplay::getDisplayName, List.of(SpoofSettings.SigningMode.values()), settings.getSigningMode())
                .withTooltip(v -> Tooltip.create(SigningModeDisplay.getTooltip(v)))
                .create(0, 0, 210, 20, Component.literal("Chat Signing"),
                (button, value) -> { settings.setSigningMode(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isDisableTelemetry())
                .withTooltip(v -> Tooltip.create(Component.literal("Block telemetry data sent to Mojang")))
                .create(0, 0, 210, 20, Component.translatable("opsec.option.disableTelemetry"),
                (button, value) -> { settings.setDisableTelemetry(value); config.save(); }));
        
        return new WidgetTab(Component.translatable("opsec.tab.protection"), widgets);
    }
    
    private Tab createMiscTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Alerts & Logging Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lAlerts & Logging"));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isShowAlerts())
                .withTooltip(v -> Tooltip.create(Component.literal("Show chat messages when tracking detected")))
                .create(0, 0, 210, 20, Component.translatable("opsec.option.showAlerts"),
                (button, value) -> { settings.setShowAlerts(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isShowToasts())
                .withTooltip(v -> Tooltip.create(Component.literal("Show popup notifications")))
                .create(0, 0, 210, 20, Component.translatable("opsec.option.showToasts"),
                (button, value) -> { settings.setShowToasts(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isLogDetections())
                .withTooltip(v -> Tooltip.create(Component.literal("Log detection events to game log")))
                .create(0, 0, 210, 20, Component.translatable("opsec.option.logDetections"),
                (button, value) -> { settings.setLogDetections(value); config.save(); }));
        
        return new WidgetTab(Component.translatable("opsec.tab.misc"), widgets);
    }
    
    private Tab createAccountsTab() {
        List<AbstractWidget> widgets = new ArrayList<>();
        AccountManager accountManager = AccountManager.getInstance();
        
        // Section header
        widgets.add(createSectionHeader("\u00A7f\u00A7lAccounts"));
        
        // Current account info
        String currentUser = Minecraft.getInstance().getUser().getName();
        widgets.add(createSectionHeader("\u00A77Current: " + currentUser));
        
        // List saved accounts
        if (!accountManager.getAccounts().isEmpty()) {
            widgets.add(createSectionHeader("\u00A7f\u00A7lSaved Accounts"));
            
            for (SessionAccount account : accountManager.getAccounts()) {
                String displayName = account.getUsername();
                // Check if this account is actually the current logged-in user (not just stored as active)
                boolean isLoggedIn = currentUser.equals(account.getUsername());
                boolean isValid = account.isValid();
                
                // Create account row with both buttons side by side
                // Check if this account is currently logging in
                boolean isLoggingIn = account.getUuid() != null && account.getUuid().equals(loggingInAccountUuid);
                
                // Green only if actually logged in, red if invalid, yellow if logging in/out, white otherwise
                String buttonText;
                if (isLoggingIn) {
                    // Animated "Logging in" text
                    String dots = ".".repeat((animationTicks / 5) % 4);
                    buttonText = "\u00A7e" + displayName + " \u00A77(logging in" + dots + ")";
                } else if (isLoggedIn && isLoggingOut) {
                    // Animated "Logging out" text
                    String dots = ".".repeat((animationTicks / 5) % 4);
                    buttonText = "\u00A7e" + displayName + " \u00A77(logging out" + dots + ")";
                } else if (!isValid) {
                    buttonText = "\u00A7c" + displayName + " \u00A78(invalid)";
                } else if (isLoggedIn) {
                    buttonText = "\u00A7a" + displayName;
                } else {
                    buttonText = displayName;
                }
                
                String refreshInfo = account.hasRefreshToken() ? "\n\u00A72Auto-refresh enabled" : "";
                String tooltip;
                if (!isValid) {
                    String errorMsg = account.getLastError();
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        tooltip = "\u00A7c" + errorMsg + "\n\u00A77Click to try login anyway" + refreshInfo;
                    } else {
                        tooltip = "\u00A7cToken expired or invalid\n\u00A77Click to try login anyway" + refreshInfo;
                    }
                } else if (isLoggedIn) {
                    tooltip = "Currently logged in\nClick to logout to original account" + refreshInfo;
                } else {
                    String errorMsg = account.getLastError();
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        tooltip = "\u00A7cLast error: " + errorMsg + "\n\u00A77Click to login as " + displayName + refreshInfo;
                    } else {
                        tooltip = "Click to login as " + displayName + refreshInfo;
                    }
                }
                
                widgets.add(new AccountRowWidget(
                        buttonText,
                        tooltip,
                        isLoggedIn,
                        // Login/logout action
                        () -> {
                            if (isLoggingIn || isLoggingOut) {
                                return; // Already logging in/out
                            }
                            if (isLoggedIn) {
                                // Logout to original account in background thread
                                isLoggingOut = true;
                                Opsec.LOGGER.info("[OpSec] Logging out of account: {}", account.getUsername());
                                
                                CompletableFuture.runAsync(() -> {
                                    accountManager.logout();
                                }).whenComplete((result, error) -> {
                                    // Update UI on main thread
                                    Minecraft.getInstance().execute(() -> {
                                        isLoggingOut = false;
                                        refreshScreen();
                                    });
                                });
                            } else {
                                // Login to this account in background thread
                                loggingInAccountUuid = account.getUuid();
                                Opsec.LOGGER.info("[OpSec] Logging into account: {}", account.getUsername());
                                
                                CompletableFuture.runAsync(() -> {
                                    boolean success = account.login();
                                    if (success) {
                                        accountManager.setActiveAccountUuid(account.getUuid());
                                    }
                                }).whenComplete((result, error) -> {
                                    // Update UI on main thread
                                    Minecraft.getInstance().execute(() -> {
                                        loggingInAccountUuid = null;
                                        refreshScreen();
                                    });
                                });
                            }
                        },
                        // Remove action
                        () -> {
                            if (isLoggingIn || isLoggingOut) {
                                return; // Don't remove while logging in/out
                            }
                            // If removing the currently active account, logout first (async)
                            if (isLoggedIn) {
                                isLoggingOut = true;
                                CompletableFuture.runAsync(() -> {
                                    accountManager.logout();
                                }).whenComplete((result, error) -> {
                                    Minecraft.getInstance().execute(() -> {
                                        isLoggingOut = false;
                                        accountManager.remove(account);
                                        refreshScreen();
                                    });
                                });
                            } else {
                                accountManager.remove(account);
                                refreshScreen();
                            }
                        }
                ));
            }
            
            // Refresh button to revalidate all accounts
            Button refreshButton = Button.builder(Component.literal(
                    accountManager.isRefreshing() ? "\u00A77Refreshing..." : "Refresh All"
            ), button -> {
                if (accountManager.isRefreshing()) {
                    return; // Already refreshing
                }
                button.setMessage(Component.literal("\u00A77Refreshing..."));
                button.active = false;
                accountManager.refreshAllAccounts((valid, invalid) -> {
                    refreshScreen();
                });
            }).size(210, 20)              .tooltip(Tooltip.create(Component.literal("Revalidate all accounts\nInvalid tokens will be marked red")))

              .build();
            
            // Disable button if already refreshing
            if (accountManager.isRefreshing()) {
                refreshButton.active = false;
            }
            
            widgets.add(refreshButton);
        }
        
        // Add account section
        widgets.add(createSectionHeader("\u00A7f\u00A7lAdd Account"));
        
        // Add button to open add account dialog
        widgets.add(Button.builder(Component.literal("Add Session Token"), button -> {
            this.minecraft.setScreen(new AddAccountScreen(this));
        }).size(210, 20)
          .tooltip(Tooltip.create(Component.literal("Add a new account using a session token")))
          .build());
        
        // Import/Export buttons
        widgets.add(new ImportExportRowWidget(
                // Import action
                () -> {
                    openImportDialog();
                },
                // Export action
                () -> {
                    openExportDialog();
                }
        ));
        
        return new WidgetTab(Component.translatable("opsec.tab.accounts"), widgets);
    }
    
    private void openImportDialog() {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.json"));
                filters.flip();
                
                String result = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Accounts",
                    "",
                    filters,
                    "JSON Files (*.json)",
                    false
                );
                
                if (result == null) return;
                
                File file = new File(result);
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                int imported = AccountManager.getInstance().importFromJson(content);
                
                // Refresh UI on main thread
                Minecraft.getInstance().execute(() -> {
                    if (imported > 0) {
                        Opsec.LOGGER.info("[OpSec] Imported {} accounts from {}", imported, file.getName());
                    }
                    refreshScreen();
                });
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to import accounts: {}", e.getMessage());
            }
        }, "OpSec-Import-Thread").start();
    }
    
    private void openExportDialog() {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.json"));
                filters.flip();
                
                String result = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export Accounts",
                    "opsec-accounts-export.json",
                    filters,
                    "JSON Files (*.json)"
                );
                
                if (result == null) return;
                
                File file = new File(result);
                if (!file.getName().toLowerCase().endsWith(".json")) {
                    file = new File(file.getParentFile(), file.getName() + ".json");
                }
                
                String json = AccountManager.getInstance().exportToJson();
                Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    
                Opsec.LOGGER.info("[OpSec] Exported accounts to {}", file.getPath());
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to export accounts: {}", e.getMessage());
            }
        }, "OpSec-Export-Thread").start();
    }
    
    // Custom widget for account row (account button + remove button side by side)
    private static class AccountRowWidget extends AbstractWidget {
        private final Button accountButton;
        private final Button removeButton;
        private final Runnable onAccountClick;
        private final Runnable onRemoveClick;
        //? if >=1.21.9 {
        /*private boolean wasMouseDown = false;*/
        //?}
        
        public AccountRowWidget(String accountName, String tooltip, boolean isActive, Runnable onAccountClick, Runnable onRemoveClick) {
            super(0, 0, 210, 20, Component.empty());
            this.onAccountClick = onAccountClick;
            this.onRemoveClick = onRemoveClick;
            
            this.accountButton = Button.builder(Component.literal(accountName), btn -> onAccountClick.run())
                    .size(150, 20)
                    .tooltip(Tooltip.create(Component.literal(tooltip)))
                    .build();
            
            this.removeButton = Button.builder(Component.literal("\u00A7cRemove"), btn -> onRemoveClick.run())
                    .size(55, 20)
                    .tooltip(Tooltip.create(Component.literal("Remove this account")))
                    .build();
        }
        
        @Override
        public void setX(int x) {
            super.setX(x);
            updateButtonPositions();
        }
        
        @Override
        public void setY(int y) {
            super.setY(y);
            updateButtonPositions();
        }
        
        private void updateButtonPositions() {
            // Center the pair of buttons
            int totalWidth = 150 + 5 + 55; // account button + gap + remove button
            int startX = getX() + (getWidth() - totalWidth) / 2;
            accountButton.setX(startX);
            accountButton.setY(getY());
            removeButton.setX(startX + 150 + 5);
            removeButton.setY(getY());
        }
        
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.render(graphics, mouseX, mouseY, partialTick);
            removeButton.render(graphics, mouseX, mouseY, partialTick);
            
            //? if >=1.21.9 {
            /*// Poll mouse state for click detection since mouseClicked API changed
            long windowHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            
            if (!isMouseDown && wasMouseDown) {
                // Mouse was just released
                if (accountButton.isMouseOver(mouseX, mouseY)) {
                    onAccountClick.run();
                } else if (removeButton.isMouseOver(mouseX, mouseY)) {
                    onRemoveClick.run();
                }
            }
            wasMouseDown = isMouseDown;*/
            //?}
        }
        
        // Forward mouse clicks to child buttons
        //? if <1.21.9 {
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) { // Left click only
                if (accountButton.isMouseOver(mouseX, mouseY)) {
                    onAccountClick.run();
                    return true;
                } else if (removeButton.isMouseOver(mouseX, mouseY)) {
                    onRemoveClick.run();
                    return true;
                }
            }
            return false;
        }
        //?}
        
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            accountButton.updateNarration(output);
        }
    }
    
    // Custom widget for import/export row
    private static class ImportExportRowWidget extends AbstractWidget {
        private final Button importButton;
        private final Button exportButton;
        private final Runnable onImportAction;
        private final Runnable onExportAction;
        //? if >=1.21.9 {
        /*private boolean wasMouseDown = false;*/
        //?}
        
        public ImportExportRowWidget(Runnable onImport, Runnable onExport) {
            super(0, 0, 210, 20, Component.empty());
            this.onImportAction = onImport;
            this.onExportAction = onExport;
            
            this.importButton = Button.builder(Component.literal("Import"), btn -> onImport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(Component.literal("Import accounts from JSON file")))
                    .build();
            
            this.exportButton = Button.builder(Component.literal("Export"), btn -> onExport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(Component.literal("Export accounts to JSON file")))
                    .build();
        }
        
        @Override
        public void setX(int x) {
            super.setX(x);
            updateButtonPositions();
        }
        
        @Override
        public void setY(int y) {
            super.setY(y);
            updateButtonPositions();
        }
        
        private void updateButtonPositions() {
            int totalWidth = 100 + 5 + 100;
            int startX = getX() + (getWidth() - totalWidth) / 2;
            importButton.setX(startX);
            importButton.setY(getY());
            exportButton.setX(startX + 100 + 5);
            exportButton.setY(getY());
        }
        
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.render(graphics, mouseX, mouseY, partialTick);
            exportButton.render(graphics, mouseX, mouseY, partialTick);
            
            //? if >=1.21.9 {
            /*// Poll mouse state for click detection since mouseClicked API changed
            long windowHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            
            if (!isMouseDown && wasMouseDown) {
                // Mouse was just released
                if (importButton.isMouseOver(mouseX, mouseY)) {
                    onImportAction.run();
                } else if (exportButton.isMouseOver(mouseX, mouseY)) {
                    onExportAction.run();
                }
            }
            wasMouseDown = isMouseDown;*/
            //?}
        }
        
        // Forward mouse clicks to child buttons
        //? if <1.21.9 {
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) { // Left click only
                if (importButton.isMouseOver(mouseX, mouseY)) {
                    onImportAction.run();
                    return true;
                } else if (exportButton.isMouseOver(mouseX, mouseY)) {
                    onExportAction.run();
                    return true;
                }
            }
            return false;
        }
        //?}
        
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            importButton.updateNarration(output);
        }
    }
    
    private Tab createWhitelistTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Section header
        widgets.add(createSectionHeader("\u00A7f\u00A7lMod Whitelist"));
        
        // Master enable/disable toggle
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isWhitelistEnabled())
                .withTooltip(v -> Tooltip.create(Component.literal(
                    "Allow whitelisted mods' translation keys and channels to pass through")))
                .create(0, 0, 210, 20, Component.literal("Enable Whitelist"),
                    (button, value) -> { 
                        settings.setWhitelistEnabled(value);
                        config.save();
                        refreshScreen();
                }));
        
        if (settings.isWhitelistEnabled()) {
            // Mod list header
            widgets.add(createSectionHeader("\u00A7f\u00A7lInstalled Mods"));
            
            // Create mod entries
            for (ModContainer mod : getWhitelistableMods()) {
                String modId = mod.getMetadata().getId();
                String modName = mod.getMetadata().getName();
                
                boolean isWhitelisted = settings.getWhitelistedMods().contains(modId);
                final String finalModId = modId;
                CycleButton<Boolean> modButton = cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), isWhitelisted)
                    .withTooltip(v -> Tooltip.create(Component.literal(finalModId)))
                    .create(0, 0, 210, 20, Component.literal(modName),
                        (button, value) -> { 
                            if (value) {
                                settings.getWhitelistedMods().add(finalModId);
                            } else {
                                settings.getWhitelistedMods().remove(finalModId);
                            }
                            config.save();
                    });
                
                widgets.add(modButton);
            }
        }
        
        return new WidgetTab(Component.literal("Whitelist"), widgets);
    }
    
    /**
     * Get mods that can be whitelisted.
     * Only shows mods that have registered translation keys OR network channels.
     * This filters out libraries and internal mods that don't expose content.
     */
    private static List<ModContainer> getWhitelistableMods() {
        // On-demand scan to ensure all mods with language files are registered
        // This catches mods that the mixin couldn't detect
        ensureModsScanned();
        
        List<ModContainer> result = FabricLoader.getInstance().getAllMods().stream()
            .filter(mod -> {
                String id = mod.getMetadata().getId();
                // Skip Minecraft, Java, Fabric internals
                if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")) {
                    return false;
                }
                // Skip ALL fabric-* modules (fabric-api submodules, fabric-language-*, etc.)
                // These channels are auto-whitelisted anyway
                if (id.startsWith("fabric-") || id.equals("fabric-api")) {
                    return false;
                }
                // Skip OpSec itself and mixinsquared
                if (id.equals("opsec") || id.equals("mixinsquared")) {
                    return false;
                }
                
                // Only show mods with translation keys OR network channels
                ModRegistry.ModInfo info = ModRegistry.getModInfo(id);
                if (info == null) {
                    return false;
                }
                return info.hasTrackableContent();
            })
            .sorted(Comparator.comparing(mod -> mod.getMetadata().getName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
        
        Opsec.LOGGER.debug("[OpSec] getWhitelistableMods returning {} mods", result.size());
        return result;
    }
    
    /**
     * Ensure all mods with language files are registered in ModRegistry.
     * This is a fallback for when the mixin doesn't work (e.g., method signature changes).
     */
    private static void ensureModsScanned() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            
            // Skip system mods
            if (modId.equals("minecraft") || modId.equals("java") || modId.equals("fabricloader")) {
                continue;
            }
            // Skip fabric API modules
            if (modId.startsWith("fabric-") || modId.equals("fabric-api")) {
                continue;
            }
            // Skip our own mod and mixinsquared
            if (modId.equals("opsec") || modId.equals("mixinsquared")) {
                continue;
            }
            
            // Check if this mod already has translation keys tracked
            ModRegistry.ModInfo existingInfo = ModRegistry.getModInfo(modId);
            if (existingInfo != null && existingInfo.hasTranslationKeys()) {
                continue; // Already tracked
            }
            
            // Check if this mod has a language file
            for (java.nio.file.Path rootPath : mod.getRootPaths()) {
                java.nio.file.Path langFile = rootPath.resolve("assets/" + modId + "/lang/en_us.json");
                if (java.nio.file.Files.exists(langFile)) {
                    try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                            java.nio.file.Files.newInputStream(langFile))) {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        for (String key : json.keySet()) {
                            ModRegistry.recordTranslationKey(modId, key);
                        }
                    } catch (Exception e) {
                        // Silently ignore - not critical
                    }
                    break;
                }
            }
        }
    }
    
    private StringWidget createSectionHeader(String text) {
        //? if >=1.21.6 {
        Component component = Component.literal(text);
        // Calculate actual text width and use that for the widget
        int textWidth = Minecraft.getInstance().font.width(component);
        return new StringWidget(textWidth, 20, component, Minecraft.getInstance().font);
        //?} else
        /*return new StringWidget(210, 20, Component.literal(text), Minecraft.getInstance().font);*/
    }
    
    private int getCurrentTabIndex() {
        if (this.tabs != null && this.tabManager.getCurrentTab() != null) {
            for (int i = 0; i < this.tabs.size(); i++) {
                if (this.tabs.get(i) == this.tabManager.getCurrentTab()) {
                    return i;
                }
            }
        }
        return this.currentTab;
    }
    
    //? if >=1.21.6 {
    private double getCurrentScrollOffset() {
        Tab currentTab = this.tabManager.getCurrentTab();
        if (currentTab instanceof WidgetTab widgetTab) {
            return widgetTab.getScrollAmount();
        }
        return 0;
    }
    //?}
    
    private void refreshScreen() {
        int tabIndex = getCurrentTabIndex();
        //? if >=1.21.6 {
        double scroll = getCurrentScrollOffset();
        this.minecraft.setScreen(new OpsecConfigScreen(this.parent, tabIndex, scroll));
        //?} else
        /*this.minecraft.setScreen(new OpsecConfigScreen(this.parent, tabIndex));*/
    }
    
    @Override
    protected void repositionElements() {
        if (this.tabWidget != null) {
            this.tabWidget.setWidth(this.width);
            this.tabWidget.arrangeElements();
            int tabBottom = this.tabWidget.getRectangle().bottom();
            ScreenRectangle screenRect = new ScreenRectangle(0, tabBottom, this.width, this.height - 36 - tabBottom);
            this.tabManager.setTabArea(screenRect);
            this.layout.setHeaderHeight(tabBottom);
            this.layout.arrangeElements();
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        // Update reset button state based on current tab (Accounts tab = index 4)
        if (this.resetButton != null) {
            int currentTabIndex = getCurrentTabIndex();
            boolean isAccountsTab = currentTabIndex == 4;
            this.resetButton.active = !isAccountsTab;
        }
        
        // Animate login/logout loading text
        if (loggingInAccountUuid != null || isLoggingOut) {
            animationTicks++;
            // Refresh every 5 ticks to update the animated dots
            if (animationTicks % 5 == 0) {
                refreshScreen();
            }
        } else {
            animationTicks = 0;
        }
    }
    
    @Override
    public void onClose() {
        config.save();
        this.minecraft.setScreen(parent);
    }
    
    //? if >=1.21.6 {
    // === Scrollable Tab implementation ===
    private class WidgetTab implements Tab {
        private final Component title;
        private final ScrollableWidgetList scrollableList;

        public WidgetTab(Component title, List<AbstractWidget> widgets) {
            this.title = title;
            // Create the scrollable list immediately with the widgets
            // Initial dimensions will be set by doLayout
            this.scrollableList = new ScrollableWidgetList(Minecraft.getInstance(), 300, 200, 0, widgets);
        }

        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
            consumer.accept(scrollableList);
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
            // Update the scrollable list's size and position
            scrollableList.setSize(rectangle.width(), rectangle.height());
            scrollableList.setPosition(rectangle.left(), rectangle.top());
            // Apply saved scroll offset instead of resetting
            scrollableList.setScrollAmount(scrollOffset);
        }
        
        @Override
        public Component getTabExtraNarration() {
            return Component.empty();
        }
        
        public double getScrollAmount() {
            return scrollableList.scrollAmount();
        }
    }
    //?} else {
    /*// === Scrollable Tab implementation extending GridLayoutTab for compatibility ===
    // GridLayoutTab provides default getTabExtraNarration() implementation
    private class WidgetTab extends GridLayoutTab {
        private final ScrollableWidgetList scrollableList;

        public WidgetTab(Component title, List<AbstractWidget> widgets) {
            super(title);
            // Create the scrollable list immediately with the widgets
            // Initial dimensions will be set by doLayout
            this.scrollableList = new ScrollableWidgetList(Minecraft.getInstance(), 300, 200, 0, widgets);
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
            // Use our scrollable list instead of the parent's GridLayout
            consumer.accept(scrollableList);
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
            // Update the scrollable list's size and position
            scrollableList.setSize(rectangle.width(), rectangle.height());
            scrollableList.setPosition(rectangle.left(), rectangle.top());
        }
    }*/
    //?}

    // === Scrollable Widget List ===
    private static class ScrollableWidgetList extends ContainerObjectSelectionList<ScrollableWidgetList.WidgetEntry> {
        public ScrollableWidgetList(Minecraft minecraft, int width, int height, int top, List<AbstractWidget> widgets) {
            super(minecraft, width, height, top, 25);
            this.centerListVertically = false;

            for (AbstractWidget widget : widgets) {
                //? if >=1.21.6
                this.addEntry(new WidgetEntry(widget, this));
                //? if <1.21.6
                /*this.addEntry(new WidgetEntry(widget));*/
            }
        }

        @Override
        public int getRowWidth() {
            return 220;
        }

        @Override
        protected int scrollBarX() {
            return this.getX() + this.getWidth() / 2 + 124;
        }
        

        public static class WidgetEntry extends ContainerObjectSelectionList.Entry<WidgetEntry> {
            private final AbstractWidget widget;

            //? if >=1.21.6 {
            public WidgetEntry(AbstractWidget widget, ScrollableWidgetList parentList) {
                this.widget = widget;
            }
            //?} else {
            /*WidgetEntry(AbstractWidget widget) {
                this.widget = widget;
            }*/
            //?}

            //? if >=1.21.9 {
            /*@Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                // Center the widget horizontally on the screen
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int centerX = (screenWidth - widget.getWidth()) / 2;
                widget.setX(centerX);
                widget.setY(this.getContentY());
                widget.render(graphics, mouseX, mouseY, partialTick);
            }*/
            //?} else {
            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
                widget.setX(left + (width - widget.getWidth()) / 2);
                widget.setY(top);
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            //?}

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(widget);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(widget);
            }

            //? if <1.21.9 {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return widget.mouseClicked(mouseX, mouseY, button);
            }
            
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                return widget.keyPressed(keyCode, scanCode, modifiers);
            }
            
            @Override
            public boolean charTyped(char chr, int modifiers) {
                return widget.charTyped(chr, modifiers);
            }
            //?}
        }
    }
    
    // === Enums ===
    
    public enum BrandType {
        VANILLA("vanilla", "Vanilla"),
        FABRIC("fabric", "Fabric"),
        FORGE("forge", "Forge");
        
        private final String value;
        private final String displayName;
        
        BrandType(String value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }
        
        public String getValue() {
            return value;
        }
        
        public Component getDisplayName() {
            return Component.literal(displayName);
        }
        
        public static BrandType fromString(String s) {
            for (BrandType type : values()) {
                if (type.value.equalsIgnoreCase(s)) {
                    return type;
                }
            }
            return VANILLA;
        }
    }
    
    
    /**
     * Display helper for SigningMode enum.
     * Controls whether chat messages are cryptographically signed.
     */
    private static class SigningModeDisplay {
        public static Component getDisplayName(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> Component.literal("\u00A7cON"); // Red - signing enabled
                case OFF -> Component.literal("\u00A7eOFF"); // Yellow - signing disabled 
                case ON_DEMAND -> Component.literal("\u00A7aAUTO"); // Green - recommended
            };
        }
        
        public static Component getTooltip(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> Component.literal("Always sign chat messages");
                case OFF -> Component.literal("Never sign chat messages");
                case ON_DEMAND -> Component.literal("Only sign chat messages when server requires it");
            };
        }
    }
}

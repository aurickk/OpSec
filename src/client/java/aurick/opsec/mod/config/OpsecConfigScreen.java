package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.accounts.Account;
import aurick.opsec.mod.accounts.SessionAccount;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.mixin.MeteorMixinCanceller;
import aurick.opsec.mod.protection.ResourcePackGuard;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
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
    private StringWidget versionLabel;
    private boolean versionOutdated;
    private int currentTab = 0;
    private double scrollOffset = 0;
    private List<Tab> tabs;

    //? if <1.20.3 {
    /*// 1.20.1 / 1.20.2: Tab.visitChildren only accepts AbstractWidget, but
    // ScrollableWidgetList is a ContainerObjectSelectionList (Renderable + GuiEventListener
    // + NarratableEntry, not AbstractWidget). The screen registers the active tab's body
    // itself, bypassing the tab-manager pipeline. Also tracks the last-known current tab so
    // a click on a tab strip swaps the active body on the next render frame.
    private ScrollableWidgetList managedBody;
    *///?}

    public OpsecConfigScreen(Screen parent) {
        this(parent, 0, 0);
    }

    public OpsecConfigScreen(Screen parent, int initialTab) {
        this(parent, initialTab, 0);
    }

    public OpsecConfigScreen(Screen parent, int initialTab, double scrollOffset) {
        super(OpsecLang.component(OpsecStrings.CONFIG_TITLE));
        this.parent = parent;
        this.config = OpsecConfig.getInstance();
        this.currentTab = initialTab;
        this.scrollOffset = scrollOffset;
    }
    
    @Override
    protected void init() {
        // Build tabs with current settings
        SpoofSettings settings = config.getSettings();
        
        this.tabs = List.of(
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
        // Accounts tab is index 3 - reset button doesn't apply to accounts
        boolean isAccountsTab = this.currentTab == 3;
        this.resetButton = Button.builder(Component.translatable("controls.reset"), button -> {
            SpoofSettings defaults = new SpoofSettings();
            settings.copyFrom(defaults);
            config.save();
            UpdateChecker.resetShown();
            JarIntegrityChecker.resetShown();
            refreshScreen();
        }).width(150)
          .tooltip(Tooltip.create(OpsecLang.component(isAccountsTab
                  ? OpsecStrings.BUTTON_RESET_DISABLED_TOOLTIP
                  : OpsecStrings.BUTTON_RESET_TOOLTIP)))
          .build();
        
        // Disable reset button on Accounts tab
        this.resetButton.active = !isAccountsTab;
        
        this.doneButton = Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(150)
                .build();
        
        //? if >=1.20.3 {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        //?} else {
        /*// 1.20.1 / 1.20.2: LinearLayout.horizontal() static factory not yet present —
        // construct directly with explicit position + orientation.
        LinearLayout footer = this.layout.addToFooter(new LinearLayout(0, 0, LinearLayout.Orientation.HORIZONTAL));
        *///?}
        footer.addChild(this.resetButton);
        footer.addChild(this.doneButton);
        
        this.layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            this.addRenderableWidget(widget);
        });

        this.repositionElements();

        // Version label in bottom-left corner, vertically centered in the 36px footer
        String currentVersion = "v" + UpdateChecker.getCurrentVersion();
        this.versionOutdated = UpdateChecker.getLatestVersion() != null
                && !UpdateChecker.getLatestVersion().equals(UpdateChecker.getCurrentVersion());
        String color = versionOutdated ? "\u00A7c" : "\u00A7a";
        Component versionText = Component.literal(color + currentVersion);
        int textWidth = this.font.width(currentVersion);
        int labelHeight = 10;
        int footerY = this.height - 36;
        int labelY = footerY + (36 - labelHeight) / 2;

        this.versionLabel = new StringWidget(textWidth, labelHeight, versionText, this.font);
        this.versionLabel.setX(6);
        this.versionLabel.setY(labelY + 2);
        if (versionOutdated) {
            this.versionLabel.setTooltip(Tooltip.create(OpsecLang.component(
                    OpsecStrings.VERSION_TOOLTIP_OUTDATED, UpdateChecker.getLatestVersion())));
        } else {
            this.versionLabel.setTooltip(Tooltip.create(OpsecLang.component(OpsecStrings.VERSION_TOOLTIP_UPTODATE)));
        }
        this.addRenderableWidget(this.versionLabel);
    }
    
    private Tab createProtectionTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();

        // Client Brand Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lClient Brand"));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA)));
        } else {
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isSpoofAsVanilla())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA_TOOLTIP)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA),
                        (button, value) -> {
                            // setSpoofAsVanilla snapshots / restores whitelistMode around
                            // the implicit Block-All override (see SpoofSettings).
                            settings.setSpoofAsVanilla(value);
                            config.save();
                            refreshScreen();
                    }));
        }

        // Resource Pack Protection Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lResource Pack Protection"));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            if (OpsecConfig.MC_VERSION_HAS_MULTI_PACK) {
                widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_ISOLATE_PACK_CACHE)));
            }
            if (OpsecConfig.MC_VERSION_HAS_BLOCK_LOCAL_URLS) {
                widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_BLOCK_LOCAL_PACK_URLS)));
            }
        } else {
            if (OpsecConfig.MC_VERSION_HAS_MULTI_PACK) {
                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isIsolatePackCache())
                        .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_ISOLATE_PACK_CACHE)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_ISOLATE_PACK_CACHE),
                        (button, value) -> { settings.setIsolatePackCache(value); config.save(); }));
            }

            if (OpsecConfig.MC_VERSION_HAS_BLOCK_LOCAL_URLS) {
                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isBlockLocalPackUrls())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_BLOCK_LOCAL_PACK_URLS)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_BLOCK_LOCAL_PACK_URLS),
                        (button, value) -> { settings.setBlockLocalPackUrls(value); config.save(); }));
            }

            // Bypass Server Pack Requirement (tri-state: MANUAL / ASK / ALWAYS_ON)
            widgets.add(cycleBuilder(
                    (SpoofSettings.StripMode m) -> switch (m) {
                        case MANUAL    -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_MANUAL);
                        case ASK       -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ASK);
                        case ALWAYS_ON -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ALWAYS_ON);
                    },
                    List.of(SpoofSettings.StripMode.values()),
                    settings.getPackStripMode())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(switch (v) {
                    case MANUAL    -> OpsecStrings.PACKSTRIP_MODE_MANUAL_TOOLTIP;
                    case ASK       -> OpsecStrings.PACKSTRIP_MODE_ASK_TOOLTIP;
                    case ALWAYS_ON -> OpsecStrings.PACKSTRIP_MODE_ALWAYS_ON_TOOLTIP;
                })))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_PACK_STRIP_MODE),
                    (button, value) -> {
                        settings.setPackStripMode(value);
                        config.save();
                    }));
        }

        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.OPTION_CLEAR_CACHE), button -> {
                ResourcePackGuard.clearAllCaches();
            }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_CLEAR_CACHE)))
          .build());

        // Key Resolution Protection Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lKey Resolution Protection"));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_KEY_RESOLUTION_SPOOFING)));
        } else {
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isTranslationProtectionEnabled())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_KEY_RESOLUTION_SPOOFING)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_KEY_RESOLUTION_SPOOFING),
                        (button, value) -> {
                            settings.setTranslationProtection(value);
                            config.save();
                            refreshScreen();
                    }));

            // Only show sub-options when translation protection is enabled
            if (settings.isTranslationProtectionEnabled()) {
                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isFakeDefaultKeybinds())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_FAKE_DEFAULT_KEYBINDS)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_FAKE_DEFAULT_KEYBINDS),
                            (button, value) -> {
                                settings.setFakeDefaultKeybinds(value);
                                config.save();
                        }));

                //? if <26.1 {
                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isMeteorFix())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_METEOR_FIX)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_METEOR_FIX),
                            (button, value) -> {
                                settings.setMeteorFix(value);
                                config.save();
                                refreshScreen();
                        }));

                // Show warning only when setting differs from what was applied at startup
                if (MeteorMixinCanceller.needsRestart(settings.isMeteorFix())) {
                    widgets.add(createSectionHeader("\u00A7e\u26A0 Requires game restart to take effect"));
                }
                //?}
            }
        }

        // Privacy & Security Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lPrivacy & Security"));

        // Tracks the "Managed by X" header most recently emitted so adjacent managed
        // controls sharing a manager don't repeat it; null once a real control breaks the run.
        String lastManagedHeader = null;

        // Chat signing \u2014 defer to No Chat Reports / No Prying Eyes when present.
        if (OpsecConfig.CHAT_SIGNING_MANAGED_EXTERNALLY) {
            String manager = OpsecConfig.chatSigningManagerName();
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.COMPAT_MANAGED_HEADER, manager)));
            lastManagedHeader = manager;
            widgets.add(createManagedToggle(OpsecLang.component(OpsecStrings.OPTION_CHAT_SIGNING),
                    OpsecLang.component(OpsecStrings.COMPAT_MANAGED_TOOLTIP, manager)));
        } else {
            widgets.add(cycleBuilder(SigningModeDisplay::getDisplayName, List.of(SpoofSettings.SigningMode.values()), settings.getSigningMode())
                    .withTooltip(v -> Tooltip.create(SigningModeDisplay.getTooltip(v)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_CHAT_SIGNING),
                    (button, value) -> { settings.setSigningMode(value); config.save(); }));
        }

        // Telemetry blocking \u2014 defer to No Chat Reports / No Prying Eyes when present.
        if (OpsecConfig.TELEMETRY_MANAGED_EXTERNALLY) {
            String manager = OpsecConfig.telemetryManagerName();
            // Reuse the header above when the control directly preceding shares this manager.
            if (!manager.equals(lastManagedHeader)) {
                widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.COMPAT_MANAGED_HEADER, manager)));
            }
            widgets.add(createManagedToggle(OpsecLang.component(OpsecStrings.OPTION_DISABLE_TELEMETRY),
                    OpsecLang.component(OpsecStrings.COMPAT_MANAGED_TOOLTIP, manager)));
        } else {
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isDisableTelemetry())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_DISABLE_TELEMETRY)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_DISABLE_TELEMETRY),
                    (button, value) -> { settings.setDisableTelemetry(value); config.save(); }));
        }

        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_PROTECTION), widgets);
    }
    
    private Tab createMiscTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Alerts & Logging Section
        widgets.add(createSectionHeader("\u00A7f\u00A7lAlerts & Logging"));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isShowAlerts())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_ALERTS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_SHOW_ALERTS),
                (button, value) -> { settings.setShowAlerts(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isShowToasts())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_TOASTS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_SHOW_TOASTS),
                (button, value) -> { settings.setShowToasts(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isLogDetections())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_LOG_DETECTIONS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_LOG_DETECTIONS),
                (button, value) -> { settings.setLogDetections(value); config.save(); }));

        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isDebugAlerts())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_DEBUG_ALERTS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_DEBUG_ALERTS),
                (button, value) -> { settings.setDebugAlerts(value); config.save(); }));

        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_MISC), widgets);
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
            
            for (Account account : accountManager.getAccounts()) {
                String displayName = account.getUsername() + (account.isCracked() ? " \u00A77(offline)" : "");
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
                
                String refreshInfo = (account instanceof SessionAccount sa && sa.hasRefreshToken()) ? "\n\u00A72Auto-refresh enabled" : "";
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
            Button refreshButton = Button.builder(OpsecLang.component(
                    accountManager.isRefreshing() ? OpsecStrings.BUTTON_REFRESHING : OpsecStrings.BUTTON_REFRESH_ALL
            ), button -> {
                if (accountManager.isRefreshing()) {
                    return; // Already refreshing
                }
                button.setMessage(OpsecLang.component(OpsecStrings.BUTTON_REFRESHING));
                button.active = false;
                accountManager.refreshAllAccounts((valid, invalid) -> {
                    refreshScreen();
                });
            }).size(230, 20)              .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_REFRESH_TOOLTIP)))

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
        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_SESSION), button -> {
            this.minecraft.setScreen(new AddAccountScreen(this));
        }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_SESSION_TOOLTIP)))
          .build());

        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_OFFLINE), button -> {
            this.minecraft.setScreen(new AddCrackedAccountScreen(this));
        }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_OFFLINE_TOOLTIP)))
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
        
        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_ACCOUNTS), widgets);
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
            super(0, 0, 230, 20, Component.empty());
            this.onAccountClick = onAccountClick;
            this.onRemoveClick = onRemoveClick;
            
            this.accountButton = Button.builder(Component.literal(accountName), btn -> onAccountClick.run())
                    .size(150, 20)
                    .tooltip(Tooltip.create(Component.literal(tooltip)))
                    .build();
            
            this.removeButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_REMOVE), btn -> onRemoveClick.run())
                    .size(55, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_REMOVE_TOOLTIP)))
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
        
        //? if >=26.1 {
        /*@Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            removeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

            // Poll mouse state for click detection since mouseClicked API changed
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
            wasMouseDown = isMouseDown;
        }*/
        //?} elif >=1.21.9 {
        /*@Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.render(graphics, mouseX, mouseY, partialTick);
            removeButton.render(graphics, mouseX, mouseY, partialTick);

            // Poll mouse state for click detection since mouseClicked API changed
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
            wasMouseDown = isMouseDown;
        }*/
        //?} else {
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.render(graphics, mouseX, mouseY, partialTick);
            removeButton.render(graphics, mouseX, mouseY, partialTick);
        }
        //?}
        
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
            super(0, 0, 230, 20, Component.empty());
            this.onImportAction = onImport;
            this.onExportAction = onExport;
            
            this.importButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_IMPORT), btn -> onImport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_IMPORT_TOOLTIP)))
                    .build();
            
            this.exportButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_EXPORT), btn -> onExport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_EXPORT_TOOLTIP)))
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
        
        //? if >=26.1 {
        /*@Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            exportButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

            // Poll mouse state for click detection since mouseClicked API changed
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
        //?} elif >=1.21.9 {
        /*@Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.render(graphics, mouseX, mouseY, partialTick);
            exportButton.render(graphics, mouseX, mouseY, partialTick);

            // Poll mouse state for click detection since mouseClicked API changed
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
        //?} else {
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.render(graphics, mouseX, mouseY, partialTick);
            exportButton.render(graphics, mouseX, mouseY, partialTick);
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
    
    // Custom widget for toggle all on/off row in whitelist
    private static class ToggleAllRowWidget extends AbstractWidget {
        private final Button enableAllButton;
        private final Button disableAllButton;
        private final Runnable onEnableAll;
        private final Runnable onDisableAll;
        //? if >=1.21.9 {
        /*private boolean wasMouseDown = false;*/
        //?}

        public ToggleAllRowWidget(Runnable onEnableAll, Runnable onDisableAll) {
            super(0, 0, 230, 20, Component.empty());
            this.onEnableAll = onEnableAll;
            this.onDisableAll = onDisableAll;

            this.enableAllButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_ENABLE_ALL), btn -> onEnableAll.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_ENABLE_ALL_TOOLTIP)))
                    .build();

            this.disableAllButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_DISABLE_ALL), btn -> onDisableAll.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_DISABLE_ALL_TOOLTIP)))
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
            enableAllButton.setX(startX);
            enableAllButton.setY(getY());
            disableAllButton.setX(startX + 100 + 5);
            disableAllButton.setY(getY());
        }

        //? if >=26.1 {
        /*@Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            enableAllButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            disableAllButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

            // Poll mouse state for click detection since mouseClicked API changed
            long windowHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (!isMouseDown && wasMouseDown) {
                // Mouse was just released
                if (enableAllButton.isMouseOver(mouseX, mouseY)) {
                    enableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onEnableAll.run();
                } else if (disableAllButton.isMouseOver(mouseX, mouseY)) {
                    disableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onDisableAll.run();
                }
            }
            wasMouseDown = isMouseDown;
        }*/
        //?} elif >=1.21.9 {
        /*@Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            enableAllButton.render(graphics, mouseX, mouseY, partialTick);
            disableAllButton.render(graphics, mouseX, mouseY, partialTick);

            // Poll mouse state for click detection since mouseClicked API changed
            long windowHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (!isMouseDown && wasMouseDown) {
                // Mouse was just released
                if (enableAllButton.isMouseOver(mouseX, mouseY)) {
                    enableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onEnableAll.run();
                } else if (disableAllButton.isMouseOver(mouseX, mouseY)) {
                    disableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onDisableAll.run();
                }
            }
            wasMouseDown = isMouseDown;
        }*/
        //?} else {
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            enableAllButton.render(graphics, mouseX, mouseY, partialTick);
            disableAllButton.render(graphics, mouseX, mouseY, partialTick);
        }
        //?}

        // Forward mouse clicks to child buttons
        //? if <1.21.9 {
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) { // Left click only
                if (enableAllButton.isMouseOver(mouseX, mouseY)) {
                    enableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onEnableAll.run();
                    return true;
                } else if (disableAllButton.isMouseOver(mouseX, mouseY)) {
                    disableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onDisableAll.run();
                    return true;
                }
            }
            return false;
        }
        //?}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            enableAllButton.updateNarration(output);
        }
    }

    private Tab createWhitelistTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();

        // Section header
        widgets.add(createSectionHeader("\u00A7f\u00A7lMod Whitelist"));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            CycleButton<SpoofSettings.WhitelistMode> modeButton = cycleBuilder(WhitelistModeDisplay::getDisplayName, List.of(SpoofSettings.WhitelistMode.values()), SpoofSettings.WhitelistMode.OFF)
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.EP_MANAGED_TOOLTIP)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE), (b, v) -> {});
            modeButton.active = false;
            widgets.add(modeButton);
        } else if (settings.isSpoofAsVanilla()) {
            // Spoofing as vanilla blocks ALL custom payloads, so the whitelist mode
            // is moot. Pin the cycle to OFF (Block All) and grey it out with a
            // tooltip explaining how to unlock it.
            CycleButton<SpoofSettings.WhitelistMode> lockedModeButton = cycleBuilder(WhitelistModeDisplay::getDisplayName, List.of(SpoofSettings.WhitelistMode.values()), SpoofSettings.WhitelistMode.OFF)
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE_LOCKED_TOOLTIP)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE), (b, v) -> {});
            lockedModeButton.active = false;
            widgets.add(lockedModeButton);
        } else {
            // Two-state mode selector: AUTO <-> CUSTOM. Block All (WhitelistMode.OFF)
            // is an internal override only exercised while spoof-as-vanilla is on
            // (handled in the locked branch above), so it isn't offered here.
            widgets.add(cycleBuilder(
                        WhitelistModeDisplay::getDisplayName,
                        List.of(SpoofSettings.WhitelistMode.AUTO, SpoofSettings.WhitelistMode.CUSTOM),
                        settings.getWhitelistMode() == SpoofSettings.WhitelistMode.OFF
                            ? SpoofSettings.WhitelistMode.AUTO
                            : settings.getWhitelistMode())
                    .withTooltip(v -> Tooltip.create(WhitelistModeDisplay.getTooltip(v)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE),
                        (button, value) -> {
                            settings.setWhitelistMode(value);
                            config.save();
                            refreshScreen();
                    }));

            if (settings.getWhitelistMode() == SpoofSettings.WhitelistMode.AUTO) {
                List<ModContainer> whitelistableMods = getWhitelistableMods();
                for (ModContainer mod : whitelistableMods) {
                    String modId = mod.getMetadata().getId();
                    String modName = mod.getMetadata().getName();
                    ModRegistry.ModInfo info = ModRegistry.getModInfo(modId);
                    boolean hasChannels = info != null && info.hasChannels();
                    if (hasChannels) {
                        widgets.add(createSectionHeader("\u00A7a" + modName + " \u00A77(" + info.getChannels().size() + " channels)"));
                    } else if (ModRegistry.isInDependencyClosure(modId)) {
                        widgets.add(createSectionHeader("\u00A7a" + modName + OpsecLang.tr(OpsecStrings.WHITELIST_SUFFIX_REQUIRED)));
                    }
                }
            } else if (settings.getWhitelistMode() == SpoofSettings.WhitelistMode.CUSTOM) {
                // ON mode: existing manual toggle UI
                widgets.add(createSectionHeader("\u00A7f\u00A7lInstalled Mods"));

                List<ModContainer> whitelistableMods = getWhitelistableMods();
                widgets.add(new ToggleAllRowWidget(
                    () -> {
                        for (ModContainer m : whitelistableMods) {
                            settings.getWhitelistedMods().add(m.getMetadata().getId());
                        }
                        config.save();
                        refreshScreen();
                    },
                    () -> {
                        settings.getWhitelistedMods().clear();
                        config.save();
                        refreshScreen();
                    }
                ));

                for (ModContainer mod : whitelistableMods) {
                    String modId = mod.getMetadata().getId();
                    String modName = mod.getMetadata().getName();

                    boolean isExplicit = settings.getWhitelistedMods().contains(modId);
                    // Locked ON when pulled in via dep closure; user must release via the depender.
                    boolean isImplicit = !isExplicit && ModRegistry.isInDependencyClosure(modId);
                    final String finalModId = modId;

                    Component tooltip = isImplicit
                        ? OpsecLang.component(OpsecStrings.WHITELIST_TOOLTIP_REQUIRED_BY,
                            finalModId, ModRegistry.resolveRequiringModName(modId))
                        : Component.literal(finalModId);

                    CycleButton<Boolean> modButton = cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), isExplicit || isImplicit)
                        .withTooltip(v -> Tooltip.create(tooltip))
                        .create(0, 0, 230, 20, Component.literal(modName),
                            (button, value) -> {
                                if (value) {
                                    settings.getWhitelistedMods().add(finalModId);
                                } else {
                                    settings.getWhitelistedMods().remove(finalModId);
                                }
                                config.save();
                                refreshScreen();
                        });
                    if (isImplicit) modButton.active = false;
                    widgets.add(modButton);
                }
            }
        }

        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_WHITELIST), widgets);
    }
    
    /**
     * Get mods that can be whitelisted.
     * Only shows mods that have registered translation keys, network channels, or keybinds.
     * This filters out libraries and internal mods that don't expose content.
     */
    private static List<ModContainer> getWhitelistableMods() {
        // On-demand scan to ensure all mods with language files are registered
        // This catches mods that the mixin couldn't detect
        ensureModsScanned();
        
        List<ModContainer> result = FabricLoader.getInstance().getAllMods().stream()
            .filter(mod -> {
                String id = mod.getMetadata().getId();
                if (ModRegistry.PLATFORM_MODS.contains(id)) return false;
                // Only top-level installed jars; JIJ children show up via their host's aggregate.
                if (mod.getContainingMod().isPresent()) return false;
                return ModRegistry.hasTrackableContentIncludingJij(id);
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

            if (ModRegistry.PLATFORM_MODS.contains(modId)) continue;
            // Skip JIJ children: their lang already loads via their host's mixin.
            if (mod.getContainingMod().isPresent()) continue;
            
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
    
    private CycleButton<Boolean> createEPManagedToggle(Component label) {
        return createManagedToggle(label, OpsecLang.component(OpsecStrings.EP_MANAGED_TOOLTIP));
    }

    /** A greyed-out, inactive toggle standing in for a feature another mod manages. */
    private CycleButton<Boolean> createManagedToggle(Component label, Component tooltip) {
        CycleButton<Boolean> button = cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), false)
                .withTooltip(v -> Tooltip.create(tooltip))
                .create(0, 0, 230, 20, label, (b, v) -> {});
        button.active = false;
        return button;
    }

    private StringWidget createSectionHeader(String text) {
        //? if >=1.21.6 {
        Component component = Component.literal(text);
        // Calculate actual text width and use that for the widget
        int textWidth = Minecraft.getInstance().font.width(component);
        return new StringWidget(textWidth, 20, component, Minecraft.getInstance().font);
        //?} else
        /*return new StringWidget(230, 20, Component.literal(text), Minecraft.getInstance().font);*/
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
    
    private double getCurrentScrollOffset() {
        Tab currentTab = this.tabManager.getCurrentTab();
        if (currentTab instanceof WidgetTab widgetTab) {
            return widgetTab.getScrollAmount();
        }
        return 0;
    }

    private void refreshScreen() {
        int tabIndex = getCurrentTabIndex();
        double scroll = getCurrentScrollOffset();
        this.minecraft.setScreen(new OpsecConfigScreen(this.parent, tabIndex, scroll));
    }

    //? if <1.20.2 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1: Screen.render does NOT auto-invoke renderBackground and does NOT
        // route Tab body widgets through the renderables list — handle both here.
        // (renderBackground is 1-arg on 1.20.1; gained mouseX/mouseY/partialTick in 1.20.2.)
        syncManagedBody();
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///?} elif <1.20.3 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.2: same need as 1.20.1 (Tab body widgets aren't auto-routed since
        // ScrollableWidgetList isn't an AbstractWidget yet), but renderBackground
        // now takes the full (graphics, mouseX, mouseY, partialTick) signature.
        syncManagedBody();
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///?}

    //? if <1.20.3 {
    /*private void syncManagedBody() {
        ScrollableWidgetList desired = null;
        Tab current = this.tabManager.getCurrentTab();
        if (current instanceof WidgetTab wt) desired = wt.scrollableList;
        if (this.managedBody == desired) return;
        if (this.managedBody != null) this.removeWidget(this.managedBody);
        this.managedBody = desired;
        if (this.managedBody != null) {
            sizeManagedBody();
            this.managedBody.setScrollAmount(this.scrollOffset);
            this.addRenderableWidget(this.managedBody);
        }
    }

    private void sizeManagedBody() {
        if (this.managedBody == null || this.tabWidget == null) return;
        int tabBottom = this.tabWidget.getRectangle().bottom();
        int bodyHeight = this.height - 36 - tabBottom;
        this.managedBody.updateSize(this.width, bodyHeight, tabBottom, tabBottom + bodyHeight);
    }
    *///?}

    @Override
    protected void repositionElements() {
        if (this.tabWidget != null) {
            //? if >=26.1 {
            /*this.tabWidget.updateWidth(this.width);*/
            //?} else {
            this.tabWidget.setWidth(this.width);
            //?}
            this.tabWidget.arrangeElements();
            int tabBottom = this.tabWidget.getRectangle().bottom();
            ScreenRectangle screenRect = new ScreenRectangle(0, tabBottom, this.width, this.height - 36 - tabBottom);
            this.tabManager.setTabArea(screenRect);
            this.layout.setHeaderHeight(tabBottom);
            this.layout.arrangeElements();
            //? if <1.20.3 {
            /*sizeManagedBody();
            *///?}
        }
        // Reposition version label to match current screen height
        if (this.versionLabel != null) {
            int labelHeight = 10;
            int footerY = this.height - 36;
            int labelY = footerY + (36 - labelHeight) / 2;
            this.versionLabel.setX(6);
            this.versionLabel.setY(labelY + 2);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Update reset button state based on current tab (Accounts tab = index 3)
        if (this.resetButton != null) {
            int currentTabIndex = getCurrentTabIndex();
            boolean isAccountsTab = currentTabIndex == 3;
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
        //? if >=1.21.9
        pollVersionLabelClick();
    }

    //? if <1.21.9 {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverVersionLabel(mouseX, mouseY)) {
            openReleaseUrl();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }*/
    //?}

    private boolean isOverVersionLabel(double mouseX, double mouseY) {
        if (!versionOutdated || versionLabel == null) return false;
        return mouseX >= versionLabel.getX() && mouseX < versionLabel.getX() + versionLabel.getWidth()
                && mouseY >= versionLabel.getY() && mouseY < versionLabel.getY() + versionLabel.getHeight();
    }

    private boolean versionLabelMouseWasDown = false;

    private void pollVersionLabelClick() {
        if (!versionOutdated || versionLabel == null) return;
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        boolean isDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!isDown && versionLabelMouseWasDown) {
            double[] mx = new double[1], my = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, mx, my);
            double scale = this.minecraft.getWindow().getGuiScale();
            double guiX = mx[0] / scale;
            double guiY = my[0] / scale;
            if (isOverVersionLabel(guiX, guiY)) {
                openReleaseUrl();
            }
        }
        versionLabelMouseWasDown = isDown;
    }

    private void openReleaseUrl() {
        try {
            //? if >=1.21.11 {
            /*net.minecraft.util.Util.getPlatform().openUri(UpdateChecker.getReleaseUrl());*/
            //?} else {
            net.minecraft.Util.getPlatform().openUri(UpdateChecker.getReleaseUrl());
            //?}
        } catch (Exception e) {
            Opsec.LOGGER.warn("[OpSec] Failed to open release URL: {}", e.getMessage());
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
            return scrollableList.currentScrollAmount();
        }
    }
    //?} else if >=1.20.3 {
    /*// === Scrollable Tab implementation extending GridLayoutTab for compatibility ===
    // GridLayoutTab provides default getTabExtraNarration() implementation
    private class WidgetTab extends GridLayoutTab {
        private final ScrollableWidgetList scrollableList;

        public WidgetTab(Component title, List<AbstractWidget> widgets) {
            super(title);
            this.scrollableList = new ScrollableWidgetList(Minecraft.getInstance(), 300, 200, 0, widgets);
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
            consumer.accept(scrollableList);
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
            scrollableList.setSize(rectangle.width(), rectangle.height());
            scrollableList.setPosition(rectangle.left(), rectangle.top());
            scrollableList.setScrollAmount(scrollOffset);
        }

        public double getScrollAmount() {
            return scrollableList.currentScrollAmount();
        }
    }*/
    //?} else {
    /*// === 1.20.1 / 1.20.2: AbstractSelectionList lacks setSize/setPosition and is not an
    // AbstractWidget. The visitChildren signature accepts a raw widget consumer that
    // we can no-op (no widget is added — content is drawn manually via doLayout).
    private class WidgetTab extends GridLayoutTab {
        private final ScrollableWidgetList scrollableList;

        public WidgetTab(Component title, List<AbstractWidget> widgets) {
            super(title);
            this.scrollableList = new ScrollableWidgetList(Minecraft.getInstance(), 300, 200, 0, widgets);
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
            // 1.20.1: ScrollableWidgetList is not an AbstractWidget — skip child visit.
            // Tab content still renders via the scrollableList field directly.
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
            // 1.20.1: setSize/setPosition were inlined as field assignments; use the
            // protected fields on AbstractSelectionList directly via reflection-free
            // helper. Since the list will be rendered manually, we just record offset.
            scrollableList.setScrollAmount(scrollOffset);
        }

        public double getScrollAmount() {
            return scrollableList.currentScrollAmount();
        }
    }
    *///?}

    // === Scrollable Widget List ===
    private static class ScrollableWidgetList extends ContainerObjectSelectionList<ScrollableWidgetList.WidgetEntry> {
        public ScrollableWidgetList(Minecraft minecraft, int width, int height, int top, List<AbstractWidget> widgets) {
            //? if >=1.20.3 {
            super(minecraft, width, height, top, 25);
            //?} else {
            /*// 1.20.1 / 1.20.2: 6-arg constructor including y1 (top + height).
            super(minecraft, width, height, top, top + height, 25);
            *///?}
            this.centerListVertically = false;
            //? if <1.20.2 {
            /*// AbstractSelectionList draws fading dark strips above y0 and below y1
            // by default. Above y0 = tabBottom would obscure the TabNavigationBar's
            // header bar; disable so the header and footer render unobscured.
            this.setRenderTopAndBottom(false);
            *///?}

            for (AbstractWidget widget : widgets) {
                //? if >=1.21.6
                this.addEntry(new WidgetEntry(widget, this));
                //? if <1.21.6
                /*this.addEntry(new WidgetEntry(widget));*/
            }
        }

        //? if <1.21.6 {
        /*private double trackedScroll = 0;

        @Override
        public void setScrollAmount(double scrollAmount) {
            super.setScrollAmount(scrollAmount);
            this.trackedScroll = scrollAmount;
        }*/
        //?}

        public double currentScrollAmount() {
            //? if >=1.21.6
            return this.scrollAmount();
            //? if <1.21.6
            /*return this.trackedScroll;*/
        }

        @Override
        public int getRowWidth() {
            return 220;
        }

        //? if >=1.21.2 {
        @Override
        protected int scrollBarX() {
            return this.getX() + this.getWidth() / 2 + 124;
        }
        //?} else if >=1.20.3 {
        /*@Override
        protected int getScrollbarPosition() {
            return this.getX() + this.getWidth() / 2 + 124;
        }*/
        //?} else {
        /*@Override
        protected int getScrollbarPosition() {
            // 1.20.1 / 1.20.2: AbstractSelectionList uses protected x0/width fields directly,
            // not getX()/getWidth() accessors (those came in 1.20.3 with the AbstractWidget
            // layout API alignment).
            return this.x0 + this.width / 2 + 124;
        }
        *///?}
        

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

            //? if >=26.1 {
            /*@Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                // Center the widget horizontally on the screen
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int centerX = (screenWidth - widget.getWidth()) / 2;
                widget.setX(centerX);
                widget.setY(this.getContentY());
                widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }*/
            //?} elif >=1.21.9 {
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
    
    /**
     * Display helper for WhitelistMode enum.
     */
    private static class WhitelistModeDisplay {
        public static Component getDisplayName(SpoofSettings.WhitelistMode mode) {
            return switch (mode) {
                case OFF -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_BLOCK_ALL);
                case AUTO -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_AUTO);
                case CUSTOM -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_CUSTOM);
            };
        }

        public static Component getTooltip(SpoofSettings.WhitelistMode mode) {
            return switch (mode) {
                case OFF -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_BLOCK_ALL_TOOLTIP);
                case AUTO -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_AUTO_TOOLTIP);
                case CUSTOM -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_CUSTOM_TOOLTIP);
            };
        }
    }

    /**
     * Display helper for SigningMode enum.
     * Controls whether chat messages are cryptographically signed.
     */
    private static class SigningModeDisplay {
        public static Component getDisplayName(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> Component.literal("\u00A7aON");  // Green - default, chat works everywhere
                case OFF  -> Component.literal("\u00A7eOFF"); // Yellow - privacy at the cost of chat on strict servers
            };
        }

        public static Component getTooltip(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> OpsecLang.component(OpsecStrings.CHATSIGNING_SIGN_TOOLTIP);
                case OFF  -> OpsecLang.component(OpsecStrings.CHATSIGNING_OFF_TOOLTIP);
            };
        }
    }
}

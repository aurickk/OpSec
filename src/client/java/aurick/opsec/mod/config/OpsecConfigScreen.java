package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
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
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class OpsecConfigScreen extends Screen {
    private static final Function<Boolean, Component> COLORED_BOOL_TO_TEXT = b -> 
        Boolean.TRUE.equals(b) 
            ? Component.literal("\u00A7aON") 
            : Component.literal("\u00A7cOFF");
    
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
            createMiscTab(settings)
        );
        
        this.tabWidget = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(this.tabs.toArray(new Tab[0]))
                .build();
        this.addRenderableWidget(this.tabWidget);
        this.tabWidget.selectTab(this.currentTab, false);
        
        // Create footer buttons
        this.resetButton = Button.builder(Component.translatable("controls.reset"), button -> {
            SpoofSettings defaults = new SpoofSettings();
            settings.copyFrom(defaults);
            config.save();
            refreshScreen();
        }).width(150).build();
        
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
          .tooltip(Tooltip.create(Component.literal("Deletes all cached server resource packs")))
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

            //? if <1.21.6 {
            /*@Override
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
            }*/
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

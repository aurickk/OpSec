package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.detection.SignExploitDetector;
import incognito.mod.protection.AnvilExploitTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept AnvilScreen initialization and analyze item Components
 * BEFORE they are resolved to strings.
 * 
 * This gives us Component access for anvils, allowing proper spoofing just like signs.
 * 
 * Flow:
 * 1. Server places item with Component.keybind("key.forward") in anvil
 * 2. AnvilScreen opens, we intercept and analyze the Component
 * 3. We store: "W" (resolved) -> "key.forward" (original key) in AnvilExploitTracker
 * 4. When user submits "Z" (custom keybind), we know to spoof to "W" (vanilla default)
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
    
    @Shadow
    private EditBox name;
    
    @Unique
    private boolean incognito$hasAnalyzed = false;
    
    @Unique
    private boolean incognito$hasAlerted = false;
    
    @Unique
    private ItemStack incognito$lastAnalyzedItem = ItemStack.EMPTY;
    
    // Maximum recursion depth for component checking (prevents stack overflow attacks)
    @Unique
    private static final int MAX_COMPONENT_DEPTH = 16;
    
    /**
     * Intercept subInit which is called during screen initialization.
     * AnvilScreen overrides this method from ItemCombinerScreen.
     */
    @Inject(method = "subInit", at = @At("TAIL"))
    private void incognito$onSubInit(CallbackInfo ci) {
        try {
            incognito$hasAnalyzed = false;
            incognito$hasAlerted = false;
            incognito$lastAnalyzedItem = ItemStack.EMPTY;
            incognito$lastNameFieldValue = "";
            AnvilExploitTracker.clearMapping();
            incognito$analyzeAnvilItem("subInit");
            incognito$checkNameFieldForExploits("subInit");
        } catch (Exception e) {
            Incognito.LOGGER.error("[Incognito] Exception in AnvilScreen subInit hook", e);
        }
    }
    
    
    @Unique
    private void incognito$checkNameFieldForExploits(String source) {
        if (this.name == null) return;
        
        String nameValue = this.name.getValue();
        if (nameValue == null || nameValue.isEmpty()) return;
        
        if (SignExploitDetector.containsSuspiciousResolvedValue(nameValue)) {
            AnvilExploitTracker.setHasExploitContent(true);
            AnvilExploitTracker.setOriginalResolvedName(nameValue);
            
            if (!incognito$hasAlerted) {
                incognito$hasAlerted = true;
                boolean protectionEnabled = IncognitoConfig.getInstance().shouldBlockTranslationExploit();
                String summary = "Name field: '" + nameValue + "'" + (protectionEnabled ? "" : " (protection OFF)");
                PrivacyLogger.alertTranslationExploitDetected(PrivacyLogger.ExploitSource.ANVIL, summary);
            }
            
            boolean protectionEnabled = IncognitoConfig.getInstance().shouldBlockTranslationExploit();
            if (protectionEnabled && IncognitoConfig.getInstance().shouldSpoofTranslationExploit()) {
                String spoofedText = SignExploitDetector.spoofText(nameValue);
                if (!spoofedText.equals(nameValue)) {
                    Incognito.LOGGER.debug("[Incognito] Spoofing anvil name field: '{}' -> '{}'", nameValue, spoofedText);
                    this.name.setValue(spoofedText);
                }
            } else if (protectionEnabled) {
                String sanitized = SignExploitDetector.sanitizeText(nameValue);
                if (!sanitized.equals(nameValue)) {
                    Incognito.LOGGER.debug("[Incognito] Blocking anvil name field: '{}' -> '{}'", nameValue, sanitized);
                    this.name.setValue(sanitized);
                }
            }
        }
    }
    
    @Unique
    private String incognito$lastNameFieldValue = "";
    
    /**
     * Hook into renderBg to catch when items arrive after screen init.
     * renderBg is called every frame, so we track if we've already analyzed.
     * 
     * ALWAYS runs detection regardless of protection status (for alerts).
     */
    @Inject(method = "renderBg", at = @At("HEAD"))
    private void incognito$onRenderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        try {
        // Always run detection regardless of protection status (for alerts)
        AnvilScreen screen = (AnvilScreen)(Object)this;
        AnvilMenu menu = screen.getMenu();
        ItemStack currentItem = menu.getSlot(0).getItem();
        
        // Check if item changed or if we haven't analyzed yet
        if (!incognito$hasAnalyzed || !ItemStack.matches(currentItem, incognito$lastAnalyzedItem)) {
            if (!currentItem.isEmpty()) {
                incognito$analyzeAnvilItem("renderBg");
                incognito$hasAnalyzed = true;
                incognito$lastAnalyzedItem = currentItem.copy();
            }
            }
            
            // Also check if name field was modified externally (by server packets)
            if (this.name != null) {
                String currentNameValue = this.name.getValue();
                if (currentNameValue != null && !currentNameValue.equals(incognito$lastNameFieldValue)) {
                    incognito$lastNameFieldValue = currentNameValue;
                    // Only recheck if we haven't already alerted (to avoid spam)
                    if (!incognito$hasAlerted && !currentNameValue.isEmpty()) {
                        incognito$checkNameFieldForExploits("renderBg");
                    }
                }
            }
        } catch (Exception e) {
            // Log but don't spam - only log once
            Incognito.LOGGER.debug("[Incognito] Exception in AnvilScreen renderBg hook: {}", e.getMessage());
        }
    }
    
    @Unique
    private void incognito$analyzeAnvilItem(String source) {
        AnvilExploitTracker.clearMapping();
        
        AnvilScreen screen = (AnvilScreen)(Object)this;
        AnvilMenu menu = screen.getMenu();
        ItemStack item = menu.getSlot(0).getItem();
        
        if (item.isEmpty()) return;
        
        Component hoverName = item.getHoverName();
        if (hoverName == null) return;
        
        AnvilExploitTracker.setOriginalResolvedName(hoverName.getString());
        incognito$analyzeComponentWithDepth(hoverName, 0);
        
        boolean protectionEnabled = IncognitoConfig.getInstance().shouldBlockTranslationExploit();
        
        if (AnvilExploitTracker.hasExploitContent()) {
            if (!incognito$hasAlerted) {
                incognito$hasAlerted = true;
                
                StringBuilder summary = new StringBuilder();
                for (AnvilExploitTracker.ComponentInfo info : AnvilExploitTracker.getResolvedMapping().values()) {
                    if (summary.length() > 0) summary.append(", ");
                    summary.append("'").append(info.resolvedValue()).append("'→'").append(info.vanillaDefault()).append("'");
                }
                
                if (!protectionEnabled) {
                    summary.append(" (protection OFF)");
                }
                
                PrivacyLogger.alertTranslationExploitDetected(PrivacyLogger.ExploitSource.ANVIL, summary.toString());
            }
            
            if (protectionEnabled && IncognitoConfig.getInstance().shouldSpoofTranslationExploit() && this.name != null) {
                String currentText = this.name.getValue();
                String spoofedText = AnvilExploitTracker.getSpoofedText(currentText);
                if (!spoofedText.equals(currentText)) {
                    this.name.setValue(spoofedText);
                }
            }
        }
    }
    
    @Unique
    private void incognito$analyzeComponentWithDepth(Component component, int depth) {
        if (component == null) return;
        
        if (depth > MAX_COMPONENT_DEPTH) {
            AnvilExploitTracker.setHasExploitContent(true);
            AnvilExploitTracker.addMapping(component.getString(), 
                new AnvilExploitTracker.ComponentInfo("depth_limit_exceeded", component.getString(), "", false));
            return;
        }
        
        var contents = component.getContents();
        
        if (contents instanceof KeybindContents keybind) {
            String key = keybind.getName();
            String resolved = component.getString();
            String vanillaDefault = AnvilExploitTracker.getVanillaDefaultOrElse(key, resolved);
            
            SignExploitDetector.DetectionResult detection = SignExploitDetector.analyzeKeybind(key);
            if (detection.type() != SignExploitDetector.DetectionType.NONE) {
                AnvilExploitTracker.setHasExploitContent(true);
                AnvilExploitTracker.addMapping(resolved, new AnvilExploitTracker.ComponentInfo(key, resolved, vanillaDefault, true));
            }
        }
        
        if (contents instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            String resolved = component.getString();
            
            SignExploitDetector.DetectionResult detection = SignExploitDetector.analyzeText(key);
            if (detection.type() != SignExploitDetector.DetectionType.NONE) {
                AnvilExploitTracker.setHasExploitContent(true);
                String vanillaDefault = detection.type() == SignExploitDetector.DetectionType.MOD_DETECTION ? key : resolved;
                AnvilExploitTracker.addMapping(resolved, new AnvilExploitTracker.ComponentInfo(key, resolved, vanillaDefault, false));
            }
            
            Object[] args = translatable.getArgs();
            for (Object arg : args) {
                if (arg instanceof Component argComponent) {
                    incognito$analyzeComponentWithDepth(argComponent, depth + 1);
                }
            }
        }
        
        String contentTypeName = contents.getClass().getSimpleName();
        if (contentTypeName.contains("Nbt") || contentTypeName.contains("Score") || contentTypeName.contains("Selector")) {
            AnvilExploitTracker.setHasExploitContent(true);
            AnvilExploitTracker.addMapping(component.getString(), 
                new AnvilExploitTracker.ComponentInfo(contentTypeName, component.getString(), "", false));
        }
        
        for (Component sibling : component.getSiblings()) {
            incognito$analyzeComponentWithDepth(sibling, depth + 1);
        }
    }
}

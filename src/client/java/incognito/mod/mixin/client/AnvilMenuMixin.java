package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.detection.SignExploitDetector;
import incognito.mod.protection.AnvilExploitTracker;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Mixin to protect against anvil translation vulnerability.
 * 
 * The exploit works similarly to the sign exploit:
 * 1. Server places an item with a translatable display name in the anvil
 * 2. When the player edits/submits the name, the client resolves translation keys
 * 3. The resolved text (containing language, keybinds, or mod info) is sent to server
 * 
 * This mixin now works WITH AnvilScreenMixin to get Component access:
 * - AnvilScreenMixin intercepts screen init and analyzes the item's Component
 * - It stores mapping: resolved_value -> (original_key, vanilla_default)
 * - This mixin uses that mapping for proper spoofing (just like signs!)
 * 
 * Two modes:
 * - SPOOF: Replace with vanilla defaults (keybinds) or translation keys (mods)
 * - BLOCK: Clear suspicious content to empty strings
 * 
 * @see AnvilScreenMixin
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    
    @Shadow
    @Nullable
    private String itemName;
    
    @Unique
    private boolean incognito$alreadyAlerted = false;
    
    /**
     * Modify the item name parameter before it's processed.
     * 
     * Uses Component mapping from AnvilScreenMixin for accurate spoofing.
     * Falls back to string-based detection if no mapping available.
     * 
     * Detection ALWAYS runs (for alerts), but modification only happens if protection is enabled.
     */
    @ModifyVariable(method = "setItemName", at = @At("HEAD"), argsOnly = true)
    private String incognito$processItemName(String name) {
        if (name == null) {
            return name;
        }
        
        boolean protectionEnabled = IncognitoConfig.getInstance().shouldBlockTranslationExploit();
        
        // First, check if AnvilScreenMixin detected exploit content via Component analysis
        boolean hasComponentMapping = AnvilExploitTracker.hasExploitContent();
        Map<String, AnvilExploitTracker.ComponentInfo> mapping = AnvilExploitTracker.getResolvedMapping();
        
        // Check if this name matches the original resolved name or any part of it
        String originalResolved = AnvilExploitTracker.getOriginalResolvedName();
        boolean matchesExploitContent = hasComponentMapping && originalResolved != null && 
            (name.equals(originalResolved) || containsAnyMappedValue(name, mapping));
        
        if (!matchesExploitContent) {
            // Fall back to string-based detection for edge cases
            // (e.g., if AnvilScreen wasn't intercepted properly)
            if (!SignExploitDetector.containsSuspiciousResolvedValue(name)) {
                return name;
            }
        }
        
        // Exploit detected - if protection is disabled, just log and alert but don't modify
        if (!protectionEnabled) {
            Incognito.LOGGER.info("[Incognito] DETECTED anvil exploit (protection OFF): '{}'", name);
            // Alert happens in AnvilScreenMixin, avoid duplicate alerts here
            return name;  // Return unmodified
        }
        
        // Protection is enabled - determine mode: spoof or block
        boolean shouldSpoof = IncognitoConfig.getInstance().shouldSpoofTranslationExploit();
        
        String result;
        if (shouldSpoof) {
            if (hasComponentMapping && !mapping.isEmpty()) {
                // SPOOF MODE with Component mapping (accurate, like signs!)
                result = spoofWithMapping(name, mapping);
                Incognito.LOGGER.info("[Incognito] SPOOF anvil (Component): '{}' -> '{}'", name, result);
            } else {
                // Fall back to string-based spoofing
                result = SignExploitDetector.spoofText(name);
                Incognito.LOGGER.info("[Incognito] SPOOF anvil (string): '{}' -> '{}'", name, result);
            }
        } else {
            // BLOCK MODE: Clear suspicious content to empty
            result = SignExploitDetector.sanitizeText(name);
            Incognito.LOGGER.info("[Incognito] BLOCK anvil: '{}' -> '{}'", name, result);
        }
        
        if (!result.equals(name)) {
            String contentSummary = "'" + name + "' → '" + result + "'";
            PrivacyLogger.alertTranslationExploitDetected(PrivacyLogger.ExploitSource.ANVIL, contentSummary);
            incognito$alreadyAlerted = true;
        }
        
        return result;
    }
    
    /**
     * Check if the input string contains any mapped resolved values.
     */
    @Unique
    private boolean containsAnyMappedValue(String input, Map<String, AnvilExploitTracker.ComponentInfo> mapping) {
        for (String resolved : mapping.keySet()) {
            if (input.contains(resolved)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Spoof the input using Component mapping from AnvilExploitTracker.
     * 
     * For keybinds: Replace resolved value with vanilla default
     * For mod translations: Replace resolved value with translation key (hides mod existence)
     */
    @Unique
    private String spoofWithMapping(String input, Map<String, AnvilExploitTracker.ComponentInfo> mapping) {
        String result = input;
        
        for (Map.Entry<String, AnvilExploitTracker.ComponentInfo> entry : mapping.entrySet()) {
            String resolved = entry.getKey();
            AnvilExploitTracker.ComponentInfo info = entry.getValue();
            
            if (result.contains(resolved)) {
                // For keybinds: use vanilla default (e.g., "Z" -> "W")
                // For mod translations: use the key itself (e.g., "Meteor" -> "meteor.client.name")
                String replacement = info.isKeybind() ? info.vanillaDefault() : info.originalKey();
                result = result.replace(resolved, replacement);
                
                Incognito.LOGGER.debug("[Incognito] Spoofed '{}' -> '{}' (key={}, isKeybind={})", 
                    resolved, replacement, info.originalKey(), info.isKeybind());
            }
        }
        
        return result;
    }
    
    /**
     * Additional check: detect and log suspicious item names.
     * Always runs detection (for logging) regardless of protection status.
     */
    @Inject(method = "setItemName", at = @At("HEAD"))
    private void incognito$checkItemName(String name, CallbackInfoReturnable<Boolean> cir) {
        // Reset alert flag for new names
        incognito$alreadyAlerted = false;
        
        if (name == null) {
            return;
        }
        
        // Log detection for debugging (always runs, regardless of protection status)
        boolean hasComponentMapping = AnvilExploitTracker.hasExploitContent();
        if (hasComponentMapping || SignExploitDetector.containsSuspiciousResolvedValue(name)) {
            boolean protectionEnabled = IncognitoConfig.getInstance().shouldBlockTranslationExploit();
            boolean isSpoof = IncognitoConfig.getInstance().shouldSpoofTranslationExploit();
            Incognito.LOGGER.debug("[Incognito] Detected suspicious anvil content, protection: {}, mode: {}, hasComponent: {}", 
                protectionEnabled ? "ON" : "OFF", isSpoof ? "SPOOF" : "BLOCK", hasComponentMapping);
        }
    }
}


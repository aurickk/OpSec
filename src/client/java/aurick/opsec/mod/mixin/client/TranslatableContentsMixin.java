package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.detection.ExploitContext;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Intercepts translation key resolution with context-aware protection.
 * 
 * Uses ThreadLocal context detection to only protect in exploitable contexts:
 * - Normal mod UI: Allow normal resolution (so mod menus work)
 * - Sign/Anvil/Book screens: Block mod translation keys
 * 
 * Whitelist priority:
 * 1. Vanilla keys - Always allowed
 * 2. Server resource pack keys - Session whitelisted (prevents anti-spoof detection)
 * 3. Mod/Unknown keys - Blocked (return raw key)
 * 
 * This prevents servers from detecting installed mods via translation key probing.
 */
@Mixin(TranslatableContents.class)
public class TranslatableContentsMixin {
    
    /**
     * Intercept translation key resolution (single parameter version).
     * Detection and alerts work even when protection is disabled.
     */
    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;"),
        require = 0
    )
    private String opsec$interceptTranslation(Language instance, String key, Operation<String> original) {
        // Vanilla translation key - always allow (no exploit risk)
        if (ModRegistry.isVanillaTranslationKey(key)) {
            return original.call(instance, key);
        }
        
        // Server resource pack key - allow (prevents anti-spoof detection)
        if (ModRegistry.isServerPackTranslationKey(key)) {
            return original.call(instance, key);
        }
        
        // Whitelisted mod key - allow when whitelist is enabled
        if (ModRegistry.isWhitelistedTranslationKey(key)) {
            return original.call(instance, key);
        }
        
        // Not in exploitable context - allow normal resolution
        if (!ExploitContext.isInExploitableContext()) {
            return original.call(instance, key);
        }
        
        // In exploitable context - always notify detection (header alert)
        TranslationProtectionHandler.notifyExploitDetected();
        
        // Get original value
        String originalValue = original.call(instance, key);
        
        // If protection is disabled, allow normal resolution but still log detection
        if (!OpsecConfig.getInstance().isTranslationProtectionEnabled()) {
            TranslationProtectionHandler.logDetection(key, originalValue, originalValue);
            return originalValue;
        }
        
        // Protection enabled - block mod translation
        // Only send detail if value would have changed
        if (!originalValue.equals(key)) {
            TranslationProtectionHandler.sendDetail(key, originalValue, key);
        }
        
        // Always log detection (even if value unchanged)
        TranslationProtectionHandler.logDetection(key, originalValue, key);
        
        return key;  // Return raw key, don't reveal mod translation
    }
    
    /**
     * Intercept translation key resolution (two parameter version with fallback).
     * Detection and alerts work even when protection is disabled.
     */
    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        require = 0
    )
    private String opsec$interceptTranslationWithFallback(
            Language instance, String key, String fallback, Operation<String> original) {
        // Vanilla translation key - always allow (no exploit risk)
        if (ModRegistry.isVanillaTranslationKey(key)) {
            return original.call(instance, key, fallback);
        }
        
        // Server resource pack key - allow (prevents anti-spoof detection)
        if (ModRegistry.isServerPackTranslationKey(key)) {
            return original.call(instance, key, fallback);
        }
        
        // Whitelisted mod key - allow when whitelist is enabled
        if (ModRegistry.isWhitelistedTranslationKey(key)) {
            return original.call(instance, key, fallback);
        }
        
        // Not in exploitable context - allow normal resolution
        if (!ExploitContext.isInExploitableContext()) {
            return original.call(instance, key, fallback);
        }
        
        // In exploitable context - always notify detection (header alert)
        TranslationProtectionHandler.notifyExploitDetected();
        
        // Get original value
        String originalValue = original.call(instance, key, fallback);
        
        // If protection is disabled, allow normal resolution but still log detection
        if (!OpsecConfig.getInstance().isTranslationProtectionEnabled()) {
            TranslationProtectionHandler.logDetection(key, originalValue, originalValue);
            return originalValue;
        }
        
        // Protection enabled - block mod translation
        String spoofedValue = fallback != null ? fallback : key;
        
        // Only send detail if value would have changed
        if (!originalValue.equals(spoofedValue)) {
            TranslationProtectionHandler.sendDetail(key, originalValue, spoofedValue);
        }
        
        // Always log detection (even if value unchanged)
        TranslationProtectionHandler.logDetection(key, originalValue, spoofedValue);
        
        return spoofedValue;  // Return fallback or raw key
    }
}

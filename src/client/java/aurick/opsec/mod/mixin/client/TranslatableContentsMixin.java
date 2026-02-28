package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.ExploitContext;
import aurick.opsec.mod.protection.ForgeTranslations;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Intercepts TranslatableContents to block mod translation resolution.
 * 
 * Behavior by mode:
 * - VANILLA: Block all non-vanilla, non-resourcepack keys
 * - FABRIC: Block non-vanilla, non-resourcepack, non-whitelisted keys
 * - FORGE: Block non-vanilla, non-resourcepack keys; fabricate known Forge values
 */
@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsMixin {
    
    @Shadow @Final private String key;
    @Shadow @Final private String fallback;
    
    // Track the language instance when this was last decomposed
    @Shadow private Language decomposedWith;
    
    /**
     * Force re-decomposition when in exploit context.
     */
    @Inject(method = "decompose", at = @At("HEAD"))
    private void opsec$forceRedecompose(CallbackInfo ci) {
        // Only when in exploitable context
        if (!ExploitContext.isInExploitableContext()) return;
        
        // Always allow vanilla keys
        if (ModRegistry.isVanillaTranslationKey(key)) return;
        
        // Always allow server resource pack keys
        if (ModRegistry.isServerPackTranslationKey(key)) return;

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        // If protection is disabled, still detect/log but don't break cache
        if (!config.isTranslationProtectionEnabled()) {
            TranslationProtectionHandler.notifyExploitDetected();
            opsec$logDetection();
            return;
        }

        // VANILLA MODE: Block all mod keys
        if (settings.isVanillaMode()) {
            this.decomposedWith = null;
            TranslationProtectionHandler.notifyExploitDetected();
            opsec$logDetection();
            return;
        }
        
        // FABRIC MODE: Allow Fabric keys and whitelisted mod keys
        if (settings.isFabricMode()) {
            if (ModRegistry.isWhitelistedTranslationKey(key)) {
                // Still alert (server is probing) but no details since nothing changed
                TranslationProtectionHandler.notifyExploitDetected();
                return;
            }
            this.decomposedWith = null;
            TranslationProtectionHandler.notifyExploitDetected();
            opsec$logDetection();
            return;
        }
        
        // FORGE MODE: Fabricate known Forge keys, block others
        if (settings.isForgeMode()) {
            // For Forge keys: clear cache so LanguageMixin can fabricate the value
            // Without clearing, the cached (real) value would be used
            if (ForgeTranslations.isForgeKey(key)) {
                this.decomposedWith = null; // Force re-resolve to get fabricated value
                TranslationProtectionHandler.notifyExploitDetected();
                opsec$logForgeFabrication();
                return;
            }
            // Block non-Forge mod keys
            this.decomposedWith = null;
            TranslationProtectionHandler.notifyExploitDetected();
            opsec$logDetection();
            return;
        }
        
        // Fallback: Use whitelist behavior
        if (ModRegistry.isWhitelistedTranslationKey(key)) {
            // Still alert (server is probing) but no details since nothing changed
            TranslationProtectionHandler.notifyExploitDetected();
            return;
        }
        this.decomposedWith = null;
        TranslationProtectionHandler.notifyExploitDetected();
        opsec$logDetection();
    }
    
    @Unique
    private void opsec$logDetection() {
        String naturalFallback = fallback != null ? fallback : key;
        
        // Get the REAL translation value by accessing storage directly (bypass our mixin)
        String originalValue = opsec$getRealTranslation(key, naturalFallback);
        
        if (!originalValue.equals(naturalFallback)) {
            TranslationProtectionHandler.sendDetail(key, originalValue, naturalFallback);
        }
        TranslationProtectionHandler.logDetection(key, originalValue, naturalFallback);
    }
    
    @Unique
    private void opsec$logForgeFabrication() {
        String naturalFallback = fallback != null ? fallback : key;
        String fabricatedValue = ForgeTranslations.getTranslation(key);
        
        if (fabricatedValue != null) {
            // Show: 'original' → 'fabricated forge value'
            TranslationProtectionHandler.sendDetail(key, naturalFallback, fabricatedValue);
            TranslationProtectionHandler.logDetection(key, naturalFallback, fabricatedValue);
        }
    }
    
    /**
     * Get the real translation value by directly accessing ClientLanguage's storage map.
     * Uses {@link ClientLanguageAccessor} to bypass our LanguageMixin interception.
     */
    @Unique
    private String opsec$getRealTranslation(String translationKey, String defaultValue) {
        try {
            Language lang = Language.getInstance();
            if (lang instanceof ClientLanguageAccessor accessor) {
                Map<String, String> storage = accessor.opsec$getStorage();
                String value = storage.get(translationKey);
                return value != null ? value : defaultValue;
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Failed to get real translation for key '{}': {}",
                    translationKey, e.getMessage());
        }
        return defaultValue;
    }
}

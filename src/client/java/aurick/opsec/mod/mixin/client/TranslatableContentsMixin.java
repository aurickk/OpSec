package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.ExploitContext;
import aurick.opsec.mod.protection.ForgeTranslations;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

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
    
    // Cached reflection field for getting real translations
    @Unique
    private static Field storageField;
    
    /**
     * Force re-decomposition when in exploit context.
     * By clearing decomposedWith, we force decompose() to run again,
     * where LanguageMixin will block the resolution.
     */
    @Inject(
        method = "visit(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;",
        at = @At("HEAD"),
        require = 0
    )
    private <T> void opsec$forceRedecompose(FormattedText.ContentConsumer<T> consumer, CallbackInfoReturnable<Optional<T>> cir) {
        // Only when in exploitable context
        if (!ExploitContext.isInExploitableContext()) return;
        
        // Always allow vanilla keys
        if (ModRegistry.isVanillaTranslationKey(key)) return;
        
        // Always allow server resource pack keys
        if (ModRegistry.isServerPackTranslationKey(key)) return;
        
        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        
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
     * This bypasses our LanguageMixin interception.
     */
    @Unique
    @SuppressWarnings("unchecked")
    private String opsec$getRealTranslation(String translationKey, String defaultValue) {
        try {
            Language lang = Language.getInstance();
            if (lang instanceof ClientLanguage clientLang) {
                // Find the storage field - it may have different names in dev vs runtime
                if (storageField == null) {
                    // Search for a Map<String, String> field
                    for (Field field : ClientLanguage.class.getDeclaredFields()) {
                        if (Map.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            Object value = field.get(clientLang);
                            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                                // Check if it's String -> String
                                Map.Entry<?, ?> entry = map.entrySet().iterator().next();
                                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                                    storageField = field;
                                    break;
                                }
                            }
                        }
                    }
                }
                
                if (storageField != null) {
                    Map<String, String> storage = (Map<String, String>) storageField.get(clientLang);
                    String value = storage.get(translationKey);
                    return value != null ? value : defaultValue;
                }
            }
        } catch (Exception e) {
            // Fallback if reflection fails
        }
        return defaultValue;
    }
}

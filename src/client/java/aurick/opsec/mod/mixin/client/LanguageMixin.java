package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.ExploitContext;
import aurick.opsec.mod.protection.ForgeTranslations;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks translation resolution for mod keys when in exploit context.
 * 
 * Behavior by mode:
 * - VANILLA: Block all non-vanilla, non-resourcepack keys (no whitelist)
 * - FABRIC: Block non-vanilla, non-resourcepack, non-whitelisted keys (whitelist applies)
 * - FORGE: Block non-vanilla, non-resourcepack keys; fabricate known Forge values
 * 
 * TranslatableContentsMixin handles logging and detection notifications.
 */
@Mixin(ClientLanguage.class)
public abstract class LanguageMixin {
    
    /**
     * Block mod translation lookups in exploit context.
     * Return defaultValue (natural fallback) - as if key wasn't in language file.
     */
    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void opsec$blockTranslation(String key, String defaultValue, CallbackInfoReturnable<String> cir) {
        // Only block in exploitable contexts
        if (!ExploitContext.isInExploitableContext()) return;
        
        // Always allow vanilla keys
        if (ModRegistry.isVanillaTranslationKey(key)) return;
        
        // Always allow server resource pack keys
        if (ModRegistry.isServerPackTranslationKey(key)) return;

        // If protection is disabled, allow normal resolution
        if (!OpsecConfig.getInstance().isTranslationProtectionEnabled()) return;

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        
        // VANILLA MODE: Block all mod keys (no whitelist, no Fabric/Forge keys)
        if (settings.isVanillaMode()) {
            cir.setReturnValue(defaultValue != null ? defaultValue : key);
            return;
        }
        
        // FABRIC MODE: Allow Fabric keys and whitelisted mod keys
        if (settings.isFabricMode()) {
            if (ModRegistry.isWhitelistedTranslationKey(key)) return;
            cir.setReturnValue(defaultValue != null ? defaultValue : key);
            return;
        }
        
        // FORGE MODE: Fabricate known Forge keys, block everything else
        if (settings.isForgeMode()) {
            String forgeValue = ForgeTranslations.getTranslation(key);
            if (forgeValue != null) {
                // Return fabricated Forge translation
                cir.setReturnValue(forgeValue);
                return;
            }
            // Block non-Forge mod keys
            cir.setReturnValue(defaultValue != null ? defaultValue : key);
            return;
        }
        
        // Fallback: If no mode matches (spoofing disabled?), use default whitelist behavior
        if (ModRegistry.isWhitelistedTranslationKey(key)) return;
        cir.setReturnValue(defaultValue != null ? defaultValue : key);
    }
}

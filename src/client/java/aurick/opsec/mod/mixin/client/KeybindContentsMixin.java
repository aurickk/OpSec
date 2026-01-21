package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.ExploitContext;
import aurick.opsec.mod.protection.ForgeTranslations;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.tracking.ModRegistry;
import aurick.opsec.mod.util.KeybindDefaults;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

/**
 * Intercepts keybind resolution to protect user privacy.
 * 
 * Uses ThreadLocal context detection to only protect in exploitable contexts:
 * - Normal mod UI: Allow normal resolution
 * - Sign/Anvil/Book screens: Protect by returning cached defaults or raw key names
 * 
 * Whitelist priority:
 * 1. Vanilla keybinds - Return cached default value
 * 2. Server resource pack keybinds - Allow resolution (prevents anti-spoof detection)
 * 3. Mod/Unknown keybinds - Return raw key name
 * 
 * This prevents servers from detecting:
 * 1. User's custom keybind settings (vanilla keybinds)
 * 2. Installed mods (mod keybinds with any naming convention)
 * 
 * Note: Some mods use non-standard keybind names (e.g., "gui.xaero_toggle_slime"
 * instead of "key.xaero.toggle_slime"). We protect ALL keybinds regardless of
 * naming convention since anything in KeybindContents is a keybind by definition.
 */
@Mixin(KeybindContents.class)
public class KeybindContentsMixin {
    
    @Shadow @Final
    private String name;
    
    /**
     * Intercept keybind resolution with context-aware protection.
     * Detection and alerts work even when protection is disabled.
     * Protects ALL keybinds regardless of naming convention.
     */
    @WrapOperation(
        method = "getNestedComponent",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"),
        require = 0
    )
    private Object opsec$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
        // Not in exploitable context - allow normal resolution (no detection needed)
        if (!ExploitContext.isInExploitableContext()) {
            return original.call(supplier);
        }
        
        // Server resource pack keybind - allow (prevents anti-spoof detection)
        // Check this BEFORE triggering detection alerts
        if (ModRegistry.isServerPackTranslationKey(name)) {
            return original.call(supplier);
        }
        
        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        
        // FORGE MODE: Fabricate known Forge keys sent through keybind mechanism
        if (settings.isForgeMode() && ForgeTranslations.isForgeKey(name)) {
            String fabricatedValue = ForgeTranslations.getTranslation(name);
            if (fabricatedValue != null) {
                TranslationProtectionHandler.notifyExploitDetected();
                // Log: 'rawKey' → 'fabricatedValue'
                TranslationProtectionHandler.sendDetail(name, name, fabricatedValue);
                TranslationProtectionHandler.logDetection(name, name, fabricatedValue);
                return Component.literal(fabricatedValue);
            }
        }
        
        // Whitelisted mod keybind - allow but still show header alert
        if (ModRegistry.isWhitelistedKeybind(name)) {
            // Still alert (server is probing) but allow resolution since whitelisted
            TranslationProtectionHandler.notifyExploitDetected();
            return original.call(supplier);
        }
        
        // In exploitable context - notify detection (header alert)
        TranslationProtectionHandler.notifyExploitDetected();
        
        // Get original value
        Object originalResult = original.call(supplier);
        String originalValue = originalResult instanceof Component c ? c.getString() : originalResult.toString();
        
        // If protection is disabled, allow normal resolution but still log detection
        if (!OpsecConfig.getInstance().isTranslationProtectionEnabled()) {
            TranslationProtectionHandler.logDetection(name, originalValue, originalValue);
            return originalResult;
        }
        
        // Protection enabled - determine spoofed value
        String spoofedValue;
        if (KeybindDefaults.hasDefault(name)) {
            // Vanilla keybind - check if we should fake defaults
            if (settings.isFakeDefaultKeybinds()) {
                spoofedValue = KeybindDefaults.getDefault(name);
            } else {
                // Fake defaults disabled - allow real value but still log detection
                TranslationProtectionHandler.logDetection(name, originalValue, originalValue);
                return originalResult;
            }
        } else {
            // Mod keybind or unknown - return raw key name
            spoofedValue = name;
        }
        
        // Send detail alert if value changed
        if (!originalValue.equals(spoofedValue)) {
            TranslationProtectionHandler.sendDetail(name, originalValue, spoofedValue);
        }
        
        // Always log detection (even if value unchanged)
        TranslationProtectionHandler.logDetection(name, originalValue, spoofedValue);
        
        return Component.literal(spoofedValue);
    }
}

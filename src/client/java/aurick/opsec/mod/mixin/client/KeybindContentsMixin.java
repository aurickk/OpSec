package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.protection.ForgeTranslations;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.protection.TranslationProtectionHandler.InterceptionType;
import aurick.opsec.mod.tracking.ModRegistry;
import aurick.opsec.mod.util.KeybindDefaults;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Intercepts keybind resolution to protect user privacy.
 * 
 * Uses packet-origin tagging to only protect content from network packets:
 * - Normal mod UI / client-created content: Allow normal resolution
 * - Server-sent packets (multiplayer): Protect by returning cached defaults or raw key names
 * 
 * Whitelist priority:
 * 1. Vanilla keybinds - Return cached default value
 * 2. Whitelisted mod keybinds - Allow resolution (per user whitelist config)
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

    @Unique
    private boolean opsec$fromPacket = false;

    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("TAIL"))
    private void opsec$tagFromPacket(String name, CallbackInfo ci) {
        this.opsec$fromPacket = PacketContext.isProcessingPacket();
    }

    /**
     * Context-aware keybind interception. Never resolves what we're going to block —
     * only calls original.call() for passthrough cases. Blocked keybinds read the
     * real value through {@link #opsec$readKeybindDisplay()} for logging only.
     */
    @WrapOperation(
        method = "getNestedComponent",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
    )
    private Object opsec$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
        if (!this.opsec$fromPacket || Minecraft.getInstance().hasSingleplayerServer()) {
            return original.call(supplier);
        }

        TranslationProtectionHandler.notifyExploitDetected();

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        if (settings.isForgeMode() && ForgeTranslations.isForgeKey(name)) {
            String fabricatedValue = ForgeTranslations.getTranslation(name);
            if (fabricatedValue != null) {
                TranslationProtectionHandler.sendDetail(InterceptionType.KEYBIND, name, name, fabricatedValue);
                TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, name, fabricatedValue);
                return Component.literal(fabricatedValue);
            }
        }

        if (ModRegistry.isWhitelistedKeybind(name)) {
            return original.call(supplier);
        }

        // Protection disabled — passthrough with logging
        if (!config.isTranslationProtectionEnabled()) {
            Object originalResult = original.call(supplier);
            String originalValue = originalResult instanceof Component c ? c.getString() : originalResult.toString();
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, name, originalValue, originalValue);
            TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, originalValue, originalValue);
            return originalResult;
        }

        // Vanilla keybind
        if (KeybindDefaults.hasDefault(name)) {
            if (!settings.isFakeDefaultKeybinds()) {
                Object originalResult = original.call(supplier);
                String originalValue = originalResult instanceof Component c ? c.getString() : originalResult.toString();
                TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, name, originalValue, originalValue);
                TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, originalValue, originalValue);
                return originalResult;
            }
            String spoofedValue = KeybindDefaults.getDefault(name);
            opsec$logBlocked(name, spoofedValue);
            return Component.literal(spoofedValue);
        }

        // Mod/unknown keybind — return as translatable so server resource pack
        // values resolve through TranslatableContentsMixin (safe: server gets its
        // own value back). Matches vanilla behavior for unknown keybinds.
        String serverPackValue = ModRegistry.getServerPackTranslation(name);
        opsec$logBlocked(name, serverPackValue != null ? serverPackValue : name);
        return Component.translatable(name);
    }

    /**
     * Log a blocked keybind. Reads the real value via {@link #opsec$readKeybindDisplay()}
     * to avoid triggering the Supplier resolution chain.
     */
    @Unique
    private void opsec$logBlocked(String keybindName, String spoofedValue) {
        String realValue = opsec$readKeybindDisplay();

        if (!realValue.equals(spoofedValue)) {
            TranslationProtectionHandler.sendDetail(InterceptionType.KEYBIND, keybindName, realValue, spoofedValue);
        } else {
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, keybindName, realValue, spoofedValue);
        }
        TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, keybindName, realValue, spoofedValue);
    }

    /**
     * Read the keybind's current display value via {@link KeyMapping#createNameSupplier},
     * bypassing the Supplier chain in {@code getNestedComponent()}.
     * Keybind equivalent of TranslatableContentsMixin's {@code opsec$getRealTranslation()}.
     */
    @Unique
    private String opsec$readKeybindDisplay() {
        try {
            Component display = KeyMapping.createNameSupplier(name).get();
            if (display != null) {
                return display.getString();
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Failed to read keybind '{}': {}", name, e.getMessage());
        }
        return name;
    }
}

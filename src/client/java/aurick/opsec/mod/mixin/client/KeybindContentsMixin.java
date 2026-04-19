package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.protection.OpsecFromPacketAccess;
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
public class KeybindContentsMixin implements OpsecFromPacketAccess {

    @Shadow @Final
    private String name;

    @Unique
    private boolean opsec$fromPacket = false;

    @Unique
    private Object opsec$cachedBlocked = null;

    @Unique
    private boolean opsec$reported = false;

    @Override
    public void opsec$setFromPacket() {
        this.opsec$fromPacket = true;
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
        if (opsec$cachedBlocked != null) {
            return opsec$cachedBlocked;
        }

        TranslationProtectionHandler.notifyExploitDetected();

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        if (ModRegistry.isWhitelistedKeybind(name)) {
            if (OpsecConfig.getInstance().isDebugAlerts()) {
                String displayValue = opsec$readKeybindDisplay();
                TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, displayValue, displayValue);
            }
            return original.call(supplier);
        }

        // Protection disabled — passthrough with logging
        if (!config.isTranslationProtectionEnabled()) {
            Object originalResult = original.call(supplier);
            String originalValue = originalResult instanceof Component c ? c.getString()
                : originalResult != null ? originalResult.toString() : name;
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, name, originalValue, originalValue);
            TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, originalValue, originalValue);
            return originalResult;
        }

        // Vanilla keybind
        if (KeybindDefaults.hasDefault(name)) {
            if (!settings.isFakeDefaultKeybinds()) {
                Object originalResult = original.call(supplier);
                String originalValue = originalResult instanceof Component c ? c.getString()
                    : originalResult != null ? originalResult.toString() : name;
                TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, name, originalValue, originalValue);
                TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, originalValue, originalValue);
                return originalResult;
            }
            String spoofedValue = KeybindDefaults.getDefault(name);
            opsec$logBlocked(name, spoofedValue);
            Component literal = Component.literal(spoofedValue);
            opsec$cachedBlocked = literal;
            return literal;
        }

        // Return a marked Component.translatable so the translation pipeline
        // runs: server-pack keys re-resolve to pack value (matches vanilla
        // echo), mod keys block at the Language.getOrDefault call site.
        // Mark silent so the downstream translation path doesn't double-log
        // what we already reported here.
        String realValue = opsec$readKeybindDisplay();
        String spoofed = ModRegistry.isServerPackTranslationKey(name) ? realValue : name;
        opsec$reportBlocked(name, realValue, spoofed);
        Component replacement = Component.translatable(name);
        if (replacement.getContents() instanceof OpsecFromPacketAccess access) {
            access.opsec$setFromPacket();
            access.opsec$setSilent();
        }
        opsec$cachedBlocked = replacement;
        return replacement;
    }

    @Unique
    private void opsec$logBlocked(String keybindName, String spoofedValue) {
        opsec$reportBlocked(keybindName, opsec$readKeybindDisplay(), spoofedValue);
    }

    @Unique
    private void opsec$reportBlocked(String keybindName, String realValue, String spoofedValue) {
        if (opsec$reported) return;
        opsec$reported = true;
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

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
import net.minecraft.client.Minecraft;
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
 * Intercepts TranslatableContents to block mod translation resolution at the call site.
 *
 * Uses @WrapOperation on Language.getOrDefault() calls inside decompose() rather than
 * intercepting at the callee (ClientLanguage). This ensures protection fires regardless
 * of which Language implementation is active.
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

    @Unique
    private boolean opsec$fromPacket = false;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V", at = @At("TAIL"))
    private void opsec$tagFromPacket(String key, String fallback, Object[] args, CallbackInfo ci) {
        this.opsec$fromPacket = PacketContext.isProcessingPacket();
    }

    /** Sentinel value indicating the original call should proceed. */
    @Unique
    private static final String OPSEC_ALLOW_ORIGINAL = "\0__opsec_allow__";

    /**
     * Wrap the single-arg Language.getOrDefault(String) call inside decompose().
     * This is called when fallback is null (vanilla behavior falls back to the key itself).
     */
    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String opsec$wrapGetOrDefault(Language instance, String id, Operation<String> original) {
        String result = opsec$handleTranslationLookup(id, id);
        if (result == OPSEC_ALLOW_ORIGINAL) {
            return original.call(instance, id);
        }
        return result;
    }

    /**
     * Wrap the two-arg Language.getOrDefault(String, String) call inside decompose().
     * This is called when fallback is non-null.
     */
    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    )
    private String opsec$wrapGetOrDefaultWithFallback(Language instance, String keyArg, String defaultValue, Operation<String> original) {
        String result = opsec$handleTranslationLookup(keyArg, defaultValue);
        if (result == OPSEC_ALLOW_ORIGINAL) {
            return original.call(instance, keyArg, defaultValue);
        }
        return result;
    }

    /**
     * Shared handler for translation lookup interception.
     * Returns either a replacement value (blocked/fabricated) or the sentinel
     * {@link #OPSEC_ALLOW_ORIGINAL} to indicate the original call should proceed.
     *
     * @param translationKey the translation key being looked up
     * @param defaultValue   the fallback value if blocked (key itself for single-arg, fallback for two-arg)
     * @return replacement value, or {@link #OPSEC_ALLOW_ORIGINAL} to call through
     */
    @Unique
    private String opsec$handleTranslationLookup(String translationKey, String defaultValue) {
        // Not from a packet or in singleplayer — allow normal resolution
        if (!this.opsec$fromPacket || Minecraft.getInstance().hasSingleplayerServer()) {
            return OPSEC_ALLOW_ORIGINAL;
        }

        // In exploit context — always notify header (cooldown prevents spam)
        TranslationProtectionHandler.notifyExploitDetected();

        // Always allow vanilla keys — log to console if debug enabled
        if (ModRegistry.isVanillaTranslationKey(translationKey)) {
            if (OpsecConfig.getInstance().isDebugAlerts()) {
                String realValue = opsec$getRealTranslation(translationKey, defaultValue);
                TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
            }
            return OPSEC_ALLOW_ORIGINAL;
        }

        // Allow server resource pack keys through vanilla resolution.
        // A vanilla client resolves these through Language.getOrDefault() at call time.
        if (ModRegistry.isServerPackTranslationKey(translationKey)) {
            if (OpsecConfig.getInstance().isDebugAlerts()) {
                String realValue = opsec$getRealTranslation(translationKey, defaultValue);
                TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
            }
            return OPSEC_ALLOW_ORIGINAL;
        }

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        // If protection is disabled, still log but allow resolution
        if (!config.isTranslationProtectionEnabled()) {
            String realValue = opsec$getRealTranslation(translationKey, defaultValue);
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
            TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
            return OPSEC_ALLOW_ORIGINAL;
        }

        // VANILLA MODE: Block all mod keys
        if (settings.isVanillaMode()) {
            String blockedValue = opsec$getBlockedValue(translationKey, defaultValue);
            opsec$logBlocked(translationKey, blockedValue);
            return blockedValue;
        }

        // FABRIC MODE: Allow whitelisted mod keys, block others
        if (settings.isFabricMode()) {
            if (ModRegistry.isWhitelistedTranslationKey(translationKey)) {
                return OPSEC_ALLOW_ORIGINAL;
            }
            String blockedValue = opsec$getBlockedValue(translationKey, defaultValue);
            opsec$logBlocked(translationKey, blockedValue);
            return blockedValue;
        }

        // FORGE MODE: Fabricate known Forge keys, block others
        if (settings.isForgeMode()) {
            String forgeValue = ForgeTranslations.getTranslation(translationKey);
            if (forgeValue != null) {
                opsec$logForgeFabrication(translationKey, defaultValue, forgeValue);
                return forgeValue;
            }
            String blockedValue = opsec$getBlockedValue(translationKey, defaultValue);
            opsec$logBlocked(translationKey, blockedValue);
            return blockedValue;
        }

        // Fallback: Use whitelist behavior
        if (ModRegistry.isWhitelistedTranslationKey(translationKey)) {
            return OPSEC_ALLOW_ORIGINAL;
        }
        String blockedValue = opsec$getBlockedValue(translationKey, defaultValue);
        opsec$logBlocked(translationKey, blockedValue);
        return blockedValue;
    }

    /**
     * Log detection when a mod translation key is blocked.
     * Gets the real translation value by directly accessing storage for accurate logging.
     */
    @Unique
    private void opsec$logBlocked(String translationKey, String defaultValue) {
        String originalValue = opsec$getRealTranslation(translationKey, defaultValue);

        if (!originalValue.equals(defaultValue)) {
            TranslationProtectionHandler.sendDetail(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
        } else {
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
        }
        TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
    }

    /**
     * Log detection when a Forge key is being fabricated.
     */
    @Unique
    private void opsec$logForgeFabrication(String translationKey, String defaultValue, String fabricatedValue) {
        TranslationProtectionHandler.sendDetail(InterceptionType.TRANSLATION, translationKey, defaultValue, fabricatedValue);
        TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, defaultValue, fabricatedValue);
    }

    /**
     * Get the value to return when blocking a key.
     * Server resource pack keys are whitelisted for vanilla resolution earlier in the
     * pipeline, so any key reaching this point is a mod key that should be blocked.
     */
    @Unique
    private String opsec$getBlockedValue(String translationKey, String defaultValue) {
        return defaultValue;
    }

    /**
     * Get the real translation value by directly accessing ClientLanguage's storage map.
     * Uses {@link ClientLanguageAccessor} to bypass our interception.
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

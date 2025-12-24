package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to handle telemetry.
 * 
 * Signing modes:
 * - SIGN: Sign messages normally (chat reportable)
 * - ON_DEMAND: Sign messages only if server requires it
 * 
 * For ON_DEMAND mode, the key is always sent so we CAN sign when required.
 * Signature stripping is handled in ServerboundChatPacketMixin.
 * 
 * Telemetry: By returning false from allowsTelemetry(), we prevent telemetry data
 * from being sent to Mojang/Microsoft.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    // Key is always sent - ON_DEMAND strips signatures in ServerboundChatPacketMixin
    // when server doesn't require signing
    
    /**
     * Disable telemetry by returning false from allowsTelemetry().
     * This prevents telemetry data from being collected and sent.
     */
    @Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
    private void incognito$disableTelemetry(CallbackInfoReturnable<Boolean> info) {
        if (IncognitoConfig.getInstance().shouldDisableTelemetry()) {
            Incognito.LOGGER.debug("[Incognito] Blocking telemetry (allowsTelemetry returning false)");
            info.setReturnValue(false);
        }
    }
    // Session state reset is handled in ClientPacketListenerMixin.close()
}

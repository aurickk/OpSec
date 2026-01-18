package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.protection.ClientSpoofer;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to log when configuration finishes.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ChannelRegistrationMixin {
    
    @Inject(method = "handleConfigurationFinished", at = @At("HEAD"))
    private void onConfigurationFinished(ClientboundFinishConfigurationPacket packet, CallbackInfo ci) {
        OpsecConfig config = OpsecConfig.getInstance();
        
        if (!config.shouldSpoofBrand()) {
            return;
        }
        
        String brand = config.getEffectiveBrand();
        
        Opsec.LOGGER.debug("[OpSec] Configuration finished - spoofing as '{}'", brand);

        // Brand-only spoofing mode (no channel modifications)
        if (!config.shouldSpoofChannels()) {
            Opsec.LOGGER.debug("[OpSec] BRAND-ONLY MODE - channels are NOT spoofed/blocked");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS,
                "Privacy active: brand-only mode (channels unmodified)");
            return;
        }
        
        if (ClientSpoofer.isVanillaMode()) {
            Opsec.LOGGER.debug("[OpSec] VANILLA MODE - all custom payloads blocked");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS, 
                "Privacy active: vanilla mode (all channels blocked)");
        } else if (ClientSpoofer.isFabricMode()) {
            Opsec.LOGGER.debug("[OpSec] FABRIC MODE - mod channels blocked, fabric allowed");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS, 
                "Privacy active: fabric mode (mod channels blocked)");
        } else if (ClientSpoofer.isForgeMode()) {
            Opsec.LOGGER.debug("[OpSec] FORGE MODE - emulating clean Forge client");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS, 
                "Privacy active: forge mode (forge:login, forge:handshake)");
        }
    }
}

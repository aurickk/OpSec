package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.protection.ClientSpoofer;
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
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        if (!config.shouldSpoofBrand()) {
            return;
        }
        
        String brand = config.getEffectiveBrand();
        
        Incognito.LOGGER.info("[Incognito] Configuration finished - spoofing as '{}'", brand);

        // Brand-only spoofing mode (no channel modifications)
        if (!config.shouldSpoofChannels()) {
            Incognito.LOGGER.info("[Incognito] BRAND-ONLY MODE - channels are NOT spoofed/blocked");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS,
                "Privacy active: brand-only mode (channels unmodified)");
            return;
        }
        
        if (ClientSpoofer.isVanillaMode()) {
            Incognito.LOGGER.info("[Incognito] VANILLA MODE - all custom payloads blocked");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS, 
                "Privacy active: vanilla mode (all channels blocked)");
        } else if (ClientSpoofer.isFabricMode()) {
            Incognito.LOGGER.info("[Incognito] FABRIC MODE - mod channels blocked, fabric allowed");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS, 
                "Privacy active: fabric mode (mod channels blocked)");
        } else if (ClientSpoofer.isForgeMode()) {
            Incognito.LOGGER.info("[Incognito] FORGE MODE - emulating clean Forge client");
            PrivacyLogger.alert(PrivacyLogger.AlertType.SUCCESS, 
                "Privacy active: forge mode (forge:login, forge:handshake)");
        }
    }
}

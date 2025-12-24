package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to spoof the client brand at its source.
 * ClientBrandRetriever is modified by Fabric to return "fabric".
 * We intercept it to return our spoofed brand.
 * 
 * Note: remap = false because this method is added by Fabric, not vanilla.
 */
@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverMixin {
    
    @org.spongepowered.asm.mixin.Unique
    private static boolean incognito$logged = false;
    
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetClientModName(CallbackInfoReturnable<String> cir) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        if (config.shouldSpoofBrand()) {
            String spoofedBrand = config.getSettings().getEffectiveBrand();
            
            // Only log once to avoid spam
            if (!incognito$logged) {
                incognito$logged = true;
                Incognito.LOGGER.info("[Incognito] ClientBrandRetriever active - spoofing brand as: {}", spoofedBrand);
            }
            
            cir.setReturnValue(spoofedBrand);
        }
    }
}


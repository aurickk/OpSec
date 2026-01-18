package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spoofs the client brand at its source.
 */
@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverMixin {
    
    @Unique
    private static final AtomicBoolean opsec$logged = new AtomicBoolean(false);
    
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetClientModName(CallbackInfoReturnable<String> cir) {
        OpsecConfig config = OpsecConfig.getInstance();
        
        if (config.shouldSpoofBrand()) {
            String spoofedBrand = config.getSettings().getEffectiveBrand();
            
            if (opsec$logged.compareAndSet(false, true)) {
                Opsec.LOGGER.debug("[OpSec] ClientBrandRetriever active - spoofing brand as: {}", spoofedBrand);
            }
            
            cir.setReturnValue(spoofedBrand);
        }
    }
}


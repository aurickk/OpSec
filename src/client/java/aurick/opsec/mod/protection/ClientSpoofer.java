package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConstants;
import aurick.opsec.mod.tracking.ModRegistry;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.concurrent.atomic.AtomicBoolean;

import static aurick.opsec.mod.config.OpsecConstants.Brands.*;

/**
 * Handles client brand spoofing and channel filtering logic.
 * Provides methods to check spoofing modes (vanilla, fabric, forge)
 * and determines which network channels should be blocked.
 */
public class ClientSpoofer {
    
    private static final AtomicBoolean loggedBrandSpoof = new AtomicBoolean(false);
    
    public static String getSpoofedBrand() {
        OpsecConfig config = OpsecConfig.getInstance();
        
        if (!config.shouldSpoofBrand()) {
            return FABRIC;
        }
        
        String brand = config.getSettings().getEffectiveBrand();
        
        if (loggedBrandSpoof.compareAndSet(false, true)) {
            Opsec.LOGGER.debug("[OpSec] Spoofing brand as: {}", brand);
            PrivacyLogger.alertClientBrandSpoofed(FABRIC, brand);
        }
        
        return brand;
    }
    
    /**
     * Check if running in vanilla mode for channel filtering purposes.
     * Requires both brand spoofing and channel spoofing to be enabled.
     * Delegates to SpoofSettings for brand mode check.
     */
    public static boolean isVanillaMode() {
        OpsecConfig config = OpsecConfig.getInstance();
        return config.shouldSpoofChannels() && config.getSettings().isVanillaMode();
    }

    /**
     * Check if running in fabric mode for channel filtering purposes.
     * Requires both brand spoofing and channel spoofing to be enabled.
     * Delegates to SpoofSettings for brand mode check.
     */
    public static boolean isFabricMode() {
        OpsecConfig config = OpsecConfig.getInstance();
        return config.shouldSpoofChannels() && config.getSettings().isFabricMode();
    }

    /**
     * Check if running in forge mode for channel filtering purposes.
     * Requires both brand spoofing and channel spoofing to be enabled.
     * Delegates to SpoofSettings for brand mode check.
     */
    public static boolean isForgeMode() {
        OpsecConfig config = OpsecConfig.getInstance();
        return config.shouldSpoofChannels() && config.getSettings().isForgeMode();
    }
    
    //? if >=1.21.11 {
    /*public static boolean shouldBlockPayload(Identifier payloadId) {*/
    //?} else {
    public static boolean shouldBlockPayload(ResourceLocation payloadId) {
    //?}
        OpsecConfig config = OpsecConfig.getInstance();
        
        if (!config.shouldSpoofBrand() || !config.shouldSpoofChannels()) {
            return false;
        }
        
        String channel = payloadId.toString();
        String namespace = payloadId.getNamespace();
        String path = payloadId.getPath();
        String brand = config.getEffectiveBrand();
        
        if (VANILLA.equals(brand)) {
            if (Opsec.LOGGER.isDebugEnabled()) {
            Opsec.LOGGER.debug("[OpSec] VANILLA MODE - Blocking payload: {}", channel);
            }
            return true;
        }
        
        if (FABRIC.equals(brand)) {
            if (OpsecConstants.Channels.MINECRAFT.equals(namespace) 
                    || OpsecConstants.Channels.COMMON.equals(namespace)) {
                return false;
            }
            
            if (OpsecConstants.Channels.FABRIC_NAMESPACE.equals(namespace) 
                    || namespace.startsWith(OpsecConstants.Channels.FABRIC_NAMESPACE + "-")) {
                if (Opsec.LOGGER.isDebugEnabled()) {
                Opsec.LOGGER.debug("[OpSec] FABRIC MODE - Allowing fabric channel: {}", channel);
                }
                return false;
            }
            
            // Allow whitelisted mod channels
            if (ModRegistry.isWhitelistedChannel(payloadId)) {
                if (Opsec.LOGGER.isDebugEnabled()) {
                    Opsec.LOGGER.debug("[OpSec] FABRIC MODE - Allowing whitelisted channel: {}", channel);
                }
                return false;
            }
            
            if (Opsec.LOGGER.isDebugEnabled()) {
            Opsec.LOGGER.debug("[OpSec] FABRIC MODE - Blocking mod channel: {}", channel);
            }
            return true;
        }
        
        if (FORGE.equals(brand)) {
            if (OpsecConstants.Channels.MINECRAFT.equals(namespace)) {
                return false;
            }
            
            if (OpsecConstants.Channels.FORGE_NAMESPACE.equals(namespace) 
                    && (OpsecConstants.Channels.LOGIN.equals(path) 
                        || OpsecConstants.Channels.HANDSHAKE.equals(path))) {
                if (Opsec.LOGGER.isDebugEnabled()) {
                Opsec.LOGGER.debug("[OpSec] FORGE MODE - Allowing forge channel: {}", channel);
                }
                return false;
            }
            
            if (Opsec.LOGGER.isDebugEnabled()) {
            Opsec.LOGGER.debug("[OpSec] FORGE MODE - Blocking channel: {}", channel);
            }
            return true;
        }
        
        return false;
    }
    
    public static void reset() {
        loggedBrandSpoof.set(false);
    }
}

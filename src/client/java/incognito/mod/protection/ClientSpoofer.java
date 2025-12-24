package incognito.mod.protection;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * Handles client brand and channel spoofing for AntiSpoof bypass.
 * 
 * @see <a href="https://github.com/GigaZelensky/AntiSpoof">AntiSpoof</a>
 */
public class ClientSpoofer {
    
    private static boolean loggedBrandSpoof = false;
    
    public static String getSpoofedBrand() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        if (!config.shouldSpoofBrand()) {
            return "fabric";
        }
        
        String brand = config.getSettings().getEffectiveBrand();
        
        if (!loggedBrandSpoof) {
            loggedBrandSpoof = true;
            Incognito.LOGGER.info("[Incognito] Spoofing brand as: {}", brand);
            PrivacyLogger.alertClientBrandSpoofed("fabric", brand);
        }
        
        return brand;
    }
    
    public static boolean isVanillaMode() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        return config.shouldSpoofBrand() && config.shouldSpoofChannels() && config.getSettings().isVanillaMode();
    }
    
    public static boolean isFabricMode() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        return config.shouldSpoofBrand() && config.shouldSpoofChannels() && config.getSettings().isFabricMode();
    }
    
    public static boolean isForgeMode() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        return config.shouldSpoofBrand() && config.shouldSpoofChannels() && config.getSettings().isForgeMode();
    }
    
    public static boolean shouldBlockPayload(ResourceLocation payloadId) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        if (!config.shouldSpoofBrand()) {
            return false;
        }

        if (!config.shouldSpoofChannels()) {
            return false;
        }
        
        String channel = payloadId.toString();
        String namespace = payloadId.getNamespace();
        String path = payloadId.getPath();
        
        if (config.getSettings().isVanillaMode()) {
            Incognito.LOGGER.debug("[Incognito] VANILLA MODE - Blocking payload: {}", channel);
            return true;
        }
        
        if (config.getSettings().isFabricMode()) {
            if (namespace.equals("minecraft") || namespace.equals("c")) {
                return false;
            }
            
            if (namespace.equals("fabric") || namespace.startsWith("fabric-")) {
                Incognito.LOGGER.debug("[Incognito] FABRIC MODE - Allowing fabric channel: {}", channel);
                return false;
            }
            
            Incognito.LOGGER.debug("[Incognito] FABRIC MODE - Blocking mod channel: {}", channel);
            return true;
        }
        
        if (config.getSettings().isForgeMode()) {
            if (namespace.equals("minecraft")) {
                return false;
            }
            
            if (namespace.equals("forge") && ("login".equals(path) || "handshake".equals(path))) {
                Incognito.LOGGER.debug("[Incognito] FORGE MODE - Allowing forge channel: {}", channel);
                return false;
            }
            
            Incognito.LOGGER.debug("[Incognito] FORGE MODE - Blocking channel: {}", channel);
            return true;
        }
        
        return false;
    }
    
    public static void reset() {
        loggedBrandSpoof = false;
    }
}

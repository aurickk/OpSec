package aurick.opsec.mod.mixin;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cancels Meteor Client's broken AbstractSignEditScreenMixin when the Meteor Fix option is enabled.
 * 
 * Meteor's mixin converts TranslatableContents to PlainTextContent.Literal, which:
 * 1. Destroys the fallback value
 * 2. Returns the raw key instead of the expected fallback
 * 3. Exposes the client to anti-spoof detection by servers
 * 
 * By disabling Meteor's mixin, OpSec's proper implementation handles everything correctly,
 * including returning fallback values when present.
 * 
 * Note: Changes to this setting require a game restart to take effect.
 */
public class MeteorMixinCanceller implements MixinCanceller {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("OpSec");
    private static final String METEOR_MIXIN = "meteordevelopment.meteorclient.mixin.AbstractSignEditScreenMixin";
    
    private static final boolean meteorFixEnabled;
    private static final boolean meteorPresent;
    
    // Track the value that was applied at startup for UI comparison
    private static final boolean appliedMeteorFix;
    
    static {
        // Check if Meteor Client is installed
        meteorPresent = FabricLoader.getInstance().isModLoaded("meteor-client");
        
        // Read config to check if Meteor Fix is enabled
        meteorFixEnabled = readMeteorFixSetting();
        
        // Store what was actually applied (only effective if Meteor is present)
        appliedMeteorFix = meteorPresent && meteorFixEnabled;
        
        if (meteorPresent && meteorFixEnabled) {
            LOGGER.info("[OpSec] Meteor Client detected - Meteor Fix enabled, will disable Meteor's broken key resolution protection");
        } else if (meteorPresent) {
            LOGGER.warn("[OpSec] Meteor Client detected - Meteor Fix is DISABLED. Meteor's translation protection may expose your mods to servers!");
        }
    }
    
    /**
     * Returns whether Meteor Fix was applied at game startup.
     * Used by the config screen to determine if the setting has changed.
     */
    public static boolean wasAppliedAtStartup() {
        return appliedMeteorFix;
    }
    
    /**
     * Returns whether the current config setting differs from what was applied at startup.
     * If true, a game restart is needed for the change to take effect.
     */
    public static boolean needsRestart(boolean currentSetting) {
        if (!meteorPresent) {
            return false; // No restart needed if Meteor isn't installed
        }
        return currentSetting != appliedMeteorFix;
    }
    
    /**
     * Read the meteorFix setting directly from the config file.
     * This runs very early before the full mod initialization, so we can't use OpsecConfig.
     * 
     * Meteor Fix is only effective when:
     * 1. translationProtection is enabled (default: true)
     * 2. meteorFix is enabled (default: true)
     */
    private static boolean readMeteorFixSetting() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("opsec.json");
        
        if (!Files.exists(configPath)) {
            // Default to enabled
            return true;
        }
        
        try {
            String content = Files.readString(configPath);
            if (content == null || content.trim().isEmpty()) {
                return true;
            }
            
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            // Check settings
            if (json.has("settings")) {
                JsonObject settings = json.getAsJsonObject("settings");
                
                // If translation protection is disabled, Meteor Fix is also disabled
                if (settings.has("translationProtection") && !settings.get("translationProtection").getAsBoolean()) {
                    LOGGER.info("[OpSec] Key resolution protection is disabled, Meteor Fix will not apply");
                    return false;
                }
                
                // Check meteorFix setting
                if (settings.has("meteorFix")) {
                    return settings.get("meteorFix").getAsBoolean();
                }
            }
            
            // Default to enabled
            return true;
            
        } catch (IOException | com.google.gson.JsonSyntaxException | IllegalStateException e) {
            LOGGER.warn("[OpSec] Could not read config for Meteor Fix setting, defaulting to enabled: {}", e.getMessage());
            return true;
        }
    }
    
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        // Only cancel Meteor's AbstractSignEditScreenMixin when:
        // 1. Meteor Client is installed
        // 2. Meteor Fix is enabled in config
        // 3. This is the specific mixin we want to cancel
        if (meteorPresent && meteorFixEnabled && METEOR_MIXIN.equals(mixinClassName)) {
            LOGGER.debug("[OpSec] Cancelling Meteor mixin: {}", mixinClassName);
            return true;
        }
        return false;
    }
}


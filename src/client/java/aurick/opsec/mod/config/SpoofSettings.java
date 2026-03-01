package aurick.opsec.mod.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

import static aurick.opsec.mod.config.OpsecConstants.Brands.*;

/**
 * Settings for client spoofing and privacy protection.
 */
public class SpoofSettings {
    
    /**
     * Signing modes for chat messages.
     * Controls whether messages are cryptographically signed.
     */
    public enum SigningMode {
        /** Always sign messages (chat reportable to Mojang) */
        SIGN,
        /** Never sign messages (may break on strict servers) */
        OFF,
        /** Only sign when server requires it (recommended) */
        ON_DEMAND
    }
    
    // Brand spoofing
    private boolean spoofBrand = true;
    private String customBrand = VANILLA;
    private boolean spoofChannels = false;
    
    // Resource pack protection
    private boolean isolatePackCache = true;
    private boolean blockLocalPackUrls = true;
    
    // Translation exploit protection
    private boolean translationProtection = true;
    private boolean fakeDefaultKeybinds = true;  // Spoof vanilla keybinds to default values
    private boolean meteorFix = true;  // Block Meteor's broken translation protection
    
    // Alerts
    private boolean showAlerts = true;
    private boolean showToasts = true;
    private boolean logDetections = true;
    
    // Chat Signing
    private SigningMode signingMode = SigningMode.ON_DEMAND;
    private transient boolean tempSign = false;
    private transient boolean signingToastShown = false;
    
    // Privacy
    private boolean disableTelemetry = true;
    
    // UI Settings
    private int buttonX = -1;
    private int buttonY = -1;
    
    // Whitelist settings
    private boolean whitelistEnabled = false;
    private Set<String> whitelistedMods = new HashSet<>();
    
    public SpoofSettings() {}
    
    public boolean isSpoofBrand() { return spoofBrand; }
    public void setSpoofBrand(boolean spoofBrand) { this.spoofBrand = spoofBrand; }
    
    public String getCustomBrand() { return customBrand; }
    public void setCustomBrand(String customBrand) {
        if (VANILLA.equalsIgnoreCase(customBrand)) {
            this.customBrand = VANILLA;
        } else if (FORGE.equalsIgnoreCase(customBrand)) {
            this.customBrand = FORGE;
        } else {
            this.customBrand = FABRIC;
        }
    }

    public boolean isSpoofChannels() { return spoofChannels; }
    public void setSpoofChannels(boolean spoofChannels) { this.spoofChannels = spoofChannels; }
    
    public boolean isIsolatePackCache() { return isolatePackCache; }
    public void setIsolatePackCache(boolean isolatePackCache) { this.isolatePackCache = isolatePackCache; }
    
    public boolean isBlockLocalPackUrls() { return blockLocalPackUrls; }
    public void setBlockLocalPackUrls(boolean blockLocalPackUrls) { this.blockLocalPackUrls = blockLocalPackUrls; }
    
    public boolean isTranslationProtectionEnabled() { return translationProtection; }
    public void setTranslationProtection(boolean enabled) { this.translationProtection = enabled; }
    
    public boolean isFakeDefaultKeybinds() { return fakeDefaultKeybinds; }
    public void setFakeDefaultKeybinds(boolean enabled) { this.fakeDefaultKeybinds = enabled; }
    
    public boolean isMeteorFix() { return meteorFix; }
    public void setMeteorFix(boolean enabled) { this.meteorFix = enabled; }
    
    public boolean isShowAlerts() { return showAlerts; }
    public void setShowAlerts(boolean showAlerts) { this.showAlerts = showAlerts; }
    
    public boolean isShowToasts() { return showToasts; }
    public void setShowToasts(boolean showToasts) { this.showToasts = showToasts; }
    
    public boolean isLogDetections() { return logDetections; }
    public void setLogDetections(boolean logDetections) { this.logDetections = logDetections; }
    
    // Signing mode methods
    public SigningMode getSigningMode() { return signingMode; }
    public void setSigningMode(SigningMode mode) { this.signingMode = mode; }
    
    public boolean shouldNotSign() {
        return signingMode == SigningMode.OFF || (signingMode == SigningMode.ON_DEMAND && !tempSign);
    }
    
    public boolean isOnDemand() {
        return signingMode == SigningMode.ON_DEMAND;
    }
    
    public boolean isTempSign() { return tempSign; }
    public void setTempSign(boolean tempSign) { this.tempSign = tempSign; }
    
    public boolean isSigningToastShown() { return signingToastShown; }
    public void setSigningToastShown(boolean shown) { this.signingToastShown = shown; }
    
    public void resetSessionState() {
        this.tempSign = false;
        this.signingToastShown = false;
    }
    
    public boolean isDisableTelemetry() { return disableTelemetry; }
    public void setDisableTelemetry(boolean disableTelemetry) { this.disableTelemetry = disableTelemetry; }
    
    public int[] getButtonPosition() {
        if (buttonX < 0 || buttonY < 0) return null;
        return new int[] { buttonX, buttonY };
    }
    
    public void setButtonPosition(int x, int y) {
        this.buttonX = x;
        this.buttonY = y;
    }
    
    // Whitelist methods
    public boolean isWhitelistEnabled() { return whitelistEnabled; }
    public void setWhitelistEnabled(boolean enabled) { this.whitelistEnabled = enabled; }
    
    public Set<String> getWhitelistedMods() { return whitelistedMods; }
    public void setWhitelistedMods(Set<String> mods) { this.whitelistedMods = mods != null ? mods : new HashSet<>(); }
    
    public boolean isModWhitelisted(String modId) { 
        return whitelistEnabled && modId != null && whitelistedMods.contains(modId); 
    }
    
    public String getEffectiveBrand() {
        if (!spoofBrand) return FABRIC;
        // When whitelist is enabled, force Fabric brand
        if (whitelistEnabled) return FABRIC;
        if (VANILLA.equalsIgnoreCase(customBrand)) return VANILLA;
        if (FORGE.equalsIgnoreCase(customBrand)) return FORGE;
        return FABRIC;
    }
    
    /**
     * Check if currently in vanilla mode.
     * Uses getEffectiveBrand() which respects whitelist settings.
     */
    public boolean isVanillaMode() {
        return spoofBrand && VANILLA.equals(getEffectiveBrand());
    }

    /**
     * Check if currently in fabric mode.
     * Uses getEffectiveBrand() which respects whitelist settings.
     * Note: When whitelist is enabled, this will always return true.
     */
    public boolean isFabricMode() {
        return spoofBrand && FABRIC.equals(getEffectiveBrand());
    }

    /**
     * Check if currently in forge mode.
     * Uses getEffectiveBrand() which respects whitelist settings.
     * Note: When whitelist is enabled, this will always return false.
     */
    public boolean isForgeMode() {
        return spoofBrand && FORGE.equals(getEffectiveBrand());
    }
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("spoofBrand", spoofBrand);
        json.addProperty("customBrand", customBrand);
        json.addProperty("spoofChannels", spoofChannels);
        json.addProperty("isolatePackCache", isolatePackCache);
        json.addProperty("blockLocalPackUrls", blockLocalPackUrls);
        json.addProperty("translationProtection", translationProtection);
        json.addProperty("fakeDefaultKeybinds", fakeDefaultKeybinds);
        json.addProperty("meteorFix", meteorFix);
        json.addProperty("showAlerts", showAlerts);
        json.addProperty("showToasts", showToasts);
        json.addProperty("logDetections", logDetections);
        json.addProperty("signingMode", signingMode.name());
        json.addProperty("disableTelemetry", disableTelemetry);
        json.addProperty("buttonX", buttonX);
        json.addProperty("buttonY", buttonY);
        
        // Whitelist settings
        json.addProperty("whitelistEnabled", whitelistEnabled);
        JsonArray modsArray = new JsonArray();
        for (String modId : whitelistedMods) {
            modsArray.add(modId);
        }
        json.add("whitelistedMods", modsArray);
        
        return json;
    }
    
    public static SpoofSettings fromJson(JsonObject json) {
        SpoofSettings s = new SpoofSettings();
        if (json.has("spoofBrand")) s.spoofBrand = json.get("spoofBrand").getAsBoolean();
        if (json.has("customBrand")) s.setCustomBrand(json.get("customBrand").getAsString());
        if (json.has("spoofChannels")) s.spoofChannels = json.get("spoofChannels").getAsBoolean();
        if (json.has("isolatePackCache")) s.isolatePackCache = json.get("isolatePackCache").getAsBoolean();
        if (json.has("blockLocalPackUrls")) s.blockLocalPackUrls = json.get("blockLocalPackUrls").getAsBoolean();
        // Legacy support
        if (json.has("spoofLocalPackUrls")) s.blockLocalPackUrls = json.get("spoofLocalPackUrls").getAsBoolean();
        if (json.has("translationProtection")) s.translationProtection = json.get("translationProtection").getAsBoolean();
        // Legacy support
        if (json.has("blockTranslationExploit")) s.translationProtection = json.get("blockTranslationExploit").getAsBoolean();
        if (json.has("fakeDefaultKeybinds")) s.fakeDefaultKeybinds = json.get("fakeDefaultKeybinds").getAsBoolean();
        if (json.has("meteorFix")) s.meteorFix = json.get("meteorFix").getAsBoolean();
        if (json.has("showAlerts")) s.showAlerts = json.get("showAlerts").getAsBoolean();
        if (json.has("showToasts")) s.showToasts = json.get("showToasts").getAsBoolean();
        if (json.has("logDetections")) s.logDetections = json.get("logDetections").getAsBoolean();
        if (json.has("signingMode")) {
            String mode = json.get("signingMode").getAsString();
            if ("NO_KEY".equals(mode) || "NO_SIGN".equals(mode)) {
                s.signingMode = SigningMode.OFF;
            } else if ("SIGN".equals(mode)) {
                s.signingMode = SigningMode.SIGN;
            } else if ("ON_DEMAND".equals(mode)) {
                s.signingMode = SigningMode.ON_DEMAND;
            } else if ("OFF".equals(mode)) {
                s.signingMode = SigningMode.OFF;
            } else {
                s.signingMode = SigningMode.ON_DEMAND;
            }
        }
        if (json.has("disableTelemetry")) s.disableTelemetry = json.get("disableTelemetry").getAsBoolean();
        if (json.has("buttonX")) s.buttonX = json.get("buttonX").getAsInt();
        if (json.has("buttonY")) s.buttonY = json.get("buttonY").getAsInt();
        
        // Whitelist settings
        if (json.has("whitelistEnabled")) s.whitelistEnabled = json.get("whitelistEnabled").getAsBoolean();
        if (json.has("whitelistedMods")) {
            JsonArray modsArray = json.getAsJsonArray("whitelistedMods");
            s.whitelistedMods = new HashSet<>();
            for (int i = 0; i < modsArray.size(); i++) {
                s.whitelistedMods.add(modsArray.get(i).getAsString());
            }
        }
        
        return s;
    }
    
    public void copyFrom(SpoofSettings other) {
        this.spoofBrand = other.spoofBrand;
        this.customBrand = other.customBrand;
        this.spoofChannels = other.spoofChannels;
        this.isolatePackCache = other.isolatePackCache;
        this.blockLocalPackUrls = other.blockLocalPackUrls;
        this.translationProtection = other.translationProtection;
        this.fakeDefaultKeybinds = other.fakeDefaultKeybinds;
        this.meteorFix = other.meteorFix;
        this.showAlerts = other.showAlerts;
        this.showToasts = other.showToasts;
        this.logDetections = other.logDetections;
        this.signingMode = other.signingMode;
        this.disableTelemetry = other.disableTelemetry;
        this.buttonX = other.buttonX;
        this.buttonY = other.buttonY;
        this.whitelistEnabled = other.whitelistEnabled;
        this.whitelistedMods = new HashSet<>(other.whitelistedMods);
    }
}

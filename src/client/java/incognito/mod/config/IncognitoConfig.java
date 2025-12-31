package incognito.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import incognito.mod.Incognito;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main configuration manager for Incognito mod.
 * Handles loading, saving, and validating configuration settings.
 * Uses thread-safe singleton pattern with double-checked locking.
 */
public class IncognitoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("incognito.json");
    
    private static volatile IncognitoConfig INSTANCE;
    private static final Object LOCK = new Object();
    
    private SpoofSettings settings = new SpoofSettings();
    private volatile String currentServer = null;
    
    private IncognitoConfig() {
        load();
    }
    
    public static IncognitoConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new IncognitoConfig();
                }
            }
        }
        return INSTANCE;
    }
    
    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            Incognito.LOGGER.info("[Incognito] Creating default config");
            save();
            return;
        }
        
        try {
            String content = Files.readString(CONFIG_PATH);
            if (content == null || content.trim().isEmpty()) {
                Incognito.LOGGER.warn("[Incognito] Config file is empty, using defaults");
                settings = new SpoofSettings();
                save();
                return;
            }
            
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            if (json.has("settings")) {
                settings = SpoofSettings.fromJson(json.getAsJsonObject("settings"));
            } else if (json.has("defaultSettings")) {
                settings = SpoofSettings.fromJson(json.getAsJsonObject("defaultSettings"));
            }
            
            if (!validateAndCorrectSettings(settings)) {
                Incognito.LOGGER.warn("[Incognito] Config validation failed, resetting to defaults");
                settings = new SpoofSettings();
                save();
                return;
            }
            
            Incognito.LOGGER.info("[Incognito] Loaded config - brand: {}, spoofing: {}", 
                settings.getCustomBrand(), settings.isSpoofBrand());
        } catch (IOException e) {
            Incognito.LOGGER.error("[Incognito] Failed to read config file: {}", e.getMessage());
            settings = new SpoofSettings();
            save();
        } catch (com.google.gson.JsonSyntaxException | IllegalStateException e) {
            Incognito.LOGGER.error("[Incognito] Invalid JSON in config file: {}", e.getMessage());
            settings = new SpoofSettings();
            save();
        }
    }
    
    /**
     * Validates and auto-corrects settings if necessary.
     * This method modifies invalid settings to valid defaults.
     * 
     * @param settings The settings to validate
     * @return true if settings were valid or have been corrected, false if settings is null
     */
    private boolean validateAndCorrectSettings(SpoofSettings settings) {
        if (settings == null) {
            Incognito.LOGGER.error("[Incognito] Validation failed: settings is null");
            return false;
        }
        
        boolean modified = false;
        
        String brand = settings.getCustomBrand();
        if (brand == null || (!IncognitoConstants.Brands.VANILLA.equals(brand) 
                && !IncognitoConstants.Brands.FABRIC.equals(brand) 
                && !IncognitoConstants.Brands.FORGE.equals(brand))) {
            Incognito.LOGGER.warn("[Incognito] Invalid brand value: {}, resetting to vanilla", brand);
            settings.setCustomBrand(IncognitoConstants.Brands.VANILLA);
            modified = true;
        }
        
        if (settings.getSigningMode() == null) {
            Incognito.LOGGER.warn("[Incognito] Invalid signing mode, resetting to ON_DEMAND");
            settings.setSigningMode(SpoofSettings.SigningMode.ON_DEMAND);
            modified = true;
        }
        
        if (modified) {
            save();
        }
        
        return true;
    }
    
    public void save() {
        try {
            JsonObject json = new JsonObject();
            json.add("settings", settings.toJson());
            
            Files.createDirectories(CONFIG_PATH.getParent());
            
            Path tempFile = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            Files.writeString(tempFile, GSON.toJson(json));
            Files.move(tempFile, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Incognito.LOGGER.error("[Incognito] Failed to save config to {}: {}", CONFIG_PATH, e.getMessage());
        }
    }
    
    public SpoofSettings getSettings() { return settings; }
    
    public void setCurrentServer(String server) { this.currentServer = normalizeAddress(server); }
    public String getCurrentServer() { return currentServer; }
    
    // Identity protection
    public boolean shouldSpoofBrand() { return settings.isSpoofBrand(); }
    public boolean shouldSpoofChannels() { return settings.isSpoofBrand() && settings.isSpoofChannels(); }
    
    /** Convenience method to check if both brand and channel spoofing are active */
    public boolean shouldSpoofBrandAndChannels() { 
        return settings.isSpoofBrand() && settings.isSpoofChannels(); 
    }
    
    public String getEffectiveBrand() { return settings.getEffectiveBrand(); }
    
    // Resource pack protection
    public boolean shouldIsolatePackCache() { return settings.isIsolatePackCache(); }
    public boolean shouldBlockLocalPackUrls() { return settings.isBlockLocalPackUrls(); }
    
    // Translation protection
    public boolean isTranslationProtectionEnabled() { return settings.isTranslationProtectionEnabled(); }
    public boolean isMeteorFix() { return settings.isMeteorFix(); }
    
    // Alerts and logging
    public boolean shouldShowAlerts() { return settings.isShowAlerts(); }
    public boolean shouldShowToasts() { return settings.isShowToasts(); }
    public boolean isLogDetections() { return settings.isLogDetections(); }
    
    // Chat signing
    public SpoofSettings.SigningMode getSigningMode() { return settings.getSigningMode(); }
    public boolean shouldNotSign() { return settings.shouldNotSign(); }
    public boolean isOnDemandSigning() { return settings.isOnDemand(); }
    
    // Privacy
    public boolean shouldDisableTelemetry() { return settings.isDisableTelemetry(); }
    
    /**
     * Normalizes a server address by converting to lowercase and removing default port.
     * @param address The server address to normalize
     * @return Normalized address or "unknown" if null
     */
    public static String normalizeAddress(String address) {
        if (address == null) return "unknown";
        String normalized = address.toLowerCase().trim();
        if (normalized.endsWith(":25565")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        return normalized;
    }
}

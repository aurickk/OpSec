package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Main configuration manager for OpSec mod.
 * Handles loading, saving, and validating configuration settings.
 * Uses thread-safe singleton pattern with double-checked locking.
 */
public class OpsecConfig {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("opsec.json");

    public static final boolean EXPLOIT_PREVENTER_LOADED =
        FabricLoader.getInstance().isModLoaded("exploitpreventer");

    // Per-MC-version feature gate: DownloadQueue + multi-pack stacking arrived 1.20.3.
    // 1.20.1/1.20.2 isolate the pack cache via LegacyDownloadedPackSourceMixin instead.
    //? if >=1.20.3 {
    public static final boolean MC_VERSION_HAS_MULTI_PACK = true;
    //?} else {
    /*public static final boolean MC_VERSION_HAS_MULTI_PACK = false;
    *///?}

    // Block Local URLs: 1.20.1's HttpUtil lambda targeting was unreliable in practice; off there.
    //? if >=1.20.2 {
    public static final boolean MC_VERSION_HAS_BLOCK_LOCAL_URLS = true;
    //?} else {
    /*public static final boolean MC_VERSION_HAS_BLOCK_LOCAL_URLS = false;
    *///?}

    private static volatile OpsecConfig INSTANCE;
    private static final Object LOCK = new Object();

    private SpoofSettings settings = new SpoofSettings();
    private volatile String currentServer = null;

    private OpsecConfig() {
        load();
        if (EXPLOIT_PREVENTER_LOADED) {
            Opsec.LOGGER.info(
                "[OpSec] ExploitPreventer detected - compatibility mode active."
            );
        }
    }

    public static OpsecConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new OpsecConfig();
                }
            }
        }
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            Opsec.LOGGER.info("[OpSec] Creating default config");
            save();
            return;
        }

        try {
            String content = Files.readString(CONFIG_PATH);
            if (content == null || content.trim().isEmpty()) {
                Opsec.LOGGER.warn(
                    "[OpSec] Config file is empty, using defaults"
                );
                settings = new SpoofSettings();
                save();
                return;
            }

            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            if (json.has("settings")) {
                settings = SpoofSettings.fromJson(
                    json.getAsJsonObject("settings")
                );
            } else if (json.has("defaultSettings")) {
                settings = SpoofSettings.fromJson(
                    json.getAsJsonObject("defaultSettings")
                );
            }

            if (!validateAndCorrectSettings(settings)) {
                Opsec.LOGGER.warn(
                    "[OpSec] Config validation failed, resetting to defaults"
                );
                settings = new SpoofSettings();
                save();
                return;
            }

            Opsec.LOGGER.info(
                "[OpSec] Loaded config - spoofAsVanilla: {}",
                settings.isSpoofAsVanilla()
            );
        } catch (IOException e) {
            Opsec.LOGGER.error(
                "[OpSec] Failed to read config file: {}",
                e.getMessage()
            );
            settings = new SpoofSettings();
            save();
        } catch (
            com.google.gson.JsonSyntaxException
            | IllegalStateException e
        ) {
            Opsec.LOGGER.error(
                "[OpSec] Invalid JSON in config file: {}",
                e.getMessage()
            );
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
            Opsec.LOGGER.error("[OpSec] Validation failed: settings is null");
            return false;
        }

        boolean modified = false;

        if (settings.getSigningMode() == null) {
            Opsec.LOGGER.warn(
                "[OpSec] Invalid signing mode, resetting to SIGN"
            );
            settings.setSigningMode(SpoofSettings.SigningMode.SIGN);
            modified = true;
        }

        if (settings.getWhitelistMode() == null) {
            Opsec.LOGGER.warn(
                "[OpSec] Invalid whitelist mode, resetting to AUTO"
            );
            settings.setWhitelistMode(SpoofSettings.WhitelistMode.AUTO);
            modified = true;
        }

        // Spoof-as-vanilla is the master mode: it implies "block ALL custom payloads",
        // so an active whitelist (Auto/Custom) would be moot. Force the whitelist to
        // OFF (block all) whenever spoofAsVanilla is on. setSpoofAsVanilla normally
        // handles the swap, but hand-edited configs can still land here inconsistent.
        if (settings.isSpoofAsVanilla() && settings.getWhitelistMode() != SpoofSettings.WhitelistMode.OFF) {
            Opsec.LOGGER.warn(
                "[OpSec] active whitelist incompatible with spoofAsVanilla; forcing whitelist OFF (block all)"
            );
            settings.setWhitelistMode(SpoofSettings.WhitelistMode.OFF);
            modified = true;
        }

        // Block-All is no longer user-selectable: when spoof-vanilla is off, migrate
        // any leftover OFF mode (from older configs that exposed it as a tri-state)
        // back to AUTO so the user isn't stuck in an unreachable state.
        if (!settings.isSpoofAsVanilla() && settings.getWhitelistMode() == SpoofSettings.WhitelistMode.OFF) {
            Opsec.LOGGER.info(
                "[OpSec] Migrating legacy whitelist OFF (block all) to AUTO — option is no longer user-selectable"
            );
            settings.setWhitelistMode(SpoofSettings.WhitelistMode.AUTO);
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

            Path tempFile = CONFIG_PATH.resolveSibling(
                CONFIG_PATH.getFileName() + ".tmp"
            );
            Files.writeString(tempFile, GSON.toJson(json));
            Files.move(
                tempFile,
                CONFIG_PATH,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            Opsec.LOGGER.error(
                "[OpSec] Failed to save config to {}: {}",
                CONFIG_PATH,
                e.getMessage()
            );
        }
        // Null-guard avoids constructor's save→getInstance recursion; OpsecClient seeds the first build.
        if (INSTANCE != null) {
            ModRegistry.rebuildDependencyClosure();
        }
    }

    public SpoofSettings getSettings() {
        return settings;
    }

    public void setCurrentServer(String server) {
        this.currentServer = normalizeAddress(server);
    }

    public String getCurrentServer() {
        return currentServer;
    }

    // Identity protection
    /**
     * Whether the brand string should be overridden to "vanilla". Only true when
     * the user opted in via the UI toggle and ExploitPreventer isn't loaded.
     */
    public boolean shouldSpoofBrand() {
        return !EXPLOIT_PREVENTER_LOADED && settings.isSpoofAsVanilla();
    }

    /**
     * Whether channel spoofing/filtering should be active. Independent of brand —
     * channel filtering happens whenever OpSec is in charge (i.e., EP not loaded).
     * The filter mode (block-all vs whitelist) is chosen by isVanillaMode/
     * isFabricMode in SpoofSettings, which read directly from spoofAsVanilla.
     */
    public boolean shouldSpoofChannels() {
        return !EXPLOIT_PREVENTER_LOADED;
    }

    public String getEffectiveBrand() {
        return settings.getEffectiveBrand();
    }

    // Resource pack protection
    public boolean shouldIsolatePackCache() {
        return !EXPLOIT_PREVENTER_LOADED && settings.isIsolatePackCache();
    }

    public boolean shouldBlockLocalPackUrls() {
        return !EXPLOIT_PREVENTER_LOADED && settings.isBlockLocalPackUrls();
    }

    // Key resolution protection
    public boolean isTranslationProtectionEnabled() {
        return (
            !EXPLOIT_PREVENTER_LOADED &&
            settings.isTranslationProtectionEnabled()
        );
    }

    public boolean isMeteorFix() {
        return settings.isMeteorFix();
    }

    // Bypass Server Pack Requirement
    public SpoofSettings.StripMode getPackStripMode() {
        return settings.getPackStripMode();
    }

    public boolean shouldStripPack() {
        return !EXPLOIT_PREVENTER_LOADED;
    }

    /** The consent overlay prompt should show for an incoming push. */
    public boolean shouldShowPackOverlay() {
        return (
            !EXPLOIT_PREVENTER_LOADED &&
            settings.getPackStripMode() == SpoofSettings.StripMode.ASK
        );
    }

    // Alerts and logging
    public boolean shouldShowAlerts() {
        return settings.isShowAlerts();
    }

    public boolean shouldShowToasts() {
        return settings.isShowToasts();
    }

    public boolean isLogDetections() {
        return settings.isLogDetections();
    }

    public boolean isDebugAlerts() {
        return settings.isDebugAlerts();
    }

    // Chat signing
    public SpoofSettings.SigningMode getSigningMode() {
        return settings.getSigningMode();
    }

    public boolean shouldNotSign() {
        return settings.shouldNotSign();
    }

    // Privacy
    public boolean shouldDisableTelemetry() {
        return settings.isDisableTelemetry();
    }

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

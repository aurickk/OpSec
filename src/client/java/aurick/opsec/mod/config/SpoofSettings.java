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
     *
     * <p>ON_DEMAND was removed: it could not be implemented without either a
     * server-detectable fingerprint (auto-upgrade based on attacker-controlled
     * system chat) or breaking on every proxy network (login flag is set by the
     * proxy, not the backend). Users who want maximum privacy set OFF and
     * accept that some servers will reject their chats; users who want chat to
     * just work set SIGN. The mod no longer tries to straddle the two.</p>
     */
    public enum SigningMode {
        /** Always sign messages (chat reportable to Mojang). Default. */
        SIGN,
        /** Never sign messages (may break on strict servers). */
        OFF
    }

    /**
     * Whitelist modes for mod filtering.
     * Controls which mods are allowed through the whitelist.
     */
    public enum WhitelistMode {
        /** Whitelist disabled - all mod content blocked */
        OFF,
        /** Auto-whitelist mods that have registered network channels */
        AUTO,
        /** Manual whitelist - only explicitly selected mods allowed */
        CUSTOM
    }

    /**
     * Bypass Server Pack Requirement modes.
     * Controls whether server-pushed resource packs have their visual/audio content
     * stripped (textures/sounds/models/fonts removed; lang data still applied so
     * translation-key probes resolve vanilla-identically).
     * In all modes the user can toggle any server pack in the resource pack menu;
     * the mode only decides the initial state and whether a consent overlay appears.
     */
    public enum StripMode {
        /** Default vanilla behavior on push. User may still toggle the pack off via the
         *  resource pack menu to strip it while keeping lang loaded. */
        MANUAL,
        /** Consent screen asks on push. Pack is stripped until the user opts in. */
        ASK,
        /** No consent screen. Pack is always stripped; user may still toggle it on. */
        ALWAYS_ON
    }
    
    // Brand spoofing — when true, the client advertises a vanilla brand and blocks
    // ALL outbound custom payloads. When false, the natural Fabric brand passes
    // through and channels are filtered via the Whitelist tab (Block All / Auto /
    // Custom). The UI prevents spoofAsVanilla=true while the whitelist is in
    // Auto/Custom — the resulting state would be incoherent (vanilla brand
    // advertising selective mod channels).
    private boolean spoofAsVanilla = false;

    // Resource pack protection
    private boolean isolatePackCache = true;
    private boolean blockLocalPackUrls = true;
    
    // Key resolution protection
    private boolean translationProtection = true;
    private boolean fakeDefaultKeybinds = true;  // Spoof vanilla keybinds to default values
    private boolean meteorFix = true;  // Block Meteor's broken key resolution protection

    // Strip Server Pack
    // Pack is still downloaded + cached by vanilla (fingerprint-normal), lang is loaded
    // into ClientLanguage as a vanilla client would; only textures/sounds/models/fonts
    // are filtered out via LangOnlyPackResources.
    private StripMode packStripMode = StripMode.MANUAL;
    
    // Alerts
    private boolean showAlerts = true;
    private boolean showToasts = true;
    private boolean logDetections = true;
    private boolean debugAlerts = false;
    
    // Chat Signing
    private SigningMode signingMode = SigningMode.SIGN;
    
    // Privacy
    private boolean disableTelemetry = true;
    
    // UI Settings
    private int buttonX = -1;
    private int buttonY = -1;

    // Update notification
    private String skippedUpdateVersion = "";
    private boolean tamperWarningDismissed = false;

    // One-time hints (persisted, never shown again)
    private boolean alertHintShown = false;
    
    // Whitelist settings
    private WhitelistMode whitelistMode = WhitelistMode.AUTO;
    // Snapshot of whitelistMode taken when spoofAsVanilla flipped on, so we can
    // restore it when the user toggles spoofAsVanilla off. Null when spoofAsVanilla
    // is currently false. Block-All (WhitelistMode.OFF) is the implicit override
    // used while spoofAsVanilla is on and is not user-selectable from the UI.
    private WhitelistMode previousWhitelistMode = null;
    private Set<String> whitelistedMods = new HashSet<>();

    public SpoofSettings() {}

    public boolean isSpoofAsVanilla() { return spoofAsVanilla; }
    public void setSpoofAsVanilla(boolean spoofAsVanilla) {
        if (this.spoofAsVanilla == spoofAsVanilla) return;
        if (spoofAsVanilla) {
            // Off -> On: remember the user's current whitelist choice so we can
            // restore it later, then force the Block-All override.
            if (this.whitelistMode != WhitelistMode.OFF) {
                this.previousWhitelistMode = this.whitelistMode;
            }
            this.whitelistMode = WhitelistMode.OFF;
        } else {
            // On -> Off: restore the user's prior whitelist choice. Default to
            // AUTO if there is no remembered choice (or it was somehow OFF) so
            // the user is never left in the now-unreachable Block-All state.
            WhitelistMode restored = this.previousWhitelistMode;
            if (restored == null || restored == WhitelistMode.OFF) {
                restored = WhitelistMode.AUTO;
            }
            this.whitelistMode = restored;
            this.previousWhitelistMode = null;
        }
        this.spoofAsVanilla = spoofAsVanilla;
    }

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

    public StripMode getPackStripMode() { return packStripMode; }
    public void setPackStripMode(StripMode mode) { this.packStripMode = mode != null ? mode : StripMode.MANUAL; }
    
    public boolean isShowAlerts() { return showAlerts; }
    public void setShowAlerts(boolean showAlerts) { this.showAlerts = showAlerts; }
    
    public boolean isShowToasts() { return showToasts; }
    public void setShowToasts(boolean showToasts) { this.showToasts = showToasts; }
    
    public boolean isLogDetections() { return logDetections; }
    public void setLogDetections(boolean logDetections) { this.logDetections = logDetections; }

    public boolean isDebugAlerts() { return debugAlerts; }
    public void setDebugAlerts(boolean debugAlerts) { this.debugAlerts = debugAlerts; }
    
    // Signing mode methods
    public SigningMode getSigningMode() { return signingMode; }
    public void setSigningMode(SigningMode mode) { this.signingMode = mode; }

    public boolean shouldNotSign() {
        return signingMode == SigningMode.OFF;
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
    
    // Alert hint methods
    public boolean isAlertHintShown() { return alertHintShown; }
    public void setAlertHintShown(boolean shown) { this.alertHintShown = shown; }

    // Whitelist methods
    public WhitelistMode getWhitelistMode() { return whitelistMode; }
    public void setWhitelistMode(WhitelistMode mode) { this.whitelistMode = mode; }

    /** Convenience: returns true when whitelist is active (AUTO or ON) */
    public boolean isWhitelistEnabled() { return whitelistMode != WhitelistMode.OFF; }

    public Set<String> getWhitelistedMods() { return whitelistedMods; }
    public void setWhitelistedMods(Set<String> mods) { this.whitelistedMods = mods != null ? mods : new HashSet<>(); }

    /** Returns true only in ON mode when mod is in the manual set */
    public boolean isModWhitelisted(String modId) {
        return whitelistMode == WhitelistMode.CUSTOM && modId != null && whitelistedMods.contains(modId);
    }

    // Update notification methods
    public String getSkippedUpdateVersion() { return skippedUpdateVersion; }
    public void setSkippedUpdateVersion(String version) { this.skippedUpdateVersion = version != null ? version : ""; }
    public boolean isVersionSkipped(String version) { return version != null && version.equals(skippedUpdateVersion); }

    public boolean isTamperWarningDismissed() { return tamperWarningDismissed; }
    public void setTamperWarningDismissed(boolean dismissed) { this.tamperWarningDismissed = dismissed; }

    public String getEffectiveBrand() {
        return spoofAsVanilla ? VANILLA : FABRIC;
    }

    public boolean isVanillaMode() {
        return spoofAsVanilla;
    }

    public boolean isFabricMode() {
        return !spoofAsVanilla;
    }
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("spoofAsVanilla", spoofAsVanilla);
        json.addProperty("isolatePackCache", isolatePackCache);
        json.addProperty("blockLocalPackUrls", blockLocalPackUrls);
        json.addProperty("translationProtection", translationProtection);
        json.addProperty("fakeDefaultKeybinds", fakeDefaultKeybinds);
        json.addProperty("meteorFix", meteorFix);
        json.addProperty("packStripMode", packStripMode.name());
        json.addProperty("showAlerts", showAlerts);
        json.addProperty("showToasts", showToasts);
        json.addProperty("logDetections", logDetections);
        json.addProperty("debugAlerts", debugAlerts);
        json.addProperty("signingMode", signingMode.name());
        json.addProperty("disableTelemetry", disableTelemetry);
        json.addProperty("buttonX", buttonX);
        json.addProperty("buttonY", buttonY);
        json.addProperty("skippedUpdateVersion", skippedUpdateVersion);
        json.addProperty("tamperWarningDismissed", tamperWarningDismissed);
        json.addProperty("alertHintShown", alertHintShown);

        // Whitelist settings
        json.addProperty("whitelistMode", whitelistMode.name());
        if (previousWhitelistMode != null) {
            json.addProperty("previousWhitelistMode", previousWhitelistMode.name());
        }
        JsonArray modsArray = new JsonArray();
        for (String modId : whitelistedMods) {
            modsArray.add(modId);
        }
        json.add("whitelistedMods", modsArray);

        return json;
    }
    
    public static SpoofSettings fromJson(JsonObject json) {
        SpoofSettings s = new SpoofSettings();
        if (json.has("spoofAsVanilla")) {
            s.spoofAsVanilla = json.get("spoofAsVanilla").getAsBoolean();
        } else if (json.has("spoofBrand")) {
            // Legacy migration: old spoofBrand + customBrand collapse into a single toggle.
            // spoofAsVanilla=true iff the old config was actively spoofing AND spoofing to vanilla.
            boolean sb = json.get("spoofBrand").getAsBoolean();
            String cb = json.has("customBrand") ? json.get("customBrand").getAsString() : FABRIC;
            s.spoofAsVanilla = sb && VANILLA.equalsIgnoreCase(cb);
        }
        if (json.has("isolatePackCache")) s.isolatePackCache = json.get("isolatePackCache").getAsBoolean();
        if (json.has("blockLocalPackUrls")) s.blockLocalPackUrls = json.get("blockLocalPackUrls").getAsBoolean();
        // Legacy support
        if (json.has("spoofLocalPackUrls")) s.blockLocalPackUrls = json.get("spoofLocalPackUrls").getAsBoolean();
        if (json.has("translationProtection")) s.translationProtection = json.get("translationProtection").getAsBoolean();
        // Legacy support
        if (json.has("blockTranslationExploit")) s.translationProtection = json.get("blockTranslationExploit").getAsBoolean();
        if (json.has("fakeDefaultKeybinds")) s.fakeDefaultKeybinds = json.get("fakeDefaultKeybinds").getAsBoolean();
        if (json.has("meteorFix")) s.meteorFix = json.get("meteorFix").getAsBoolean();
        // Bypass Server Pack Requirement (tri-state, with backward-compat migrations)
        if (json.has("packStripMode")) {
            String raw = json.get("packStripMode").getAsString();
            // Legacy OFF/ON enum values from the unreleased staged version.
            s.packStripMode = switch (raw) {
                case "OFF" -> StripMode.MANUAL;
                case "ON"  -> StripMode.ALWAYS_ON;
                default    -> {
                    try {
                        yield StripMode.valueOf(raw);
                    } catch (IllegalArgumentException e) {
                        yield StripMode.MANUAL;
                    }
                }
            };
        } else if (json.has("fakeAcceptResourcePack")) {
            boolean legacyEnabled = json.get("fakeAcceptResourcePack").getAsBoolean();
            boolean legacyOverlay = !json.has("showPackAcceptOverlay")
                    || json.get("showPackAcceptOverlay").getAsBoolean();
            s.packStripMode = !legacyEnabled ? StripMode.MANUAL
                    : (legacyOverlay ? StripMode.ASK : StripMode.ALWAYS_ON);
        }
        if (json.has("showAlerts")) s.showAlerts = json.get("showAlerts").getAsBoolean();
        if (json.has("showToasts")) s.showToasts = json.get("showToasts").getAsBoolean();
        if (json.has("logDetections")) s.logDetections = json.get("logDetections").getAsBoolean();
        if (json.has("debugAlerts")) s.debugAlerts = json.get("debugAlerts").getAsBoolean();
        if (json.has("signingMode")) {
            String mode = json.get("signingMode").getAsString();
            // Legacy migrations:
            //   NO_KEY / NO_SIGN  → OFF  (old enum values)
            //   ON_DEMAND         → SIGN (removed: see SigningMode javadoc)
            //   unknown           → SIGN (safe default — chat just works)
            if ("NO_KEY".equals(mode) || "NO_SIGN".equals(mode) || "OFF".equals(mode)) {
                s.signingMode = SigningMode.OFF;
            } else {
                s.signingMode = SigningMode.SIGN;
            }
        }
        if (json.has("disableTelemetry")) s.disableTelemetry = json.get("disableTelemetry").getAsBoolean();
        if (json.has("buttonX")) s.buttonX = json.get("buttonX").getAsInt();
        if (json.has("buttonY")) s.buttonY = json.get("buttonY").getAsInt();
        if (json.has("skippedUpdateVersion")) s.skippedUpdateVersion = json.get("skippedUpdateVersion").getAsString();
        if (json.has("tamperWarningDismissed")) s.tamperWarningDismissed = json.get("tamperWarningDismissed").getAsBoolean();
        if (json.has("alertHintShown")) s.alertHintShown = json.get("alertHintShown").getAsBoolean();

        // Whitelist settings (new tri-state, with backward compat for old boolean)
        if (json.has("whitelistMode")) {
            String modeStr = json.get("whitelistMode").getAsString();
            if ("ON".equals(modeStr)) modeStr = "CUSTOM";
            try {
                s.whitelistMode = WhitelistMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                s.whitelistMode = WhitelistMode.OFF;
            }
        } else if (json.has("whitelistEnabled")) {
            s.whitelistMode = json.get("whitelistEnabled").getAsBoolean() ? WhitelistMode.CUSTOM : WhitelistMode.OFF;
        }
        if (json.has("previousWhitelistMode")) {
            String prevStr = json.get("previousWhitelistMode").getAsString();
            try {
                WhitelistMode prev = WhitelistMode.valueOf(prevStr);
                // OFF is never a meaningful "previous" (it's the override state) — drop it.
                if (prev != WhitelistMode.OFF) {
                    s.previousWhitelistMode = prev;
                }
            } catch (IllegalArgumentException ignored) {
                // unknown enum value — leave null
            }
        }
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
        this.spoofAsVanilla = other.spoofAsVanilla;
        this.isolatePackCache = other.isolatePackCache;
        this.blockLocalPackUrls = other.blockLocalPackUrls;
        this.translationProtection = other.translationProtection;
        this.fakeDefaultKeybinds = other.fakeDefaultKeybinds;
        this.meteorFix = other.meteorFix;
        this.packStripMode = other.packStripMode;
        this.showAlerts = other.showAlerts;
        this.showToasts = other.showToasts;
        this.logDetections = other.logDetections;
        this.debugAlerts = other.debugAlerts;
        this.signingMode = other.signingMode;
        this.disableTelemetry = other.disableTelemetry;
        this.buttonX = other.buttonX;
        this.buttonY = other.buttonY;
        this.skippedUpdateVersion = other.skippedUpdateVersion;
        this.tamperWarningDismissed = other.tamperWarningDismissed;
        this.alertHintShown = other.alertHintShown;
        this.whitelistMode = other.whitelistMode;
        this.previousWhitelistMode = other.previousWhitelistMode;
        this.whitelistedMods = new HashSet<>(other.whitelistedMods);
    }
}

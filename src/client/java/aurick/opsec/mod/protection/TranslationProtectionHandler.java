package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.detection.ExploitContext;

import aurick.opsec.mod.config.SpoofSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized handler for key resolution protection alerts.
 *
 * Alert format:
 * ⛔ [OpSec] Key resolution exploit detected!     (header with cooldown)
 * [key.meteor-client.open-gui] 'Right Shift'→'key.meteor-client.open-gui'  (detail, deduped)
 * [key.hotbar.6] 'Q'→'6'
 *
 * - Header: Sent once per 5 seconds (cooldown prevents spam)
 * - Details: Sent when values are changed (deduped per session)
 * - Logging: Deduped to prevent spam from multiple render calls
 * - Detection works even if protection is OFF (alerts/logs still show)
 */
public class TranslationProtectionHandler {

    /** The type of interception that triggered the alert. */
    public enum InterceptionType {
        TRANSLATION("Translation"),
        KEYBIND("Keybind");

        private final String displayName;
        InterceptionType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
    
    /** Dedup key for detail alerts — just key name, since same key produces same detail within a probe */
    private record AlertDedupeKey(String keyName) {}

    /** Dedup key for logs — full tuple to preserve log accuracy */
    private record LogDedupeKey(InterceptionType type, String keyName, String originalValue, String spoofedValue) {}

    // Separate deduplication sets for alerts and logging
    private static final Set<AlertDedupeKey> alertedKeys = ConcurrentHashMap.newKeySet();
    private static final Set<LogDedupeKey> loggedKeys = ConcurrentHashMap.newKeySet();

    // Size limits to prevent unbounded growth
    private static final int MAX_DEDUPE_ENTRIES = 500;
    
    private static volatile long lastHeaderTime = 0;

    private static final long HEADER_COOLDOWN_MS = 5000;  // 5 seconds between headers
    
    private TranslationProtectionHandler() {}
    
    /**
     * Notify that an exploit attempt was detected (header alert only).
     * Shows header alert when in exploitable context, regardless of key changes.
     */
    public static void notifyExploitDetected() {
        if (!shouldProcess()) {
            return;
        }
        
        long now = System.currentTimeMillis();

        // Header with cooldown: ⛔ [OpSec] Key resolution exploit detected!
        if (now - lastHeaderTime >= HEADER_COOLDOWN_MS) {
            lastHeaderTime = now;
            
            // Get source for logging only
            PrivacyLogger.ExploitSource source = ExploitContext.getSource();
            
            // Alert without source (source is in logs)
            if (OpsecConfig.getInstance().shouldShowAlerts()) {
                PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER, "Key resolution exploit detected!");
            }
            
            // Toast notification (separate from chat alerts)
            PrivacyLogger.toast(PrivacyLogger.AlertType.DANGER, "Key Resolution Exploit Detected");
            
            // Log with source
            if (OpsecConfig.getInstance().isLogDetections()) {
                Opsec.LOGGER.info("[OpSec] Key resolution exploit detected via {}",
                    source.getDisplayName().toLowerCase());
            }

            // One-time hint about disabling alerts
            SpoofSettings settings = OpsecConfig.getInstance().getSettings();
            if (!settings.isAlertHintShown()) {
                settings.setAlertHintShown(true);
                OpsecConfig.getInstance().save();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.literal("Chat and toast alerts can be disabled in OpSec > Misc settings.")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                }
            }
        }
    }
    
    /**
     * Send detail alert for a changed key.
     * Only call when value actually changed.
     * Deduped per session to prevent spam.
     *
     * @param type The interception type (TRANSLATION or KEYBIND)
     * @param keyName The translation/keybind key name
     * @param originalValue What Minecraft would have resolved it to
     * @param spoofedValue What we're returning instead
     */
    public static void sendDetail(InterceptionType type, String keyName, String originalValue, String spoofedValue) {
        if (!OpsecConfig.getInstance().shouldShowAlerts()) {
            return;
        }

        // Clear if too large to prevent unbounded growth
        if (alertedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            alertedKeys.clear();
        }

        // Dedupe by key name — same key won't produce different details within a probe
        if (!alertedKeys.add(new AlertDedupeKey(keyName))) {
            return;
        }

        // Detail alert: [key.hotbar.6] 'Q'→'6'
        PrivacyLogger.sendKeybindDetail(
            "[" + keyName + "] '" + originalValue + "'→'" + spoofedValue + "'");
    }
    
    /**
     * Log detection details.
     * Deduped to prevent spam from multiple render calls.
     *
     * @param type The interception type (TRANSLATION or KEYBIND)
     * @param keyName The translation/keybind key name
     * @param originalValue What Minecraft would have resolved it to
     * @param spoofedValue What we're returning (may be same as original)
     */
    public static void logDetection(InterceptionType type, String keyName, String originalValue, String spoofedValue) {
        if (!OpsecConfig.getInstance().isLogDetections()) {
            return;
        }

        // Clear if too large to prevent unbounded growth
        if (loggedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            loggedKeys.clear();
        }

        // Dedupe by full tuple to preserve log accuracy
        if (!loggedKeys.add(new LogDedupeKey(type, keyName, originalValue, spoofedValue))) {
            return;
        }

        PrivacyLogger.ExploitSource source = ExploitContext.getSource();
        Opsec.LOGGER.info("[{}:{}] '{}' '{}' → '{}'",
            type.getDisplayName(), source.getDisplayName(), keyName, originalValue, spoofedValue);
    }
    
    /**
     * Check if we should process alerts/logs.
     * When both alerts AND logging are disabled, skip everything.
     */
    private static boolean shouldProcess() {
        return OpsecConfig.getInstance().shouldShowAlerts() 
            || OpsecConfig.getInstance().isLogDetections();
    }
    
    /**
     * Clear key-level dedup caches. Called when entering a new exploit context
     * so each sign/anvil probe gets fresh alerts and logs.
     * Does NOT reset the header cooldown — that prevents header spam across rapid probes.
     */
    public static void clearDedup() {
        alertedKeys.clear();
        loggedKeys.clear();
    }

    /**
     * Clear all cached state. Called on disconnect.
     */
    public static void clearCache() {
        alertedKeys.clear();
        loggedKeys.clear();
        lastHeaderTime = 0;
    }
}

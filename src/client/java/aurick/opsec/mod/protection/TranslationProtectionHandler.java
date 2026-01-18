package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.detection.ExploitContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized handler for translation key and keybind protection alerts.
 * 
 * Alert format:
 * ⛔ [OpSec] Translation exploit detected!     (header with cooldown)
 * [key.meteor-client.open-gui] 'Right Shift'→'key.meteor-client.open-gui'  (detail, deduped)
 * [key.hotbar.6] 'Q'→'6'
 * 
 * - Header: Sent once per 5 seconds (cooldown prevents spam)
 * - Details: Sent when values are changed (deduped per session)
 * - Logging: Deduped to prevent spam from multiple render calls
 * - Detection works even if protection is OFF (alerts/logs still show)
 */
public class TranslationProtectionHandler {
    
    /** Record for efficient deduplication key (avoids string concatenation) */
    private record DedupeKey(String keyName, String originalValue, String spoofedValue) {}
    
    // Separate deduplication sets for alerts and logging
    private static final Set<DedupeKey> alertedKeys = ConcurrentHashMap.newKeySet();
    private static final Set<DedupeKey> loggedKeys = ConcurrentHashMap.newKeySet();
    
    // Size limits to prevent unbounded growth
    private static final int MAX_DEDUPE_ENTRIES = 500;
    
    private static volatile long lastHeaderTime = 0;
    private static volatile long lastClearTime = 0;
    
    private static final long HEADER_COOLDOWN_MS = 5000;  // 5 seconds between headers
    private static final long DEDUPE_CLEAR_MS = 10000;    // Clear dedupe every 10 seconds
    
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
        
        // Clear dedupe caches periodically
        if (now - lastClearTime > DEDUPE_CLEAR_MS) {
            alertedKeys.clear();
            loggedKeys.clear();
            lastClearTime = now;
        }
        
        // Header with cooldown: ⛔ [OpSec] Translation exploit detected!
        if (now - lastHeaderTime >= HEADER_COOLDOWN_MS) {
            lastHeaderTime = now;
            
            // Get source for logging only
            PrivacyLogger.ExploitSource source = ExploitContext.getSource();
            
            // Alert without source (source is in logs)
            if (OpsecConfig.getInstance().shouldShowAlerts()) {
                PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER, "Translation exploit detected!");
                PrivacyLogger.toast(PrivacyLogger.AlertType.DANGER, "Translation Exploit Detected");
            }
            
            // Log with source
            if (OpsecConfig.getInstance().isLogDetections()) {
                Opsec.LOGGER.info("[OpSec] Translation exploit detected via {}", 
                    source.getDisplayName().toLowerCase());
            }
        }
    }
    
    /**
     * Send detail alert for a changed key.
     * Only call when value actually changed.
     * Deduped per session to prevent spam.
     * 
     * @param keyName The translation/keybind key name
     * @param originalValue What Minecraft would have resolved it to
     * @param spoofedValue What we're returning instead
     */
    public static void sendDetail(String keyName, String originalValue, String spoofedValue) {
        if (!OpsecConfig.getInstance().shouldShowAlerts()) {
            return;
        }
        
        // Clear if too large to prevent unbounded growth
        if (alertedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            alertedKeys.clear();
        }
        
        // Dedupe check - don't alert same key:value combo repeatedly
        DedupeKey key = new DedupeKey(keyName, originalValue, spoofedValue);
        
        // Skip if already alerted this exact key:value:spoofed combo
        if (!alertedKeys.add(key)) {
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
     * @param keyName The translation/keybind key name
     * @param originalValue What Minecraft would have resolved it to
     * @param spoofedValue What we're returning (may be same as original)
     */
    public static void logDetection(String keyName, String originalValue, String spoofedValue) {
        if (!OpsecConfig.getInstance().isLogDetections()) {
            return;
        }
        
        // Clear if too large to prevent unbounded growth
        if (loggedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            loggedKeys.clear();
        }
        
        // Dedupe check - don't log same key:value combo repeatedly
        DedupeKey key = new DedupeKey(keyName, originalValue, spoofedValue);
        
        // Skip if already logged this exact key:value:spoofed combo
        if (!loggedKeys.add(key)) {
            return;
        }
        
        PrivacyLogger.ExploitSource source = ExploitContext.getSource();
        Opsec.LOGGER.info("[Translation:{}] '{}' '{}' → '{}'", 
            source.getDisplayName(), keyName, originalValue, spoofedValue);
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
     * Clear all cached state. Called on disconnect.
     */
    public static void clearCache() {
        alertedKeys.clear();
        loggedKeys.clear();
        lastHeaderTime = 0;
        lastClearTime = 0;
    }
}

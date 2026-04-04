package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized handler for key resolution protection alerts.
 *
 * Alert format:
 * [OpSec] Key resolution probe detected           (header with cooldown)
 * [key.meteor-client.open-gui] 'Right Shift'→'key.meteor-client.open-gui'  (detail, deduped)
 * [key.hotbar.6] 'Q'→'6'
 *
 * - Header: Deferred until a value change unless debug alerts are ON
 * - Details: Sent when values are changed (deduped per session)
 * - Debug mode: Header fires immediately, details shown for ALL keys including unchanged
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

    /** Dedup key for detail alerts — type + key name, since Translation and Keybind produce different details */
    private record AlertDedupeKey(InterceptionType type, String keyName) {}

    /** Dedup key for logs — full tuple to preserve log accuracy */
    private record LogDedupeKey(InterceptionType type, String packetName, String keyName, String originalValue, String spoofedValue) {}

    // Separate deduplication sets for alerts and logging
    private static final Set<AlertDedupeKey> alertedKeys = ConcurrentHashMap.newKeySet();
    private static final Set<LogDedupeKey> loggedKeys = ConcurrentHashMap.newKeySet();

    // Size limits to prevent unbounded growth
    private static final int MAX_DEDUPE_ENTRIES = 500;

    private static volatile long lastHeaderTime = 0;
    private static volatile boolean headerPending = false;

    private static final long HEADER_COOLDOWN_MS = 5000;  // 5 seconds between headers

    private TranslationProtectionHandler() {}

    /**
     * Notify that an exploit attempt was detected.
     *
     * In debug mode: emits header immediately (current behavior).
     * In normal mode: defers header until {@link #sendDetail} confirms a value change.
     */
    public static void notifyExploitDetected() {
        if (!shouldProcess()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Cooldown check applies to both modes
        if (now - lastHeaderTime < HEADER_COOLDOWN_MS) {
            return;
        }

        if (OpsecConfig.getInstance().isDebugAlerts()) {
            // Debug mode: emit header immediately
            lastHeaderTime = now;
            emitHeader();
        } else {
            // Normal mode: defer header until sendDetail confirms a value change
            headerPending = true;
        }
    }

    /**
     * Emit the header alert, toast, log, and one-time hint.
     * Called either immediately (debug mode) or deferred (normal mode, from sendDetail).
     */
    private static void emitHeader() {
        String source = PacketContext.getPacketName();

        // Chat alert: red, no emoji icon
        if (OpsecConfig.getInstance().shouldShowAlerts()) {
            Minecraft mc = Minecraft.getInstance();
            Runnable sendAlert = () -> {
                if (mc.player != null) {
                    //? if >=26.1 {
                    /*mc.player.sendSystemMessage(
                        Component.literal("[OpSec] ").withStyle(ChatFormatting.DARK_PURPLE)
                            .append(Component.literal("Key resolution probe detected").withStyle(ChatFormatting.RED)));*/
                    //?} else {
                    mc.player.displayClientMessage(
                        Component.literal("[OpSec] ").withStyle(ChatFormatting.DARK_PURPLE)
                            .append(Component.literal("Key resolution probe detected").withStyle(ChatFormatting.RED)),
                        false);
                    //?}
                }
            };
            if (mc.isSameThread()) {
                sendAlert.run();
            } else {
                mc.execute(sendAlert);
            }
        }

        // Toast notification: red, no emoji icon
        if (OpsecConfig.getInstance().shouldShowToasts()) {
            PrivacyLogger.showToastRaw(
                Component.literal("Key Resolution Probe Detected").withStyle(ChatFormatting.RED),
                null);
        }

        // Log with source
        if (OpsecConfig.getInstance().isLogDetections()) {
            Opsec.LOGGER.info("[OpSec] Key resolution exploit detected via {}", source);
        }

        // One-time hint about disabling alerts (delayed so it appears after the first alert)
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isAlertHintShown()) {
            settings.setAlertHintShown(true);
            OpsecConfig.getInstance().save();
            CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        //? if >=26.1 {
                        /*mc.player.sendSystemMessage(
                            Component.literal("Chat and toast alerts can be disabled in OpSec > Misc settings.")
                                .withStyle(ChatFormatting.AQUA));*/
                        //?} else {
                        mc.player.displayClientMessage(
                            Component.literal("Chat and toast alerts can be disabled in OpSec > Misc settings.")
                                .withStyle(ChatFormatting.AQUA), false);
                        //?}
                    }
                });
            });
        }
    }

    /**
     * Send detail alert for a key interception.
     * Deduped per session to prevent spam.
     *
     * In normal mode, flushes the deferred header on the first detail.
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

        // Dedupe by type + key name — Translation and Keybind produce different details for the same key
        if (!alertedKeys.add(new AlertDedupeKey(type, keyName))) {
            return;
        }

        // Flush deferred header on first detail
        if (headerPending) {
            headerPending = false;
            lastHeaderTime = System.currentTimeMillis();
            emitHeader();
        }

        // Detail alert: [key.hotbar.6] 'Q'→'6'
        // In debug mode, prepend [Type:packetName] in purple
        if (OpsecConfig.getInstance().isDebugAlerts()) {
            String packetName = PacketContext.getPacketName();
            MutableComponent detail = Component.literal("[" + type.getDisplayName() + ":" + packetName + "] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal("[" + keyName + "] '" + originalValue + "'→'" + spoofedValue + "'").withStyle(ChatFormatting.DARK_GRAY));
            PrivacyLogger.sendKeybindDetail(detail);
        } else {
            PrivacyLogger.sendKeybindDetail(
                "[" + keyName + "] '" + originalValue + "'→'" + spoofedValue + "'");
        }
    }

    /**
     * Send detail for debug mode only.
     * Called from paths that don't normally send details (unchanged values,
     * protection-disabled). Only fires when debug alerts are enabled.
     *
     * @param type The interception type (TRANSLATION or KEYBIND)
     * @param keyName The translation/keybind key name
     * @param originalValue What Minecraft would have resolved it to
     * @param spoofedValue What we're returning (may be same as original)
     */
    public static void sendDetailDebug(InterceptionType type, String keyName, String originalValue, String spoofedValue) {
        if (!OpsecConfig.getInstance().isDebugAlerts()) return;
        sendDetail(type, keyName, originalValue, spoofedValue);
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

        String packetName = PacketContext.getPacketName();

        // Clear if too large to prevent unbounded growth
        if (loggedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            loggedKeys.clear();
        }

        // Dedupe by full tuple to preserve log accuracy
        if (!loggedKeys.add(new LogDedupeKey(type, packetName, keyName, originalValue, spoofedValue))) {
            return;
        }

        Opsec.LOGGER.info("[{}:{}] '{}' '{}' -> '{}'",
            type.getDisplayName(), packetName, keyName, originalValue, spoofedValue);
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
        headerPending = false;
    }

    /**
     * Clear all cached state. Called on disconnect.
     */
    public static void clearCache() {
        alertedKeys.clear();
        loggedKeys.clear();
        lastHeaderTime = 0;
        headerPending = false;
    }
}

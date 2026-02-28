package aurick.opsec.mod;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages privacy-related alerts, toasts, and logging for exploit detection.
 * Provides methods to notify users of detected tracking attempts and security events.
 */
public class PrivacyLogger {
    // Bounded LRU cache for toast cooldowns
    private static final Map<String, Long> toastCooldowns = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > OpsecConstants.Limits.MAX_TOAST_COOLDOWNS;
        }
    };
    private static final Object COOLDOWN_LOCK = new Object();
    
    private static final Set<String> pendingPortScans = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger totalPortScansBlocked = new AtomicInteger(0);
    private static final AtomicBoolean portScanSummaryShown = new AtomicBoolean(false);
    
    private static boolean isToastOnCooldown(String cooldownKey, long cooldownMs) {
        long now = System.currentTimeMillis();
        synchronized (COOLDOWN_LOCK) {
        Long lastToast = toastCooldowns.get(cooldownKey);
        
        if (lastToast != null && (now - lastToast) < cooldownMs) {
            return true;
        }
        
        toastCooldowns.put(cooldownKey, now);
        return false;
        }
    }
    
    public static void clearCooldowns() {
        synchronized (COOLDOWN_LOCK) {
        toastCooldowns.clear();
        }
    }
    
    public enum AlertType {
        INFO(ChatFormatting.GRAY, "ℹ"),
        WARNING(ChatFormatting.YELLOW, "⚠"),
        DANGER(ChatFormatting.RED, "⛔"),
        SUCCESS(ChatFormatting.GREEN, "✓"),
        BLOCKED(ChatFormatting.GOLD, "🛡");
        
        private final ChatFormatting color;
        private final String icon;
        
        AlertType(ChatFormatting color, String icon) {
            this.color = color;
            this.icon = icon;
        }
        
        public ChatFormatting getColor() { return color; }
        public String getIcon() { return icon; }
    }
    
    public static void alert(AlertType type, String message) {
        if (!OpsecConfig.getInstance().shouldShowAlerts()) return;
        sendMessage(type, message);
    }
    
    public static void toast(AlertType type, String title, String message) {
        if (!OpsecConfig.getInstance().shouldShowToasts()) return;
        try {
            showToast(type, title, message);
        } catch (RuntimeException e) {
            Opsec.LOGGER.error("[OpSec] Exception in toast(): {}", e.getMessage());
        }
    }
    
    /**
     * Show a toast with title only (no description).
     */
    public static void toast(AlertType type, String title) {
        toast(type, title, null);
    }
    
    public static void toastWithCooldown(AlertType type, String title, String message, String cooldownKey, long cooldownMs) {
        if (isToastOnCooldown(cooldownKey, cooldownMs)) return;
        toast(type, title, message);
    }
    
    public static void alertWithToast(AlertType type, String message, String toastTitle) {
        alert(type, message);
        toast(type, toastTitle);
    }
    
    public static void showToast(AlertType type, String title, String message) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            
            if (!client.isSameThread()) {
                client.execute(() -> showToast(type, title, message));
                return;
            }
            
            var toastComponent = client.getToastManager();
            if (toastComponent == null) return;
            
            Component titleComponent = Component.literal(type.getIcon() + " " + title).withStyle(type.getColor());
            Component messageComponent = (message != null && !message.isEmpty()) 
                ? Component.literal(message).withStyle(ChatFormatting.GRAY) 
                : null;
            
            SystemToast.add(toastComponent, SystemToast.SystemToastId.PACK_LOAD_FAILURE, titleComponent, messageComponent);
        } catch (RuntimeException e) {
            Opsec.LOGGER.error("[OpSec] Exception showing toast: {}", e.getMessage());
        }
    }
    
    public static void sendMessage(AlertType type, String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            Opsec.LOGGER.info("[{}] {}", type.name(), message);
            return;
        }
        
        MutableComponent component = Component.literal(type.getIcon() + " ")
                .withStyle(type.getColor())
                .append(Component.literal("[OpSec] ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal(message).withStyle(type.getColor()));
        
        client.player.displayClientMessage(component, false);
    }
    
    /**
     * Send keybind detail message without header prefix (just the keybind info).
     */
    public static void sendKeybindDetail(String detail) {
        if (!OpsecConfig.getInstance().shouldShowAlerts()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        // Simple format: just the keybind detail in red
        MutableComponent component = Component.literal(detail).withStyle(ChatFormatting.RED);
        client.player.displayClientMessage(component, false);
    }
    
    public static void actionBar(AlertType type, String message) {
        if (!OpsecConfig.getInstance().shouldShowAlerts()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        MutableComponent component = Component.literal(type.getIcon() + " ")
                .withStyle(type.getColor())
                .append(Component.literal(message).withStyle(type.getColor()));
        
        client.player.displayClientMessage(component, true);
    }
    
    public static void logDetection(String category, String details) {
        if (!OpsecConfig.getInstance().isLogDetections()) return;
        Opsec.LOGGER.info("[Detection:{}] {}", category, details);
    }
    
    public static void alertTrackPackDetected(String url) {
        logDetection("TrackPack", "Suspicious URL: " + url);
        alert(AlertType.DANGER, "Resource pack fingerprinting detected! URL: " + url);
        toastWithCooldown(AlertType.DANGER, "Resource Pack Fingerprinting Detected", 
            null, "trackpack_detected", OpsecConstants.Timeouts.DEFAULT_TOAST_COOLDOWN_MS);
    }
    
    /**
     * Alert for local port scan detection.
     * Detection always happens, blocking is optional based on protection setting.
     */
    public static void alertLocalPortScanDetected(String url, boolean blocked) {
        String hostPort = extractHostPort(url);
        String action = blocked ? "Blocked" : "Detected (protection OFF)";
        logDetection("LocalPack", action + " local URL probe: " + url);
        
        totalPortScansBlocked.incrementAndGet();
        
        // Limit size of pending port scans
        if (pendingPortScans.size() < OpsecConstants.Limits.MAX_PENDING_PORT_SCANS) {
        pendingPortScans.add(hostPort);
        }
        
        if (!isToastOnCooldown("localpack_alert", OpsecConstants.Timeouts.EXPLOIT_TOAST_COOLDOWN_MS)) {
            alert(AlertType.DANGER, "Port scan " + (blocked ? "blocked" : "detected") + ": " + hostPort);
            toast(AlertType.DANGER, "Local URL Scan Detected");
        }
    }
    
    public static void showPortScanSummary() {
        if (portScanSummaryShown.get() || pendingPortScans.isEmpty()) return;
        
        portScanSummaryShown.set(true);
        int uniquePorts = pendingPortScans.size();
        int total = totalPortScansBlocked.get();
        
        if (uniquePorts == 1) {
            String port = pendingPortScans.iterator().next();
            alert(AlertType.DANGER, "Detected " + total + " local port scan(s) to " + port);
        } else {
            StringBuilder portsStr = new StringBuilder();
            int shown = 0;
            for (String port : pendingPortScans) {
                if (shown > 0) portsStr.append(", ");
                portsStr.append(port);
                if (++shown >= OpsecConstants.Display.MAX_PORTS_TO_SHOW) {
                    if (uniquePorts > OpsecConstants.Display.MAX_PORTS_TO_SHOW) 
                        portsStr.append(" +").append(uniquePorts - OpsecConstants.Display.MAX_PORTS_TO_SHOW).append(" more");
                    break;
                }
            }
            alert(AlertType.DANGER, "Detected " + total + " local port scan(s): " + portsStr);
        }
        
        Opsec.LOGGER.info("[OpSec] Port scan summary: detected {} requests to {} unique targets", total, uniquePorts);
    }
    
    public static void resetPortScanTracking() {
        pendingPortScans.clear();
        totalPortScansBlocked.set(0);
        portScanSummaryShown.set(false);
    }
    
    public static void resetAllState() {
        clearCooldowns();
        resetPortScanTracking();
    }
    
    private static String extractHostPort(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(uri.getScheme()) ? 443 : 80;
            }
            return host + ":" + port;
        } catch (Exception e) {
            String stripped = url.replaceFirst("^https?://", "");
            int slashIdx = stripped.indexOf('/');
            if (slashIdx > 0) stripped = stripped.substring(0, slashIdx);
            return stripped.isEmpty() ? url : stripped;
        }
    }
    
    public enum ExploitSource {
        SIGN("Sign"), ANVIL("Anvil"), UNKNOWN("Unknown source");
        
        private final String displayName;
        ExploitSource(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
    
    public static void alertClientBrandSpoofed(String originalBrand, String spoofedBrand) {
        logDetection("Spoof", "Brand spoofed from '" + originalBrand + "' to '" + spoofedBrand + "'");
    }
    
    public static void alertChannelBlocked(String channel) {
        logDetection("ChannelFilter", "Blocked channel registration: " + channel);
    }
    
    public static void alertChannelSpoofed(String channel, String forBrand) {
        logDetection("ChannelSpoof", "Injecting fake channel '" + channel + "' for brand '" + forBrand + "'");
    }
    
    public static void alertSecureChatRequired(String server) {
        toast(AlertType.WARNING, "Secure Chat Required");
        alert(AlertType.WARNING, "Server requires secure chat. Chat signing enabled.");
        logDetection("SecureChat", "Server enforces secure chat - ON_DEMAND signing activated");
    }
    
    public static void alertCacheIsolationActive(String accountId) {
        logDetection("CacheIsolation", "Resource pack cache isolated for account: " + accountId);
    }
}

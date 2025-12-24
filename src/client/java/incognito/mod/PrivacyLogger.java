package incognito.mod;

import incognito.mod.config.IncognitoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe privacy alert and logging system.
 */
public class PrivacyLogger {
    private static final ConcurrentHashMap<String, Long> toastCooldowns = new ConcurrentHashMap<>();
    private static final long DEFAULT_TOAST_COOLDOWN_MS = 3000L;
    private static final long EXPLOIT_TOAST_COOLDOWN_MS = 5000L;
    
    private static final Set<String> pendingPortScans = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger totalPortScansBlocked = new AtomicInteger(0);
    private static final AtomicBoolean portScanSummaryShown = new AtomicBoolean(false);
    
    private static boolean isToastOnCooldown(String cooldownKey, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastToast = toastCooldowns.get(cooldownKey);
        
        if (lastToast != null && (now - lastToast) < cooldownMs) {
            return true;
        }
        
        toastCooldowns.put(cooldownKey, now);
        return false;
    }
    
    public static void clearCooldowns() {
        toastCooldowns.clear();
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
        if (!IncognitoConfig.getInstance().shouldShowAlerts()) return;
        sendMessage(type, message);
    }
    
    public static void toast(AlertType type, String title, String message) {
        try {
            if (!IncognitoConfig.getInstance().shouldShowToasts()) return;
            showToast(type, title, message);
        } catch (Exception e) {
            Incognito.LOGGER.error("[Incognito] Exception in toast(): {}", e.getMessage());
        }
    }
    
    public static void toastWithCooldown(AlertType type, String title, String message, String cooldownKey, long cooldownMs) {
        if (isToastOnCooldown(cooldownKey, cooldownMs)) return;
        toast(type, title, message);
    }
    
    public static void alertWithToast(AlertType type, String message, String toastTitle) {
        alert(type, message);
        toast(type, toastTitle, message);
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
            Component messageComponent = Component.literal(message).withStyle(ChatFormatting.GRAY);
            
            SystemToast.add(toastComponent, SystemToast.SystemToastId.PACK_LOAD_FAILURE, titleComponent, messageComponent);
        } catch (Exception e) {
            Incognito.LOGGER.error("[Incognito] Exception showing toast: {}", e.getMessage());
        }
    }
    
    public static void sendMessage(AlertType type, String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            Incognito.LOGGER.info("[{}] {}", type.name(), message);
            return;
        }
        
        MutableComponent component = Component.literal(type.getIcon() + " ")
                .withStyle(type.getColor())
                .append(Component.literal("[Incognito] ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal(message).withStyle(type.getColor()));
        
        client.player.displayClientMessage(component, false);
    }
    
    public static void actionBar(AlertType type, String message) {
        if (!IncognitoConfig.getInstance().shouldShowAlerts()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        MutableComponent component = Component.literal(type.getIcon() + " ")
                .withStyle(type.getColor())
                .append(Component.literal(message).withStyle(type.getColor()));
        
        client.player.displayClientMessage(component, true);
    }
    
    public static void logDetection(String category, String details) {
        if (!IncognitoConfig.getInstance().isLogDetections()) return;
        Incognito.LOGGER.info("[Detection:{}] {}", category, details);
    }
    
    public static void alertTrackPackDetected(String url) {
        logDetection("TrackPack", "Suspicious URL: " + url);
        alert(AlertType.DANGER, "Resource pack fingerprinting detected! URL: " + url);
        toastWithCooldown(AlertType.DANGER, "TrackPack Detected", 
            "Resource pack fingerprinting detected!", "trackpack_detected", DEFAULT_TOAST_COOLDOWN_MS);
    }
    
    public static void alertTrackPackBlocked(String url) {
        logDetection("TrackPack", "Fake accepted URL: " + url);
        alert(AlertType.BLOCKED, "Fake accepted resource pack (not loaded): " + url);
        toastWithCooldown(AlertType.BLOCKED, "Pack Blocked", 
            "Resource pack fake-accepted", "trackpack_blocked", DEFAULT_TOAST_COOLDOWN_MS);
    }
    
    public static void alertLocalPackBlocked(String url) {
        String hostPort = extractHostPort(url);
        logDetection("LocalPack", "Spoofed failure for local URL probe: " + url);
        
        totalPortScansBlocked.incrementAndGet();
        pendingPortScans.add(hostPort);
        
        if (!isToastOnCooldown("localpack_alert", EXPLOIT_TOAST_COOLDOWN_MS)) {
            toast(AlertType.BLOCKED, "Port Scan Blocked", "Blocked " + pendingPortScans.size() + " probe(s)");
        }
    }
    
    public static void showPortScanSummary() {
        if (portScanSummaryShown.get() || pendingPortScans.isEmpty()) return;
        
        portScanSummaryShown.set(true);
        int uniquePorts = pendingPortScans.size();
        int total = totalPortScansBlocked.get();
        
        if (uniquePorts == 1) {
            String port = pendingPortScans.iterator().next();
            alert(AlertType.BLOCKED, "Blocked " + total + " local port scan(s) to " + port);
        } else {
            StringBuilder portsStr = new StringBuilder();
            int shown = 0;
            for (String port : pendingPortScans) {
                if (shown > 0) portsStr.append(", ");
                portsStr.append(port);
                if (++shown >= 5) {
                    if (uniquePorts > 5) portsStr.append(" +").append(uniquePorts - 5).append(" more");
                    break;
                }
            }
            alert(AlertType.BLOCKED, "Blocked " + total + " local port scan(s): " + portsStr);
        }
        
        Incognito.LOGGER.info("[Incognito] Port scan summary: blocked {} requests to {} unique targets", total, uniquePorts);
    }
    
    public static void resetPortScanTracking() {
        pendingPortScans.clear();
        totalPortScansBlocked.set(0);
        portScanSummaryShown.set(false);
    }
    
    public static boolean hasPendingPortScans() {
        return !pendingPortScans.isEmpty() && !portScanSummaryShown.get();
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
        SIGN("Sign"), ANVIL("Anvil"), BOOK("Book");
        
        private final String displayName;
        ExploitSource(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
    
    public static void alertTranslationExploitDetected(ExploitSource source, String spoofedContent) {
        String sourceName = source.getDisplayName();
        
        logDetection("TranslationExploit:" + sourceName, 
            "Server attempted exploit via " + sourceName.toLowerCase() + 
            (spoofedContent != null ? " - Spoofed: " + spoofedContent : ""));
        
        String message = "Translation exploit detected via " + sourceName.toLowerCase() + "!";
        if (spoofedContent != null && !spoofedContent.isEmpty()) {
            message += " Spoofed: " + spoofedContent;
        }
        alert(AlertType.DANGER, message);
        
        toastWithCooldown(AlertType.DANGER, sourceName + " Exploit Detected", 
            "Translation exploit detected via " + sourceName.toLowerCase() + "!",
            "exploit_" + sourceName.toLowerCase(), EXPLOIT_TOAST_COOLDOWN_MS);
    }
    
    public static void alertTranslationExploitBlocked(ExploitSource source) {
        String sourceName = source.getDisplayName();
        logDetection("TranslationExploit:" + sourceName, "Sanitized outgoing " + sourceName.toLowerCase() + " data");
        alert(AlertType.BLOCKED, "Blocked translation exploit via " + sourceName.toLowerCase());
        toastWithCooldown(AlertType.BLOCKED, sourceName + " Exploit Blocked", 
            "Translation exploit blocked via " + sourceName.toLowerCase(),
            "exploit_blocked_" + sourceName.toLowerCase(), EXPLOIT_TOAST_COOLDOWN_MS);
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
        toast(AlertType.WARNING, "Secure Chat Required", 
            "Server requires signed chat - signing enabled for this session");
        alert(AlertType.WARNING, "Server " + server + " requires secure chat. Chat signing enabled for this session.");
        logDetection("SecureChat", "Server " + server + " enforces secure chat - ON_DEMAND signing activated");
    }
    
    public static void alertCacheIsolationActive(String accountId) {
        logDetection("CacheIsolation", "Resource pack cache isolated for account: " + accountId);
    }
}

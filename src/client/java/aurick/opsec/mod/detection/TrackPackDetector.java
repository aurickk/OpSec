package aurick.opsec.mod.detection;

import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConstants;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Detects fingerprinting attempts through resource pack requests.
 * Analyzes patterns such as rapid requests, hash probing, and suspicious URLs
 * to identify servers attempting to track or fingerprint clients.
 */
public class TrackPackDetector {
    
    private static final Pattern[] SUSPICIOUS_URL_PATTERNS = {
        Pattern.compile("/[a-f0-9]{32,}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/[A-Za-z0-9+/=]{20,}"),
        Pattern.compile("[?&](track|id|uid|uuid|fingerprint|fp|session|sid)=", Pattern.CASE_INSENSITIVE),
    };
    
    private static final int[] KNOWN_DETECTION_PORTS = {
        15000, 25565, 8080, 3000, 4000, 5000, 8000, 9000, 1337, 7777
    };
    
    // Bounded collections to prevent memory leaks
    private static final Deque<RequestRecord> recentRequests = new ArrayDeque<>();
    private static final Set<String> uniqueHashes = new HashSet<>();
    private static final Object LOCK = new Object();
    
    private static final AtomicLong lastRequestTime = new AtomicLong(0);
    private static final AtomicInteger consecutiveRapidRequests = new AtomicInteger(0);
    private static final AtomicReference<DetectionResult> lastDetectionResult = new AtomicReference<>(null);
    private static final AtomicBoolean notifiedSuspiciousOnce = new AtomicBoolean(false);
    private static final AtomicBoolean notifiedPatternOnce = new AtomicBoolean(false);
    
    public record RequestRecord(String url, String hash, long timestamp, boolean wasSuspicious) {}
    
    public record DetectionResult(DetectionType type, String reason, int severity, String url) {}
    
    public enum DetectionType {
        NONE, LOCALHOST_URL, PRIVATE_IP, LINK_LOCAL, DETECTION_PORT,
        RAPID_REQUESTS, HASH_PROBING, FINGERPRINTING_PATTERN, SUSPICIOUS_URL, PORT_ZERO
    }
    
    public static boolean recordRequest(String url, String hash) {
        long now = System.currentTimeMillis();
        boolean suspicious = isSuspiciousUrl(url);
        
        synchronized (LOCK) {
            // Remove expired entries and enforce size limit
            while (!recentRequests.isEmpty() && 
                   (now - recentRequests.peekFirst().timestamp > OpsecConstants.Detection.DETECTION_WINDOW_MS ||
                    recentRequests.size() >= OpsecConstants.Limits.MAX_RECENT_REQUESTS)) {
                recentRequests.pollFirst();
            }
            
            // Clear hashes if no recent requests
            if (recentRequests.isEmpty()) {
                uniqueHashes.clear();
            }
            
            // Track hash with size limit
            if (hash != null && !hash.isEmpty()) {
                if (uniqueHashes.size() >= OpsecConstants.Limits.MAX_UNIQUE_HASHES) {
                    uniqueHashes.clear();
                }
                uniqueHashes.add(hash);
            }
            
            recentRequests.addLast(new RequestRecord(url, hash, now, suspicious));
        }
        
        long lastTime = lastRequestTime.get();
        if (lastTime > 0 && (now - lastTime) < OpsecConstants.Detection.RAPID_REQUEST_INTERVAL_MS) {
            consecutiveRapidRequests.incrementAndGet();
        } else {
            consecutiveRapidRequests.set(0);
        }
        lastRequestTime.set(now);
        
        analyzePatterns(url);
        return suspicious;
    }
    
    private static void analyzePatterns(String url) {
        if (isRapidRequestPattern()) {
            int rapidCount = consecutiveRapidRequests.get();
            lastDetectionResult.set(new DetectionResult(DetectionType.RAPID_REQUESTS,
                "Rapid sequential requests detected", 4, url));
            PrivacyLogger.logDetection("TrackPack", "Rapid request pattern: " + rapidCount);
        }
        
        if (isHashProbing()) {
            int hashCount;
            synchronized (LOCK) {
                hashCount = uniqueHashes.size();
            }
            lastDetectionResult.set(new DetectionResult(DetectionType.HASH_PROBING,
                "Cache probing with " + hashCount + " unique hashes", 5, url));
            PrivacyLogger.logDetection("TrackPack", "Hash probing: " + hashCount + " hashes");
        }
        
        if (isFingerprinting()) {
            lastDetectionResult.set(new DetectionResult(DetectionType.FINGERPRINTING_PATTERN,
                "Multiple requests - possible fingerprinting", 3, url));
        }
    }
    
    public static boolean isSuspiciousUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        
        if (LocalUrlDetector.isLocalUrl(url)) {
            lastDetectionResult.set(new DetectionResult(DetectionType.LOCALHOST_URL, 
                LocalUrlDetector.getBlockReason(url), 5, url));
            return true;
        }
        
        if (isClientDetectionPort(url)) {
            lastDetectionResult.set(new DetectionResult(DetectionType.DETECTION_PORT, "Known detection port", 5, url));
            return true;
        }
        
        if (url.contains(":0/") || url.endsWith(":0")) {
            lastDetectionResult.set(new DetectionResult(DetectionType.PORT_ZERO, "Invalid port 0", 4, url));
            return true;
        }
        
        int score = getSuspiciousUrlScore(url);
        if (score >= OpsecConstants.Detection.SUSPICIOUS_URL_SCORE_THRESHOLD) {
            lastDetectionResult.set(new DetectionResult(DetectionType.SUSPICIOUS_URL, "Suspicious patterns", 2, url));
        }
        
        return false;
    }
    
    private static int getSuspiciousUrlScore(String url) {
        int score = 0;
        for (Pattern pattern : SUSPICIOUS_URL_PATTERNS) {
            if (pattern.matcher(url).find()) score++;
        }
        
        try {
            URI uri = new URI(url);
            if (uri.getPath() != null && uri.getPath().length() > OpsecConstants.Detection.SUSPICIOUS_PATH_LENGTH) score++;
            if (uri.getQuery() != null && uri.getQuery().split("&").length > OpsecConstants.Detection.SUSPICIOUS_QUERY_PARAM_COUNT) score++;
        } catch (java.net.URISyntaxException e) {
            score++;
        }
        
        return score;
    }
    
    public static boolean isClientDetectionPort(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            int port = uri.getPort();
            
            if (port == -1 || host == null) return false;
            
            if (LocalUrlDetector.isLocalUrl(url)) {
                for (int p : KNOWN_DETECTION_PORTS) {
                    if (port == p) return true;
                }
            }
        } catch (java.net.URISyntaxException e) {
            aurick.opsec.mod.Opsec.LOGGER.debug("[TrackPackDetector] Failed to parse detection port from URL: {}", e.getMessage());
        }
        
        return false;
    }
    
    public static boolean isRapidRequestPattern() {
        if (consecutiveRapidRequests.get() >= OpsecConstants.Detection.RAPID_REQUEST_THRESHOLD) {
            return true;
        }
        
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            long rapidCount = recentRequests.stream()
                .filter(r -> now - r.timestamp < OpsecConstants.Detection.RAPID_WINDOW_MS)
                .count();
            return rapidCount >= OpsecConstants.Detection.RAPID_REQUEST_THRESHOLD;
        }
    }
    
    public static boolean isHashProbing() {
        synchronized (LOCK) {
            if (recentRequests.size() < OpsecConstants.Detection.MIN_REQUESTS_FOR_HASH_ANALYSIS) {
                return false;
            }
        double uniqueRatio = (double) uniqueHashes.size() / recentRequests.size();
        return uniqueHashes.size() >= OpsecConstants.Detection.UNIQUE_HASH_THRESHOLD 
            && uniqueRatio > OpsecConstants.Detection.HASH_PROBING_RATIO_THRESHOLD;
        }
    }
    
    public static boolean isFingerprinting() {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            long count = recentRequests.stream()
                .filter(r -> now - r.timestamp < OpsecConstants.Detection.DETECTION_WINDOW_MS)
                .count();
        return count >= OpsecConstants.Detection.FINGERPRINT_THRESHOLD;
        }
    }

    public static boolean consumeNotifySuspiciousOnce() {
        return notifiedSuspiciousOnce.compareAndSet(false, true);
    }

    public static boolean consumeNotifyPatternOnce() {
        return notifiedPatternOnce.compareAndSet(false, true);
    }
    
    public static void reset() {
        synchronized (LOCK) {
        recentRequests.clear();
            uniqueHashes.clear();
        }
        consecutiveRapidRequests.set(0);
        lastRequestTime.set(0);
        lastDetectionResult.set(null);
        notifiedSuspiciousOnce.set(false);
        notifiedPatternOnce.set(false);
    }
}

package incognito.mod.detection;

import incognito.mod.PrivacyLogger;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Thread-safe TrackPack fingerprinting detector.
 * Uses LocalUrlDetector for local address detection and adds pattern/timing analysis.
 */
public class TrackPackDetector {
    
    private static final long DETECTION_WINDOW_MS = 5000;
    private static final long RAPID_WINDOW_MS = 1000;
    private static final int FINGERPRINT_THRESHOLD = 5;
    private static final int RAPID_REQUEST_THRESHOLD = 3;
    private static final int UNIQUE_HASH_THRESHOLD = 3;
    private static final int SUSPICIOUS_URL_SCORE_THRESHOLD = 2;
    private static final long RAPID_REQUEST_INTERVAL_MS = 200;
    
    private static final Pattern[] SUSPICIOUS_URL_PATTERNS = {
        Pattern.compile("/[a-f0-9]{32,}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/[A-Za-z0-9+/=]{20,}"),
        Pattern.compile("[?&](track|id|uid|uuid|fingerprint|fp|session|sid)=", Pattern.CASE_INSENSITIVE),
    };
    
    private static final int[] KNOWN_DETECTION_PORTS = {
        15000, 25565, 8080, 3000, 4000, 5000, 8000, 9000, 1337, 7777
    };
    
    private static final int SUSPICIOUS_PATH_LENGTH = 100;
    private static final int SUSPICIOUS_QUERY_PARAM_COUNT = 5;
    private static final double HASH_PROBING_RATIO_THRESHOLD = 0.8;
    private static final int MIN_REQUESTS_FOR_HASH_ANALYSIS = 2;
    
    private static final List<RequestRecord> recentRequests = new CopyOnWriteArrayList<>();
    private static final Set<String> uniqueHashes = Collections.synchronizedSet(new HashSet<>());
    private static final AtomicLong lastRequestTime = new AtomicLong(0);
    private static final AtomicInteger consecutiveRapidRequests = new AtomicInteger(0);
    private static final AtomicReference<DetectionResult> lastDetectionResult = new AtomicReference<>(null);
    private static final AtomicBoolean notifiedSuspiciousOnce = new AtomicBoolean(false);
    private static final AtomicBoolean notifiedBlockedOnce = new AtomicBoolean(false);
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
        
        recentRequests.removeIf(r -> now - r.timestamp > DETECTION_WINDOW_MS);
        
        long lastTime = lastRequestTime.get();
        if (lastTime > 0 && (now - lastTime) < RAPID_REQUEST_INTERVAL_MS) {
            consecutiveRapidRequests.incrementAndGet();
        } else {
            consecutiveRapidRequests.set(0);
        }
        lastRequestTime.set(now);
        
        if (hash != null && !hash.isEmpty()) {
            if (recentRequests.isEmpty()) {
                synchronized (uniqueHashes) {
                    uniqueHashes.clear();
                }
            }
            uniqueHashes.add(hash);
        }
        
        recentRequests.add(new RequestRecord(url, hash, now, suspicious));
        analyzePatterns(url);
        return suspicious;
    }
    
    private static void analyzePatterns(String url) {
        if (isRapidRequestPattern()) {
            lastDetectionResult.set(new DetectionResult(DetectionType.RAPID_REQUESTS,
                "Rapid sequential requests detected", 4, url));
            PrivacyLogger.logDetection("TrackPack", "Rapid request pattern: " + consecutiveRapidRequests.get());
        }
        
        if (isHashProbing()) {
            lastDetectionResult.set(new DetectionResult(DetectionType.HASH_PROBING,
                "Cache probing with " + uniqueHashes.size() + " unique hashes", 5, url));
            PrivacyLogger.logDetection("TrackPack", "Hash probing: " + uniqueHashes.size() + " hashes");
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
        if (score >= SUSPICIOUS_URL_SCORE_THRESHOLD) {
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
            if (uri.getPath() != null && uri.getPath().length() > SUSPICIOUS_PATH_LENGTH) score++;
            if (uri.getQuery() != null && uri.getQuery().split("&").length > SUSPICIOUS_QUERY_PARAM_COUNT) score++;
        } catch (Exception e) {
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
        } catch (Exception ignored) {}
        
        return false;
    }
    
    public static boolean isRapidRequestPattern() {
        long now = System.currentTimeMillis();
        long rapidCount = recentRequests.stream().filter(r -> now - r.timestamp < RAPID_WINDOW_MS).count();
        return rapidCount >= RAPID_REQUEST_THRESHOLD || consecutiveRapidRequests.get() >= RAPID_REQUEST_THRESHOLD;
    }
    
    public static boolean isHashProbing() {
        if (recentRequests.size() < MIN_REQUESTS_FOR_HASH_ANALYSIS) return false;
        double uniqueRatio = (double) uniqueHashes.size() / recentRequests.size();
        return uniqueHashes.size() >= UNIQUE_HASH_THRESHOLD && uniqueRatio > HASH_PROBING_RATIO_THRESHOLD;
    }
    
    public static boolean isFingerprinting() {
        long now = System.currentTimeMillis();
        long count = recentRequests.stream().filter(r -> now - r.timestamp < DETECTION_WINDOW_MS).count();
        return count >= FINGERPRINT_THRESHOLD;
    }

    public static boolean consumeNotifySuspiciousOnce() {
        return notifiedSuspiciousOnce.compareAndSet(false, true);
    }

    public static boolean consumeNotifyBlockedOnce() {
        return notifiedBlockedOnce.compareAndSet(false, true);
    }

    public static boolean consumeNotifyPatternOnce() {
        return notifiedPatternOnce.compareAndSet(false, true);
    }
    
    public static DetectionResult getLastDetectionResult() {
        return lastDetectionResult.get();
    }
    
    public static void reset() {
        recentRequests.clear();
        synchronized (uniqueHashes) {
            uniqueHashes.clear();
        }
        consecutiveRapidRequests.set(0);
        lastRequestTime.set(0);
        lastDetectionResult.set(null);
        notifiedSuspiciousOnce.set(false);
        notifiedBlockedOnce.set(false);
        notifiedPatternOnce.set(false);
    }
}

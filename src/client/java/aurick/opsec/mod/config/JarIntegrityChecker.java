package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Async jar integrity checker that compares the running mod jar's SHA-256 hash
 * against the expected digest computed from the matching GitHub release asset.
 * All state fields are volatile for cross-thread visibility since the check runs
 * on a background thread and results are read on the render thread.
 */
public final class JarIntegrityChecker {

    private static final String RELEASES_BASE_URL = "https://api.github.com/repos/aurickk/OpSec/releases/tags/V";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile boolean tamperDetected = false;
    private static volatile boolean checkComplete = false;
    private static volatile boolean shownThisSession = false;
    private static volatile String expectedDigest = null;
    private static volatile String actualDigest = null;

    private JarIntegrityChecker() {
        // Utility class
    }

    /**
     * Fires an async integrity check that computes the local jar's SHA-256 and
     * compares it against the SHA-256 of the matching GitHub release asset.
     * Non-blocking: runs on a daemon thread via CompletableFuture.
     */
    public static void checkIntegrity() {
        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Get jar path from FabricLoader
                Path jarPath = FabricLoader.getInstance()
                        .getModContainer(Opsec.MOD_ID)
                        .flatMap(mod -> mod.getOrigin().getPaths().stream().findFirst())
                        .orElse(null);

                if (jarPath == null || !Files.isRegularFile(jarPath)) {
                    Opsec.LOGGER.debug("[OpSec] Not running from jar, skipping integrity check");
                    return;
                }

                if (!jarPath.toString().endsWith(".jar")) {
                    Opsec.LOGGER.debug("[OpSec] Not running from jar file, skipping integrity check");
                    return;
                }

                // Step 2: Compute local SHA-256
                byte[] jarBytes = Files.readAllBytes(jarPath);
                MessageDigest localDigest = MessageDigest.getInstance("SHA-256");
                actualDigest = bytesToHex(localDigest.digest(jarBytes));

                // Step 3: Get Minecraft version
                String mcVersion = FabricLoader.getInstance()
                        .getModContainer("minecraft")
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                        .orElse(null);

                if (mcVersion == null) {
                    Opsec.LOGGER.debug("[OpSec] Could not determine Minecraft version, skipping integrity check");
                    return;
                }

                // Step 4: Fetch the GitHub release matching the current mod version
                String currentVersion = Opsec.getVersion();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELEASES_BASE_URL + currentVersion))
                        .header("User-Agent", "OpSec-Mod/" + currentVersion)
                        .header("Accept", "application/vnd.github.v3+json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 404) {
                    // No release for this version — dev/unreleased build
                    Opsec.LOGGER.debug("[OpSec] No release found for version {}, skipping integrity check", currentVersion);
                    return;
                }

                if (response.statusCode() != 200) {
                    Opsec.LOGGER.debug("[OpSec] GitHub API returned status {}, skipping integrity check", response.statusCode());
                    return;
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (!json.has("assets")) {
                    Opsec.LOGGER.debug("[OpSec] No assets in release response, skipping integrity check");
                    return;
                }

                JsonArray assets = json.getAsJsonArray("assets");
                JsonObject matchingAsset = findMatchingAsset(assets, mcVersion);

                if (matchingAsset == null) {
                    Opsec.LOGGER.debug("[OpSec] No release asset found for MC version {}, skipping integrity check", mcVersion);
                    return;
                }

                // Step 5: Extract SHA-256 digest from GitHub API response
                // GitHub provides "digest": "sha256:abc123..." for each release asset
                String digestField = matchingAsset.has("digest")
                        ? matchingAsset.get("digest").getAsString()
                        : null;

                if (digestField == null || !digestField.startsWith("sha256:")) {
                    Opsec.LOGGER.debug("[OpSec] No SHA-256 digest for matching asset, skipping integrity check");
                    return;
                }

                expectedDigest = digestField.substring("sha256:".length());

                // Step 6: Compare digests (constant-time comparison)
                if (!MessageDigest.isEqual(expectedDigest.getBytes(), actualDigest.getBytes())) {
                    tamperDetected = true;
                    Opsec.LOGGER.warn("[OpSec] JAR INTEGRITY CHECK FAILED - Expected: {}, Actual: {}", expectedDigest, actualDigest);
                } else {
                    Opsec.LOGGER.debug("[OpSec] Jar integrity verified");
                }
            } catch (Exception e) {
                Opsec.LOGGER.debug("[OpSec] Integrity check failed: {}", e.getMessage());
            } finally {
                checkComplete = true;
            }
        });
    }

    /**
     * Returns true once the integrity check has finished (regardless of result).
     */
    public static boolean isCheckComplete() {
        return checkComplete;
    }

    /**
     * Returns true if tamper was detected, the check is complete, and the
     * warning screen has not been shown this session.
     */
    public static boolean isTamperDetected() {
        return checkComplete && tamperDetected && !shownThisSession
                && !OpsecConfig.getInstance().getSettings().isTamperWarningDismissed();
    }

    /**
     * Marks the tamper warning as shown for this session.
     * Prevents the screen from appearing again even if user navigates back.
     */
    public static void markShown() {
        shownThisSession = true;
    }

    /**
     * Resets the shown flag. Used by the config screen reset button.
     */
    public static void resetShown() {
        shownThisSession = false;
    }

    /**
     * Returns the expected SHA-256 digest from the GitHub release asset, or null if not yet checked.
     */
    public static String getExpectedDigest() {
        return expectedDigest;
    }

    /**
     * Returns the actual SHA-256 digest of the running jar, or null if not yet checked.
     */
    public static String getActualDigest() {
        return actualDigest;
    }

    /**
     * Finds the release asset whose version range covers the given MC version.
     * Asset names follow the pattern: opsec-{range}+v{mod_version}.jar
     * where range is either a single version (e.g., "26.1") or a range (e.g., "1.21.2-1.21.5").
     */
    private static JsonObject findMatchingAsset(JsonArray assets, String mcVersion) {
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.has("name") ? asset.get("name").getAsString() : "";
            if (!name.startsWith("opsec-") || !name.endsWith(".jar")) continue;

            // Extract version range: between "opsec-" and "+v"
            int rangeStart = 6; // "opsec-".length()
            int rangeEnd = name.indexOf("+v");
            if (rangeEnd <= rangeStart) continue;

            String range = name.substring(rangeStart, rangeEnd);
            int dash = range.indexOf('-');

            if (dash == -1) {
                // Single version: exact match
                if (range.equals(mcVersion)) return asset;
            } else {
                // Range: min-max (inclusive)
                String min = range.substring(0, dash);
                String max = range.substring(dash + 1);
                if (compareVersions(mcVersion, min) >= 0 && compareVersions(mcVersion, max) <= 0) {
                    return asset;
                }
            }
        }
        return null;
    }

    /**
     * Compares two dot-separated version strings numerically.
     * Returns negative if a < b, zero if equal, positive if a > b.
     */
    private static int compareVersions(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int len = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            int aNum = i < aParts.length ? Integer.parseInt(aParts[i]) : 0;
            int bNum = i < bParts.length ? Integer.parseInt(bParts[i]) : 0;
            if (aNum != bNum) return Integer.compare(aNum, bNum);
        }
        return 0;
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

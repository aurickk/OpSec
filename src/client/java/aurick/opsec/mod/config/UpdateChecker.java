package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Async update checker that queries the GitHub releases API to detect new versions.
 * All state fields are volatile for cross-thread visibility since the check runs
 * on a background thread and results are read on the render thread.
 */
public final class UpdateChecker {

    private static final String RELEASES_URL = "https://api.github.com/repos/aurickk/OpSec/releases/latest";
    private static final String FALLBACK_RELEASE_URL = "https://github.com/aurickk/OpSec/releases/latest";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile String latestVersion = null;
    private static volatile String releaseUrl = null;
    private static volatile boolean updateAvailable = false;
    private static volatile boolean checkComplete = false;
    private static volatile boolean shownThisSession = false;

    private UpdateChecker() {
        // Utility class
    }

    /**
     * Fires an async HTTP GET to the GitHub releases API to check for updates.
     * Non-blocking: runs on a daemon thread via CompletableFuture.
     */
    public static void checkForUpdate() {
        CompletableFuture.runAsync(() -> {
            try {
                String currentVersion = Opsec.getVersion();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELEASES_URL))
                        .header("User-Agent", "OpSec-Mod/" + currentVersion)
                        .header("Accept", "application/vnd.github.v3+json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                    String tagName = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
                    String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : null;

                    if (tagName != null) {
                        // Strip leading "v" or "V" if present (e.g., "V1.0.5" -> "1.0.5")
                        String version = (tagName.startsWith("v") || tagName.startsWith("V")) ? tagName.substring(1) : tagName;
                        latestVersion = version;
                        releaseUrl = htmlUrl != null ? htmlUrl : FALLBACK_RELEASE_URL;

                        // Any difference means user should update
                        if (!version.equals(currentVersion)) {
                            updateAvailable = true;
                            Opsec.LOGGER.info("[OpSec] Update available: {} -> {} ({})", currentVersion, version, releaseUrl);
                        } else {
                            Opsec.LOGGER.debug("[OpSec] Mod is up to date ({})", currentVersion);
                        }
                    }
                } else {
                    Opsec.LOGGER.debug("[OpSec] GitHub API returned status {}", response.statusCode());
                }
            } catch (Exception e) {
                Opsec.LOGGER.debug("[OpSec] Update check failed: {}", e.getMessage());
            } finally {
                checkComplete = true;
            }
        });
    }

    /**
     * Returns true if an update is available, the check is complete, and the
     * update screen has not been shown this session yet.
     */
    public static boolean isUpdateAvailable() {
        return checkComplete && updateAvailable && !shownThisSession;
    }

    /**
     * Returns the latest version string from GitHub, or null if not yet checked.
     */
    public static String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns the URL to the latest release page on GitHub.
     * Falls back to the generic latest release URL if not available.
     */
    public static String getReleaseUrl() {
        return releaseUrl != null ? releaseUrl : FALLBACK_RELEASE_URL;
    }

    /**
     * Marks the update notification as shown for this session.
     * Prevents the screen from appearing again even if user navigates back to title screen.
     */
    public static void markShown() {
        shownThisSession = true;
    }

    /**
     * Returns the current mod version. Delegates to {@link Opsec#getVersion()}.
     */
    public static String getCurrentVersion() {
        return Opsec.getVersion();
    }
}

package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a Minecraft account authenticated via session (access) token.
 * Handles token validation and account switching with proper chat signature support.
 */
public class SessionAccount {
    
    /**
     * Result of a validation attempt.
     */
    public enum ValidationResult {
        VALID,       // Token is valid
        INVALID,     // Token is expired/invalid (401, 403)
        RATE_LIMITED,// Rate limited by API (429)
        ERROR        // Network/other error
    }
    
    private String accessToken;
    private String username;
    private String uuid;
    private long lastValidated;
    private boolean valid = true; // Assume valid until proven otherwise
    private String lastError = null; // Last error message for display in UI
    
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    public SessionAccount(String accessToken) {
        this.accessToken = accessToken;
        this.username = "";
        this.uuid = "";
        this.lastValidated = 0;
    }
    
    public SessionAccount(String accessToken, String username, String uuid) {
        this.accessToken = accessToken;
        this.username = username;
        this.uuid = uuid;
        this.lastValidated = System.currentTimeMillis();
    }
    
    /**
     * Validates the access token by fetching the profile from Minecraft Services API.
     * Updates username and uuid if successful.
     * @return true if token is valid and profile was fetched
     */
    public boolean fetchInfo() {
        return fetchInfoWithResult() == ValidationResult.VALID;
    }
    
    /**
     * Validates the access token with detailed result.
     * @return ValidationResult indicating success, invalid token, rate limit, or error
     */
    public ValidationResult fetchInfoWithResult() {
        if (accessToken == null || accessToken.isBlank()) {
            return ValidationResult.INVALID;
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PROFILE_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            
            if (statusCode == 429) {
                Opsec.LOGGER.warn("[OpSec] Rate limited while validating token for {}", 
                        username.isEmpty() ? "unknown" : username);
                return ValidationResult.RATE_LIMITED;
            }
            
            if (statusCode == 401 || statusCode == 403) {
                Opsec.LOGGER.warn("[OpSec] Token invalid/expired for {}: HTTP {}", 
                        username.isEmpty() ? "unknown" : username, statusCode);
                return ValidationResult.INVALID;
            }
            
            if (statusCode != 200) {
                Opsec.LOGGER.warn("[OpSec] Failed to validate token: HTTP {}", statusCode);
                return ValidationResult.ERROR;
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            
            if (!json.has("id") || !json.has("name")) {
                Opsec.LOGGER.warn("[OpSec] Invalid profile response: missing id or name");
                return ValidationResult.ERROR;
            }
            
            this.uuid = formatUuid(json.get("id").getAsString());
            this.username = json.get("name").getAsString();
            this.lastValidated = System.currentTimeMillis();
            
            Opsec.LOGGER.info("[OpSec] Validated account: {} ({})", username, uuid);
            return ValidationResult.VALID;
            
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to validate token: {}", e.getMessage());
            return ValidationResult.ERROR;
        }
    }
    
    /**
     * Logs into this account by switching the Minecraft session and reinitializing services.
     * This properly handles chat signatures by regenerating profile keys.
     * Validates the token before switching to ensure it's still valid.
     * @return true if login was successful
     */
    public boolean login() {
        if (accessToken == null || accessToken.isBlank()) {
            this.lastError = "Missing token";
            Opsec.LOGGER.error("[OpSec] Cannot login: missing token");
            return false;
        }
        
        // Validate token before attempting login
        ValidationResult validationResult = fetchInfoWithResult();
        if (validationResult != ValidationResult.VALID) {
            this.valid = (validationResult != ValidationResult.INVALID);
            switch (validationResult) {
                case INVALID -> this.lastError = "Token expired or invalid";
                case RATE_LIMITED -> this.lastError = "Rate limited - try again later";
                case ERROR -> this.lastError = "Network error - check connection";
                default -> this.lastError = "Validation failed";
            }
            Opsec.LOGGER.error("[OpSec] Cannot login: token validation failed ({})", validationResult);
            return false;
        }
        this.valid = true;
        this.lastError = null; // Clear error on success
        
        if (uuid == null || uuid.isBlank()) {
            this.lastError = "Missing UUID after validation";
            Opsec.LOGGER.error("[OpSec] Cannot login: missing UUID after validation");
            return false;
        }
        
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftAccessor accessor = (MinecraftAccessor) mc;
            
            // Create new User (session)
            // User.Type.MSA was removed in 1.21.9
            //? if >=1.21.9 {
            /*User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    accessToken,
                    Optional.empty(),
                    Optional.empty()
            );*/
            //?} else {
            User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    accessToken,
                    Optional.empty(),
                    Optional.empty(),
                    User.Type.MSA
            );
            //?}
            
            // Set the new user/session
            accessor.opsec$setUser(newUser);
            
            // Reinitialize authentication services
            YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(mc.getProxy());
            
            // Reinitialize user API service (needed for chat signing)
            UserApiService userApiService = authService.createUserApiService(accessToken);
            accessor.opsec$setUserApiService(userApiService);
            
            // Reinitialize profile key pair manager (for chat signatures)
            ProfileKeyPairManager profileKeyPairManager = ProfileKeyPairManager.create(
                    userApiService, 
                    newUser, 
                    mc.gameDirectory.toPath()
            );
            accessor.opsec$setProfileKeyPairManager(profileKeyPairManager);
            
            // Reinitialize social manager
            PlayerSocialManager socialManager = new PlayerSocialManager(mc, userApiService);
            accessor.opsec$setPlayerSocialManager(socialManager);
            
            Opsec.LOGGER.info("[OpSec] Successfully logged in as: {}", username);
            return true;
            
        } catch (Exception e) {
            this.lastError = "Login error: " + e.getMessage();
            Opsec.LOGGER.error("[OpSec] Failed to login: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Formats a UUID string from undashed to dashed format.
     */
    private String formatUuid(String undashed) {
        if (undashed == null || undashed.length() != 32) {
            return undashed;
        }
        return String.format("%s-%s-%s-%s-%s",
                undashed.substring(0, 8),
                undashed.substring(8, 12),
                undashed.substring(12, 16),
                undashed.substring(16, 20),
                undashed.substring(20, 32));
    }
    
    // Getters
    public String getAccessToken() { return accessToken; }
    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public long getLastValidated() { return lastValidated; }
    public boolean isValid() { return valid; }
    public String getLastError() { return lastError; }
    
    public void setValid(boolean valid) { this.valid = valid; }
    public void clearError() { this.lastError = null; }
    
    public boolean hasValidInfo() {
        return username != null && !username.isBlank() && uuid != null && !uuid.isBlank();
    }
    
    /**
     * Revalidates the account by checking the token against Minecraft Services.
     * Updates the valid flag based on the result.
     * @return true if account is still valid
     */
    public boolean revalidate() {
        return revalidateWithResult() == ValidationResult.VALID;
    }
    
    /**
     * Revalidates the account with detailed result.
     * Only updates valid flag on definitive results (VALID/INVALID).
     * Rate limited or error results preserve the previous validity state.
     * @return ValidationResult
     */
    public ValidationResult revalidateWithResult() {
        ValidationResult result = fetchInfoWithResult();
        
        // Only update validity on definitive results
        if (result == ValidationResult.VALID) {
            this.valid = true;
        } else if (result == ValidationResult.INVALID) {
            this.valid = false;
        }
        // For RATE_LIMITED and ERROR, keep previous validity state
        
        return result;
    }
    
    // JSON serialization
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("accessToken", accessToken);
        json.addProperty("username", username);
        json.addProperty("uuid", uuid);
        json.addProperty("lastValidated", lastValidated);
        json.addProperty("valid", valid);
        return json;
    }
    
    public static SessionAccount fromJson(JsonObject json) {
        String token = json.has("accessToken") ? json.get("accessToken").getAsString() : "";
        String username = json.has("username") ? json.get("username").getAsString() : "";
        String uuid = json.has("uuid") ? json.get("uuid").getAsString() : "";
        
        SessionAccount account = new SessionAccount(token, username, uuid);
        if (json.has("lastValidated")) {
            account.lastValidated = json.get("lastValidated").getAsLong();
        }
        if (json.has("valid")) {
            account.valid = json.get("valid").getAsBoolean();
        }
        return account;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionAccount other)) return false;
        return uuid != null && uuid.equals(other.uuid);
    }
    
    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}

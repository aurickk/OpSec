package aurick.opsec.mod.accounts;

import com.google.gson.JsonObject;

/**
 * Common interface for Minecraft accounts (both authenticated and offline/cracked).
 */
public interface Account {

    /**
     * Logs into this account by switching the Minecraft session.
     * @return true if login was successful
     */
    boolean login();

    String getUsername();

    String getUuid();

    boolean isValid();

    String getLastError();

    boolean hasValidInfo();

    JsonObject toJson();

    /**
     * Whether this is a cracked/offline account (no authentication).
     */
    boolean isCracked();

    /**
     * Deserializes an Account from JSON, dispatching on the "type" field.
     * Defaults to "session" for backward compatibility with existing files.
     */
    static Account fromJson(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "session";
        return switch (type) {
            case "cracked" -> CrackedAccount.fromJson(json);
            default -> SessionAccount.fromJson(json);
        };
    }
}

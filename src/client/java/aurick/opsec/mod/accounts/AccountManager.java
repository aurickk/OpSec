package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages saved Minecraft accounts for OpSec.
 * Handles persistence to opsec-accounts.json in the config directory.
 */
public class AccountManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ACCOUNTS_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("opsec-accounts.json");
    
    private static volatile AccountManager INSTANCE;
    private static final Object LOCK = new Object();
    
    private final List<SessionAccount> accounts = new ArrayList<>();
    private String activeAccountUuid = null;
    
    // Store the original session info for logout
    private SessionAccount originalAccount = null;

    // Prevent concurrent refresh operations using atomic compare-and-set
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    
    private AccountManager() {
        load();
        captureOriginalAccount();
    }
    
    private void captureOriginalAccount() {
        User user = Minecraft.getInstance().getUser();
        if (user != null) {
            originalAccount = new SessionAccount(
                    user.getAccessToken(),
                    user.getName(),
                    user.getProfileId().toString()
            );
            Opsec.LOGGER.debug("[OpSec] Captured original account: {}", user.getName());
        }
    }
    
    public static AccountManager getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new AccountManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Add a new account to the manager.
     * Will not add duplicates (based on UUID).
     */
    public void add(SessionAccount account) {
        if (account == null || !account.hasValidInfo()) {
            Opsec.LOGGER.warn("[OpSec] Cannot add invalid account");
            return;
        }
        
        // Remove existing account with same UUID
        accounts.removeIf(a -> a.getUuid().equals(account.getUuid()));
        accounts.add(account);
        save();
        
        Opsec.LOGGER.info("[OpSec] Added account: {} ({})", account.getUsername(), account.getUuid());
    }
    
    /**
     * Remove an account from the manager.
     * If the removed account was currently logged in, automatically logs out to original account.
     */
    public void remove(SessionAccount account) {
        if (account == null) return;
        
        boolean wasActive = account.getUuid().equals(activeAccountUuid);
        
        if (accounts.removeIf(a -> a.getUuid().equals(account.getUuid()))) {
            // If the removed account was active, logout to original
            if (wasActive) {
                logout();
            }
            save();
            Opsec.LOGGER.info("[OpSec] Removed account: {} ({})", account.getUsername(), account.getUuid());
        }
    }
    
    /**
     * Remove an account by UUID.
     */
    public void removeByUuid(String uuid) {
        if (uuid == null) return;
        
        Optional<SessionAccount> account = getByUuid(uuid);
        account.ifPresent(this::remove);
    }
    
    /**
     * Get all saved accounts.
     */
    public List<SessionAccount> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }
    
    /**
     * Get account by UUID.
     */
    public Optional<SessionAccount> getByUuid(String uuid) {
        if (uuid == null) return Optional.empty();
        return accounts.stream()
                .filter(a -> uuid.equals(a.getUuid()))
                .findFirst();
    }
    
    /**
     * Get the currently active account UUID.
     */
    public String getActiveAccountUuid() {
        return activeAccountUuid;
    }
    
    /**
     * Set the active account UUID.
     */
    public void setActiveAccountUuid(String uuid) {
        this.activeAccountUuid = uuid;
        save();
    }
    
    /**
     * Check if an account is the currently active one.
     */
    public boolean isActive(SessionAccount account) {
        if (account == null || activeAccountUuid == null) return false;
        return activeAccountUuid.equals(account.getUuid());
    }
    
    /**
     * Get the number of saved accounts.
     */
    public int size() {
        return accounts.size();
    }
    
    /**
     * Check if we're logged into a non-original account.
     */
    public boolean isLoggedIntoAltAccount() {
        return activeAccountUuid != null;
    }
    
    /**
     * Logout to the original account.
     */
    public boolean logout() {
        if (originalAccount == null) {
            Opsec.LOGGER.warn("[OpSec] Cannot logout: no original account captured");
            return false;
        }
        
        if (originalAccount.login()) {
            activeAccountUuid = null;
            save();
            Opsec.LOGGER.info("[OpSec] Logged out to original account: {}", originalAccount.getUsername());
            return true;
        }
        return false;
    }
    
    /**
     * Export accounts to a JSON string.
     */
    public String exportToJson() {
        JsonObject json = new JsonObject();
        JsonArray accountsArray = new JsonArray();
        for (SessionAccount account : accounts) {
            accountsArray.add(account.toJson());
        }
        json.add("accounts", accountsArray);
        return GSON.toJson(json);
    }
    
    /**
     * Import accounts from a JSON string.
     * @return number of accounts imported
     */
    public int importFromJson(String jsonContent) {
        int imported = 0;
        try {
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            if (json.has("accounts")) {
                JsonArray accountsArray = json.getAsJsonArray("accounts");
                for (int i = 0; i < accountsArray.size(); i++) {
                    try {
                        JsonObject accountJson = accountsArray.get(i).getAsJsonObject();
                        SessionAccount account = SessionAccount.fromJson(accountJson);
                        if (account.hasValidInfo()) {
                            // Don't add duplicates
                            boolean exists = accounts.stream()
                                    .anyMatch(a -> a.getUuid().equals(account.getUuid()));
                            if (!exists) {
                                accounts.add(account);
                                imported++;
                            }
                        }
                    } catch (Exception e) {
                        Opsec.LOGGER.warn("[OpSec] Failed to import account at index {}: {}", i, e.getMessage());
                    }
                }
            }
            if (imported > 0) {
                save();
                Opsec.LOGGER.info("[OpSec] Imported {} accounts", imported);
            }
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to parse import JSON: {}", e.getMessage());
        }
        return imported;
    }
    
    /**
     * Check if a refresh operation is in progress.
     */
    public boolean isRefreshing() {
        return isRefreshing.get();
    }
    
    /**
     * Refresh/revalidate all accounts.
     * Accounts with expired or invalid tokens will be marked as invalid.
     * Rate-limited accounts keep their previous validity state.
     * @param callback Called on main thread when complete with (valid, invalid) counts
     */
    public void refreshAllAccounts(java.util.function.BiConsumer<Integer, Integer> callback) {
        // Use atomic compare-and-set to prevent concurrent refresh operations
        if (!isRefreshing.compareAndSet(false, true)) {
            Opsec.LOGGER.warn("[OpSec] Refresh already in progress, ignoring request");
            return;
        }
        
        new Thread(() -> {
            try {
                int valid = 0;
                int invalid = 0;
                int skipped = 0;
                
                for (int i = 0; i < accounts.size(); i++) {
                    SessionAccount account = accounts.get(i);
                    
                    // Add delay between requests to avoid rate limiting (except first)
                    if (i > 0) {
                        try {
                            Thread.sleep(OpsecConstants.Retry.ACCOUNT_REFRESH_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    SessionAccount.ValidationResult result = account.revalidateWithResult();
                    switch (result) {
                        case VALID -> valid++;
                        case INVALID -> invalid++;
                        case RATE_LIMITED, ERROR -> {
                            skipped++;
                            // Keep previous validity state, don't change it
                        }
                    }
                }
                
                save();
                
                final int v = valid;
                final int inv = invalid;
                final int sk = skipped;
                
                if (sk > 0) {
                    Opsec.LOGGER.info("[OpSec] Refreshed accounts: {} valid, {} invalid, {} skipped (rate limited)", v, inv, sk);
                } else {
                    Opsec.LOGGER.info("[OpSec] Refreshed accounts: {} valid, {} invalid", v, inv);
                }
                
                if (callback != null) {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> callback.accept(v, inv));
                }
            } finally {
                isRefreshing.set(false);
            }
        }, "OpSec-Account-Refresh").start();
    }
    
    /**
     * Load accounts from disk.
     */
    public void load() {
        if (!Files.exists(ACCOUNTS_PATH)) {
            Opsec.LOGGER.debug("[OpSec] No accounts file found, starting fresh");
            return;
        }
        
        try {
            String content = Files.readString(ACCOUNTS_PATH);
            if (content == null || content.trim().isEmpty()) {
                return;
            }
            
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            accounts.clear();
            
            if (json.has("accounts")) {
                JsonArray accountsArray = json.getAsJsonArray("accounts");
                for (int i = 0; i < accountsArray.size(); i++) {
                    try {
                        JsonObject accountJson = accountsArray.get(i).getAsJsonObject();
                        SessionAccount account = SessionAccount.fromJson(accountJson);
                        if (account.hasValidInfo()) {
                            accounts.add(account);
                        }
                    } catch (Exception e) {
                        Opsec.LOGGER.warn("[OpSec] Failed to load account at index {}: {}", i, e.getMessage());
                    }
                }
            }
            
            if (json.has("activeAccountUuid")) {
                activeAccountUuid = json.get("activeAccountUuid").getAsString();
                if (activeAccountUuid.isEmpty()) {
                    activeAccountUuid = null;
                }
            }
            
            Opsec.LOGGER.info("[OpSec] Loaded {} accounts", accounts.size());
            
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to load accounts: {}", e.getMessage());
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to parse accounts file: {}", e.getMessage());
        }
    }
    
    /**
     * Save accounts to disk.
     */
    public void save() {
        try {
            JsonObject json = new JsonObject();
            
            JsonArray accountsArray = new JsonArray();
            for (SessionAccount account : accounts) {
                accountsArray.add(account.toJson());
            }
            json.add("accounts", accountsArray);
            
            json.addProperty("activeAccountUuid", activeAccountUuid != null ? activeAccountUuid : "");
            
            // Write atomically using temp file
            Files.createDirectories(ACCOUNTS_PATH.getParent());
            Path tempFile = ACCOUNTS_PATH.resolveSibling(ACCOUNTS_PATH.getFileName() + ".tmp");
            Files.writeString(tempFile, GSON.toJson(json));
            Files.move(tempFile, ACCOUNTS_PATH, 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            
            Opsec.LOGGER.debug("[OpSec] Saved {} accounts", accounts.size());
            
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to save accounts: {}", e.getMessage());
        }
    }
}

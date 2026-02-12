package aurick.opsec.mod.config;

public final class OpsecConstants {
    private OpsecConstants() {}

    /** Client brand identifiers */
    public static final class Brands {
        public static final String VANILLA = "vanilla";
        public static final String FABRIC = "fabric";
        public static final String FORGE = "forge";

        private Brands() {}
    }

    /** Microsoft/Minecraft authentication URLs */
    public static final class AuthUrls {
        public static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
        public static final String MS_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
        public static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
        public static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
        public static final String MC_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

        private AuthUrls() {}
    }

    /** Azure client IDs for Minecraft authentication */
    public static final class AzureClientIds {
        public static final String MINECRAFT_PUBLIC = "00000000402b5328";
        public static final String XBOX_APP = "000000004C12AE6F";
        public static final String MINECRAFT_IOS = "00000000441cc96b";
        public static final String LAUNCHER_CLIENT = "810e1c10-3b3c-4d4f-be0b-f7e9a01e8b98";

        private AzureClientIds() {}
    }

    /** Retry and timing constants */
    public static final class Retry {
        public static final int MAX_RETRIES = 3;
        public static final long BASE_RETRY_DELAY_MS = 2000;
        public static final long RATE_LIMIT_BASE_DELAY_MS = 5000;
        public static final long ACCOUNT_REFRESH_DELAY_MS = 1500;
        public static final long HTTP_TIMEOUT_SECONDS = 10;
        public static final long AUTH_TIMEOUT_SECONDS = 15;

        private Retry() {}
    }

    /** Channel namespaces for filtering */
    public static final class Channels {
        public static final String MINECRAFT = "minecraft";
        public static final String FABRIC_NAMESPACE = "fabric";
        public static final String FORGE_NAMESPACE = "forge";
        public static final String COMMON = "c";
        
        // Minecraft channel paths
        public static final String REGISTER = "register";
        public static final String UNREGISTER = "unregister";
        public static final String MCO = "mco";
        
        // Forge channel paths
        public static final String LOGIN = "login";
        public static final String HANDSHAKE = "handshake";
        
        private Channels() {}
    }
    
    public static final class Timeouts {
        public static final long DEFAULT_TOAST_COOLDOWN_MS = 3000L;
        public static final long EXPLOIT_TOAST_COOLDOWN_MS = 5000L;
        public static final long ALERT_COOLDOWN_MS = 5000L;
        public static final long INTERACTION_TIMEOUT_MS = 500L;
        public static final long BATCH_WINDOW_MS = 100L;
        public static final long LOG_CACHE_CLEAR_INTERVAL_MS = 5000L;
        public static final long KEYBIND_RESCAN_INTERVAL_MS = 30000L;
        
        private Timeouts() {}
    }
    
    public static final class Detection {
        public static final long DETECTION_WINDOW_MS = 5000L;
        public static final long RAPID_WINDOW_MS = 1000L;
        public static final long RAPID_REQUEST_INTERVAL_MS = 200L;
        public static final int FINGERPRINT_THRESHOLD = 5;
        public static final int RAPID_REQUEST_THRESHOLD = 3;
        public static final int UNIQUE_HASH_THRESHOLD = 3;
        public static final int SUSPICIOUS_URL_SCORE_THRESHOLD = 2;
        public static final int SUSPICIOUS_PATH_LENGTH = 100;
        public static final int SUSPICIOUS_QUERY_PARAM_COUNT = 5;
        public static final double HASH_PROBING_RATIO_THRESHOLD = 0.8;
        public static final int MIN_REQUESTS_FOR_HASH_ANALYSIS = 2;
        public static final int MAX_HTTP_REDIRECTS = 20;
        
        private Detection() {}
    }
    
    public static final class Display {
        public static final int MAX_PORTS_TO_SHOW = 5;
        
        private Display() {}
    }
    
    /** Collection size limits to prevent unbounded growth */
    public static final class Limits {
        public static final int MAX_RECENT_REQUESTS = 100;
        public static final int MAX_UNIQUE_HASHES = 50;
        public static final int MAX_TOAST_COOLDOWNS = 50;
        public static final int MAX_PENDING_PORT_SCANS = 100;
        
        private Limits() {}
    }
}


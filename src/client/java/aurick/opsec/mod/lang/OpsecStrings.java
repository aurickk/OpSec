package aurick.opsec.mod.lang;

/**
 * String keys for OpSec UI text. Values are intentionally namespaced like vanilla
 * translation keys but live in a private lookup table (see {@link OpsecLang}) that
 * bypasses the vanilla {@code Language} map. Server resource packs cannot override
 * these strings because they are never exposed to the vanilla resource manager.
 */
public final class OpsecStrings {
    private OpsecStrings() {}

    public static final String CONFIG_TITLE = "opsec.config.title";

    public static final String TAB_IDENTITY = "opsec.tab.identity";
    public static final String TAB_PROTECTION = "opsec.tab.protection";
    public static final String TAB_ACCOUNTS = "opsec.tab.accounts";
    public static final String TAB_WHITELIST = "opsec.tab.whitelist";
    public static final String TAB_MISC = "opsec.tab.misc";

    public static final String OPTION_SPOOF_BRAND = "opsec.option.spoofBrand";
    public static final String OPTION_BRAND_TYPE = "opsec.option.brandType";
    public static final String OPTION_SPOOF_CHANNELS = "opsec.option.spoofChannels";

    public static final String OPTION_WHITELIST_MODE = "opsec.option.whitelistMode";
    public static final String WHITELIST_SEARCH = "opsec.whitelist.search";

    public static final String OPTION_ISOLATE_PACK_CACHE = "opsec.option.isolatePackCache";
    public static final String OPTION_BLOCK_LOCAL_PACK_URLS = "opsec.option.blockLocalPackUrls";
    public static final String OPTION_CLEAR_CACHE = "opsec.option.clearCache";
    public static final String OPTION_KEY_RESOLUTION_SPOOFING = "opsec.option.keyResolutionSpoofing";
    public static final String OPTION_FAKE_DEFAULT_KEYBINDS = "opsec.option.fakeDefaultKeybinds";
    public static final String OPTION_METEOR_FIX = "opsec.option.meteorFix";
    public static final String OPTION_PACK_STRIP_MODE = "opsec.option.packStripMode";

    public static final String PACKSTRIP_MODE_MANUAL = "opsec.packstrip.mode.manual";
    public static final String PACKSTRIP_MODE_ASK = "opsec.packstrip.mode.ask";
    public static final String PACKSTRIP_MODE_ALWAYS_ON = "opsec.packstrip.mode.alwaysOn";
    public static final String PACKSTRIP_MODE_MANUAL_TOOLTIP = "opsec.packstrip.mode.manual.tooltip";
    public static final String PACKSTRIP_MODE_ASK_TOOLTIP = "opsec.packstrip.mode.ask.tooltip";
    public static final String PACKSTRIP_MODE_ALWAYS_ON_TOOLTIP = "opsec.packstrip.mode.alwaysOn.tooltip";

    public static final String PACKSTRIP_TITLE = "opsec.packstrip.title";
    public static final String PACKSTRIP_REQUIRED = "opsec.packstrip.required";
    public static final String PACKSTRIP_OPTIONAL = "opsec.packstrip.optional";
    public static final String PACKSTRIP_STRIPPED = "opsec.packstrip.stripped";
    public static final String PACKSTRIP_CONTINUE = "opsec.packstrip.continue";
    public static final String PACKSTRIP_LOAD_REAL = "opsec.packstrip.loadReal";

    public static final String OPTION_CHAT_SIGNING = "opsec.option.chatSigning";
    public static final String OPTION_DISABLE_TELEMETRY = "opsec.option.disableTelemetry";

    public static final String OPTION_SHOW_ALERTS = "opsec.option.showAlerts";
    public static final String OPTION_SHOW_TOASTS = "opsec.option.showToasts";
    public static final String OPTION_LOG_DETECTIONS = "opsec.option.logDetections";
    public static final String OPTION_DEBUG_ALERTS = "opsec.option.debugAlerts";
    public static final String OPTION_HIDE_INSECURE_INDICATORS = "opsec.option.hideInsecureIndicators";
    public static final String OPTION_HIDE_MODIFIED_INDICATORS = "opsec.option.hideModifiedIndicators";
    public static final String OPTION_HIDE_SYSTEM_MSG_INDICATORS = "opsec.option.hideSystemMsgIndicators";
    public static final String OPTION_HIDE_WARNING_TOAST = "opsec.option.hideWarningToast";

    public static final String BRAND_VANILLA = "opsec.brand.vanilla";
    public static final String BRAND_FABRIC = "opsec.brand.fabric";

    public static final String TRANSLATION_MODE_SPOOF = "opsec.translationMode.spoof";
    public static final String TRANSLATION_MODE_BLOCK = "opsec.translationMode.block";

    public static final String ACCOUNT_ADD_SESSION = "opsec.account.addSession";
    public static final String ACCOUNT_ADD_SESSION_TOOLTIP = "opsec.account.addSession.tooltip";
    public static final String ACCOUNT_ADD_OFFLINE = "opsec.account.addOffline";
    public static final String ACCOUNT_ADD_OFFLINE_TOOLTIP = "opsec.account.addOffline.tooltip";

    public static final String UPDATE_TITLE = "opsec.update.title";
    public static final String UPDATE_MESSAGE = "opsec.update.message";
    public static final String UPDATE_CURRENT = "opsec.update.current";
    public static final String UPDATE_LATEST = "opsec.update.latest";
    public static final String UPDATE_DOWNLOAD = "opsec.update.download";
    public static final String UPDATE_SKIP = "opsec.update.skip";
    public static final String UPDATE_CANCEL = "opsec.update.cancel";

    public static final String TAMPER_TITLE = "opsec.tamper.title";
    public static final String TAMPER_WARNING = "opsec.tamper.warning";
    public static final String TAMPER_MISMATCH = "opsec.tamper.mismatch";
    public static final String TAMPER_MALICIOUS = "opsec.tamper.malicious";
    public static final String TAMPER_COMPROMISED = "opsec.tamper.compromised";
    public static final String TAMPER_ACTION = "opsec.tamper.action";
    public static final String TAMPER_EXPECTED = "opsec.tamper.expected";
    public static final String TAMPER_ACTUAL = "opsec.tamper.actual";
    public static final String TAMPER_DOWNLOAD = "opsec.tamper.download";
    public static final String TAMPER_DISMISS_PERMANENT = "opsec.tamper.dismiss_permanent";
    public static final String TAMPER_DISMISS = "opsec.tamper.dismiss";
}

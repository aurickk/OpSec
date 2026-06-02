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

    public static final String TAB_PROTECTION = "opsec.tab.protection";
    public static final String TAB_ACCOUNTS = "opsec.tab.accounts";
    public static final String TAB_WHITELIST = "opsec.tab.whitelist";
    public static final String TAB_MISC = "opsec.tab.misc";

    public static final String OPTION_SPOOF_AS_VANILLA = "opsec.option.spoofAsVanilla";
    public static final String OPTION_SPOOF_AS_VANILLA_TOOLTIP = "opsec.option.spoofAsVanilla.tooltip";

    public static final String OPTION_WHITELIST_MODE = "opsec.option.whitelistMode";
    public static final String OPTION_WHITELIST_MODE_LOCKED_TOOLTIP = "opsec.option.whitelistMode.locked.tooltip";
    public static final String WHITELIST_SEARCH = "opsec.whitelist.search";

    public static final String WHITELIST_MODE_BLOCK_ALL = "opsec.whitelist.mode.blockAll";
    public static final String WHITELIST_MODE_AUTO = "opsec.whitelist.mode.auto";
    public static final String WHITELIST_MODE_CUSTOM = "opsec.whitelist.mode.custom";
    public static final String WHITELIST_MODE_BLOCK_ALL_TOOLTIP = "opsec.whitelist.mode.blockAll.tooltip";
    public static final String WHITELIST_MODE_AUTO_TOOLTIP = "opsec.whitelist.mode.auto.tooltip";
    public static final String WHITELIST_MODE_CUSTOM_TOOLTIP = "opsec.whitelist.mode.custom.tooltip";

    public static final String EP_MANAGED_HEADER = "opsec.ep.managed.header";
    public static final String EP_MANAGED_TOOLTIP = "opsec.ep.managed.tooltip";

    // Generic "managed by another mod" strings; %s is the managing mod's display name.
    public static final String COMPAT_MANAGED_HEADER = "opsec.compat.managed.header";
    public static final String COMPAT_MANAGED_TOOLTIP = "opsec.compat.managed.tooltip";

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

    // Chat alerts + toast titles (user-visible runtime messages)
    public static final String ALERT_TRACKPACK_PATTERN = "opsec.alert.trackpack.pattern";
    public static final String TOAST_TRACKPACK = "opsec.toast.trackpack";
    public static final String ALERT_PORTSCAN_BLOCKED = "opsec.alert.portscan.blocked";
    public static final String ALERT_PORTSCAN_DETECTED = "opsec.alert.portscan.detected";
    public static final String TOAST_PORTSCAN = "opsec.toast.portscan";
    public static final String ALERT_PORTSCAN_SUMMARY_SINGLE = "opsec.alert.portscan.summary.single";
    public static final String ALERT_PORTSCAN_SUMMARY_MULTI = "opsec.alert.portscan.summary.multi";
    public static final String ALERT_PORTSCAN_SUMMARY_MORE = "opsec.alert.portscan.summary.more";

    // Key-resolution probe alert + toast + one-time hint
    public static final String ALERT_KEYRESOLUTION = "opsec.alert.keyresolution";
    public static final String TOAST_KEYRESOLUTION = "opsec.toast.keyresolution";
    public static final String HINT_ALERTS_CAN_BE_DISABLED = "opsec.hint.alertsCanBeDisabled";

    // /opsec command output
    public static final String COMMAND_HELP_HEADER = "opsec.command.help.header";
    public static final String COMMAND_HELP_INFO = "opsec.command.help.info";
    public static final String COMMAND_HELP_CHANNELS = "opsec.command.help.channels";

    public static final String COMMAND_OVERVIEW_HEADER = "opsec.command.overview.header";
    public static final String COMMAND_OVERVIEW_TOTAL_MODS = "opsec.command.overview.totalMods";
    public static final String COMMAND_OVERVIEW_VANILLA_KEYS = "opsec.command.overview.vanillaKeys";
    public static final String COMMAND_OVERVIEW_SERVER_KEYS = "opsec.command.overview.serverKeys";
    public static final String COMMAND_OVERVIEW_TOTAL_KEYS = "opsec.command.overview.totalKeys";
    public static final String COMMAND_OVERVIEW_TOTAL_KEYBINDS = "opsec.command.overview.totalKeybinds";
    public static final String COMMAND_OVERVIEW_TOTAL_KNOWN_PACKS = "opsec.command.overview.totalKnownPacks";
    public static final String COMMAND_OVERVIEW_MODS_HEADER = "opsec.command.overview.modsHeader";
    public static final String COMMAND_OVERVIEW_NO_MODS = "opsec.command.overview.noMods";
    public static final String COMMAND_OVERVIEW_USE_INFO = "opsec.command.overview.useInfo";

    public static final String COMMAND_INFO_NOT_FOUND = "opsec.command.info.notFound";
    public static final String COMMAND_INFO_USE_LIST = "opsec.command.info.useList";
    public static final String COMMAND_INFO_HEADER = "opsec.command.info.header";
    public static final String COMMAND_INFO_ID = "opsec.command.info.id";
    public static final String COMMAND_INFO_TRANSLATION_KEYS = "opsec.command.info.translationKeys";
    public static final String COMMAND_INFO_NONE = "opsec.command.info.none";
    public static final String COMMAND_INFO_MORE = "opsec.command.info.more";
    public static final String COMMAND_INFO_KEYBINDS = "opsec.command.info.keybinds";
    public static final String COMMAND_INFO_CHANNELS = "opsec.command.info.channels";
    public static final String COMMAND_INFO_KNOWN_PACKS = "opsec.command.info.knownPacks";
    public static final String COMMAND_INFO_JIJ = "opsec.command.info.jij";
    public static final String WHITELIST_REQUIRING_FALLBACK = "opsec.whitelist.requiringFallback";
    public static final String WHITELIST_SUFFIX_REQUIRED = "opsec.whitelist.suffix.required";
    public static final String WHITELIST_TOOLTIP_REQUIRED_BY = "opsec.whitelist.tooltip.requiredBy";
    public static final String COMMAND_INFO_STATUS_ALLOWED_AUTO = "opsec.command.info.statusAllowedAuto";
    public static final String COMMAND_INFO_STATUS_BLOCKED_AUTO = "opsec.command.info.statusBlockedAuto";
    public static final String COMMAND_INFO_STATUS_ALLOWED_CUSTOM = "opsec.command.info.statusAllowedCustom";
    public static final String COMMAND_INFO_STATUS_ALLOWED_DEP = "opsec.command.info.statusAllowedDep";
    public static final String COMMAND_INFO_STATUS_BLOCKED_CUSTOM = "opsec.command.info.statusBlockedCustom";
    public static final String COMMAND_INFO_STATUS_BLOCKED_OFF = "opsec.command.info.statusBlockedOff";

    public static final String COMMAND_CHANNELS_HEADER = "opsec.command.channels.header";
    public static final String COMMAND_CHANNELS_NONE = "opsec.command.channels.none";
    public static final String COMMAND_CHANNELS_TOTAL = "opsec.command.channels.total";
    public static final String COMMAND_CHANNELS_LEGEND = "opsec.command.channels.legend";

    public static final String COMMAND_MODENTRY_KEYS = "opsec.command.modEntry.keys";
    public static final String COMMAND_MODENTRY_KEYBINDS = "opsec.command.modEntry.keybinds";
    public static final String COMMAND_MODENTRY_CHANNELS = "opsec.command.modEntry.channels";
    public static final String COMMAND_MODENTRY_KNOWN_PACKS = "opsec.command.modEntry.knownPacks";

    // AddAccountScreen (session token)
    public static final String ACCOUNT_SCREEN_SESSION_TITLE = "opsec.account.screen.session.title";
    public static final String ACCOUNT_SCREEN_SESSION_LABEL = "opsec.account.screen.sessionLabel";
    public static final String ACCOUNT_SCREEN_SESSION_HINT = "opsec.account.screen.sessionHint";
    public static final String ACCOUNT_SCREEN_REFRESH_LABEL = "opsec.account.screen.refreshLabel";
    public static final String ACCOUNT_SCREEN_REFRESH_HINT = "opsec.account.screen.refreshHint";
    public static final String ACCOUNT_SCREEN_ADD_BUTTON = "opsec.account.screen.addButton";
    public static final String ACCOUNT_SCREEN_CANCEL_BUTTON = "opsec.account.screen.cancelButton";
    public static final String ACCOUNT_ERROR_EMPTY_TOKEN = "opsec.account.error.emptyToken";
    public static final String ACCOUNT_STATUS_VALIDATING = "opsec.account.status.validating";
    public static final String ACCOUNT_SUCCESS_ADDED = "opsec.account.success.added";
    public static final String ACCOUNT_SUCCESS_REFRESH_SUFFIX = "opsec.account.success.refreshSuffix";
    public static final String ACCOUNT_ERROR_INVALID_TOKEN = "opsec.account.error.invalidToken";

    // AddCrackedAccountScreen (offline)
    public static final String ACCOUNT_SCREEN_OFFLINE_TITLE = "opsec.account.screen.offline.title";
    public static final String ACCOUNT_SCREEN_USERNAME_LABEL = "opsec.account.screen.usernameLabel";
    public static final String ACCOUNT_SCREEN_USERNAME_HINT = "opsec.account.screen.usernameHint";
    public static final String ACCOUNT_ERROR_EMPTY_USERNAME = "opsec.account.error.emptyUsername";
    public static final String ACCOUNT_STATUS_ADDING = "opsec.account.status.adding";
    public static final String ACCOUNT_SUCCESS_ADDED_OFFLINE = "opsec.account.success.addedOffline";
    public static final String ACCOUNT_ERROR_FAILED_ADD = "opsec.account.error.failedToAdd";

    // Multiplayer-screen OpSec button
    public static final String BUTTON_SETTINGS_TOOLTIP = "opsec.button.settings.tooltip";

    // Config screen tooltips and section buttons
    public static final String BUTTON_RESET_TOOLTIP = "opsec.button.reset.tooltip";
    public static final String BUTTON_RESET_DISABLED_TOOLTIP = "opsec.button.reset.disabled.tooltip";
    public static final String VERSION_TOOLTIP_OUTDATED = "opsec.version.tooltip.outdated";
    public static final String VERSION_TOOLTIP_UPTODATE = "opsec.version.tooltip.upToDate";

    public static final String TOOLTIP_ISOLATE_PACK_CACHE = "opsec.option.isolatePackCache.tooltip";
    public static final String TOOLTIP_BLOCK_LOCAL_PACK_URLS = "opsec.option.blockLocalPackUrls.tooltip";
    public static final String TOOLTIP_CLEAR_CACHE = "opsec.option.clearCache.tooltip";
    public static final String TOOLTIP_KEY_RESOLUTION_SPOOFING = "opsec.option.keyResolutionSpoofing.tooltip";
    public static final String TOOLTIP_FAKE_DEFAULT_KEYBINDS = "opsec.option.fakeDefaultKeybinds.tooltip";
    public static final String TOOLTIP_METEOR_FIX = "opsec.option.meteorFix.tooltip";
    public static final String TOOLTIP_DISABLE_TELEMETRY = "opsec.option.disableTelemetry.tooltip";
    public static final String TOOLTIP_SHOW_ALERTS = "opsec.option.showAlerts.tooltip";
    public static final String TOOLTIP_SHOW_TOASTS = "opsec.option.showToasts.tooltip";
    public static final String TOOLTIP_LOG_DETECTIONS = "opsec.option.logDetections.tooltip";
    public static final String TOOLTIP_DEBUG_ALERTS = "opsec.option.debugAlerts.tooltip";

    // Account-management buttons
    public static final String BUTTON_REFRESH_ALL = "opsec.button.refreshAll";
    public static final String BUTTON_REFRESHING = "opsec.button.refreshing";
    public static final String BUTTON_REFRESH_TOOLTIP = "opsec.button.refresh.tooltip";
    public static final String BUTTON_REMOVE = "opsec.button.remove";
    public static final String BUTTON_REMOVE_TOOLTIP = "opsec.button.remove.tooltip";
    public static final String BUTTON_IMPORT = "opsec.button.import";
    public static final String BUTTON_IMPORT_TOOLTIP = "opsec.button.import.tooltip";
    public static final String BUTTON_EXPORT = "opsec.button.export";
    public static final String BUTTON_EXPORT_TOOLTIP = "opsec.button.export.tooltip";

    // Whitelist tab bulk-action buttons
    public static final String BUTTON_ENABLE_ALL = "opsec.button.enableAll";
    public static final String BUTTON_ENABLE_ALL_TOOLTIP = "opsec.button.enableAll.tooltip";
    public static final String BUTTON_DISABLE_ALL = "opsec.button.disableAll";
    public static final String BUTTON_DISABLE_ALL_TOOLTIP = "opsec.button.disableAll.tooltip";

    // Chat-signing mode tooltips
    public static final String CHATSIGNING_SIGN_TOOLTIP = "opsec.chatSigning.sign.tooltip";
    public static final String CHATSIGNING_OFF_TOOLTIP = "opsec.chatSigning.off.tooltip";
}

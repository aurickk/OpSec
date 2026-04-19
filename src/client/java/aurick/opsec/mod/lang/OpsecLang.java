package aurick.opsec.mod.lang;

import aurick.opsec.mod.Opsec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Private string lookup for OpSec UI text.
 *
 * <p>Loads JSON from {@code /assets/opsec/opseclang/{locale}.json} directly via
 * classpath. The directory name deliberately avoids {@code lang/} so vanilla's
 * resource manager does not enumerate these files — server resource packs cannot
 * contribute entries because the strings are never registered with
 * {@code Language.getInstance()}.
 *
 * <p>Two maps are maintained: {@code fallback} (always en_us) and {@code current}
 * (the active locale, or empty if no locale-specific file ships). {@link #tr}
 * prefers current, falls back to en_us, then returns the key itself if nothing
 * matches.
 */
public final class OpsecLang {
    private static final String DEFAULT_LOCALE = "en_us";
    private static final String PATH_PREFIX = "/assets/opsec/opseclang/";

    private static final Map<String, String> fallback = loadLocale(DEFAULT_LOCALE);
    private static volatile Map<String, String> current = Collections.emptyMap();
    private static volatile String currentLocale = DEFAULT_LOCALE;

    private OpsecLang() {}

    /**
     * Reload the current locale map based on the filename list vanilla is about
     * to load. Called from {@link aurick.opsec.mod.mixin.client.ClientLanguageMixin}
     * so every user-driven locale change (and every resource-pack reload) re-runs us.
     *
     * @param filenames vanilla's locale chain; the first non-en_us entry is taken
     *                  as the active locale.
     */
    public static void reload(List<String> filenames) {
        String locale = DEFAULT_LOCALE;
        if (filenames != null) {
            for (String name : filenames) {
                if (name != null && !DEFAULT_LOCALE.equals(name)) {
                    locale = name;
                    break;
                }
            }
        }
        if (locale.equals(currentLocale)) return;
        current = loadLocale(locale);
        currentLocale = locale;
        Opsec.LOGGER.debug("[OpSec] OpsecLang reloaded for locale '{}' ({} entries)", locale, current.size());
    }

    /**
     * Resolve a string by key. {@code args} is applied via {@link String#format}
     * when present — matches the {@code %s} style used in the bundled JSON.
     */
    public static String tr(String key, Object... args) {
        String template = current.get(key);
        if (template == null) template = fallback.get(key);
        if (template == null) return key;
        if (args == null || args.length == 0) return template;
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    /**
     * Resolve a string by key and wrap as a literal {@link Component}. Use at UI
     * call sites that need a {@code Component} — keeps text out of the vanilla
     * {@code Language} pipeline so server resource packs cannot override it.
     */
    public static Component component(String key, Object... args) {
        return Component.literal(tr(key, args));
    }

    private static Map<String, String> loadLocale(String locale) {
        String path = PATH_PREFIX + locale + ".json";
        try (InputStream in = OpsecLang.class.getResourceAsStream(path)) {
            if (in == null) return Collections.emptyMap();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> map = new HashMap<>(json.size());
                for (String key : json.keySet()) {
                    map.put(key, json.get(key).getAsString());
                }
                return map;
            }
        } catch (Exception e) {
            Opsec.LOGGER.warn("[OpSec] OpsecLang failed to load {}: {}", path, e.getMessage());
            return Collections.emptyMap();
        }
    }
}

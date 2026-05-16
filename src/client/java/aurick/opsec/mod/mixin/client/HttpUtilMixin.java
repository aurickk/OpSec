package aurick.opsec.mod.mixin.client;

//? if >=1.20.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.util.HttpURLConnectionAccessor;
import aurick.opsec.mod.util.LocalAddressUtil;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import java.util.Objects;

/**
 * Protects against redirect-to-localhost and HTTP 305 proxy redirect attacks.
 * Aligned with ExploitPreventer's HttpUtilMixin implementation.
 *
 * <p>Handles redirect codes 300-303, 305, and 307 with manual redirect following,
 * per-hop local address checking, and OpenSeSame-based Host header injection for 305 responses.
 *
 * @see <a href="https://github.com/NikOverflow/ExploitPreventer">ExploitPreventer</a>
 */
@Mixin(HttpUtil.class)
public class HttpUtilMixin {

    @SuppressWarnings("deprecation")
    @WrapOperation(
        //? if >=1.20.3 {
        method = "downloadFile",
        //?} else {
        /*// 1.20.1 / 1.20.2: HttpUtil.downloadFile doesn't exist yet — the actual HTTP work
        // lives in a private static synthetic method (the CompletableFuture.supplyAsync
        // lambda body of downloadTo). Mojang's official mappings don't cover this synthetic,
        // so Loom falls back to the Yarn intermediary name "method_15303". We list
        // "lambda$downloadTo$0" as a secondary candidate in case a future toolchain rev
        // picks the Java synthetic name instead — the @At INVOKE selector ensures only
        // one of them binds.
        method = {"method_15303", "lambda$downloadTo$0"},
        *///?}
        at = @At(value = "INVOKE", target = "Ljava/net/HttpURLConnection;getInputStream()Ljava/io/InputStream;"),
        require = 1
    )
    private static InputStream opsec$downloadFile(
            HttpURLConnection instance,
            Operation<InputStream> original,
            @Local(argsOnly = true) Proxy proxy,
            @Local(argsOnly = true) Map<String, String> requestProperties,
            @Local LocalRef<HttpURLConnection> httpURLConnection) throws IOException {

        if (!OpsecConfig.getInstance().shouldBlockLocalPackUrls()) return original.call(instance);

        if (!LocalAddressUtil.isLocalAddress(LocalAddressUtil.serverAddress)
                && LocalAddressUtil.isLocalAddress(instance.getURL().getHost())) {
            Opsec.LOGGER.warn("[OpSec] Blocked connection to local address: {}", instance.getURL());
            PrivacyLogger.alertLocalPortScanDetected(instance.getURL().toString(), true);
            throw new IllegalStateException("Tried to connect to local address!");
        }

        instance.setInstanceFollowRedirects(false);

        int redirects = 0;
        String maxRedirectString = System.getProperty("http.maxRedirects") == null
            ? "20" : System.getProperty("http.maxRedirects");
        int maxRedirects = 20;
        try { maxRedirects = Math.max(Integer.parseInt(maxRedirectString), 1); }
        catch (NumberFormatException ignored) {}

        int status = instance.getResponseCode();

        while (instance.getHeaderField("Location") != null
                && ((status > 299 && status < 304) || status == 305 || status == 307)) {
            if (redirects > maxRedirects - 1)
                throw new ProtocolException("Server redirected too many times (" + maxRedirects + ")");

            URL url;
            try {
                url = new URL(instance.getHeaderField("Location"));
                if (!instance.getURL().getProtocol().equalsIgnoreCase(url.getProtocol())) break;
            } catch (MalformedURLException exception) {
                url = new URL(instance.getURL(), instance.getHeaderField("Location"));
            }

            String host = null;
            if (status == 305) {
                url = new URL(instance.getURL().getProtocol(), url.getHost(), url.getPort(),
                    instance.getURL().getFile());
                host = instance.getURL().getHost()
                    + (instance.getURL().getPort() != instance.getURL().getDefaultPort()
                        ? (":" + instance.getURL().getPort()) : "");
            }

            instance = (HttpURLConnection) url.openConnection(proxy);
            instance.setInstanceFollowRedirects(false);
            Objects.requireNonNull(instance);
            requestProperties.forEach(instance::setRequestProperty);

            if (status == 305) {
                HttpURLConnectionAccessor.add(HttpURLConnectionAccessor.getRequests(instance), "Host", host);
            }

            if (!LocalAddressUtil.isLocalAddress(LocalAddressUtil.serverAddress)
                    && LocalAddressUtil.isLocalAddress(instance.getURL().getHost())) {
                Opsec.LOGGER.warn("[OpSec] Blocked connection to local address: {}", instance.getURL());
                PrivacyLogger.alertLocalPortScanDetected(instance.getURL().toString(), true);
                throw new IllegalStateException("Tried to connect to local address!");
            }

            status = instance.getResponseCode();
            redirects++;
        }

        if (redirects > maxRedirects - 1)
            throw new ProtocolException("Server redirected too many times (" + maxRedirects + ")");

        httpURLConnection.set(instance);
        return original.call(instance);
    }
}
//?} else {
/*
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;

// 1.20.1: Block Local URLs disabled (see MC_VERSION_HAS_BLOCK_LOCAL_URLS). Stub.
@Mixin(PacketUtils.class)
public class HttpUtilMixin {
}
*///?}

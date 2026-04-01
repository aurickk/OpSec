package aurick.opsec.mod.util;

import dev.lukebemish.opensesame.annotations.Coerce;
import dev.lukebemish.opensesame.annotations.Open;

/**
 * OpenSeSame accessor for JVM-internal {@code sun.net.www.protocol.http.HttpURLConnection}
 * and {@code sun.net.www.MessageHeader} classes.
 *
 * <p>Used by {@link aurick.opsec.mod.mixin.client.HttpUtilMixin} to inject the Host header
 * on HTTP 305 proxy redirect responses, replacing fragile {@code sun.misc.Unsafe} reflection.</p>
 */
public class HttpURLConnectionAccessor {

    @Open(
        name = "requests",
        targetName = "sun.net.www.protocol.http.HttpURLConnection",
        type = Open.Type.GET_INSTANCE
    )
    public static @Coerce(targetName = "sun.net.www.MessageHeader") Object getRequests(
        @Coerce(targetName = "sun.net.www.protocol.http.HttpURLConnection") Object instance
    ) {
        throw new RuntimeException();
    }

    @Open(
        name = "add",
        targetName = "sun.net.www.MessageHeader",
        type = Open.Type.VIRTUAL
    )
    public static void add(
        @Coerce(targetName = "sun.net.www.MessageHeader") Object instance,
        String key, String value
    ) {
        throw new RuntimeException();
    }
}

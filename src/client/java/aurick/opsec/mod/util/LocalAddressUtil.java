package aurick.opsec.mod.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Local address detection and server address state, aligned with ExploitPreventer's
 * Utils.isLocalAddress() and GlobalState.serverAddress approach.
 *
 * <p>Replaces the previous LocalUrlDetector (regex-based) and ServerAddressTracker
 * (volatile lifecycle) with a simpler DNS-resolution-based approach that catches
 * DNS rebinding attacks.
 *
 * @see <a href="https://github.com/NikOverflow/ExploitPreventer">ExploitPreventer</a>
 */
public class LocalAddressUtil {

    /**
     * Current server address, set on connect, cleared on disconnect.
     * Used to skip local address blocking when already connected to a local server.
     */
    public static String serverAddress = null;

    /**
     * Checks if a hostname resolves to a local/private address.
     * Uses DNS resolution via {@link InetAddress#getAllByName(String)} to resolve ALL addresses
     * for a host (including multi-homed), catching DNS rebinding attacks.
     *
     * <p>Checks four address categories:
     * <ul>
     *   <li>{@link InetAddress#isAnyLocalAddress()} - 0.0.0.0</li>
     *   <li>{@link InetAddress#isLoopbackAddress()} - 127.x.x.x, ::1</li>
     *   <li>{@link InetAddress#isSiteLocalAddress()} - 10.x, 172.16-31.x, 192.168.x</li>
     *   <li>{@link InetAddress#isLinkLocalAddress()} - 169.254.x, fe80::</li>
     * </ul>
     *
     * @param host the hostname to check
     * @return true if any resolved address is local/private
     * @throws UnknownHostException if the hostname cannot be resolved (propagated to caller)
     */
    public static boolean isLocalAddress(String host) throws UnknownHostException {
        if (host == null) return false;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()) {
                return true;
            }
        }
        return false;
    }
}

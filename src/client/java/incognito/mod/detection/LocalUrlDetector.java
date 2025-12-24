package incognito.mod.detection;

import incognito.mod.Incognito;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Detects if a URL points to a local/private IP address.
 * 
 * Servers can abuse resource pack URLs to probe local services on the client's machine.
 * By sending URLs like http://localhost:8080 or http://192.168.1.1, servers can detect
 * what local services are running (routers, TVs, game clients, etc).
 * 
 * See: https://github.com/NikOverflow/ExploitPreventer
 * See: https://alaggydev.github.io/posts/cytooxien/
 */
public class LocalUrlDetector {
    
    // Pattern for private IPv4 ranges
    private static final Pattern PRIVATE_IPV4 = Pattern.compile(
        "^(" +
        "10\\." +                           // 10.0.0.0/8
        "|172\\.(1[6-9]|2[0-9]|3[01])\\." + // 172.16.0.0/12
        "|192\\.168\\." +                   // 192.168.0.0/16
        "|127\\." +                          // 127.0.0.0/8 (loopback)
        "|0\\." +                            // 0.0.0.0/8
        "|169\\.254\\." +                    // 169.254.0.0/16 (link-local)
        "|100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\." + // 100.64.0.0/10 (CGNAT)
        ")"
    );
    
    // Common localhost hostnames
    private static final String[] LOCALHOST_NAMES = {
        "localhost",
        "localhost.localdomain",
        "local",
        "ip6-localhost",
        "ip6-loopback"
    };
    
    /**
     * Check if a URL points to a local/private IP address.
     * 
     * @param url The URL to check
     * @return true if the URL is local/private, false otherwise
     */
    public static boolean isLocalUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            
            if (host == null || host.isEmpty()) {
                return false;
            }
            
            // Check for localhost names
            String hostLower = host.toLowerCase();
            for (String localhost : LOCALHOST_NAMES) {
                if (hostLower.equals(localhost)) {
                    Incognito.LOGGER.debug("[Incognito] Detected localhost hostname: {}", host);
                    return true;
                }
            }
            
            // Check for private IP patterns
            if (PRIVATE_IPV4.matcher(host).find()) {
                Incognito.LOGGER.debug("[Incognito] Detected private IPv4: {}", host);
                return true;
            }
            
            // Check for IPv6 loopback
            if (host.equals("::1") || host.equals("[::1]") || host.startsWith("fe80:")) {
                Incognito.LOGGER.debug("[Incognito] Detected IPv6 loopback/link-local: {}", host);
                return true;
            }
            
            // Resolve hostname to check if it resolves to local IP
            try {
                InetAddress address = InetAddress.getByName(host);
                if (address.isLoopbackAddress() || 
                    address.isSiteLocalAddress() || 
                    address.isLinkLocalAddress() ||
                    address.isAnyLocalAddress()) {
                    Incognito.LOGGER.debug("[Incognito] Hostname {} resolves to local address: {}", 
                        host, address.getHostAddress());
                    return true;
                }
            } catch (UnknownHostException e) {
                // Can't resolve - might be a trap to probe DNS, treat as suspicious
                // But don't block since it could be legitimate
                Incognito.LOGGER.debug("[Incognito] Could not resolve hostname: {}", host);
            }
            
            return false;
            
        } catch (Exception e) {
            Incognito.LOGGER.debug("[Incognito] Error parsing URL {}: {}", url, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a description of why the URL was blocked.
     */
    public static String getBlockReason(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            
            if (host == null) {
                return "invalid URL";
            }
            
            String hostLower = host.toLowerCase();
            for (String localhost : LOCALHOST_NAMES) {
                if (hostLower.equals(localhost)) {
                    return "localhost hostname";
                }
            }
            
            if (host.startsWith("127.")) {
                return "loopback address (127.x.x.x)";
            }
            if (host.startsWith("192.168.")) {
                return "private network (192.168.x.x)";
            }
            if (host.startsWith("10.")) {
                return "private network (10.x.x.x)";
            }
            if (PRIVATE_IPV4.matcher(host).find()) {
                return "private/local IP address";
            }
            if (host.equals("::1") || host.equals("[::1]")) {
                return "IPv6 loopback";
            }
            
            return "local/private address";
            
        } catch (Exception e) {
            return "suspicious URL";
        }
    }
}


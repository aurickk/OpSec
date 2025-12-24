package incognito.mod.protection;

import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.detection.SignExploitDetector;
import net.minecraft.network.chat.Component;

/**
 * Protects against translation vulnerability exploits via signs.
 * This guard is specific to sign-based exploits.
 */
public class SignGuard {
    
    /**
     * Check if a sign editor opening should be blocked
     * @param serverInitiated true if the server sent the packet
     * @return true if the sign editor should be blocked
     */
    public static boolean shouldBlockSignEditor(boolean serverInitiated) {
        if (!IncognitoConfig.getInstance().shouldBlockTranslationExploit()) {
            return false;
        }
        
        // If the player didn't interact with a sign recently, this is suspicious
        if (serverInitiated && !SignExploitDetector.wasPlayerInitiated()) {
            PrivacyLogger.alertTranslationExploitDetected(PrivacyLogger.ExploitSource.SIGN, null);
            return true;
        }
        
        return false;
    }
    
    /**
     * Sanitize sign lines before sending to server
     */
    public static String[] sanitizeSignLines(String[] lines) {
        if (!IncognitoConfig.getInstance().shouldBlockTranslationExploit()) {
            return lines;
        }
        
        if (lines == null) {
            return new String[]{"", "", "", ""};
        }
        
        boolean sanitized = false;
        String[] result = new String[lines.length];
        
        for (int i = 0; i < lines.length; i++) {
            String original = lines[i];
            String clean = SignExploitDetector.sanitizeText(original);
            result[i] = clean;
            
            if (!clean.equals(original)) {
                sanitized = true;
            }
        }
        
        if (sanitized) {
            PrivacyLogger.alertTranslationExploitBlocked(PrivacyLogger.ExploitSource.SIGN);
        }
        
        return result;
    }
    
    /**
     * Sanitize a component before sending
     */
    public static Component sanitizeComponent(Component component) {
        if (!IncognitoConfig.getInstance().shouldBlockTranslationExploit()) {
            return component;
        }
        
        return SignExploitDetector.sanitizeComponent(component);
    }
}


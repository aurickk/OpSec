package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.detection.SignExploitDetector;
import incognito.mod.util.KeybindDefaults;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Protects against sign translation vulnerability exploits.
 * Supports SPOOF mode (vanilla-like values) or BLOCK mode (clear content).
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin {
    
    @Shadow @Final protected SignBlockEntity sign;
    @Shadow @Final protected String[] messages;
    @Shadow @Final protected boolean isFrontText;
    
    @Unique private boolean incognito$wasServerInitiated = true;
    @Unique private boolean incognito$containedSuspiciousContent = false;
    @Unique private java.util.List<String> incognito$spoofedContent;
    
    @Unique private static final int RESULT_RESOLVED = 0;
    @Unique private static final int RESULT_SPOOFED = 1;
    @Unique private static final int RESULT_SOURCE = 2;
    @Unique private static final int MAX_COMPONENT_DEPTH = 16;
    
    @Inject(method = "<init>", at = @At("TAIL"))
    private void incognito$onInit(SignBlockEntity sign, boolean isFrontText, boolean filtered, CallbackInfo ci) {
        boolean protectionEnabled = IncognitoConfig.getInstance().shouldBlockTranslationExploit();
        incognito$wasServerInitiated = !SignExploitDetector.wasPlayerInitiated();
        
        SignText editedText = isFrontText ? sign.getFrontText() : sign.getBackText();
        SignText otherText = isFrontText ? sign.getBackText() : sign.getFrontText();
        
        if (incognito$spoofedContent == null) {
            incognito$spoofedContent = new java.util.ArrayList<>();
        } else {
            incognito$spoofedContent.clear();
        }
        
        boolean shouldSpoof = IncognitoConfig.getInstance().shouldSpoofTranslationExploit();
        
        for (int i = 0; i < 4; i++) {
            String currentMessage = messages[i];
            String[] result = incognito$findExploitInSignLine(editedText, i);
            boolean isFromOtherSide = false;
            
            if (result == null) {
                result = incognito$findExploitInSignLine(otherText, i);
                if (result != null) {
                    isFromOtherSide = true;
                    Incognito.LOGGER.warn("[Incognito] Found exploit in {} side of sign", 
                        isFrontText ? "back" : "front");
                    result[RESULT_SOURCE] = result[RESULT_SOURCE] + " (other side)";
                }
            }
            
            if (result != null) {
                incognito$containedSuspiciousContent = true;
                String resolvedValue = result[RESULT_RESOLVED];
                String spoofedValue = result[RESULT_SPOOFED];
                String sourceArray = result[RESULT_SOURCE];
                
                if (isFromOtherSide) {
                    incognito$spoofedContent.add("Line " + i + " (" + sourceArray + "): detected '" + resolvedValue + "'");
                    continue;
                }
                
                if (protectionEnabled) {
                    String newMessage = incognito$applySpoofOrBlock(currentMessage, resolvedValue, spoofedValue, shouldSpoof);
                    String contentDesc = shouldSpoof 
                        ? "Line " + i + ": '" + currentMessage + "' → '" + newMessage + "'"
                        : "Line " + i + ": cleared";
                    incognito$spoofedContent.add(contentDesc);
                    Incognito.LOGGER.info("[Incognito] {} sign line {}: '{}' → '{}'", 
                        shouldSpoof ? "SPOOF" : "BLOCK", i, currentMessage, newMessage);
                    messages[i] = newMessage;
                } else {
                    incognito$spoofedContent.add("Line " + i + ": detected '" + resolvedValue + "' (NOT protected)");
                }
            }
        }
        
        if (incognito$containedSuspiciousContent) {
            String contentSummary = String.join("; ", incognito$spoofedContent);
            if (incognito$wasServerInitiated) {
                PrivacyLogger.alertTranslationExploitDetected(PrivacyLogger.ExploitSource.SIGN, contentSummary);
            }
        }
    }
    
    @Inject(method = "onDone", at = @At("HEAD"))
    private void incognito$onDone(CallbackInfo ci) {
        boolean protectionEnabled = IncognitoConfig.getInstance().shouldBlockTranslationExploit();
        if (incognito$containedSuspiciousContent) return;
        
        boolean shouldSpoof = IncognitoConfig.getInstance().shouldSpoofTranslationExploit();
        boolean detected = false;
        
        for (int i = 0; i < messages.length; i++) {
            String original = messages[i];
            String processed = shouldSpoof 
                ? SignExploitDetector.spoofText(original) 
                : SignExploitDetector.sanitizeText(original);
            
            if (!processed.equals(original)) {
                detected = true;
                if (protectionEnabled) {
                    messages[i] = processed;
                }
            }
        }
        
        if (detected) {
            if (protectionEnabled) {
                if (shouldSpoof) {
                    PrivacyLogger.alertTranslationExploitDetected(PrivacyLogger.ExploitSource.SIGN, "content spoofed");
                } else {
                    PrivacyLogger.alertTranslationExploitBlocked(PrivacyLogger.ExploitSource.SIGN);
                }
            } else {
                PrivacyLogger.alertTranslationExploitDetected(PrivacyLogger.ExploitSource.SIGN, "detected (protection disabled)");
            }
        }
    }
    
    @Unique
    private String[] incognito$findExploitInSignLine(SignText text, int lineIndex) {
        Component unfilteredComponent = text.getMessage(lineIndex, false);
        String spoofedString = incognito$buildSpoofedString(unfilteredComponent, 0);
        if (spoofedString != null) {
            return new String[] { unfilteredComponent.getString(), spoofedString, "messages" };
        }
        
        Component filteredComponent = text.getMessage(lineIndex, true);
        spoofedString = incognito$buildSpoofedString(filteredComponent, 0);
        if (spoofedString != null) {
            return new String[] { filteredComponent.getString(), spoofedString, "filtered_messages" };
        }
        
        return null;
    }
    
    @Unique
    private String incognito$buildSpoofedString(Component component, int depth) {
        if (component == null || depth > MAX_COMPONENT_DEPTH) {
            return depth > MAX_COMPONENT_DEPTH ? "" : null;
        }
        
        StringBuilder spoofedBuilder = new StringBuilder();
        boolean foundExploit = false;
        var contents = component.getContents();
        
        if (contents instanceof KeybindContents keybind) {
            spoofedBuilder.append(incognito$getKeybindSpoofValue(keybind.getName()));
            foundExploit = true;
        } else if (contents instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            
            if (key.startsWith("key.")) {
                spoofedBuilder.append(incognito$getKeybindSpoofValue(key));
                foundExploit = true;
            } else if (incognito$isSuspiciousTranslationKey(key)) {
                spoofedBuilder.append(incognito$getTranslationSpoofValue(key));
                foundExploit = true;
            } else {
                spoofedBuilder.append(component.getString());
            }
            
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent) {
                    String argSpoofed = incognito$buildSpoofedString(argComponent, depth + 1);
                    if (argSpoofed != null) {
                        spoofedBuilder.append(argSpoofed);
                        foundExploit = true;
                    }
                }
            }
        } else {
            if (contents instanceof PlainTextContents.LiteralContents literal) {
                spoofedBuilder.append(literal.text());
            }
            
            String contentTypeName = contents.getClass().getSimpleName();
            if (contentTypeName.contains("Nbt") || contentTypeName.contains("Score") || contentTypeName.contains("Selector")) {
                foundExploit = true;
            }
        }
        
        for (Component sibling : component.getSiblings()) {
            String siblingResult = incognito$buildSpoofedString(sibling, depth + 1);
            if (siblingResult != null) {
                spoofedBuilder.append(siblingResult);
                foundExploit = true;
            } else {
                var siblingContents = sibling.getContents();
                if (siblingContents instanceof PlainTextContents.LiteralContents literal) {
                    spoofedBuilder.append(literal.text());
                }
            }
        }
        
        return foundExploit ? spoofedBuilder.toString() : null;
    }
    
    @Unique
    private static String incognito$applySpoofOrBlock(String currentMessage, String resolvedValue, 
                                                       String spoofedValue, boolean shouldSpoof) {
        if (!shouldSpoof) return "";
        
        if (resolvedValue != null && !resolvedValue.isEmpty() && currentMessage.contains(resolvedValue)) {
            return currentMessage.replace(resolvedValue, spoofedValue);
        } else if (currentMessage.isEmpty() && resolvedValue != null && !resolvedValue.isEmpty()) {
            return spoofedValue;
        }
        return spoofedValue;
    }
    
    @Unique
    private static String[] incognito$getSpoofResultWithDepth(Component component, int depth) {
        if (component == null) return null;
        
        if (depth > MAX_COMPONENT_DEPTH) {
            Incognito.LOGGER.warn("[Incognito] Component depth limit exceeded");
            return new String[] { component.getString(), "" };
        }
        
        var contents = component.getContents();
        
        if (contents instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            String resolvedValue = component.getString();
            
            if (key.startsWith("key.")) {
                return new String[] { resolvedValue, incognito$getKeybindSpoofValue(key) };
            }
            
            if (incognito$isSuspiciousTranslationKey(key)) {
                return new String[] { resolvedValue, incognito$getTranslationSpoofValue(key) };
            }
            
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent) {
                    String[] argResult = incognito$getSpoofResultWithDepth(argComponent, depth + 1);
                    if (argResult != null) return argResult;
                }
            }
        }
        
        if (contents instanceof KeybindContents keybind) {
            String resolvedValue = component.getString();
            return new String[] { resolvedValue, incognito$getKeybindSpoofValue(keybind.getName()) };
        }
        
        String contentTypeName = contents.getClass().getSimpleName();
        if (contentTypeName.contains("Nbt") || contentTypeName.contains("Score") || contentTypeName.contains("Selector")) {
            return new String[] { component.getString(), "" };
        }
        
        for (Component sibling : component.getSiblings()) {
            String[] siblingResult = incognito$getSpoofResultWithDepth(sibling, depth + 1);
            if (siblingResult != null) return siblingResult;
        }
        
        return null;
    }
    
    @Unique
    private static String incognito$getKeybindSpoofValue(String keybindKey) {
        if (KeybindDefaults.hasDefault(keybindKey)) {
            return KeybindDefaults.getDefault(keybindKey);
        }
        return keybindKey;
    }
    
    @Unique
    private static String incognito$getTranslationSpoofValue(String key) {
        return switch (key) {
            case "language.name" -> "English";
            case "language.region" -> "United States";
            case "language.code" -> "en_us";
            default -> key;
        };
    }
    
    @Unique
    private static boolean incognito$isSuspiciousTranslationKey(String key) {
        if (key == null) return false;
        if (key.startsWith("language.") || key.startsWith("options.") || key.startsWith("category.")) {
            return true;
        }
        return incognito$isModTranslationKey(key);
    }
    
    @Unique
    private static boolean incognito$isModTranslationKey(String key) {
        String[] vanillaPrefixes = {
            "block.minecraft.", "item.minecraft.", "entity.minecraft.", 
            "biome.minecraft.", "effect.minecraft.", "enchantment.minecraft.",
            "stat.minecraft.", "advancement.minecraft.", "container.",
            "commands.", "chat.", "death.", "deathScreen.", "title.", "menu.",
            "narrator.", "gui.", "multiplayer.", "selectWorld.", "createWorld.",
            "generator.", "gameMode.", "difficulty.", "spectatorMenu.",
            "resourcePack.", "lanServer.", "controls.", "soundCategory.",
            "potion.", "record.", "subtitles.", "tutorial.", "book.",
            "disconnect.", "connect.", "attribute.", "structure_block.",
            "jigsaw_block.", "sign.", "filled_map.", "gamerule.", "argument.",
            "parsing.", "pack.", "accessibility.", "telemetry.", "realms.", "mco.",
            "key.", "options.", "language.", "category."
        };
        
        for (String prefix : vanillaPrefixes) {
            if (key.startsWith(prefix)) return false;
        }
        
        return key.contains(".");
    }
}

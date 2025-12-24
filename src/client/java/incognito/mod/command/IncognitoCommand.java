package incognito.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.config.SpoofSettings;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Client-side commands for Incognito mod.
 */
public class IncognitoCommand {
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            ClientCommandManager.literal("incognito")
                .executes(IncognitoCommand::showStatus)
                .then(ClientCommandManager.literal("status")
                    .executes(IncognitoCommand::showStatus))
                .then(ClientCommandManager.literal("brand")
                    .then(ClientCommandManager.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("vanilla");
                            builder.suggest("fabric");
                            return builder.buildFuture();
                        })
                        .executes(IncognitoCommand::setBrand)))
                .then(ClientCommandManager.literal("toggle")
                    .then(ClientCommandManager.literal("brand")
                        .executes(ctx -> toggle(ctx, "brand")))
                    .then(ClientCommandManager.literal("packcache")
                        .executes(ctx -> toggle(ctx, "packcache")))
                    .then(ClientCommandManager.literal("translationexploit")
                        .executes(ctx -> toggle(ctx, "translationexploit")))
                    // Legacy alias for backwards compatibility
                    .then(ClientCommandManager.literal("signexploit")
                        .executes(ctx -> toggle(ctx, "translationexploit")))
                    .then(ClientCommandManager.literal("alerts")
                        .executes(ctx -> toggle(ctx, "alerts"))))
                .then(ClientCommandManager.literal("cache")
                    .then(ClientCommandManager.literal("clear")
                        .executes(IncognitoCommand::clearCache)))
                .then(ClientCommandManager.literal("reload")
                    .executes(IncognitoCommand::reloadConfig))
        );
    }
    
    private static int showStatus(CommandContext<FabricClientCommandSource> context) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        FabricClientCommandSource source = context.getSource();
        
        source.sendFeedback(Component.literal("§f§l=== Incognito Privacy Status ==="));
        
        String currentServer = config.getCurrentServer();
        if (currentServer != null) {
            source.sendFeedback(Component.literal("§7Connected to: §f" + currentServer));
        } else {
            source.sendFeedback(Component.literal("§7Not connected to a server"));
        }
        
        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal("§f§lBrand Spoofing:"));
        source.sendFeedback(Component.literal("§7  Enabled: " + 
            (settings.isSpoofBrand() ? "§aON" : "§cOFF")));
        source.sendFeedback(Component.literal("§7  Mode: §f" + settings.getCustomBrand().toUpperCase()));
        
        if (settings.isVanillaMode()) {
            source.sendFeedback(Component.literal("§7  → §eBLOCKS ALL CHANNELS"));
        } else if (settings.isFabricMode()) {
            source.sendFeedback(Component.literal("§7  → §eAllows Fabric, blocks mods"));
        }
        
        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal("§f§lProtection:"));
        source.sendFeedback(Component.literal("§7  Isolate Pack Cache: " + 
            (settings.isIsolatePackCache() ? "§aON" : "§cOFF")));
        source.sendFeedback(Component.literal("§7  Translation Exploit Protection: " + 
            (settings.isBlockTranslationExploit() ? "§aON" : "§cOFF") + 
            " §7(signs/anvils)"));

        source.sendFeedback(Component.literal("§7  Spoof Channel IDs: " +
            (settings.isSpoofChannels() ? "§aON" : "§cOFF")));
        
        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal("§f§lAdvanced:"));
        source.sendFeedback(Component.literal("§7  Show Alerts: " + 
            (settings.isShowAlerts() ? "§aON" : "§cOFF")));
        
        return 1;
    }
    
    private static int setBrand(CommandContext<FabricClientCommandSource> context) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        String brand = StringArgumentType.getString(context, "value");
        
        settings.setCustomBrand(brand);
        settings.setSpoofBrand(true);
        config.save();
        
        PrivacyLogger.sendMessage(PrivacyLogger.AlertType.SUCCESS, 
            "Brand set to: " + settings.getCustomBrand());
        
        return 1;
    }
    
    private static int toggle(CommandContext<FabricClientCommandSource> context, String feature) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        
        record ToggleResult(boolean newState, String featureName) {}
        
        ToggleResult result = switch (feature) {
            case "brand" -> {
                boolean state = !settings.isSpoofBrand();
                settings.setSpoofBrand(state);
                yield new ToggleResult(state, "Brand Spoofing");
            }
            case "packcache" -> {
                boolean state = !settings.isIsolatePackCache();
                settings.setIsolatePackCache(state);
                yield new ToggleResult(state, "Pack Cache Isolation");
            }
            case "translationexploit" -> {
                boolean state = !settings.isBlockTranslationExploit();
                settings.setBlockTranslationExploit(state);
                yield new ToggleResult(state, "Translation Exploit Protection");
            }
            case "alerts" -> {
                boolean state = !settings.isShowAlerts();
                settings.setShowAlerts(state);
                yield new ToggleResult(state, "Alerts");
            }
            default -> {
                PrivacyLogger.sendMessage(PrivacyLogger.AlertType.WARNING, "Unknown feature: " + feature);
                yield null;
            }
        };
        
        if (result == null) {
            return 0;
        }
        
        config.save();
        PrivacyLogger.sendMessage(PrivacyLogger.AlertType.SUCCESS, 
            result.featureName() + " " + (result.newState() ? "enabled" : "disabled"));
        
        return 1;
    }
    
    private static int clearCache(CommandContext<FabricClientCommandSource> context) {
        try {
            var gameDir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath().normalize();
            var downloadsPath = gameDir.resolve("downloads").normalize();
            
            if (!downloadsPath.startsWith(gameDir)) {
                PrivacyLogger.sendMessage(PrivacyLogger.AlertType.DANGER, "Security error: invalid cache path");
                return 0;
            }
            
            if (Files.exists(downloadsPath)) {
                try (var walk = Files.walk(downloadsPath)) {
                    walk.filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                incognito.mod.Incognito.LOGGER.warn("[Incognito] Failed to delete: {}", path.getFileName());
                            }
                        });
                }
                PrivacyLogger.sendMessage(PrivacyLogger.AlertType.SUCCESS, "Resource pack cache cleared");
            } else {
                PrivacyLogger.sendMessage(PrivacyLogger.AlertType.INFO, "No resource pack cache to clear");
            }
        } catch (IOException e) {
            PrivacyLogger.sendMessage(PrivacyLogger.AlertType.WARNING, "Failed to clear resource pack cache");
        }
        return 1;
    }
    
    private static int reloadConfig(CommandContext<FabricClientCommandSource> context) {
        IncognitoConfig.getInstance().load();
        PrivacyLogger.sendMessage(PrivacyLogger.AlertType.SUCCESS, "Configuration reloaded");
        return 1;
    }
}

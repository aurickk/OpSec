package aurick.opsec.mod.command;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.tracking.ModRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*/
//?} else {
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
//?}
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Debug command for OpSec mod.
 * Usage: /opsec info [mod name]
 */
@SuppressWarnings("null")
public class OpsecCommand {
    
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(OpsecCommand::registerCommands);
    }
    
    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, 
                                          CommandBuildContext context) {
        dispatcher.register(
            //? if >=26.1 {
            /*ClientCommands.literal("opsec")*/
            //?} else {
            ClientCommandManager.literal("opsec")
            //?}
                .executes(OpsecCommand::showHelp)
                .then(
                    //? if >=26.1 {
                    /*ClientCommands.literal("info")*/
                    //?} else {
                    ClientCommandManager.literal("info")
                    //?}
                    .then(
                        //? if >=26.1 {
                        /*ClientCommands.argument("modName", StringArgumentType.greedyString())*/
                        //?} else {
                        ClientCommandManager.argument("modName", StringArgumentType.greedyString())
                        //?}
                        .suggests(OpsecCommand::suggestModNames)
                        .executes(ctx -> showModInfo(ctx, StringArgumentType.getString(ctx, "modName"))))
                    .executes(ctx -> showOverview(ctx)))
                .then(
                    //? if >=26.1 {
                    /*ClientCommands.literal("channels")*/
                    //?} else {
                    ClientCommandManager.literal("channels")
                    //?}
                    .executes(OpsecCommand::showAllChannels))
        );
    }
    
    /**
     * Show help message.
     */
    private static int showHelp(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_HELP_HEADER)));
        source.sendFeedback(Component.empty());
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_HELP_INFO)));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_HELP_CHANNELS)));
        
        return 1;
    }
    
    /**
     * Suggest mod names that have trackable content.
     */
    private static CompletableFuture<Suggestions> suggestModNames(
            CommandContext<FabricClientCommandSource> context, 
            SuggestionsBuilder builder) {
        
        Set<String> modNames = new LinkedHashSet<>();
        
        for (ModRegistry.ModInfo info : ModRegistry.getAllMods()) {
            if (info.hasTrackableContent()) {
                // Only add mod ID (unique identifier)
                modNames.add(info.getModId());
            }
        }
        
        return SharedSuggestionProvider.suggest(modNames, builder);
    }
    
    /**
     * Show overview of all tracked mods.
     */
    private static int showOverview(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_HEADER)));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_MODS, ModRegistry.getAllMods().size())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_VANILLA_KEYS, ModRegistry.getVanillaKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_SERVER_KEYS, ModRegistry.getServerPackKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KEYS, ModRegistry.getTranslationKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KEYBINDS, ModRegistry.getKeybindCount())));

        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_MODS_HEADER)));

        int count = 0;
        for (ModRegistry.ModInfo info : ModRegistry.getAllMods()) {
            if (info.hasTrackableContent()) {
                source.sendFeedback(modEntry(info));
                count++;
            }
        }

        if (count == 0) {
            source.sendFeedback(warning(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_NO_MODS)));
        }

        source.sendFeedback(Component.empty());
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_USE_INFO)));
        
        return 1;
    }
    
    /**
     * Show detailed info for a specific mod.
     */
    private static int showModInfo(CommandContext<FabricClientCommandSource> ctx, String modName) {
        FabricClientCommandSource source = ctx.getSource();
        
        // Find the mod by ID or display name
        ModRegistry.ModInfo info = findMod(modName);
        
        if (info == null) {
            source.sendFeedback(error(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NOT_FOUND, modName)));
            source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_INFO_USE_LIST)));
            return 0;
        }

        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_INFO_HEADER, info.getDisplayName())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_INFO_ID, info.getModId())));

        // Translation keys
        Set<String> keys = info.getTranslationKeys();
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_INFO_TRANSLATION_KEYS, keys.size())));
        if (keys.isEmpty()) {
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
        } else {
            int shown = 0;
            for (String key : keys) {
                if (shown >= 20) {
                    source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_MORE, keys.size() - shown)));
                    break;
                }
                source.sendFeedback(listItem(key));
                shown++;
            }
        }

        // Keybinds
        Set<String> keybinds = info.getKeybinds();
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_INFO_KEYBINDS, keybinds.size())));
        if (keybinds.isEmpty()) {
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
        } else {
            for (String keybind : keybinds) {
                source.sendFeedback(listItem(keybind));
            }
        }

        // Channels
        //? if >=1.21.11 {
        /*Set<Identifier> channels = info.getChannels();*/
        //?} else {
        Set<ResourceLocation> channels = info.getChannels();
        //?}
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_INFO_CHANNELS, channels.size())));
        if (channels.isEmpty()) {
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
        } else {
            //? if >=1.21.11 {
            /*for (Identifier channel : channels) {*/
            //?} else {
            for (ResourceLocation channel : channels) {
            //?}
                source.sendFeedback(listItem(channel.toString()));
            }
        }
        
        // Whitelist status
        source.sendFeedback(Component.empty());
        OpsecConfig opsecConfig = OpsecConfig.getInstance();
        SpoofSettings opsecSettings = opsecConfig.getSettings();
        SpoofSettings.WhitelistMode whitelistMode = opsecSettings.getWhitelistMode();

        String statusText;
        boolean isAllowed;

        // Check if this is a default Fabric API mod (always allowed in Fabric mode)
        if (opsecSettings.isFabricMode() && ModRegistry.DEFAULT_FABRIC_MODS.contains(info.getModId())) {
            isAllowed = true;
            statusText = OpsecLang.tr(OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_FABRIC);
        } else {
            switch (whitelistMode) {
                case AUTO:
                    boolean hasChannels = info.hasChannels();
                    isAllowed = hasChannels;
                    statusText = OpsecLang.tr(isAllowed
                        ? OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_AUTO
                        : OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_AUTO);
                    break;
                case CUSTOM:
                    isAllowed = opsecSettings.isModWhitelisted(info.getModId());
                    statusText = OpsecLang.tr(isAllowed
                        ? OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_CUSTOM
                        : OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_CUSTOM);
                    break;
                default: // OFF
                    isAllowed = false;
                    statusText = OpsecLang.tr(OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_OFF);
                    break;
            }
        }
        source.sendFeedback(isAllowed ? success(statusText) : warning(statusText));
        
        return 1;
    }
    
    
    /**
     * Show all tracked channels.
     */
    private static int showAllChannels(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_HEADER)));
        
        int totalChannels = 0;
        for (ModRegistry.ModInfo info : ModRegistry.getAllMods()) {
            //? if >=1.21.11 {
            /*Set<Identifier> channels = info.getChannels();*/
            //?} else {
            Set<ResourceLocation> channels = info.getChannels();
            //?}
            if (!channels.isEmpty()) {
                source.sendFeedback(subheader(info.getDisplayName() + " (" + info.getModId() + "):"));
                //? if >=1.21.11 {
                /*for (Identifier channel : channels) {*/
                //?} else {
                for (ResourceLocation channel : channels) {
                //?}
                    boolean whitelisted = ModRegistry.isWhitelistedChannel(channel);
                    if (whitelisted) {
                        source.sendFeedback(Component.literal("  ✓ " + channel.toString())
                            .withStyle(ChatFormatting.GREEN));
                    } else {
                        source.sendFeedback(Component.literal("  ✗ " + channel.toString())
                            .withStyle(ChatFormatting.RED));
                    }
                    totalChannels++;
                }
            }
        }
        
        if (totalChannels == 0) {
            source.sendFeedback(warning(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_NONE)));
        } else {
            source.sendFeedback(Component.empty());
            source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_TOTAL, totalChannels)));
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_LEGEND)));
        }
        
        return 1;
    }
    
    
    /**
     * Find a mod by ID or display name.
     */
    private static ModRegistry.ModInfo findMod(String query) {
        if (query == null || query.isEmpty()) return null;
        
        String queryLower = query.toLowerCase();
        
        // First try exact ID match
        ModRegistry.ModInfo info = ModRegistry.getModInfo(query);
        if (info != null) return info;
        
        // Then try case-insensitive ID or display name match
        for (ModRegistry.ModInfo mod : ModRegistry.getAllMods()) {
            if (mod.getModId().equalsIgnoreCase(query) ||
                mod.getDisplayName().equalsIgnoreCase(query)) {
                return mod;
            }
        }
        
        // Finally try partial match
        for (ModRegistry.ModInfo mod : ModRegistry.getAllMods()) {
            if (mod.getModId().toLowerCase().contains(queryLower) ||
                mod.getDisplayName().toLowerCase().contains(queryLower)) {
                return mod;
            }
        }
        
        return null;
    }
    
    // ==================== FORMATTING HELPERS ====================

    private static MutableComponent header(String text) {
        return Component.literal("")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append("[OpSec] ")
                .append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }

    private static MutableComponent subheader(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent info(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent success(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    private static MutableComponent warning(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent error(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent dim(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static MutableComponent listItem(String text) {
        return Component.literal("  - ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    private static MutableComponent modEntry(ModRegistry.ModInfo info) {
        MutableComponent component = Component.literal(info.getDisplayName())
                .withStyle(ChatFormatting.WHITE);

        List<String> details = new ArrayList<>();
        if (info.hasTranslationKeys()) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KEYS, info.getTranslationKeys().size()));
        }
        if (info.hasKeybinds()) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KEYBINDS, info.getKeybinds().size()));
        }
        if (info.hasChannels()) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_CHANNELS, info.getChannels().size()));
        }

        if (!details.isEmpty()) {
            component.append(Component.literal(" (" + String.join(", ", details) + ")")
                    .withStyle(ChatFormatting.GRAY));
        }

        return component;
    }
}

package aurick.opsec.mod.command;

import aurick.opsec.mod.tracking.ModRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
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
public class OpsecCommand {
    
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(OpsecCommand::registerCommands);
    }
    
    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, 
                                          CommandBuildContext context) {
        dispatcher.register(
            ClientCommandManager.literal("opsec")
                .executes(OpsecCommand::showHelp)
                .then(ClientCommandManager.literal("info")
                    .then(ClientCommandManager.argument("modName", StringArgumentType.greedyString())
                        .suggests(OpsecCommand::suggestModNames)
                        .executes(ctx -> showModInfo(ctx, StringArgumentType.getString(ctx, "modName"))))
                    .executes(ctx -> showOverview(ctx)))
                .then(ClientCommandManager.literal("channels")
                    .executes(OpsecCommand::showAllChannels))
        );
    }
    
    /**
     * Show help message.
     */
    private static int showHelp(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header("OpSec Commands"));
        source.sendFeedback(Component.empty());
        source.sendFeedback(info("/opsec info [mod]  - Show tracked mods or details for a specific mod"));
        source.sendFeedback(info("/opsec channels    - Show all tracked network channels"));
        
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
        
        source.sendFeedback(header("OPSEC Mod Registry"));
        source.sendFeedback(info("Total mods tracked: " + ModRegistry.getAllMods().size()));
        source.sendFeedback(info("Vanilla translation keys: " + ModRegistry.getVanillaKeyCount()));
        source.sendFeedback(info("Server pack keys: " + ModRegistry.getServerPackKeyCount()));
        source.sendFeedback(info("Total translation keys: " + ModRegistry.getTranslationKeyCount()));
        source.sendFeedback(info("Total keybinds: " + ModRegistry.getKeybindCount()));
        
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader("Mods with trackable content:"));
        
        int count = 0;
        for (ModRegistry.ModInfo info : ModRegistry.getAllMods()) {
            if (info.hasTrackableContent()) {
                source.sendFeedback(modEntry(info));
                count++;
            }
        }
        
        if (count == 0) {
            source.sendFeedback(warning("No mods with trackable content found."));
        }
        
        source.sendFeedback(Component.empty());
        source.sendFeedback(info("Use /opsec info <mod> for details"));
        
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
            source.sendFeedback(error("Mod not found: " + modName));
            source.sendFeedback(info("Use /opsec info to see available mods"));
            return 0;
        }
        
        source.sendFeedback(header("Mod Info: " + info.getDisplayName()));
        source.sendFeedback(info("ID: " + info.getModId()));
        
        // Translation keys
        Set<String> keys = info.getTranslationKeys();
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader("Translation Keys (" + keys.size() + "):"));
        if (keys.isEmpty()) {
            source.sendFeedback(dim("  (none)"));
        } else {
            int shown = 0;
            for (String key : keys) {
                if (shown >= 20) {
                    source.sendFeedback(dim("  ... and " + (keys.size() - shown) + " more"));
                    break;
                }
                source.sendFeedback(listItem(key));
                shown++;
            }
        }
        
        // Keybinds
        Set<String> keybinds = info.getKeybinds();
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader("Keybinds (" + keybinds.size() + "):"));
        if (keybinds.isEmpty()) {
            source.sendFeedback(dim("  (none)"));
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
        source.sendFeedback(subheader("Network Channels (" + channels.size() + "):"));
        if (channels.isEmpty()) {
            source.sendFeedback(dim("  (none)"));
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
        boolean isWhitelisted = ModRegistry.isWhitelistedMod(info.getModId());
        if (isWhitelisted) {
            source.sendFeedback(success("Whitelist Status: ALLOWED"));
        } else {
            source.sendFeedback(warning("Whitelist Status: BLOCKED"));
        }
        
        return 1;
    }
    
    
    /**
     * Show all tracked channels.
     */
    private static int showAllChannels(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header("All Tracked Channels"));
        
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
            source.sendFeedback(warning("No channels tracked yet. Try /opsec scan"));
        } else {
            source.sendFeedback(Component.empty());
            source.sendFeedback(info("Total: " + totalChannels + " channels"));
            source.sendFeedback(dim("✓ = whitelisted, ✗ = blocked"));
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
        return Component.literal("§6§l[OpSec] §r§e" + text);
    }
    
    private static MutableComponent subheader(String text) {
        return Component.literal("§7" + text);
    }
    
    private static MutableComponent info(String text) {
        return Component.literal("§f" + text);
    }
    
    private static MutableComponent success(String text) {
        return Component.literal("§a" + text);
    }
    
    private static MutableComponent warning(String text) {
        return Component.literal("§e" + text);
    }
    
    private static MutableComponent error(String text) {
        return Component.literal("§c" + text);
    }
    
    private static MutableComponent dim(String text) {
        return Component.literal("§8" + text);
    }
    
    private static MutableComponent listItem(String text) {
        return Component.literal("§7  - §f" + text);
    }
    
    private static MutableComponent modEntry(ModRegistry.ModInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("§f").append(info.getDisplayName());
        
        List<String> details = new ArrayList<>();
        if (info.hasTranslationKeys()) {
            details.add(info.getTranslationKeys().size() + " keys");
        }
        if (info.hasKeybinds()) {
            details.add(info.getKeybinds().size() + " keybinds");
        }
        if (info.hasChannels()) {
            details.add(info.getChannels().size() + " channels");
        }
        
        if (!details.isEmpty()) {
            sb.append(" §7(").append(String.join(", ", details)).append(")");
        }
        
        return Component.literal(sb.toString());
    }
}

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
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import java.util.HashSet;
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
                .then(ClientCommandManager.literal("stats")
                    .executes(OpsecCommand::showStats))
                .then(ClientCommandManager.literal("channels")
                    .executes(OpsecCommand::showAllChannels))
                .then(ClientCommandManager.literal("scan")
                    .executes(OpsecCommand::scanChannels))
                .then(ClientCommandManager.literal("fabric")
                    .executes(OpsecCommand::showFabricChannels))
                .then(ClientCommandManager.literal("help")
                    .executes(OpsecCommand::showHelp))
        );
    }
    
    /**
     * Show help message.
     */
    private static int showHelp(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header("OPSEC Debug Commands"));
        source.sendFeedback(Component.empty());
        source.sendFeedback(info("/opsec info [mod]  - Show tracked mods or details for a specific mod"));
        source.sendFeedback(info("/opsec channels    - Show all tracked network channels"));
        source.sendFeedback(info("/opsec scan        - Manually scan Fabric API for channels"));
        source.sendFeedback(info("/opsec fabric      - Show default Fabric channels (what clean Fabric sends)"));
        source.sendFeedback(info("/opsec stats       - Show registry statistics"));
        source.sendFeedback(info("/opsec help        - Show this help message"));
        source.sendFeedback(Component.empty());
        source.sendFeedback(dim("Channel tracking works by intercepting:"));
        source.sendFeedback(dim("  - minecraft:register packets (most reliable)"));
        source.sendFeedback(dim("  - Fabric API registerGlobalReceiver calls"));
        source.sendFeedback(dim("  - PayloadTypeRegistry registrations"));
        source.sendFeedback(Component.empty());
        source.sendFeedback(warning("Note: Some mods (like Simple Voice Chat) use"));
        source.sendFeedback(warning("external protocols (UDP) instead of Minecraft channels."));
        
        return 1;
    }
    
    /**
     * Suggest mod names that have trackable content.
     */
    private static CompletableFuture<Suggestions> suggestModNames(
            CommandContext<FabricClientCommandSource> context, 
            SuggestionsBuilder builder) {
        
        List<String> modNames = new ArrayList<>();
        
        for (ModRegistry.ModInfo info : ModRegistry.getAllMods()) {
            if (info.hasTrackableContent()) {
                // Add both mod ID and display name for flexibility
                modNames.add(info.getModId());
                if (!info.getDisplayName().equals(info.getModId())) {
                    modNames.add(info.getDisplayName());
                }
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
     * Show registry statistics.
     */
    private static int showStats(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header("OPSEC Registry Statistics"));
        source.sendFeedback(info("Tracked mods: " + ModRegistry.getAllMods().size()));
        source.sendFeedback(info("Vanilla keys: " + ModRegistry.getVanillaKeyCount()));
        source.sendFeedback(info("Server pack keys: " + ModRegistry.getServerPackKeyCount()));
        source.sendFeedback(info("Total keys: " + ModRegistry.getTranslationKeyCount()));
        source.sendFeedback(info("Total keybinds: " + ModRegistry.getKeybindCount()));
        source.sendFeedback(info("Registry initialized: " + ModRegistry.isInitialized()));
        
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
     * Manually scan for channels from Fabric API.
     */
    private static int scanChannels(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header("Scanning Fabric Channels..."));
        
        //? if >=1.21.11 {
        /*Set<Identifier> allChannels = new HashSet<>();*/
        //?} else {
        Set<ResourceLocation> allChannels = new HashSet<>();
        //?}
        int newChannels = 0;
        
        // Scan play channels
        try {
            //? if >=1.21.11 {
            /*Set<Identifier> playChannels = ClientPlayNetworking.getGlobalReceivers();*/
            //?} else {
            Set<ResourceLocation> playChannels = ClientPlayNetworking.getGlobalReceivers();
            //?}
            source.sendFeedback(info("Play channels found: " + playChannels.size()));
            allChannels.addAll(playChannels);
        } catch (Exception e) {
            source.sendFeedback(warning("Could not scan play channels: " + e.getMessage()));
        }
        
        // Scan config channels
        try {
            //? if >=1.21.11 {
            /*Set<Identifier> configChannels = ClientConfigurationNetworking.getGlobalReceivers();*/
            //?} else {
            Set<ResourceLocation> configChannels = ClientConfigurationNetworking.getGlobalReceivers();
            //?}
            source.sendFeedback(info("Config channels found: " + configChannels.size()));
            allChannels.addAll(configChannels);
        } catch (Exception e) {
            source.sendFeedback(warning("Could not scan config channels: " + e.getMessage()));
        }
        
        // Track all found channels
        //? if >=1.21.11 {
        /*for (Identifier channel : allChannels) {*/
        //?} else {
        for (ResourceLocation channel : allChannels) {
        //?}
            String namespace = channel.getNamespace();
            
            // Skip core channels
            if ("minecraft".equals(namespace) || "fabric".equals(namespace) || 
                namespace.startsWith("fabric-") || "c".equals(namespace)) {
                continue;
            }
            
            ModRegistry.ModInfo info = ModRegistry.getModInfo(namespace);
            if (info == null || !info.getChannels().contains(channel)) {
                ModRegistry.recordChannel(namespace, channel);
                newChannels++;
            }
        }
        
        source.sendFeedback(Component.empty());
        source.sendFeedback(success("Scan complete!"));
        source.sendFeedback(info("Total channels found: " + allChannels.size()));
        source.sendFeedback(info("New channels tracked: " + newChannels));
        source.sendFeedback(Component.empty());
        
        // Show all scanned channels
        source.sendFeedback(subheader("All Fabric API Channels:"));
        //? if >=1.21.11 {
        /*for (Identifier channel : allChannels) {*/
        //?} else {
        for (ResourceLocation channel : allChannels) {
        //?}
            boolean whitelisted = ModRegistry.isWhitelistedChannel(channel);
            if (whitelisted) {
                source.sendFeedback(Component.literal("  ✓ " + channel.toString())
                    .withStyle(ChatFormatting.GREEN));
            } else {
                source.sendFeedback(Component.literal("  ✗ " + channel.toString())
                    .withStyle(ChatFormatting.RED));
            }
        }
        
        return 1;
    }
    
    /**
     * Show default Fabric channels that a clean Fabric client would register.
     */
    private static int showFabricChannels(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header("Default Fabric Channels"));
        source.sendFeedback(info("These are the channels a stock Fabric client sends:"));
        source.sendFeedback(Component.empty());
        
        //? if >=1.21.11 {
        /*Set<Identifier> allChannels = new HashSet<>();*/
        //?} else {
        Set<ResourceLocation> allChannels = new HashSet<>();
        //?}
        
        // Get all currently registered channels from Fabric API
        try {
            allChannels.addAll(ClientPlayNetworking.getGlobalReceivers());
        } catch (Exception e) {
            // Ignore
        }
        try {
            allChannels.addAll(ClientConfigurationNetworking.getGlobalReceivers());
        } catch (Exception e) {
            // Ignore
        }
        
        // Filter to only show fabric/minecraft/c channels (core channels)
        //? if >=1.21.11 {
        /*List<Identifier> fabricChannels = new ArrayList<>();
        List<Identifier> minecraftChannels = new ArrayList<>();
        List<Identifier> commonChannels = new ArrayList<>();
        
        for (Identifier channel : allChannels) {*/
        //?} else {
        List<ResourceLocation> fabricChannels = new ArrayList<>();
        List<ResourceLocation> minecraftChannels = new ArrayList<>();
        List<ResourceLocation> commonChannels = new ArrayList<>();
        
        //? if >=1.21.11 {
        /*for (Identifier channel : allChannels) {*/
        //?} else {
        for (ResourceLocation channel : allChannels) {
        //?}
        //?}
            String ns = channel.getNamespace();
            if ("minecraft".equals(ns)) {
                minecraftChannels.add(channel);
            } else if ("fabric".equals(ns) || ns.startsWith("fabric-")) {
                fabricChannels.add(channel);
            } else if ("c".equals(ns)) {
                commonChannels.add(channel);
            }
        }
        
        // Sort each list
        fabricChannels.sort((a, b) -> a.toString().compareTo(b.toString()));
        minecraftChannels.sort((a, b) -> a.toString().compareTo(b.toString()));
        commonChannels.sort((a, b) -> a.toString().compareTo(b.toString()));
        
        // Display Minecraft channels
        if (!minecraftChannels.isEmpty()) {
            source.sendFeedback(subheader("minecraft:* channels (" + minecraftChannels.size() + "):"));
            //? if >=1.21.11 {
            /*for (Identifier channel : minecraftChannels) {*/
            //?} else {
            for (ResourceLocation channel : minecraftChannels) {
            //?}
                source.sendFeedback(listItem(channel.toString()));
            }
            source.sendFeedback(Component.empty());
        }
        
        // Display Fabric channels
        if (!fabricChannels.isEmpty()) {
            source.sendFeedback(subheader("fabric:* / fabric-*:* channels (" + fabricChannels.size() + "):"));
            //? if >=1.21.11 {
            /*for (Identifier channel : fabricChannels) {*/
            //?} else {
            for (ResourceLocation channel : fabricChannels) {
            //?}
                source.sendFeedback(listItem(channel.toString()));
            }
            source.sendFeedback(Component.empty());
        }
        
        // Display Common channels
        if (!commonChannels.isEmpty()) {
            source.sendFeedback(subheader("c:* channels (" + commonChannels.size() + "):"));
            //? if >=1.21.11 {
            /*for (Identifier channel : commonChannels) {*/
            //?} else {
            for (ResourceLocation channel : commonChannels) {
            //?}
                source.sendFeedback(listItem(channel.toString()));
            }
            source.sendFeedback(Component.empty());
        }
        
        int totalCore = fabricChannels.size() + minecraftChannels.size() + commonChannels.size();
        source.sendFeedback(success("Total core channels: " + totalCore));
        source.sendFeedback(dim("These channels are always allowed when spoofing as 'fabric'"));
        
        // Also show what would be blocked
        int modChannelCount = 0;
        //? if >=1.21.11 {
        /*for (Identifier channel : allChannels) {*/
        //?} else {
        for (ResourceLocation channel : allChannels) {
        //?}
            String ns = channel.getNamespace();
            if (!"minecraft".equals(ns) && !"fabric".equals(ns) && 
                !ns.startsWith("fabric-") && !"c".equals(ns)) {
                modChannelCount++;
            }
        }
        
        if (modChannelCount > 0) {
            source.sendFeedback(Component.empty());
            source.sendFeedback(warning("Mod channels that would be hidden: " + modChannelCount));
            source.sendFeedback(dim("Use /opsec channels to see all channels"));
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

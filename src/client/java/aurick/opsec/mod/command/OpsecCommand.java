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
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
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
     * Suggest mod ids that have any trackable content (translation keys,
     * channels, keybinds, or known-pack triples). Includes JIJ submodules —
     * {@code /opsec info} is a debug command, so granularity matters; the
     * whitelist UI is the one that only shows top-level mods.
     */
    private static CompletableFuture<Suggestions> suggestModNames(
            CommandContext<FabricClientCommandSource> context,
            SuggestionsBuilder builder) {

        Set<String> modNames = new LinkedHashSet<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            if (ModRegistry.PLATFORM_MODS.contains(modId)) continue;
            if (ModRegistry.hasTrackableContentIncludingJij(modId)) {
                modNames.add(modId);
            }
        }

        return SharedSuggestionProvider.suggest(modNames, builder);
    }
    
    /**
     * Show overview of all tracked mods.
     */
    private static int showOverview(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();

        // Pre-scan: gather displayable mods + per-mod counts in one pass so the
        // header total and the body loop never disagree.
        List<MutableComponent> entries = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            if (ModRegistry.PLATFORM_MODS.contains(modId)) continue;
            if (mod.getContainingMod().isPresent()) continue; // JIJ children roll up to host
            ModRegistry.ContentCounts counts = ModRegistry.aggregateContent(modId);
            if (counts.isEmpty()) continue;
            entries.add(modEntry(mod.getMetadata().getName(), counts));
        }

        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_HEADER)));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_MODS, entries.size())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_VANILLA_KEYS, ModRegistry.getVanillaKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_SERVER_KEYS, ModRegistry.getServerPackKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KEYS, ModRegistry.getTranslationKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KEYBINDS, ModRegistry.getKeybindCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_SHADERS, ModRegistry.getShaderCount())));
        // Known-packs total is only meaningful where the leak exists. Suppressed on hook-absent clients (same gate as /opsec info).
        if (ModRegistry.isKnownPacksHookPresent()) {
            source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KNOWN_PACKS, ModRegistry.getKnownPackCount())));
        }

        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_MODS_HEADER)));

        if (entries.isEmpty()) {
            source.sendFeedback(warning(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_NO_MODS)));
        } else {
            for (MutableComponent entry : entries) {
                source.sendFeedback(entry);
            }
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

        Resolved resolved = findMod(modName);
        if (resolved == null) {
            source.sendFeedback(error(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NOT_FOUND, modName)));
            source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_INFO_USE_LIST)));
            return 0;
        }

        String modId = resolved.modId();
        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_INFO_HEADER, resolved.displayName())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_INFO_ID, modId)));

        // Aggregate includes JIJ descendants so meta jars (e.g. fabric-api) report children's content.
        renderContentSection(source, OpsecStrings.COMMAND_INFO_TRANSLATION_KEYS,
            ModRegistry.aggregateAllTranslationKeys(modId), 20);
        renderContentSection(source, OpsecStrings.COMMAND_INFO_KEYBINDS,
            ModRegistry.aggregateAllKeybinds(modId), 20);
        renderContentSection(source, OpsecStrings.COMMAND_INFO_CHANNELS,
            ModRegistry.aggregateAllChannelIds(modId), 20);
        renderContentSection(source, OpsecStrings.COMMAND_INFO_SHADERS,
            ModRegistry.aggregateAllShaderPaths(modId), 20);

        // Suppressed when the Fabric hook is absent — the vector is dead there.
        if (ModRegistry.isKnownPacksHookPresent()) {
            List<String> knownPacks = ModRegistry.aggregateAllKnownPackStrings(modId);
            source.sendFeedback(Component.empty());
            source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_INFO_KNOWN_PACKS, knownPacks.size())));
            if (knownPacks.isEmpty()) {
                source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
            } else {
                for (String pack : knownPacks) {
                    source.sendFeedback(listItem(pack));
                }
            }
        }

        // Only list JIJ submodules with tracked content; pure library JIJs aren't queryable via /opsec info.
        List<? extends ModContainer> trackedContained = ModRegistry.getContainedMods(modId).stream()
            .filter(child -> ModRegistry.getModInfo(child.getMetadata().getId()) != null)
            .toList();
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_INFO_JIJ, trackedContained.size())));
        if (trackedContained.isEmpty()) {
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
        } else {
            for (ModContainer child : trackedContained) {
                source.sendFeedback(listItem(child.getMetadata().getId()));
            }
        }

        // Whitelist status
        source.sendFeedback(Component.empty());
        OpsecConfig opsecConfig = OpsecConfig.getInstance();
        SpoofSettings opsecSettings = opsecConfig.getSettings();
        SpoofSettings.WhitelistMode whitelistMode = opsecSettings.getWhitelistMode();
        ModRegistry.ModInfo info = ModRegistry.getModInfo(modId);
        boolean hasChannels = info != null && info.hasChannels();

        String statusText;
        boolean isAllowed;

        boolean directlyWhitelisted = switch (whitelistMode) {
            case AUTO -> hasChannels;
            case CUSTOM -> opsecSettings.isModWhitelisted(modId);
            default -> false;
        };

        if (directlyWhitelisted) {
            isAllowed = true;
            statusText = OpsecLang.tr(whitelistMode == SpoofSettings.WhitelistMode.AUTO
                ? OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_AUTO
                : OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_CUSTOM);
        } else if (ModRegistry.isInDependencyClosure(modId)) {
            isAllowed = true;
            statusText = OpsecLang.tr(OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_DEP,
                ModRegistry.resolveRequiringModName(modId));
        } else {
            isAllowed = false;
            statusText = OpsecLang.tr(switch (whitelistMode) {
                case AUTO -> OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_AUTO;
                case CUSTOM -> OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_CUSTOM;
                default -> OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_OFF;
            });
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
    
    
    /** Resolution result for {@link #findMod}. {@code modId} is canonical; {@code displayName} comes from ModInfo when present, else the mod's metadata name. */
    private record Resolved(String modId, String displayName) {}

    /**
     * Find a mod by id or display name. Resolves both tracked mods (with a
     * {@link ModRegistry.ModInfo}) and meta-mods (no ModInfo of their own,
     * content only via JIJ descendants — e.g. fabric-api). The exact-id
     * branch hits ModContainer when no ModInfo exists, gated on
     * {@link ModRegistry#hasTrackableContentIncludingJij}.
     */
    private static Resolved findMod(String query) {
        if (query == null || query.isEmpty()) return null;

        // Exact id: prefer tracked ModInfo, then meta-mod via ModContainer.
        ModRegistry.ModInfo info = ModRegistry.getModInfo(query);
        if (info != null) return new Resolved(info.getModId(), info.getDisplayName());
        var container = FabricLoader.getInstance().getModContainer(query);
        if (container.isPresent()) {
            // Canonicalize provides aliases — `getModContainer("fabric")` on older
            // fabric-api returns the fabric-api container; we want subsequent
            // aggregation to use the real id so JIJ walks find everything.
            var meta = container.get().getMetadata();
            String realId = meta.getId();
            if (ModRegistry.hasTrackableContentIncludingJij(realId)) {
                return new Resolved(realId, meta.getName());
            }
        }

        // Case-insensitive id / display-name match against tracked mods.
        for (ModRegistry.ModInfo mod : ModRegistry.getAllMods()) {
            if (mod.getModId().equalsIgnoreCase(query) ||
                mod.getDisplayName().equalsIgnoreCase(query)) {
                return new Resolved(mod.getModId(), mod.getDisplayName());
            }
        }
        // Same against top-level meta-mods (catches "Fabric API" by name when fabric-api itself has no ModInfo).
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (mod.getContainingMod().isPresent()) continue;
            if (ModRegistry.PLATFORM_MODS.contains(id)) continue;
            if (!ModRegistry.hasTrackableContentIncludingJij(id)) continue;
            String name = mod.getMetadata().getName();
            if (id.equalsIgnoreCase(query) || name.equalsIgnoreCase(query)) {
                return new Resolved(id, name);
            }
        }

        return null;
    }
    
    /** Render a /opsec info section: subheader+count, then "(none)" or up to {@code limit} items (negative = unlimited). */
    private static void renderContentSection(
        FabricClientCommandSource source,
        String headerKey,
        List<String> items,
        int limit
    ) {
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(headerKey, items.size())));
        if (items.isEmpty()) {
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
            return;
        }
        int shown = 0;
        for (String item : items) {
            if (limit >= 0 && shown >= limit) {
                source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_MORE, items.size() - shown)));
                break;
            }
            source.sendFeedback(listItem(item));
            shown++;
        }
    }

    // ==================== FORMATTING HELPERS ====================

    private static MutableComponent header(String text) {
        return Component.literal("[OpSec] ")
                .withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
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

    private static MutableComponent modEntry(String displayName, ModRegistry.ContentCounts counts) {
        MutableComponent component = Component.literal(displayName).withStyle(ChatFormatting.WHITE);

        List<String> details = new ArrayList<>();
        if (counts.translationKeys() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KEYS, counts.translationKeys()));
        }
        if (counts.keybinds() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KEYBINDS, counts.keybinds()));
        }
        if (counts.channels() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_CHANNELS, counts.channels()));
        }
        if (counts.knownPacks() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KNOWN_PACKS, counts.knownPacks()));
        }
        if (counts.shaders() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_SHADERS, counts.shaders()));
        }

        if (!details.isEmpty()) {
            component.append(Component.literal(" (" + String.join(", ", details) + ")")
                    .withStyle(ChatFormatting.GRAY));
        }

        return component;
    }
}

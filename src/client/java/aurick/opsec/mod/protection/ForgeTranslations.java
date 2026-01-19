package aurick.opsec.mod.protection;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides fake Forge/FML translations for spoofing as a Forge client.
 * Since OpSec runs on Fabric, these translations don't exist naturally.
 * This allows the client to appear consistent when spoofing as Forge.
 * 
 * Source: MinecraftForge/src/main/resources/assets/forge/lang/en_us.json
 */
public class ForgeTranslations {
    
    private static final Map<String, String> TRANSLATIONS = new HashMap<>();
    
    static {
        // FML Menu
        TRANSLATIONS.put("fml.menu.mods", "Mods");
        TRANSLATIONS.put("fml.menu.mods.title", "Mods");
        TRANSLATIONS.put("fml.menu.mods.normal", "Off");
        TRANSLATIONS.put("fml.menu.mods.search", "Search");
        TRANSLATIONS.put("fml.menu.mods.a_to_z", "A-Z");
        TRANSLATIONS.put("fml.menu.mods.z_to_a", "Z-A");
        TRANSLATIONS.put("fml.menu.mods.config", "Config");
        TRANSLATIONS.put("fml.menu.mods.openmodsfolder", "Open mods folder");
        TRANSLATIONS.put("fml.menu.modoptions", "Mod Options...");
        TRANSLATIONS.put("fml.menu.mods.info.version", "Version: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.idstate", "ModID: {0} State:{1,lower}");
        TRANSLATIONS.put("fml.menu.mods.info.credits", "Credits: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.authors", "Authors: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.displayurl", "Homepage: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.license", "License: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.securejardisabled", "Secure mod features disabled, update JDK");
        TRANSLATIONS.put("fml.menu.mods.info.signature", "Signature: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.signature.unsigned", "UNSIGNED");
        TRANSLATIONS.put("fml.menu.mods.info.trust", "Trust: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.trust.noauthority", "None");
        TRANSLATIONS.put("fml.menu.mods.info.nochildmods", "No child mods found");
        TRANSLATIONS.put("fml.menu.mods.info.childmods", "Child mods: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.updateavailable", "Update available: {0}");
        TRANSLATIONS.put("fml.menu.mods.info.changelogheader", "Changelog:");
        
        // FML Multiplayer
        TRANSLATIONS.put("fml.menu.multiplayer.compatible", "Compatible FML modded server\n{0,choice,1#1 mod|1<{0} mods} present");
        TRANSLATIONS.put("fml.menu.multiplayer.incompatible", "Incompatible FML modded server");
        TRANSLATIONS.put("fml.menu.multiplayer.incompatible.extra", "Incompatible FML modded server\n{0}");
        TRANSLATIONS.put("fml.menu.multiplayer.truncated", "Data may be inaccurate due to protocol size limits.");
        TRANSLATIONS.put("fml.menu.multiplayer.vanilla", "Vanilla server");
        TRANSLATIONS.put("fml.menu.multiplayer.vanilla.incompatible", "Incompatible Vanilla server");
        TRANSLATIONS.put("fml.menu.multiplayer.unknown", "Unknown server {0}");
        TRANSLATIONS.put("fml.menu.multiplayer.serveroutdated", "Forge server network version is outdated");
        TRANSLATIONS.put("fml.menu.multiplayer.clientoutdated", "Forge client network version is outdated");
        TRANSLATIONS.put("fml.menu.multiplayer.extraservermods", "Server has additional mods that may be needed on the client");
        TRANSLATIONS.put("fml.menu.multiplayer.modsincompatible", "Server mod list is not compatible");
        TRANSLATIONS.put("fml.menu.multiplayer.networkincompatible", "Server network message list is not compatible");
        TRANSLATIONS.put("fml.menu.multiplayer.missingdatapackregistries", "Missing required datapack registries: {0}");
        TRANSLATIONS.put("fml.menu.loadingmods", "{0,choice,0#No mods|1#1 mod|1<{0} mods} loaded");
        
        // FML Notifications
        TRANSLATIONS.put("fml.menu.notification.title", "Startup Notification");
        TRANSLATIONS.put("fml.menu.accessdenied.title", "Server Access Denied");
        TRANSLATIONS.put("fml.menu.accessdenied.message", "Forge Mod Loader could not connect to this server\nThe server {0} has forbidden modded access");
        TRANSLATIONS.put("fml.menu.backupfailed.title", "Backup Failed");
        TRANSLATIONS.put("fml.menu.backupfailed.message", "There was an error saving the archive {0}\nPlease fix the problem and try again");
        TRANSLATIONS.put("fml.button.open.file", "Open {0}");
        TRANSLATIONS.put("fml.button.open.mods.folder", "Open Mods Folder");
        TRANSLATIONS.put("fml.button.continue.launch", "Proceed to main menu");
        
        // FML Error/Warning Screens
        TRANSLATIONS.put("fml.loadingerrorscreen.errorheader", "Error loading mods\n{0,choice,1#1 error has|1<{0} errors have} occurred during loading");
        TRANSLATIONS.put("fml.loadingerrorscreen.warningheader", "{0,choice,1#Warning|1<Warnings} while loading mods\n{0,choice,1#1 warning has|1<{0} warnings have} occurred during loading");
        TRANSLATIONS.put("fml.modmismatchscreen.missingmods.client", "Your client is missing the following mods, install these mods to join this server:");
        TRANSLATIONS.put("fml.modmismatchscreen.missingmods.server", "The server is missing the following mods, remove these mods from your client to join this server:");
        TRANSLATIONS.put("fml.modmismatchscreen.mismatchedmods", "The following mod versions do not match, install the same version of these mods that the server has to join this server:");
        TRANSLATIONS.put("fml.modmismatchscreen.table.modname", "Mod name");
        TRANSLATIONS.put("fml.modmismatchscreen.table.youneed", "You need");
        TRANSLATIONS.put("fml.modmismatchscreen.table.youhave", "You have");
        TRANSLATIONS.put("fml.modmismatchscreen.table.serverhas", "Server has");
        TRANSLATIONS.put("fml.modmismatchscreen.additional", "[{0} additional, see latest.log for the full list]");
        TRANSLATIONS.put("fml.modmismatchscreen.homepage", "Click to get to the homepage of this mod");
        
        // FML Mod Loading Messages
        TRANSLATIONS.put("fml.language.missingversion", "Mod File {5} needs language provider {3}:{4,vr} to load\n\u00a77We have found {6,i18n,fml.messages.artifactversion}");
        TRANSLATIONS.put("fml.modloading.missingclasses", "The Mod File {3} has mods that were not found");
        TRANSLATIONS.put("fml.modloading.missingmetadata", "mods.toml missing metadata for modid {3}");
        TRANSLATIONS.put("fml.modloading.failedtoloadmodclass", "{0,modinfo,name} has class loading errors\n\u00a77{2,exc,msg}");
        TRANSLATIONS.put("fml.modloading.failedtoloadmod", "{0,modinfo,name} ({0,modinfo,id}) has failed to load correctly\n\u00a77{2,exc,msg}");
        TRANSLATIONS.put("fml.modloading.feature.missing", "{0,modinfo,name} ({0,modinfo,id}) is missing a feature it requires to run\n§7It requires {3,featurebound} but {4} is available");
        TRANSLATIONS.put("fml.modloading.uncaughterror", "An uncaught parallel processing error has occurred.\n\u00a77{2,exc,msg}");
        TRANSLATIONS.put("fml.modloading.errorduringevent", "{0,modinfo,name} ({0,modinfo,id}) encountered an error during the {1,lower} event phase\n\u00a77{2,exc,msg}");
        TRANSLATIONS.put("fml.modloading.failedtoloadforge", "Failed to load Forge");
        TRANSLATIONS.put("fml.modloading.missingdependency", "Mod \u00a7e{4}\u00a7r requires \u00a76{3}\u00a7r \u00a7o{5,vr}\u00a7r\n\u00a77Currently, \u00a76{3}\u00a7r\u00a77 is \u00a7o{6,i18n,fml.messages.artifactversion.ornotinstalled}");
        TRANSLATIONS.put("fml.modloading.missingdependency.optional", "Mod \u00a7e{4}\u00a7r only supports \u00a73{3}\u00a7r \u00a7o{5,vr}\u00a7r\n\u00a77Currently, \u00a73{3}\u00a7r\u00a77 is \u00a7o{6}");
        TRANSLATIONS.put("fml.dependencyloading.conflictingdependencies", "Some mods have requested conflicting versions of: \u00a76{3}\u00a7r. Requested by: \u00a7e{4}\u00a7r.");
        TRANSLATIONS.put("fml.dependencyloading.mismatchedcontaineddependencies", "Some mods have agreed upon an acceptable version range for : \u00a76{3}\u00a7r, but no jar was provided which matched the range. Requested by: \u00a7e{4}\u00a7r.");
        TRANSLATIONS.put("fml.modloading.cycle", "Detected a mod dependency cycle: {3}");
        TRANSLATIONS.put("fml.modloading.failedtoprocesswork", "{0,modinfo,name} ({0,modinfo,id}) encountered an error processing deferred work\n\u00a77{2,exc,msg}");
        TRANSLATIONS.put("fml.modloading.brokenfile", "File {2} is not a valid mod file");
        TRANSLATIONS.put("fml.modloading.brokenfile.oldforge", "File {2} is for an older version of Forge and cannot be loaded");
        TRANSLATIONS.put("fml.modloading.brokenfile.liteloader", "File {2} is a LiteLoader mod and cannot be loaded");
        TRANSLATIONS.put("fml.modloading.brokenfile.fabric", "File {2} is a Fabric mod and cannot be loaded");
        TRANSLATIONS.put("fml.modloading.brokenfile.optifine", "File {2} is an incompatible version of OptiFine");
        TRANSLATIONS.put("fml.modloading.brokenfile.bukkit", "File {2} is a Bukkit or Bukkit-implementor (Spigot, Paper, etc.) plugin and cannot be loaded");
        TRANSLATIONS.put("fml.modloading.brokenfile.invalidzip", "File {2} is not a jar file");
        TRANSLATIONS.put("fml.modloading.brokenresources", "File {2} failed to load a valid ResourcePackInfo");
        TRANSLATIONS.put("fml.modloading.missinglicense", "Missing License Information in file {3}");
        TRANSLATIONS.put("fml.resources.modresources", "Resources for {0} mod files");
        
        // FML Version Messages
        TRANSLATIONS.put("fml.messages.artifactversion.ornotinstalled", "{0,ornull,fml.messages.artifactversion.notinstalled}");
        TRANSLATIONS.put("fml.messages.artifactversion", "{0,ornull,fml.messages.artifactversion.none}");
        TRANSLATIONS.put("fml.messages.artifactversion.none", "none");
        TRANSLATIONS.put("fml.messages.artifactversion.notinstalled", "\u00a7nnot installed");
        TRANSLATIONS.put("fml.messages.version.restriction.any", "any");
        TRANSLATIONS.put("fml.messages.version.restriction.lower.inclusive", "{0} or above");
        TRANSLATIONS.put("fml.messages.version.restriction.lower.exclusive", "above {0}");
        TRANSLATIONS.put("fml.messages.version.restriction.upper.inclusive", "{0} or below");
        TRANSLATIONS.put("fml.messages.version.restriction.upper.exclusive", "below {0}");
        TRANSLATIONS.put("fml.messages.version.restriction.bounded", "between {0} and {1}");
        TRANSLATIONS.put("fml.messages.version.restriction.bounded.inclusive", "between {0} and {1} (inclusive)");
        TRANSLATIONS.put("fml.messages.version.restriction.bounded.exclusive", "between {0} and {1} (exclusive)");
        TRANSLATIONS.put("fml.messages.version.restriction.bounded.lowerexclusive", "above {0}, and {1} or below");
        TRANSLATIONS.put("fml.messages.version.restriction.bounded.upperexclusive", "{0} or above, and below {1}");
        
        // Forge Commands
        TRANSLATIONS.put("commands.forge.arguments.enum.invalid", "Enum constant must be one of {0}, found {1}");
        TRANSLATIONS.put("commands.forge.dimensions.list", "Currently registered dimensions by type:");
        TRANSLATIONS.put("commands.forge.entity.list.invalid", "Invalid filter, does not match any entities. Use /forge entity list for a proper list");
        TRANSLATIONS.put("commands.forge.entity.list.invalidworld", "Could not load world for dimension {0}. Please select a valid dimension.");
        TRANSLATIONS.put("commands.forge.entity.list.none", "No entities found.");
        TRANSLATIONS.put("commands.forge.entity.list.single.header", "Entity: {0} Total: {1}");
        TRANSLATIONS.put("commands.forge.entity.list.multiple.header", "Total: {0}");
        TRANSLATIONS.put("commands.forge.gen.usage", "Use /forge gen <x> <y> <z> <chunkCount> [dimension] [interval]");
        TRANSLATIONS.put("commands.forge.gen.dim_fail", "Failed to load world for dimension {0}, Task terminated.");
        TRANSLATIONS.put("commands.forge.gen.progress", "Generation Progress: {0}/{1}");
        TRANSLATIONS.put("commands.forge.gen.complete", "Finished generating {0} new chunks (out of {1}) for dimension {2}.");
        TRANSLATIONS.put("commands.forge.gen.start", "Starting to generate {0} chunks in a spiral around {1}, {2} in dimension {3}.");
        TRANSLATIONS.put("commands.forge.setdim.invalid.entity", "The entity selected ({0}) is not valid.");
        TRANSLATIONS.put("commands.forge.setdim.invalid.dim", "The dimension ID specified ({0}) is not valid.");
        TRANSLATIONS.put("commands.forge.setdim.invalid.nochange", "The entity selected ({0}) is already in the dimension specified ({1}).");
        TRANSLATIONS.put("commands.forge.setdim.deprecated", "This command is deprecated for removal in 1.17, use %s instead.");
        TRANSLATIONS.put("commands.forge.tps.invalid", "Invalid dimension {0} Possible values: {1}");
        TRANSLATIONS.put("commands.forge.tps.summary.all", "Overall: Mean tick time: {0} ms. Mean TPS: {1}");
        TRANSLATIONS.put("commands.forge.mods.list", "Mod List: {0}");
        TRANSLATIONS.put("commands.forge.tps.summary.basic", "Dim {0}: Mean tick time: {1} ms. Mean TPS: {2}");
        TRANSLATIONS.put("commands.forge.tps.summary.named", "Dim {0} ({1}): Mean tick time: {2} ms. Mean TPS: {3}");
        TRANSLATIONS.put("commands.forge.tracking.entity.enabled", "Entity tracking enabled for %d seconds.");
        TRANSLATIONS.put("commands.forge.tracking.entity.reset", "Entity timings data has been cleared!");
        TRANSLATIONS.put("commands.forge.tracking.invalid", "Invalid tracking data.");
        TRANSLATIONS.put("commands.forge.tracking.be.enabled", "Block Entity tracking enabled for %d seconds.");
        TRANSLATIONS.put("commands.forge.tracking.be.reset", "Block entity timings data has been cleared!");
        TRANSLATIONS.put("commands.forge.tracking.timing_entry", "{0} - {1} [{2}, {3}, {4}]: {5}");
        TRANSLATIONS.put("commands.forge.tracking.no_data", "No data has been recorded yet.");
        TRANSLATIONS.put("commands.forge.tags.error.unknown_registry", "Unknown registry '%s'");
        TRANSLATIONS.put("commands.forge.tags.error.unknown_tag", "Unknown tag '%s' in registry '%s'");
        TRANSLATIONS.put("commands.forge.tags.error.unknown_element", "Unknown element '%s' in registry '%s'");
        TRANSLATIONS.put("commands.forge.tags.registry_key", "%s");
        TRANSLATIONS.put("commands.forge.tags.tag_count", "Tags: %s");
        TRANSLATIONS.put("commands.forge.tags.copy_tag_names", "Click to copy all tag names to clipboard");
        TRANSLATIONS.put("commands.forge.tags.element_count", "Elements: %s");
        TRANSLATIONS.put("commands.forge.tags.copy_element_names", "Click to copy all element names to clipboard");
        TRANSLATIONS.put("commands.forge.tags.tag_key", "%s / %s");
        TRANSLATIONS.put("commands.forge.tags.containing_tag_count", "Containing tags: %s");
        TRANSLATIONS.put("commands.forge.tags.element", "%s : %s");
        TRANSLATIONS.put("commands.forge.tags.page_info", "%s <page %s / %s>");
        
        // Config Commands
        TRANSLATIONS.put("commands.config.getwithtype", "Config for %s of type %s found at %s");
        TRANSLATIONS.put("commands.config.noconfig", "Config for %s of type %s not found");
        
        // Forge Updates
        TRANSLATIONS.put("forge.update.newversion", "New Forge version available: %s");
        TRANSLATIONS.put("forge.menu.updatescreen.title", "Mod Update");
        
        // Forge Config GUI
        TRANSLATIONS.put("forge.configgui.removeErroringEntities.tooltip", "Set this to true to remove any Entity that throws an error in its update method instead of closing the server and reporting a crash log. BE WARNED THIS COULD SCREW UP EVERYTHING USE SPARINGLY WE ARE NOT RESPONSIBLE FOR DAMAGES.");
        TRANSLATIONS.put("forge.configgui.removeErroringEntities", "Remove Erroring Entities");
        TRANSLATIONS.put("forge.configgui.removeErroringBlockEntities.tooltip", "Set this to true to remove any BlockEntity that throws an error in its update method instead of closing the server and reporting a crash log. BE WARNED THIS COULD SCREW UP EVERYTHING USE SPARINGLY WE ARE NOT RESPONSIBLE FOR DAMAGES.");
        TRANSLATIONS.put("forge.configgui.removeErroringBlockEntities", "Remove Erroring Block Entities");
        TRANSLATIONS.put("forge.configgui.fullBoundingBoxLadders.tooltip", "Set this to true to check the entire entity's collision bounding box for ladders instead of just the block they are in. Causes noticeable differences in mechanics so default is vanilla behavior. Default: false.");
        TRANSLATIONS.put("forge.configgui.fullBoundingBoxLadders", "Full Bounding Box Ladders");
        TRANSLATIONS.put("forge.configgui.zombieBaseSummonChance.tooltip", "Base zombie summoning spawn chance. Allows changing the bonus zombie summoning mechanic.");
        TRANSLATIONS.put("forge.configgui.zombieBaseSummonChance", "Zombie Summon Chance");
        TRANSLATIONS.put("forge.configgui.zombieBabyChance.tooltip", "Chance that a zombie (or subclass) is a baby. Allows changing the zombie spawning mechanic.");
        TRANSLATIONS.put("forge.configgui.zombieBabyChance", "Zombie Baby Chance");
        TRANSLATIONS.put("forge.configgui.logCascadingWorldGeneration.tooltip", "Log cascading chunk generation issues during terrain population.");
        TRANSLATIONS.put("forge.configgui.logCascadingWorldGeneration", "Log Cascading World Gen");
        TRANSLATIONS.put("forge.configgui.fixVanillaCascading.tooltip", "Fix vanilla issues that cause worldgen cascading. This DOES change vanilla worldgen so DO NOT report bugs related to world differences if this flag is on.");
        TRANSLATIONS.put("forge.configgui.fixVanillaCascading", "Fix Vanilla Cascading");
        TRANSLATIONS.put("forge.configgui.dimensionUnloadQueueDelay.tooltip", "The time in ticks the server will wait when a dimension was queued to unload. This can be useful when rapidly loading and unloading dimensions, like e.g. throwing items through a nether portal a few time per second.");
        TRANSLATIONS.put("forge.configgui.dimensionUnloadQueueDelay", "Delay when unloading dimension");
        TRANSLATIONS.put("forge.configgui.clumpingThreshold.tooltip", "Controls the number threshold at which Packet51 is preferred over Packet52, default and minimum 64, maximum 1024.");
        TRANSLATIONS.put("forge.configgui.clumpingThreshold", "Packet Clumping Threshold");
        TRANSLATIONS.put("forge.configgui.treatEmptyTagsAsAir.tooltip", "Vanilla will treat crafting recipes using empty tags as air, and allow you to craft with nothing in that slot. This changes empty tags to use BARRIER as the item. To prevent crafting with air.");
        TRANSLATIONS.put("forge.configgui.treatEmptyTagsAsAir", "Treat empty tags as air");
        TRANSLATIONS.put("forge.configgui.skipEmptyShapelessCheck.tooltip", "Skip checking if an ingredient is empty during shapeless recipe deserialization to prevent complex ingredients from caching tags too early.");
        TRANSLATIONS.put("forge.configgui.skipEmptyShapelessCheck", "Skip checking for empty ingredients in Shapeless Recipe Deserialization");
        TRANSLATIONS.put("forge.configgui.forceSystemNanoTime.tooltip", "Force the use of System.nanoTime instead of glfwGetTime as the main client Time provider.");
        TRANSLATIONS.put("forge.configgui.forceSystemNanoTime", "Force System.nanoTime");
        TRANSLATIONS.put("forge.configgui.zoomInMissingModelTextInGui.tooltip", "Toggle off to make missing model text in the gui fit inside the slot.");
        TRANSLATIONS.put("forge.configgui.zoomInMissingModelTextInGui", "Zoom in Missing model text in the GUI");
        TRANSLATIONS.put("forge.configgui.forgeCloudsEnabled.tooltip", "Enable uploading cloud geometry to the GPU for faster rendering.");
        TRANSLATIONS.put("forge.configgui.forgeCloudsEnabled", "Use Forge cloud renderer");
        TRANSLATIONS.put("forge.configgui.disableStairSlabCulling.tooltip", "Disable culling of hidden faces next to stairs and slabs. Causes extra rendering, but may fix some resource packs that exploit this vanilla mechanic.");
        TRANSLATIONS.put("forge.configgui.disableStairSlabCulling", "Disable Stair/Slab culling");
        TRANSLATIONS.put("forge.configgui.alwaysSetupTerrainOffThread.tooltip", "Enable Forge to queue all chunk updates to the Chunk Update thread.\nMay increase FPS significantly, but may also cause weird rendering lag.\nNot recommended for computers without a significant number of cores available.");
        TRANSLATIONS.put("forge.configgui.alwaysSetupTerrainOffThread", "Force threaded chunk rendering");
        TRANSLATIONS.put("forge.configgui.forgeLightPipelineEnabled.tooltip", "Enable the Forge block rendering pipeline - fixes the lighting of custom models.");
        TRANSLATIONS.put("forge.configgui.forgeLightPipelineEnabled", "Forge Light Pipeline Enabled");
        TRANSLATIONS.put("forge.configgui.selectiveResourceReloadEnabled.tooltip", "When enabled, makes specific reload tasks such as language changing quicker to run.");
        TRANSLATIONS.put("forge.configgui.selectiveResourceReloadEnabled", "Enable Selective Resource Loading");
        TRANSLATIONS.put("forge.configgui.showLoadWarnings.tooltip", "When enabled, Forge will show any warnings that occurred during loading.");
        TRANSLATIONS.put("forge.configgui.showLoadWarnings", "Show Load Warnings");
        TRANSLATIONS.put("forge.configgui.allowMipmapLowering.tooltip", "When enabled, Forge will allow mipmaps to be lowered in real-time. This is the default behavior in vanilla. Use this if you experience issues with resource packs that use textures lower than 8x8.");
        TRANSLATIONS.put("forge.configgui.allowMipmapLowering", "Allow mipmap lowering");
        TRANSLATIONS.put("forge.configgui.disableVersionCheck.tooltip", "Set to true to disable Forge version check mechanics. Forge queries a small json file on our server for version information. For more details see the ForgeVersion class in our github.");
        TRANSLATIONS.put("forge.configgui.disableVersionCheck", "Disable Forge Version Check");
        TRANSLATIONS.put("forge.configgui.cachePackAccess.tooltip", "Set this to true to cache resource listings in resource and data packs");
        TRANSLATIONS.put("forge.configgui.cachePackAccess", "Cache Pack Access");
        TRANSLATIONS.put("forge.configgui.indexVanillaPackCachesOnThread.tooltip", "Set this to true to index vanilla resource and data packs on thread");
        TRANSLATIONS.put("forge.configgui.indexVanillaPackCachesOnThread", "Index vanilla resource packs on thread");
        TRANSLATIONS.put("forge.configgui.indexModPackCachesOnThread.tooltip", "Set this to true to index mod resource and data packs on thread");
        TRANSLATIONS.put("forge.configgui.indexModPackCachesOnThread", "Index mod resource packs on thread");
        TRANSLATIONS.put("forge.configgui.calculateAllNormals", "Calculate All Normals");
        TRANSLATIONS.put("forge.configgui.calculateAllNormals.tooltip", "During block model baking, manually calculates the normal for all faces. You will need to reload your resources to see results.");
        TRANSLATIONS.put("forge.configgui.stabilizeDirectionGetNearest", "Stabilize Direction Get Nearest");
        TRANSLATIONS.put("forge.configgui.stabilizeDirectionGetNearest.tooltip", "When enabled, a slightly biased Direction#getNearest calculation will be used to prevent normal fighting on 45 degree angle faces.");
        
        // Forge Controls
        TRANSLATIONS.put("forge.controlsgui.shift", "SHIFT + %s");
        TRANSLATIONS.put("forge.controlsgui.control", "CTRL + %s");
        TRANSLATIONS.put("forge.controlsgui.control.mac", "CMD + %s");
        TRANSLATIONS.put("forge.controlsgui.alt", "ALT + %s");
        
        // Forge Attributes
        TRANSLATIONS.put("forge.container.enchant.limitedEnchantability", "Limited Enchantability");
        TRANSLATIONS.put("forge.swim_speed", "Swim Speed");
        TRANSLATIONS.put("forge.name_tag_distance", "Nametag Render Distance");
        TRANSLATIONS.put("forge.entity_gravity", "Gravity");
        TRANSLATIONS.put("forge.block_reach", "Block Reach");
        TRANSLATIONS.put("forge.entity_reach", "Entity Reach");
        TRANSLATIONS.put("forge.step_height", "Step Height");
        
        // Fluids
        TRANSLATIONS.put("fluid_type.minecraft.milk", "Milk");
        TRANSLATIONS.put("fluid_type.minecraft.flowing_milk", "Milk");
        
        // Forge Misc
        TRANSLATIONS.put("forge.froge.warningScreen.title", "Forge snapshots notice");
        TRANSLATIONS.put("forge.froge.warningScreen.text", "Froge is not officially supported. Bugs and instability are expected.");
        TRANSLATIONS.put("forge.froge.supportWarning", "WARNING: Froge is not supported by Minecraft Forge");
        TRANSLATIONS.put("forge.gui.exit", "Exit");
        TRANSLATIONS.put("forge.experimentalsettings.tooltip", "This world uses experimental settings, which could stop working at any time.");
        TRANSLATIONS.put("forge.selectWorld.backupWarning.experimental.additional", "This message will not show again for this world.");
        TRANSLATIONS.put("forge.chatType.system", "{0}");
        
        // Resource Packs
        TRANSLATIONS.put("pack.forge.description", "Forge resource pack");
        TRANSLATIONS.put("pack.source.mod", "Mod");
        TRANSLATIONS.put("pack.source.forgemod", "Forge mod");
    }
    
    /**
     * Check if a translation key is a known Forge key.
     */
    public static boolean isForgeKey(String key) {
        return key != null && TRANSLATIONS.containsKey(key);
    }
    
    /**
     * Get the fake translation for a Forge key.
     * @return The translation value, or null if not a known Forge key.
     */
    public static String getTranslation(String key) {
        return key != null ? TRANSLATIONS.get(key) : null;
    }
    
    /**
     * Get the number of registered Forge translations.
     */
    public static int getTranslationCount() {
        return TRANSLATIONS.size();
    }
}

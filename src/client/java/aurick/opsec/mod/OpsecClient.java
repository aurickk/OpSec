package aurick.opsec.mod;

import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.command.OpsecCommand;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.JarIntegrityChecker;
import aurick.opsec.mod.config.UpdateChecker;
import aurick.opsec.mod.protection.PackStripOverlay;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Client-side initialization for the OpSec mod.
 * Loads configuration and initializes protection systems.
 */
public class OpsecClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Log mod initialization
		Opsec.LOGGER.info("{} v{} - Privacy protection for Minecraft", Opsec.MOD_NAME, Opsec.getVersion());
		Opsec.LOGGER.info("Protecting against: TrackPack, Key Resolution Exploit, Client Fingerprinting");
		
		OpsecConfig.getInstance();
		OpsecCommand.register();
		AccountManager.getInstance(); // Load saved accounts

		// Check for mod updates (non-blocking)
		UpdateChecker.checkForUpdate();

		// Check jar integrity against GitHub release (non-blocking)
		JarIntegrityChecker.checkIntegrity();

		// Scan for registered channels after all mods have initialized
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			scanRegisteredChannels();
			// Fallback: scan mods for language files if mixin didn't catch them
			scanModsForLanguageFiles();
		});

		ClientTickEvents.END_CLIENT_TICK.register(PackStripOverlay::tryShowNext);

		Opsec.LOGGER.info("OpSec client protection initialized");
	}
	
	/**
	 * Scan for all registered channels from Fabric API.
	 * This runs after all mods have initialized, so we capture
	 * channels before the user opens the whitelist menu.
	 */
	private void scanRegisteredChannels() {
		int channelCount = 0;

		channelCount += scanChannelSource(ClientPlayNetworking::getGlobalReceivers, "play channels");
		channelCount += scanChannelSource(ClientConfigurationNetworking::getGlobalReceivers, "config channels");
		channelCount += scanChannelSource(ClientPlayNetworking::getReceived, "play received channels");
		channelCount += scanChannelSource(ClientPlayNetworking::getSendable, "play sendable channels");
		channelCount += scanChannelSource(ClientConfigurationNetworking::getReceived, "config received channels");
		channelCount += scanChannelSource(ClientConfigurationNetworking::getSendable, "config sendable channels");

		Opsec.LOGGER.debug("[OpSec] Scanned {} mod channels at startup", channelCount);
	}

	//? if >=1.21.11 {
	/*private int scanChannelSource(Supplier<Set<Identifier>> source, String label) {*/
	//?} else {
	private int scanChannelSource(Supplier<Set<ResourceLocation>> source, String label) {
	//?}
		try {
			int count = 0;
			for (var channel : source.get()) {
				String namespace = channel.getNamespace();
				if (!"minecraft".equals(namespace)) {
					ModRegistry.recordChannel(namespace, channel);
					count++;
				}
			}
			return count;
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan {}: {}", label, e.getMessage());
			return 0;
		}
	}
	
	/**
	 * Fallback: Scan all mods for language files and register them.
	 * This handles cases where the ClientLanguageMixin doesn't work
	 * (e.g., if the method signature changed in a new Minecraft version).
	 */
	private void scanModsForLanguageFiles() {
		int modsWithLang = 0;
		int modsAdded = 0;
		
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String modId = mod.getMetadata().getId();
			
			// Skip system mods
			if (modId.equals("minecraft") || modId.equals("java") || modId.equals("fabricloader")) {
				continue;
			}
			// Skip fabric API modules
			if (modId.startsWith("fabric-") || modId.equals("fabric-api")) {
				continue;
			}
			// Skip our own mod and mixinsquared
			if (modId.equals("opsec") || modId.equals("mixinsquared")) {
				continue;
			}
			
			// Check if this mod already has translation keys tracked
			ModRegistry.ModInfo existingInfo = ModRegistry.getModInfo(modId);
			if (existingInfo != null && existingInfo.hasTranslationKeys()) {
				modsWithLang++;
				continue;
			}
			
			// Check if this mod has a language file
			boolean found = false;
			for (Path rootPath : mod.getRootPaths()) {
				Path langFile = rootPath.resolve("assets/" + modId + "/lang/en_us.json");
				if (Files.exists(langFile)) {
					found = true;
					// This mod has a language file - register it
					try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(langFile))) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						int keyCount = 0;
						for (String key : json.keySet()) {
							ModRegistry.recordTranslationKey(modId, key);
							keyCount++;
						}
						if (keyCount > 0) {
							Opsec.LOGGER.debug("[OpSec] Fallback: Registered {} translation keys for mod '{}'", keyCount, modId);
							modsWithLang++;
							modsAdded++;
						}
					} catch (Exception e) {
						Opsec.LOGGER.debug("[OpSec] Could not read language file for {}: {}", modId, e.getMessage());
					}
					break; // Only check first root path
				}
			}
			
			// If no language file found, still count if mod has channels
			if (!found && existingInfo != null && existingInfo.hasChannels()) {
				modsWithLang++;
			}
		}
		
		Opsec.LOGGER.debug("[OpSec] Fallback scan added {} mods with translation keys", modsAdded);
		Opsec.LOGGER.debug("[ModRegistry] Total: {} whitelistable mods, {} translation keys, {} keybinds",
			modsWithLang, ModRegistry.getTranslationKeyCount(), ModRegistry.getKeybindCount());
	}
}

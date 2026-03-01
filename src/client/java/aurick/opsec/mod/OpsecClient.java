package aurick.opsec.mod;

import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.command.OpsecCommand;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.UpdateChecker;
import aurick.opsec.mod.protection.ChannelFilterHelper;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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
		if (!OpsecConfig.getInstance().getSettings().isDismissUpdateNotification()) {
			UpdateChecker.checkForUpdate();
		}

		// Scan for registered channels after all mods have initialized
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			scanRegisteredChannels();
			// Fallback: scan mods for language files if mixin didn't catch them
			scanModsForLanguageFiles();
		});

		Opsec.LOGGER.info("OpSec client protection initialized");
	}
	
	/**
	 * Scan for all registered channels from Fabric API.
	 * This runs after all mods have initialized, so we capture
	 * channels before the user opens the whitelist menu.
	 */
	private void scanRegisteredChannels() {
		int channelCount = 0;
		
		try {
			//? if >=1.21.11 {
			/*Set<Identifier> playChannels = ClientPlayNetworking.getGlobalReceivers();
			for (Identifier channel : playChannels) {*/
			//?} else {
			Set<ResourceLocation> playChannels = ClientPlayNetworking.getGlobalReceivers();
			for (ResourceLocation channel : playChannels) {
			//?}
				String namespace = channel.getNamespace();
				if (!ChannelFilterHelper.isCoreNamespace(namespace)) {
					ModRegistry.recordChannel(namespace, channel);
					channelCount++;
				}
			}
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan play channels: {}", e.getMessage());
		}
		
		try {
			//? if >=1.21.11 {
			/*Set<Identifier> configChannels = ClientConfigurationNetworking.getGlobalReceivers();
			for (Identifier channel : configChannels) {*/
			//?} else {
			Set<ResourceLocation> configChannels = ClientConfigurationNetworking.getGlobalReceivers();
			for (ResourceLocation channel : configChannels) {
			//?}
				String namespace = channel.getNamespace();
				if (!ChannelFilterHelper.isCoreNamespace(namespace)) {
					ModRegistry.recordChannel(namespace, channel);
					channelCount++;
				}
			}
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan config channels: {}", e.getMessage());
		}
		
		try {
			//? if >=1.21.11 {
			/*Set<Identifier> playReceived = ClientPlayNetworking.getReceived();
			for (Identifier channel : playReceived) {*/
			//?} else {
			Set<ResourceLocation> playReceived = ClientPlayNetworking.getReceived();
			for (ResourceLocation channel : playReceived) {
			//?}
				String namespace = channel.getNamespace();
				if (!ChannelFilterHelper.isCoreNamespace(namespace)) {
					ModRegistry.recordChannel(namespace, channel);
					channelCount++;
				}
			}
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan play received channels: {}", e.getMessage());
		}

		try {
			//? if >=1.21.11 {
			/*Set<Identifier> playSendable = ClientPlayNetworking.getSendable();
			for (Identifier channel : playSendable) {*/
			//?} else {
			Set<ResourceLocation> playSendable = ClientPlayNetworking.getSendable();
			for (ResourceLocation channel : playSendable) {
			//?}
				String namespace = channel.getNamespace();
				if (!ChannelFilterHelper.isCoreNamespace(namespace)) {
					ModRegistry.recordChannel(namespace, channel);
					channelCount++;
				}
			}
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan play sendable channels: {}", e.getMessage());
		}

		try {
			//? if >=1.21.11 {
			/*Set<Identifier> configReceived = ClientConfigurationNetworking.getReceived();
			for (Identifier channel : configReceived) {*/
			//?} else {
			Set<ResourceLocation> configReceived = ClientConfigurationNetworking.getReceived();
			for (ResourceLocation channel : configReceived) {
			//?}
				String namespace = channel.getNamespace();
				if (!ChannelFilterHelper.isCoreNamespace(namespace)) {
					ModRegistry.recordChannel(namespace, channel);
					channelCount++;
				}
			}
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan config received channels: {}", e.getMessage());
		}

		try {
			//? if >=1.21.11 {
			/*Set<Identifier> configSendable = ClientConfigurationNetworking.getSendable();
			for (Identifier channel : configSendable) {*/
			//?} else {
			Set<ResourceLocation> configSendable = ClientConfigurationNetworking.getSendable();
			for (ResourceLocation channel : configSendable) {
			//?}
				String namespace = channel.getNamespace();
				if (!ChannelFilterHelper.isCoreNamespace(namespace)) {
					ModRegistry.recordChannel(namespace, channel);
					channelCount++;
				}
			}
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan config sendable channels: {}", e.getMessage());
		}

		Opsec.LOGGER.debug("[OpSec] Scanned {} mod channels at startup", channelCount);
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

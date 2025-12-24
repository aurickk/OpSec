package incognito.mod;

import incognito.mod.command.IncognitoCommand;
import incognito.mod.config.IncognitoConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class IncognitoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IncognitoConfig.getInstance();
		ClientCommandRegistrationCallback.EVENT.register(IncognitoCommand::register);
		Incognito.LOGGER.info("Incognito client protection initialized");
	}
}

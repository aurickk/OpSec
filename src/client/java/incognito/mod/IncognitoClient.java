package incognito.mod;

import incognito.mod.config.IncognitoConfig;
import net.fabricmc.api.ClientModInitializer;

public class IncognitoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IncognitoConfig.getInstance();
		Incognito.LOGGER.info("Incognito client protection initialized");
	}
}

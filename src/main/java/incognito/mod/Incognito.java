package incognito.mod;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Incognito implements ModInitializer {
	public static final String MOD_ID = "incognito";
	public static final String MOD_NAME = "Incognito";
	public static final String VERSION = "1.0.0";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("{} v{} - Privacy protection for Minecraft", MOD_NAME, VERSION);
		LOGGER.info("Protecting against: TrackPack, Sign Translation Exploit, Client Fingerprinting");
	}
}

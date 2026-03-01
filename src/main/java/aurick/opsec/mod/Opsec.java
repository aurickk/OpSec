package aurick.opsec.mod;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants and utilities for OpSec - Privacy protection for Minecraft.
 * Protects against client fingerprinting, tracking, and key resolution exploits.
 * 
 * Note: This is a client-only mod. The actual initialization happens in
 * {@link aurick.opsec.mod.OpsecClient#onInitializeClient()}.
 */
public final class Opsec {
	public static final String MOD_ID = "opsec";
	public static final String MOD_NAME = "OpSec";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private Opsec() {
		// Utility class - prevent instantiation
	}

	public static String getVersion() {
		return FabricLoader.getInstance()
			.getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}
}

package aurick.opsec.mod.protection;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.tracking.ModRegistry;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

/**
 * Handles client brand spoofing and channel filtering logic.
 * Provides methods to check spoofing modes (vanilla, fabric)
 * and determines which network channels should be blocked.
 */
public class ClientSpoofer {

    /**
     * Check if running in vanilla mode for channel filtering purposes.
     * Requires both brand spoofing and channel spoofing to be enabled.
     * Delegates to SpoofSettings for brand mode check.
     */
    public static boolean isVanillaMode() {
        OpsecConfig config = OpsecConfig.getInstance();
        return config.shouldSpoofChannels() && config.getSettings().isVanillaMode();
    }

    /**
     * Check if running in fabric mode for channel filtering purposes.
     * Requires both brand spoofing and channel spoofing to be enabled.
     * Delegates to SpoofSettings for brand mode check.
     */
    public static boolean isFabricMode() {
        OpsecConfig config = OpsecConfig.getInstance();
        return config.shouldSpoofChannels() && config.getSettings().isFabricMode();
    }

    /**
     * Whether an inbound mod-channel payload should be filtered (treated as if absent, matching
     * vanilla) while spoofing. {@code minecraft:} channels always pass — vanilla handles them
     * identically; vanilla mode drops every mod channel; fabric mode drops only non-whitelisted
     * ones. Shared by the inbound decode ({@code PayloadTypeRegistryImplMixin}) and dispatch
     * ({@code AbstractChanneledNetworkAddonMixin}) hooks.
     */
    //? if >=1.21.11 {
    /*public static boolean shouldDropInboundChannel(Identifier channel) {*/
    //?} else {
    public static boolean shouldDropInboundChannel(ResourceLocation channel) {
    //?}
        if ("minecraft".equals(channel.getNamespace())) return false;
        if (isVanillaMode()) return true;
        return isFabricMode() && !ModRegistry.isWhitelistedChannel(channel);
    }
}

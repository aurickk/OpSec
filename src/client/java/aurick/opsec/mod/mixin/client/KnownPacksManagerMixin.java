package aurick.opsec.mod.mixin.client;

//? if >=1.20.5 {
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.tracking.ModRegistry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.KnownPacksManager;
import net.minecraft.server.packs.repository.KnownPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * The file is Stonecutter-gated {@code >=1.20.5} because {@code KnownPacksManager}
 * doesn't exist on earlier MC. The mixin entry in {@code opsec.client.mixins.json}
 * is gated by a matching {@code build.gradle} template-expand step.
 */
@Mixin(KnownPacksManager.class)
public class KnownPacksManagerMixin {

    @ModifyReturnValue(method = "trySelectingPacks", at = @At("RETURN"))
    private List<KnownPack> opsec$filterModdedPacks(List<KnownPack> original) {
        if (!ModRegistry.isKnownPacksHookPresent()) return original;
        if (!OpsecConfig.getInstance().shouldSpoofKnownPacks()) return original;

        boolean vanillaMode = OpsecConfig.getInstance().getSettings().isVanillaMode();

        List<KnownPack> filtered = new ArrayList<>(original.size());
        for (KnownPack pack : original) {
            if ("minecraft".equals(pack.namespace())) {
                filtered.add(pack);
                continue;
            }
            if (vanillaMode) continue;
            if (ModRegistry.isWhitelistedKnownPack(pack.namespace(), pack.id(), pack.version())) {
                filtered.add(pack);
            }
        }
        return filtered;
    }
}
//?} else {
/*public class KnownPacksManagerMixin {}
*///?}

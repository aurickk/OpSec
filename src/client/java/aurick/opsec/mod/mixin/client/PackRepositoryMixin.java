package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.protection.PackStripHandler;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.UUID;

/**
 * Vanilla's {@code DownloadedPackSource} builds every server pack with
 * {@code PackSelectionConfig(required=true, TOP, fixedPosition=true)} regardless
 * of the packet's {@code required} flag, so {@code Pack.isRequired()} returns
 * true for both required AND optional server packs. This means
 * {@code rebuildSelected} auto-re-adds any server pack the user unselects —
 * including optional ones that should follow vanilla toggle semantics.
 *
 * <p>This mixin narrows the auto-preserve check: OpSec-tracked optional server
 * packs report as non-required to {@code rebuildSelected}, so the user can
 * actually remove them from the repo. Required server packs keep the auto-
 * preserve so their lang stays resolving.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @WrapOperation(
        method = "rebuildSelected",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/Pack;isRequired()Z"))
    private boolean opsec$allowUnselectOptionalServerPack(Pack pack, Operation<Boolean> original) {
        boolean vanilla = original.call(pack);
        if (!vanilla) return false;
        if (pack.getPackSource() != PackSource.SERVER) return true;
        if (!OpsecConfig.getInstance().shouldStripPack()) return true;
        Optional<UUID> id = PackStripHandler.packIdToUuid(pack.getId());
        if (id.isEmpty()) return true;
        // Per-pack persistent opt-out: once the user unselects an optional server
        // pack in pack-select, skip the rebuildSelected auto-preserve for it on
        // every subsequent rebuild (including the post-commit reload) until the
        // user re-selects it or the server re-pushes with the same UUID.
        if (PackStripHandler.isUserUnselectedOptional(id.get())) return false;
        return true;
    }
}

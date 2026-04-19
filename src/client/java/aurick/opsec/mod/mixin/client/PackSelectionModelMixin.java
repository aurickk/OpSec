package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.protection.PackStripHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the side effects of {@link PackSelectionModelEntryBaseMixin}'s flag
 * flip. Vanilla's pack toggle now works on server packs; this mixin coordinates
 * the per-pack bypass state:
 * <ul>
 *   <li>Required packs: when the user visually moves one to {@code unselected},
 *       commit flips {@code loadForReal} off and forces a reload. Vanilla's
 *       {@code PackRepository.rebuildSelected} auto-preserves {@code isRequired()=true}
 *       packs, so the pack stays loaded in the repo and its lang keeps resolving
 *       via {@link aurick.opsec.mod.protection.LangOnlyPackResources}.</li>
 *   <li>Optional packs: commit records a persistent "user unselected" opt-out in
 *       {@link PackStripHandler}. {@link PackRepositoryMixin} reads that flag to
 *       skip the auto-preserve for that pack on every subsequent rebuild, so the
 *       pack + its lang actually stay out of the repo until the user re-selects
 *       or the server re-pushes the same UUID.</li>
 *   <li>On screen reopen, required packs with {@code !loadForReal} start in
 *       {@code unselected} so the UI reflects their persisted strip state.
 *       Optional packs follow their actual repo state (selected or not).</li>
 *   <li>Vanilla's {@code PackSelectionScreen.onClose} commits on both Done and
 *       Escape, so we treat every close as a commit.</li>
 * </ul>
 */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {

    @Shadow @Final private List<Pack> selected;
    @Shadow @Final private List<Pack> unselected;

    @Unique
    private boolean opsec$stripStateChanged;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void opsec$rearrangeOnOpen(CallbackInfo ci) {
        if (!OpsecConfig.getInstance().shouldStripPack()) return;

        List<Pack> toMove = new ArrayList<>();
        Iterator<Pack> it = selected.iterator();
        while (it.hasNext()) {
            Pack pack = it.next();
            if (pack.getPackSource() != PackSource.SERVER) continue;
            Optional<UUID> maybeId = PackStripHandler.packIdToUuid(pack.getId());
            if (maybeId.isEmpty()) continue;
            UUID id = maybeId.get();
            // Non-wrapped packs (optional in MANUAL): show actual repo state.
            if (!PackStripHandler.isWrapped(id)) continue;
            // Wrapped packs: show unselected if currently stripped.
            if (PackStripHandler.isLoadForReal(id)) continue;
            toMove.add(pack);
            it.remove();
        }
        for (Pack pack : toMove) {
            unselected.add(pack);
        }
    }

    @Inject(method = "commit", at = @At("HEAD"))
    private void opsec$applyStripState(CallbackInfo ci) {
        opsec$stripStateChanged = false;
        if (!OpsecConfig.getInstance().shouldStripPack()) return;

        for (Pack pack : selected) opsec$applyPackState(pack, true);
        for (Pack pack : unselected) opsec$applyPackState(pack, false);
    }

    // Options#updateResourcePacks skips fixedPosition packs (all server packs) when
    // diffing its reload trigger list, so a server-pack-only toggle never fires a
    // reload on its own. Force one when our tracked state actually flipped.
    @Inject(method = "commit", at = @At("RETURN"))
    private void opsec$reloadOnStripChange(CallbackInfo ci) {
        if (!opsec$stripStateChanged) return;
        Minecraft.getInstance().reloadResourcePacks();
    }

    private void opsec$applyPackState(Pack pack, boolean visuallySelected) {
        if (pack.getPackSource() != PackSource.SERVER) return;
        Optional<UUID> maybeId = PackStripHandler.packIdToUuid(pack.getId());
        if (maybeId.isEmpty()) return;
        UUID id = maybeId.get();

        if (PackStripHandler.isWrapped(id)) {
            // Wrapped pack (required, or optional under ASK / ALWAYS_ON):
            // flip its LangOnlyPackResources filter. Vanilla's rebuildSelected
            // auto-preserve keeps it in the repo because Pack.isRequired()=true.
            boolean prev = PackStripHandler.isLoadForReal(id);
            if (prev == visuallySelected) return;
            if (visuallySelected) PackStripHandler.markLoadForReal(id);
            else PackStripHandler.clearLoadForReal(id);
            opsec$stripStateChanged = true;
        } else {
            // Non-wrapped optional pack (MANUAL mode): record / clear persistent
            // user opt-out so PackRepositoryMixin can skip auto-preserve.
            boolean wasOptOut = PackStripHandler.isUserUnselectedOptional(id);
            boolean shouldOptOut = !visuallySelected;
            if (wasOptOut == shouldOptOut) return;
            if (shouldOptOut) PackStripHandler.markUserUnselectedOptional(id);
            else PackStripHandler.clearUserUnselectedOptional(id);
            opsec$stripStateChanged = true;
        }
    }
}

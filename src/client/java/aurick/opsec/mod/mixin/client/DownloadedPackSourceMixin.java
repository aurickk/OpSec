package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.protection.LangOnlyPackResources;
import aurick.opsec.mod.protection.PackStripHandler;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;

/**
 * Wraps each server pack's supplier with a {@link LangOnlyPackResources} based
 * on {@link PackStripHandler#isWrapped(java.util.UUID)} — required packs are
 * always wrapped; optional packs are wrapped under ASK / ALWAYS_ON so they get
 * stripped by default, and pass through unwrapped under MANUAL so they follow
 * vanilla toggle semantics (lang loads/unloads with the pack). Wrapped packs
 * keep lang applied even when stripped, so translation probes resolve vanilla-
 * identically.
 */
@Mixin(DownloadedPackSource.class)
public abstract class DownloadedPackSourceMixin {

    @WrapOperation(
        method = "loadRequestedPacks",
        at = @At(value = "NEW", target = "(Ljava/nio/file/Path;)Lnet/minecraft/server/packs/FilePackResources$FileResourcesSupplier;"))
    private FilePackResources.FileResourcesSupplier opsec$wrapFilePackSupplier(
            Path file,
            Operation<FilePackResources.FileResourcesSupplier> original,
            @Local PackReloadConfig.IdAndPath idAndPath) {

        FilePackResources.FileResourcesSupplier real = original.call(file);

        if (!OpsecConfig.getInstance().shouldStripPack()) return real;

        final java.util.UUID packId = idAndPath.id();
        if (!PackStripHandler.isWrapped(packId)) return real;

        return new FilePackResources.FileResourcesSupplier(file) {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) {
                return new LangOnlyPackResources(real.openPrimary(loc), packId);
            }

            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata md) {
                return new LangOnlyPackResources(real.openFull(loc, md), packId);
            }
        };
    }
}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.tracking.ModRegistry;
import com.llamalad7.mixinextras.sugar.Local;
//? if >=1.21.11 {
/*import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.impl.resource.ResourceLoaderImpl;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
*/
//?} else {
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ResourceManagerHelperImpl;
//?}
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records every {@code registerBuiltinPack}/{@code registerBuiltinResourcePack}
 * call whose SERVER_DATA pack ended up non-null (which means the pack will
 * appear in the configuration-phase {@code ServerboundSelectKnownPacks}
 * handshake).
 *
 * <p>OpSec's {@code /opsec info &lt;mod&gt;} reads from these via
 * {@link ModRegistry#getKnownPackStrings(String)} so users can see the exact
 * {@code (namespace, id, version)} triple a fishing server would need to
 * match to detect their installed builtin packs.</p>
 *
 * <p><b>Version split at MC 1.21.11.</b> Fabric API moved the real
 * registration impl out of the deprecated v0 {@code ResourceManagerHelperImpl}
 * (where it lived through 1.21.10) into v1 {@code ResourceLoaderImpl} starting
 * at 1.21.11. Pre-1.21.11 v0 jars have the method; post-1.21.11 v0 jars are
 * a thin shim and the method is gone. The same cutoff applies to the
 * {@code ResourceLocation} -&gt; {@code Identifier} mojang-mapping rename.
 * Both v0 and v1 impls share the same local-variable names
 * ({@code resourcePack}, {@code dataPack}), so {@code @Local(name = "dataPack")}
 * works in both.</p>
 */
//? if >=1.21.11 {
/*@Mixin(value = ResourceLoaderImpl.class, remap = false)
*/
//?} else {
@Mixin(value = ResourceManagerHelperImpl.class, remap = false)
//?}
public class ResourceLoaderBuiltinPackMixin {

    // No descriptor in `method = "..."` — the target class has `remap = false`
    // (it's a Fabric API class, not MC), so Mixin would treat the descriptor
    // literally and look for Mojang-name params in bytecode that's already in
    // intermediary form (e.g. class_2960 / class_2561). Matching by method name
    // only lets Mixin pick the correct overload via the handler's parameter
    // list, which Loom DOES remap at jar-build time. The two overloads on these
    // classes differ in the presence of the Component arg — the handler's
    // 5-param signature picks the version with displayName, which is the one
    // with the resourcePack/dataPack locals.
    //? if >=1.21.11 {
    /*@SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "registerBuiltinPack", at = @At("RETURN"))
    private static void opsec$onRegisterBuiltinPack(
            Identifier id, String subPath, ModContainer container,
            Component displayName, PackActivationType activationType,
            CallbackInfoReturnable<Boolean> cir,
            @Local(name = "dataPack") ModNioPackResources dataPack) {
        // Only record packs with server-data content — they're the ones that
        // ride the known-packs handshake. Resource-only builtin packs aren't.
        if (dataPack == null) return;
        ModRegistry.recordBuiltinPack(container.getMetadata().getId(), id.toString());
    }
    *///?} else {
    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "registerBuiltinResourcePack", at = @At("RETURN"))
    private static void opsec$onRegisterBuiltinPack(
            ResourceLocation id, String subPath, ModContainer container,
            Component displayName, ResourcePackActivationType activationType,
            CallbackInfoReturnable<Boolean> cir,
            @Local(name = "dataPack") ModNioResourcePack dataPack) {
        if (dataPack == null) return;
        ModRegistry.recordBuiltinPack(container.getMetadata().getId(), id.toString());
    }
    //?}
}

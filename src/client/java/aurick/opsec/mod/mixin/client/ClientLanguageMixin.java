package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModIdResolver;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Mixin to track translation keys from language files.
 * Uses instanceof checks matching ExploitPreventer's approach for reliability.
 * 
 * Pack types and handling:
 * - VanillaPackResources: Vanilla Minecraft → Always whitelisted
 * - FilePackResources: Downloaded server resource packs → Session whitelisted
 * - CompositePackResources: Combined packs (can include server) → Session whitelisted
 * - Fabric mod packs (detected via reflection) → Tracked as mod (blocked in exploitable contexts)
 * - PathPackResources: File path packs → Passthrough (not tracked)
 */
@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
    
    @Unique
    private static boolean opsec$loggedOnce = false;
    
    /**
     * Reset logging flag before loading new language.
     * NOTE: We no longer clear translation keys here because the WrapOperation may fail
     * silently (require=0) and our fallback scanner runs before this, so we'd wipe out
     * all the fallback's work without being able to re-add it.
     */
    @Inject(method = "loadFrom", at = @At("HEAD"))
    private static void opsec$onLoadStart(ResourceManager resourceManager, List<String> filenames, 
            boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        Opsec.LOGGER.debug("[OpSec] ClientLanguageMixin: Starting language load");
        opsec$loggedOnce = false;
    }
    
    /**
     * Mark initialization complete after loading.
     */
    @Inject(method = "loadFrom", at = @At("RETURN"))
    private static void opsec$onLoadComplete(ResourceManager resourceManager, List<String> filenames,
            boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        ModRegistry.markInitialized();
        
        if (!opsec$loggedOnce) {
            opsec$loggedOnce = true;
            Opsec.LOGGER.debug("[OpSec] Translation key tracking: {} vanilla, {} server pack, {} total",
                ModRegistry.getVanillaKeyCount(),
                ModRegistry.getServerPackKeyCount(),
                ModRegistry.getTranslationKeyCount());
        }
    }
    
    /**
     * Intercept language file loading to track keys by source.
     * Uses instanceof checks for reliable pack type detection.
     */
    @WrapOperation(
        method = "appendFrom",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"),
        require = 0
    )
    private static void opsec$trackTranslationKeys(
            InputStream stream, 
            BiConsumer<String, String> output, 
            Operation<Void> original,
            @Local Resource resource) {
        
        PackResources pack = resource.source();
        
        // Vanilla pack - always whitelist
        if (pack instanceof VanillaPackResources) {
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModRegistry.recordVanillaTranslationKey(key);
                output.accept(key, value);
            });
            return;
        }
        
        // Try to detect Fabric mod pack FIRST via reflection (avoids hard dependency on internal classes)
        // Works with both old ModNioResourcePack and new ModResourcePack implementations
        // Must check this before PathPackResources since mod packs may extend it
        String modId = opsec$getModIdFromPack(pack);
        if (modId != null) {
            Set<String> modKeys = new HashSet<>();
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModRegistry.recordTranslationKey(modId, key);
                modKeys.add(key);
                output.accept(key, value);
            });
            
            Opsec.LOGGER.debug("[OpSec] Tracked {} translation keys from mod '{}'", modKeys.size(), modId);
            return;
        }
        
        // Server resource pack (downloaded) or composite pack - session whitelist
        // These are packs the server requires, clean clients resolve them normally
        if (pack instanceof FilePackResources || pack instanceof CompositePackResources) {
            Set<String> serverKeys = new HashSet<>();
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModRegistry.recordServerPackTranslationKey(key);
                serverKeys.add(key);
                output.accept(key, value);
            });
            
            if (!serverKeys.isEmpty()) {
                Opsec.LOGGER.debug("[OpSec] Whitelisted {} server pack translation keys", serverKeys.size());
            }
            return;
        }
        
        // Path pack resources - try to extract mod ID from pack ID as fallback
        if (pack instanceof PathPackResources) {
            // Try to get mod ID from pack ID (format is usually "mod_id" or similar)
            String packId = pack.packId();
            if (packId != null && !packId.isEmpty() && !packId.equals("vanilla") && !packId.startsWith("file/")) {
                // Clean up pack ID - remove common prefixes/suffixes
                String extractedModId = packId.replace("fabric/", "").replace("mod/", "");
                if (!extractedModId.isEmpty() && !extractedModId.startsWith("fabric-")) {
                    Set<String> modKeys = new HashSet<>();
                    final String finalModId = extractedModId;
                    original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                        ModRegistry.recordTranslationKey(finalModId, key);
                        modKeys.add(key);
                        output.accept(key, value);
                    });
                    Opsec.LOGGER.debug("[OpSec] Tracked {} translation keys from pack '{}' (mod: {})", 
                        modKeys.size(), packId, extractedModId);
                    return;
                }
            }
            // Fallback - passthrough without tracking
            original.call(stream, output);
            return;
        }
        
        // Completely unknown pack type - passthrough but log warning
        Opsec.LOGGER.debug("[OpSec] Unknown pack type: {} - passing through without tracking", 
            pack.getClass().getName());
        original.call(stream, output);
    }
    
    /**
     * Try to get mod ID from a pack using multiple detection methods.
     */
    @Unique
    private static String opsec$getModIdFromPack(PackResources pack) {
        if (pack == null) return null;
        
        // Method 1: Try reflection for Fabric's mod resource packs (getFabricModMetadata)
        try {
            var method = pack.getClass().getMethod("getFabricModMetadata");
            var metadata = method.invoke(pack);
            if (metadata != null) {
                var getIdMethod = metadata.getClass().getMethod("getId");
                String id = (String) getIdMethod.invoke(metadata);
                if (id != null) return id;
            }
        } catch (Exception e) {
            // Not a Fabric mod pack or reflection failed
        }
        
        // Method 2: Try getModMetadata (different Fabric versions)
        try {
            var method = pack.getClass().getMethod("getModMetadata");
            var metadata = method.invoke(pack);
            if (metadata != null) {
                var getIdMethod = metadata.getClass().getMethod("getId");
                String id = (String) getIdMethod.invoke(metadata);
                if (id != null) return id;
            }
        } catch (Exception e) {
            // Not available
        }
        
        // Method 3: Try getId directly on the pack
        try {
            var method = pack.getClass().getMethod("getId");
            Object result = method.invoke(pack);
            if (result instanceof String id && !id.isEmpty()) {
                return id;
            }
        } catch (Exception e) {
            // Not available
        }
        
        // Method 4: Fall back to class-based detection
        return ModIdResolver.getModIdFromClass(pack.getClass());
    }
}

package aurick.opsec.mod.mixin.client;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor for ClientLanguage's translation storage map.
 * Replaces fragile reflection with mapping-safe Mixin accessor.
 */
@Mixin(ClientLanguage.class)
public interface ClientLanguageAccessor {

    @Accessor("storage")
    Map<String, String> opsec$getStorage();
}

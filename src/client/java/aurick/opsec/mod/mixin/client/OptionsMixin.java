package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to track vanilla keybinds when Options is initialized.
 */
@Mixin(Options.class)
public class OptionsMixin {
    
    @Shadow @Final public KeyMapping keyUp;
    @Shadow @Final public KeyMapping keyDown;
    @Shadow @Final public KeyMapping keyLeft;
    @Shadow @Final public KeyMapping keyRight;
    @Shadow @Final public KeyMapping keyJump;
    @Shadow @Final public KeyMapping keyShift;
    @Shadow @Final public KeyMapping keySprint;
    @Shadow @Final public KeyMapping keyInventory;
    @Shadow @Final public KeyMapping keyDrop;
    @Shadow @Final public KeyMapping keyChat;
    @Shadow @Final public KeyMapping keyPlayerList;
    @Shadow @Final public KeyMapping keyPickItem;
    @Shadow @Final public KeyMapping keyCommand;
    @Shadow @Final public KeyMapping keyScreenshot;
    @Shadow @Final public KeyMapping keyTogglePerspective;
    @Shadow @Final public KeyMapping keySmoothCamera;
    @Shadow @Final public KeyMapping keyFullscreen;
    @Shadow @Final public KeyMapping keySpectatorOutlines;
    @Shadow @Final public KeyMapping keySwapOffhand;
    @Shadow @Final public KeyMapping keySaveHotbarActivator;
    @Shadow @Final public KeyMapping keyLoadHotbarActivator;
    @Shadow @Final public KeyMapping keyAdvancements;
    @Shadow @Final public KeyMapping[] keyHotbarSlots;
    @Shadow @Final public KeyMapping keyAttack;
    @Shadow @Final public KeyMapping keyUse;
    @Shadow @Final public KeyMapping keySocialInteractions;
    
    @Unique
    private static boolean opsec$trackedVanillaKeybinds = false;
    
    /**
     * Track vanilla keybinds after Options is loaded.
     */
    @Inject(method = "load()V", at = @At("RETURN"), require = 0)
    private void opsec$trackVanillaKeybinds(CallbackInfo ci) {
        if (opsec$trackedVanillaKeybinds) return;
        opsec$trackedVanillaKeybinds = true;
        
        // Track all vanilla keybinds
        opsec$recordKeybind(keyUp);
        opsec$recordKeybind(keyDown);
        opsec$recordKeybind(keyLeft);
        opsec$recordKeybind(keyRight);
        opsec$recordKeybind(keyJump);
        opsec$recordKeybind(keyShift);
        opsec$recordKeybind(keySprint);
        opsec$recordKeybind(keyInventory);
        opsec$recordKeybind(keyDrop);
        opsec$recordKeybind(keyChat);
        opsec$recordKeybind(keyPlayerList);
        opsec$recordKeybind(keyPickItem);
        opsec$recordKeybind(keyCommand);
        opsec$recordKeybind(keyScreenshot);
        opsec$recordKeybind(keyTogglePerspective);
        opsec$recordKeybind(keySmoothCamera);
        opsec$recordKeybind(keyFullscreen);
        opsec$recordKeybind(keySpectatorOutlines);
        opsec$recordKeybind(keySwapOffhand);
        opsec$recordKeybind(keySaveHotbarActivator);
        opsec$recordKeybind(keyLoadHotbarActivator);
        opsec$recordKeybind(keyAdvancements);
        opsec$recordKeybind(keySocialInteractions);
        opsec$recordKeybind(keyAttack);
        opsec$recordKeybind(keyUse);
        
        // Track hotbar slots
        for (KeyMapping hotbarKey : keyHotbarSlots) {
            opsec$recordKeybind(hotbarKey);
        }
        
        Opsec.LOGGER.debug("[OpSec] Tracked {} vanilla keybinds", ModRegistry.getKeybindCount());
    }
    
    @Unique
    private void opsec$recordKeybind(KeyMapping keyMapping) {
        if (keyMapping != null) {
            ModRegistry.recordVanillaKeybind(keyMapping.getName());
        }
    }
}


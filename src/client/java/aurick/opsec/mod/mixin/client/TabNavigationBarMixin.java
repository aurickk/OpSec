package aurick.opsec.mod.mixin.client;

//? if <1.20.5 {
/*
import aurick.opsec.mod.config.OpsecConfigScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Pre-1.20.5: TabNavigationBar.render paints a solid-black rectangle from y=0 to y=24
// behind the tab buttons. 1.20.6+ removed that fill so the screen's renderBackground
// (dirt) shows through behind the buttons. Suppress the fill only when the OpSec
// config screen is the active screen so vanilla CreateWorldScreen and friends keep
// their original visual.
@Mixin(TabNavigationBar.class)
public class TabNavigationBarMixin {

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V")
    )
    private void opsec$skipBlackHeaderFill(GuiGraphics gfx, int x1, int y1, int x2, int y2, int color,
            Operation<Void> original) {
        if (Minecraft.getInstance().screen instanceof OpsecConfigScreen) return;
        original.call(gfx, x1, y1, x2, y2, color);
    }
}
*///?} else {
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;

// 1.20.5+: TabNavigationBar.render no longer paints a solid-black header background
// behind the tab buttons (the dirt renderBackground shows through naturally), so
// nothing to suppress. Empty mixin into a stable always-present class so the entry
// stays in opsec.client.mixins.json across all versions.
@Mixin(PacketUtils.class)
public class TabNavigationBarMixin {
}
//?}

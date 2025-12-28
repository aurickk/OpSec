package incognito.mod.mixin.client;

import incognito.mod.config.IncognitoConfig;
import incognito.mod.config.IncognitoConfigScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add an Incognito settings button to the multiplayer server list screen.
 * 
 * The button is draggable via right-click and position is saved to config.
 * Uses 1.21.10 MouseButtonEvent API.
 * Snaps to header/footer areas to avoid covering the server list.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    
    // Layout constants for snapping
    @Unique
    private static final int HEADER_HEIGHT = 32;  // Title area
    @Unique
    private static final int FOOTER_HEIGHT = 80;  // Button area at bottom (two rows of buttons + padding)
    
    @Unique
    private Button incognito$button;
    
    @Unique
    private boolean incognito$dragging = false;
    
    @Unique
    private int incognito$dragOffsetX = 0;
    
    @Unique
    private int incognito$dragOffsetY = 0;
    
    @Unique
    private int incognito$buttonXOffset = 0;  // X offset from right edge
    
    @Unique
    private int incognito$buttonYOffset = 0;  // Y offset (from top or bottom)
    
    @Unique
    private boolean incognito$isInFooter = false;  // true = footer, false = header
    
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void incognito$addSettingsButton(CallbackInfo ci) {
        // Load saved position or use default (top-left with padding)
        int[] savedPos = IncognitoConfig.getInstance().getSettings().getButtonPosition();
        
        if (savedPos == null) {
            incognito$buttonXOffset = this.width - 5;  // Default to top-left (far from right edge)
            incognito$buttonYOffset = 5;
            incognito$isInFooter = false;
        } else {
            int savedX = savedPos[0];
            int savedY = savedPos[1];
            
            // X is always relative to right edge
            incognito$buttonXOffset = this.width - savedX;
            
            // Y uses header/footer zones
            int footerTopY = this.height - FOOTER_HEIGHT;
            int middleY = (HEADER_HEIGHT + footerTopY) / 2;
            
            if (savedY >= middleY) {
                incognito$isInFooter = true;
                incognito$buttonYOffset = this.height - savedY;
            } else {
                incognito$isInFooter = false;
                incognito$buttonYOffset = savedY;
            }
        }
        
        int buttonX = incognito$calculateButtonX();
        int buttonY = incognito$calculateButtonY();
        
        incognito$button = Button.builder(
            Component.literal("Incognito"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new IncognitoConfigScreen(this));
                }
            }
        ).bounds(buttonX, buttonY, 70, 20).build();
        
        incognito$button.setTooltip(Tooltip.create(Component.literal("§eLeft-click: §fOpen settings\n§eRight-drag: §fMove button")));
        
        this.addRenderableWidget(incognito$button);
    }
    
    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void incognito$repositionButton(CallbackInfo ci) {
        if (incognito$button != null) {
            int finalX = incognito$calculateButtonX();
            int finalY = incognito$calculateButtonY();
            
            incognito$button.setX(finalX);
            incognito$button.setY(finalY);
        }
    }
    
    
    /**
     * Calculates the button Y position based on zone and offset.
     * Header: offset from top
     * Footer: offset from bottom
     */
    @Unique
    private int incognito$calculateButtonY() {
        int buttonHeight = 20;
        
        if (incognito$isInFooter) {
            // Footer zone - calculate from bottom
            int footerContentTopY = this.height - 58;
            int footerContentBottomY = this.height - 8;
            int targetY = this.height - incognito$buttonYOffset;
            // Clamp within footer content area
            return Math.max(footerContentTopY, Math.min(targetY, footerContentBottomY - buttonHeight));
        } else {
            // Header zone - offset from top
            int targetY = incognito$buttonYOffset;
            // Clamp within header area
            return Math.max(0, Math.min(targetY, HEADER_HEIGHT - buttonHeight));
        }
    }
    
    @Unique
    private int incognito$calculateButtonX() {
        int buttonWidth = 70;
        int targetX = this.width - incognito$buttonXOffset;
        return Math.max(0, Math.min(targetX, this.width - buttonWidth));
    }
    
    
    // Handle right-click drag using 1.21.10 MouseButtonEvent API
    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && incognito$button != null && incognito$button.isMouseOver(event.x(), event.y())) {
            incognito$dragging = true;
            incognito$dragOffsetX = (int) event.x() - incognito$button.getX();
            incognito$dragOffsetY = (int) event.y() - incognito$button.getY();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    
    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        if (event.button() == 1 && incognito$dragging) {
            incognito$dragging = false;
            if (incognito$button != null) {
                int snappedY = incognito$snapToHeaderOrFooter(incognito$button.getY());
                incognito$button.setY(snappedY);
                
                int clampedX = Math.max(0, Math.min(incognito$button.getX(), this.width - incognito$button.getWidth()));
                incognito$button.setX(clampedX);
                
                int footerTopY = this.height - FOOTER_HEIGHT;
                int middleY = (HEADER_HEIGHT + footerTopY) / 2;
                
                if (snappedY >= middleY) {
                    incognito$isInFooter = true;
                    incognito$buttonYOffset = this.height - snappedY;
                } else {
                    incognito$isInFooter = false;
                    incognito$buttonYOffset = snappedY;
                }
                
                incognito$buttonXOffset = this.width - clampedX;
                
                IncognitoConfig.getInstance().getSettings().setButtonPosition(clampedX, snappedY);
                IncognitoConfig.getInstance().save();
            }
            return true;
        }
        return super.mouseReleased(event);
    }
    
    /**
     * Snaps the button Y position to either the header or footer area,
     * whichever is closer, to prevent it from blocking the server list.
     * Allows free movement within those zones.
     */
    @Unique
    private int incognito$snapToHeaderOrFooter(int currentY) {
        int buttonHeight = incognito$button != null ? incognito$button.getHeight() : 20;
        // Footer content area (where the actual buttons are)
        int footerContentTopY = this.height - 58;  // Align with top of footer button area
        int footerContentBottomY = this.height - 8;  // Small padding from bottom edge
        
        // Footer zone for determining which area to snap to
        int footerZoneTopY = this.height - FOOTER_HEIGHT;
        
        // Calculate center of screen (middle of server list area)
        int middleY = (HEADER_HEIGHT + footerZoneTopY) / 2;
        
        // Snap to whichever zone is closer, but allow movement within that zone
        if (currentY < middleY) {
            // Closer to header - clamp within header area
            return Math.max(0, Math.min(currentY, HEADER_HEIGHT - buttonHeight));
        } else {
            // Closer to footer - clamp within footer content area
            return Math.max(footerContentTopY, Math.min(currentY, footerContentBottomY - buttonHeight));
        }
    }
        
        @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent event, double dragX, double dragY) {
        if (incognito$dragging && event.button() == 1 && incognito$button != null) {
            int newX = (int) event.x() - incognito$dragOffsetX;
            int newY = (int) event.y() - incognito$dragOffsetY;
            
            // Clamp to screen bounds
            newX = Math.max(0, Math.min(newX, this.width - incognito$button.getWidth()));
            newY = Math.max(0, Math.min(newY, this.height - incognito$button.getHeight()));
            
            incognito$button.setX(newX);
            incognito$button.setY(newY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }
    
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
            
        // Draw visual feedback
        if (incognito$button != null) {
            if (incognito$dragging) {
                graphics.fill(incognito$button.getX(), incognito$button.getY(), 
                    incognito$button.getX() + incognito$button.getWidth(), 
                    incognito$button.getY() + incognito$button.getHeight(), 0x40FFFFFF);
            } else if (incognito$button.isHovered()) {
                graphics.fill(incognito$button.getX(), incognito$button.getY(), 
                    incognito$button.getX() + incognito$button.getWidth(), 
                    incognito$button.getY() + incognito$button.getHeight(), 0x30FFFFFF);
            }
        }
    }
}

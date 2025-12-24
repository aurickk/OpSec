package incognito.mod.mixin.client;

import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

/**
 * Mixin to gray out the "Only Show Secure Chat" option.
 * 
 * When chat signing is disabled, there are no signatures to verify, so this option
 * serves no purpose. We disable it to avoid confusion.
 * 
 * Based on No Chat Reports by Aizistral-Studios:
 * https://github.com/Aizistral-Studios/No-Chat-Reports
 */
@Mixin(ChatOptionsScreen.class)
public abstract class ChatOptionsScreenMixin extends OptionsSubScreen {
    
    protected ChatOptionsScreenMixin(Screen parent, net.minecraft.client.Options options, Component title) {
        super(parent, options, title);
    }
    
    /**
     * Override init to disable the "Only Show Secure Chat" option after super.init().
     */
    @Override
    protected void init() {
        super.init();
        
        // Disable option when not signing (OFF, or ON_DEMAND without tempSign)
        if (IncognitoConfig.getInstance().shouldNotSign()) {
            // Find and disable the onlyShowSecureChat option
            Optional.ofNullable(this.list.findOption(Minecraft.getInstance().options.onlyShowSecureChat()))
                .ifPresent(option -> option.active = false);
        }
    }
}


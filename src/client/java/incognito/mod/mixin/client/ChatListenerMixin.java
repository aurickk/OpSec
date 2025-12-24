package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.ChatTrustLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

/**
 * Mixin to hide chat message trust indicators.
 * 
 * Removes "Not Secure" (orange badge) and "Modified" indicators from chat messages.
 * These indicators serve no purpose when chat signing is disabled and can be annoying.
 * 
 * Based on No Chat Reports by Aizistral-Studios:
 * https://github.com/Aizistral-Studios/No-Chat-Reports
 */
@Mixin(ChatListener.class)
public class ChatListenerMixin {
    
    /**
     * Intercept trust level evaluation to hide message indicators.
     * 
     * By returning ChatTrustLevel.SECURE instead of NOT_SECURE or MODIFIED,
     * we prevent the client from showing those indicators.
     */
    @Inject(method = "evaluateTrustLevel", at = @At("RETURN"), cancellable = true)
    private void incognito$hideTrustIndicators(PlayerChatMessage playerChatMessage, Component component, 
            Instant instant, CallbackInfoReturnable<ChatTrustLevel> info) {
        ChatTrustLevel originalLevel = info.getReturnValue();
        
        if (originalLevel == ChatTrustLevel.NOT_SECURE && 
                IncognitoConfig.getInstance().shouldHideInsecureIndicators()) {
            Incognito.LOGGER.debug("[Incognito] Hiding NOT_SECURE indicator on chat message");
            info.setReturnValue(ChatTrustLevel.SECURE);
        } else if (originalLevel == ChatTrustLevel.MODIFIED && 
                IncognitoConfig.getInstance().shouldHideModifiedIndicators()) {
            Incognito.LOGGER.debug("[Incognito] Hiding MODIFIED indicator on chat message");
            info.setReturnValue(ChatTrustLevel.SECURE);
        }
    }
}


package aurick.opsec.mod.mixin.client;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for Minecraft client fields needed for account switching and cache management.
 * Allows setting session, profile keys, and related services when logging into a different account.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    
    @Mutable
    @Accessor("user")
    void opsec$setUser(User user);
    
    @Mutable
    @Accessor("profileKeyPairManager")
    void opsec$setProfileKeyPairManager(ProfileKeyPairManager manager);
    
    @Mutable
    @Accessor("userApiService")
    void opsec$setUserApiService(UserApiService service);
    
    @Mutable
    @Accessor("playerSocialManager")
    void opsec$setPlayerSocialManager(PlayerSocialManager manager);
    
    @Accessor("downloadedPackSource")
    DownloadedPackSource opsec$getDownloadedPackSource();
}

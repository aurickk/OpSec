package aurick.opsec.mod.mixin.client;

import net.minecraft.client.resources.server.DownloadedPackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for DownloadedPackSource to allow clearing download state.
 */
@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {
    
    /**
     * Invokes the cleanup method that clears download state.
     * This is normally called when disconnecting from a server.
     */
    @Invoker("cleanupAfterDisconnect")
    void opsec$invokeCleanupAfterDisconnect();
}

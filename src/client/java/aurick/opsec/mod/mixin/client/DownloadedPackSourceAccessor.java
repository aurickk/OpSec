package aurick.opsec.mod.mixin.client;

//? if >=1.20.5 {
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
//?} else {
/*
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;

// 1.20.4 and below: DownloadedPackSource lives at a different package path and the
// cleanupAfterDisconnect lifecycle isn't the same — disconnect-cleanup on this era
// happens elsewhere. Empty mixin on a stable always-present class.
@Mixin(PacketUtils.class)
public interface DownloadedPackSourceAccessor {
}
*///?}

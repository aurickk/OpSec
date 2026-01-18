package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks server connection events for OpSec.
 * Handles secure chat enforcement detection (based on NoPryingEyes).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    
    @Unique
    private static volatile ScheduledExecutorService opsec$scheduler;
    
    @Unique
    private static volatile ScheduledFuture<?> opsec$pendingTask;
    
    @Unique
    private static synchronized ScheduledExecutorService opsec$getScheduler() {
        if (opsec$scheduler == null || opsec$scheduler.isShutdown()) {
            opsec$scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OpSec-Scheduler");
            t.setDaemon(true);
            return t;
        });
        }
        return opsec$scheduler;
    }
    
    @Unique
    private static synchronized void opsec$shutdownScheduler() {
        if (opsec$pendingTask != null) {
            opsec$pendingTask.cancel(false);
            opsec$pendingTask = null;
        }
        if (opsec$scheduler != null && !opsec$scheduler.isShutdown()) {
            opsec$scheduler.shutdownNow();
            opsec$scheduler = null;
        }
    }
    
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener)(Object)this;
        String serverAddress = listener.getConnection().getRemoteAddress().toString();
        
        if (serverAddress.startsWith("/")) {
            serverAddress = serverAddress.substring(1);
        }
        int colonIndex = serverAddress.lastIndexOf(':');
        if (colonIndex > 0) {
            serverAddress = serverAddress.substring(0, colonIndex);
        }
        
        OpsecConfig.getInstance().setCurrentServer(serverAddress);
        
        // Check if server enforces secure chat and handle ON_DEMAND signing
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (packet.enforcesSecureChat()) {
            Opsec.LOGGER.debug("[OpSec] Server enforces secure chat");
            
            if (settings.isOnDemand()) {
                // Enable signing for this session
                settings.setTempSign(true);
                
                // Show warning toast and message
                if (!settings.isSigningToastShown()) {
                    settings.setSigningToastShown(true);
                    PrivacyLogger.alertSecureChatRequired(serverAddress);
                }
            }
        }
        
        // Schedule port scan summary after 2 seconds
        opsec$pendingTask = opsec$getScheduler().schedule(() -> {
            Minecraft.getInstance().execute(PrivacyLogger::showPortScanSummary);
        }, 2, TimeUnit.SECONDS);
    }
    
    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        opsec$shutdownScheduler();
        PrivacyLogger.resetPortScanTracking();
        PrivacyLogger.clearCooldowns();
        OpsecConfig.getInstance().setCurrentServer(null);
        OpsecConfig.getInstance().getSettings().resetSessionState();
    }
}

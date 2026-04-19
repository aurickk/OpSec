package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.tracking.ModRegistry;
import aurick.opsec.mod.util.LocalAddressUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static aurick.opsec.mod.config.OpsecConstants.Channels.*;

/**
 * Intercepts and filters outgoing custom payloads for brand spoofing and channel filtering.
 * Also tracks server address for LAN detection.
 */
@Mixin(Connection.class)
public class ClientConnectionMixin {
    
    @Shadow
    private Channel channel;
    
    @Inject(method = "channelActive", at = @At("HEAD"))
    private void opsec$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        try {
            if (context.channel() != null && context.channel().remoteAddress() != null) {
                java.net.SocketAddress addr = context.channel().remoteAddress();
                if (addr instanceof java.net.InetSocketAddress inetSocketAddress) {
                    LocalAddressUtil.serverAddress = inetSocketAddress.getAddress().getHostAddress();
                    Opsec.LOGGER.debug("[OpSec] Connected to server: {}", LocalAddressUtil.serverAddress);
                } else {
                    LocalAddressUtil.serverAddress = null;
                }
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Failed to track server address on connect: {}", e.getMessage());
            LocalAddressUtil.serverAddress = null;
        }
    }
    
    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void opsec$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        Opsec.LOGGER.debug("[OpSec] Disconnected from server");
        LocalAddressUtil.serverAddress = null;
    }
    
    @Unique
    private static final AtomicBoolean opsec$logged = new AtomicBoolean(false);
    
    @Unique
    private volatile boolean opsec$pipelineHandlerInstalled = false;
    
    @Unique
    private static final ThreadLocal<Boolean> opsec$sending = ThreadLocal.withInitial(() -> false);
    
    @Unique
    private void opsec$ensurePipelineHandler() {
        if (opsec$pipelineHandlerInstalled || channel == null) {
            return;
        }
        
        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get("opsec_filter") == null) {
                pipeline.addAfter("encoder", "opsec_filter", new OpsecPacketFilter());
                opsec$pipelineHandlerInstalled = true;
                Opsec.LOGGER.debug("[OpSec] Installed Netty pipeline filter (after encoder)");
            }
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to install pipeline handler: {}", e.getMessage(), e);
        }
    }
    
    @Unique
    private static class OpsecPacketFilter extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!(msg instanceof Packet<?> packet)) {
                ctx.write(msg, promise);
                return;
            }
            
            if (!(packet instanceof ServerboundCustomPayloadPacket customPayloadPacket)) {
                ctx.write(msg, promise);
                return;
            }
            
            CustomPacketPayload payload = customPayloadPacket.payload();
            //? if >=1.21.11 {
            /*Identifier payloadId = payload.type().id();*/
            //?} else {
            ResourceLocation payloadId = payload.type().id();
            //?}
            
            // ALWAYS track channels from minecraft:register packets for whitelist support
            // This runs regardless of spoofing settings so /opsec channels is accurate
            if (payload instanceof RegistrationPayload registrationPayload) {
                String namespace = payloadId.getNamespace();
                String path = payloadId.getPath();
                if (MINECRAFT.equals(namespace) && REGISTER.equals(path)) {
                    opsec$trackChannelsFromPayload(registrationPayload.channels());
                }
            }
            
            OpsecConfig config = OpsecConfig.getInstance();
            if (!config.shouldSpoofBrand() || !config.shouldSpoofChannels()) {
                ctx.write(msg, promise);
                return;
            }
            
            if (payload instanceof BrandPayload) {
                ctx.write(msg, promise);
                return;
            }
            
            if (config.getSettings().isVanillaMode()) {
                Opsec.LOGGER.debug("[OpSec] VANILLA MODE (pipeline) - Blocking: {}", payloadId);
                promise.setSuccess();
                return;
            }
            
            if (config.getSettings().isFabricMode()) {
                String namespace = payloadId.getNamespace();
                String path = payloadId.getPath();
                
                if (MINECRAFT.equals(namespace) && (REGISTER.equals(path) || UNREGISTER.equals(path))) {
                    if (payload instanceof RegistrationPayload registrationPayload) {
                        // Use ModRegistry.isWhitelistedChannel which handles:
                        // - minecraft:* channels
                        // - Whitelisted mod channels (including fabric API modules via DEFAULT_FABRIC_MODS)
                        //? if >=1.21.11 {
                        /*List<Identifier> filtered = registrationPayload.channels().stream()*/
                        //?} else {
                        List<ResourceLocation> filtered = registrationPayload.channels().stream()
                        //?}
                            .filter(ModRegistry::isWhitelistedChannel)
                            .toList();
                        
                        Opsec.LOGGER.debug("[OpSec] FABRIC MODE (pipeline) - Filtered channels: {} -> {}", 
                            registrationPayload.channels().size(), filtered.size());
                        
                        if (filtered.isEmpty()) {
                            promise.setSuccess();
                            return;
                        }
                        
                        RegistrationPayload newPayload = opsec$createRegistrationPayload(registrationPayload, new ArrayList<>(filtered));
                        if (newPayload != null) {
                            ctx.write(new ServerboundCustomPayloadPacket(newPayload), promise);
                            return;
                        }
                    }
                    promise.setSuccess();
                    return;
                }
                
                if (MINECRAFT.equals(payloadId.getNamespace())) {
                    ctx.write(msg, promise);
                    return;
                }
                
                // Allow whitelisted mod channels (includes fabric API modules via DEFAULT_FABRIC_MODS)
                if (ModRegistry.isWhitelistedChannel(payloadId)) {
                    ctx.write(msg, promise);
                    return;
                }
                
                Opsec.LOGGER.debug("[OpSec] FABRIC MODE (pipeline) - Blocking mod channel: {}", payloadId);
                promise.setSuccess();
                return;
            }
            
            ctx.write(msg, promise);
        }
    }
    
    
    @Inject(method = "configurePacketHandler", at = @At("TAIL"))
    private void onConfigurePacketHandler(CallbackInfo ci) {
        opsec$ensurePipelineHandler();
    }
    
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        opsec$ensurePipelineHandler();
        
        if (opsec$logged.compareAndSet(false, true)) {
            Opsec.LOGGER.debug("[OpSec] Connection.send mixin active!");
        }
        handleOutgoingPacket(packet, ci, (Connection)(Object)this);
    }
    
    
    @Unique
    private void handleOutgoingPacket(Packet<?> packet, CallbackInfo ci, Connection connection) {
        if (opsec$sending.get()) {
            return;
        }
        
        // Keybind protection is handled globally by KeybindContentsMixin
        // This handler only processes brand/channel spoofing
        
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayloadPacket)) {
            return;
        }
        
        CustomPacketPayload payload = customPayloadPacket.payload();
        //? if >=1.21.11 {
        /*Identifier payloadId = payload.type().id();*/
        //?} else {
        ResourceLocation payloadId = payload.type().id();
        //?}
        
        // ALWAYS track channels from minecraft:register packets for whitelist support
        // This runs regardless of spoofing settings so /opsec channels is accurate
        if (payload instanceof RegistrationPayload registrationPayload) {
            String namespace = payloadId.getNamespace();
            String path = payloadId.getPath();
            if (MINECRAFT.equals(namespace) && REGISTER.equals(path)) {
                opsec$trackChannelsFromPayload(registrationPayload.channels());
            }
        }
        
        OpsecConfig config = OpsecConfig.getInstance();
        if (!config.shouldSpoofBrand()) {
            return;
        }

        if (!config.shouldSpoofChannels()) {
            return;
        }
        
        if (payload instanceof BrandPayload) {
            return;
        }
        
        if (config.getSettings().isVanillaMode()) {
            Opsec.LOGGER.debug("[OpSec] VANILLA MODE - Blocking: {}", payloadId);
            ci.cancel();
            return;
        }
        
        if (config.getSettings().isFabricMode()) {
            String namespace = payloadId.getNamespace();
            String path = payloadId.getPath();
            
            if (MINECRAFT.equals(namespace) && (REGISTER.equals(path) || UNREGISTER.equals(path))) {
                if (payload instanceof RegistrationPayload registrationPayload) {
                    opsec$handleMinecraftRegisterForFabric(registrationPayload, ci, connection);
                } else {
                    Opsec.LOGGER.warn("[OpSec] Blocking unknown {}: {}", 
                        payloadId, payload.getClass().getName());
                    ci.cancel();
                }
                return;
            }
            
            if (MINECRAFT.equals(namespace) && MCO.equals(path)) {
                Opsec.LOGGER.debug("[OpSec] Blocking minecraft:mco to match stock Fabric");
                ci.cancel();
                return;
            }
            
            if (MINECRAFT.equals(namespace)) {
                return;
            }
            
            // Allow whitelisted mod channels (includes fabric API modules via DEFAULT_FABRIC_MODS)
            if (ModRegistry.isWhitelistedChannel(payloadId)) {
                return;
            }
            
            Opsec.LOGGER.debug("[OpSec] FABRIC MODE - Blocking mod channel: {}", payloadId);
            ci.cancel();
            return;
        }
        
    }
    
    @Unique
    private void opsec$handleMinecraftRegisterForFabric(RegistrationPayload original, CallbackInfo ci, Connection connection) {
        //? if >=1.21.11 {
        /*List<Identifier> originalChannels = original.channels();*/
        //?} else {
        List<ResourceLocation> originalChannels = original.channels();
        //?}
        
        // Track ALL channels before filtering - this is what the server would see
        opsec$trackAllChannels(originalChannels);
        
        // Use ModRegistry.isWhitelistedChannel which handles:
        // - minecraft:* channels
        // - Whitelisted mod channels (including fabric API modules via DEFAULT_FABRIC_MODS)
        //? if >=1.21.11 {
        /*List<Identifier> filteredChannels = originalChannels.stream()*/
        //?} else {
        List<ResourceLocation> filteredChannels = originalChannels.stream()
        //?}
            .filter(ModRegistry::isWhitelistedChannel)
            .toList();
        
        Opsec.LOGGER.debug("[OpSec] FABRIC MODE - Filtered channels: {} -> {}", 
            originalChannels.size(), filteredChannels.size());
        
        ci.cancel();
        
        if (filteredChannels.isEmpty()) {
            return;
        }
        
        RegistrationPayload filteredPayload = opsec$createRegistrationPayload(original, new ArrayList<>(filteredChannels));
        if (filteredPayload == null) {
            Opsec.LOGGER.error("[OpSec] Could not create filtered RegistrationPayload");
            return;
        }
        
        opsec$sending.set(true);
        try {
            connection.send(new ServerboundCustomPayloadPacket(filteredPayload));
        } finally {
            opsec$sending.set(false);
        }
    }
    
    /**
     * Track ALL channels from the minecraft:register packet.
     * This is the authoritative source - exactly what the server would see.
     */
    @Unique
    //? if >=1.21.11 {
    /*private void opsec$trackAllChannels(List<Identifier> channels) {*/
    //?} else {
    private void opsec$trackAllChannels(List<ResourceLocation> channels) {
    //?}
        opsec$trackChannelsFromPayload(channels);
    }
    
    /**
     * Static method to track channels - can be called from both instance and static contexts.
     */
    @Unique
    //? if >=1.21.11 {
    /*private static void opsec$trackChannelsFromPayload(List<Identifier> channels) {*/
    //?} else {
    private static void opsec$trackChannelsFromPayload(List<ResourceLocation> channels) {
    //?}
        if (channels == null || channels.isEmpty()) return;
        Opsec.LOGGER.debug("[OpSec] Tracking {} channels from minecraft:register packet", channels.size());
        //? if >=1.21.11 {
        /*for (Identifier channel : channels) {*/
        //?} else {
        for (ResourceLocation channel : channels) {
        //?}
            String namespace = channel.getNamespace();

            // Skip core channels (minecraft + Forge "common" alias)
            if ("minecraft".equals(namespace) || "c".equals(namespace)) {
                continue;
            }

            // Resolve namespace to actual mod ID(s).
            // NOTE: Do NOT use ModIdResolver.getModIdFromStacktrace() here -- this runs during
            // packet processing, not mod registration, so the stack trace would show Netty/MC/OpSec
            // frames, not the registering mod's frames.
            Set<String> resolvedModIds = ModRegistry.resolveModIdsForNamespace(namespace);
            if (!resolvedModIds.isEmpty()) {
                for (String modId : resolvedModIds) {
                    ModRegistry.recordChannel(modId, channel);
                }
            } else {
                // Fall back to namespace as mod ID (backwards compat)
                ModRegistry.recordChannel(namespace, channel);
            }
        }
    }
    
    @Unique
    //? if >=1.21.11 {
    /*private static RegistrationPayload opsec$createRegistrationPayload(RegistrationPayload original, List<Identifier> channels) {*/
    //?} else {
    private static RegistrationPayload opsec$createRegistrationPayload(RegistrationPayload original, List<ResourceLocation> channels) {
    //?}
        try {
            for (Constructor<?> constructor : RegistrationPayload.class.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 2) {
                    constructor.setAccessible(true);
                    try {
                        //? if >=26.1 {
                        /*return (RegistrationPayload) constructor.newInstance(original.type(), channels);*/
                        //?} else {
                        return (RegistrationPayload) constructor.newInstance(original.id(), channels);
                        //?}
                    } catch (Exception e1) {
                        try {
                            //? if >=26.1 {
                            /*return (RegistrationPayload) constructor.newInstance(channels, original.type());*/
                            //?} else {
                            return (RegistrationPayload) constructor.newInstance(channels, original.id());
                            //?}
                        } catch (Exception e2) {
                            Opsec.LOGGER.debug("[OpSec] Failed parameter order attempt: {}", e2.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to create RegistrationPayload: {}", e.getMessage(), e);
        }
        Opsec.LOGGER.warn("[OpSec] Unable to create RegistrationPayload - no compatible constructor found");
        return null;
    }
}

package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Mixin to intercept and filter outgoing custom payloads.
 * 
 * Key behaviors:
 * - VANILLA MODE: Block ALL custom payloads (no channels at all)
 * - FABRIC MODE: Intercept minecraft:register packets and rebuild them with ONLY fabric channels.
 *   This makes the client appear as a clean Fabric client with no third-party mods.
 * - FORGE MODE: Register only forge:login and forge:handshake channels.
 *   This makes the client appear as a clean Forge client.
 */
@Mixin(Connection.class)
public class ClientConnectionMixin {
    
    @Shadow
    private Channel channel;
    
    @Unique
    private static boolean incognito$logged = false;
    
    @Unique
    private boolean incognito$pipelineHandlerInstalled = false;
    
    @Unique
    private static final ThreadLocal<Boolean> incognito$sending = ThreadLocal.withInitial(() -> false);
    
    @Unique
    private void incognito$ensurePipelineHandler() {
        if (incognito$pipelineHandlerInstalled || channel == null) {
            return;
        }
        
        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get("incognito_filter") == null) {
                pipeline.addAfter("encoder", "incognito_filter", new IncognitoPacketFilter());
                incognito$pipelineHandlerInstalled = true;
                Incognito.LOGGER.info("[Incognito] Installed Netty pipeline filter (after encoder)");
            }
        } catch (Exception e) {
            Incognito.LOGGER.error("[Incognito] Failed to install pipeline handler: {}", e.getMessage(), e);
        }
    }
    
    @Unique
    private static class IncognitoPacketFilter extends ChannelOutboundHandlerAdapter {
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
            ResourceLocation payloadId = payload.type().id();
            
            IncognitoConfig config = IncognitoConfig.getInstance();
            if (!config.shouldSpoofBrand() || !config.shouldSpoofChannels()) {
                ctx.write(msg, promise);
                return;
            }
            
            if (payload instanceof BrandPayload) {
                ctx.write(msg, promise);
                return;
            }
            
            if (config.getSettings().isVanillaMode()) {
                Incognito.LOGGER.info("[Incognito] VANILLA MODE (pipeline) - Blocking: {}", payloadId);
                promise.setSuccess();
                return;
            }
            
            if (config.getSettings().isFabricMode()) {
                String namespace = payloadId.getNamespace();
                String path = payloadId.getPath();
                
                if ("minecraft".equals(namespace) && ("register".equals(path) || "unregister".equals(path))) {
                    if (payload instanceof RegistrationPayload registrationPayload) {
                        List<ResourceLocation> filtered = registrationPayload.channels().stream()
                            .filter(ch -> {
                                String ns = ch.getNamespace();
                                return "fabric".equals(ns) || ns.startsWith("fabric-");
                            })
                            .toList();
                        
                        Incognito.LOGGER.info("[Incognito] FABRIC MODE (pipeline) - Filtered channels: {} -> {}", 
                            registrationPayload.channels().size(), filtered.size());
                        
                        if (filtered.isEmpty()) {
                            promise.setSuccess();
                            return;
                        }
                        
                        RegistrationPayload newPayload = incognito$createRegistrationPayloadStatic(registrationPayload, new ArrayList<>(filtered));
                        if (newPayload != null) {
                            ctx.write(new ServerboundCustomPayloadPacket(newPayload), promise);
                            return;
                        }
                    }
                    promise.setSuccess();
                    return;
                }
                
                if ("fabric".equals(payloadId.getNamespace()) || payloadId.getNamespace().startsWith("fabric-")) {
                    ctx.write(msg, promise);
                    return;
                }
                
                if ("minecraft".equals(payloadId.getNamespace())) {
                    ctx.write(msg, promise);
                    return;
                }
                
                Incognito.LOGGER.info("[Incognito] FABRIC MODE (pipeline) - Blocking mod channel: {}", payloadId);
                promise.setSuccess();
                return;
            }
            
            if (config.getSettings().isForgeMode()) {
                String namespace = payloadId.getNamespace();
                String path = payloadId.getPath();
                
                if ("minecraft".equals(namespace) && ("register".equals(path) || "unregister".equals(path))) {
                    if (payload instanceof RegistrationPayload registrationPayload) {
                        List<ResourceLocation> forgeChannels = List.of(
                            ResourceLocation.parse("forge:login"),
                            ResourceLocation.parse("forge:handshake")
                        );
                        
                        Incognito.LOGGER.info("[Incognito] FORGE MODE (pipeline) - Replacing {} channels with forge channels", 
                            registrationPayload.channels().size());
                        
                        RegistrationPayload forgePayload = incognito$createRegistrationPayloadStatic(registrationPayload, new ArrayList<>(forgeChannels));
                        if (forgePayload != null) {
                            ctx.write(new ServerboundCustomPayloadPacket(forgePayload), promise);
                            return;
                        }
                    }
                    promise.setSuccess();
                    return;
                }
                
                if ("forge".equals(namespace) && ("login".equals(path) || "handshake".equals(path))) {
                    ctx.write(msg, promise);
                    return;
                }
                
                if ("minecraft".equals(namespace)) {
                    if ("mco".equals(path)) {
                        Incognito.LOGGER.info("[Incognito] FORGE MODE (pipeline) - Blocking minecraft:mco");
                        promise.setSuccess();
                        return;
                    }
                    ctx.write(msg, promise);
                    return;
                }
                
                Incognito.LOGGER.info("[Incognito] FORGE MODE (pipeline) - Blocking non-forge channel: {}", payloadId);
                promise.setSuccess();
                return;
            }
            
            ctx.write(msg, promise);
        }
    }
    
    @Unique
    private static RegistrationPayload incognito$createRegistrationPayloadStatic(RegistrationPayload original, List<ResourceLocation> channels) {
        try {
            for (Constructor<?> constructor : RegistrationPayload.class.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 2) {
                    constructor.setAccessible(true);
                    try {
                        return (RegistrationPayload) constructor.newInstance(original.id(), channels);
                    } catch (Exception e1) {
                        try {
                            return (RegistrationPayload) constructor.newInstance(channels, original.id());
                        } catch (Exception e2) {
                            Incognito.LOGGER.debug("[Incognito] Failed parameter order attempt: {}", e2.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Incognito.LOGGER.error("[Incognito] Failed to create RegistrationPayload: {}", e.getMessage(), e);
        }
        Incognito.LOGGER.warn("[Incognito] Unable to create RegistrationPayload - no compatible constructor found");
        return null;
    }
    
    @Inject(method = "configurePacketHandler", at = @At("TAIL"), require = 0)
    private void onConfigurePacketHandler(CallbackInfo ci) {
        incognito$ensurePipelineHandler();
    }
    
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        incognito$ensurePipelineHandler();
        
        if (!incognito$logged) {
            incognito$logged = true;
            Incognito.LOGGER.info("[Incognito] Connection.send mixin active!");
        }
        handleOutgoingPacket(packet, ci, (Connection)(Object)this);
    }
    
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", 
            at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void onSendWithListener(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        handleOutgoingPacket(packet, ci, (Connection)(Object)this);
    }
    
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", 
            at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void onSendWithFlush(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo ci) {
        handleOutgoingPacket(packet, ci, (Connection)(Object)this);
    }
    
    /**
     * Version-compatible sendPacket method injection for Fabric API 1.21.5+
     */
    @Inject(
        method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        expect = 0
    )
    private void onSendPacketWithListener(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        handleOutgoingPacket(packet, ci, (Connection)(Object)this);
    }
    
    @Inject(
        method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        expect = 0
    )
    private void onSendPacketWithListenerAndFlush(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo ci) {
        handleOutgoingPacket(packet, ci, (Connection)(Object)this);
    }
    
    @Unique
    private void handleOutgoingPacket(Packet<?> packet, CallbackInfo ci, Connection connection) {
        if (incognito$sending.get()) {
            return;
        }
        
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayloadPacket)) {
            return;
        }
        
        IncognitoConfig config = IncognitoConfig.getInstance();
        if (!config.shouldSpoofBrand()) {
            return;
        }

        if (!config.shouldSpoofChannels()) {
            return;
        }
        
        CustomPacketPayload payload = customPayloadPacket.payload();
        ResourceLocation payloadId = payload.type().id();
        
        if (payload instanceof BrandPayload) {
            return;
        }
        
        if (config.getSettings().isVanillaMode()) {
            Incognito.LOGGER.info("[Incognito] VANILLA MODE - Blocking: {}", payloadId);
            ci.cancel();
            return;
        }
        
        if (config.getSettings().isFabricMode()) {
            String namespace = payloadId.getNamespace();
            String path = payloadId.getPath();
            
            if ("minecraft".equals(namespace) && ("register".equals(path) || "unregister".equals(path))) {
                if (payload instanceof RegistrationPayload registrationPayload) {
                    incognito$handleMinecraftRegisterForFabric(registrationPayload, ci, connection);
                } else {
                    Incognito.LOGGER.warn("[Incognito] Blocking unknown {}: {}", 
                        payloadId, payload.getClass().getName());
                    ci.cancel();
                }
                return;
            }
            
            if ("minecraft".equals(namespace) && "mco".equals(path)) {
                Incognito.LOGGER.info("[Incognito] Blocking minecraft:mco to match stock Fabric");
                ci.cancel();
                return;
            }
            
            if ("minecraft".equals(namespace)) {
                return;
            }
            
            if ("fabric".equals(namespace) || namespace.startsWith("fabric-")) {
                return;
            }
            
            if ("c".equals(namespace)) {
                return;
            }
            
            Incognito.LOGGER.info("[Incognito] FABRIC MODE - Blocking mod channel: {}", payloadId);
            ci.cancel();
            return;
        }
        
        if (config.getSettings().isForgeMode()) {
            String namespace = payloadId.getNamespace();
            String path = payloadId.getPath();
            
            if ("minecraft".equals(namespace) && ("register".equals(path) || "unregister".equals(path))) {
                if (payload instanceof RegistrationPayload registrationPayload) {
                    incognito$handleMinecraftRegisterForForge(registrationPayload, ci, connection);
                } else {
                    Incognito.LOGGER.warn("[Incognito] FORGE MODE - Blocking unknown {}: {}", 
                        payloadId, payload.getClass().getName());
                    ci.cancel();
                }
                return;
            }
            
            if ("forge".equals(namespace) && ("login".equals(path) || "handshake".equals(path))) {
                return;
            }
            
            if ("minecraft".equals(namespace) && "mco".equals(path)) {
                Incognito.LOGGER.info("[Incognito] FORGE MODE - Blocking minecraft:mco");
                ci.cancel();
                return;
            }
            
            if ("minecraft".equals(namespace)) {
                return;
            }
            
            Incognito.LOGGER.info("[Incognito] FORGE MODE - Blocking non-forge channel: {}", payloadId);
            ci.cancel();
        }
    }
    
    @Unique
    private void incognito$handleMinecraftRegisterForFabric(RegistrationPayload original, CallbackInfo ci, Connection connection) {
        List<ResourceLocation> originalChannels = original.channels();
        
        List<ResourceLocation> filteredChannels = originalChannels.stream()
            .filter(channel -> {
                String ns = channel.getNamespace();
                return "fabric".equals(ns) || ns.startsWith("fabric-");
            })
            .toList();
        
        Incognito.LOGGER.debug("[Incognito] FABRIC MODE - Filtered channels: {} -> {}", 
            originalChannels.size(), filteredChannels.size());
        
        ci.cancel();
        
        if (filteredChannels.isEmpty()) {
            return;
        }
        
        RegistrationPayload filteredPayload = incognito$createRegistrationPayload(original, new ArrayList<>(filteredChannels));
        if (filteredPayload == null) {
            Incognito.LOGGER.error("[Incognito] Could not create filtered RegistrationPayload");
            return;
        }
        
        incognito$sending.set(true);
        try {
            connection.send(new ServerboundCustomPayloadPacket(filteredPayload));
        } finally {
            incognito$sending.set(false);
        }
    }
    
    @Unique
    private void incognito$handleMinecraftRegisterForForge(RegistrationPayload original, CallbackInfo ci, Connection connection) {
        ci.cancel();
        
        List<ResourceLocation> forgeChannels = List.of(
            ResourceLocation.parse("forge:login"),
            ResourceLocation.parse("forge:handshake")
        );
        
        RegistrationPayload forgePayload = incognito$createRegistrationPayload(original, new ArrayList<>(forgeChannels));
        if (forgePayload == null) {
            Incognito.LOGGER.error("[Incognito] Could not create Forge RegistrationPayload");
            return;
        }
        
        Incognito.LOGGER.debug("[Incognito] FORGE MODE - Registering forge channels");
        
        incognito$sending.set(true);
        try {
            connection.send(new ServerboundCustomPayloadPacket(forgePayload));
        } finally {
            incognito$sending.set(false);
        }
    }
    
    @Unique
    private static RegistrationPayload incognito$createRegistrationPayload(RegistrationPayload original, List<ResourceLocation> channels) {
        try {
            for (Constructor<?> constructor : RegistrationPayload.class.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 2) {
                    constructor.setAccessible(true);
                    try {
                        return (RegistrationPayload) constructor.newInstance(original.id(), channels);
                    } catch (Exception e1) {
                        try {
                            return (RegistrationPayload) constructor.newInstance(channels, original.id());
                        } catch (Exception e2) {
                            Incognito.LOGGER.debug("[Incognito] Failed parameter order attempt: {}", e2.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Incognito.LOGGER.error("[Incognito] Failed to create RegistrationPayload: {}", e.getMessage(), e);
        }
        Incognito.LOGGER.warn("[Incognito] Unable to create RegistrationPayload - no compatible constructor found");
        return null;
    }
}


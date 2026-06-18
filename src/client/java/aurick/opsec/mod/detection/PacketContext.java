package aurick.opsec.mod.detection;

import net.minecraft.network.protocol.Packet;

/**
 * ThreadLocal tracking for packet processing context.
 * Set true during packet decode and handle, read by content constructors
 * to tag instances that originated from network packets.
 *
 * Two injection points use this:
 * - PacketDecoderMixin wraps StreamCodec.decode() (eager deserialization)
 * - PacketProcessorMixin wraps Packet.handle() (lazy deserialization)
 *
 * {@link #isDecodingPayload()} is the narrower of the two: it is true ONLY inside
 * StreamCodec.decode (PacketDecoderMixin), never during handle(). The inbound
 * channel-fingerprint gate needs that distinction — Fabric calls the same
 * PayloadTypeRegistryImpl.get(id) both while decoding an inbound payload (which we
 * want to redirect to vanilla's DiscardedPayload) and while registering mod
 * receivers during the config->play transition, which runs inside finish_configuration's
 * handle() (where suppressing it would crash registration).
 */
public class PacketContext {
    private static final ThreadLocal<Boolean> PROCESSING_PACKET =
        ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> DECODING_PAYLOAD =
        ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<String> PACKET_NAME =
        ThreadLocal.withInitial(() -> "unknown");

    private PacketContext() {}

    public static boolean isProcessingPacket() {
        return PROCESSING_PACKET.get();
    }

    public static void setProcessingPacket(boolean value) {
        PROCESSING_PACKET.set(value);
    }

    /** True only inside StreamCodec.decode (PacketDecoderMixin), not during handle(). */
    public static boolean isDecodingPayload() {
        return DECODING_PAYLOAD.get();
    }

    public static void setDecodingPayload(boolean value) {
        DECODING_PAYLOAD.set(value);
    }

    public static String getPacketName() {
        return PACKET_NAME.get();
    }

    /**
     * Resolve and store the packet name from its PacketType ResourceLocation.
     * This gives stable, human-readable IDs like "minecraft:system_chat"
     * regardless of obfuscation.
     */
    public static void setPacketName(Object packet) {
        if (packet instanceof Packet<?> p) {
            try {
                //? if >=1.20.5 {
                PACKET_NAME.set(p.type().id().toString());
                //?} else {
                /*// 1.20.4 and below: Packet doesn't have type() — fall back to class name
                PACKET_NAME.set(p.getClass().getSimpleName());
                *///?}
            } catch (Exception e) {
                PACKET_NAME.set("unknown");
            }
        }
    }
}

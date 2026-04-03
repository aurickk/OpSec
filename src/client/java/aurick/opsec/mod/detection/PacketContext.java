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
 */
public class PacketContext {
    private static final ThreadLocal<Boolean> PROCESSING_PACKET =
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
                PACKET_NAME.set(p.type().id().toString());
            } catch (Exception e) {
                PACKET_NAME.set("unknown");
            }
        }
    }
}

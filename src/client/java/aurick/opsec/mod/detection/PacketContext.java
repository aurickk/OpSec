package aurick.opsec.mod.detection;

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

    private PacketContext() {}

    public static boolean isProcessingPacket() {
        return PROCESSING_PACKET.get();
    }

    public static void setProcessingPacket(boolean value) {
        PROCESSING_PACKET.set(value);
    }
}

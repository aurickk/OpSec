package aurick.opsec.mod.protection;

public interface OpsecFromPacketAccess {
    void opsec$setFromPacket();
    default void opsec$setSilent() {}
}

package aurick.opsec.mod.protection;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
//? if >=1.21.4 {
import net.minecraft.server.packs.metadata.MetadataSectionType;
//?} else {
/*import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
*/
//?}
import net.minecraft.server.packs.resources.IoSupplier;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*/
//?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A {@link PackResources} wrapper that exposes only {@code assets/<ns>/lang/*.json}
 * plus the pack metadata; other resource types are reported as absent. Lets vanilla
 * perform the normal download/cache/status-packet flow (fingerprint-identical to a
 * real client) while preventing textures/sounds/models/fonts from applying.
 */
public final class LangOnlyPackResources implements PackResources {

    private final PackResources delegate;
    private final UUID packId;

    public LangOnlyPackResources(PackResources delegate, UUID packId) {
        this.delegate = delegate;
        this.packId = packId;
    }

    PackResources delegate() {
        return delegate;
    }

    // Filter bypassed after [Load Pack For Real] so a subsequent reload pulls the
    // full pack through this same wrapper instance.
    private boolean filterActive() {
        return !PackStripHandler.isLoadForReal(packId);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return delegate.getRootResource(paths);
    }

    //? if >=1.21.11 {
    /*@Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (filterActive() && !isLangResource(type, location.getPath())) return null;
        return delegate.getResource(type, location);
    }
    *///?} else {
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (filterActive() && !isLangResource(type, location.getPath())) return null;
        return delegate.getResource(type, location);
    }
    //?}

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (filterActive() && !isLangPath(type, path)) return;
        delegate.listResources(type, namespace, path, output);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    //? if >=1.21.4 {
    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        return delegate.getMetadataSection(type);
    }
    //?} else {
    /*@Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        return delegate.getMetadataSection(serializer);
    }
    *///?}

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    @Override
    public String packId() {
        return delegate.packId();
    }

    @Override
    public Optional<net.minecraft.server.packs.repository.KnownPack> knownPackInfo() {
        return delegate.knownPackInfo();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static boolean isLangResource(PackType type, String path) {
        return type == PackType.CLIENT_RESOURCES
                && path.startsWith("lang/")
                && path.endsWith(".json");
    }

    private static boolean isLangPath(PackType type, String path) {
        return type == PackType.CLIENT_RESOURCES
                && (path.equals("lang") || path.startsWith("lang/"));
    }
}

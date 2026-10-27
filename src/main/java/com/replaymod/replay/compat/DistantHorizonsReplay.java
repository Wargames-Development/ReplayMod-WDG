package com.replaymod.replay.compat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.replaymod.recording.packet.PacketListener;
import com.replaymod.replaystudio.replay.ReplayFile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Optional DH API bridge. Replays read the real world's LOD cache without rebuilding it from replay chunks. */
public final class DistantHorizonsReplay {
    public static final String MANIFEST = "wdg/lod-sources.json";
    private static final Map<Integer, String> sources = new HashMap<>();
    private static final Map<PacketListener, Map<Integer, String>> recorded = new WeakHashMap<>();
    private static Object injector, override;
    private static Class<?> saveInterface;
    private static boolean active;
    private static boolean wasReady;
    private static long stableSince, loadStarted, lastSignature = Long.MIN_VALUE;
    private static int cameraCellX = Integer.MIN_VALUE, cameraCellZ;
    private static long lastPreviewTick;
    private static boolean previewTickWarning;
    private DistantHorizonsReplay() { }
    public static boolean available() {
        try { Class.forName("com.seibel.distanthorizons.api.DhApi", false, DistantHorizonsReplay.class.getClassLoader()); return true; }
        catch (ClassNotFoundException absent) { return false; }
    }
    private static Object worldProxy() throws ReflectiveOperationException { return Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("worldProxy").get(null); }
    private static Object call(Object object, String name, Class<?>[] types, Object... arguments) throws ReflectiveOperationException {
        return object.getClass().getMethod(name, types).invoke(object, arguments);
    }
    public static void record(PacketListener listener) {
        if (!available() || active) return;
        try {
            Object world = worldProxy(); if (world == null || !(Boolean) call(world, "worldLoaded", new Class<?>[0])) return;
            Map<Integer, String> folders = recorded.computeIfAbsent(listener, unused -> new HashMap<>());
            boolean changed = false;
            for (Object level : (Iterable<?>) call(world, "getAllLoadedLevelWrappers", new Class<?>[0])) {
                Object wrapped = call(level, "getWrappedMcObject", new Class<?>[0]);
                File folder = (File) call(level, "getDhSaveFolder", new Class<?>[0]);
                if (!(wrapped instanceof World) || folder == null || !folder.isDirectory()) continue;
                String relative = relativeFolder(folder);
                int dimension = ((World) wrapped).provider.dimensionId;
                if (!relative.equals(folders.get(dimension))) { folders.put(dimension, relative); changed = true; }
            }
            if (changed) listener.saveExtra(MANIFEST, new Gson().toJson(folders).getBytes(StandardCharsets.UTF_8));
        } catch (ReflectiveOperationException | IOException error) {
            com.replaymod.compat.ReplayModCompat.LOGGER.debug("DH replay cache discovery is not ready", error);
        }
    }
    public static Map<Integer, String> read(ReplayFile file) throws IOException {
        com.google.common.base.Optional<InputStream> saved = file.get(MANIFEST);
        if (!saved.isPresent()) return discover(file);
        try (Reader reader = new InputStreamReader(saved.get(), StandardCharsets.UTF_8)) {
            Map<Integer, String> result = new Gson().fromJson(reader, new TypeToken<Map<Integer, String>>() { }.getType());
            return result == null ? new HashMap<>() : new HashMap<>(result);
        } catch (RuntimeException invalid) { throw new IOException("Invalid LOD source mapping", invalid); }
    }
    private static Map<Integer, String> discover(ReplayFile file) throws IOException {
        Map<Integer, String> found = new HashMap<>();
        if (!available()) return found;
        com.google.common.base.Optional<InputStream> packets = file.get("recording.tmcpr");
        if (!packets.isPresent()) return found;
        try (InputStream input = packets.get()) {
            Object registry = Class.forName("com.seibel.distanthorizons.core.network.messages.MessageRegistry").getField("INSTANCE").get(null);
            int id = (Integer) call(registry, "getMessageId", new Class<?>[]{Class.class}, Class.forName("com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage"));
            String channel = (String) Class.forName("com.seibel.distanthorizons.common.AbstractPluginPacketSender").getField("WRAPPER_PACKET_RESOURCE").get(null);
            Map<Integer, Set<String>> identities = RecordedLodKeys.read(input, channel, id);
            File root = new File(Minecraft.getMinecraft().mcDataDir, "Distant_Horizons_server_data");
            for (Map.Entry<Integer, File> match : LodCacheFolders.match(root, identities).entrySet()) {
                found.put(match.getKey(), relativeFolder(match.getValue()));
            }
        } catch (ReflectiveOperationException error) { com.replaymod.compat.ReplayModCompat.LOGGER.warn("Cannot discover recorded DH world keys; select a cache in Replay library", error); }
        return found;
    }
    public static String relativeFolder(File folder) throws IOException {
        Path root = Minecraft.getMinecraft().mcDataDir.getCanonicalFile().toPath(), path = folder.getCanonicalFile().toPath();
        if (!path.startsWith(root.resolve("Distant_Horizons_server_data")) && !path.startsWith(root.resolve("saves"))) throw new IOException("Choose a Distant Horizons cache inside this Minecraft instance.");
        return root.relativize(path).toString();
    }
    public static File resolve(String relative) throws IOException {
        File folder = new File(Minecraft.getMinecraft().mcDataDir, relative).getCanonicalFile();
        relativeFolder(folder);
        if (!new File(folder, "DistantHorizons.sqlite").isFile()) throw new IOException("LOD cache was not found: " + folder);
        return folder;
    }
    public static synchronized void prepare(ReplayFile file) throws IOException {
        close();
        if (!available()) return;
        sources.putAll(read(file));
        for (String relative : sources.values()) resolve(relative);
        try {
            saveInterface = Class.forName("com.seibel.distanthorizons.api.interfaces.override.levelHandling.IDhApiSaveStructure");
            injector = Class.forName("com.seibel.distanthorizons.api.DhApi").getField("overrides").get(null);
            if (call(injector, "get", new Class<?>[]{Class.class}, saveInterface) != null) throw new IOException("Another mod already controls DH's terrain cache. Its override must be resolved before opening this replay with LODs.");
            override = Proxy.newProxyInstance(saveInterface.getClassLoader(), new Class<?>[]{saveInterface}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getPriority": return 10;
                    case "getDelayedSetupComplete": return true;
                    case "finishDelayedSetup": return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "ReplayMod LOD terrain source";
                    case "overrideFilePath":
                        if (!active) return null;
                        Object wrapped = call(args[1], "getWrappedMcObject", new Class<?>[0]);
                        if (!(wrapped instanceof World)) return null;
                        String relative = sources.get(((World) wrapped).provider.dimensionId);
                        return relative == null ? null : resolve(relative);
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
            active = true;
            Class<?> base = Class.forName("com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable");
            call(injector, "bind", new Class<?>[]{Class.class, base}, saveInterface, override);
        } catch (ReflectiveOperationException error) { active = false; throw new IOException("Could not connect ReplayMod to the installed Distant Horizons API.", error); }
    }
    public static void protectTerrain() {
        if (!active) return;
        try { Object world = worldProxy(); if (world != null && (Boolean) call(world, "worldLoaded", new Class<?>[0])) call(world, "setReadOnly", new Class<?>[]{boolean.class}, true); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not enable read-only LOD terrain for replay playback.", error); }
    }
    public static boolean isActive() { return active; }
    public static boolean hasSource(int dimension) { return active && sources.containsKey(dimension); }
    public static void requireTerrain() {
        if (!available()) return;
        World world = Minecraft.getMinecraft().theWorld;
        if (!active || world == null || !sources.containsKey(world.provider.dimensionId)) throw new IllegalStateException("Choose this recording's LOD terrain cache in Replay library before exporting. Distant Horizons is installed but the replay has no terrain source for this dimension.");
        protectTerrain();
    }
    public static void beginExport() { wasReady = false; cameraCellX = Integer.MIN_VALUE; lastSignature = Long.MIN_VALUE; stableSince = loadStarted = System.currentTimeMillis(); }
    public static void tickPreview() {
        if (!active || System.currentTimeMillis() - lastPreviewTick < 200) return;
        lastPreviewTick = System.currentTimeMillis();
        try {
            Object world = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi").getMethod("tryGetDhClientWorld").invoke(null);
            if (world == null) return;
            for (Object level : (Iterable<?>) call(world, "getAllLoadedLevels", new Class<?>[0])) {
                Object wrapper = call(level, "getLevelWrapper", new Class<?>[0]);
                if (call(wrapper, "getWrappedMcObject", new Class<?>[0]) == Minecraft.getMinecraft().theWorld) call(level.getClass().getField("clientside").get(level), "clientTick", new Class<?>[0]);
            }
        } catch (ReflectiveOperationException error) {
            if (!previewTickWarning) com.replaymod.compat.ReplayModCompat.LOGGER.warn("Could not tick the DH viewer independently of replay time", error);
            previewTickWarning = true;
        }
    }
    /** Tick the render tree even when replay time is frozen and Minecraft has no elapsed game ticks. */
    public static boolean renderReady() {
        if (!active) return true;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            int cellX = (int) Math.floor(mc.renderViewEntity.posX / 16), cellZ = (int) Math.floor(mc.renderViewEntity.posZ / 16);
            long now = System.currentTimeMillis();
            boolean first = cameraCellX == Integer.MIN_VALUE;
            if (first || cellX != cameraCellX || cellZ != cameraCellZ) {
                cameraCellX = cellX; cameraCellZ = cellZ; stableSince = loadStarted = now; lastSignature = Long.MIN_VALUE;
            }
            Object world = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi").getMethod("tryGetDhClientWorld").invoke(null);
            if (world == null) throw new IllegalStateException("Distant Horizons has not loaded the replay world.");
            int loading = 0, ready = 0, sections = 0;
            boolean foundLevel = false;
            for (Object level : (Iterable<?>) call(world, "getAllLoadedLevels", new Class<?>[0])) {
                Object wrapper = call(level, "getLevelWrapper", new Class<?>[0]);
                if (call(wrapper, "getWrappedMcObject", new Class<?>[0]) != mc.theWorld) continue;
                foundLevel = true;
                Object module = level.getClass().getField("clientside").get(level);
                call(module, "clientTick", new Class<?>[0]);
                java.util.concurrent.atomic.AtomicReference<?> ref = (java.util.concurrent.atomic.AtomicReference<?>) module.getClass().getField("ClientRenderStateRef").get(module);
                Object state = ref.get();
                if (state == null) { loading++; continue; }
                Object tree = state.getClass().getField("quadtree").get(state);
                ArrayList<Object> nodes = new ArrayList<>(); call(tree, "populateListWithEnabledRenderSections", new Class<?>[]{ArrayList.class}, nodes);
                sections += nodes.size();
                for (Object node : nodes) {
                    if ((Boolean) call(node, "gpuUploadInProgress", new Class<?>[0])) loading++;
                    if ((Boolean) call(node, "gpuUploadComplete", new Class<?>[0])) ready++;
                }
            }
            long signature = ((long) ready << 32) | (sections & 0xFFFFFFFFL);
            if (signature != lastSignature || loading > 0) { stableSince = now; lastSignature = signature; }
            // Allow queued file reads to start before accepting an apparently idle renderer.
            boolean complete = foundLevel && ready > 0 && loading == 0 && now - stableSince >= 350 && now - loadStarted >= 1000;
            if (!complete && wasReady) loadStarted = now;
            wasReady = complete;
            if (!complete && now - loadStarted > 120000) throw new IllegalStateException("Distant Horizons LOD loading did not finish in two minutes. Check the chosen terrain cache and DH settings; export stopped before capturing incomplete terrain.");
            return complete;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot verify Distant Horizons LOD readiness for this installed version.", error); }
    }
    public static synchronized void close() {
        if (override != null && injector != null) {
            try { call(injector, "unbind", new Class<?>[]{Class.class, Class.forName("com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable")}, saveInterface, override); }
            catch (ReflectiveOperationException error) { com.replaymod.compat.ReplayModCompat.LOGGER.warn("Could not release DH replay override", error); }
        }
        active = false; override = null; injector = null; sources.clear();
    }
}

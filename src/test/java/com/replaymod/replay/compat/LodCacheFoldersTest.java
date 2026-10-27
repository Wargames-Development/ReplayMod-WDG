package com.replaymod.replay.compat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class LodCacheFoldersTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File cache(String path, String key) throws IOException {
        File folder = new File(temporary.getRoot(), path);
        assertTrue(folder.mkdirs());
        assertTrue(new File(folder, "DistantHorizons.sqlite").createNewFile());
        if (key != null) Files.write(new File(folder, ".dh-level-key").toPath(), key.getBytes(StandardCharsets.UTF_8));
        return folder;
    }

    @Test public void readableNamesAndLegacyFoldersBothAppearWithTheirActualDimensions() throws Exception {
        File readable = cache("host.wargames.uk%3A27007/earth40", "00000001kk71e@overworld:0\n");
        File legacy = cache("old-server/world@nether@@-1", null);
        cache("REPLAY/unused@overworld@@0", null);
        cache("ReplayModReplay-test/unused@overworld@@0", null);
        cache("unknown-server/unknown", null);
        assertEquals(Integer.valueOf(0), LodCacheFolders.dimension(readable));
        assertEquals(Integer.valueOf(-1), LodCacheFolders.dimension(legacy));
        assertEquals(new HashSet<>(Arrays.asList(readable, legacy)), new HashSet<>(LodCacheFolders.candidates(temporary.getRoot())));
    }

    @Test public void identityComesFromTheSidecarAndNeverFromAConflictingReadableName() throws Exception {
        File folder = cache("server/wrong@nether@@-1", "actual@overworld:0");
        assertEquals(Integer.valueOf(0), LodCacheFolders.dimension(folder));
        Map<Integer, Set<String>> identity = Collections.singletonMap(0, Collections.singleton("actual@overworld@@0"));
        assertEquals(folder, LodCacheFolders.match(temporary.getRoot(), identity).get(0));
        Files.write(new File(folder, ".dh-level-key").toPath(), "../outside".getBytes(StandardCharsets.UTF_8));
        assertTrue(LodCacheFolders.candidates(temporary.getRoot()).isEmpty());
        Files.write(new File(folder, ".dh-level-key").toPath(), new byte[513]);
        assertNull(LodCacheFolders.key(folder));
    }

    @Test public void duplicateCachesAndMultipleRecordedWorldsRequireExplicitSelection() throws Exception {
        cache("first/earth40", "actual@overworld:0");
        cache("second/earth40", "actual@overworld:0");
        assertTrue(LodCacheFolders.match(temporary.getRoot(), Collections.singletonMap(0, Collections.singleton("actual@overworld@@0"))).isEmpty());
        assertTrue(LodCacheFolders.match(temporary.getRoot(), Collections.singletonMap(0, new HashSet<>(Arrays.asList("actual@overworld@@0", "other@overworld@@0")))).isEmpty());
    }

    @Test public void actualRecordingMatchesTheInstalledReadableCache() throws Exception {
        String recording = System.getenv("REPLAYMOD_TEST_RECORDING"), root = System.getenv("REPLAYMOD_TEST_LOD_ROOT");
        assumeTrue(recording != null && root != null);
        try (ZipFile file = new ZipFile(recording); InputStream packets = file.getInputStream(file.getEntry("recording.tmcpr"))) {
            Map<Integer, Set<String>> identities = RecordedLodKeys.read(packets, "distant_horizonsmsg", 2);
            File match = LodCacheFolders.match(new File(root), identities).get(0);
            assertNotNull("The recorded overworld must match the actual DH sidecar", match);
            assertEquals("earth40", match.getName());
        }
    }
}

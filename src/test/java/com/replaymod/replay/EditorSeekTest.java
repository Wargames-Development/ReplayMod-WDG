package com.replaymod.replay;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class EditorSeekTest {
    @Test public void rapidDragsReplaceQueuedTargetsInsteadOfReplayingEveryMouseEvent() {
        Backend backend=new Backend(); EditorSeek seek=new EditorSeek(backend);
        seek.request(100,true,0); seek.request(200,true,1); seek.request(300,true,2);
        seek.tick(3);
        assertEquals(Arrays.asList(300),backend.targets); assertFalse(seek.pending());
        assertEquals(1,backend.begins); assertEquals(1,backend.finishes); assertEquals(1,backend.releases);
        assertEquals(EditorSeek.FRAME_BUDGET_NANOS,backend.budget);
    }
    @Test public void anInFlightSeekUsesTheNewestTargetOnTheNextFrame() {
        Backend backend=new Backend(); backend.done=false; EditorSeek seek=new EditorSeek(backend);
        seek.request(100,true,0); seek.tick(0); seek.request(500,true,1); seek.tick(1);
        assertEquals(Arrays.asList(100,500),backend.targets); assertEquals(1,backend.begins);
        backend.done=true; seek.request(600,false,2); seek.tick(2);
        assertEquals(Arrays.asList(100,500,600),backend.targets); assertFalse(seek.pending()); assertEquals(1,backend.releases);
    }
    @Test public void movingRewindWaitsBrieflyButReleaseAppliesTheFinalTimeImmediately() {
        Backend backend=new Backend(); backend.position=1000; EditorSeek seek=new EditorSeek(backend);
        seek.request(800,true,0); seek.tick(10); seek.request(600,true,20); seek.tick(30);
        assertTrue(backend.targets.isEmpty()); assertTrue(seek.pending());
        seek.request(600,false,31); seek.tick(31);
        assertEquals(Arrays.asList(600),backend.targets); assertFalse(seek.pending());
    }
    @Test public void heldRewindNeverRepeatedlyRebuildsMinecraftAndDhWorlds() {
        Backend backend=new Backend(); backend.position=1000; EditorSeek seek=new EditorSeek(backend);
        seek.request(500,true,0); seek.tick(74); assertTrue(backend.targets.isEmpty());
        seek.tick(75); seek.tick(1000); seek.tick(5000); assertTrue(backend.targets.isEmpty());
        seek.request(450,false,5001); seek.tick(5001); assertEquals(Arrays.asList(450),backend.targets);
    }
    @Test public void failureOrCloseReleasesSenderOwnershipExactlyOnce() {
        Backend backend=new Backend(); backend.fail=true; EditorSeek seek=new EditorSeek(backend);
        seek.request(100,false,0);
        try { seek.tick(0); fail(); } catch(IllegalStateException expected) { }
        seek.cancel(); assertFalse(seek.pending()); assertEquals(1,backend.releases);
        backend.fail=false; backend.done=false; seek.request(200,false,1); seek.tick(1); seek.cancel(); seek.cancel();
        assertEquals(2,backend.releases); assertEquals(0,backend.finishes);
    }
    @Test public void aNewRequestDuringCompletionIsNotLost() {
        Backend backend=new Backend(); EditorSeek seek=new EditorSeek(backend);
        backend.onFinish=() -> seek.request(900,false,1);
        seek.request(100,false,0); seek.tick(0);
        assertTrue(seek.pending()); assertEquals(900,seek.target());
        backend.onFinish=() -> {}; seek.tick(2); assertFalse(seek.pending());
        assertEquals(Arrays.asList(100,900),backend.targets);
    }
    private static class Backend implements EditorSeek.Backend {
        int position,begins,finishes,releases; long budget;
        boolean done=true,fail;
        Runnable onFinish=() -> {};
        List<Integer> targets=new ArrayList<>();
        public void begin() { begins++; }
        public int position() { return position; }
        public boolean advance(int target,long budget) { this.budget=budget; targets.add(target); if(fail) throw new IllegalStateException("read error"); return done; }
        public void finish() { finishes++; onFinish.run(); }
        public void release() { releases++; }
    }
}

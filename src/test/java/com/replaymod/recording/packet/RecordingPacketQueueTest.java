package com.replaymod.recording.packet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class RecordingPacketQueueTest {
    @Test
    public void localSpawnCannotOvertakeJoinGameWaitingForItsRecordingTask() {
        Harness h = new Harness();
        h.onMainThread.set(false);
        h.queue.save("handshake");
        h.queue.save("join");
        assertTrue(h.recorded.isEmpty());

        // NetworkManager handles JoinGame before the scheduled recording tasks
        // are run, and its RETURN injection records the local player's spawn.
        h.onMainThread.set(true);
        h.queue.save("spawn");
        assertEquals(Arrays.asList("handshake", "join", "spawn"), h.recorded);
        assertEquals(Arrays.asList(1), h.spawnWorlds);
        h.runScheduledTasks();
        assertEquals("Delayed tasks must not duplicate packets", 3, h.recorded.size());
    }

    @Test
    public void blockEventsDuringWorldLoadAlsoFollowJoinGame() {
        Harness h = new Harness();
        h.onMainThread.set(false);
        h.queue.save("join");
        h.onMainThread.set(true);
        h.queue.save("block-event");
        h.queue.save("spawn");
        h.runScheduledTasks();
        assertEquals(Arrays.asList("join", "block-event", "spawn"), h.recorded);
    }

    @Test
    public void respawnIsRecordedBeforePlayerIsSpawnedInTheNewWorld() {
        Harness h = new Harness();
        h.queue.save("join");
        h.queue.save("spawn");
        h.onMainThread.set(false);
        h.queue.save("respawn");
        h.onMainThread.set(true);
        h.queue.save("spawn");
        h.runScheduledTasks();
        assertEquals(Arrays.asList("join", "spawn", "respawn", "spawn"), h.recorded);
        assertEquals(Arrays.asList(1, 2), h.spawnWorlds);
    }

    @Test
    public void inboundOnlyPacketsAreDrainedOnMainThreadInArrivalOrder() {
        Harness h = new Harness();
        h.onMainThread.set(false);
        h.queue.save("handshake");
        h.queue.save("join");
        h.queue.save("chunk");
        assertTrue(h.recorded.isEmpty());
        h.onMainThread.set(true);
        h.runScheduledTasks();
        assertEquals(Arrays.asList("handshake", "join", "chunk"), h.recorded);
    }

    private static class Harness {
        final AtomicBoolean onMainThread = new AtomicBoolean(true);
        final List<Runnable> scheduled = new ArrayList<>();
        final List<String> recorded = new ArrayList<>();
        final List<Integer> spawnWorlds = new ArrayList<>();
        int world;
        final RecordingPacketQueue<String> queue = new RecordingPacketQueue<>(
                onMainThread::get, scheduled::add, packet -> {
                    assertTrue("Packet serialization must run on the main thread", onMainThread.get());
                    if (packet.equals("join") || packet.equals("respawn")) {
                        world++;
                    } else if (packet.equals("spawn") || packet.equals("block-event")) {
                        assertTrue("World-dependent packets must follow JoinGame", world > 0);
                        if (packet.equals("spawn")) {
                            spawnWorlds.add(world);
                        }
                    }
                    recorded.add(packet);
                });

        void runScheduledTasks() {
            for (Runnable task : new ArrayList<>(scheduled)) {
                task.run();
            }
            scheduled.clear();
        }
    }
}

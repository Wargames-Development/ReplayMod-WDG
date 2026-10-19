package com.replaymod.recording.packet;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Keeps received and locally injected packets ordered before main-thread serialization. */
final class RecordingPacketQueue<T> {
    private final Queue<T> pending = new ConcurrentLinkedQueue<>();
    private final BooleanSupplier onMainThread;
    private final Consumer<Runnable> schedule;
    private final Consumer<T> record;

    RecordingPacketQueue(BooleanSupplier onMainThread, Consumer<Runnable> schedule, Consumer<T> record) {
        this.onMainThread = onMainThread;
        this.schedule = schedule;
        this.record = record;
    }

    void save(T packet) {
        // Enqueue on the receiving thread, before NetworkManager can process the
        // packet and cause main-thread callbacks to inject dependent packets.
        pending.add(packet);
        if (onMainThread.getAsBoolean()) {
            drain();
        } else {
            schedule.accept(this::drain);
        }
    }

    private void drain() {
        if (!onMainThread.getAsBoolean()) {
            throw new IllegalStateException("Recording packets must be serialized on the Minecraft thread.");
        }
        T packet;
        while ((packet = pending.poll()) != null) {
            record.accept(packet);
        }
    }
}

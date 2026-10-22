package com.replaymod.replay;

/** Latest-request-wins seeking, pumped on the game thread between displayed frames. */
public final class EditorSeek {
    public interface Backend {
        void begin();
        int position();
        boolean advance(int target, long budgetNanos);
        void finish();
        void release();
    }
    public static final long FRAME_BUDGET_NANOS = 3_000_000;
    private final Backend backend;
    private int target = -1;
    private boolean active, dragging;
    public EditorSeek(Backend backend) { this.backend = backend; }
    public void request(int target, boolean dragging, long nowMillis) {
        if (target < 0) throw new IllegalArgumentException("Replay time cannot be negative.");
        this.target = target; this.dragging = dragging;
    }
    public boolean pending() { return target >= 0 || active; }
    public boolean working() { return active; }
    public int target() { return target; }
    public void tick(long nowMillis) {
        if (target < 0) return;
        // A rewind tears down the Minecraft/DH world. Never repeat that expensive
        // operation while the pointer is still held; commit its final time on release.
        if (dragging && target < backend.position()) return;
        try {
            if (!active) { active = true; backend.begin(); }
            if (backend.advance(target, FRAME_BUDGET_NANOS)) {
                target = -1;
                try { backend.finish(); } finally { active = false; backend.release(); }
            }
        } catch (RuntimeException error) {
            cancel(); throw error;
        }
    }
    public void cancel() {
        target = -1;
        if (active) { active = false; backend.release(); }
    }
}

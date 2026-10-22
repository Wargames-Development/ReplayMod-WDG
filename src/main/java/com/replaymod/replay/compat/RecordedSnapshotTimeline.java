package com.replaymod.replay.compat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Removes isolated network arrival spikes without flattening sustained server slowdown. */
public final class RecordedSnapshotTimeline {
    private final List<Integer> arrivals = new ArrayList<>();
    private final List<Long> sequences = new ArrayList<>();

    public int add(int time, long sequence) throws IOException {
        if (arrivals.size() >= 500000) throw new IOException("Too many MCHR snapshots. Split the recording.");
        arrivals.add(time); sequences.add(sequence);
        return arrivals.size();
    }

    /** Batch IDs are one-based; packet gaps and sequence restarts bound each filter window. */
    public int[] times() {
        int[] result = new int[arrivals.size() + 1];
        for (int i = 0; i < arrivals.size(); i++) result[i + 1] = arrivals.get(i);
        for (int start = 0; start < arrivals.size();) {
            int end = start;
            while (end + 1 < arrivals.size() && continuous(end, end + 1)) end++;
            for (int i = start + 1; i < end; i++) {
                int radius = Math.min(2, Math.min(i - start, end - i));
                long[] offsets = new long[radius * 2 + 1];
                for (int j = -radius; j <= radius; j++)
                    offsets[j + radius] = arrivals.get(i + j) - (sequences.get(i + j) - sequences.get(i)) * 50L;
                Arrays.sort(offsets);
                long candidate = offsets[radius];
                if (Math.abs(candidate - arrivals.get(i)) <= 100L)
                    result[i + 1] = (int)candidate;
            }
            // Malformed/irregular sequences must never reorder recorded events.
            for (int i = start + 1; i <= end; i++) if (result[i + 1] < result[i]) {
                for (int j = start; j <= end; j++) result[j + 1] = arrivals.get(j);
                break;
            }
            start = end + 1;
        }
        return result;
    }

    private boolean continuous(int from, int to) {
        long a = sequences.get(from), b = sequences.get(to);
        int elapsed = arrivals.get(to) - arrivals.get(from);
        return a >= 0 && b > a && b - a <= 10 && elapsed >= 0 && elapsed <= 500;
    }
}

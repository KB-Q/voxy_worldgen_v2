package com.ethan.voxyworldgenv2.core;

import com.ethan.voxyworldgenv2.VoxyWorldGenV2;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.function.IntSupplier;

// adaptive limit on the number of chunks generating at once, evaluated once a second on the server thread:
// - live heap (after the last gc) above throttleSlowHeapPercent lowers the limit, above throttlePauseHeapPercent stops generation
// - tick work time above throttleTargetMspt, a tick rate below 18 tps, or (singleplayer, focused window) fps below
//   throttleMinFps halve the limit, otherwise it grows back by a tenth of maxActiveTasks per second
// - below 10 tps generation stops until the server recovers
public final class GenerationThrottle {
    private static final int WINDOW = 20;
    private static final int EVALUATE_EVERY = 20;
    // an interval this long is a pause or a stalled server thread, which the generation heartbeat already handles
    private static final long MAX_INTERVAL_NANOS = 1_000_000_000L;
    // used heap including garbage, near this the jvm is about to run out whatever the last gc left
    private static final double USED_HEAP_PAUSE = 0.95;

    private static final List<MemoryPoolMXBean> HEAP_POOLS = ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getType() == MemoryType.HEAP).toList();

    private final long[] workNanos = new long[WINDOW];
    private final long[] intervalNanos = new long[WINDOW];
    private int workCount, intervalCount, workIndex, intervalIndex, ticksSinceEvaluate;
    private long tickStartNanos, lastTickEndNanos;

    private IntSupplier clientFps = () -> -1;

    private volatile int allowed = Integer.MAX_VALUE;
    private volatile String reason = null;
    private volatile boolean paused = false;
    private volatile double heapPercent, usedHeapPercent, workMs, tps = 20;
    private volatile int fps = -1;

    public void setClientFps(IntSupplier supplier) {
        this.clientFps = supplier;
    }

    public void onTickStart() {
        tickStartNanos = System.nanoTime();
    }

    public void onTickEnd(int maxTasks) {
        long now = System.nanoTime();
        if (tickStartNanos != 0) {
            workNanos[workIndex] = now - tickStartNanos;
            workIndex = (workIndex + 1) % WINDOW;
            workCount = Math.min(workCount + 1, WINDOW);
        }
        if (lastTickEndNanos != 0 && now - lastTickEndNanos < MAX_INTERVAL_NANOS) {
            intervalNanos[intervalIndex] = now - lastTickEndNanos;
            intervalIndex = (intervalIndex + 1) % WINDOW;
            intervalCount = Math.min(intervalCount + 1, WINDOW);
        }
        lastTickEndNanos = now;
        if (allowed == Integer.MAX_VALUE) allowed = maxTasks;
        if (++ticksSinceEvaluate >= EVALUATE_EVERY) {
            ticksSinceEvaluate = 0;
            evaluate(maxTasks);
        }
    }

    private void evaluate(int maxTasks) {
        var cfg = Config.DATA;
        long max = Runtime.getRuntime().maxMemory();
        long afterGc = 0, used = 0;
        for (var pool : HEAP_POOLS) {
            var usage = pool.getCollectionUsage();
            if (usage != null) afterGc += usage.getUsed();
            used += pool.getUsage().getUsed();
        }
        heapPercent = 100.0 * (afterGc > 0 ? afterGc : used) / max;
        usedHeapPercent = 100.0 * used / max;
        workMs = average(workNanos, workCount) / 1e6;
        double intervalMs = average(intervalNanos, intervalCount) / 1e6;
        tps = intervalMs > 0 ? Math.min(20, 1000 / intervalMs) : 20;
        fps = clientFps.getAsInt();

        if (!cfg.throttleEnabled) {
            set(maxTasks, null, false);
            return;
        }
        if (heapPercent >= cfg.throttlePauseHeapPercent || usedHeapPercent >= USED_HEAP_PAUSE * 100) {
            set(0, String.format("memory %.0f%%", heapPercent), true);
            return;
        }
        if (tps < 10) {
            set(0, String.format("%.1f tps", tps), true);
            return;
        }

        int heapCap = maxTasks;
        if (heapPercent > cfg.throttleSlowHeapPercent && cfg.throttlePauseHeapPercent > cfg.throttleSlowHeapPercent) {
            double headroom = (cfg.throttlePauseHeapPercent - heapPercent) / (cfg.throttlePauseHeapPercent - cfg.throttleSlowHeapPercent);
            heapCap = Math.max(1, (int) (maxTasks * headroom));
        }

        String over = null;
        if (workMs > cfg.throttleTargetMspt) {
            over = String.format("tick %.0f ms", workMs);
        } else if (tps < 18) {
            over = String.format("%.1f tps", tps);
        } else if (cfg.throttleMinFps > 0 && fps >= 0 && fps < cfg.throttleMinFps) {
            over = fps + " fps";
        }

        int next = Math.min(allowed, maxTasks);
        if (over != null) {
            next = Math.max(1, next / 2);
        } else {
            next = Math.min(maxTasks, next + Math.max(1, maxTasks / 10));
        }
        if (next > heapCap) {
            next = heapCap;
            if (over == null) over = String.format("memory %.0f%%", heapPercent);
        }
        set(next, next < maxTasks ? over : null, false);
    }

    private void set(int allowed, String reason, boolean paused) {
        // log changes between running, slowed and paused, not every change of the limit
        int state = paused ? 2 : reason != null ? 1 : 0;
        int oldState = this.paused ? 2 : this.reason != null ? 1 : 0;
        if (state != oldState) {
            switch (state) {
                case 2 -> VoxyWorldGenV2.LOGGER.info("generation paused by the throttle: {}", reason);
                case 1 -> VoxyWorldGenV2.LOGGER.info("generation slowed by the throttle to {} tasks: {}", allowed, reason);
                default -> VoxyWorldGenV2.LOGGER.info("generation back at full speed");
            }
        }
        this.allowed = allowed;
        this.reason = reason;
        this.paused = paused;
    }

    private static double average(long[] values, int count) {
        if (count == 0) return 0;
        long sum = 0;
        for (int i = 0; i < count; i++) sum += values[i];
        return (double) sum / count;
    }

    public void reset() {
        workCount = intervalCount = workIndex = intervalIndex = ticksSinceEvaluate = 0;
        tickStartNanos = lastTickEndNanos = 0;
        set(Integer.MAX_VALUE, null, false);
    }

    // chunks that may generate at once right now, 0 while paused by the throttle
    public int allowedTasks() { return allowed; }
    // why generation is slowed or paused, null when it runs at maxActiveTasks
    public String reason() { return reason; }
    public boolean isPaused() { return paused; }
    public double heapPercent() { return heapPercent; }
    public double workMs() { return workMs; }
    public double tps() { return tps; }
    public int fps() { return fps; }
}

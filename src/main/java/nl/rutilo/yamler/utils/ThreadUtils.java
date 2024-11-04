package nl.rutilo.yamler.utils;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class ThreadUtils {
    private static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(100);
    private static final Duration DEFAULT_THROTTLE = Duration.ofMillis(100);
    private ThreadUtils() {}
    private static final class ThrottleInfo {
        private final Object lastResult;
        private final long lastRunTime;
        private final long shelfLife;
        private ThrottleInfo(Object lastResult, long lastRunTime, long shelfLife) {
            this.lastResult = lastResult;
            this.lastRunTime = lastRunTime;
            this.shelfLife = shelfLife;
        }
    }
    private static final class DebounceInfo {
        private final long lastRunTime;
        private final long debounceTime;
        private final long maxDelay;
        private DebounceInfo(long lastRunTime, long debounceTime, long maxDelay) {
            this.lastRunTime = lastRunTime;
            this.debounceTime = debounceTime;
            this.maxDelay = maxDelay;
        }
    }
    private static final Map<String,ThrottleInfo> throttles = new HashMap<>();
    private static final Map<String,DebounceInfo> debounces = new HashMap<>();

    static {
        TimedRunner.runPeriodicallyAtFixedDelay("pruneThrottles", Duration.ofMinutes(1), ThreadUtils::pruneThrottles);
        TimedRunner.runPeriodicallyAtFixedDelay("pruneDebounces", Duration.ofMinutes(1), ThreadUtils::pruneDebounces);
    }

    /** Remove throttle infos over their shelf life which otherwise would prevent garbage collection of lastResult */
    private static void pruneThrottles() {
        synchronized (throttles) {
            final long now = System.currentTimeMillis();
            new HashSet<>(throttles.keySet()).forEach(key -> {
               final ThrottleInfo ti = throttles.get(key);
               if(ti.lastRunTime + ti.shelfLife > now) throttles.remove(key);
            });
        }
    }
    /** Remove debounce infos that exist longer than their max delay of debounced */
    private static void pruneDebounces() {
        synchronized (debounces) {
            final long now = System.currentTimeMillis();
            new HashSet<>(debounces.keySet()).forEach(key -> {
                final DebounceInfo di = debounces.get(key);
                if(di.lastRunTime + di.maxDelay > now) debounces.remove(key);
            });
        }
    }

    public static void sleepMillis(int ms) { sleep(Duration.ofMillis(ms)); }
    public static void sleep(Duration timeToSleep) {
        try {
            Thread.sleep(timeToSleep.toMillis());
        } catch (final InterruptedException e) { // NOSONAR
            throw WrappedException.wrap(e);
        }
    }

    // debounce -> don't run until <time> has passed since the last call
    // throttle -> don't run until <time> has passed since the last run

    /** Don't call runner until 100ms has passed since the debounce was called. */
    public static void debounce(Runnable runner) {
        debounce(Reflection.getCallerId(ThreadUtils.class), DEFAULT_DEBOUNCE, runner);
    }

    /** Don't call runner until debounceTime has passed since the debounce was called. */
    public static void debounce(Duration debounceTime, Runnable runner) {
        debounce(Reflection.getCallerId(ThreadUtils.class), debounceTime, runner);
    }

    /** Don't call runner until debounceTime has passed since the debounce was called unless that
      * takes longer than maxDelay. The maxDelay was added to prevent starvation in cases where the
      * debounce is called continuously which would otherwise lead to the runner never be called.
      * A maxDelay basically makes this a debounced throttle.
      */
    public static void debounce(Duration debounceTime, Duration maxDelay, Runnable runner) {
        debounce(Reflection.getCallerId(ThreadUtils.class), debounceTime, maxDelay, runner);
    }

    /** Don't call runner until debounceTime has passed since the debounce was called. */
    public static void debounce(String id, Duration debounceTime, Runnable runner) {
        TimedRunner.runAfterDelay(id, debounceTime, runner);
    }

    /** Don't call runner until debounceTime has passed since the debounce was called, unless that
      * takes longer than maxDelay. The maxDelay was added to prevent starvation in cases where the
      * debounce is called continuously which would otherwise lead to the runner never be called.
      * A maxDelay basically makes this a debounced throttle.
      */
    public static void debounce(String id, Duration debounceTime, Duration maxDelay, Runnable runner) {
        final long now = System.currentTimeMillis();
        final boolean needsToRunNow;
        synchronized(debounces) {
            final DebounceInfo info = debounces.computeIfAbsent(id, u -> new DebounceInfo(now, debounceTime.toMillis(), maxDelay.toMillis()));
            needsToRunNow = info.maxDelay > 0 && now - info.lastRunTime > info.maxDelay;
            if (needsToRunNow) {
                TimedRunner.cancel(id);
                debounces.put(id, new DebounceInfo(now, debounceTime.toMillis(), maxDelay.toMillis()));
            }
        }
        if(needsToRunNow) {
            runner.run(); // run outside synchronized
        } else {
            TimedRunner.runAfterDelay(id, debounceTime, () -> {
                runner.run();
                debounces.put(id, new DebounceInfo(System.currentTimeMillis(), debounceTime.toMillis(), maxDelay.toMillis()));
            });
        }
    }

    /** Don't call supplier if it ran in the last 100ms, The id will be the location of
      * the caller.<br><br>
      *
      * Alias for {@link #throttle(String, Duration, boolean, Supplier)} where id is the callerId,
      * duration 100ms, addRunTimeToThrottleTime=true and supplier runs the runner.
      */
    public static void throttle(Runnable runner) {
        throttle(Reflection.getCallerId(), DEFAULT_THROTTLE, true, () -> { runner.run(); return null; });
    }

    /** Don't call supplier if it ran in the last throttleTime, The id will be the location of
      * the caller.<br><br>
      *
      * Alias for {@link #throttle(String, Duration, boolean, Supplier)} where id is the callerId,
      * addRunTimeToThrottleTime=true and supplier runs the runner.
      */
    public static void throttle(Duration throttleTime, Runnable runner) {
        throttle(Reflection.getCallerId(), throttleTime, true, () -> { runner.run(); return null; });
    }

    /** Don't call runner if it ran in the last throttleTime for the given id.<br><br>
      *
      * Alias for {@link #throttle(String, Duration, boolean, Supplier)} where
      * addRunTimeToThrottleTime=true and supplier runs the runner.
      */
    public static void throttle(String id, Duration throttleTime, Runnable runner) {
        throttle(id, throttleTime, true, () -> { runner.run(); return null; });
    }

    /** Don't call supplier if it ran in the last throttleTime, The id will be the location of
      * the caller.<br><br>
      *
      * Alias for {@link #throttle(String, Duration, boolean, Supplier)} where id is the callerId
      * and addRunTimeToThrottleTime=true.
      */
    public static <T> T throttle(Duration throttleTime, Supplier<T> supplier) {
        return throttle(Reflection.getCallerId(), throttleTime, true, supplier);
    }

    /** Don't call supplier if it ran in the last throttleTime, The id will be the location of
      * the caller.<br><br>
      *
      * Alias for {@link #throttle(String, Duration, boolean, Supplier)} where id is the callerId.
      */
    public static <T> T throttle(Duration throttleTime, boolean addRunTimeToThrottleTime, Supplier<T> supplier) {
        return throttle(Reflection.getCallerId(), throttleTime, addRunTimeToThrottleTime, supplier);
    }

    /** Don't call runnable if it ran in the last throttleTime<br><br>
      *
      * Alias for {@link #throttle(String, Duration, boolean, Supplier)} where id runner is a void supplier.
      */
    public static void throttle(String id, Duration throttleTime, boolean addRunTimeToThrottleTime, Runnable runner) {
        throttle(id, throttleTime, addRunTimeToThrottleTime, () -> { runner.run(); return null; });
    }

    /** Don't call supplier if it ran in the last throttleTime
      *
      * @param id              Unique identifier for this throttle
      * @param throttleTime    Time until the supplier can be called again
      * @param addRunTimeToThrottleTime  True to increase throttleTime by the time of the supplier
     *                         (which may be different each run). This difference
      *                        is only noticeable when the supplier can be slow.
      * @param supplier        Supplier to run throttled
      * @param <T>             Type of the result of the supplier that will be returned by this call as well
      * @return                Returns the last result of a call to the supplier. This result is cached
      *                        for throttled calls so won't be garbage collected until a next call to
      *                        the supplier.
      */
    public static <T> T throttle(String id, Duration throttleTime, boolean addRunTimeToThrottleTime, Supplier<T> supplier) {
        final long callTime = System.currentTimeMillis();
        synchronized(throttles) {
            final ThrottleInfo throttleInfo = throttles.get(id);
            if(throttleInfo != null && callTime - throttleInfo.lastRunTime < throttleTime.toMillis()) return (T)throttleInfo.lastResult;
        }
        T result = null;
        try {
            result = supplier.get();
        } finally {
            synchronized (throttles) {
                throttles.put(id, new ThrottleInfo(result, addRunTimeToThrottleTime ? System.currentTimeMillis() : callTime, throttleTime.toMillis()));
            }
        }
        return result;
    }
}

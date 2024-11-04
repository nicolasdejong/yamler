package nl.rutilo.yamler.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static nl.rutilo.yamler.utils.ThreadUtils.sleep;
import static nl.rutilo.yamler.utils.ThreadUtils.sleepMillis;

class TimedRunnerTest {
    // Some time in the future this has to be adapted to work with a temporal abstraction
    // so that tests won't fail when the machine running the tests is busy.
    // For now, to prevent this, delays are set a bit higher making this test slow.

    private static final Map<String,Boolean> hasRun = Collections.synchronizedMap(new HashMap<>());
    private static final AtomicInteger     runIndex = new AtomicInteger(0);
    private static final List<Long>        runTimes = new ArrayList<>();
    private static final AtomicLong       startTime = new AtomicLong(0);

    @BeforeEach void setup() {
        hasRun.clear();
        runIndex.set(0);
        runTimes.clear();
        startTime.set(now());
    }
    @AfterEach void cleanup() {
        TimedRunner.cancelAll();
    }

    @Test void decoratorShouldDecorate() {
        final AtomicInteger n = new AtomicInteger(0);
        TimedRunner.runAfterDelay("a", Duration.ofMillis(10), () -> n.addAndGet(100));
        sleepMillis(50);
        assertThat(n.get(), is(100));
        n.set(0);

        final UnaryOperator<Runnable> decorator = r -> () -> {
            n.addAndGet(10);
            try {
                r.run();
            } catch(final Exception e) {
                n.addAndGet(1000);
            }
            n.decrementAndGet();
        };
        TimedRunner.addDecorator(decorator);

        TimedRunner.runAfterDelay("a", Duration.ofMillis(10), () -> n.addAndGet(100));
        sleepMillis(50);
        assertThat(n.get(), is(109));
        n.set(0);

        TimedRunner.runAfterDelay("a", Duration.ofMillis(10), () -> { throw new IllegalStateException("test"); });
        sleepMillis(50);
        assertThat(n.get(), is(1009));
        n.set(0);

        TimedRunner.removeDecorator(decorator);
        TimedRunner.runAfterDelay("a", Duration.ofMillis(10), () -> n.addAndGet(100));
        sleepMillis(50);
        assertThat(n.get(), is(100));
    }

    @Test void runAtTimeShouldRun() {
        final String id = "a";
        triggerRun(id, LocalDateTime.now().plus(150, ChronoUnit.MILLIS));
        assertDidNotRun(id);
        sleepMillis(50);
        assertDidNotRun(id);
        sleepMillis(250);
        assertDidRun(id);
        triggerRun(id, LocalDateTime.now().plus(2, ChronoUnit.SECONDS));
        sleepMillis(1500);
        assertDidNotRun(id);
        sleepMillis(1000);
        assertDidRun(id);
    }

    @Test void runAfterTimeShouldRun() {
        final String id = "a";
        triggerRun(id, 200);
        triggerRun(id, 100);
        assertDidNotRun(id);
        sleepMillis(40);
        assertDidNotRun(id);
        sleepMillis(120);
        assertDidRun(id);

        triggerRun(id, 200);
        assertDidNotRun(id);
        sleepMillis(240);
        assertDidRun(id);

        triggerRun(id, 200);
        triggerRun(id, 20);
        sleepMillis(40);
        assertDidRun(id);
    }
    @Test void runAfterTimeIdsShouldNotInterfere() {
        triggerRun("b", 150);
        triggerRun("d", 20);
        triggerRun("c", 250);
        triggerRun("a", 50);
        TimedRunner.cancel("d");

        sleepMillis(80);
        assertDidRun("a");
        assertDidNotRun("b", "c", "d");
        sleepMillis(100);
        assertDidRun("b");
        assertDidNotRun("c", "d");
        sleepMillis(100);
        assertDidRun("c");
        assertDidNotRun("d");
    }
    @Test void runAfterTimeShouldNotRunWhenRemoved() {
        triggerRun("a", 60);
        TimedRunner.cancel("a");
        sleepMillis(80);
        assertDidNotRun("a");
    }
    @Test void cancelShouldPreventRuns() {
        triggerRun("a", 60);
        sleepMillis(10);
        TimedRunner.cancel("a");
        sleepMillis(100);
        assertDidNotRun("a");
    }
    @Test void cancelAllShouldPreventRuns() {
        triggerRun("a", 60);
        triggerRun("b", 100);
        sleepMillis(10);
        TimedRunner.cancelAll();
        sleepMillis(100);
        assertDidNotRun("a", "b");
    }

    @Test void runPeriodicallyAtFixedRateShouldHaveCorrectTiming() {
        TimedRunner.runPeriodicallyAtFixedRate("a", Duration.ofMillis(100), getRunner());
        sleepMillis(440);
        assertRunTimes(100, 200, 300, 400);
    }
    @Test void runPeriodicallyAtFixedRateShouldHaveCorrectInitialDelay() {
        TimedRunner.runPeriodicallyAtFixedRate("a", Duration.ofMillis(300), Duration.ofMillis(100), getRunner());
        sleepMillis(440);
        assertRunTimes(300, 400);
    }
    @Test void runPeriodicallyAtFixedRateShouldSkipSlowRuns() {
        TimedRunner.runPeriodicallyAtFixedRate("a", Duration.ofMillis(100), getRunner(2));
        sleepMillis(780);
        assertRunTimes(100, 200, 500, 600, 700);
    }

    @Test void runPeriodicallyAtFixedDelayShouldHaveCorrectTiming() {
        TimedRunner.runPeriodicallyAtFixedDelay("a", Duration.ofMillis(100), getRunner(2, 5));
        sleepMillis(1190);
        assertRunTimes(100, 5+200, 10+240+300, 15+240+400, 20+240+500, 25+240+240+600);
    }
    @Test void runPeriodicallyAtFixedDelayShouldHaveCorrectInitialDelay() {
        TimedRunner.runPeriodicallyAtFixedDelay("a", Duration.ofMillis(600), Duration.ofMillis(100), getRunner());
        sleepMillis(600+250);
        assertRunTimes(600, 700, 800);
    }

    private static final Duration RUN_DELAY = Duration.ofMillis(240);

    private static Runnable getRunner(Integer... delayIndices) { return getRunner(Stream.of(delayIndices).collect(Collectors.toList())); }
    private static Runnable getRunner(List<Integer> delayIndices) {
        return () -> {
            runTimes.add(now() - startTime.get());
            runIndex.incrementAndGet();
            if (delayIndices.contains(runIndex.get())) sleep(RUN_DELAY);
        };
    }
    private static void assertRunTimes(Integer... expectedRunTimes) {
        final int allowedJitterBelow = -4; // sometimes one or two millis below?
        final int allowedJitterAbove = 30; // depends on machine ... will occasionally fail until temporal abstraction

        // This assertThat will always fail due to time jitter, so it is only called at failure
        final Consumer<String> fail = msg ->
            assertThat(msg, runTimes, is(List.of(expectedRunTimes)));
        if(expectedRunTimes.length != runTimes.size()) fail.accept("Incorrect number or runs");
        for(int i=0; i<expectedRunTimes.length; i++) {
            if(runTimes.get(i) - expectedRunTimes[i] < allowedJitterBelow) fail.accept("Too much jitter below: " + (runTimes.get(i) - expectedRunTimes[i]));
            if(runTimes.get(i) - expectedRunTimes[i] > allowedJitterAbove) fail.accept("Too much jitter above: " + (runTimes.get(i) - expectedRunTimes[i]));
        }
        //System.out.println("runTimes:" + runTimes + " expected: " + List.of(expectedRunTimes));
    }

    private static void assertDidNotRun(String... ids) {
        for(final String id : ids) {
            assertThat(hasRun.computeIfAbsent(id, s -> false), is(false));
        }
    }
    private static void assertDidRun(String... ids) {
        for (final String id : ids) {
            assertThat(hasRun.computeIfAbsent(id, s -> false), is(true));
            resetRun(id);
        }
    }
    private static void storeRun(String id) { hasRun.put(id, true); }
    private static void resetRun(String id) { hasRun.put(id, false); }
    private static void triggerRun(String id, int ms) { TimedRunner.runAfterDelay(id, Duration.ofMillis(ms), () -> storeRun(id)); }
    private static void triggerRun(String id, LocalDateTime dt) { TimedRunner.runAtTime(id, dt, () -> storeRun(id)); }
    private static long now() { return System.currentTimeMillis(); }
}
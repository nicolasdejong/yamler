package nl.rutilo.yamler.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static nl.rutilo.yamler.testutils.SilverMatchers.is;
import static nl.rutilo.yamler.testutils.SilverMatchers.isApproximately;
import static nl.rutilo.yamler.testutils.SilverMatchers.isLargerThan;
import static nl.rutilo.yamler.utils.ThreadUtils.sleepMillis;

class ThreadUtilsTest {

    private long resetToCurrentTimeAndReturnDelta(long[] value) {
        final long now = System.currentTimeMillis();
        final long delta = value[0] <= 0 ? now : now - value[0];
        value[0] = now;
        return delta;
    }

    @Test void debounce() {
        final Duration debounceTime = Duration.ofMillis(100);
        final long[] lastCallTime = { 0 };
        final int[] callCount = { 0 };
        final List<Long> timesSinceLastCall = new ArrayList<>();
        final Runnable runner = () -> {
            callCount[0]++;
            final long timeSinceLastCall = resetToCurrentTimeAndReturnDelta(lastCallTime);
            timesSinceLastCall.add(timeSinceLastCall);
        };

        for(int i=0; i<20; i++) {
            ThreadUtils.debounce(debounceTime, runner);
            sleepMillis(i == 10 ? 150 : 50);
        }
        assertThat(callCount[0], is(1));
        ThreadUtils.sleep(debounceTime.plusMillis(200));
        assertThat(callCount[0], is(2));

        timesSinceLastCall.forEach(timeSinceLastCall -> {
            assertThat("debounce didn't work", timeSinceLastCall, isLargerThan(debounceTime.toMillis()));
        });
    }
    @Test void debounceShouldNotStarve() {
        final Duration debounceTime = Duration.ofMillis(100);
        final Duration maxDelay = Duration.ofMillis(500);
        final int[] callCount = { 0 };
        final Runnable runner = () -> callCount[0]++;

        for(int i=0; i<21; i++) {
            ThreadUtils.debounce(debounceTime, maxDelay, runner);
            sleepMillis(50);
        }
        assertThat(callCount[0], is(2));
    }

    final AtomicInteger num = new AtomicInteger(0);
    private void increaseNumThrottled() {
        ThreadUtils.throttle(Duration.ofMillis(100), num::incrementAndGet);
    }

    @Test void throttle() {
        num.set(0);
        for(int i=0; i<50; i++) {
            increaseNumThrottled();
            sleepMillis(20);
        }
        assertThat(num.get(), isApproximately(10, 2));
    }

    @Test void throttleReturnValueOfLastRun() {
        num.set(0);
        final String id = "a";
        final Duration throttleTime = Duration.ofMillis(100);
        final Supplier<Integer> toRun = () -> {
            num.incrementAndGet();
            return num.get();
        };
        final List<Integer> results = new ArrayList<>();
        for(int i=0; i<25; i++) {
            results.add(ThreadUtils.throttle(id, throttleTime, true, toRun));
            sleepMillis(40);
        }
        assertThat(num.get(), isApproximately(10, 2));
        assertThat(
            results.stream().map(Object::toString).collect(Collectors.joining(",")),
            is("1,1,1,2,2,2,3,3,3,4,4,4,5,5,5,6,6,6,7,7,7,8,8,8,9")
        );
    }
}
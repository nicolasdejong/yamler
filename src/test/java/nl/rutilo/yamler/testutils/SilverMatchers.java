package nl.rutilo.yamler.testutils;

import nl.rutilo.yamler.utils.StringUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.Map;
import java.util.function.Function;

public final class SilverMatchers {
    private SilverMatchers() {}

    private static class MoreMatcher<T> extends BaseMatcher<T> {
        private final T expected;
        private final Function<T,Boolean> matcher;
        private final String description;

        public MoreMatcher(T expected, Function<T,Boolean> matcher) {
            this(expected, matcher, "");
        }

        public MoreMatcher(T expected, Function<T,Boolean> matcher, String description) {
            this.expected = expected;
            this.matcher = matcher;
            this.description = description == null || description.trim().isEmpty() ? "" : description;
        }

        @Override
        public void describeMismatch(Object item, Description description) {
            description.appendValue(item);
        }

        @Override
        public void describeTo(Description descriptionInfo) {
            if(description.contains("${")) {
                final Map<String,String> info = Map.of(
                    "expected", String.valueOf(expected)
                );
                descriptionInfo.appendText(
                    StringUtils.replaceRegex(description, "\\$\\{([^}]+?)\\}", groups -> {
                        return info.getOrDefault(groups[1], "?{" + groups[1] + "}");
                    })
                );
            } else {
                descriptionInfo.appendValue(expected).appendText(" " + description);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean matches(Object given) {
            if(expected == null) return given == null;
            if(!isCorrectClass(given)) return false;

            return matcher.apply((T)given);
        }

        protected boolean isCorrectClass(Object given) {
            return given != null && (expected instanceof Number || expected.getClass().isAssignableFrom(given.getClass()));
        }
    }
    private static class MoreNumMatcher<T extends Number> extends MoreMatcher<T> {
        public MoreNumMatcher(T expected, Function<Long,Boolean> longMatcher, Function<Double,Boolean> doubleMatcher) {
            this(expected, longMatcher, doubleMatcher, "");
        }
        public MoreNumMatcher(T expected, Function<Long,Boolean> longMatcher, Function<Double,Boolean> doubleMatcher, String extraDescription) {
            super(expected, num -> {
                return (expected instanceof Double || expected instanceof Float)
                    ? doubleMatcher.apply((Double)num)
                    : longMatcher.apply(num.longValue());
            }, extraDescription);
        }
    }

    public static <T> Matcher<T> is(Matcher<T> matcher) {
        return IsMatcher.is(matcher);
    }

    public static <T> Matcher<T> is(T value) {
        return IsMatcher.is(value);
    }

    public static <T> Matcher<T> isA(Class<T> type) {
        return IsMatcher.isA(type);
    }

    public static <T extends Number> Matcher<T> isSmallerThan(T expectedBelow) {
        return new MoreNumMatcher<>(expectedBelow,
            givenLong   -> givenLong   < expectedBelow.longValue(),
            givenDouble -> givenDouble < expectedBelow.doubleValue(),
            "below ${expected}"
        );
    }
    public static <T extends Number> Matcher<T> isLargerThan(T expectedAbove) {
        return new MoreNumMatcher<>(expectedAbove,
            givenLong   -> givenLong   > expectedAbove.longValue(),
            givenDouble -> givenDouble > expectedAbove.doubleValue(),
            "above <${expected}>"
        );
    }

    public static <T extends Number> Matcher<T> isApproximately(T expected, T allowedDeviation) {
        return new MoreNumMatcher<>(expected,
            givenLong   -> givenLong   >= expected.longValue()   - allowedDeviation.longValue()
                        && givenLong   <= expected.longValue()   + allowedDeviation.longValue(),
            givenDouble -> givenDouble >= expected.doubleValue() - allowedDeviation.doubleValue()
                        && givenDouble <= expected.doubleValue() + allowedDeviation.doubleValue(),
            "plus or minus " + allowedDeviation
        );
    }
}

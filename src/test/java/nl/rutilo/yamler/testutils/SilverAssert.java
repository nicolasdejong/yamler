package nl.rutilo.yamler.testutils;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

public final class SilverAssert {
    public SilverAssert() {
    }

    public static <T> void assertThat(T actual, Matcher<? super T> matcher) {
        assertThat("", actual, matcher);
    }

    public static <T> void assertThat(String reason, T actual, Matcher<? super T> matcher) {
        if (!matcher.matches(actual)) {
            final Description description = new StringDescription();
            description
                .appendText(reason)
                .appendText("\nExpected: ")
                .appendDescriptionOf(matcher)
                .appendText("\n  Result: ");
            matcher.describeMismatch(actual, description);
            throw new AssertionError(description.toString());
        }
    }

    public static void assertThat(String reason, boolean assertion) {
        if (!assertion) throw new AssertionError(reason);
    }
}

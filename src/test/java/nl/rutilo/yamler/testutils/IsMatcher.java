package nl.rutilo.yamler.testutils;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;

// Override Hamcrest Is class to insert a space before the "is" so " is" and "was" are aligned
// TODO: can this be merged into MoreMatchers?
@SuppressWarnings("unchecked")
public class IsMatcher<T> extends Is<T> {
    private final Matcher<T> matcher;

    public IsMatcher(Matcher<T> matcher) {
        super(matcher);
        this.matcher = matcher;
    }

    public void describeTo(Description description) {
        description.appendText(" is ").appendDescriptionOf(this.matcher);
    }

    public static <T> Matcher<T> is(Matcher<T> matcher) {
        return new IsMatcher<>(matcher);
    }

    public static <T> Matcher<T> is(T value) {
        return is(IsEqual.equalTo(value));
    }

    public static <T> Matcher<T> isA(Class<?> type) {
        return is(IsInstanceOf.instanceOf(type));
    }
}

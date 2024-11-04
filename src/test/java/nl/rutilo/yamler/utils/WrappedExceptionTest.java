package nl.rutilo.yamler.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;

class WrappedExceptionTest {

    @Test void isChecked() {
        assertThat(new WrappedException(new IOException()).isChecked(), is(true));
        assertThat(new WrappedException(new IllegalArgumentException()).isChecked(), is(false));
    }

    @Test void wrap() {
        assertThat(WrappedException.wrap(new WrappedException(new IllegalArgumentException())).wrapped instanceof IllegalArgumentException, is(true));
        assertThat(WrappedException.wrap(new IllegalArgumentException()).wrapped instanceof IllegalArgumentException, is(true));
    }

    @Test void unwrap() {
        assertThat(WrappedException.unwrap(new IllegalArgumentException()) instanceof IllegalArgumentException, is(true));
        assertThat(WrappedException.unwrap(new WrappedException(new IllegalArgumentException())) instanceof IllegalArgumentException, is(true));
    }

    @Test void toRuntimeException() {
        assertThat(WrappedException.toRuntimeException(new IOException()) instanceof WrappedException, is(true));
        assertThat(WrappedException.toRuntimeException(new IllegalArgumentException()) instanceof WrappedException, is(false));
    }
}
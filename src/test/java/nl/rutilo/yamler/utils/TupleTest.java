package nl.rutilo.yamler.utils;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class TupleTest {

    @Test void of2() {
        final Tuple.Tuple2<String,String> tuple = Tuple.of("a","b");
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
    }

    @Test void of3() {
        final Tuple.Tuple3<String,String,Integer> tuple = Tuple.of("a","b",3);
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
        assertThat(tuple.c, is(3));
    }

    @Test void of4() {
        final Tuple.Tuple4<String,String,Integer,Integer> tuple = Tuple.of("a","b",3,4);
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
        assertThat(tuple.c, is(3));
        assertThat(tuple.d, is(4));
    }

    @Test void of5() {
        final Tuple.Tuple5<String,String,Integer,Integer,Boolean> tuple = Tuple.of("a","b",3,4,true);
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
        assertThat(tuple.c, is(3));
        assertThat(tuple.d, is(4));
        assertThat(tuple.e, is(true));
    }

    @Test void ofMapEntry() {
        assertThat(Tuple.of(new AbstractMap.SimpleEntry<>("a",1)), is(Tuple.of("a",1)));
    }

    @Test void of2vararg() {
        final Tuple.Tuple2<String,String> tuple = Tuple.of2("a", "b", "c");
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
        final Tuple.Tuple2<String,String> tupleB = Tuple.of2("a");
        assertThat(tupleB.a, is("a"));
        assertThat(tupleB.b, nullValue());
        final Tuple.Tuple2<String,String> tupleC = Tuple.of2();
        assertThat(tupleC.a, nullValue());
        assertThat(tupleC.b, nullValue());
    }

    @Test void of3vararg() {
        final Tuple.Tuple3<String,String,String> tuple = Tuple.of3("a", "b", "c");
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
        assertThat(tuple.c, is("c"));
        final Tuple.Tuple3<String,String,String> tupleB = Tuple.of3("a", "b");
        assertThat(tupleB.a, is("a"));
        assertThat(tupleB.b, is("b"));
        assertThat(tupleB.c, nullValue());
        final Tuple.Tuple3<String,String,String> tupleC = Tuple.of3("a");
        assertThat(tupleC.a, is("a"));
        assertThat(tupleC.b, nullValue());
        assertThat(tupleC.c, nullValue());
        final Tuple.Tuple3<String,String,String> tupleD = Tuple.of3();
        assertThat(tupleD.a, nullValue());
        assertThat(tupleD.b, nullValue());
        assertThat(tupleD.c, nullValue());
    }

    @Test void of4vararg() {
        final Tuple.Tuple4<String,String,String,String> tuple = Tuple.of4("a", "b", "c", "d");
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
        assertThat(tuple.c, is("c"));
        assertThat(tuple.d, is("d"));
        final Tuple.Tuple4<String,String,String,String> tupleB = Tuple.of4("a", "b", "c");
        assertThat(tupleB.a, is("a"));
        assertThat(tupleB.b, is("b"));
        assertThat(tupleB.c, is("c"));
        assertThat(tupleB.d, nullValue());
        final Tuple.Tuple4<String,String,String,String> tupleC = Tuple.of4("a", "b");
        assertThat(tupleC.a, is("a"));
        assertThat(tupleC.b, is("b"));
        assertThat(tupleC.c, nullValue());
        assertThat(tupleC.d, nullValue());
        final Tuple.Tuple4<String,String,String,String> tupleD = Tuple.of4("a");
        assertThat(tupleD.a, is("a"));
        assertThat(tupleD.b, nullValue());
        assertThat(tupleD.c, nullValue());
        assertThat(tupleD.d, nullValue());
        final Tuple.Tuple4<String,String,String,String> tupleE = Tuple.of4();
        assertThat(tupleE.a, nullValue());
        assertThat(tupleE.b, nullValue());
        assertThat(tupleE.c, nullValue());
        assertThat(tupleE.d, nullValue());
    }

    @Test void of5vararg() {
        final Tuple.Tuple5<String,String,String,String,String> tuple = Tuple.of5("a", "b", "c", "d", "e");
        assertThat(tuple.a, is("a"));
        assertThat(tuple.b, is("b"));
        assertThat(tuple.c, is("c"));
        assertThat(tuple.d, is("d"));
        assertThat(tuple.e, is("e"));
        final Tuple.Tuple5<String,String,String,String,String> tupleB = Tuple.of5("a", "b", "c", "d");
        assertThat(tupleB.a, is("a"));
        assertThat(tupleB.b, is("b"));
        assertThat(tupleB.c, is("c"));
        assertThat(tupleB.d, is("d"));
        assertThat(tupleB.e, nullValue());
        final Tuple.Tuple5<String,String,String,String,String> tupleC = Tuple.of5("a", "b", "c");
        assertThat(tupleC.a, is("a"));
        assertThat(tupleC.b, is("b"));
        assertThat(tupleC.c, is("c"));
        assertThat(tupleC.d, nullValue());
        assertThat(tupleC.e, nullValue());
        final Tuple.Tuple5<String,String,String,String,String> tupleD = Tuple.of5("a", "b");
        assertThat(tupleD.a, is("a"));
        assertThat(tupleD.b, is("b"));
        assertThat(tupleD.c, nullValue());
        assertThat(tupleD.d, nullValue());
        assertThat(tupleD.e, nullValue());
        final Tuple.Tuple5<String,String,String,String,String> tupleE = Tuple.of5("a");
        assertThat(tupleE.a, is("a"));
        assertThat(tupleE.b, nullValue());
        assertThat(tupleE.c, nullValue());
        assertThat(tupleE.d, nullValue());
        assertThat(tupleE.e, nullValue());
        final Tuple.Tuple5<String,String,String,String,String> tupleF = Tuple.of5();
        assertThat(tupleF.a, nullValue());
        assertThat(tupleF.b, nullValue());
        assertThat(tupleF.c, nullValue());
        assertThat(tupleF.d, nullValue());
        assertThat(tupleF.e, nullValue());
    }
}
package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.utils.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;


class YamlParserTest extends BaseYamlTest {
    private Object parse(String s) { return Value.ofNullable(new YamlParser(s).parse()).map(YamlDocuments::value).orElse(null); }
    
    @Test void jsonNull() {
        assertThat(parse("null"), is(nullValue()));
        assertThat(parse("undefined"), is(nullValue()));
    }
    @Test void jsonString() {
        assertThat(parse("\"test abc\""), is("test abc"));
        assertThat(parse("\"test\\n\\\"abc\""), is("test\n\"abc"));
    }
    @Test void jsonNumber() {
        assertThat(parse("123"), is(123));
    }
    @Test void jsonList() {
        assertThat(parse("[\"abc\", 123, \"def\", 456.78]"), is(listOf("abc", 123, "def", 456.78d)));
        assertThat(parse("[\"a\",null,\"b\",,123,]"), is(listOf("a",null,"b",null,123)));
        assertThat(parse("[1,,3,,5,]"), is(listOf(1,null,3,null,5)));
        assertThat(parse("[\"a\",\"b\",[\"aa\",\"bb\",[\"aaa\",\"bbb\"],\"cc\",[\"aaaa\",\"bbbb\"]],\"c\"]\n"),
            is(List.of("a","b",List.of("aa","bb",List.of("aaa","bbb"),"cc",List.of("aaaa","bbbb")), "c")));
    }
    @Test void jsonMap() {
        assertThat(parse("{\"a\":1,\"b\":2}"), is(mapOf("a", 1, "b", 2)));
        assertThat(parse("{\"a\":1,\"b\":2,}"), is(mapOf("a", 1, "b", 2)));
        assertThat(parse("{a:}"), is(mapOf("a", null)));
        assertThat(parse("{a:,}"), is(mapOf("a", null)));
        assertThat(parse("{:b}"), is(mapOf(null,"b")));
        assertThat(parse("{:b,}"), is(mapOf(null,"b")));

        assertThat(parse("{'a':1,'b':{'b1':11,'b2':22},'c':3}"),
            is(mapOf("a",1, "b",mapOf("b1",11,"b2",22), "c",3))
        );
    }
    @Test void jsonCombined() {
        assertThat(parse("[{\"a\":1,\"b\":[\"c\",{\"d\":3,\"e\":4}]}, {\"f\":\"fff\"}]"),
            is(listOf(mapOf("a",1,"b",listOf("c",mapOf("d",3,"e",4))),mapOf("f","fff"))));
    }
    
    @Test void list_basic() {
        assertThat(parse("- a\n- b\n- c"), is(List.of("a","b","c")));
    }
    @Test void list_nested() {
        assertThat(parse("- a\n- - b1\n  - b2\n- c"),
            is(List.of("a",List.of("b1","b2"),"c")));
    }
    @Test void list_emptyItems() {
        assertThat(parse("- a\n-\n- c"), is(listOf("a",null,"c")));
        assertThat(parse("- a\n- !!str null\n- c\n"), is(listOf("a", "", "c")));
    }

    @Test void map_basic() {
        assertThat(parse("a: 1\nb: 2\nc: 3"), is(Map.of("a",1, "b",2, "c",3)));
    }
    @Test void map_nested() {
        assertThat(parse(
            "a: 1\n"
          + "b: b1: 11\n"
          + "   b2: 22\n"
          + "c: 3"),
            is(Map.of("a",1, "b",Map.of("b1",11, "b2",22), "c",3)));

        assertThat(parse(
            "- a\n"
          + "- ['b1','b2']: [1,2,3]\n"
          + "- ['c1','c2']: [11,22,33]\n"
          + "- d"),
            is(List.of("a",Map.of(List.of("b1","b2"),List.of(1,2,3)),
                           Map.of(List.of("c1","c2"),List.of(11,22,33)),
                       "d")));
    }
    @Test void map_nestedListWithMaps() {
        assertThat(parse("a: 1\nb: - { b1: 11 }\n   - { b2: 22 }\nc: 3"),
            is(Map.of("a",1, "b",List.of(Map.of("b1",11), Map.of("b2",22)), "c",3)));
    }
    @Test void map_itemContainingListOfMaps() {
        assertThat(parse("map:\n- a: 1\n- b: 2\n- c: 3"),
            is(Map.of("map", List.of(Map.of("a",1), Map.of("b",2), Map.of("c",3)))));
    }

    @Test void map_emptyKey() {
        assertThat(parse("a: 1\n: 2\nc: 3"),
            is(mapOf("a",1, null,2, "c",3)));
        assertThat(parse(
            "map:\n" +
            " ? : aval\n" +
            " ? bkey : bval\n" +
            ""), is(mapOf("map", mapOf(null, "aval", "bkey", "bval"))));
    }
    @Test void map_emptyValue() {
        assertThat(parse("a: 1\nb:\nc: 3"),
            is(mapOf("a",1, "b",null, "c",3)));
        assertThat(parse(
            "a: 1\n" +
            "b:\n" +
            "c: # empty\n" +
            "d:\n" +
            "e: 5"),
            is(mapOf("a",1, "b",null, "c",null, "d",null, "e",5)));
        assertThat(parse(
            "list:\n" +
            "- a\n" +
            "- !!str null\n" +
            "- c\n"), is(mapOf("list", listOf("a", "", "c"))));
        assertThat(parse(
            "map:\n" +
            " ? akey\n" +
            " ? bkey : bval\n" +
            ""), is(mapOf("map", mapOf("akey", null, "bkey", "bval"))));
        assertThat(parse(
            "map:\n" +
            " ? akey\n" +
            " bkey : bval\n" +
            ""), is(mapOf("map", mapOf("akey", null, "bkey", "bval"))));
    }
    @Test void map_emptyKeyAndValue() {
        assertThat(parse("a: 1\n:\nc: 3"), is(mapOf("a",1, null,null, "c",3)));

        assertThat(parse(
            "foo : \n" +
            " : bar\n" +
            "list:\n" +
            "- a\n" +
            "- b\n"),
            is(mapOf("foo",null, null,"bar","list",List.of("a","b"))));
    }

    @Test void yamlExplicit() {
        final String input =
            "!!map {\n" +
            "  ? !!str `sequence`\n" +
            "  : !!seq [ !!str `one`, !!str `two` ],\n" +
            "  ? !!str `mapping`\n" +
            "  : !!map {\n" +
            "    ? !!str `sky` : !!str `blue`,\n" +
            "    ? !!str `sea` : !!str `green`,\n" +
            "  },\n" +
            "}\n";
        assertThat(parse(input),
            is(mapOf(
                "sequence", List.of("one","two"),
                "mapping", mapOf("sky","blue", "sea","green")
            )));
    }
    @Test void yamlExplicitComplexKey() {
        final String input =
            "!!seq [\n" +
            "  !!map {\n" +
            "     !!str `sun` : !!str `yellow`,\n" +
            "  },\n" +
            "  !!map {\n" +
            "    ? !!map {\n" +
            "      ? !!str `earth`\n" +
            "      : !!str `blue`\n" +
            "    }\n" + // a comma here makes a null value and next null key
            "    : !!map {\n" +
            "      ? !!str `moon`\n" +
            "      : !!str `white`\n" +
            "    },\n" +
            "  }\n" +
            "]\n";
        assertThat(parse(input),
            is(List.of(
                    mapOf("sun", "yellow"),
                    mapOf(mapOf("earth","blue"), mapOf("moon","white"))
                )
            ));
    }

    // YAML list & map are tested from fragments file
    // It uses json to compare against, which was tested above.
    // This because Java doesn't have convenient strings.
    // YAML basics are tested above. More in fragments file.

    @Test
    void testYamlFragmentsFromFile() throws IOException {
        testYamlFragmentsFromFile("/test-yaml-fragments.txt", 'y');
    }
}

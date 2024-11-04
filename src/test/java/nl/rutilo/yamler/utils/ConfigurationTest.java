package nl.rutilo.yamler.utils;

import nl.rutilo.yamler.utils.Configuration.FqnConfigItem;
import nl.rutilo.yamler.utils.Configuration.VarDeclDefaultModifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static nl.rutilo.yamler.utils.Configuration.VarDeclDefaultModifier.FINAL;
import static nl.rutilo.yamler.utils.Configuration.VarDeclDefaultModifier.MUTABLE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigurationTest {

    @Test void defaultPaksShouldBeReadCorrectly() {
        final String cfg = String.join("\r\n", List.of(
          "# some remarks",
          "# some more remarks",
          "",
          "defaults:   ",
          " *:final",
          " some.*.pak.and.Type: mutable",
          " some.* : final",
          "",
          "something else"
        ));
        final List<FqnConfigItem> items = new Configuration(cfg).fqnDefaultModifiers;

        assertThat(items.size(), is(3));

        assertThat(items.get(0).fqnDefault, is(FINAL));
        assertThat(items.get(1).fqnDefault, is(MUTABLE));
        assertThat(items.get(2).fqnDefault, is(FINAL));

        assertThat(items.get(0).fqnPattern.pattern(), is(".*"));
        assertThat(items.get(1).fqnPattern.pattern(), is("(\\Qsome.\\E).*(\\Q.pak.and.Type\\E)"));
        assertThat(items.get(2).fqnPattern.pattern(), is("(\\Qsome.\\E).*"));
    }
    @Test void mostSignificantPakShouldBeChosen() {
        final String cfgText = String.join("\r\n", List.of(
          "defaults:   ",
          " *:final",
          " a.b.c.Type:mutable",
          " a.*.Type:final",
          " a.*:MUTABLE",
          " a.b.* : FINAL"
        ));
        final Configuration cfg = new Configuration(cfgText);

        assertThat(cfg.fqnDefaultModifiers.size(), is(5));
        assertThat(cfg.getDefaultForType("foo.bar"), is(FINAL));
        assertThat(cfg.getDefaultForType("a.Type"), is(MUTABLE));
        assertThat(cfg.getDefaultForType("a.b.Type"), is(FINAL));
        assertThat(cfg.getDefaultForType("a.b.c.Type"), is(MUTABLE));

        assertThat(modifierForType(List.of(
          "a.b.*.Type:FINAL",
          "a.b.c.*Type:MUTABLE"
        ), "a.b.c.d.Type"), is(MUTABLE));
        assertThat(modifierForType(List.of(
          "a.b.*.Type:FINAL",
          "a.b.*.z.Type:MUTABLE"
        ), "a.b.c.d.z.Type"), is(MUTABLE));
        assertThat(modifierForType(List.of(
          "a.b.*Type:FINAL",
          "a.b.*.e.*Type:MUTABLE"
        ), "a.b.c.d.e.z.SomeType"), is(MUTABLE));
    }

    private VarDeclDefaultModifier modifierForType(List<String> defCfg, String fqnType) {
        final String cfgText =
          "defaults:\n" + defCfg.stream().map(s -> " " + s).collect(Collectors.joining("\n"));
        final Configuration cfg = new Configuration(cfgText);
        assertThat(cfg.fqnDefaultModifiers.size(), is(defCfg.size()));
        return cfg.getDefaultForType(fqnType);
    }
}
package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraDeOverallTest {

    @Test
    void deveSomarOsAtributosPonderadosPelosPesosDoPerfil() {
        // 80 × 0.5 + 60 × 0.5 = 70
        var atributos = atributosCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, 80,
                AtributoAvaliavel.RITMO, 60));
        var perfil = perfilCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, "0.5000",
                AtributoAvaliavel.RITMO, "0.5000"));

        var overall = CalculadoraDeOverall.calcular(atributos, perfil);

        assertThat(overall).isEqualTo(70);
    }

    // A consequência que o spec do catálogo destaca como desejada.
    @Test
    void deveDarNotasDiferentesAoMesmoJogadorEmPosicoesDiferentes() {
        var zagueiroDeOficio = atributosCom(Map.of(
                AtributoAvaliavel.MARCACAO, 88,
                AtributoAvaliavel.DESARME, 86,
                AtributoAvaliavel.CABECEIO, 84,
                AtributoAvaliavel.FORCA, 82,
                AtributoAvaliavel.CRUZAMENTO, 40,
                AtributoAvaliavel.RITMO, 62,
                AtributoAvaliavel.FOLEGO, 65));

        var comoZagueiro = CalculadoraDeOverall.calcular(zagueiroDeOficio, perfilDeZagueiro());
        var comoLateral = CalculadoraDeOverall.calcular(zagueiroDeOficio, perfilDeLateral());

        assertThat(comoZagueiro).isGreaterThan(comoLateral);
    }

    @Test
    void deveDevolverNoventaENoveQuandoTodosOsAtributosSaoMaximos() {
        var perfeito = atributosUniformes(99);

        var overall = CalculadoraDeOverall.calcular(perfeito, perfilDeZagueiro());

        assertThat(overall).isEqualTo(99);
    }

    @Test
    void deveDevolverZeroQuandoTodosOsAtributosSaoZero() {
        var nulo = atributosUniformes(0);

        var overall = CalculadoraDeOverall.calcular(nulo, perfilDeZagueiro());

        assertThat(overall).isZero();
    }

    @Test
    void deveArredondarMeioParaCima() {
        // 71 × 0.5 + 70 × 0.5 = 70.5 → 71
        var atributos = atributosCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, 71,
                AtributoAvaliavel.RITMO, 70));
        var perfil = perfilCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, "0.5000",
                AtributoAvaliavel.RITMO, "0.5000"));

        var overall = CalculadoraDeOverall.calcular(atributos, perfil);

        assertThat(overall).isEqualTo(71);
    }

    @Test
    void deveFalharQuandoOPerfilNaoDeclaraUmDosAtributos() {
        var perfilIncompleto = new EnumMap<AtributoAvaliavel, BigDecimal>(AtributoAvaliavel.class);
        perfilIncompleto.put(AtributoAvaliavel.RITMO, BigDecimal.ONE);

        assertThatThrownBy(() -> CalculadoraDeOverall.calcular(atributosUniformes(70), perfilIncompleto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORCA");
    }

    // jogador_atributo declara as 18 colunas como not null: atributo nulo é erro de
    // dado, não caso a tratar. Falhar alto evita overall silenciosamente errado.
    @Test
    void deveFalharQuandoUmAtributoVemNulo() {
        var comNulo = new AtributosJogador(
                null, 70, 70, 70, 70, 70, 70, 70, 70,
                70, 70, 70, 70, 70, 70, 70, 70, 70, 80, "IMPORTADO");

        assertThatThrownBy(() -> CalculadoraDeOverall.calcular(comNulo, perfilDeZagueiro()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RITMO");
    }

    private static AtributosJogador atributosUniformes(int valor) {
        return new AtributosJogador(
                valor, valor, valor, valor, valor, valor, valor, valor, valor,
                valor, valor, valor, valor, valor, valor, valor, valor, valor,
                90, "IMPORTADO");
    }

    private static AtributosJogador atributosCom(Map<AtributoAvaliavel, Integer> valores) {
        var base = new EnumMap<AtributoAvaliavel, Integer>(AtributoAvaliavel.class);
        for (var atributo : AtributoAvaliavel.values()) {
            base.put(atributo, valores.getOrDefault(atributo, 0));
        }
        return new AtributosJogador(
                base.get(AtributoAvaliavel.RITMO), base.get(AtributoAvaliavel.FORCA),
                base.get(AtributoAvaliavel.FOLEGO), base.get(AtributoAvaliavel.SALTO),
                base.get(AtributoAvaliavel.AGILIDADE), base.get(AtributoAvaliavel.PASSE),
                base.get(AtributoAvaliavel.DRIBLE), base.get(AtributoAvaliavel.CRUZAMENTO),
                base.get(AtributoAvaliavel.FRIEZA), base.get(AtributoAvaliavel.FINALIZACAO),
                base.get(AtributoAvaliavel.CABECEIO), base.get(AtributoAvaliavel.FALTA),
                base.get(AtributoAvaliavel.PENALTI), base.get(AtributoAvaliavel.DESARME),
                base.get(AtributoAvaliavel.MARCACAO), base.get(AtributoAvaliavel.GOL_REFLEXO),
                base.get(AtributoAvaliavel.GOL_POSICIONAMENTO), base.get(AtributoAvaliavel.GOL_MANEJO),
                90, "IMPORTADO");
    }

    /** Completa com zero os atributos não informados, como o seed faz. */
    private static Map<AtributoAvaliavel, BigDecimal> perfilCom(Map<AtributoAvaliavel, String> pesos) {
        var perfil = new EnumMap<AtributoAvaliavel, BigDecimal>(AtributoAvaliavel.class);
        for (var atributo : AtributoAvaliavel.values()) {
            perfil.put(atributo, new BigDecimal(pesos.getOrDefault(atributo, "0.0000")));
        }
        return perfil;
    }

    private static Map<AtributoAvaliavel, BigDecimal> perfilDeZagueiro() {
        return perfilCom(Map.ofEntries(
                Map.entry(AtributoAvaliavel.RITMO, "0.0600"),
                Map.entry(AtributoAvaliavel.FORCA, "0.1400"),
                Map.entry(AtributoAvaliavel.FOLEGO, "0.0100"),
                Map.entry(AtributoAvaliavel.SALTO, "0.0900"),
                Map.entry(AtributoAvaliavel.AGILIDADE, "0.0200"),
                Map.entry(AtributoAvaliavel.PASSE, "0.0700"),
                Map.entry(AtributoAvaliavel.FRIEZA, "0.0400"),
                Map.entry(AtributoAvaliavel.CABECEIO, "0.1300"),
                Map.entry(AtributoAvaliavel.DESARME, "0.2000"),
                Map.entry(AtributoAvaliavel.MARCACAO, "0.2400")));
    }

    private static Map<AtributoAvaliavel, BigDecimal> perfilDeLateral() {
        return perfilCom(Map.ofEntries(
                Map.entry(AtributoAvaliavel.RITMO, "0.1400"),
                Map.entry(AtributoAvaliavel.FORCA, "0.0400"),
                Map.entry(AtributoAvaliavel.FOLEGO, "0.1300"),
                Map.entry(AtributoAvaliavel.AGILIDADE, "0.0700"),
                Map.entry(AtributoAvaliavel.PASSE, "0.1000"),
                Map.entry(AtributoAvaliavel.DRIBLE, "0.0600"),
                Map.entry(AtributoAvaliavel.CRUZAMENTO, "0.1500"),
                Map.entry(AtributoAvaliavel.FRIEZA, "0.0200"),
                Map.entry(AtributoAvaliavel.DESARME, "0.1300"),
                Map.entry(AtributoAvaliavel.MARCACAO, "0.1600")));
    }
}

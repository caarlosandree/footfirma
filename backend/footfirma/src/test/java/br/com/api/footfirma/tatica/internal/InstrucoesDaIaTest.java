package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.Pressao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstrucoesDaIaTest {

    @Test
    void deveDarMentalidadeOfensivaAoClubeMuitoAcimaDaMedia() {
        assertThat(EscolhedorDeInstrucoes.mentalidadeDe(88, 55.0))
                .isEqualTo(Mentalidade.MUITO_OFENSIVA);
    }

    @Test
    void deveDarMentalidadeDefensivaAoClubeMuitoAbaixoDaMedia() {
        assertThat(EscolhedorDeInstrucoes.mentalidadeDe(35, 55.0))
                .isEqualTo(Mentalidade.MUITO_DEFENSIVA);
    }

    @Test
    void deveDarMentalidadeEquilibradaAoClubeNaMedia() {
        assertThat(EscolhedorDeInstrucoes.mentalidadeDe(55, 55.0))
                .isEqualTo(Mentalidade.EQUILIBRADA);
    }

    @Test
    void deveOrdenarAsMentalidadesPelaDistanciaDaMedia() {
        var muitoAbaixo = EscolhedorDeInstrucoes.mentalidadeDe(30, 55.0);
        var abaixo = EscolhedorDeInstrucoes.mentalidadeDe(48, 55.0);
        var naMedia = EscolhedorDeInstrucoes.mentalidadeDe(55, 55.0);
        var acima = EscolhedorDeInstrucoes.mentalidadeDe(62, 55.0);
        var muitoAcima = EscolhedorDeInstrucoes.mentalidadeDe(80, 55.0);

        assertThat(List.of(muitoAbaixo, abaixo, naMedia, acima, muitoAcima))
                .containsExactly(Mentalidade.MUITO_DEFENSIVA, Mentalidade.DEFENSIVA,
                        Mentalidade.EQUILIBRADA, Mentalidade.OFENSIVA,
                        Mentalidade.MUITO_OFENSIVA);
    }

    @ParameterizedTest
    @EnumSource(Mentalidade.class)
    void deveDarInstrucoesCoerentesParaTodaMentalidade(Mentalidade mentalidade) {
        var instrucoes = EscolhedorDeInstrucoes.instrucoesDe(mentalidade);

        assertThat(instrucoes.mentalidade()).isEqualTo(mentalidade);
        assertThat(instrucoes.ritmo()).isNotNull();
        assertThat(instrucoes.largura()).isNotNull();

        var ofensiva = mentalidade == Mentalidade.OFENSIVA
                || mentalidade == Mentalidade.MUITO_OFENSIVA;
        if (ofensiva) {
            assertThat(instrucoes.linhaDefensiva())
                    .as("time ofensivo não joga com linha recuada")
                    .isNotEqualTo(LinhaDefensiva.RECUADA);
            assertThat(instrucoes.pressao()).isNotEqualTo(Pressao.BAIXA);
            assertThat(instrucoes.largura()).isNotEqualTo(Largura.ESTREITA);
        }

        var defensiva = mentalidade == Mentalidade.DEFENSIVA
                || mentalidade == Mentalidade.MUITO_DEFENSIVA;
        if (defensiva) {
            assertThat(instrucoes.linhaDefensiva()).isNotEqualTo(LinhaDefensiva.ADIANTADA);
            assertThat(instrucoes.pressao()).isNotEqualTo(Pressao.ALTA);
        }
    }

    @Test
    void deveDarABracadeiraAoTitularDeMaiorAptidao() {
        var onze = new Onze(List.of(
                new SlotPreenchido(1, 1L, 10L, 62),
                new SlotPreenchido(2, 2L, 20L, 81),
                new SlotPreenchido(3, 9L, 30L, 74)), 217);

        assertThat(EscolhedorDeInstrucoes.capitaoDe(onze)).isEqualTo(20L);
    }

    @Test
    void deveDesempatarACapitaniaPeloMenorId() {
        var onze = new Onze(List.of(
                new SlotPreenchido(1, 1L, 30L, 70),
                new SlotPreenchido(2, 2L, 12L, 70)), 140);

        assertThat(EscolhedorDeInstrucoes.capitaoDe(onze)).isEqualTo(12L);
    }
}

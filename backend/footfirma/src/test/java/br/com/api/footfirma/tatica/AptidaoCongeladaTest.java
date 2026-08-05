package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AptidaoCongeladaTest {

    @Autowired
    TaticaService tatica;

    @Autowired
    JdbcTemplate jdbc;

    CenarioDeElenco cenario;

    @BeforeEach
    void montarCenario() {
        cenario = CenarioDeElenco.montar(jdbc);
    }

    @Test
    void deveManterOCongeladoEMoverOAtualQuandoOOverallMuda() {
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario));

        var antes = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId())
                .orElseThrow();
        var titular = antes.titulares().getFirst();
        assertThat(titular.aptidaoNoMomento()).isEqualTo(titular.aptidaoAtual());

        jdbc.update("""
                update jogador_overall set overall = 99
                where jogador_id = ? and temporada_id = ? and posicao_id = ?
                """, titular.jogadorId(), cenario.temporadaId(), titular.posicaoId());

        var depois = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId())
                .orElseThrow();
        var mesmo = depois.titulares().getFirst();

        assertThat(mesmo.aptidaoNoMomento())
                .as("o histórico não se reescreve")
                .isEqualTo(titular.aptidaoNoMomento());
        assertThat(mesmo.aptidaoAtual())
                .as("a partida não joga com dado velho")
                .isEqualTo(99);
    }

    @Test
    void naoDeveMoverNenhumaDasDuasQuandoAPosicaoPrincipalDoReservaMuda() {
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario));

        var antes = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId())
                .orElseThrow();
        var reserva = antes.banco().getFirst();

        var outraPosicao = cenario.posicoesEmOrdem().stream()
                .filter(id -> id != reserva.posicaoId())
                .findFirst().orElseThrow();
        jdbc.update("update jogador set posicao_principal_id = ? where id = ?",
                outraPosicao, reserva.jogadorId());

        var depois = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId())
                .orElseThrow();
        var mesmo = depois.banco().getFirst();

        assertThat(mesmo.posicaoId())
                .as("a posição do congelamento é lida de plano_escalacao, não derivada")
                .isEqualTo(reserva.posicaoId());
        assertThat(mesmo.aptidaoNoMomento()).isEqualTo(reserva.aptidaoNoMomento());
        assertThat(mesmo.aptidaoAtual()).isEqualTo(reserva.aptidaoAtual());
    }

    @Test
    void deveMarcarComoIrregularOTitularQueSaiuDoClube() {
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario));

        var antes = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId())
                .orElseThrow();
        assertThat(antes.titulares()).allSatisfy(escalado ->
                assertThat(escalado.irregular()).isFalse());

        var saindo = antes.titulares().getFirst().jogadorId();
        jdbc.update("delete from jogador_vinculo where jogador_id = ? and clube_id = ?",
                saindo, cenario.clubeId());

        var depois = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId())
                .orElseThrow();

        assertThat(depois.titulares()).filteredOn(escalado -> escalado.jogadorId() == saindo)
                .singleElement()
                .satisfies(escalado -> assertThat(escalado.irregular()).isTrue());
    }

    @Test
    void deveVoltarVazioQuandoOClubeNaoTemPlano() {
        assertThat(tatica.buscarPlanoVigente(cenario.clubeSemTreinadorId(),
                cenario.temporadaId())).isEmpty();
    }
}

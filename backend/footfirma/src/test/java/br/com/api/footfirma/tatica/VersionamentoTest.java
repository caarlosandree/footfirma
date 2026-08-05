package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.tatica.dto.TitularEscalado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VersionamentoTest {

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
    void deveGerarVersaoNovaEDeixarSoAUltimaVigente() {
        var primeiro = PlanoDeTeste.valido(jdbc, cenario);
        var primeiroId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), primeiro);

        // Troca o último titular por alguém do banco: escalação diferente, plano novo.
        var titulares = new ArrayList<>(primeiro.titulares());
        var banco = new ArrayList<>(primeiro.banco());
        var entrando = banco.removeLast();
        var saindo = titulares.removeLast();
        titulares.add(new TitularEscalado(entrando, saindo.slotOrdem()));
        banco.add(saindo.jogadorId());

        var segundoId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(primeiro, titulares, banco, primeiro.capitaoId()));

        assertThat(segundoId).isNotEqualTo(primeiroId);

        var versoes = jdbc.queryForList("""
                select versao, vigente from plano_tatico
                where clube_id = ? and temporada_id = ? order by versao
                """, cenario.clubeId(), cenario.temporadaId());

        assertThat(versoes).hasSize(2);
        assertThat(versoes.get(0)).containsEntry("versao", 1).containsEntry("vigente", false);
        assertThat(versoes.get(1)).containsEntry("versao", 2).containsEntry("vigente", true);
    }

    @Test
    void devePreservarAsLinhasEAAptidaoCongeladaDaVersaoAntiga() {
        var primeiro = PlanoDeTeste.valido(jdbc, cenario);
        var primeiroId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), primeiro);

        var antigas = jdbc.queryForList("""
                select jogador_id, aptidao_no_momento from plano_escalacao
                where plano_id = ? order by jogador_id
                """, primeiroId);

        jdbc.update("update jogador_overall set overall = 5 where temporada_id = ?",
                cenario.temporadaId());
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), primeiro);

        var depois = jdbc.queryForList("""
                select jogador_id, aptidao_no_momento from plano_escalacao
                where plano_id = ? order by jogador_id
                """, primeiroId);

        assertThat(depois)
                .as("a versão antiga é registro histórico e não se reescreve")
                .isEqualTo(antigas);
    }

    @Test
    void deveGravarAptidaoNovaNaVersaoNova() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), plano);

        jdbc.update("update jogador_overall set overall = 5 where temporada_id = ?",
                cenario.temporadaId());
        var segundoId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), plano);

        assertThat(jdbc.queryForList("""
                select distinct aptidao_no_momento from plano_escalacao where plano_id = ?
                """, Integer.class, segundoId))
                .containsExactly(5);
    }
}

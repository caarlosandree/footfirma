package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.calendario.dto.EdicaoParaGerar;
import br.com.api.footfirma.calendario.dto.ResultadoDoJogo;
import br.com.api.footfirma.shared.exception.ResultadoInvalidoException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O teste que sustenta a metade não exercitada do módulo.
 *
 * <p>Nenhuma competição do mundo gerado usa mata-mata, então esta classe é a única prova
 * de que chaveamento, propagação e realocação funcionam.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EliminatoriaIntegridadeTest {

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    CalendarioFactory factory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    CalendarioFactory.Cenario copa;

    @BeforeAll
    void gerarEDisputar() {
        copa = factory.criar("copa-integra", 8, "ELIMINATORIA", 2, true, true, true);
        calendarioService.gerarTemporada(copa.temporadaId(), 21L,
                List.of(new EdicaoParaGerar(copa.edicaoId(), 1, CalendarioFactory.PERFIL)));
    }

    @Test
    void devePropagarAteAFinalEDarUmCampeao() {
        disputarToda();

        var semVencedor = jdbcTemplate.queryForObject("""
                select count(*) from confronto where fase_id = ? and vencedor_clube_id is null
                """, Integer.class, copa.faseId());
        assertThat(semVencedor).isZero();

        var campeao = jdbcTemplate.queryForObject("""
                select vencedor_clube_id from confronto
                where fase_id = ? order by ordem desc limit 1
                """, Long.class, copa.faseId());
        assertThat(campeao).isNotNull().isIn(copa.clubeIds());
    }

    @Test
    void devePreencherOsClubesDoConfrontoSeguinteAoResolverOAnterior() {
        disputarToda();

        var semifinaisCheias = jdbcTemplate.queryForObject("""
                select count(*) from confronto
                where fase_id = ? and origem_lado_a is not null
                  and clube_a_id is not null and clube_b_id is not null
                """, Integer.class, copa.faseId());
        assertThat(semifinaisCheias).isEqualTo(3);
    }

    @Test
    void deveDarMandanteEstadioEDataAoJogoQuandoOConfrontoSeCompleta() {
        disputarToda();

        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from jogo j
                join confronto c on c.id = j.confronto_id
                where c.fase_id = ? and c.clube_a_id is not null and c.clube_b_id is not null
                  and (j.mandante_id is null or j.estadio_id is null or j.data_jogo is null)
                """, Integer.class, copa.faseId());
        assertThat(incompletos).isZero();
    }

    @Test
    void deveRespeitarODescansoDepoisDaRealocacao() {
        disputarToda();

        var violacoes = jdbcTemplate.queryForObject("""
                with meus as (
                    select j.* from jogo j
                    join confronto c on c.id = j.confronto_id
                    where c.fase_id = ? and j.data_jogo is not null and j.mandante_id is not null
                ),
                agenda as (
                    select mandante_id as clube_id, data_jogo from meus
                    union all
                    select visitante_id, data_jogo from meus
                ),
                consecutivos as (
                    select clube_id, data_jogo,
                           lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
                    from agenda
                )
                select count(*) from consecutivos
                where anterior is not null and data_jogo - anterior < 3
                """, Integer.class, copa.faseId());
        assertThat(violacoes).isZero();
    }

    @Test
    void deveManterOMandoInvertidoEntreIdaEVoltaNasFasesPosteriores() {
        disputarToda();

        var errados = jdbcTemplate.queryForObject("""
                select count(*) from jogo ida
                join jogo volta on volta.confronto_id = ida.confronto_id
                                and volta.ordem_no_confronto = 2
                join confronto c on c.id = ida.confronto_id
                where c.fase_id = ? and ida.ordem_no_confronto = 1
                  and c.origem_lado_a is not null
                  and (ida.mandante_id <> volta.visitante_id
                    or ida.visitante_id <> volta.mandante_id)
                """, Integer.class, copa.faseId());
        assertThat(errados).isZero();
    }

    @Test
    void deveRecusarResultadoDeJogoSemClubesDefinidos() {
        // Um cenário próprio, sem disputar nada: a final continua vazia.
        var virgem = factory.criar("copa-virgem", 4, "ELIMINATORIA", 1, true, true, true);
        calendarioService.gerarTemporada(virgem.temporadaId(), 31L,
                List.of(new EdicaoParaGerar(virgem.edicaoId(), 1, CalendarioFactory.PERFIL)));

        var jogoDaFinal = jdbcTemplate.queryForObject("""
                select j.id from jogo j
                join confronto c on c.id = j.confronto_id
                where c.fase_id = ? and c.clube_a_id is null limit 1
                """, Long.class, virgem.faseId());

        assertThatThrownBy(() -> calendarioService.registrarResultado(
                jogoDaFinal, ResultadoDoJogo.noTempoNormal(1, 0)))
                .isInstanceOf(ResultadoInvalidoException.class)
                .hasMessageContaining("clubes");
    }

    @Test
    void deveAceitarPenaltisNaFaseQueOsDeclara() {
        var comPenaltis = factory.criar("copa-penaltis", 2, "ELIMINATORIA", 1, true, true, true);
        calendarioService.gerarTemporada(comPenaltis.temporadaId(), 41L,
                List.of(new EdicaoParaGerar(comPenaltis.edicaoId(), 1, CalendarioFactory.PERFIL)));

        var jogoId = jdbcTemplate.queryForObject("""
                select j.id from jogo j join confronto c on c.id = j.confronto_id
                where c.fase_id = ? limit 1
                """, Long.class, comPenaltis.faseId());

        calendarioService.registrarResultado(jogoId, new ResultadoDoJogo(1, 1, 0, 0, 5, 4));

        var vencedor = jdbcTemplate.queryForObject(
                "select vencedor_clube_id from confronto where fase_id = ?",
                Long.class, comPenaltis.faseId());
        assertThat(vencedor).isNotNull();
    }

    /**
     * Disputa a chave inteira, fase por fase, com o lado A do confronto vencendo sempre.
     *
     * <p>Dar vitória ao mandante nos dois jogos <strong>não</strong> serve: em ida e volta
     * isso produz agregado empatado e zero gol fora para os dois, e o confronto
     * corretamente não se resolve. O vencedor precisa ser o mesmo clube nas duas partidas.
     */
    private void disputarToda() {
        for (var rodada = 0; rodada < 20; rodada++) {
            var pendentes = jdbcTemplate.queryForList("""
                    select j.id, (j.mandante_id = c.clube_a_id) as a_manda from jogo j
                    join confronto c on c.id = j.confronto_id
                    where c.fase_id = ? and j.situacao = 'AGENDADO' and j.mandante_id is not null
                    order by j.id
                    """, copa.faseId());
            if (pendentes.isEmpty()) {
                return;
            }
            pendentes.forEach(linha -> {
                var aManda = (Boolean) linha.get("a_manda");
                var placar = aManda
                        ? ResultadoDoJogo.noTempoNormal(2, 0)
                        : ResultadoDoJogo.noTempoNormal(0, 2);
                calendarioService.registrarResultado((Long) linha.get("id"), placar);
            });
        }
    }
}

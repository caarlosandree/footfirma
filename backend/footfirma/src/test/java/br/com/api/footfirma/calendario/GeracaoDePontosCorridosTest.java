package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.calendario.dto.EdicaoParaGerar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeracaoDePontosCorridosTest {

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    CalendarioFactory factory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    CalendarioFactory.Cenario cenario;

    // Uma liga de 20 clubes, turno e returno, na janela do mundo gerado. Gerar uma vez
    // para a classe inteira: são 380 jogos, e repetir por teste multiplicaria a carga.
    @BeforeAll
    void gerar() {
        cenario = factory.criar("liga-teste", 20, "PONTOS_CORRIDOS", 2);
        calendarioService.gerarTemporada(cenario.temporadaId(), 42L,
                List.of(new EdicaoParaGerar(cenario.edicaoId(), 1, CalendarioFactory.PERFIL)));
    }

    @Test
    void deveCriarTrintaEOitoRodadasETrezentosEOitentaJogos() {
        assertThat(contarDaFase("rodada r", "r.fase_id")).isEqualTo(38);
        assertThat(contarDaFase("confronto c", "c.fase_id")).isEqualTo(380);
        assertThat(contarJogos("")).isEqualTo(380);
    }

    @Test
    void deveDarUmJogoPorConfrontoEmPontosCorridos() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select j.confronto_id, count(*) as jogos from jogo j
                    join rodada r on r.id = j.rodada_id
                    where r.fase_id = ?
                    group by j.confronto_id
                ) c where jogos <> 1
                """, Integer.class, cenario.faseId());
        assertThat(fora).isZero();
    }

    @Test
    void naoDeveMarcarNenhumJogoNaSexta() {
        assertThat(contarJogos("and extract(dow from j.data_jogo) = 5")).isZero();
    }

    @Test
    void deveRespeitarODescansoMinimoDeTodoClube() {
        var violacoes = jdbcTemplate.queryForObject("""
                with meus as (
                    select j.* from jogo j join rodada r on r.id = j.rodada_id
                    where r.fase_id = ?
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
                """, Integer.class, cenario.faseId());
        assertThat(violacoes).isZero();
    }

    @Test
    void naoDeveEscalarNenhumClubeDuasVezesNaMesmaRodada() {
        var repetidos = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select j.rodada_id, c.clube_id from jogo j
                    join rodada r on r.id = j.rodada_id
                    cross join lateral (values (j.mandante_id), (j.visitante_id)) as c(clube_id)
                    where r.fase_id = ?
                    group by j.rodada_id, c.clube_id having count(*) > 1
                ) x
                """, Integer.class, cenario.faseId());
        assertThat(repetidos).isZero();
    }

    @Test
    void deveDarMandanteVisitanteEstadioEDataATodoJogo() {
        assertThat(contarJogos("""
                and (j.mandante_id is null or j.visitante_id is null
                  or j.estadio_id is null or j.data_jogo is null)
                """)).isZero();
    }

    @Test
    void deveDeixarVencedorNuloEmPontosCorridos() {
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from confronto
                where fase_id = ? and vencedor_clube_id is not null
                """, Integer.class, cenario.faseId())).isZero();
    }

    @Test
    void deveFazerTodoClubeJogarTrintaEOitoVezes() {
        var fora = jdbcTemplate.queryForObject("""
                with meus as (
                    select j.* from jogo j join rodada r on r.id = j.rodada_id
                    where r.fase_id = ?
                )
                select count(*) from (
                    select clube_id, count(*) as jogos from (
                        select mandante_id as clube_id from meus
                        union all
                        select visitante_id from meus
                    ) a group by clube_id
                ) t where jogos <> 38
                """, Integer.class, cenario.faseId());
        assertThat(fora).isZero();
    }

    @Test
    void deveManterTodoJogoDentroDaJanelaDaEdicao() {
        assertThat(contarJogos(
                "and (j.data_jogo < date '2026-04-01' or j.data_jogo > date '2026-12-20')"))
                .isZero();
    }

    @Test
    void deveExporAAgendaDoClubePelaPortaPublica() {
        var agenda = calendarioService.listarAgendaDoClube(
                cenario.clubeIds().getFirst(), cenario.temporadaId());

        assertThat(agenda).hasSize(38);
        assertThat(agenda).extracting(jogo -> jogo.dataJogo()).doesNotContainNull();
        assertThat(agenda).isSortedAccordingTo(
                java.util.Comparator.comparing(jogo -> jogo.dataJogo()));
    }

    @Test
    void deveExporAsRodadasPelaPortaPublica() {
        var rodadas = calendarioService.listarRodadas(cenario.edicaoId());

        assertThat(rodadas).hasSize(38);
        assertThat(rodadas.getFirst().ordem()).isEqualTo(1);
        assertThat(rodadas.getFirst().jogos()).hasSize(10);
    }

    /**
     * Conta linhas da fase deste cenário, e não do banco inteiro.
     *
     * <p>A suíte compartilha o container e {@code MundoIntegridadeTest} roda com
     * {@code recriar=true} — sem o filtro, o resultado destes testes dependeria da ordem
     * em que as classes rodam.
     */
    private int contarDaFase(String tabelaComAlias, String colunaDaFase) {
        return jdbcTemplate.queryForObject(
                "select count(*) from %s where %s = ?".formatted(tabelaComAlias, colunaDaFase),
                Integer.class, cenario.faseId());
    }

    private int contarJogos(String filtroExtra) {
        return jdbcTemplate.queryForObject("""
                select count(*) from jogo j
                join rodada r on r.id = j.rodada_id
                where r.fase_id = ?
                """ + filtroExtra, Integer.class, cenario.faseId());
    }
}

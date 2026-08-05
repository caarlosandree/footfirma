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
        assertThat(contar("rodada")).isEqualTo(38);
        assertThat(contar("confronto")).isEqualTo(380);
        assertThat(contar("jogo")).isEqualTo(380);
    }

    @Test
    void deveDarUmJogoPorConfrontoEmPontosCorridos() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select confronto_id, count(*) as jogos from jogo group by confronto_id
                ) c where jogos <> 1
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void naoDeveMarcarNenhumJogoNaSexta() {
        var sextas = jdbcTemplate.queryForObject(
                "select count(*) from jogo where extract(dow from data_jogo) = 5", Integer.class);
        assertThat(sextas).isZero();
    }

    @Test
    void deveRespeitarODescansoMinimoDeTodoClube() {
        var violacoes = jdbcTemplate.queryForObject("""
                with agenda as (
                    select mandante_id as clube_id, data_jogo from jogo
                    union all
                    select visitante_id, data_jogo from jogo
                ),
                consecutivos as (
                    select clube_id, data_jogo,
                           lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
                    from agenda
                )
                select count(*) from consecutivos
                where anterior is not null and data_jogo - anterior < 3
                """, Integer.class);
        assertThat(violacoes).isZero();
    }

    @Test
    void naoDeveEscalarNenhumClubeDuasVezesNaMesmaRodada() {
        var repetidos = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select j.rodada_id, c.clube_id from jogo j
                    cross join lateral (values (j.mandante_id), (j.visitante_id)) as c(clube_id)
                    group by j.rodada_id, c.clube_id having count(*) > 1
                ) r
                """, Integer.class);
        assertThat(repetidos).isZero();
    }

    @Test
    void deveDarMandanteVisitanteEstadioEDataATodoJogo() {
        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from jogo
                where mandante_id is null or visitante_id is null
                   or estadio_id is null or data_jogo is null
                """, Integer.class);
        assertThat(incompletos).isZero();
    }

    @Test
    void deveDeixarVencedorNuloEmPontosCorridos() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from confronto where vencedor_clube_id is not null",
                Integer.class)).isZero();
    }

    @Test
    void deveFazerTodoClubeJogarTrintaEOitoVezes() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id, count(*) as jogos from (
                        select mandante_id as clube_id from jogo
                        union all
                        select visitante_id from jogo
                    ) a group by clube_id
                ) t where jogos <> 38
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void deveManterTodoJogoDentroDaJanelaDaEdicao() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from jogo
                where data_jogo < date '2026-04-01' or data_jogo > date '2026-12-20'
                """, Integer.class);
        assertThat(fora).isZero();
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

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}

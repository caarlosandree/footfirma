package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.calendario.dto.ConfrontoDetalhe;
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
class ChaveamentoTest {

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    CalendarioFactory factory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    CalendarioFactory.Cenario mataMata;
    CalendarioFactory.Cenario grupos;

    @BeforeAll
    void gerar() {
        // Copa de 8 clubes, ida e volta, com as três regras de desempate ligadas.
        mataMata = factory.criar("copa", 8, "ELIMINATORIA", 2, true, true, true);
        calendarioService.gerarTemporada(mataMata.temporadaId(), 11L,
                List.of(new EdicaoParaGerar(mataMata.edicaoId(), 1, CalendarioFactory.PERFIL)));

        // Fase de grupos: 8 clubes em 2 grupos de 4, turno único.
        grupos = factory.criar("grupos", 8, "GRUPOS", 1);
        calendarioService.gerarTemporada(grupos.temporadaId(), 13L,
                List.of(new EdicaoParaGerar(grupos.edicaoId(), 1, CalendarioFactory.PERFIL)));
    }

    @Test
    void deveCriarSeteConfrontosEQuatorzeJogosNoMataMata() {
        assertThat(contarConfrontos(mataMata.faseId())).isEqualTo(7);
        assertThat(contarJogos(mataMata.faseId(), "")).isEqualTo(14);
    }

    @Test
    void deveDeixarOsConfrontosPosterioresSemClube() {
        var vazios = jdbcTemplate.queryForObject("""
                select count(*) from confronto where fase_id = ? and clube_a_id is null
                """, Integer.class, mataMata.faseId());
        assertThat(vazios).isEqualTo(3);
    }

    @Test
    void deveApontarOsConfrontosPosterioresParaOsAnteriores() {
        var comOrigem = jdbcTemplate.queryForObject("""
                select count(*) from confronto
                where fase_id = ? and origem_lado_a is not null and origem_lado_b is not null
                """, Integer.class, mataMata.faseId());
        assertThat(comOrigem).isEqualTo(3);
    }

    @Test
    void deveDeixarOsJogosDosConfrontosVaziosSemMandanteEEstadio() {
        var incoerentes = jdbcTemplate.queryForObject("""
                select count(*) from jogo j
                join confronto c on c.id = j.confronto_id
                where c.fase_id = ? and c.clube_a_id is null
                  and (j.mandante_id is not null or j.estadio_id is not null)
                """, Integer.class, mataMata.faseId());
        assertThat(incoerentes).isZero();
    }

    @Test
    void deveDatarTodosOsJogosInclusiveOsDeConfrontoVazio() {
        // A data é provisória e será realocada — mas existe desde o sorteio, para que o
        // calendário da temporada não fique com buracos.
        assertThat(contarJogos(mataMata.faseId(), "and j.data_jogo is null")).isZero();
    }

    @Test
    void deveInverterOMandoEntreIdaEVolta() {
        var errados = jdbcTemplate.queryForObject("""
                select count(*) from jogo ida
                join jogo volta on volta.confronto_id = ida.confronto_id
                                and volta.ordem_no_confronto = 2
                join confronto c on c.id = ida.confronto_id
                where c.fase_id = ? and ida.ordem_no_confronto = 1
                  and ida.mandante_id is not null
                  and (ida.mandante_id <> volta.visitante_id
                    or ida.visitante_id <> volta.mandante_id)
                """, Integer.class, mataMata.faseId());
        assertThat(errados).isZero();
    }

    @Test
    void deveExporOChaveamentoInteiroPelaPortaPublica() {
        var chaveamento = calendarioService.listarChaveamento(mataMata.faseId());

        assertThat(chaveamento).hasSize(7);
        assertThat(chaveamento).extracting(ConfrontoDetalhe::ordem)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(chaveamento.stream().filter(c -> c.clubeAId() != null)).hasSize(4);
        assertThat(chaveamento.getLast().jogos()).hasSize(2);
        assertThat(chaveamento).allSatisfy(
                confronto -> assertThat(confronto.vencedorClubeId()).isNull());
    }

    @Test
    void deveCriarDozeConfrontosNaFaseDeGrupos() {
        // 2 grupos de 4, turno único: 6 confrontos por grupo.
        assertThat(contarConfrontos(grupos.faseId())).isEqualTo(12);
        assertThat(contarJogos(grupos.faseId(), "")).isEqualTo(12);
    }

    @Test
    void deveDarClubesATodoConfrontoDeGrupos() {
        var vazios = jdbcTemplate.queryForObject("""
                select count(*) from confronto
                where fase_id = ? and (clube_a_id is null or clube_b_id is null)
                """, Integer.class, grupos.faseId());
        assertThat(vazios).isZero();
    }

    @Test
    void deveRotularOsGruposEDividiLosIgualmente() {
        var porChave = jdbcTemplate.queryForList("""
                select chave, count(*) as confrontos from confronto
                where fase_id = ? group by chave order by chave
                """, grupos.faseId());

        assertThat(porChave).hasSize(2);
        assertThat(porChave.get(0)).containsEntry("chave", "A").containsEntry("confrontos", 6L);
        assertThat(porChave.get(1)).containsEntry("chave", "B").containsEntry("confrontos", 6L);
    }

    @Test
    void naoDeveCruzarClubesDeGruposDiferentes() {
        var cruzados = jdbcTemplate.queryForObject("""
                with lados as (
                    select chave, clube_a_id as clube from confronto where fase_id = ?
                    union all
                    select chave, clube_b_id from confronto where fase_id = ?
                )
                select count(*) from (
                    select clube from lados group by clube having count(distinct chave) > 1
                ) x
                """, Integer.class, grupos.faseId(), grupos.faseId());
        assertThat(cruzados).isZero();
    }

    @Test
    void deveCompartilharAsRodadasEntreOsGrupos() {
        // Na rodada 1 os dois grupos jogam: 3 rodadas, não 6.
        var rodadas = jdbcTemplate.queryForObject(
                "select count(*) from rodada where fase_id = ?", Integer.class, grupos.faseId());
        assertThat(rodadas).isEqualTo(3);
    }

    private int contarConfrontos(long faseId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from confronto where fase_id = ?", Integer.class, faseId);
    }

    private int contarJogos(long faseId, String filtroExtra) {
        return jdbcTemplate.queryForObject("""
                select count(*) from jogo j
                join confronto c on c.id = j.confronto_id
                where c.fase_id = ?
                """ + filtroExtra, Integer.class, faseId);
    }
}

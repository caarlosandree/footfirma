package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.calendario.dto.EdicaoParaGerar;
import br.com.api.footfirma.calendario.dto.ResultadoDoJogo;
import br.com.api.footfirma.calendario.dto.SituacaoDoJogo;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistroDeResultadoTest {

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    CalendarioFactory factory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    CalendarioFactory.Cenario cenario;
    List<Long> jogoIds;

    @BeforeAll
    void gerar() {
        cenario = factory.criar("resultado", 4, "PONTOS_CORRIDOS", 1);
        calendarioService.gerarTemporada(cenario.temporadaId(), 7L,
                List.of(new EdicaoParaGerar(cenario.edicaoId(), 1, CalendarioFactory.PERFIL)));
        // Filtrado pela fase do cenário, e não "todos os jogos do banco": a suíte
        // compartilha o container, e sem o filtro estes testes agiriam sobre jogos criados
        // por outra classe — com outras regras de desempate.
        jogoIds = jdbcTemplate.queryForList("""
                select j.id from jogo j
                join rodada r on r.id = j.rodada_id
                where r.fase_id = ?
                order by j.id
                """, Long.class, cenario.faseId());
    }

    @Test
    void deveGravarOPlacarEEncerrarOJogo() {
        var jogoId = jogoIds.get(0);

        calendarioService.registrarResultado(jogoId, ResultadoDoJogo.noTempoNormal(2, 1));

        var jogo = calendarioService.buscarJogo(jogoId).orElseThrow();
        assertThat(jogo.golsMandante()).isEqualTo(2);
        assertThat(jogo.golsVisitante()).isEqualTo(1);
        assertThat(jogo.situacao()).isEqualTo(SituacaoDoJogo.ENCERRADO);
    }

    @Test
    void deveRecusarPenaltisEmPontosCorridos() {
        assertThatThrownBy(() -> calendarioService.registrarResultado(
                jogoIds.get(1), new ResultadoDoJogo(1, 1, null, null, 4, 3)))
                .isInstanceOf(ResultadoInvalidoException.class)
                .hasMessageContaining("pênaltis");
    }

    @Test
    void deveRecusarProrrogacaoEmPontosCorridos() {
        assertThatThrownBy(() -> calendarioService.registrarResultado(
                jogoIds.get(2), new ResultadoDoJogo(1, 1, 1, 0, null, null)))
                .isInstanceOf(ResultadoInvalidoException.class)
                .hasMessageContaining("prorrogação");
    }

    @Test
    void deveRecusarJogoJaEncerrado() {
        var jogoId = jogoIds.get(3);
        calendarioService.registrarResultado(jogoId, ResultadoDoJogo.noTempoNormal(0, 0));

        assertThatThrownBy(() -> calendarioService.registrarResultado(
                jogoId, ResultadoDoJogo.noTempoNormal(1, 0)))
                .isInstanceOf(ResultadoInvalidoException.class)
                .hasMessageContaining("encerrado");
    }

    @Test
    void naoDeveResolverConfrontoEmPontosCorridos() {
        calendarioService.registrarResultado(jogoIds.get(4), ResultadoDoJogo.noTempoNormal(3, 0));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from confronto
                where fase_id = ? and vencedor_clube_id is not null
                """, Integer.class, cenario.faseId())).isZero();
    }

    @Test
    void deveTrazerAsRegrasDeDesempateJuntoDoJogo() {
        var jogo = calendarioService.buscarJogo(jogoIds.get(5)).orElseThrow();

        assertThat(jogo.desempate()).isNotNull();
        assertThat(jogo.desempate().temGolFora()).isFalse();
        assertThat(jogo.desempate().temProrrogacao()).isFalse();
        assertThat(jogo.desempate().temPenaltis()).isFalse();
        assertThat(jogo.desempate().decisivo()).isTrue();
    }
}

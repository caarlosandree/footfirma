package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "footfirma.mundo.recriar=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MundoIntegridadeTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // Gerar uma vez para a classe inteira: são 1.520 jogadores, e repetir por teste
    // multiplicaria a carga por seis sem checar nada de novo.
    @BeforeAll
    void gerarOMundo() {
        mundoService.gerar();
    }

    @Test
    void deveCriarMilQuinhentosEVinteJogadores() {
        assertThat(contar("jogador")).isEqualTo(1_520);
        assertThat(contar("jogador_atributo")).isEqualTo(1_520);
        assertThat(contar("jogador_atributo_oculto")).isEqualTo(1_520);
        assertThat(contar("jogador_vinculo")).isEqualTo(1_520);
    }

    @Test
    void deveDarVinteESeisProfissionaisEDozeDaBaseACadaClube() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id,
                           count(*) filter (where categoria = 'PROFISSIONAL') as profissionais,
                           count(*) filter (where categoria = 'BASE') as base
                    from jogador_vinculo group by clube_id
                ) elenco where profissionais <> 26 or base <> 12
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void deveCobrirAsNovePosicoesEmTodoElencoProfissional() {
        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select v.clube_id, count(distinct p.codigo) as posicoes
                    from jogador_vinculo v
                    join jogador j on j.id = v.jogador_id
                    join posicao p on p.id = j.posicao_principal_id
                    where v.categoria = 'PROFISSIONAL'
                    group by v.clube_id
                ) cobertura where posicoes <> 9
                """, Integer.class);
        assertThat(incompletos).isZero();
    }

    @Test
    void deveDarCamisasUnicasDentroDeCadaClube() {
        var duplicadas = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id, numero_camisa from jogador_vinculo
                    where numero_camisa is not null
                    group by clube_id, numero_camisa having count(*) > 1
                ) repetidas
                """, Integer.class);
        assertThat(duplicadas).isZero();
    }

    @Test
    void deveMaterializarOverallDeTodoJogadorNasNovePosicoes() {
        assertThat(contar("jogador_overall")).isEqualTo(1_520 * 9);
    }

    @Test
    void deveManterOOverallDentroDaFaixaQueACurvaProduz() {
        // O alvo não é gravado; o que se verifica é que o overall na posição
        // principal ficou dentro da faixa que a curva de papéis produz.
        var forasteiros = jdbcTemplate.queryForObject("""
                select count(*) from jogador_overall o
                join jogador j on j.id = o.jogador_id
                where o.posicao_id = j.posicao_principal_id
                  and (o.overall < 20 or o.overall > 97)
                """, Integer.class);
        assertThat(forasteiros).isZero();
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}

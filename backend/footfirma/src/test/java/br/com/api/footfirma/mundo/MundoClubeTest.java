package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "footfirma.mundo.recriar=true")
class MundoClubeTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCriarQuarentaClubesComEstadioProprio() {
        mundoService.gerar();

        assertThat(contar("clube")).isEqualTo(40);
        assertThat(contar("estadio")).isEqualTo(40);
        assertThat(contar("edicao_participante")).isEqualTo(40);
    }

    @Test
    void deveDistribuirVinteClubesPorDivisao() {
        mundoService.gerar();

        var porDivisao = jdbcTemplate.queryForList("""
                select c.nivel, count(*) as total
                from edicao_participante p
                join edicao e on e.id = p.edicao_id
                join competicao c on c.id = e.competicao_id
                group by c.nivel order by c.nivel
                """);
        assertThat(porDivisao).hasSize(2);
        assertThat(porDivisao.getFirst().get("total")).isEqualTo(20L);
        assertThat(porDivisao.getLast().get("total")).isEqualTo(20L);
    }

    @Test
    void deveResolverOEstadoDeCadaClube() {
        mundoService.gerar();

        assertThat(contar("clube where estado_id is null")).isZero();
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}

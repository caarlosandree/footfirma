package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "footfirma.mundo.recriar=true")
class MundoDeterminismoTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveProduzirOMesmoMundoNaSegundaGeracao() {
        mundoService.gerar();
        var primeira = fotografia();

        mundoService.gerar();
        var segunda = fotografia();

        assertThat(segunda).isEqualTo(primeira);
    }

    @Test
    void naoDeveDuplicarNemDeixarOrfaoAoRegerar() {
        mundoService.gerar();
        mundoService.gerar();

        assertThat(contar("clube")).isEqualTo(40);
        assertThat(contar("jogador")).isEqualTo(1_520);
        assertThat(contar("jogador_vinculo")).isEqualTo(1_520);
        assertThat(contar("""
                jogador j where not exists (
                    select 1 from jogador_vinculo v where v.jogador_id = j.id)
                """)).isZero();
    }

    @Test
    void deveRelatarASementeUsada() {
        var relatorio = mundoService.gerar();

        assertThat(relatorio.semente()).isEqualTo(20260803L);
        assertThat(relatorio.temporada()).isEqualTo("2026");
        assertThat(relatorio.contagens()).isNotEmpty();
    }

    private List<String> fotografia() {
        return jdbcTemplate.queryForList("""
                select j.slug || ':' || o.overall
                from jogador j
                join jogador_overall o on o.jogador_id = j.id
                                      and o.posicao_id = j.posicao_principal_id
                order by j.slug
                """, String.class);
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}

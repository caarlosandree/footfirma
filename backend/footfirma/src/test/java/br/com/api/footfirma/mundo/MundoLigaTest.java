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
class MundoLigaTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCriarAsDuasDivisoesComEdicaoDeDoisMilEVinteESeis() {
        mundoService.gerar();

        var slugs = jdbcTemplate.queryForList(
                "select slug from competicao order by nivel", String.class);
        assertThat(slugs).containsExactly("primeira-divisao", "segunda-divisao");
        assertThat(contar("edicao")).isEqualTo(2);
        assertThat(contar("fase")).isEqualTo(2);
    }

    @Test
    void deveCriarAsRegrasDeAcessoEDeRebaixamento() {
        mundoService.gerar();

        var tipos = jdbcTemplate.queryForList("""
                select r.tipo from regra_classificacao r
                join edicao e on e.id = r.edicao_id
                join competicao c on c.id = e.competicao_id
                order by c.nivel
                """, String.class);
        assertThat(tipos).containsExactly("REBAIXAMENTO", "ACESSO");
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}

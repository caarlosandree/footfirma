package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.tatica.repository.FormacaoRepository;
import br.com.api.footfirma.tatica.repository.FormacaoSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogoDeFormacaoTest {

    @Autowired
    FormacaoRepository formacoes;

    @Autowired
    FormacaoSlotRepository slots;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveSeedarSeisFormacoesEmOrdemContinua() {
        var todas = formacoes.findAllByOrderByOrdemAsc();

        assertThat(todas).hasSize(6);
        assertThat(todas).extracting("ordem").containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(todas.getFirst().getCodigo())
                .as("a de ordem 1 alimenta o repertório mínimo e precisa ser a mais genérica")
                .isEqualTo("4-4-2");
    }

    @Test
    void deveDarOnzeSlotsEmOrdemContinuaParaCadaFormacao() {
        for (var formacao : formacoes.findAllByOrderByOrdemAsc()) {
            var doJogo = slots.findByFormacaoIdOrderByOrdemAsc(formacao.getId());

            assertThat(doJogo)
                    .as("formação %s", formacao.getCodigo())
                    .hasSize(11)
                    .extracting("ordem")
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        }
    }

    @Test
    void deveDarExatamenteUmGoleiroPorFormacao() {
        var porFormacao = jdbcTemplate.queryForList("""
                select f.codigo, count(*) as goleiros
                from formacao f
                join formacao_slot s on s.formacao_id = f.id
                join posicao p       on p.id = s.posicao_id
                where p.codigo = 'GOL'
                group by f.codigo
                """);

        assertThat(porFormacao).hasSize(6);
        assertThat(porFormacao).allSatisfy(linha ->
                assertThat(linha.get("goleiros")).isEqualTo(1L));
    }
}

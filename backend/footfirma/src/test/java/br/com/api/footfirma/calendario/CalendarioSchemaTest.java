package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CalendarioSchemaTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCriarAsTresTabelasVazias() {
        assertThat(contar("rodada")).isZero();
        assertThat(contar("confronto")).isZero();
        assertThat(contar("jogo")).isZero();
    }

    @Test
    void deveRecusarTipoDeRodadaForaDoCheck() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into rodada (fase_id, ordem, tipo, data_alvo, janela_inicio, janela_fim)
                values (1, 1, 'SEXTA_FEIRA', '2026-04-05', '2026-04-03', '2026-04-07')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRecusarSituacaoDeJogoForaDoCheck() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into jogo (confronto_id, rodada_id, ordem_no_confronto, situacao)
                values (1, 1, 1, 'ADIADO')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveTerOsDoisIndicesParciaisDeOrigem() {
        var indices = jdbcTemplate.queryForList("""
                select indexname from pg_indexes
                where tablename = 'confronto' and indexname like 'idx_confronto_origem%'
                """, String.class);
        assertThat(indices).containsExactlyInAnyOrder(
                "idx_confronto_origem_a", "idx_confronto_origem_b");
    }

    @Test
    void deveDocumentarPorQueOCheckDeClubesDistintosAceitaNulo() {
        // A constraint passa quando um dos lados é nulo — é o jogo de eliminatória antes
        // da classificação. O comentário existe para que isso não seja lido como bug.
        var comentario = jdbcTemplate.queryForObject("""
                select obj_description(c.oid, 'pg_constraint')
                from pg_constraint c
                where c.conname = 'ck_jogo_clubes_distintos'
                """, String.class);
        assertThat(comentario).contains("null <> null");
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}

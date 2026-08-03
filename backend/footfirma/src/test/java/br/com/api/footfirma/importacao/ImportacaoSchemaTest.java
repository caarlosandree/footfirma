package br.com.api.footfirma.importacao;

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
class ImportacaoSchemaTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveRejeitarStatusForaDoDominio() {
        assertThatThrownBy(() -> inserirExecucao("INVENTADO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveExigirMotivoQuandoStatusEFalhou() {
        assertThatThrownBy(() -> inserirExecucao("FALHOU", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarMotivoQuandoStatusEConcluida() {
        assertThatThrownBy(() -> inserirExecucao("CONCLUIDA", "não deveria ter motivo"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveAceitarExecucaoConcluidaSemMotivo() {
        var execucaoId = inserirExecucao("CONCLUIDA", null);

        assertThat(execucaoId).isNotNull();
    }

    @Test
    void deveRejeitarSegundaContagemDaMesmaEntidadeNaMesmaExecucao() {
        var execucaoId = inserirExecucao("CONCLUIDA", null);
        inserirContagem(execucaoId, "clube");

        assertThatThrownBy(() -> inserirContagem(execucaoId, "clube"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarSeveridadeForaDoDominio() {
        var execucaoId = inserirExecucao("CONCLUIDA", null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into importacao_ocorrencia (execucao_id, entidade, linha, chave, severidade, motivo)
                values (?, 'clube', 3, 'sinc-alfa', 'CATASTROFE', 'motivo qualquer')
                """, execucaoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarEstadioDuplicadoPorNomeECidade() {
        jdbcTemplate.update(
                "insert into estadio (nome, cidade) values ('Estádio Schema', 'Recife')");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into estadio (nome, cidade) values ('Estádio Schema', 'Recife')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long inserirExecucao(String status, String motivo) {
        jdbcTemplate.update("""
                insert into importacao_execucao
                    (dataset_versao, schema_versao, diretorio, status, motivo_da_falha)
                values ('fixtures-v1', '1', 'fixtures/v1', ?, ?)
                """, status, motivo);
        return jdbcTemplate.queryForObject(
                "select max(id) from importacao_execucao", Long.class);
    }

    private void inserirContagem(Long execucaoId, String entidade) {
        jdbcTemplate.update("""
                insert into importacao_contagem (execucao_id, entidade, lidos, criados)
                values (?, ?, 8, 8)
                """, execucaoId, entidade);
    }
}

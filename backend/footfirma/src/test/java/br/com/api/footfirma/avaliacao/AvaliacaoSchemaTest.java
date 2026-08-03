package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import br.com.api.footfirma.avaliacao.repository.JogadorOverallRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AvaliacaoSchemaTest {

    @Autowired
    JogadorOverallRepository jogadorOverallRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveRejeitarDoisPerfisAtivosParaAMesmaPosicao() {
        var atacante = posicaoId("ATA");
        desativarPerfisExistentes(atacante);
        inserirPerfil(atacante, 7, true);

        assertThatThrownBy(() -> inserirPerfil(atacante, 8, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void devePermitirVersaoInativaAoLadoDaAtiva() {
        var ponta = posicaoId("PTA");
        desativarPerfisExistentes(ponta);
        inserirPerfil(ponta, 7, true);

        inserirPerfil(ponta, 8, false);

        var versoes = jdbcTemplate.queryForObject("""
                select count(*) from perfil_avaliacao where posicao_id = ? and versao in (7, 8)
                """, Integer.class, ponta);
        assertThat(versoes).isEqualTo(2);
    }

    @Test
    void deveRejeitarOverallDuplicadoParaMesmoJogadorTemporadaEPosicao() {
        var jogadorId = jogadorSalvo();
        var temporadaId = temporada("2031");
        var posicaoId = posicaoId("VOL");
        jogadorOverallRepository.saveAndFlush(overall(jogadorId, temporadaId, posicaoId, 70));

        assertThatThrownBy(() -> jogadorOverallRepository
                .saveAndFlush(overall(jogadorId, temporadaId, posicaoId, 80)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarOverallForaDaEscalaDeZeroANoventaENove() {
        var registro = overall(jogadorSalvo(), temporada("2032"), posicaoId("MEC"), 120);

        assertThatThrownBy(() -> jogadorOverallRepository.saveAndFlush(registro))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JogadorOverall overall(Long jogadorId, Long temporadaId, Long posicaoId, int valor) {
        var registro = new JogadorOverall(jogadorId, temporadaId, posicaoId);
        registro.setPerfilVersao(1);
        registro.setOverall(valor);
        registro.setCalculadoEm(OffsetDateTime.now());
        return registro;
    }

    private void inserirPerfil(Long posicaoId, int versao, boolean ativo) {
        jdbcTemplate.update("""
                insert into perfil_avaliacao (posicao_id, versao, ativo, vigente_desde)
                values (?, ?, ?, date '2026-08-02')
                """, posicaoId, versao, ativo);
    }

    // Na Task 2 não faz nada: a tabela está vazia. A partir da Task 3, o seed já
    // ocupa a versão 1 ativa de cada posição, e sem isto o primeiro insert do teste
    // colidiria com o índice parcial antes de chegar na asserção. @DataJpaTest
    // reverte a transação, então nenhum outro teste enxerga a desativação.
    private void desativarPerfisExistentes(Long posicaoId) {
        jdbcTemplate.update("update perfil_avaliacao set ativo = false where posicao_id = ?", posicaoId);
    }

    private Long posicaoId(String codigo) {
        return jdbcTemplate.queryForObject("select id from posicao where codigo = ?", Long.class, codigo);
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }

    private Long jogadorSalvo() {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = posicaoId("VOL");
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values ('teste-overall', 'teste|1998-03-10|BRA', 'Teste Overall', 'Teste Overall',
                        date '1998-03-10', ?, 'DIREITO', ?, 42, 'REAL')
                on conflict (slug) do nothing
                """, paisId, posicaoId);
        return jdbcTemplate.queryForObject(
                "select id from jogador where slug = 'teste-overall'", Long.class);
    }
}

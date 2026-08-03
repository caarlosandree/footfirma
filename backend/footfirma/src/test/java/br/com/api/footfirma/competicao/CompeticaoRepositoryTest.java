package br.com.api.footfirma.competicao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.competicao.domain.Competicao;
import br.com.api.footfirma.competicao.domain.Edicao;
import br.com.api.footfirma.competicao.domain.Fase;
import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import br.com.api.footfirma.competicao.domain.TipoClassificacao;
import br.com.api.footfirma.competicao.domain.TipoCompeticao;
import br.com.api.footfirma.competicao.domain.TipoFase;
import br.com.api.footfirma.competicao.repository.CompeticaoRepository;
import br.com.api.footfirma.competicao.repository.EdicaoRepository;
import br.com.api.footfirma.competicao.repository.RegraClassificacaoRepository;
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
class CompeticaoRepositoryTest {

    @Autowired
    CompeticaoRepository competicaoRepository;

    @Autowired
    EdicaoRepository edicaoRepository;

    @Autowired
    RegraClassificacaoRepository regraClassificacaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveListarCompeticoesDoPaisOrdenadasPorNivel() {
        competicaoRepository.save(competicao("brasileirao-serie-b", "Brasileirão Série B", TipoCompeticao.LIGA, 2));
        competicaoRepository.saveAndFlush(competicao("brasileirao-serie-a", "Brasileirão Série A", TipoCompeticao.LIGA, 1));

        var competicoes = competicaoRepository.findByPaisIso("BRA");

        assertThat(competicoes).extracting(Competicao::getSlug)
                .containsExactly("brasileirao-serie-a", "brasileirao-serie-b");
    }

    @Test
    void deveBuscarEdicaoComFasesPeloSlugEPelaTemporada() {
        var serieA = competicaoRepository.save(
                competicao("brasileirao-serie-a", "Brasileirão Série A", TipoCompeticao.LIGA, 1));
        var edicao = new Edicao(serieA, temporada("2025"), "Brasileirão Série A 2025");
        edicao.getFases().add(new Fase(edicao, 1, "Fase única", TipoFase.PONTOS_CORRIDOS));
        edicaoRepository.save(edicao);

        var encontrada = edicaoRepository.buscarPorSlugETemporadaId("brasileirao-serie-a", temporada("2025"));

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getFases()).hasSize(1);
        assertThat(encontrada.get().getFases().getFirst().getTipo()).isEqualTo(TipoFase.PONTOS_CORRIDOS);
    }

    @Test
    void deveGuardarRegraDeRebaixamentoComoDado() {
        var serieA = competicaoRepository.save(
                competicao("brasileirao-serie-a", "Brasileirão Série A", TipoCompeticao.LIGA, 1));
        var serieB = competicaoRepository.save(
                competicao("brasileirao-serie-b", "Brasileirão Série B", TipoCompeticao.LIGA, 2));
        var edicao = edicaoRepository.save(new Edicao(serieA, temporada("2025"), "Brasileirão Série A 2025"));

        regraClassificacaoRepository.save(
                new RegraClassificacao(edicao, 17, 20, TipoClassificacao.REBAIXAMENTO, serieB.getId()));
        regraClassificacaoRepository.save(
                new RegraClassificacao(edicao, 1, 4, TipoClassificacao.LIBERTADORES_GRUPOS, null));

        var regras = regraClassificacaoRepository.findByEdicaoIdOrderByPosicaoInicio(edicao.getId());

        assertThat(regras).hasSize(2);
        assertThat(regras.getFirst().getTipo()).isEqualTo(TipoClassificacao.LIBERTADORES_GRUPOS);
        assertThat(regras.getLast().getPosicaoInicio()).isEqualTo(17);
    }

    private Competicao competicao(String slug, String nome, TipoCompeticao tipo, Integer nivel) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var competicao = new Competicao(slug, nome, tipo);
        competicao.setPaisId(paisId);
        competicao.setNivel(nivel);
        return competicao;
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }
}

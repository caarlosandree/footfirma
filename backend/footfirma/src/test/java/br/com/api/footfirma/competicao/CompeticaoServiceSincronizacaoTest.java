package br.com.api.footfirma.competicao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CompeticaoServiceSincronizacaoTest {

    @Autowired
    CompeticaoService competicaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long paisId;
    private Long temporadaId;
    private Long clubeId;

    @BeforeEach
    void prepararCatalogo() {
        jdbcTemplate.update("delete from regra_classificacao where edicao_id in (select e.id from edicao e join competicao c on c.id = e.competicao_id where c.slug like 'sinc-%')");
        jdbcTemplate.update("delete from edicao_participante where edicao_id in (select e.id from edicao e join competicao c on c.id = e.competicao_id where c.slug like 'sinc-%')");
        jdbcTemplate.update("delete from fase where edicao_id in (select e.id from edicao e join competicao c on c.id = e.competicao_id where c.slug like 'sinc-%')");
        jdbcTemplate.update("delete from edicao where competicao_id in (select id from competicao where slug like 'sinc-%')");
        jdbcTemplate.update("delete from competicao where slug like 'sinc-%'");
        jdbcTemplate.update("delete from clube where slug like 'sincc-%'");
        jdbcTemplate.update("delete from temporada where label = '2097'");

        paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        jdbcTemplate.update("insert into temporada (label, ano_inicio, ano_fim) values ('2097', 2097, 2097)");
        temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2097'", Long.class);
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id, reputacao, qualidade_base)
                values ('sincc-um', 'Sincc Um Futebol Clube', 'Sincc Um', ?, 70, 60)
                """, paisId);
        clubeId = jdbcTemplate.queryForObject(
                "select id from clube where slug = 'sincc-um'", Long.class);
    }

    @Test
    void deveCriarCompeticaoQuandoSlugNaoExiste() {
        var resultado = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao("sinc-liga", "Liga Sinc", paisId, "LIGA", 1, "MASCULINO"));

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveDevolverOMesmoIdQuandoSlugDeCompeticaoJaExiste() {
        var primeiro = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao("sinc-liga2", "Liga Sinc 2", paisId, "LIGA", 1, "MASCULINO"));

        var segundo = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao("sinc-liga2", "Liga Sinc 2 Renomeada", paisId, "LIGA", 2, "MASCULINO"));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveReaproveitarEdicaoQuandoCompeticaoETemporadaCoincidem() {
        var competicaoId = novaCompeticao("sinc-liga3");
        var primeira = competicaoService.sincronizarEdicao(
                new DadosDeEdicao(competicaoId, temporadaId, "Liga Sinc 3 2097", null, null));

        var segunda = competicaoService.sincronizarEdicao(
                new DadosDeEdicao(competicaoId, temporadaId, "Liga Sinc 3 2097",
                        LocalDate.of(2097, 4, 1), LocalDate.of(2097, 12, 5)));

        assertThat(segunda.criado()).isFalse();
        assertThat(segunda.id()).isEqualTo(primeira.id());
    }

    @Test
    void deveManterFaseUnicaPorOrdemNaEdicao() {
        var edicaoId = novaEdicao("sinc-liga4");
        var primeira = competicaoService.sincronizarFase(
                new DadosDeFase(edicaoId, 1, "Fase única", "PONTOS_CORRIDOS", 1, false, false, false));

        var segunda = competicaoService.sincronizarFase(
                new DadosDeFase(edicaoId, 1, "Turno único", "PONTOS_CORRIDOS", 1, false, false, false));

        assertThat(segunda.criado()).isFalse();
        assertThat(segunda.id()).isEqualTo(primeira.id());
        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from fase where edicao_id = ?", Integer.class, edicaoId);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveAtualizarPosicaoFinalDoParticipanteSemDuplicar() {
        var edicaoId = novaEdicao("sinc-liga5");
        competicaoService.sincronizarParticipante(new DadosDeParticipante(edicaoId, clubeId, 5));

        var segundo = competicaoService.sincronizarParticipante(
                new DadosDeParticipante(edicaoId, clubeId, 2));

        assertThat(segundo.criado()).isFalse();
        var posicao = jdbcTemplate.queryForObject(
                "select posicao_final from edicao_participante where edicao_id = ? and clube_id = ?",
                Integer.class, edicaoId, clubeId);
        assertThat(posicao).isEqualTo(2);
    }

    @Test
    void deveAceitarRegraApontandoParaCompeticaoSemEdicao() {
        var edicaoId = novaEdicao("sinc-liga6");
        var destinoId = novaCompeticao("sinc-liga-destino");

        var resultado = competicaoService.sincronizarRegra(
                new DadosDeRegra(edicaoId, 7, 8, "REBAIXAMENTO", destinoId));

        assertThat(resultado.criado()).isTrue();
        var edicoesDoDestino = jdbcTemplate.queryForObject(
                "select count(*) from edicao where competicao_id = ?", Integer.class, destinoId);
        assertThat(edicoesDoDestino).isZero();
    }

    @Test
    void deveReaproveitarRegraQuandoFaixaETipoCoincidem() {
        var edicaoId = novaEdicao("sinc-liga7");
        var primeira = competicaoService.sincronizarRegra(
                new DadosDeRegra(edicaoId, 1, 4, "LIBERTADORES_GRUPOS", null));

        var segunda = competicaoService.sincronizarRegra(
                new DadosDeRegra(edicaoId, 1, 4, "LIBERTADORES_GRUPOS", null));

        assertThat(segunda.criado()).isFalse();
        assertThat(segunda.id()).isEqualTo(primeira.id());
    }

    @Test
    void deveResolverIdPorSlug() {
        var competicaoId = novaCompeticao("sinc-liga8");

        var encontrado = competicaoService.buscarIdPorSlug("sinc-liga8");

        assertThat(encontrado).contains(competicaoId);
    }

    private Long novaCompeticao(String slug) {
        return competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao(slug, "Competição " + slug, paisId, "LIGA", 1, "MASCULINO")).id();
    }

    private Long novaEdicao(String slug) {
        var competicaoId = novaCompeticao(slug);
        return competicaoService.sincronizarEdicao(
                new DadosDeEdicao(competicaoId, temporadaId, "Edição " + slug, null, null)).id();
    }
}

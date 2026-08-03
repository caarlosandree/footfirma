package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.dto.DadosDeAtributos;
import br.com.api.footfirma.jogador.dto.DadosDeAtributosOcultos;
import br.com.api.footfirma.jogador.dto.DadosDeCaracteristica;
import br.com.api.footfirma.jogador.dto.DadosDeJogador;
import br.com.api.footfirma.jogador.dto.DadosDePosicaoSecundaria;
import br.com.api.footfirma.jogador.dto.DadosDeVinculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JogadorServiceSincronizacaoTest {

    @Autowired
    JogadorService jogadorService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long paisId;
    private Long atacanteId;
    private Long zagueiroId;
    private Long temporadaA;
    private Long temporadaB;
    private Long clubeA;
    private Long clubeB;

    @BeforeEach
    void prepararCatalogo() {
        jdbcTemplate.update("delete from jogador_overall where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_vinculo where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_atributo where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_atributo_oculto where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_caracteristica where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_posicao where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador where slug like 'sinc-%'");
        jdbcTemplate.update("delete from clube where slug like 'sincj-%'");
        jdbcTemplate.update("delete from temporada where label in ('2095', '2096')");

        paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        atacanteId = jdbcTemplate.queryForObject("select id from posicao where codigo = 'ATA'", Long.class);
        zagueiroId = jdbcTemplate.queryForObject("select id from posicao where codigo = 'ZAG'", Long.class);
        temporadaA = inserirTemporada("2095");
        temporadaB = inserirTemporada("2096");
        clubeA = inserirClube("sincj-a");
        clubeB = inserirClube("sincj-b");
    }

    @Test
    void deveCriarJogadorQuandoChaveNaturalNaoExiste() {
        var resultado = jogadorService.sincronizarJogador(jogador("sinc-um", "sinc um"));

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveGravarASementeInformadaPeloImportador() {
        jogadorService.sincronizarJogador(jogador("sinc-dois", "sinc dois"));

        var semente = jdbcTemplate.queryForObject(
                "select semente from jogador where slug = 'sinc-dois'", Long.class);
        assertThat(semente).isEqualTo(4242424242L);
    }

    @Test
    void deveAtualizarOSlugQuandoAChaveNaturalJaExiste() {
        var primeiro = jogadorService.sincronizarJogador(jogador("sinc-tres", "sinc tres"));

        var segundo = jogadorService.sincronizarJogador(
                new DadosDeJogador("sinc-tres-renomeado", "sinc tres|1999-03-14|BRA", 4242424242L,
                        "Sinc Tres", "Sinc Tres", LocalDate.of(1999, 3, 14), paisId, null,
                        180, 75, "DIREITO", atacanteId, "REAL"));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
        var slug = jdbcTemplate.queryForObject(
                "select slug from jogador where id = ?", String.class, primeiro.id());
        assertThat(slug).isEqualTo("sinc-tres-renomeado");
    }

    @Test
    void deveManterUmaUnicaLinhaQuandoSincronizaJogadorDuasVezes() {
        jogadorService.sincronizarJogador(jogador("sinc-quatro", "sinc quatro"));
        jogadorService.sincronizarJogador(jogador("sinc-quatro", "sinc quatro"));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador where slug = 'sinc-quatro'", Integer.class);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveGuardarAtributosDeDuasTemporadasParaOMesmoJogador() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-cinco", "sinc cinco")).id();

        jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaA, 70));
        jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaB, 78));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_atributo where jogador_id = ?", Integer.class, jogadorId);
        assertThat(linhas).isEqualTo(2);
    }

    @Test
    void deveAtualizarAtributosQuandoJogadorETemporadaJaTemLinha() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-seis", "sinc seis")).id();
        jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaA, 70));

        var segundo = jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaA, 82));

        assertThat(segundo.criado()).isFalse();
        var finalizacao = jdbcTemplate.queryForObject(
                "select finalizacao from jogador_atributo where jogador_id = ? and temporada_id = ?",
                Integer.class, jogadorId, temporadaA);
        assertThat(finalizacao).isEqualTo(82);
    }

    @Test
    void deveSubstituirAtributosOcultosQuandoJogadorJaTemRegistro() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-sete", "sinc sete")).id();
        jogadorService.sincronizarAtributosOcultos(
                new DadosDeAtributosOcultos(jogadorId, 60, 60, 60, 60, 60, 60, 60, 60));

        var segundo = jogadorService.sincronizarAtributosOcultos(
                new DadosDeAtributosOcultos(jogadorId, 80, 60, 60, 60, 60, 60, 60, 60));

        assertThat(segundo.criado()).isFalse();
        var profissionalismo = jdbcTemplate.queryForObject(
                "select profissionalismo from jogador_atributo_oculto where jogador_id = ?",
                Integer.class, jogadorId);
        assertThat(profissionalismo).isEqualTo(80);
    }

    @Test
    void deveAceitarDoisVinculosNaMesmaTemporadaEmClubesDiferentes() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-oito", "sinc oito")).id();

        jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeA, temporadaA, 9));
        jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeB, temporadaA, 11));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_vinculo where jogador_id = ?", Integer.class, jogadorId);
        assertThat(linhas).isEqualTo(2);
    }

    @Test
    void deveReaproveitarVinculoQuandoJogadorTemporadaEClubeCoincidem() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-nove", "sinc nove")).id();
        var primeiro = jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeA, temporadaA, 9));

        var segundo = jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeA, temporadaA, 10));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveReaproveitarPosicaoSecundariaQuandoJogadorEPosicaoCoincidem() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-dez", "sinc dez")).id();
        jogadorService.sincronizarPosicaoSecundaria(
                new DadosDePosicaoSecundaria(jogadorId, zagueiroId, 2));

        var segundo = jogadorService.sincronizarPosicaoSecundaria(
                new DadosDePosicaoSecundaria(jogadorId, zagueiroId, 3));

        assertThat(segundo.criado()).isFalse();
        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_posicao where jogador_id = ?", Integer.class, jogadorId);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveVincularCaracteristicaDoCatalogoAoJogador() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-onze", "sinc onze")).id();
        var caracteristicaId = jogadorService.buscarIdCaracteristicaPorCodigo("DRIBLADOR").orElseThrow();

        var resultado = jogadorService.sincronizarCaracteristica(
                new DadosDeCaracteristica(jogadorId, caracteristicaId));

        assertThat(resultado.criado()).isTrue();
    }

    @Test
    void deveDevolverVazioQuandoCodigoDeCaracteristicaNaoExiste() {
        var encontrada = jogadorService.buscarIdCaracteristicaPorCodigo("NAO_EXISTE");

        assertThat(encontrada).isEmpty();
    }

    private DadosDeJogador jogador(String slug, String nomeNormalizado) {
        return new DadosDeJogador(slug, nomeNormalizado + "|1999-03-14|BRA", 4242424242L,
                "Sinc " + slug, "Sinc " + slug, LocalDate.of(1999, 3, 14), paisId, null,
                180, 75, "DIREITO", atacanteId, "REAL");
    }

    private DadosDeAtributos atributos(Long jogadorId, Long temporadaId, int finalizacao) {
        return new DadosDeAtributos(jogadorId, temporadaId,
                70, 70, 70, 70, 70, 70, 70, 70, 70,
                finalizacao, 70, 70, 70, 70, 70, 30, 30, 30,
                85, 5, "IMPORTADO", OffsetDateTime.parse("2026-01-15T10:00:00Z"));
    }

    @Test
    void deveGravarVinculoDeBase() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-base", "sinc base")).id();
        var dados = new DadosDeVinculo(jogadorId, clubeA, temporadaA, "CONTRATO", "BASE",
                null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

        var resultado = jogadorService.sincronizarVinculo(dados);

        var categoria = jdbcTemplate.queryForObject(
                "select categoria from jogador_vinculo where id = ?", String.class, resultado.id());
        assertThat(categoria).isEqualTo("BASE");
    }

    private DadosDeVinculo vinculo(Long jogadorId, Long clubeId, Long temporadaId, int camisa) {
        return new DadosDeVinculo(jogadorId, clubeId, temporadaId, "CONTRATO", "PROFISSIONAL",
                camisa, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("1500000.00"));
    }

    private Long inserirTemporada(String label) {
        jdbcTemplate.update(
                "insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)",
                label, Integer.valueOf(label), Integer.valueOf(label));
        return jdbcTemplate.queryForObject(
                "select id from temporada where label = ?", Long.class, label);
    }

    private Long inserirClube(String slug) {
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id, reputacao, qualidade_base)
                values (?, ?, ?, ?, 70, 60)
                """, slug, slug + " Futebol Clube", slug, paisId);
        return jdbcTemplate.queryForObject("select id from clube where slug = ?", Long.class, slug);
    }
}

package br.com.api.footfirma.clube;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.clube.dto.DadosDeAlias;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClubeServiceSincronizacaoTest {

    @Autowired
    ClubeService clubeService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long paisId;

    @BeforeEach
    void limparClubesDeTeste() {
        jdbcTemplate.update("delete from clube_alias where alias like 'sinc-%'");
        jdbcTemplate.update("delete from clube where slug like 'sinc-%'");
        jdbcTemplate.update("delete from estadio where nome like 'Estádio Sinc%'");
        paisId = jdbcTemplate.queryForObject(
                "select id from pais where iso_code = 'BRA'", Long.class);
    }

    @Test
    void deveCriarClubeQuandoSlugNaoExiste() {
        var resultado = clubeService.sincronizarClube(clube("sinc-alfa", "Alfa", 70));

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveDevolverOMesmoIdQuandoSlugJaExiste() {
        var primeiro = clubeService.sincronizarClube(clube("sinc-beta", "Beta", 70));

        var segundo = clubeService.sincronizarClube(clube("sinc-beta", "Beta", 80));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveGravarAReputacaoAtualizadaQuandoClubeJaExiste() {
        clubeService.sincronizarClube(clube("sinc-gama", "Gama", 70));

        clubeService.sincronizarClube(clube("sinc-gama", "Gama", 85));

        var reputacao = jdbcTemplate.queryForObject(
                "select reputacao from clube where slug = 'sinc-gama'", Integer.class);
        assertThat(reputacao).isEqualTo(85);
    }

    @Test
    void deveManterUmaUnicaLinhaQuandoSincronizaClubeDuasVezes() {
        clubeService.sincronizarClube(clube("sinc-delta", "Delta", 70));
        clubeService.sincronizarClube(clube("sinc-delta", "Delta", 70));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from clube where slug = 'sinc-delta'", Integer.class);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveReaproveitarEstadioQuandoNomeECidadeCoincidem() {
        var primeiro = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Um", "Belo Horizonte", null, 40000, 1965));

        var segundo = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Um", "Belo Horizonte", null, 45000, 1965));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveCriarEstadiosDistintosQuandoCidadeDifere() {
        var soteropolitano = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Dois", "Salvador", null, 30000, 1970));

        var santista = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Dois", "Santos", null, 30000, 1970));

        assertThat(santista.criado()).isTrue();
        assertThat(santista.id()).isNotEqualTo(soteropolitano.id());
    }

    @Test
    void deveReaproveitarAliasQuandoAliasEFonteCoincidem() {
        var clubeId = clubeService.sincronizarClube(clube("sinc-epsilon", "Epsilon", 70)).id();
        var primeiro = clubeService.sincronizarAlias(
                new DadosDeAlias(clubeId, "sinc-epsilon-apelido", "MANUAL"));

        var segundo = clubeService.sincronizarAlias(
                new DadosDeAlias(clubeId, "sinc-epsilon-apelido", "MANUAL"));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveLigarOEstadioAoClubeQuandoInformado() {
        var estadioId = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Três", "Curitiba", null, 25000, 1980)).id();
        var dados = new DadosDeClube("sinc-zeta", "Zeta Futebol Clube", "Zeta", null,
                1912, paisId, null, estadioId, "#FF0000", "#FFFFFF", 70, 50, 60, null);

        clubeService.sincronizarClube(dados);

        var gravado = jdbcTemplate.queryForObject(
                "select estadio_id from clube where slug = 'sinc-zeta'", Long.class);
        assertThat(gravado).isEqualTo(estadioId);
    }

    @Test
    void deveGravarForcaFinanceiraDoClube() {
        var dados = new DadosDeClube("sinc-omega", "Omega Futebol Clube", "Omega", null,
                1912, paisId, null, null, "#101010", "#FFFFFF", 71, 44, 88, null);

        var resultado = clubeService.sincronizarClube(dados);

        var forca = jdbcTemplate.queryForObject(
                "select forca_financeira from clube where id = ?", Integer.class, resultado.id());
        assertThat(forca).isEqualTo(44);
    }

    private DadosDeClube clube(String slug, String nomeCurto, int reputacao) {
        return new DadosDeClube(slug, nomeCurto + " Futebol Clube", nomeCurto, null,
                1900, paisId, null, null, "#0000FF", "#FFFFFF", reputacao, 50, 60, null);
    }
}

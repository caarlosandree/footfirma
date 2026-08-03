package br.com.api.footfirma.temporada;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TemporadaServiceSincronizacaoTest {

    @Autowired
    TemporadaService temporadaService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparTemporadasDeTeste() {
        jdbcTemplate.update("delete from temporada where label like '209%'");
    }

    @Test
    void deveCriarTemporadaQuandoLabelNaoExiste() {
        var dados = new DadosDeTemporada("2091", 2091, 2091);

        var resultado = temporadaService.sincronizar(dados);

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveDevolverOMesmoIdQuandoLabelJaExiste() {
        var primeiro = temporadaService.sincronizar(new DadosDeTemporada("2092", 2092, 2092));

        var segundo = temporadaService.sincronizar(new DadosDeTemporada("2092", 2092, 2093));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveGravarOsAnosAtualizadosQuandoTemporadaJaExiste() {
        temporadaService.sincronizar(new DadosDeTemporada("2093", 2093, 2093));

        temporadaService.sincronizar(new DadosDeTemporada("2093", 2093, 2094));

        var anoFim = jdbcTemplate.queryForObject(
                "select ano_fim from temporada where label = '2093'", Integer.class);
        assertThat(anoFim).isEqualTo(2094);
    }

    @Test
    void deveManterUmaUnicaLinhaQuandoSincronizaDuasVezes() {
        temporadaService.sincronizar(new DadosDeTemporada("2094", 2094, 2094));
        temporadaService.sincronizar(new DadosDeTemporada("2094", 2094, 2094));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from temporada where label = '2094'", Integer.class);
        assertThat(linhas).isEqualTo(1);
    }
}

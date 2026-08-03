package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.domain.JogadorVinculo;
import br.com.api.footfirma.jogador.domain.TipoVinculo;
import br.com.api.footfirma.jogador.repository.CaracteristicaRepository;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.JogadorVinculoRepository;
import br.com.api.footfirma.jogador.repository.PosicaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JogadorVinculoRepositoryTest {

    @Autowired
    JogadorRepository jogadorRepository;

    @Autowired
    JogadorVinculoRepository jogadorVinculoRepository;

    @Autowired
    CaracteristicaRepository caracteristicaRepository;

    @Autowired
    PosicaoRepository posicaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCarregarAsCaracteristicasDaMigrationDeSeed() {
        var caracteristicas = caracteristicaRepository.findAll();

        assertThat(caracteristicas).hasSize(13);
        assertThat(caracteristicaRepository.findByCodigo("BICICLETA")).isPresent();
    }

    @Test
    void deveBuscarElencoDoClubeNaTemporada() {
        var clubeId = clube("flamengo");
        var temporadaId = temporada("2025");
        vinculo(jogador("jogador-um", "Jogador Um"), clubeId, temporadaId, 10);
        vinculo(jogador("jogador-dois", "Jogador Dois"), clubeId, temporadaId, 9);

        var elenco = jogadorVinculoRepository.buscarElenco(clubeId, temporadaId);

        assertThat(elenco).hasSize(2);
        assertThat(elenco).extracting(vinculo -> vinculo.getJogador().getNomeExibicao())
                .containsExactlyInAnyOrder("Jogador Um", "Jogador Dois");
    }

    @Test
    void naoDeveRetornarElencoDeOutraTemporada() {
        var clubeId = clube("palmeiras");
        vinculo(jogador("jogador-tres", "Jogador Três"), clubeId, temporada("2025"), 7);

        var elencoDe2026 = jogadorVinculoRepository.buscarElenco(clubeId, temporada("2026"));

        assertThat(elencoDe2026).isEmpty();
    }

    private Jogador jogador(String slug, String nome) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var atacante = posicaoRepository.findByCodigo("ATA").orElseThrow();
        return jogadorRepository.save(
                JogadorFactory.valido(slug, nome, LocalDate.of(1999, 1, 1), paisId, atacante));
    }

    private void vinculo(Jogador jogador, Long clubeId, Long temporadaId, int camisa) {
        var vinculo = new JogadorVinculo(jogador, clubeId, temporadaId, TipoVinculo.CONTRATO);
        vinculo.setNumeroCamisa(camisa);
        jogadorVinculoRepository.save(vinculo);
    }

    private Long clube(String slug) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id) values (?, ?, ?, ?)
                on conflict (slug) do nothing
                """, slug, slug + " oficial", slug, paisId);
        return jdbcTemplate.queryForObject("select id from clube where slug = ?", Long.class, slug);
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }
}

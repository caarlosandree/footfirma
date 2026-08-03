package br.com.api.footfirma.clube;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.clube.domain.Clube;
import br.com.api.footfirma.clube.domain.ClubeAlias;
import br.com.api.footfirma.clube.domain.FonteExterna;
import br.com.api.footfirma.clube.repository.ClubeAliasRepository;
import br.com.api.footfirma.clube.repository.ClubeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClubeRepositoryTest {

    @Autowired
    ClubeRepository clubeRepository;

    @Autowired
    ClubeAliasRepository clubeAliasRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveEncontrarClubePeloSlug() {
        clubeRepository.save(ClubeFactory.valido("gremio", "Grêmio", idDoBrasil()));

        var encontrado = clubeRepository.findBySlug("gremio");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getNomeCurto()).isEqualTo("Grêmio");
    }

    @Test
    void deveResolverClubePorAliasDeFonteExterna() {
        var clube = clubeRepository.save(ClubeFactory.valido("atletico-mg", "Atlético-MG", idDoBrasil()));
        clubeAliasRepository.save(new ClubeAlias(clube, "Clube Atlético Mineiro", FonteExterna.TRANSFERMARKT));

        var resolvido = clubeAliasRepository.findByAlias("Clube Atlético Mineiro");

        assertThat(resolvido).isPresent();
        assertThat(resolvido.get().getClube().getSlug()).isEqualTo("atletico-mg");
    }

    @Test
    void deveListarClubesPaginadosOrdenadosPorNome() {
        clubeRepository.save(ClubeFactory.valido("santos", "Santos", idDoBrasil()));
        clubeRepository.save(ClubeFactory.valido("bahia", "Bahia", idDoBrasil()));

        var pagina = clubeRepository.findAllByOrderByNomeCurto(PageRequest.of(0, 10));

        assertThat(pagina.getContent()).extracting(Clube::getSlug)
                .containsExactly("bahia", "santos");
    }

    private Long idDoBrasil() {
        return jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
    }
}

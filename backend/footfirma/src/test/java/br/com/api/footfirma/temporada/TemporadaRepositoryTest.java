package br.com.api.footfirma.temporada;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.temporada.domain.Temporada;
import br.com.api.footfirma.temporada.repository.TemporadaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TemporadaRepositoryTest {

    @Autowired
    TemporadaRepository temporadaRepository;

    @Test
    void deveEncontrarTemporadaPeloLabel() {
        temporadaRepository.save(new Temporada("2025", 2025, 2025));

        var encontrada = temporadaRepository.findByLabel("2025");

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getAnoInicio()).isEqualTo(2025);
    }

    @Test
    void deveListarTemporadasDaMaisRecenteParaAMaisAntiga() {
        temporadaRepository.save(new Temporada("2024", 2024, 2024));
        temporadaRepository.save(new Temporada("2026", 2026, 2026));
        temporadaRepository.save(new Temporada("2025", 2025, 2025));

        var temporadas = temporadaRepository.findAllByOrderByAnoInicioDesc();

        assertThat(temporadas).extracting(Temporada::getLabel)
                .containsExactly("2026", "2025", "2024");
    }
}

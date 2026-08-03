package br.com.api.footfirma.geografia;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.geografia.domain.Confederacao;
import br.com.api.footfirma.geografia.domain.Estado;
import br.com.api.footfirma.geografia.repository.EstadoRepository;
import br.com.api.footfirma.geografia.repository.PaisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GeografiaRepositoryTest {

    @Autowired
    PaisRepository paisRepository;

    @Autowired
    EstadoRepository estadoRepository;

    @Test
    void deveEncontrarBrasilCarregadoPelaMigrationDeSeed() {
        var brasil = paisRepository.findByIsoCode("BRA");

        assertThat(brasil).isPresent();
        assertThat(brasil.get().getNome()).isEqualTo("Brasil");
        assertThat(brasil.get().getConfederacao()).isEqualTo(Confederacao.CONMEBOL);
    }

    @Test
    void deveCarregarAsVinteESeteUnidadesFederativas() {
        var estados = estadoRepository.findByPaisIsoCode("BRA");

        assertThat(estados).hasSize(27);
        assertThat(estados.getFirst().getUf()).isEqualTo("AC");
    }

    @Test
    void deveTrazerOPaisJuntoDoEstadoSemConsultaAdicional() {
        var estados = estadoRepository.findByPaisIsoCode("BRA");

        assertThat(estados).extracting(Estado::getPais)
                .allSatisfy(pais -> assertThat(pais.getIsoCode()).isEqualTo("BRA"));
    }
}

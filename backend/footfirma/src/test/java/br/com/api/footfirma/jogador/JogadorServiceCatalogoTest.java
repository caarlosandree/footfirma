package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JogadorServiceCatalogoTest {

    @Autowired
    JogadorService jogadorService;

    @Test
    void deveListarAsNovePosicoesNaOrdemDoCatalogo() {
        var posicoes = jogadorService.listarPosicoes();

        assertThat(posicoes).hasSize(9);
        assertThat(posicoes.getFirst())
                .extracting("codigo", "nome", "setor")
                .containsExactly("GOL", "Goleiro", "GOLEIRO");
        assertThat(posicoes.getLast().codigo()).isEqualTo("ATA");
    }

    @Test
    void deveDevolverIdentificadorDePosicaoUtilizavelComoChaveEstrangeira() {
        var posicoes = jogadorService.listarPosicoes();

        assertThat(posicoes)
                .extracting("id")
                .doesNotContainNull();
        assertThat(posicoes).extracting("codigo")
                .containsExactly("GOL", "ZAG", "LTD", "LTE", "VOL", "MEC", "MEA", "PTA", "ATA");
    }
}

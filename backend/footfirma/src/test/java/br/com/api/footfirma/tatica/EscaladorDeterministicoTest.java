package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.shared.exception.EscalacaoInvalidaException;
import br.com.api.footfirma.tatica.dto.Escalado;
import br.com.api.footfirma.tatica.dto.OrigemDoPlano;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EscaladorDeterministicoTest {

    @Autowired
    TaticaService tatica;

    @Autowired
    JdbcTemplate jdbc;

    CenarioDeElenco cenario;

    @BeforeEach
    void montarCenario() {
        cenario = CenarioDeElenco.montar(jdbc);
    }

    @Test
    void deveMontarOMesmoOnzeEmDuasExecucoes() {
        var primeiro = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());
        jdbc.update("delete from plano_escalacao");
        jdbc.update("delete from plano_tatico");

        var segundo = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(ids(segundo.titulares())).isEqualTo(ids(primeiro.titulares()));
        assertThat(ids(segundo.banco())).isEqualTo(ids(primeiro.banco()));
        assertThat(segundo.formacaoId()).isEqualTo(primeiro.formacaoId());
        assertThat(segundo.mentalidade()).isEqualTo(primeiro.mentalidade());
        assertThat(segundo.capitaoId()).isEqualTo(primeiro.capitaoId());
    }

    @Test
    void deveDevolverOPlanoExistenteSemCriarOutro() {
        var criado = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        var segunda = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(segunda.planoId()).isEqualTo(criado.planoId());
        assertThat(jdbc.queryForObject("select count(*) from plano_tatico", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void deveGravarOPlanoComOrigemAutomatica() {
        var plano = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(plano.origem()).isEqualTo(OrigemDoPlano.AUTOMATICO);
        assertThat(plano.titulares()).hasSize(11);
        assertThat(plano.banco()).isNotEmpty();
        assertThat(plano.capitaoId()).isNotNull();
    }

    @Test
    void deveCairNoRepertorioMinimoQuandoNaoHaSkillGravada() {
        jdbc.update("delete from treinador_skill where temporada_id = ?", cenario.temporadaId());

        var plano = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(plano.titulares()).hasSize(11);
        assertThat(plano.formacaoCodigo())
                .as("sem skill, o repertório é só a primeira formação do catálogo")
                .isEqualTo("4-4-2");
    }

    @Test
    void deveEstourarQuandoOElencoNaoFechaOTime() {
        jdbc.update("delete from jogador_vinculo where clube_id = ?", cenario.clubeId());

        assertThatThrownBy(() -> tatica.garantirPlanoVigente(cenario.clubeId(),
                cenario.temporadaId()))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("elenco");
    }

    private static List<Long> ids(List<Escalado> escalados) {
        return escalados.stream().map(Escalado::jogadorId).toList();
    }
}

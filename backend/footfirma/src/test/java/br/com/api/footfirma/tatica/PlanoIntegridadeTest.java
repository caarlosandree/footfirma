package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.tatica.repository.FormacaoRepository;
import br.com.api.footfirma.tatica.repository.PlanoEscalacaoRepository;
import br.com.api.footfirma.tatica.repository.PlanoTaticoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlanoIntegridadeTest {

    @Autowired
    PlanoTaticoRepository planos;

    @Autowired
    PlanoEscalacaoRepository escalacoes;

    @Autowired
    FormacaoRepository formacoes;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    CenarioDeElenco cenario;
    long formacaoId;
    long posicao;

    @BeforeEach
    void montarCenario() {
        cenario = CenarioDeElenco.montar(jdbc);
        formacaoId = formacoes.findAllByOrderByOrdemAsc().getFirst().getId();
        posicao = cenario.posicoesEmOrdem().getFirst();
    }

    @Test
    void deveRejeitarSegundoPlanoVigenteNoMesmoClubeETemporada() {
        planos.save(PlanoFactory.vigente(cenario, formacaoId, 1));
        em.flush();

        var segundo = PlanoFactory.vigente(cenario, formacaoId, 2);

        assertThatThrownBy(() -> planos.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_plano_vigente");
    }

    @Test
    void devePermitirSegundoPlanoQuandoOPrimeiroDeixaDeSerVigente() {
        var primeiro = planos.save(PlanoFactory.vigente(cenario, formacaoId, 1));
        primeiro.setVigente(false);
        em.flush();

        var segundo = planos.saveAndFlush(PlanoFactory.vigente(cenario, formacaoId, 2));

        assertThat(segundo.getId()).isNotNull();
    }

    @Test
    void deveRejeitarTitularSemSlot() {
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, formacaoId, 1));

        var invalido = PlanoFactory.titular(plano.getId(), cenario.primeiroJogadorId(),
                posicao, 1, 70);
        invalido.setSlotOrdem(null);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(invalido))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_papel_coerente");
    }

    @Test
    void deveRejeitarReservaComSlot() {
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, formacaoId, 1));

        var invalido = PlanoFactory.reserva(plano.getId(), cenario.primeiroJogadorId(),
                posicao, 1, 70);
        invalido.setSlotOrdem(3);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(invalido))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_papel_coerente");
    }

    @Test
    void deveRejeitarDoisTitularesNoMesmoSlot() {
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, formacaoId, 1));
        escalacoes.saveAndFlush(PlanoFactory.titular(plano.getId(),
                cenario.primeiroJogadorId(), posicao, 1, 70));

        var colidente = PlanoFactory.titular(plano.getId(),
                cenario.segundoJogadorId(), posicao, 1, 68);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(colidente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_escalacao_slot");
    }

    @Test
    void deveRejeitarDoisReservasNaMesmaOrdemDeBanco() {
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, formacaoId, 1));
        escalacoes.saveAndFlush(PlanoFactory.reserva(plano.getId(),
                cenario.primeiroJogadorId(), posicao, 3, 60));

        var colidente = PlanoFactory.reserva(plano.getId(),
                cenario.segundoJogadorId(), posicao, 3, 59);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(colidente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_escalacao_banco");
    }
}

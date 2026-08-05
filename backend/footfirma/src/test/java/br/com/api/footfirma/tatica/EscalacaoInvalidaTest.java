package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.shared.exception.EscalacaoInvalidaException;
import br.com.api.footfirma.tatica.dto.TitularEscalado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EscalacaoInvalidaTest {

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
    void deveAceitarUmPlanoValido() {
        assertThatCode(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario))).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarDezTitulares() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var dezTitulares = new ArrayList<>(plano.titulares());
        dezTitulares.removeLast();

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, dezTitulares, plano.banco(), plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("11");
    }

    @Test
    void deveRejeitarSlotForaDaFaixa() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var comSlotInvalido = new ArrayList<>(plano.titulares());
        comSlotInvalido.set(10, new TitularEscalado(
                comSlotInvalido.getLast().jogadorId(), 12));

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, comSlotInvalido, plano.banco(), plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("slot");
    }

    @Test
    void deveRejeitarBancoSemGoleiroReserva() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        // PlanoDeTeste põe o goleiro reserva na primeira posição do banco. Tirá-lo deixa
        // o banco com cinco, ainda dentro do mínimo, e sem goleiro — que é o caso aqui.
        var semGoleiro = new ArrayList<>(plano.banco());
        semGoleiro.removeFirst();
        assertThat(semGoleiro).hasSize(5);

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), semGoleiro, plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("goleiro");
    }

    @Test
    void deveRejeitarJogadorDeOutroClube() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var forasteiro = new ArrayList<>(plano.titulares());
        forasteiro.set(10, new TitularEscalado(999_999L, 11));

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, forasteiro, plano.banco(), plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("vínculo");
    }

    @Test
    void deveRejeitarCapitaoQueEstaNoBanco() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), plano.banco(),
                        plano.banco().getFirst())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("capitão");
    }

    @Test
    void deveRejeitarBancoMenorQueOMinimo() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), List.of(plano.banco().getFirst()),
                        plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("banco");
    }

    @Test
    void deveRejeitarJogadorRepetidoEntreTitularEBanco() {
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var bancoComTitular = new ArrayList<>(plano.banco());
        bancoComTitular.set(1, plano.titulares().getFirst().jogadorId());

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), bancoComTitular, plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("repetido");
    }

    @Test
    void deveProvarQueOBancoSozinhoAceitaAsViolacoesDoServico() {
        var formacaoId = jdbc.queryForObject(
                "select id from formacao where codigo = '4-4-2'", Long.class);
        var planoId = jdbc.queryForObject("""
                insert into plano_tatico (clube_id, temporada_id, versao, vigente, formacao_id,
                                          origem, mentalidade, ritmo, linha_defensiva,
                                          pressao, largura)
                values (?, ?, 1, true, ?, 'MANUAL', 'EQUILIBRADA', 'EQUILIBRADO', 'MEDIA',
                        'MEDIA', 'MEDIA') returning id
                """, Long.class, cenario.clubeId(), cenario.temporadaId(), formacaoId);

        // Um único titular, sem banco, sem goleiro reserva: o banco de dados aceita.
        jdbc.update("""
                insert into plano_escalacao (plano_id, jogador_id, papel, slot_ordem,
                                             posicao_id, aptidao_no_momento)
                values (?, ?, 'TITULAR', 1, ?, 70)
                """, planoId, cenario.primeiroJogadorId(), cenario.posicoesEmOrdem().getFirst());

        assertThat(jdbc.queryForObject(
                "select count(*) from plano_escalacao where plano_id = ?", Long.class, planoId))
                .as("é por isso que as invariantes de agregado vivem no serviço")
                .isEqualTo(1L);
    }
}

package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.evento.PartidaEncerrada;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O motor nunca lê {@code clube.reputacao} — as reputações vigentes viajam dentro do
 * evento, congeladas no instante do fato.
 *
 * <p>Estes testes falham se alguém, por conveniência, fizer o motor consultar o clube. É
 * essa conveniência que quebraria tudo quando o spec de progressão tornar a reputação
 * dinâmica: reprocessar uma campanha antiga passaria a produzir uma carreira diferente da
 * que aconteceu.
 *
 * <p>O experimento é o mais controlado possível: mesmo treinador, mesmo clube, mesma moral
 * inicial, mesma meta, mesmíssima lista de eventos. A <b>única</b> coisa que muda entre as
 * duas passagens é a reputação do clube gravada no banco.
 */
class CongelamentoTest extends CenarioDeTreinador {

    private static final LocalDate FIM_DA_TEMPORADA = LocalDate.of(2026, 12, 1);

    @Test
    void deveDevolverAMoralOriginalAoReprocessarComReputacaoDoClubeAlterada() {
        var celeiro = clube("celeiro", 35);
        var treinador = treinador("ana-souza");
        var campanha = campanhaDoCeleiro(celeiro);

        var primeira = vinculo(treinador, celeiro, 50.0, 16);
        campanha.forEach(ouvinte::processar);
        var moralOriginal = moralDe(primeira);
        encerrar(primeira);

        oCeleiroViraGigante(celeiro);
        var segunda = vinculo(treinador, celeiro, 50.0, 16);
        campanha.forEach(ouvinte::processar);

        assertThat(moralDe(segunda)).isEqualTo(moralOriginal);
    }

    @Test
    void naoDeveDemitirNoReprocessamentoDepoisQueOClubeVirouGigante() {
        var celeiro = clube("celeiro", 35);
        var treinador = treinador("bia-lima");
        var campanha = campanhaDoCeleiro(celeiro);

        var primeira = vinculo(treinador, celeiro, 50.0, 16);
        campanha.forEach(ouvinte::processar);
        encerrar(primeira);

        oCeleiroViraGigante(celeiro);
        var segunda = vinculo(treinador, celeiro, 50.0, 16);
        campanha.forEach(ouvinte::processar);

        // Um gigante nesta campanha é demitido — ver CampanhaSimuladaTest. Sobreviver aqui
        // é a prova de que a reputação 85 do banco não entrou em nenhuma conta.
        assertThat(recarregar(segunda).getFim()).isNull();
    }

    @Test
    void deveTerminarLongeDoTetoParaQueAComparacaoSignifiqueAlgo() {
        var celeiro = clube("celeiro", 35);
        var vinculo = vinculo(treinador("caio-melo"), celeiro, 50.0, 16);

        campanhaDoCeleiro(celeiro).forEach(ouvinte::processar);

        // Sem esta guarda, as duas passagens poderiam empatar apenas por estarem ambas
        // grudadas no clamp de 99 — e o teste de congelamento passaria sem provar nada.
        assertThat(moralDe(vinculo)).isBetween(50.1, 98.9);
    }

    private List<PartidaEncerrada> campanhaDoCeleiro(Long celeiro) {
        return EventoFactory.campanha(celeiro, 35, clube("adversario", 60), 60,
                EventoFactory.campanhaMediana());
    }

    private void oCeleiroViraGigante(Long celeiro) {
        jdbcTemplate.update("update clube set reputacao = 85 where id = ?", celeiro);
    }

    private void encerrar(VinculoTreinador vinculo) {
        vinculo.encerrar(FIM_DA_TEMPORADA, MotivoFim.FIM_DE_CONTRATO);
        vinculos.flush();
    }
}

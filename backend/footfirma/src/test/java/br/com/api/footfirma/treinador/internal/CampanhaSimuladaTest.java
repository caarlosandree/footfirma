package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.Resultado;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste que carrega o spec: 38 eventos sintéticos, nenhuma partida real.
 *
 * <p>Os dois cenários rodam sobre <b>a mesma sequência de resultados</b> e discordam no
 * desfecho. É a prova de que a régua é relativa à expectativa: se alguém trocar a fórmula
 * por absoluta, este teste quebra e diz exatamente por quê.
 */
class CampanhaSimuladaTest extends CenarioDeTreinador {

    /** 14 vitórias, 10 empates, 14 derrotas — campanha de meio de tabela. */
    private static final List<Resultado> CAMPANHA_MEDIANA = EventoFactory.campanhaMediana();

    private static final int RODADAS = 38;

    @Test
    void deveSerUmaCampanhaMedianaDeTrintaEOitoRodadas() {
        assertThat(CAMPANHA_MEDIANA).hasSize(RODADAS);
        assertThat(CAMPANHA_MEDIANA).filteredOn(Resultado.VITORIA::equals).hasSize(14);
        assertThat(CAMPANHA_MEDIANA).filteredOn(Resultado.EMPATE::equals).hasSize(10);
        assertThat(CAMPANHA_MEDIANA).filteredOn(Resultado.DERROTA::equals).hasSize(14);
    }

    @Test
    void deveManterTreinadorDoClubePequenoAposCampanhaMediana() {
        var celeiro = clube("celeiro", 35);
        var vinculo = vinculo(treinador("ana-souza"), celeiro, 50.0, 16);

        campanhaDe(celeiro, 35);

        assertThat(recarregar(vinculo).getFim()).isNull();
        assertThat(moralDe(vinculo)).isGreaterThan(50.0);
    }

    @Test
    void deveChegarAoFimDaTemporadaComOClubePequeno() {
        var celeiro = clube("celeiro", 35);
        var vinculo = vinculo(treinador("bia-lima"), celeiro, 50.0, 16);

        campanhaDe(celeiro, 35);

        assertThat(processados.countByVinculoId(vinculo.getId())).isEqualTo(RODADAS);
    }

    @Test
    void deveDemitirTreinadorDoGiganteAposAMesmaCampanha() {
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("caio-melo"), gigante, 50.0, 1);

        campanhaDe(gigante, 88);

        assertThat(recarregar(vinculo).getFim()).isNotNull();
        assertThat(recarregar(vinculo).getMotivoFim()).isEqualTo(MotivoFim.DEMISSAO);
    }

    @Test
    void deveDemitirOGiganteAntesDaUltimaRodada() {
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("davi-rocha"), gigante, 50.0, 1);

        campanhaDe(gigante, 88);

        // Depois da demissão o clube não tem vínculo ativo e as rodadas restantes passam
        // direto — a contagem é a rodada em que o conselho perdeu a paciência.
        assertThat(processados.countByVinculoId(vinculo.getId())).isLessThan(RODADAS);
    }

    /** A mesma campanha, contra um adversário de reputação média, vista do clube dado. */
    private void campanhaDe(Long clubeId, int reputacaoPropria) {
        var adversario = clube("adversario", 60);
        EventoFactory.campanha(clubeId, reputacaoPropria, adversario, 60, CAMPANHA_MEDIANA)
                .forEach(ouvinte::processar);
    }
}

package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.evento.TemporadaEncerrada;
import br.com.api.footfirma.treinador.CenarioDeTreinador;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.StatusConfianca;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O fim do ano fecha as contas de cada treinador e encerra o vínculo da temporada.
 *
 * <p>A classificação chega dentro do evento porque a tabela de classificação não existe —
 * e porque a de 2026 não pode mudar depois que 2027 acontecer.
 */
class OuvinteDeTemporadaTest extends CenarioDeTreinador {

    private static final Instant FIM_DO_ANO = Instant.parse("2026-12-15T20:00:00Z");
    private static final int REPUTACAO_INICIAL = 50;

    @Autowired
    OuvinteDeTemporada ouvinte;

    @Test
    void deveDarTresPontosQuandoTreinadorBateuAMeta() {
        var vinculo = vinculo(treinador("ana-souza"), clube("celeiro", 35), 60.0, 8);

        encerrarTemporadaCom(vinculo, 4);

        assertThat(pontosDe(vinculo)).isEqualTo(3);
    }

    @Test
    void deveDarDoisPontosQuandoNaoBateuAMetaMasSobreviveu() {
        var vinculo = vinculo(treinador("bia-lima"), clube("celeiro", 35), 60.0, 4);

        encerrarTemporadaCom(vinculo, 9);

        assertThat(pontosDe(vinculo)).isEqualTo(2);
    }

    @Test
    void deveDarUmPontoQuandoFoiDemitido() {
        var vinculo = demitido(treinador("caio-melo"), 4);

        encerrarTemporadaCom(vinculo, 9);

        assertThat(pontosDe(vinculo)).isEqualTo(1);
    }

    @Test
    void deveDarDoisPontosQuandoAPosicaoFinalNaoVeioNoEvento() {
        var vinculo = vinculo(treinador("davi-rocha"), clube("celeiro", 35), 60.0, 4);

        ouvinte.processar(new TemporadaEncerrada(temporada(), Map.of(), FIM_DO_ANO));

        assertThat(pontosDe(vinculo)).isEqualTo(2);
    }

    @Test
    void deveElevarReputacaoQuandoSuperouAMeta() {
        var vinculo = vinculo(treinador("elias-dias"), clube("celeiro", 35), 60.0, 16);

        encerrarTemporadaCom(vinculo, 3);

        // (16 - 3) x 0,8 = 10,4, limitado ao teto de +10.
        assertThat(reputacaoDe(vinculo)).isEqualTo(REPUTACAO_INICIAL + 10);
    }

    @Test
    void deveDerrubarReputacaoQuandoTerminouAbaixoDaMeta() {
        var vinculo = vinculo(treinador("fabio-reis"), clube("gigante", 88), 60.0, 4);

        encerrarTemporadaCom(vinculo, 12);

        // (4 - 12) x 0,8 = -6,4, arredondado para -6.
        assertThat(reputacaoDe(vinculo)).isEqualTo(REPUTACAO_INICIAL - 6);
    }

    @Test
    void deveDerrubarReputacaoNaDemissaoSemOlharAClassificacao() {
        var vinculo = demitido(treinador("gil-nunes"), 4);

        // O clube terminou em 1º — mas com outro treinador.
        encerrarTemporadaCom(vinculo, 1);

        assertThat(reputacaoDe(vinculo)).isEqualTo(REPUTACAO_INICIAL - 5);
    }

    @Test
    void naoDeveMexerNaReputacaoQuandoAPosicaoFinalNaoVeioNoEvento() {
        var vinculo = vinculo(treinador("hugo-pires"), clube("celeiro", 35), 60.0, 4);

        ouvinte.processar(new TemporadaEncerrada(temporada(), Map.of(), FIM_DO_ANO));

        assertThat(reputacaoDe(vinculo)).isEqualTo(REPUTACAO_INICIAL);
    }

    @Test
    void deveEncerrarOVinculoAtivoAoFimDaTemporada() {
        var vinculo = vinculo(treinador("ivo-castro"), clube("celeiro", 35), 60.0, 8);

        encerrarTemporadaCom(vinculo, 4);

        assertThat(recarregar(vinculo).getFim()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(recarregar(vinculo).getMotivoFim()).isEqualTo(MotivoFim.FIM_DE_CONTRATO);
    }

    @Test
    void deveConsolidarAfinidadeDeTodoJogadorDoElencoAoEncerrarVinculo() {
        var treinador = treinador("joana-luz");
        var vinculo = vinculo(treinador, clube("celeiro", 35), 60.0, 8);
        var titular = jogador("titular");
        var reserva = jogador("reserva");
        elenco(vinculo, titular, StatusConfianca.INDISCUTIVEL, 80.0);
        elenco(vinculo, reserva, StatusConfianca.ROTACAO, 70.0);

        encerrarTemporadaCom(vinculo, 4);

        assertThat(afinidade(treinador, titular)).isGreaterThan(50.0);
        assertThat(afinidade(treinador, reserva)).isGreaterThan(50.0);
    }

    @Test
    void deveSerIdempotenteQuandoOFimDaTemporadaChegaDuasVezes() {
        var vinculo = vinculo(treinador("kaio-brito"), clube("celeiro", 35), 60.0, 8);
        var evento = new TemporadaEncerrada(temporada(), Map.of(clubeDe(vinculo), 4), FIM_DO_ANO);

        ouvinte.processar(evento);
        vinculos.flush();
        ouvinte.processar(evento);

        assertThat(pontosDe(vinculo)).isEqualTo(3);
        assertThat(reputacaoDe(vinculo)).isEqualTo(REPUTACAO_INICIAL + 3);
    }

    @Test
    void deveLiberarOTreinadorParaOMercadoDaTemporadaSeguinte() {
        var treinador = treinador("lia-moraes");
        var vinculo = vinculo(treinador, clube("celeiro", 35), 60.0, 8);

        encerrarTemporadaCom(vinculo, 4);
        vinculos.flush();

        assertThat(treinadores.buscarLivres()).extracting(Treinador::getSlug).contains("lia-moraes");
    }

    private void encerrarTemporadaCom(VinculoTreinador vinculo, int posicaoFinal) {
        ouvinte.processar(new TemporadaEncerrada(temporada(),
                Map.of(clubeDe(vinculo), posicaoFinal), FIM_DO_ANO));
    }

    private VinculoTreinador demitido(Treinador treinador, int metaPosicao) {
        var vinculo = vinculo(treinador, clube("gigante", 88), 8.0, metaPosicao);
        vinculo.encerrar(LocalDate.of(2026, 6, 30), MotivoFim.DEMISSAO);
        return vinculo;
    }

    private Long clubeDe(VinculoTreinador vinculo) {
        return recarregar(vinculo).getClubeId();
    }

    private int pontosDe(VinculoTreinador vinculo) {
        return recarregar(vinculo).getTreinador().getPontosDisponiveis();
    }

    private int reputacaoDe(VinculoTreinador vinculo) {
        return recarregar(vinculo).getTreinador().getReputacao();
    }

    private double afinidade(Treinador treinador, Long jogadorId) {
        return afinidades.findByTreinadorIdAndJogadorId(treinador.getId(), jogadorId)
                .orElseThrow()
                .getAfinidade();
    }
}

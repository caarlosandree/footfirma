package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.JogadorInsatisfeito;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.StatusConfianca;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.within;

@RecordApplicationEvents
class OuvinteDePartidaTest extends CenarioDeTreinador {

    @Autowired
    ApplicationEvents publicados;

    @Test
    void deveElevarMoralQuandoClubePequenoVenceGigante() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("ana-souza"), celeiro, 50.0, 16);

        ouvinte.processar(EventoFactory.vitoriaDoMandante(celeiro, 35, gigante, 88));

        assertThat(moralDe(vinculo)).isCloseTo(55.9, within(0.2));
    }

    @Test
    void deveDerrubarPoucoAMoralQuandoClubePequenoPerdeParaGigante() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("bia-lima"), celeiro, 50.0, 16);

        ouvinte.processar(EventoFactory.derrotaDoMandante(celeiro, 35, gigante, 88));

        assertThat(moralDe(vinculo)).isCloseTo(49.2, within(0.2));
    }

    @Test
    void deveAplicarAMoralDoVisitanteQuandoOClubeJogaFora() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("caio-melo"), celeiro, 50.0, 16);

        // O mandante é o gigante e ele venceu, então o visitante perdeu.
        ouvinte.processar(EventoFactory.vitoriaDoMandante(gigante, 88, celeiro, 35));

        assertThat(moralDe(vinculo)).isCloseTo(49.2, within(0.2));
    }

    @Test
    void deveDemitirQuandoMoralCaiAbaixoDoLimiarAposACarencia() {
        var gigante = clube("gigante", 88);
        var celeiro = clube("celeiro", 35);
        var vinculo = vinculo(treinador("davi-rocha"), gigante, 12.0, 1);
        comPartidasJogadas(vinculo, 20);

        ouvinte.processar(EventoFactory.derrotaDoMandante(gigante, 88, celeiro, 35));
        vinculos.flush();

        assertThat(recarregar(vinculo).getFim()).isNotNull();
        assertThat(recarregar(vinculo).getMotivoFim()).isEqualTo(MotivoFim.DEMISSAO);
    }

    @Test
    void naoDeveDemitirDentroDaCarenciaMesmoComMoralNoChao() {
        var gigante = clube("gigante", 88);
        var celeiro = clube("celeiro", 35);
        var vinculo = vinculo(treinador("elias-dias"), gigante, 5.0, 1);

        ouvinte.processar(EventoFactory.derrotaDoMandante(gigante, 88, celeiro, 35));

        assertThat(recarregar(vinculo).getFim()).isNull();
    }

    @Test
    void deveIgnorarPartidaDeClubeSemTreinadorVinculado() {
        var semTreinador = clube("sem-treinador", 50);
        var outro = clube("outro", 50);

        assertThatNoException().isThrownBy(() ->
                ouvinte.processar(EventoFactory.vitoriaDoMandante(semTreinador, 50, outro, 50)));
    }

    @Test
    void deveSerIdempotenteQuandoOMesmoEventoChegaDuasVezes() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("fabio-reis"), celeiro, 50.0, 16);
        var partida = EventoFactory.vitoriaDoMandante(celeiro, 35, gigante, 88);

        ouvinte.processar(partida);
        var aposAPrimeira = moralDe(vinculo);
        ouvinte.processar(partida);

        assertThat(moralDe(vinculo)).isEqualTo(aposAPrimeira);
    }

    @Test
    void deveContarUmaPartidaPorEventoAplicadoAoVinculo() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("gil-nunes"), celeiro, 50.0, 16);
        var partida = EventoFactory.vitoriaDoMandante(celeiro, 35, gigante, 88);

        ouvinte.processar(partida);
        ouvinte.processar(partida);

        assertThat(processados.countByVinculoId(vinculo.getId())).isEqualTo(1);
    }

    @Test
    void deveDerrubarAMoralDoIndiscutivelDeixadoNoBanco() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("hugo-pires"), celeiro, 50.0, 16);
        var reserva = jogador("reserva");
        var relacao = elenco(vinculo, reserva, StatusConfianca.INDISCUTIVEL, 50.0);

        ouvinte.processar(EventoFactory.com(
                EventoFactory.vitoriaDoMandante(celeiro, 35, gigante, 88), Map.of(reserva, 0)));

        assertThat(relacao.getMoral()).isCloseTo(46.8, within(0.1));
        assertThat(relacao.getMinutosAcumulados()).isZero();
    }

    @Test
    void devePremiarAPromessaQueJogouAPartidaInteira() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("ivo-castro"), celeiro, 50.0, 16);
        var garoto = jogador("garoto");
        var relacao = elenco(vinculo, garoto, StatusConfianca.PROMESSA, 50.0);

        ouvinte.processar(EventoFactory.com(
                EventoFactory.vitoriaDoMandante(celeiro, 35, gigante, 88), Map.of(garoto, 90)));

        assertThat(relacao.getMoral()).isCloseTo(52.0, within(0.1));
        assertThat(relacao.getMinutosAcumulados()).isEqualTo(90);
    }

    @Test
    void naoDeveMexerNaConfiancaQuandoOEventoNaoTrazMinutagem() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("joana-luz"), celeiro, 50.0, 16);
        var relacao = elenco(vinculo, jogador("titular"), StatusConfianca.INDISCUTIVEL, 50.0);

        ouvinte.processar(EventoFactory.vitoriaDoMandante(celeiro, 35, gigante, 88));

        assertThat(relacao.getMoral()).isEqualTo(50.0);
    }

    @Test
    void devePublicarJogadorInsatisfeitoQuandoAMoralCruzaOPiso() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("kaio-brito"), celeiro, 50.0, 16);
        var magoado = jogador("magoado");
        elenco(vinculo, magoado, StatusConfianca.INDISCUTIVEL, 26.0);

        ouvinte.processar(EventoFactory.com(
                EventoFactory.vitoriaDoMandante(celeiro, 35, gigante, 88), Map.of(magoado, 0)));

        assertThat(publicados.stream(JogadorInsatisfeito.class))
                .singleElement()
                .satisfies(evento -> assertThat(evento.jogadorId()).isEqualTo(magoado));
    }

    @Test
    void naoDeveRepetirJogadorInsatisfeitoEnquantoAMoralSegueNoChao() {
        var celeiro = clube("celeiro", 35);
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador("lia-moraes"), celeiro, 50.0, 16);
        var magoado = jogador("magoado");
        elenco(vinculo, magoado, StatusConfianca.INDISCUTIVEL, 26.0);
        var campanha = EventoFactory.campanha(celeiro, 35, gigante, 88,
                EventoFactory.campanhaMediana().subList(0, 3));

        campanha.forEach(partida ->
                ouvinte.processar(EventoFactory.com(partida, Map.of(magoado, 0))));

        assertThat(publicados.stream(JogadorInsatisfeito.class)).hasSize(1);
    }
}

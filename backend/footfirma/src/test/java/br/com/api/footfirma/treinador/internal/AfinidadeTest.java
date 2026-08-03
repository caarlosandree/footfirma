package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.Afinidade;
import br.com.api.footfirma.treinador.domain.StatusConfianca;
import br.com.api.footfirma.treinador.domain.Treinador;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * As duas camadas da relação com o jogador: a memória que atravessa clubes e o presente
 * que morre com o vínculo.
 *
 * <p>O efeito emergente — levar o pupilo junto para o clube seguinte — sai das duas
 * tabelas, sem regra especial. O último teste é ele.
 */
class AfinidadeTest extends CenarioDeBorda {

    @Autowired
    ConfiancaDoElenco confiancaDoElenco;

    // --- O motor, sem Spring e sem banco ---

    @Test
    void devePesarPoucoQuandoAPassagemFoiCurta() {
        assertThat(MotorDeAfinidade.consolidar(50.0, 90.0, 3)).isCloseTo(54.0, within(0.5));
    }

    @Test
    void devePesarMuitoQuandoAPassagemDurouTemporadas() {
        assertThat(MotorDeAfinidade.consolidar(50.0, 90.0, 114)).isCloseTo(78.0, within(0.5));
    }

    @Test
    void naoDeveApagarHistoricoAntigoNumUnicoAnoRuim() {
        assertThat(MotorDeAfinidade.consolidar(90.0, 10.0, 500)).isGreaterThan(30.0);
    }

    @Test
    void deveIgnorarOAcumuladoEUsarSoAPassagemAoPesar() {
        var passagemCurta = MotorDeAfinidade.consolidar(90.0, 10.0, 3);

        // Três jogos mal movem a memória, por mais longa que a carreira em comum já seja.
        assertThat(passagemCurta).isCloseTo(82.0, within(0.5));
    }

    // --- A semeadura da moral na chegada ---

    @Test
    void deveAdiantarMoralDoJogadorQueJaFoiPupilo() {
        var treinador = treinador("ana-souza");
        var jogador = jogador("pupilo");
        comAfinidade(treinador, jogador, 90.0);

        var relacao = confiancaDoElenco.registrar(
                vinculo(treinador, clube("celeiro", 35), 50.0, 16), jogador);

        assertThat(relacao.getMoral()).isCloseTo(74.0, within(0.5));
    }

    @Test
    void deveComecarEmCinquentaQuandoJogadorNuncaFoiDirigido() {
        var treinador = treinador("bia-lima");

        var relacao = confiancaDoElenco.registrar(
                vinculo(treinador, clube("celeiro", 35), 50.0, 16), jogador("estranho"));

        assertThat(relacao.getMoral()).isCloseTo(50.0, within(0.5));
    }

    @Test
    void devePenalizarMoralDoJogadorQueFoiQueimadoNoBanco() {
        var treinador = treinador("caio-melo");
        var jogador = jogador("queimado");
        comAfinidade(treinador, jogador, 20.0);

        var relacao = confiancaDoElenco.registrar(
                vinculo(treinador, clube("celeiro", 35), 50.0, 16), jogador);

        assertThat(relacao.getMoral()).isCloseTo(32.0, within(0.5));
    }

    // --- A consolidação ao encerrar ---

    @Test
    void deveConsolidarAAfinidadeQuandoOTreinadorEhDemitido() {
        var treinador = treinador("davi-rocha");
        var jogador = jogador("satisfeito");
        demitirAposPassagemCom(treinador, jogador, 80.0);

        assertThat(afinidadeDe(treinador, jogador).getAfinidade()).isCloseTo(60.5, within(0.1));
    }

    @Test
    void deveAcumularOsJogosDaPassagemNaMemoriaDoPar() {
        var treinador = treinador("elias-dias");
        var jogador = jogador("veterano");
        demitirAposPassagemCom(treinador, jogador, 80.0);

        // Vinte partidas de carência mais a que custou o emprego.
        assertThat(afinidadeDe(treinador, jogador).getJogosJuntos()).isEqualTo(21);
    }

    @Test
    void deveDerrubarAAfinidadeQuandoAPassagemTerminouMal() {
        var treinador = treinador("fabio-reis");
        var jogador = jogador("magoado");
        demitirAposPassagemCom(treinador, jogador, 10.0);

        assertThat(afinidadeDe(treinador, jogador).getAfinidade()).isLessThan(50.0);
    }

    // --- O efeito emergente ---

    @Test
    void deveLevarOPupiloAdiantadoParaOClubeSeguinte() {
        var treinador = treinador("gil-nunes");
        var jogador = jogador("pupilo");
        demitirAposPassagemCom(treinador, jogador, 80.0);

        var novoVinculo = vinculo(treinador, clube("outro-clube", 50), 50.0, 10);
        var relacao = confiancaDoElenco.registrar(novoVinculo, jogador);

        // Afinidade 60,5 vira moral 56,3 na chegada — nem reset, nem lua de mel completa.
        assertThat(relacao.getMoral()).isCloseTo(56.3, within(0.2));
    }

    /**
     * Uma passagem que termina em demissão: carência cumprida, moral do vínculo no chão e
     * o jogador saindo com a moral informada.
     */
    private void demitirAposPassagemCom(Treinador treinador, Long jogadorId, double moralFinal) {
        var gigante = clube("gigante", 88);
        var vinculo = vinculo(treinador, gigante, 12.0, 1);
        comPartidasJogadas(vinculo, 20);
        elenco(vinculo, jogadorId, StatusConfianca.IMPORTANTE, moralFinal);

        ouvinte.processar(EventoFactory.derrotaDoMandante(gigante, 88, clube("celeiro", 35), 35));
        vinculos.flush();

        assertThat(recarregar(vinculo).getFim()).isNotNull();
    }

    private void comAfinidade(Treinador treinador, Long jogadorId, double valor) {
        var afinidade = new Afinidade(treinador, jogadorId);
        afinidade.setAfinidade(valor);
        afinidades.save(afinidade);
    }

    private Afinidade afinidadeDe(Treinador treinador, Long jogadorId) {
        return afinidades.findByTreinadorIdAndJogadorId(treinador.getId(), jogadorId)
                .orElseThrow();
    }
}

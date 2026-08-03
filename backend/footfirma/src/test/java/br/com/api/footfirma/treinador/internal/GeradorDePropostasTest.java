package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.exception.PropostaIndisponivelException;
import br.com.api.footfirma.treinador.CenarioDeTreinador;
import br.com.api.footfirma.treinador.TreinadorFactory;
import br.com.api.footfirma.treinador.TreinadorService;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.Proposta;
import br.com.api.footfirma.treinador.domain.StatusProposta;
import br.com.api.footfirma.treinador.domain.TipoTreinador;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.internal.MercadoDeTreinadores.VagaAberta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * O mercado: quem serve para uma vaga, quando um clube maior tenta tirar alguém do
 * concorrente, e o que acontece quando a proposta é respondida.
 */
class GeradorDePropostasTest extends CenarioDeTreinador {

    private static final int META_DA_VAGA = 4;

    private static final OffsetDateTime DAQUI_A_UMA_SEMANA =
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(7);

    @Autowired
    MercadoDeTreinadores mercado;

    @Autowired
    TreinadorService treinadorService;

    // --- A escolha, sem Spring e sem banco ---

    @Test
    void deveAceitarCandidatoDentroDaFaixaDeVinteECinco() {
        var candidatos = GeradorDePropostas.candidatos(60, List.of(
                livre("ana", 40), livre("bia", 60), livre("caio", 80), livre("davi", 20)));

        assertThat(candidatos).hasSize(3);
    }

    @Test
    void devePreferirOCandidatoMaisReputadoDentroDaFaixa() {
        var candidatos = GeradorDePropostas.candidatos(60, List.of(
                livre("ana", 40), livre("caio", 80), livre("bia", 60)));

        assertThat(candidatos).first()
                .extracting(Treinador::getSlug).isEqualTo("caio");
    }

    @Test
    void deveAssediarQuandoClubeEhMaiorETreinadorVaiBem() {
        assertThat(GeradorDePropostas.deveAssediar(80, 60, 70.0, 70)).isTrue();
    }

    @Test
    void naoDeveAssediarQuandoMoralEstaAbaixoDeSessenta() {
        assertThat(GeradorDePropostas.deveAssediar(80, 60, 45.0, 70)).isFalse();
    }

    @Test
    void naoDeveAssediarQuandoClubeNaoEhSuficientementeMaior() {
        assertThat(GeradorDePropostas.deveAssediar(65, 60, 70.0, 70)).isFalse();
    }

    @Test
    void naoDeveAssediarQuandoTreinadorEhPequenoDemaisParaOClube() {
        assertThat(GeradorDePropostas.deveAssediar(90, 60, 70.0, 55)).isFalse();
    }

    // --- A vaga aberta ---

    @Test
    void deveOferecerAVagaAoTreinadorLivreCompativel() {
        treinadores.save(TreinadorFactory.comReputacao("ana-souza", paisBrasil(), 55));

        var proposta = mercado.abrirVaga(vagaEm(clube("celeiro", 50), 50));

        assertThat(proposta.getTreinador().getSlug()).isEqualTo("ana-souza");
        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.ABERTA);
    }

    @Test
    void deveGerarTreinadorDeIaQuandoNaoHaCandidatoParaOClube() {
        treinadores.save(TreinadorFactory.comReputacao("pequeno", paisBrasil(), 20));

        var proposta = mercado.abrirVaga(vagaEm(clube("gigante", 95), 95));

        assertThat(proposta.getTreinador().getTipo()).isEqualTo(TipoTreinador.IA);
        assertThat(proposta.getTreinador().getReputacao()).isEqualTo(95);
    }

    @Test
    void deveDarAsSeisSkillsAoTreinadorDeIaGerado() {
        var proposta = mercado.abrirVaga(vagaEm(clube("gigante", 95), 95));

        var detalhe = treinadorService
                .buscarPorSlug(proposta.getTreinador().getSlug(), temporada()).orElseThrow();
        assertThat(detalhe.skills()).hasSize(6);
        assertThat(detalhe.skills().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(20);
    }

    @Test
    void deveCongelarAReputacaoDoClubeNaProposta() {
        var proposta = mercado.abrirVaga(vagaEm(clube("celeiro", 35), 35));

        jdbcTemplate.update("update clube set reputacao = 90 where slug = 'celeiro'");

        assertThat(propostaRecarregada(proposta).getReputacaoClube()).isEqualTo(35);
    }

    // --- A resposta ---

    @Test
    void deveEncerrarVinculoAnteriorAoAceitarProposta() {
        var treinador = treinador("ana-souza");
        var antigo = vinculo(treinador, clube("celeiro", 35), 70.0, 16);
        var proposta = propostaPara(treinador, clube("gigante", 88), 88);

        treinadorService.aceitarProposta(proposta.getId());

        assertThat(recarregar(antigo).getMotivoFim()).isEqualTo(MotivoFim.ACEITOU_PROPOSTA);
    }

    @Test
    void deveDeixarOTreinadorDirigindoApenasONovoClube() {
        var treinador = treinador("bia-lima");
        vinculo(treinador, clube("celeiro", 35), 70.0, 16);
        var gigante = clube("gigante", 88);

        var novo = treinadorService.aceitarProposta(propostaPara(treinador, gigante, 88).getId());
        vinculos.flush();

        assertThat(novo.clubeId()).isEqualTo(gigante);
        assertThat(vinculos.findByTreinadorIdAndFimIsNull(treinador.getId()))
                .map(v -> v.getId()).contains(novo.id());
    }

    @Test
    void deveSemearAMoralPelaReputacaoCongeladaNaProposta() {
        var treinador = treinadores.save(
                TreinadorFactory.comReputacao("caio-melo", paisBrasil(), 85));

        var novo = treinadorService.aceitarProposta(
                propostaPara(treinador, clube("medio", 40), 40).getId());

        // clamp(50 + (85 - 40) x 0,4 ; 25 ; 85) = 68 — contratação de prestígio.
        assertThat(novo.moral()).isCloseTo(68.0, within(0.1));
    }

    @Test
    void deveHerdarAMetaQueViajouNaProposta() {
        var treinador = treinador("davi-rocha");

        var novo = treinadorService.aceitarProposta(
                propostaPara(treinador, clube("gigante", 88), 88).getId());

        assertThat(novo.metaPosicao()).isEqualTo(META_DA_VAGA);
    }

    @Test
    void deveMarcarAPropostaComoAceita() {
        var treinador = treinador("elias-dias");
        var proposta = propostaPara(treinador, clube("gigante", 88), 88);

        treinadorService.aceitarProposta(proposta.getId());

        assertThat(propostaRecarregada(proposta).getStatus()).isEqualTo(StatusProposta.ACEITA);
        assertThat(propostaRecarregada(proposta).getRespondidaEm()).isNotNull();
    }

    @Test
    void deveMarcarAPropostaComoRecusada() {
        var treinador = treinador("fabio-reis");
        var proposta = propostaPara(treinador, clube("gigante", 88), 88);

        treinadorService.recusarProposta(proposta.getId());

        assertThat(propostaRecarregada(proposta).getStatus()).isEqualTo(StatusProposta.RECUSADA);
    }

    @Test
    void deveRejeitarPropostaJaRespondida() {
        var treinador = treinador("gil-nunes");
        var proposta = propostaPara(treinador, clube("gigante", 88), 88);
        treinadorService.recusarProposta(proposta.getId());

        assertThatThrownBy(() -> treinadorService.aceitarProposta(proposta.getId()))
                .isInstanceOf(PropostaIndisponivelException.class);
    }

    @Test
    void deveRejeitarPropostaVencida() {
        var treinador = treinador("hugo-pires");
        var vencida = propostas.save(new Proposta(clube("gigante", 88), treinador, temporada(),
                META_DA_VAGA, 88, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)));

        assertThatThrownBy(() -> treinadorService.aceitarProposta(vencida.getId()))
                .isInstanceOf(PropostaIndisponivelException.class);
    }

    @Test
    void naoDeveMostrarPropostaVencidaNaCaixaDeEntrada() {
        var treinador = treinador("joana-luz");
        propostas.save(new Proposta(clube("gigante", 88), treinador, temporada(),
                META_DA_VAGA, 88, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)));

        assertThat(treinadorService.listarPropostasAbertas(treinador.getId())).isEmpty();
    }

    @Test
    void deveListarApenasAsPropostasAindaEmJogo() {
        var treinador = treinador("ivo-castro");
        propostaPara(treinador, clube("gigante", 88), 88);
        var recusada = propostaPara(treinador, clube("outro", 80), 80);
        treinadorService.recusarProposta(recusada.getId());

        assertThat(treinadorService.listarPropostasAbertas(treinador.getId())).hasSize(1);
    }

    private static Treinador livre(String slug, int reputacao) {
        return TreinadorFactory.comReputacao(slug, 1L, reputacao);
    }

    private VagaAberta vagaEm(Long clubeId, int reputacao) {
        return new VagaAberta(clubeId, reputacao, paisBrasil(), temporada(),
                META_DA_VAGA, DAQUI_A_UMA_SEMANA);
    }

    private Proposta propostaPara(Treinador treinador, Long clubeId, int reputacaoDoClube) {
        return propostas.save(new Proposta(clubeId, treinador, temporada(),
                META_DA_VAGA, reputacaoDoClube, DAQUI_A_UMA_SEMANA));
    }

    private Proposta propostaRecarregada(Proposta proposta) {
        return propostas.findById(proposta.getId()).orElseThrow();
    }
}

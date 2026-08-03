package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.evento.PartidaEncerrada;
import br.com.api.footfirma.treinador.TreinadorDemitido;
import br.com.api.footfirma.treinador.TreinadorSobPressao;
import br.com.api.footfirma.treinador.domain.EventoProcessado;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.Resultado;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.EventoProcessadoRepository;
import br.com.api.footfirma.treinador.repository.VinculoTreinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A borda do módulo: traduz o que aconteceu em campo para as duas morais que o módulo
 * guarda — a do treinador com o clube e a de cada jogador com o treinador.
 *
 * <p>Nada aqui decide regra. Os motores são funções puras e recebem apenas o fato; esta
 * classe existe para achar o vínculo, garantir que o evento entre uma vez só e delegar as
 * consequências a quem é dono delas.
 */
@Component
@RequiredArgsConstructor
class OuvinteDePartida {

    private final VinculoTreinadorRepository vinculos;
    private final EventoProcessadoRepository processados;
    private final ConfiancaDoElenco elenco;
    private final EncerradorDeVinculo encerrador;
    private final ApplicationEventPublisher eventos;

    @ApplicationModuleListener
    void em(PartidaEncerrada evento) {
        processar(evento);
    }

    /**
     * O que o listener faz, com o proxy fora do caminho.
     *
     * <p>{@code @ApplicationModuleListener} é {@code @Async}, e o Modulith liga o
     * {@code @EnableAsync}: chamar {@link #em} direto devolveria antes de qualquer escrita
     * acontecer. Os testes entram por aqui.
     */
    void processar(PartidaEncerrada evento) {
        var doMandante = Resultado.de(evento.golsMandante(), evento.golsVisitante());

        aplicarNoClube(evento, evento.clubeMandanteId(), evento.reputacaoMandante(),
                evento.reputacaoVisitante(), doMandante);
        aplicarNoClube(evento, evento.clubeVisitanteId(), evento.reputacaoVisitante(),
                evento.reputacaoMandante(), doMandante.invertido());
    }

    private void aplicarNoClube(PartidaEncerrada evento, long clubeId, int minhaReputacao,
                                int reputacaoAdversario, Resultado resultado) {
        var vinculo = vinculos.findByClubeIdAndFimIsNull(clubeId).orElse(null);
        if (vinculo == null || !registrar(vinculo, evento)) {
            return;
        }

        // posicaoAtual recebe metaPosicao porque classificação não existe até a partida
        // existir — é o que mantém fator_meta em 1,0, como o spec previu. Quando a tabela
        // existir, só esta linha muda.
        var fato = new FatoDeResultado(minhaReputacao, reputacaoAdversario, resultado,
                vinculo.getMetaPosicao(), vinculo.getMetaPosicao());
        vinculo.setMoral(MotorDeMoral.aplicar(vinculo.getMoral(), fato));

        elenco.aplicarMinutagem(vinculo, evento);
        avaliarDemissao(vinculo, evento, minhaReputacao);
    }

    /** @return {@code false} quando este evento já tinha sido aplicado a este vínculo. */
    private boolean registrar(VinculoTreinador vinculo, PartidaEncerrada evento) {
        if (processados.existsByVinculoIdAndChaveEvento(vinculo.getId(), evento.chave())) {
            return false;
        }
        processados.save(new EventoProcessado(vinculo, evento.chave()));
        return true;
    }

    private void avaliarDemissao(VinculoTreinador vinculo, PartidaEncerrada evento,
                                 int reputacaoDoClube) {
        var partidas = (int) processados.countByVinculoId(vinculo.getId());

        switch (PoliticaDeDemissao.avaliar(vinculo.getMoral(), reputacaoDoClube, partidas)) {
            case SEGURO -> {
            }
            case SOB_PRESSAO -> eventos.publishEvent(new TreinadorSobPressao(
                    vinculo.getTreinador().getId(), vinculo.getClubeId(),
                    vinculo.getId(), vinculo.getMoral()));
            case DEMITIDO -> demitir(vinculo, evento);
        }
    }

    private void demitir(VinculoTreinador vinculo, PartidaEncerrada evento) {
        // A data sai do evento, não do relógio: reprocessar precisa reproduzir a mesma
        // carreira, e System.now() faria a data do fim depender de quando se reprocessou.
        encerrador.encerrar(vinculo, LocalDate.ofInstant(evento.ocorridoEm(), ZoneOffset.UTC),
                MotivoFim.DEMISSAO);

        eventos.publishEvent(new TreinadorDemitido(vinculo.getTreinador().getId(),
                vinculo.getClubeId(), vinculo.getId(), vinculo.getMoral()));
    }
}

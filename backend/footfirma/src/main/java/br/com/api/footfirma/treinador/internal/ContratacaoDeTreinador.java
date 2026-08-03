package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.exception.PropostaIndisponivelException;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import br.com.api.footfirma.treinador.VinculoIniciado;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.Proposta;
import br.com.api.footfirma.treinador.domain.StatusProposta;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.PropostaRepository;
import br.com.api.footfirma.treinador.repository.VinculoTreinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * O que acontece quando uma proposta é respondida.
 *
 * <p>Aceitar é uma troca, não uma adição: o vínculo atual precisa terminar antes que o
 * novo comece, senão os índices parciais únicos rejeitam a gravação — e é justamente
 * isso que impede alguém de terminar dirigindo dois clubes.
 */
@Component
@RequiredArgsConstructor
class ContratacaoDeTreinador {

    private final VinculoTreinadorRepository vinculos;
    private final PropostaRepository propostas;
    private final EncerradorDeVinculo encerrador;
    private final ApplicationEventPublisher eventos;

    VinculoTreinador aceitar(long propostaId) {
        var agora = OffsetDateTime.now(ZoneOffset.UTC);
        var proposta = emJogo(propostaId, agora);
        var treinador = proposta.getTreinador();
        var hoje = agora.toLocalDate();

        vinculos.findByTreinadorIdAndFimIsNull(treinador.getId())
                .ifPresent(atual -> encerrador.encerrar(atual, hoje, MotivoFim.ACEITOU_PROPOSTA));
        // O vínculo antigo precisa estar gravado antes do novo: uq_vinculo_treinador_ativo
        // enxerga o estado do banco, não o da sessão.
        vinculos.flush();

        var novo = vinculos.save(new VinculoTreinador(treinador, proposta.getClubeId(),
                proposta.getTemporadaId(), hoje,
                CalculadoraDeExpectativa.moralInicialDoVinculo(
                        treinador.getReputacao(), proposta.getReputacaoClube()),
                proposta.getMetaPosicao()));

        proposta.responder(StatusProposta.ACEITA, agora);
        eventos.publishEvent(new VinculoIniciado(treinador.getId(), novo.getClubeId(),
                novo.getId(), novo.getTemporadaId()));

        return novo;
    }

    Proposta recusar(long propostaId) {
        var agora = OffsetDateTime.now(ZoneOffset.UTC);
        var proposta = emJogo(propostaId, agora);

        proposta.responder(StatusProposta.RECUSADA, agora);
        return proposta;
    }

    /**
     * A proposta vencida é recusada sem ser marcada.
     *
     * <p>Marcá-la {@code EXPIRADA} aqui seria escrita inútil: a exceção sai da mesma
     * transação que a gravaria, o rollback desfaz a marcação e a proposta continua
     * {@code ABERTA} no banco. Quem esconde a vencida de quem a recebeu é a consulta da
     * caixa de entrada, que filtra por prazo — o estado {@code EXPIRADA} fica reservado
     * para a varredura que ainda não existe.
     */
    private Proposta emJogo(long propostaId, OffsetDateTime agora) {
        var proposta = propostas.findById(propostaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "proposta " + propostaId + " não existe"));

        if (!proposta.estaAberta()) {
            throw new PropostaIndisponivelException(
                    "proposta " + propostaId + " já foi respondida");
        }
        if (proposta.getExpiraEm().isBefore(agora)) {
            throw new PropostaIndisponivelException("proposta " + propostaId + " expirou em "
                    + proposta.getExpiraEm());
        }
        return proposta;
    }
}

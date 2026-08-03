package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.VinculoEncerrado;
import br.com.api.footfirma.treinador.domain.Afinidade;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.AfinidadeRepository;
import br.com.api.footfirma.treinador.repository.EventoProcessadoRepository;
import br.com.api.footfirma.treinador.repository.TreinadorJogadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * O único caminho pelo qual uma passagem termina.
 *
 * <p>Existe centralizado porque encerrar não é só gravar {@code fim}: é também consolidar
 * a afinidade com cada jogador do elenco e abrir a vaga para o mercado. Demissão, pedido
 * de demissão, fim de contrato e proposta aceita são motivos diferentes para o mesmo
 * conjunto de consequências — duplicá-lo seria garantir que um dos caminhos esquecesse
 * uma delas.
 */
@Component
@RequiredArgsConstructor
class EncerradorDeVinculo {

    private final TreinadorJogadorRepository confiancas;
    private final AfinidadeRepository afinidades;
    private final EventoProcessadoRepository processados;
    private final ApplicationEventPublisher eventos;

    void encerrar(VinculoTreinador vinculo, LocalDate quando, MotivoFim motivo) {
        vinculo.encerrar(quando, motivo);
        consolidarAfinidades(vinculo, quando);

        eventos.publishEvent(new VinculoEncerrado(vinculo.getTreinador().getId(),
                vinculo.getClubeId(), vinculo.getId(), vinculo.getTemporadaId()));
    }

    /**
     * A moral do presente vira memória. É aqui, e só aqui, que a passagem deixa marca
     * capaz de atravessar a próxima contratação.
     */
    private void consolidarAfinidades(VinculoTreinador vinculo, LocalDate quando) {
        var jogosNaPassagem = (int) processados.countByVinculoId(vinculo.getId());
        var treinador = vinculo.getTreinador();

        for (var relacao : confiancas.findByVinculoId(vinculo.getId())) {
            var afinidade = afinidades
                    .findByTreinadorIdAndJogadorId(treinador.getId(), relacao.getJogadorId())
                    .orElseGet(() -> new Afinidade(treinador, relacao.getJogadorId()));

            afinidade.setAfinidade(MotorDeAfinidade.consolidar(
                    afinidade.getAfinidade(), relacao.getMoral(), jogosNaPassagem));
            // A coluna acumula a carreira em comum; o peso acima usa só esta passagem.
            afinidade.setJogosJuntos(afinidade.getJogosJuntos() + jogosNaPassagem);
            afinidade.setAtualizadoEm(quando.atStartOfDay().atOffset(ZoneOffset.UTC));

            afinidades.save(afinidade);
        }
    }
}

package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.evento.TemporadaEncerrada;
import br.com.api.footfirma.treinador.domain.EventoProcessado;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.EventoProcessadoRepository;
import br.com.api.footfirma.treinador.repository.VinculoTreinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * O fim do ano: cada treinador recebe o que a temporada deixou e o vínculo do ano acaba.
 *
 * <p>O vínculo é do ano — {@code temporada_id} está na tabela, a meta foi congelada para
 * aquela temporada e as skills são versionadas por ela. Encerrar aqui é o que faz o
 * mercado ter o que preencher na virada, em vez de o mundo carregar vínculos eternos.
 */
@Component
@RequiredArgsConstructor
class OuvinteDeTemporada {

    private final VinculoTreinadorRepository vinculos;
    private final EventoProcessadoRepository processados;
    private final EncerradorDeVinculo encerrador;

    @ApplicationModuleListener
    void em(TemporadaEncerrada evento) {
        processar(evento);
    }

    /** Ponto de entrada síncrono, pelo mesmo motivo de {@code OuvinteDePartida.processar}. */
    void processar(TemporadaEncerrada evento) {
        ultimoVinculoPorTreinador(evento.temporadaId())
                .forEach(vinculo -> fecharContas(vinculo, evento));
    }

    /**
     * Quem trocou de clube no meio do ano tem dois vínculos na temporada, e só o último
     * conta: o desfecho do ano é onde a pessoa terminou. Os anteriores já foram encerrados
     * e já consolidaram afinidade quando a troca aconteceu.
     */
    private Collection<VinculoTreinador> ultimoVinculoPorTreinador(long temporadaId) {
        return vinculos.findByTemporadaId(temporadaId).stream()
                .collect(Collectors.toMap(
                        vinculo -> vinculo.getTreinador().getId(),
                        Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparing(VinculoTreinador::getInicio))))
                .values();
    }

    private void fecharContas(VinculoTreinador vinculo, TemporadaEncerrada evento) {
        if (jaProcessado(vinculo, evento)) {
            return;
        }

        // Lido antes de encerrar: depois do encerramento todo vínculo tem fim, e não
        // haveria como distinguir quem foi demitido de quem chegou ao fim do contrato.
        var fato = new FatoDeTemporada(foiDemitido(vinculo), vinculo.getMetaPosicao(),
                posicaoFinal(evento.posicaoFinalPorClube(), vinculo.getClubeId()));
        var treinador = vinculo.getTreinador();

        treinador.setPontosDisponiveis(treinador.getPontosDisponiveis()
                + MotorDeCarreira.pontos(MotorDeCarreira.desfecho(fato)));
        treinador.setReputacao(MotorDeCarreira.aplicarReputacao(treinador.getReputacao(), fato));

        if (vinculo.estaAtivo()) {
            encerrador.encerrar(vinculo, LocalDate.ofInstant(evento.ocorridoEm(), ZoneOffset.UTC),
                    MotivoFim.FIM_DE_CONTRATO);
        }

        // Depois do encerramento, e não antes: a tabela de eventos processados também é a
        // contagem de partidas do vínculo, e o encerramento a usa para pesar a afinidade.
        // Marcar aqui evita que o próprio fim de ano conte como uma partida jogada.
        marcarProcessado(vinculo, evento);
    }

    private static boolean foiDemitido(VinculoTreinador vinculo) {
        return vinculo.getMotivoFim() == MotivoFim.DEMISSAO;
    }

    private static OptionalInt posicaoFinal(Map<Long, Integer> classificacao, Long clubeId) {
        var posicao = classificacao.get(clubeId);
        return posicao == null ? OptionalInt.empty() : OptionalInt.of(posicao);
    }

    private boolean jaProcessado(VinculoTreinador vinculo, TemporadaEncerrada evento) {
        return processados.existsByVinculoIdAndChaveEvento(vinculo.getId(), evento.chave());
    }

    private void marcarProcessado(VinculoTreinador vinculo, TemporadaEncerrada evento) {
        processados.save(new EventoProcessado(vinculo, evento.chave()));
    }
}

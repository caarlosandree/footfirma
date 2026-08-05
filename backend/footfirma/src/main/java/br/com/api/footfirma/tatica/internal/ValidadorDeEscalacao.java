package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.shared.exception.EscalacaoInvalidaException;
import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.TitularEscalado;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * As invariantes que nenhum {@code check} de linha alcança, porque nenhuma enxerga as
 * outras linhas do agregado.
 *
 * <p>Não há checagem de "exatamente um goleiro entre os titulares": ela é consequência,
 * não regra. A posição de cada slot vem da formação, toda formação tem exatamente um
 * slot GOL — travado por {@code CatalogoDeFormacaoTest} — e os slots são validados aqui
 * como exatamente {@code 1..11}. Logo exatamente um titular ocupa o gol, sempre.
 * Escalar um zagueiro ali continua permitido, e continua custando caro pela aptidão.
 *
 * <p>A invariante que de fato pode ser violada é a do banco: um time sem goleiro
 * reserva.
 */
final class ValidadorDeEscalacao {

    private ValidadorDeEscalacao() {
    }

    static void validar(NovoPlano plano, Set<Long> elencoDoClube, long posicaoDeGoleiro,
                        Map<Long, Long> principalPorJogador) {
        if (plano.titulares().size() != ConstantesDeTatica.TITULARES) {
            throw new EscalacaoInvalidaException(
                    "%d titulares, esperados %d".formatted(
                            plano.titulares().size(), ConstantesDeTatica.TITULARES));
        }

        var slots = plano.titulares().stream()
                .map(TitularEscalado::slotOrdem)
                .collect(Collectors.toSet());
        if (slots.size() != ConstantesDeTatica.TITULARES) {
            throw new EscalacaoInvalidaException("dois titulares no mesmo slot");
        }
        if (slots.stream().anyMatch(slot -> slot < 1 || slot > ConstantesDeTatica.TITULARES)) {
            throw new EscalacaoInvalidaException(
                    "slot fora da faixa 1-%d".formatted(ConstantesDeTatica.TITULARES));
        }

        var escalados = new HashSet<Long>();
        for (var titular : plano.titulares()) {
            if (!escalados.add(titular.jogadorId())) {
                throw new EscalacaoInvalidaException(
                        "jogador %d repetido na escalação".formatted(titular.jogadorId()));
            }
        }
        for (var reserva : plano.banco()) {
            if (!escalados.add(reserva)) {
                throw new EscalacaoInvalidaException(
                        "jogador %d repetido na escalação".formatted(reserva));
            }
        }

        for (var jogadorId : escalados) {
            if (!elencoDoClube.contains(jogadorId)) {
                throw new EscalacaoInvalidaException(
                        "jogador %d não tem vínculo com o clube nesta temporada"
                                .formatted(jogadorId));
            }
        }

        if (plano.banco().size() < ConstantesDeTatica.BANCO_MINIMO
                || plano.banco().size() > ConstantesDeTatica.BANCO_MAXIMO) {
            throw new EscalacaoInvalidaException(
                    "banco com %d jogadores, esperado entre %d e %d".formatted(
                            plano.banco().size(), ConstantesDeTatica.BANCO_MINIMO,
                            ConstantesDeTatica.BANCO_MAXIMO));
        }

        var goleiroNoBanco = plano.banco().stream().anyMatch(reserva ->
                posicaoDeGoleiro == principalPorJogador.getOrDefault(reserva, -1L));
        if (!goleiroNoBanco) {
            throw new EscalacaoInvalidaException("banco sem goleiro reserva");
        }

        if (plano.capitaoId() != null) {
            var ehTitular = plano.titulares().stream()
                    .anyMatch(escalado -> escalado.jogadorId() == plano.capitaoId());
            if (!ehTitular) {
                throw new EscalacaoInvalidaException(
                        "capitão %d não está entre os titulares".formatted(plano.capitaoId()));
            }
        }
    }
}

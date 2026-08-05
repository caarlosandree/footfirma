package br.com.api.footfirma.calendario.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Escolhe o dia de um jogo dentro da janela da rodada, respeitando o descanso dos dois
 * clubes contra tudo que já está na agenda deles na temporada.
 *
 * <p>Recebe as agendas já carregadas, e não o repositório: é o que mantém a regra pura e
 * testável sem banco, como as fábricas de {@code mundo}.
 *
 * <p>Guloso — escolhe dia a dia e não retrocede. Erra onde uma busca completa acertaria;
 * com uma competição por clube o erro é raro, e o teste de descanso pega a violação.
 */
final class AlocadorDeDatas {

    private AlocadorDeDatas() {
    }

    static LocalDate alocar(LocalDate janelaInicio, LocalDate janelaFim,
                            List<DayOfWeek> ordemDeDias,
                            List<LocalDate> agendaMandante, List<LocalDate> agendaVisitante,
                            int descansoMinimo) {
        for (var dia : ordemDeDias) {
            for (var data = janelaInicio; !data.isAfter(janelaFim); data = data.plusDays(1)) {
                if (data.getDayOfWeek() == dia
                        && descansa(data, agendaMandante, descansoMinimo)
                        && descansa(data, agendaVisitante, descansoMinimo)) {
                    return data;
                }
            }
        }

        // Leque esgotado: empurra para o primeiro dia posterior que sirva. A rodada não
        // desliza — deslizá-la propagaria em cascata por todas as seguintes e tornaria o
        // calendário instável a cada competição acrescentada.
        for (var i = 1; i <= ConstantesDeCalendario.DESLOCAMENTO_MAXIMO_EM_DIAS; i++) {
            var data = janelaFim.plusDays(i);
            if (descansa(data, agendaMandante, descansoMinimo)
                    && descansa(data, agendaVisitante, descansoMinimo)) {
                return data;
            }
        }

        throw new IllegalStateException(
                "Nenhuma data respeita o descanso de %d dias entre %s e %s mais %d"
                        .formatted(descansoMinimo, janelaInicio, janelaFim,
                                ConstantesDeCalendario.DESLOCAMENTO_MAXIMO_EM_DIAS));
    }

    private static boolean descansa(LocalDate candidata, List<LocalDate> agenda, int minimo) {
        return agenda.stream().allMatch(
                ocupada -> Math.abs(ChronoUnit.DAYS.between(ocupada, candidata)) >= minimo);
    }
}

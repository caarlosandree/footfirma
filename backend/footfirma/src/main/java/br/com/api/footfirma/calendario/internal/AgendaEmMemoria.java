package br.com.api.footfirma.calendario.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * As datas já ocupadas por cada clube, acumuladas durante a geração.
 *
 * <p>Existe para não consultar o banco uma vez por jogo alocado — seriam 760 idas numa
 * temporada de duas ligas. É semeada com o que já está gravado e cresce a cada jogo novo,
 * o que também é o que faz a segunda competição enxergar a primeira.
 */
final class AgendaEmMemoria {

    private final Map<Long, List<LocalDate>> porClube = new HashMap<>();

    void semear(long clubeId, List<LocalDate> datas) {
        porClube.computeIfAbsent(clubeId, id -> new ArrayList<>()).addAll(datas);
    }

    List<LocalDate> de(long clubeId) {
        return porClube.getOrDefault(clubeId, List.of());
    }

    void ocupar(long clubeId, LocalDate data) {
        porClube.computeIfAbsent(clubeId, id -> new ArrayList<>()).add(data);
    }
}

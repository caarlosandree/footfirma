package br.com.api.footfirma.calendario.dto;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Os dias em que uma competição joga, por tipo de rodada, e o descanso que ela respeita.
 *
 * <p>É entrada de geração, não dado persistido — como a semente. Persistir exigiria
 * migration em {@code competicao} ou tabela nova para um dado que hoje tem dois valores.
 * Se um dia a API precisar responder "quando esse campeonato costuma jogar", a resposta
 * sai dos jogos gerados, que são o fato.
 *
 * @param pesos peso relativo de cada dia dentro do leque do tipo. Não precisa somar 100.
 */
public record PerfilDeCalendario(
        Map<TipoDeRodada, Map<DayOfWeek, Integer>> pesos,
        int descansoMinimoEmDias) {
}

package br.com.api.footfirma.mundo.internal;

import br.com.api.footfirma.calendario.dto.PerfilDeCalendario;
import br.com.api.footfirma.calendario.dto.TipoDeRodada;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Em que dias cada divisão joga.
 *
 * <p>Os pesos vieram da distribuição real da tabela da CBF — nas rodadas 23 a 27 da Série
 * A, 16 jogos no domingo, 13 no sábado e nenhum na sexta —, mas são julgamento e não
 * medição. Como {@code CatalogoDeArquetipos}, é aqui que se rebalanceia; os testes travam
 * a forma (nada na sexta, meio de semana só em quarta e quinta, descanso respeitado) e não
 * os percentuais.
 *
 * <p>As duas divisões dividem o mesmo leque de dias e se distinguem pelo peso: a primeira
 * concentra no domingo, a segunda vaza mais para segunda e terça. Não são conjuntos
 * disjuntos — na vida real as duas jogam sábado.
 */
final class PerfisDeLiga {

    private PerfisDeLiga() {
    }

    private static final Map<DayOfWeek, Integer> MEIO_DE_SEMANA = Map.of(
            DayOfWeek.WEDNESDAY, 60, DayOfWeek.THURSDAY, 40);

    static final PerfilDeCalendario PRIMEIRA = new PerfilDeCalendario(Map.of(
            TipoDeRodada.FIM_DE_SEMANA, Map.of(
                    DayOfWeek.SUNDAY, 45, DayOfWeek.SATURDAY, 40, DayOfWeek.MONDAY, 15),
            TipoDeRodada.MEIO_DE_SEMANA, MEIO_DE_SEMANA), 3);

    static final PerfilDeCalendario SEGUNDA = new PerfilDeCalendario(Map.of(
            TipoDeRodada.FIM_DE_SEMANA, Map.of(
                    DayOfWeek.SATURDAY, 40, DayOfWeek.SUNDAY, 30,
                    DayOfWeek.MONDAY, 20, DayOfWeek.TUESDAY, 10),
            TipoDeRodada.MEIO_DE_SEMANA, MEIO_DE_SEMANA), 3);
}

package br.com.api.footfirma.calendario;

import br.com.api.footfirma.calendario.dto.ConfrontoDetalhe;
import br.com.api.footfirma.calendario.dto.EdicaoParaGerar;
import br.com.api.footfirma.calendario.dto.JogoAgendado;
import br.com.api.footfirma.calendario.dto.RelatorioDeCalendario;
import br.com.api.footfirma.calendario.dto.ResultadoDoJogo;
import br.com.api.footfirma.calendario.dto.RodadaDetalhe;

import java.util.List;
import java.util.Optional;

/**
 * A única porta pública do módulo, e o único caminho de escrita.
 *
 * <p>As invariantes do calendário são de agregado — "ninguém joga duas vezes na mesma
 * rodada", "todo clube descansa N dias" —, e {@code check} não enxerga outras linhas.
 */
public interface CalendarioService {

    /**
     * Gera o calendário das edições, na ordem da precedência de cada uma.
     *
     * <p>Recebe a lista inteira, e não uma edição por chamada, porque a ordem decide o
     * calendário: quem gera primeiro ocupa os melhores dias. Ordenar aqui dentro tira essa
     * dependência da sequência das linhas de quem chama.
     *
     * @throws br.com.api.footfirma.shared.exception.CalendarioInvalidoException
     *         se alguma fase não puder ser gerada
     */
    RelatorioDeCalendario gerarTemporada(long temporadaId, long semente,
                                         List<EdicaoParaGerar> edicoes);

    /**
     * Grava o placar, encerra o jogo e, em fase eliminatória, resolve o confronto.
     *
     * @throws br.com.api.footfirma.shared.exception.ResultadoInvalidoException
     *         se o desempate não for permitido pela fase, o jogo já estiver encerrado ou
     *         seus clubes ainda não estiverem definidos
     */
    void registrarResultado(long jogoId, ResultadoDoJogo resultado);

    /** O jogo com as regras de desempate que valem para ele. */
    Optional<JogoAgendado> buscarJogo(long jogoId);

    /**
     * Os jogos do clube na temporada, em qualquer competição, por data.
     *
     * <p>Não é só leitura de API: é o que o próprio gerador consulta para verificar
     * descanso, e é o que torna a agenda do clube um conceito de primeira classe em vez de
     * uma query escondida dentro do alocador.
     */
    List<JogoAgendado> listarAgendaDoClube(long clubeId, long temporadaId);

    List<RodadaDetalhe> listarRodadas(long edicaoId);

    List<ConfrontoDetalhe> listarChaveamento(long faseId);
}

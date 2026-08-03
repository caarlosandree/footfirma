package br.com.api.footfirma.treinador;

import br.com.api.footfirma.treinador.dto.DistribuicaoDeSkills;
import br.com.api.footfirma.treinador.dto.NovoTreinador;
import br.com.api.footfirma.treinador.dto.TreinadorDetalhe;
import br.com.api.footfirma.treinador.dto.TreinadorResumo;

import java.util.Optional;

/**
 * A única porta pública do módulo.
 *
 * <p>Ela é o único caminho de escrita de propósito: a soma das seis skills numa temporada
 * é invariante de agregado, e {@code check} não enxerga outras linhas. Quem escrever
 * {@code treinador_skill} por fora produz um treinador com 40 pontos distribuídos e o
 * banco aceita.
 */
public interface TreinadorService {

    /**
     * @param temporadaId de qual ano vêm as skills do detalhe — não existe "as skills do
     *                    treinador" sem dizer de quando.
     */
    Optional<TreinadorDetalhe> buscarPorSlug(String slug, long temporadaId);

    /** Distribui os 20 pontos iniciais: as seis skills, cada uma entre 1 e 10. */
    TreinadorResumo criar(NovoTreinador dados);

    /**
     * Aplica os pontos ganhos ao fim da temporada sobre as skills já gravadas.
     *
     * <p>Diferente de {@link #criar}, aqui a distribuição é <b>incremental</b>: só as
     * skills que recebem algum entram no mapa, a soma precisa gastar exatamente os pontos
     * disponíveis e nenhuma pode passar do teto de 10.
     */
    TreinadorDetalhe distribuirPontos(long treinadorId, long temporadaId,
                                      DistribuicaoDeSkills ganhos);
}

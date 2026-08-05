package br.com.api.footfirma.treinador;

import br.com.api.footfirma.treinador.dto.DistribuicaoDeSkills;
import br.com.api.footfirma.treinador.dto.NovoTreinador;
import br.com.api.footfirma.treinador.dto.PropostaResumo;
import br.com.api.footfirma.treinador.dto.TreinadorDetalhe;
import br.com.api.footfirma.treinador.dto.TreinadorResumo;
import br.com.api.footfirma.treinador.dto.VinculoResumo;

import java.util.List;
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

    /** A caixa de entrada do treinador: o que ainda está em jogo, do mais recente ao mais antigo. */
    List<PropostaResumo> listarPropostasAbertas(long treinadorId);

    /**
     * Encerra o vínculo atual, se houver, e abre o novo com a moral semeada pela reputação
     * congelada na proposta.
     */
    VinculoResumo aceitarProposta(long propostaId);

    PropostaResumo recusarProposta(long propostaId);

    /**
     * O perfil de quem dirige o clube na temporada. Vazio quando o clube está sem
     * treinador — situação normal entre a demissão e a próxima contratação.
     *
     * <p>Devolve {@link PerfilDeTreinador}, e não {@code TreinadorDetalhe}, porque
     * {@code treinador.dto} é subpacote sem {@code @NamedInterface}: um consumidor de
     * fora não pode referenciá-lo sem reprovar no teste de modularidade.
     */
    Optional<PerfilDeTreinador> buscarPerfilDoClube(long clubeId, long temporadaId);
}

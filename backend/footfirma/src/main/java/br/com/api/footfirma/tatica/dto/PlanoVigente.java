package br.com.api.footfirma.tatica.dto;

import java.util.List;

/**
 * O contrato que o módulo {@code partida} vai consumir.
 *
 * <p>Desenhado antes do consumidor existir — risco 2 do spec. Acrescentar campo aqui não
 * quebra quem já lê, então quem se adapta é a partida.
 *
 * @param titulares em ordem de slot.
 * @param banco     em ordem de banco.
 */
public record PlanoVigente(long planoId, long clubeId, long temporadaId, int versao,
                           OrigemDoPlano origem, long formacaoId, String formacaoCodigo,
                           Mentalidade mentalidade, Ritmo ritmo, LinhaDefensiva linhaDefensiva,
                           Pressao pressao, Largura largura, Long capitaoId,
                           List<Escalado> titulares, List<Escalado> banco) {
}

package br.com.api.footfirma.jogador.dto;

import java.time.LocalDate;

/**
 * Entrada de ingestão. {@code chaveNatural} e {@code semente} chegam calculadas pelo
 * importador: são invariantes do sistema, não dados da fonte, e derivá-las aqui
 * duplicaria a regra de identidade no dia em que um pipeline externo precisar dela.
 */
public record DadosDeJogador(String slug, String chaveNatural, long semente,
                             String nomeCompleto, String nomeExibicao, LocalDate dataNascimento,
                             Long paisId, Long segundaNacionalidadeId,
                             Integer alturaCm, Integer pesoKg, String pePreferido,
                             Long posicaoPrincipalId, String origem) {
}

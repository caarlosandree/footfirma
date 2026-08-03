package br.com.api.footfirma.jogador.dto;

import java.time.LocalDate;

/**
 * Entrada de ingestão. {@code chaveNatural} e {@code semente} chegam calculadas por
 * quem gera o jogador: são invariantes do sistema, não dados da fonte, e derivá-las
 * aqui duplicaria a regra de identidade em quem também precisa dela.
 */
public record DadosDeJogador(String slug, String chaveNatural, long semente,
                             String nomeCompleto, String nomeExibicao, LocalDate dataNascimento,
                             Long paisId, Long segundaNacionalidadeId,
                             Integer alturaCm, Integer pesoKg, String pePreferido,
                             Long posicaoPrincipalId, String origem) {
}

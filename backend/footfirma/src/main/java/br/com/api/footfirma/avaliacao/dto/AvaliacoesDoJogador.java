package br.com.api.footfirma.avaliacao.dto;

import java.util.List;

/** Plural porque devolve as nove posições, não um número só. */
public record AvaliacoesDoJogador(String slug, String temporada, List<AvaliacaoDePosicao> avaliacoes) {
}

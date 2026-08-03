package br.com.api.footfirma.mundo.internal;

import java.time.LocalDate;

/** Um jogador antes de virar linha. O gerador o traduz em {@code DadosDeJogador}. */
record JogadorGerado(String slug, String chaveNatural, long semente,
                     String nomeCompleto, String nomeExibicao, LocalDate dataNascimento,
                     String posicao, String pePreferido, int alturaCm, int pesoKg,
                     int alvoOverall, int potencialBase, int potencialVariacao,
                     String categoria, Integer numeroCamisa) {
}

package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AtributoAvaliavelTest {

    // Cada skill recebe um valor distinto para que uma extração trocada apareça
    // como número errado, e não como coincidência.
    private static final AtributosJogador ATRIBUTOS = new AtributosJogador(
            1, 2, 3, 4, 5,
            6, 7, 8, 9,
            10, 11, 12, 13,
            14, 15,
            16, 17, 18,
            99, "IMPORTADO");

    @Test
    void deveDeclararDezoitoAtributos() {
        assertThat(AtributoAvaliavel.values()).hasSize(18);
    }

    @Test
    void deveExtrairCadaAtributoDoRecordCorrespondente() {
        assertThat(AtributoAvaliavel.RITMO.extrair(ATRIBUTOS)).isEqualTo(1);
        assertThat(AtributoAvaliavel.AGILIDADE.extrair(ATRIBUTOS)).isEqualTo(5);
        assertThat(AtributoAvaliavel.FRIEZA.extrair(ATRIBUTOS)).isEqualTo(9);
        assertThat(AtributoAvaliavel.PENALTI.extrair(ATRIBUTOS)).isEqualTo(13);
        assertThat(AtributoAvaliavel.MARCACAO.extrair(ATRIBUTOS)).isEqualTo(15);
        assertThat(AtributoAvaliavel.GOL_MANEJO.extrair(ATRIBUTOS)).isEqualTo(18);
    }

    @Test
    void naoDeveExtrairPotencialNemFonteDoAtributo() {
        var extraidos = Arrays.stream(AtributoAvaliavel.values())
                .map(atributo -> atributo.extrair(ATRIBUTOS))
                .toList();

        assertThat(extraidos).containsExactlyInAnyOrder(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
    }
}

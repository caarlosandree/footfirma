package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ChaveNaturalTest {

    private static final LocalDate NASCIMENTO = LocalDate.of(1998, 7, 21);

    @Test
    void deveIgnorarAcentosAoNormalizar() {
        var comAcento = ChaveNatural.de("Antônio Gonçalves", NASCIMENTO, "BRA");
        var semAcento = ChaveNatural.de("Antonio Goncalves", NASCIMENTO, "BRA");

        assertThat(comAcento).isEqualTo(semAcento);
    }

    @Test
    void deveIgnorarCaixaEEspacoDuplicado() {
        var bagunçado = ChaveNatural.de("  JOÃO   DA SILVA ", NASCIMENTO, "BRA");
        var limpo = ChaveNatural.de("joão da silva", NASCIMENTO, "BRA");

        assertThat(bagunçado).isEqualTo(limpo);
    }

    @Test
    void deveDiferenciarHomonimosComDataDeNascimentoDiferente() {
        var maisVelho = ChaveNatural.de("Carlos Souza", LocalDate.of(1990, 1, 1), "BRA");
        var maisNovo = ChaveNatural.de("Carlos Souza", LocalDate.of(2001, 1, 1), "BRA");

        assertThat(maisVelho).isNotEqualTo(maisNovo);
    }

    @Test
    void deveDiferenciarHomonimosDeNacionalidadeDiferente() {
        var brasileiro = ChaveNatural.de("Carlos Souza", NASCIMENTO, "BRA");
        var portugues = ChaveNatural.de("Carlos Souza", NASCIMENTO, "PRT");

        assertThat(brasileiro).isNotEqualTo(portugues);
    }

    @Test
    void deveProduzirSempreAMesmaSementeParaAMesmaChave() {
        var chave = ChaveNatural.de("Rafael Andrade", NASCIMENTO, "BRA");

        assertThat(ChaveNatural.semente(chave)).isEqualTo(ChaveNatural.semente(chave));
    }

    @Test
    void deveProduzirSementeNaoNegativa() {
        for (var indice = 0; indice < 500; indice++) {
            var chave = ChaveNatural.de("Jogador Numero " + indice, NASCIMENTO, "BRA");

            assertThat(ChaveNatural.semente(chave)).isNotNegative();
        }
    }

    @Test
    void deveProduzirSementesDistintasParaMilChavesDistintas() {
        var sementes = new HashSet<Long>();

        for (var indice = 0; indice < 1_000; indice++) {
            sementes.add(ChaveNatural.semente(
                    ChaveNatural.de("Jogador Sintetico " + indice, NASCIMENTO, "BRA")));
        }

        assertThat(sementes).hasSize(1_000);
    }

    @Test
    void deveCaberNoLimiteDaColunaComONomeMaisLongoPermitido() {
        var nomeDe160Caracteres = "a".repeat(160);

        var chave = ChaveNatural.de(nomeDe160Caracteres, NASCIMENTO, "BRA");

        assertThat(chave.length()).isBetween(5, 220);
    }

    @Test
    void deveNormalizarPontuacaoParaEspaco() {
        var comPontuacao = ChaveNatural.normalizar("D'Alessandro-Filho Jr.");

        assertThat(comPontuacao).isEqualTo("d alessandro filho jr");
    }
}

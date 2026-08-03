package br.com.api.footfirma.mundo.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Monta os 40 clubes casando a lista curada de nomes com a distribuição de
 * arquétipos. Os 20 primeiros nomes vão para a primeira divisão — fixo, não
 * sorteado: mundo em que a divisão de um clube muda a cada regeração é mundo que
 * ninguém consegue depurar.
 */
final class FabricaDeClubes {

    private static final List<String> CORES = List.of(
            "#C62828", "#1565C0", "#2E7D32", "#F9A825", "#6A1B9A",
            "#00838F", "#EF6C00", "#37474F", "#AD1457", "#4E342E");

    private FabricaDeClubes() {
    }

    static List<ClubeGerado> gerar(SplittableRandom aleatorio) {
        var clubes = new ArrayList<ClubeGerado>(40);
        clubes.addAll(gerarDivisao(1, 0, aleatorio));
        clubes.addAll(gerarDivisao(2, 20, aleatorio));
        return List.copyOf(clubes);
    }

    private static List<ClubeGerado> gerarDivisao(int divisao, int deslocamento,
                                                  SplittableRandom aleatorio) {
        var arquetipos = CatalogoDeArquetipos.distribuicao(divisao);
        var clubes = new ArrayList<ClubeGerado>(20);
        for (var i = 0; i < 20; i++) {
            var nome = GeradorDeNomes.CLUBES.get(deslocamento + i);
            var arquetipo = arquetipos.get(i);
            var primeira = CORES.get(aleatorio.nextInt(CORES.size()));
            var segunda = CORES.get(aleatorio.nextInt(CORES.size()));
            clubes.add(new ClubeGerado(
                    GeradorDeNomes.slug(nome.nomeCurto()),
                    nome.nomeOficial(), nome.nomeCurto(), nome.apelido(),
                    aleatorio.nextInt(1902, 1979),
                    nome.cidade(), nome.uf(),
                    "Arena " + nome.nomeCurto(),
                    aleatorio.nextInt(12_000, 68_000),
                    aleatorio.nextInt(1955, 2019),
                    primeira, primeira.equals(segunda) ? "#FFFFFF" : segunda,
                    arquetipo.sortearReputacao(aleatorio),
                    arquetipo.sortearFinancas(aleatorio),
                    arquetipo.sortearBase(aleatorio),
                    divisao));
        }
        return clubes;
    }
}

package br.com.api.footfirma.mundo.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Monta os 38 jogadores de um clube: 26 profissionais e 12 na base. A qualidade do
 * elenco vem de reputação e dinheiro; a da base, só da formação — é o descasamento
 * que põe joia em time pobre.
 */
final class FabricaDeElenco {

    private static final int ANO_DA_TEMPORADA = 2026;
    private static final String ISO_PAIS = "BRA";

    /** 26 vagas cobrindo as nove posições com reserva real em cada setor. */
    private static final List<String> POSICOES_PROFISSIONAIS = List.of(
            "GOL", "GOL", "GOL",
            "ZAG", "ZAG", "ZAG", "ZAG", "ZAG",
            "LTD", "LTD", "LTE", "LTE",
            "VOL", "VOL", "VOL", "VOL",
            "MEC", "MEC", "MEC", "MEA", "MEA",
            "PTA", "PTA", "PTA", "ATA", "ATA");

    private static final List<String> POSICOES_DA_BASE = List.of(
            "GOL", "ZAG", "ZAG", "LTD", "LTE", "VOL", "VOL", "MEC", "MEC", "MEA", "PTA", "ATA");

    private static final List<String> PES = List.of("DIREITO", "ESQUERDO", "AMBIDESTRO");

    private FabricaDeElenco() {
    }

    static List<JogadorGerado> gerar(ClubeGerado clube, SplittableRandom aleatorio,
                                     Set<String> chavesUsadas) {
        var jogadores = new ArrayList<JogadorGerado>(38);
        var papeisProfissionais = expandir(false);
        var papeisDaBase = expandir(true);

        for (var i = 0; i < POSICOES_PROFISSIONAIS.size(); i++) {
            jogadores.add(criar(clube, papeisProfissionais.get(i), POSICOES_PROFISSIONAIS.get(i),
                    i + 1, aleatorio, chavesUsadas));
        }
        for (var i = 0; i < POSICOES_DA_BASE.size(); i++) {
            jogadores.add(criar(clube, papeisDaBase.get(i), POSICOES_DA_BASE.get(i),
                    null, aleatorio, chavesUsadas));
        }
        return List.copyOf(jogadores);
    }

    private static List<PapelNoElenco> expandir(boolean base) {
        var papeis = new ArrayList<PapelNoElenco>();
        for (var papel : PapelNoElenco.values()) {
            if (papel.daBase() == base) {
                for (var i = 0; i < papel.quantidade(); i++) {
                    papeis.add(papel);
                }
            }
        }
        return papeis;
    }

    private static JogadorGerado criar(ClubeGerado clube, PapelNoElenco papel, String posicao,
                                       Integer camisa, SplittableRandom aleatorio,
                                       Set<String> chavesUsadas) {
        var referencia = papel.daBase() ? clube.qualidadeBase() : clube.nivelElenco();
        var alvo = limitar(referencia + entre(aleatorio, papel.deltaMinimo(), papel.deltaMaximo()));
        var idade = entre(aleatorio, papel.idadeMinima(), papel.idadeMaxima());
        var nascimento = LocalDate.of(ANO_DA_TEMPORADA - idade,
                entre(aleatorio, 1, 12), entre(aleatorio, 1, 28));

        var nome = sortearNomeInedito(aleatorio, nascimento, chavesUsadas);
        var chaveNatural = ChaveDeJogador.de(nome, nascimento, ISO_PAIS);
        chavesUsadas.add(chaveNatural);

        return new JogadorGerado(
                GeradorDeNomes.slug(nome) + "-" + Integer.toHexString(chaveNatural.hashCode() & 0xFFFFFF),
                chaveNatural,
                ChaveDeJogador.semente(chaveNatural),
                nome,
                nomeDeExibicao(nome),
                nascimento,
                posicao,
                PES.get(aleatorio.nextInt(PES.size())),
                entre(aleatorio, 165, 197),
                entre(aleatorio, 62, 92),
                alvo,
                potencial(papel, alvo, idade, aleatorio),
                entre(aleatorio, 3, 8),
                papel.categoria(),
                camisa);
    }

    /**
     * O espaço entre overall e potencial encolhe com a idade: aos 32 o jogador já é
     * o que vai ser. É o que faz "jovem promissor" significar alguma coisa.
     */
    private static int potencial(PapelNoElenco papel, int alvo, int idade,
                                 SplittableRandom aleatorio) {
        if (papel.daBase()) {
            return limitar(Math.max(alvo,
                    entre(aleatorio, papel.potencialMinimo(), papel.potencialMaximo())));
        }
        var folgaMaxima = Math.max(0, 32 - idade);
        return limitar(alvo + entre(aleatorio, 0, folgaMaxima));
    }

    private static String sortearNomeInedito(SplittableRandom aleatorio, LocalDate nascimento,
                                             Set<String> chavesUsadas) {
        // Chave natural duplicada viola a unicidade da tabela jogador e derrubaria a
        // carga inteira. Re-sortear é mais barato que descobrir isso em produção.
        for (var tentativa = 0; tentativa < 100; tentativa++) {
            var nome = sortearNome(aleatorio);
            if (!chavesUsadas.contains(ChaveDeJogador.de(nome, nascimento, ISO_PAIS))) {
                return nome;
            }
        }
        throw new IllegalStateException("Pool de nomes esgotado para " + nascimento);
    }

    private static String sortearNome(SplittableRandom aleatorio) {
        var prenome = GeradorDeNomes.PRENOMES.get(aleatorio.nextInt(GeradorDeNomes.PRENOMES.size()));
        var primeiro = GeradorDeNomes.SOBRENOMES.get(aleatorio.nextInt(GeradorDeNomes.SOBRENOMES.size()));
        var segundo = GeradorDeNomes.SOBRENOMES.get(aleatorio.nextInt(GeradorDeNomes.SOBRENOMES.size()));
        return primeiro.equals(segundo)
                ? prenome + " " + primeiro
                : prenome + " " + primeiro + " " + segundo;
    }

    private static String nomeDeExibicao(String nomeCompleto) {
        var partes = nomeCompleto.split(" ");
        return partes[0] + " " + partes[partes.length - 1];
    }

    private static int entre(SplittableRandom aleatorio, int minimo, int maximo) {
        return minimo >= maximo ? minimo : aleatorio.nextInt(minimo, maximo + 1);
    }

    private static int limitar(int valor) {
        return Math.clamp(valor, 25, 95);
    }
}

package br.com.api.footfirma.importacao.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Emite o dataset de fixtures. Determinístico: mesma semente, mesmos arquivos.
 *
 * <p>Nomes de clube e jogador são inventados — o dataset é versionado no repositório
 * e não pode carregar dado licenciado.
 *
 * <p>Roda por {@code ./gradlew gerarFixtures}, nunca pela suíte de testes.
 */
public final class GeradorDeFixtures {

    private static final long SEMENTE = 20260803L;
    private static final String GERADO_EM = "2026-08-03";
    private static final String DATASET_VERSAO = "fixtures-v1";
    private static final int JOGADORES_POR_CLUBE = 22;
    private static final String COLETA_2025 = "2025-01-15T10:00:00Z";
    private static final String COLETA_2026 = "2026-01-15T10:00:00Z";

    private static final List<String> SKILLS = List.of(
            "ritmo", "forca", "folego", "salto", "agilidade",
            "passe", "drible", "cruzamento", "frieza",
            "finalizacao", "cabeceio", "falta", "penalti",
            "desarme", "marcacao",
            "gol_reflexo", "gol_posicionamento", "gol_manejo");

    private static final List<String> PRENOMES = List.of(
            "Adriano", "Alisson", "Anderson", "Bruno", "Caio", "Danilo", "Diego", "Douglas",
            "Eduardo", "Emerson", "Fabrício", "Felipe", "Gabriel", "Gustavo", "Henrique",
            "Igor", "Ítalo", "Kleber", "Leandro", "Lucas", "Marcelo", "Mateus",
            "Murilo", "Nathan", "Otávio", "Patrick", "Rafael", "Renan", "Ricardo",
            "Rodrigo", "Samuel", "Thiago", "Vinícius", "Wallace", "Wesley", "Yuri");

    private static final List<String> SOBRENOMES = List.of(
            "Albuquerque", "Andrade", "Aragão", "Azevedo", "Bezerra", "Braga", "Cardoso",
            "Cavalcanti", "Correia", "Cunha", "Dantas", "Duarte", "Esteves", "Farias",
            "Fonseca", "Freitas", "Furtado", "Guimarães", "Lacerda", "Leite",
            "Macedo", "Magalhães", "Marinho", "Medeiros", "Meireles",
            "Moraes", "Nogueira", "Pacheco", "Peixoto", "Pontes", "Queiroz",
            "Rabelo", "Ramalho", "Rezende", "Sampaio", "Siqueira", "Tavares",
            "Valadares", "Vasconcelos", "Xavier");

    private static final List<String> ELENCO = List.of(
            "GOL", "GOL", "GOL", "ZAG", "ZAG", "ZAG", "ZAG", "LTD", "LTD", "LTE", "LTE",
            "VOL", "VOL", "VOL", "MEC", "MEC", "MEA", "MEA", "PTA", "PTA", "ATA", "ATA");

    /** Posição secundária plausível para cada principal. */
    private static final Map<String, String> POSICAO_SECUNDARIA = Map.of(
            "GOL", "GOL", "ZAG", "VOL", "LTD", "PTA", "LTE", "PTA",
            "VOL", "MEC", "MEC", "MEA", "MEA", "PTA", "PTA", "ATA", "ATA", "PTA");

    private record ClubeFicticio(String slug, String nomeCurto, String nomeOficial, String uf,
                                 String cidade, String estadio, int reputacao, int qualidadeBase,
                                 String corPrimaria, String corSecundaria) {
    }

    private static final List<ClubeFicticio> CLUBES = List.of(
            new ClubeFicticio("atletico-serrano", "Serrano", "Atlético Clube Serrano", "MG",
                    "Juiz de Fora", "Arena da Serra", 78, 72, "#1B4D3E", "#FFFFFF"),
            new ClubeFicticio("guarani-portuario", "Portuário", "Guarani Portuário Futebol Clube", "SP",
                    "Santos", "Estádio das Docas", 74, 68, "#0A2D6E", "#F2C230"),
            new ClubeFicticio("sociedade-ipanema", "Ipanema", "Sociedade Esportiva Ipanema", "RJ",
                    "Niterói", "Estádio Beira-Mar", 71, 65, "#7B1E3A", "#FFFFFF"),
            new ClubeFicticio("esporte-clube-varzea", "Várzea", "Esporte Clube Várzea", "RS",
                    "Pelotas", "Estádio Campo Novo", 68, 70, "#0F5132", "#111111"),
            new ClubeFicticio("nacional-do-cerrado", "Cerrado", "Nacional do Cerrado", "GO",
                    "Anápolis", "Arena do Planalto", 64, 58, "#B33A00", "#FFFFFF"),
            new ClubeFicticio("uniao-litoranea", "Litorânea", "União Litorânea Futebol Clube", "SC",
                    "Itajaí", "Estádio Marítimo", 61, 62, "#00557F", "#9FD8EF"),
            new ClubeFicticio("real-sertanejo", "Sertanejo", "Real Sertanejo Esporte Clube", "BA",
                    "Feira de Santana", "Arena do Sertão", 57, 55, "#4B2E83", "#F5F5F5"),
            new ClubeFicticio("avante-fluvial", "Fluvial", "Avante Fluvial Clube", "PA",
                    "Santarém", "Estádio Rio Grande", 52, 50, "#006B54", "#EAEAEA"));

    /** Ajuste por posição, um por skill, na ordem de {@link #SKILLS}. */
    private static final Map<String, int[]> AJUSTE_POR_POSICAO = Map.of(
            "GOL", new int[]{-15, 0, -6, 2, 4, -10, -18, -20, 0, -25, -18, -12, -8, -20, -20, 14, 14, 12},
            "ZAG", new int[]{-6, 8, 0, 6, -4, 0, -14, -12, 0, -18, 10, -8, -8, 10, 10, -35, -35, -35},
            "LTD", new int[]{8, -2, 8, 0, 4, 2, 2, 8, -2, -10, -6, -2, -6, 2, 2, -35, -35, -35},
            "LTE", new int[]{8, -2, 8, 0, 4, 2, 2, 8, -2, -10, -6, -2, -6, 2, 2, -35, -35, -35},
            "VOL", new int[]{-2, 4, 6, 0, 0, 4, -4, -4, 2, -10, 0, 0, -4, 8, 8, -35, -35, -35},
            "MEC", new int[]{-2, -2, 4, -4, 4, 10, 6, 4, 6, -6, -8, 6, 2, -4, -4, -35, -35, -35},
            "MEA", new int[]{2, -4, 2, -4, 6, 8, 8, 4, 8, 4, -6, 6, 4, -12, -12, -35, -35, -35},
            "PTA", new int[]{10, -4, 4, -2, 6, 2, 10, 8, 2, 2, -6, 0, 0, -14, -14, -35, -35, -35},
            "ATA", new int[]{4, 4, 0, 4, 2, -2, 2, -8, 6, 12, 8, -2, 8, -16, -16, -35, -35, -35});

    private static final List<String> CARACTERISTICAS_DE_LINHA = List.of(
            "DRIBLADOR", "VOLEIO", "BICICLETA", "CHUTE_DE_LONGE", "CHUTE_COLOCADO",
            "ESPECIALISTA_FALTA", "ESPECIALISTA_PENALTI", "ARMADOR", "PASSE_LONGO",
            "DESARME_LIMPO", "MARCADOR_AGRESSIVO", "CABECEIO_AEREO", "LIDER");

    private static final class JogadorGerado {
        private String slug;
        private String nomeCompleto;
        private String nomeExibicao;
        private LocalDate nascimento;
        private int idade;
        private String posicao;
        private String pePreferido;
        private int alturaCm;
        private int pesoKg;
        private int indiceClube;
        private int numeroCamisa;
        private int[] atributos2025;
        private int[] atributos2026;
        private int potencialBase;
        private int potencialVariacao;
        private int[] ocultos;
        private List<String> caracteristicas;
    }

    private GeradorDeFixtures() {
    }

    public static void main(String[] argumentos) throws IOException {
        if (argumentos.length != 1) {
            throw new IllegalArgumentException("Uso: GeradorDeFixtures <diretorio-de-saida>");
        }
        var diretorio = Path.of(argumentos[0]);
        Files.createDirectories(diretorio);

        var jogadores = gerarJogadores();
        var arquivos = new LinkedHashMap<String, List<String>>();
        arquivos.put("temporada.csv", temporadas());
        arquivos.put("estadio.csv", estadios());
        arquivos.put("clube.csv", clubes());
        arquivos.put("clube_alias.csv", aliases());
        arquivos.put("competicao.csv", competicoes());
        arquivos.put("edicao.csv", edicoes());
        arquivos.put("fase.csv", fases());
        arquivos.put("edicao_participante.csv", participantes());
        arquivos.put("regra_classificacao.csv", regras());
        arquivos.put("jogador.csv", linhasDeJogador(jogadores));
        arquivos.put("jogador_posicao.csv", linhasDePosicao(jogadores));
        arquivos.put("jogador_atributo.csv", linhasDeAtributo(jogadores));
        arquivos.put("jogador_atributo_oculto.csv", linhasDeOcultos(jogadores));
        arquivos.put("jogador_caracteristica.csv", linhasDeCaracteristica(jogadores));
        arquivos.put("jogador_vinculo.csv", linhasDeVinculo(jogadores));

        for (var entrada : arquivos.entrySet()) {
            escrever(diretorio, entrada.getKey(), entrada.getValue());
        }
        escreverManifesto(diretorio, arquivos);

        System.out.println("Fixtures geradas em " + diretorio.toAbsolutePath());
        System.out.println("Jogadores: " + jogadores.size());
    }

    // ---------- catálogo ----------

    private static List<String> temporadas() {
        return List.of("label,ano_inicio,ano_fim", "2025,2025,2025", "2026,2026,2026");
    }

    private static List<String> estadios() {
        var linhas = new ArrayList<String>();
        linhas.add("chave,nome,cidade,uf,capacidade,ano_inauguracao");
        for (var clube : CLUBES) {
            linhas.add(juntar(clube.slug(), clube.estadio(), clube.cidade(), clube.uf(),
                    String.valueOf(20000 + clube.reputacao() * 400),
                    String.valueOf(1950 + clube.reputacao() % 30)));
        }
        return linhas;
    }

    private static List<String> clubes() {
        var linhas = new ArrayList<String>();
        linhas.add("slug,nome_oficial,nome_curto,apelido,ano_fundacao,iso_pais,uf,estadio_chave,"
                + "cor_primaria,cor_secundaria,reputacao,qualidade_base,uf_base");
        for (var clube : CLUBES) {
            linhas.add(juntar(clube.slug(), clube.nomeOficial(), clube.nomeCurto(), "",
                    String.valueOf(1900 + clube.reputacao() % 25), "BRA", clube.uf(), clube.slug(),
                    clube.corPrimaria(), clube.corSecundaria(),
                    String.valueOf(clube.reputacao()), String.valueOf(clube.qualidadeBase()),
                    clube.uf()));
        }
        return linhas;
    }

    private static List<String> aliases() {
        var linhas = new ArrayList<String>();
        linhas.add("clube_slug,alias,fonte");
        for (var clube : CLUBES) {
            linhas.add(juntar(clube.slug(), clube.nomeCurto(), "MANUAL"));
            linhas.add(juntar(clube.slug(), clube.nomeOficial(), "MANUAL"));
        }
        return linhas;
    }

    private static List<String> competicoes() {
        return List.of(
                "slug,nome,iso_pais,tipo,nivel,genero",
                "serie-ouro,Campeonato Nacional Série Ouro,BRA,LIGA,1,MASCULINO",
                "serie-prata,Campeonato Nacional Série Prata,BRA,LIGA,2,MASCULINO",
                "copa-nacional,Copa Nacional,BRA,COPA,,MASCULINO");
    }

    private static List<String> edicoes() {
        return List.of(
                "competicao_slug,temporada,nome,data_inicio,data_fim",
                "serie-ouro,2025,Série Ouro 2025,2025-04-12,2025-12-07",
                "serie-ouro,2026,Série Ouro 2026,2026-04-11,2026-12-06",
                "copa-nacional,2026,Copa Nacional 2026,2026-03-04,2026-09-23");
    }

    private static List<String> fases() {
        return List.of(
                "competicao_slug,temporada,ordem,nome,tipo,jogos_por_confronto,"
                        + "tem_gol_fora,tem_prorrogacao,tem_penaltis",
                "serie-ouro,2025,1,Turno e returno,PONTOS_CORRIDOS,2,false,false,false",
                "serie-ouro,2026,1,Turno e returno,PONTOS_CORRIDOS,2,false,false,false",
                "copa-nacional,2026,1,Quartas de final,ELIMINATORIA,2,false,true,true",
                "copa-nacional,2026,2,Semifinal,ELIMINATORIA,2,false,true,true",
                "copa-nacional,2026,3,Final,ELIMINATORIA,1,false,true,true");
    }

    private static List<String> participantes() {
        // 2026 usa uma permutação fixa para que a tabela não repita a de 2025.
        var posicoesDe2026 = new int[]{2, 1, 4, 3, 6, 5, 8, 7};
        var linhas = new ArrayList<String>();
        linhas.add("competicao_slug,temporada,clube_slug,posicao_final");
        for (var indice = 0; indice < CLUBES.size(); indice++) {
            linhas.add(juntar("serie-ouro", "2025", CLUBES.get(indice).slug(),
                    String.valueOf(indice + 1)));
        }
        for (var indice = 0; indice < CLUBES.size(); indice++) {
            linhas.add(juntar("serie-ouro", "2026", CLUBES.get(indice).slug(),
                    String.valueOf(posicoesDe2026[indice])));
        }
        for (var clube : CLUBES) {
            linhas.add(juntar("copa-nacional", "2026", clube.slug(), ""));
        }
        return linhas;
    }

    private static List<String> regras() {
        var linhas = new ArrayList<String>();
        linhas.add("competicao_slug,temporada,posicao_inicio,posicao_fim,tipo,competicao_destino_slug");
        for (var temporada : List.of("2025", "2026")) {
            linhas.add(juntar("serie-ouro", temporada, "1", "4", "LIBERTADORES_GRUPOS", ""));
            linhas.add(juntar("serie-ouro", temporada, "5", "6", "SULAMERICANA", ""));
            linhas.add(juntar("serie-ouro", temporada, "7", "8", "REBAIXAMENTO", "serie-prata"));
        }
        return linhas;
    }

    // ---------- jogadores ----------

    private static List<JogadorGerado> gerarJogadores() {
        var random = new Random(SEMENTE);
        var slugsUsados = new HashSet<String>();
        var jogadores = new ArrayList<JogadorGerado>();

        for (var indiceClube = 0; indiceClube < CLUBES.size(); indiceClube++) {
            var clube = CLUBES.get(indiceClube);
            for (var vaga = 0; vaga < JOGADORES_POR_CLUBE; vaga++) {
                var jogador = new JogadorGerado();
                jogador.posicao = ELENCO.get(vaga);
                jogador.indiceClube = indiceClube;
                jogador.numeroCamisa = vaga + 1;
                jogador.nomeCompleto = nomeInedito(random, slugsUsados);
                jogador.nomeExibicao = primeiroEUltimo(jogador.nomeCompleto);
                jogador.slug = slugDe(jogador.nomeCompleto, slugsUsados);
                jogador.idade = 17 + random.nextInt(22);
                jogador.nascimento = LocalDate.of(2026 - jogador.idade,
                        1 + random.nextInt(12), 1 + random.nextInt(28));
                jogador.pePreferido = pePreferido(jogador.posicao, random);
                jogador.alturaCm = "GOL".equals(jogador.posicao)
                        ? 183 + random.nextInt(16)
                        : 165 + random.nextInt(31);
                jogador.pesoKg = jogador.alturaCm - 100 + random.nextInt(9) - 4;

                var base = 40 + clube.reputacao() / 2;
                jogador.atributos2025 = atributos(jogador.posicao, base, jogador.idade - 1, random);
                jogador.atributos2026 = evoluir(jogador.atributos2025, jogador.idade);
                var overall = mediaRelevante(jogador.atributos2026, jogador.posicao);
                jogador.potencialBase = Math.min(94, overall + Math.max(0, 30 - jogador.idade));
                jogador.potencialVariacao = 2 + random.nextInt(7);
                jogador.ocultos = ocultos(random);
                jogador.caracteristicas = caracteristicas(jogador.posicao, random);

                jogadores.add(jogador);
            }
        }
        return jogadores;
    }

    private static String nomeInedito(Random random, Set<String> jaUsados) {
        while (true) {
            var prenome = PRENOMES.get(random.nextInt(PRENOMES.size()));
            var primeiro = SOBRENOMES.get(random.nextInt(SOBRENOMES.size()));
            var segundo = SOBRENOMES.get(random.nextInt(SOBRENOMES.size()));
            if (primeiro.equals(segundo)) {
                continue;
            }
            var nome = prenome + " " + primeiro + " " + segundo;
            if (!jaUsados.contains(ChaveNatural.normalizar(nome))) {
                return nome;
            }
        }
    }

    private static String primeiroEUltimo(String nomeCompleto) {
        var partes = nomeCompleto.split(" ");
        return partes[0] + " " + partes[partes.length - 1];
    }

    private static String slugDe(String nomeCompleto, Set<String> jaUsados) {
        var normalizado = ChaveNatural.normalizar(nomeCompleto);
        jaUsados.add(normalizado);
        return normalizado.replace(' ', '-');
    }

    private static String pePreferido(String posicao, Random random) {
        if ("LTE".equals(posicao)) {
            return "ESQUERDO";
        }
        return random.nextInt(10) < 8 ? "DIREITO" : "ESQUERDO";
    }

    private static int[] atributos(String posicao, int base, int idade, Random random) {
        var ajustes = AJUSTE_POR_POSICAO.get(posicao);
        var valores = new int[SKILLS.size()];
        for (var indice = 0; indice < valores.length; indice++) {
            var bruto = base + ajustes[indice] + ajustePorIdade(idade) + random.nextInt(17) - 8;
            valores[indice] = Math.clamp(bruto, 20, 94);
        }
        return valores;
    }

    private static int ajustePorIdade(int idade) {
        if (idade < 20) {
            return -6;
        }
        if (idade > 33) {
            return -4;
        }
        return 0;
    }

    /** Jovem sobe, veterano cai — a progressão determinística entre 2025 e 2026. */
    private static int[] evoluir(int[] anterior, int idade) {
        var delta = idade < 25 ? 2 : idade > 32 ? -2 : 0;
        var evoluido = new int[anterior.length];
        for (var indice = 0; indice < anterior.length; indice++) {
            evoluido[indice] = Math.clamp(anterior[indice] + delta, 20, 94);
        }
        return evoluido;
    }

    private static int mediaRelevante(int[] atributos, String posicao) {
        var deGoleiro = "GOL".equals(posicao);
        var inicio = deGoleiro ? 15 : 0;
        var fim = deGoleiro ? 18 : 15;
        var soma = 0;
        for (var indice = inicio; indice < fim; indice++) {
            soma += atributos[indice];
        }
        return soma / (fim - inicio);
    }

    private static int[] ocultos(Random random) {
        var valores = new int[8];
        for (var indice = 0; indice < valores.length; indice++) {
            valores[indice] = 35 + random.nextInt(55);
        }
        return valores;
    }

    private static List<String> caracteristicas(String posicao, Random random) {
        var quantidade = random.nextInt(3);
        var escolhidas = new ArrayList<String>();
        for (var sorteio = 0; sorteio < quantidade; sorteio++) {
            var candidata = CARACTERISTICAS_DE_LINHA.get(random.nextInt(CARACTERISTICAS_DE_LINHA.size()));
            // Goleiro não cobra pênalti nem falta na fixture: dado incoerente vira ruído.
            var incoerenteParaGoleiro = "GOL".equals(posicao)
                    && (candidata.startsWith("ESPECIALISTA_") || "DRIBLADOR".equals(candidata));
            if (!incoerenteParaGoleiro && !escolhidas.contains(candidata)) {
                escolhidas.add(candidata);
            }
        }
        return escolhidas;
    }

    // ---------- linhas de jogador ----------

    private static List<String> linhasDeJogador(List<JogadorGerado> jogadores) {
        var linhas = new ArrayList<String>();
        linhas.add("slug,nome_completo,nome_exibicao,data_nascimento,iso_pais,"
                + "iso_segunda_nacionalidade,altura_cm,peso_kg,pe_preferido,posicao_principal,origem");
        for (var jogador : jogadores) {
            linhas.add(juntar(jogador.slug, jogador.nomeCompleto, jogador.nomeExibicao,
                    jogador.nascimento.toString(), "BRA", "",
                    String.valueOf(jogador.alturaCm), String.valueOf(jogador.pesoKg),
                    jogador.pePreferido, jogador.posicao, "REAL"));
        }
        return linhas;
    }

    private static List<String> linhasDePosicao(List<JogadorGerado> jogadores) {
        var linhas = new ArrayList<String>();
        linhas.add("jogador_slug,posicao,ordem");
        for (var jogador : jogadores) {
            var secundaria = POSICAO_SECUNDARIA.get(jogador.posicao);
            if (!secundaria.equals(jogador.posicao)) {
                linhas.add(juntar(jogador.slug, secundaria, "2"));
            }
        }
        return linhas;
    }

    private static List<String> linhasDeAtributo(List<JogadorGerado> jogadores) {
        var linhas = new ArrayList<String>();
        linhas.add("jogador_slug,temporada," + String.join(",", SKILLS)
                + ",potencial_base,potencial_variacao,fonte_atributo,coletado_em");
        for (var jogador : jogadores) {
            linhas.add(linhaDeAtributo(jogador, "2025", jogador.atributos2025, COLETA_2025));
            linhas.add(linhaDeAtributo(jogador, "2026", jogador.atributos2026, COLETA_2026));
        }
        return linhas;
    }

    private static String linhaDeAtributo(JogadorGerado jogador, String temporada,
                                          int[] atributos, String coletadoEm) {
        var campos = new ArrayList<String>();
        campos.add(jogador.slug);
        campos.add(temporada);
        for (var valor : atributos) {
            campos.add(String.valueOf(valor));
        }
        campos.add(String.valueOf(jogador.potencialBase));
        campos.add(String.valueOf(jogador.potencialVariacao));
        campos.add("IMPORTADO");
        campos.add(coletadoEm);
        return juntar(campos.toArray(String[]::new));
    }

    private static List<String> linhasDeOcultos(List<JogadorGerado> jogadores) {
        var linhas = new ArrayList<String>();
        linhas.add("jogador_slug,profissionalismo,ambicao,lealdade,temperamento,"
                + "lideranca,regularidade,propensao_lesao,resistencia_pressao");
        for (var jogador : jogadores) {
            var campos = new ArrayList<String>();
            campos.add(jogador.slug);
            for (var valor : jogador.ocultos) {
                campos.add(String.valueOf(valor));
            }
            linhas.add(juntar(campos.toArray(String[]::new)));
        }
        return linhas;
    }

    private static List<String> linhasDeCaracteristica(List<JogadorGerado> jogadores) {
        var linhas = new ArrayList<String>();
        linhas.add("jogador_slug,caracteristica");
        for (var jogador : jogadores) {
            for (var caracteristica : jogador.caracteristicas) {
                linhas.add(juntar(jogador.slug, caracteristica));
            }
        }
        return linhas;
    }

    private static List<String> linhasDeVinculo(List<JogadorGerado> jogadores) {
        // Índices fixos, não sorteados: transferência é caso de teste, e caso de teste
        // escolhido por sorteio muda de lugar a cada ajuste do gerador.
        var transferidos = Set.of(10, 45, 120);
        var emprestado = 77;
        var linhas = new ArrayList<String>();
        linhas.add("jogador_slug,clube_slug,temporada,tipo,numero_camisa,"
                + "data_inicio,data_fim,valor_mercado_eur");
        for (var indice = 0; indice < jogadores.size(); indice++) {
            var jogador = jogadores.get(indice);
            var clubeDe2025 = CLUBES.get(jogador.indiceClube).slug();
            var clubeDe2026 = transferidos.contains(indice)
                    ? CLUBES.get((jogador.indiceClube + 1) % CLUBES.size()).slug()
                    : clubeDe2025;
            var valor = valorDeMercado(jogador);
            linhas.add(juntar(jogador.slug, clubeDe2025, "2025", "CONTRATO",
                    String.valueOf(jogador.numeroCamisa), "2025-01-01", "2025-12-31", valor));
            linhas.add(juntar(jogador.slug, clubeDe2026, "2026",
                    indice == emprestado ? "EMPRESTIMO" : "CONTRATO",
                    String.valueOf(jogador.numeroCamisa), "2026-01-01", "2026-12-31", valor));
        }
        return linhas;
    }

    private static String valorDeMercado(JogadorGerado jogador) {
        var overall = mediaRelevante(jogador.atributos2026, jogador.posicao);
        return (long) overall * overall * 800L + ".00";
    }

    // ---------- escrita ----------

    private static void escrever(Path diretorio, String nome, List<String> linhas) throws IOException {
        Files.writeString(diretorio.resolve(nome),
                String.join("\n", linhas) + "\n", StandardCharsets.UTF_8);
    }

    private static void escreverManifesto(Path diretorio, Map<String, List<String>> arquivos)
            throws IOException {
        var entradas = new StringJoiner(",\n    ", "[\n    ", "\n  ]");
        for (var entrada : arquivos.entrySet()) {
            var nome = entrada.getKey();
            var linhasDeDados = entrada.getValue().size() - 1;
            var sha256 = ValidadorDeDataset.sha256(diretorio.resolve(nome));
            entradas.add("{ \"nome\": \"%s\", \"linhas\": %d, \"sha256\": \"%s\" }"
                    .formatted(nome, linhasDeDados, sha256));
        }
        Files.writeString(diretorio.resolve("manifest.json"), """
                {
                  "schemaVersao": "%s",
                  "datasetVersao": "%s",
                  "geradoEm": "%s",
                  "arquivos": %s
                }
                """.formatted(Manifesto.SCHEMA_SUPORTADO, DATASET_VERSAO, GERADO_EM, entradas),
                StandardCharsets.UTF_8);
    }

    /**
     * Nenhum campo das fixtures contém vírgula ou aspas. Se algum dia contiver, é
     * melhor falhar ruidosamente do que gravar um CSV que o parser lê torto.
     */
    private static String juntar(String... campos) {
        for (var campo : campos) {
            if (campo.indexOf(',') >= 0 || campo.indexOf('"') >= 0) {
                throw new IllegalStateException(
                        "campo '%s' precisa de aspas; o gerador não as emite".formatted(campo));
            }
        }
        return String.join(",", campos);
    }
}

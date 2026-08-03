package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.DadosDeAlias;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.geografia.dto.PaisResumo;
import br.com.api.footfirma.importacao.ImportacaoService;
import br.com.api.footfirma.importacao.dto.ContagemDeEntidade;
import br.com.api.footfirma.importacao.dto.RelatorioDeImportacao;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.DadosDeAtributos;
import br.com.api.footfirma.jogador.dto.DadosDeAtributosOcultos;
import br.com.api.footfirma.jogador.dto.DadosDeCaracteristica;
import br.com.api.footfirma.jogador.dto.DadosDeJogador;
import br.com.api.footfirma.jogador.dto.DadosDePosicaoSecundaria;
import br.com.api.footfirma.jogador.dto.DadosDeVinculo;
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
@Slf4j
class ImportacaoServiceImpl implements ImportacaoService {

    private static final String ISO_PADRAO = "BRA";

    // 8 dependências, acima do limite de 4~5 de .rules/java-core.md, e justificado:
    // este é o orquestrador, e cada uma é um módulo do catálogo. Dividi-lo por
    // módulo só moveria a lista para outro lugar.
    private final TemporadaService temporadaService;
    private final GeografiaService geografiaService;
    private final ClubeService clubeService;
    private final CompeticaoService competicaoService;
    private final JogadorService jogadorService;
    private final AvaliacaoService avaliacaoService;
    private final RegistroDeExecucao registroDeExecucao;
    private final TransactionTemplate transactionTemplate;

    /** Contador mutável de uma etapa. */
    private static final class Contador {
        private int lidos;
        private int criados;
        private int atualizados;
        private int recusados;

        void registrar(ResultadoDeSincronizacao resultado) {
            if (resultado.criado()) {
                criados++;
            } else {
                atualizados++;
            }
        }

        void recusar() {
            recusados++;
        }
    }

    /** Estado de uma execução. Instância nova por carga — nada sobrevive entre elas. */
    private static final class Contexto {
        private final ColecionadorDeOcorrencias ocorrencias = new ColecionadorDeOcorrencias();
        private final List<ContagemDeEntidade> contagens = new ArrayList<>();
        private final Map<String, Long> temporadaPorLabel = new HashMap<>();
        private final Map<String, Long> estadoPorUf = new HashMap<>();
        private final Map<String, Long> estadioPorChave = new HashMap<>();
        private final Map<String, Long> clubePorSlug = new HashMap<>();
        private final Map<String, Long> competicaoPorSlug = new HashMap<>();
        private final Map<String, Long> edicaoPorChave = new HashMap<>();
        private final Map<String, Long> jogadorPorSlug = new HashMap<>();
        private final Map<String, Long> posicaoPorCodigo = new HashMap<>();
        private final Map<String, Long> caracteristicaPorCodigo = new HashMap<>();
        private final LinkedHashSet<String> temporadasCarregadas = new LinkedHashSet<>();
        private Long paisPadraoId;

        static String chaveDeEdicao(String competicaoSlug, String temporadaLabel) {
            return competicaoSlug + "|" + temporadaLabel;
        }
    }

    @Override
    public RelatorioDeImportacao importar(Path diretorio) {
        // Validação antes de abrir execução: dataset inválido não produziu carga
        // nenhuma, e uma linha FALHOU sem dataset válido não informa nada.
        var manifesto = ValidadorDeDataset.validar(diretorio);
        var execucaoId = registroDeExecucao.abrir(
                manifesto.datasetVersao(), manifesto.schemaVersao(), diretorio.toString());
        var contexto = new Contexto();

        try {
            carregarCatalogoDeSeed(contexto);
            executarEtapas(diretorio, contexto);
            var overalls = materializarTemporadas(contexto);
            registroDeExecucao.concluir(execucaoId, contexto.contagens, contexto.ocorrencias.lista());
            return new RelatorioDeImportacao(execucaoId, manifesto.datasetVersao(), "CONCLUIDA",
                    contexto.contagens, contexto.ocorrencias.lista(), overalls, null);
        } catch (RuntimeException erro) {
            log.error("Importação {} falhou", execucaoId, erro);
            registroDeExecucao.falhar(execucaoId, erro.getMessage(),
                    contexto.contagens, contexto.ocorrencias.lista());
            return new RelatorioDeImportacao(execucaoId, manifesto.datasetVersao(), "FALHOU",
                    contexto.contagens, contexto.ocorrencias.lista(), 0, erro.getMessage());
        }
    }

    private void carregarCatalogoDeSeed(Contexto contexto) {
        contexto.paisPadraoId = geografiaService.buscarPaisPorIso(ISO_PADRAO)
                .map(PaisResumo::id)
                .orElseThrow(() -> new IllegalStateException(
                        "seed de geografia ausente: país " + ISO_PADRAO));
        geografiaService.listarEstadosDoPais(ISO_PADRAO)
                .forEach(estado -> contexto.estadoPorUf.put(estado.uf(), estado.id()));
        jogadorService.listarPosicoes()
                .forEach(posicao -> contexto.posicaoPorCodigo.put(posicao.codigo(), posicao.id()));
    }

    private void executarEtapas(Path diretorio, Contexto contexto) {
        etapaTemporada(diretorio, contexto);
        etapaEstadio(diretorio, contexto);
        etapaClube(diretorio, contexto);
        etapaAlias(diretorio, contexto);
        etapaCompeticao(diretorio, contexto);
        etapaEdicao(diretorio, contexto);
        etapaFase(diretorio, contexto);
        etapaParticipante(diretorio, contexto);
        etapaRegra(diretorio, contexto);
        etapaJogador(diretorio, contexto);
        etapaPosicaoSecundaria(diretorio, contexto);
        etapaAtributo(diretorio, contexto);
        etapaAtributoOculto(diretorio, contexto);
        etapaCaracteristica(diretorio, contexto);
        etapaVinculo(diretorio, contexto);
    }

    /**
     * Uma transação por etapa. A carga inteira numa transação seguraria a conexão
     * por minutos; uma por linha multiplicaria o custo por ~1.500 sem ganho.
     */
    private void etapa(Path diretorio, String arquivo, List<String> cabecalho,
                       Contexto contexto, BiConsumer<LinhaDeCsv, Contador> acao) {
        var linhas = LeitorDeCsv.ler(diretorio.resolve(arquivo), cabecalho);
        var contador = new Contador();
        contador.lidos = linhas.size();
        transactionTemplate.executeWithoutResult(status ->
                linhas.forEach(linha -> acao.accept(linha, contador)));
        var contagem = new ContagemDeEntidade(
                arquivo.replace(".csv", ""),
                contador.lidos, contador.criados, contador.atualizados, contador.recusados);
        contexto.contagens.add(contagem);
        log.info("etapa {}: {} lidos, {} criados, {} atualizados, {} recusados",
                contagem.entidade(), contagem.lidos(), contagem.criados(),
                contagem.atualizados(), contagem.recusados());
    }

    private int materializarTemporadas(Contexto contexto) {
        var linhas = 0;
        for (var label : contexto.temporadasCarregadas) {
            linhas += avaliacaoService.materializar(label).linhas();
        }
        return linhas;
    }

    // ---------- etapas ----------

    private void etapaTemporada(Path diretorio, Contexto contexto) {
        etapa(diretorio, "temporada.csv", List.of("label", "ano_inicio", "ano_fim"), contexto,
                (linha, contador) -> {
                    var label = linha.textoObrigatorio("label");
                    var resultado = temporadaService.sincronizar(new DadosDeTemporada(
                            label, linha.inteiro("ano_inicio"), linha.inteiro("ano_fim")));
                    contexto.temporadaPorLabel.put(label, resultado.id());
                    contexto.temporadasCarregadas.add(label);
                    contador.registrar(resultado);
                });
    }

    private void etapaEstadio(Path diretorio, Contexto contexto) {
        etapa(diretorio, "estadio.csv",
                List.of("chave", "nome", "cidade", "uf", "capacidade", "ano_inauguracao"),
                contexto, (linha, contador) -> {
                    var chave = linha.textoObrigatorio("chave");
                    var resultado = clubeService.sincronizarEstadio(new DadosDeEstadio(
                            linha.textoObrigatorio("nome"),
                            linha.textoObrigatorio("cidade"),
                            estadoDaUf(contexto, linha.texto("uf"), "estadio", linha.numero(), chave),
                            linha.inteiro("capacidade"),
                            linha.inteiro("ano_inauguracao")));
                    contexto.estadioPorChave.put(chave, resultado.id());
                    contador.registrar(resultado);
                });
    }

    private void etapaClube(Path diretorio, Contexto contexto) {
        etapa(diretorio, "clube.csv",
                List.of("slug", "nome_oficial", "nome_curto", "apelido", "ano_fundacao",
                        "iso_pais", "uf", "estadio_chave", "cor_primaria", "cor_secundaria",
                        "reputacao", "qualidade_base", "uf_base"),
                contexto, (linha, contador) -> {
                    var slug = linha.textoObrigatorio("slug");
                    var estadioChave = linha.texto("estadio_chave");
                    var estadioId = estadioChave == null
                            ? null : contexto.estadioPorChave.get(estadioChave);
                    if (estadioChave != null && estadioId == null) {
                        contexto.ocorrencias.avisar("clube", linha.numero(), slug,
                                "estádio '" + estadioChave + "' não existe no dataset");
                    }
                    var resultado = clubeService.sincronizarClube(new DadosDeClube(
                            slug,
                            linha.textoObrigatorio("nome_oficial"),
                            linha.textoObrigatorio("nome_curto"),
                            linha.texto("apelido"),
                            linha.inteiro("ano_fundacao"),
                            contexto.paisPadraoId,
                            estadoDaUf(contexto, linha.texto("uf"), "clube", linha.numero(), slug),
                            estadioId,
                            linha.texto("cor_primaria"),
                            linha.texto("cor_secundaria"),
                            linha.inteiro("reputacao"),
                            linha.inteiro("qualidade_base"),
                            estadoDaUf(contexto, linha.texto("uf_base"), "clube", linha.numero(), slug)));
                    contexto.clubePorSlug.put(slug, resultado.id());
                    contador.registrar(resultado);
                });
    }

    private void etapaAlias(Path diretorio, Contexto contexto) {
        etapa(diretorio, "clube_alias.csv", List.of("clube_slug", "alias", "fonte"), contexto,
                (linha, contador) -> {
                    var clubeSlug = linha.textoObrigatorio("clube_slug");
                    var clubeId = contexto.clubePorSlug.get(clubeSlug);
                    if (clubeId == null) {
                        contexto.ocorrencias.recusar("clube_alias", linha.numero(), clubeSlug,
                                "clube não encontrado no dataset");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(clubeService.sincronizarAlias(new DadosDeAlias(
                            clubeId, linha.textoObrigatorio("alias"),
                            linha.textoObrigatorio("fonte"))));
                });
    }

    private void etapaCompeticao(Path diretorio, Contexto contexto) {
        etapa(diretorio, "competicao.csv",
                List.of("slug", "nome", "iso_pais", "tipo", "nivel", "genero"),
                contexto, (linha, contador) -> {
                    var slug = linha.textoObrigatorio("slug");
                    var resultado = competicaoService.sincronizarCompeticao(new DadosDeCompeticao(
                            slug,
                            linha.textoObrigatorio("nome"),
                            linha.texto("iso_pais") == null ? null : contexto.paisPadraoId,
                            linha.textoObrigatorio("tipo"),
                            linha.inteiro("nivel"),
                            linha.textoObrigatorio("genero")));
                    contexto.competicaoPorSlug.put(slug, resultado.id());
                    contador.registrar(resultado);
                });
    }

    private void etapaEdicao(Path diretorio, Contexto contexto) {
        etapa(diretorio, "edicao.csv",
                List.of("competicao_slug", "temporada", "nome", "data_inicio", "data_fim"),
                contexto, (linha, contador) -> {
                    var competicaoSlug = linha.textoObrigatorio("competicao_slug");
                    var temporadaLabel = linha.textoObrigatorio("temporada");
                    var competicaoId = contexto.competicaoPorSlug.get(competicaoSlug);
                    var temporadaId = contexto.temporadaPorLabel.get(temporadaLabel);
                    if (competicaoId == null || temporadaId == null) {
                        contexto.ocorrencias.recusar("edicao", linha.numero(),
                                Contexto.chaveDeEdicao(competicaoSlug, temporadaLabel),
                                "competição ou temporada não encontradas no dataset");
                        contador.recusar();
                        return;
                    }
                    var resultado = competicaoService.sincronizarEdicao(new DadosDeEdicao(
                            competicaoId, temporadaId, linha.textoObrigatorio("nome"),
                            linha.data("data_inicio"), linha.data("data_fim")));
                    contexto.edicaoPorChave.put(
                            Contexto.chaveDeEdicao(competicaoSlug, temporadaLabel), resultado.id());
                    contador.registrar(resultado);
                });
    }

    private void etapaFase(Path diretorio, Contexto contexto) {
        etapa(diretorio, "fase.csv",
                List.of("competicao_slug", "temporada", "ordem", "nome", "tipo",
                        "jogos_por_confronto", "tem_gol_fora", "tem_prorrogacao", "tem_penaltis"),
                contexto, (linha, contador) -> {
                    var chave = Contexto.chaveDeEdicao(
                            linha.textoObrigatorio("competicao_slug"),
                            linha.textoObrigatorio("temporada"));
                    var edicaoId = contexto.edicaoPorChave.get(chave);
                    if (edicaoId == null) {
                        contexto.ocorrencias.recusar("fase", linha.numero(), chave,
                                "edição não encontrada no dataset");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(competicaoService.sincronizarFase(new DadosDeFase(
                            edicaoId,
                            linha.inteiro("ordem"),
                            linha.textoObrigatorio("nome"),
                            linha.textoObrigatorio("tipo"),
                            linha.inteiro("jogos_por_confronto"),
                            linha.booleano("tem_gol_fora"),
                            linha.booleano("tem_prorrogacao"),
                            linha.booleano("tem_penaltis"))));
                });
    }

    private void etapaParticipante(Path diretorio, Contexto contexto) {
        etapa(diretorio, "edicao_participante.csv",
                List.of("competicao_slug", "temporada", "clube_slug", "posicao_final"),
                contexto, (linha, contador) -> {
                    var chave = Contexto.chaveDeEdicao(
                            linha.textoObrigatorio("competicao_slug"),
                            linha.textoObrigatorio("temporada"));
                    var clubeSlug = linha.textoObrigatorio("clube_slug");
                    var edicaoId = contexto.edicaoPorChave.get(chave);
                    var clubeId = contexto.clubePorSlug.get(clubeSlug);
                    if (edicaoId == null || clubeId == null) {
                        contexto.ocorrencias.recusar("edicao_participante", linha.numero(),
                                chave + "@" + clubeSlug,
                                "edição ou clube não encontrados no dataset");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(competicaoService.sincronizarParticipante(
                            new DadosDeParticipante(edicaoId, clubeId, linha.inteiro("posicao_final"))));
                });
    }

    private void etapaRegra(Path diretorio, Contexto contexto) {
        etapa(diretorio, "regra_classificacao.csv",
                List.of("competicao_slug", "temporada", "posicao_inicio", "posicao_fim",
                        "tipo", "competicao_destino_slug"),
                contexto, (linha, contador) -> {
                    var chave = Contexto.chaveDeEdicao(
                            linha.textoObrigatorio("competicao_slug"),
                            linha.textoObrigatorio("temporada"));
                    var edicaoId = contexto.edicaoPorChave.get(chave);
                    if (edicaoId == null) {
                        contexto.ocorrencias.recusar("regra_classificacao", linha.numero(), chave,
                                "edição não encontrada no dataset");
                        contador.recusar();
                        return;
                    }
                    var destinoSlug = linha.texto("competicao_destino_slug");
                    var destinoId = destinoSlug == null
                            ? null : contexto.competicaoPorSlug.get(destinoSlug);
                    if (destinoSlug != null && destinoId == null) {
                        contexto.ocorrencias.avisar("regra_classificacao", linha.numero(), chave,
                                "competição destino '" + destinoSlug + "' não existe no dataset");
                    }
                    contador.registrar(competicaoService.sincronizarRegra(new DadosDeRegra(
                            edicaoId, linha.inteiro("posicao_inicio"), linha.inteiro("posicao_fim"),
                            linha.textoObrigatorio("tipo"), destinoId)));
                });
    }

    private void etapaJogador(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador.csv",
                List.of("slug", "nome_completo", "nome_exibicao", "data_nascimento", "iso_pais",
                        "iso_segunda_nacionalidade", "altura_cm", "peso_kg", "pe_preferido",
                        "posicao_principal", "origem"),
                contexto, (linha, contador) -> {
                    var slug = linha.textoObrigatorio("slug");
                    var codigoDaPosicao = linha.textoObrigatorio("posicao_principal");
                    var posicaoId = contexto.posicaoPorCodigo.get(codigoDaPosicao);
                    if (posicaoId == null) {
                        contexto.ocorrencias.recusar("jogador", linha.numero(), slug,
                                "posição '" + codigoDaPosicao + "' não existe no catálogo");
                        contador.recusar();
                        return;
                    }
                    var nomeCompleto = linha.textoObrigatorio("nome_completo");
                    var nascimento = linha.data("data_nascimento");
                    var isoPais = linha.textoObrigatorio("iso_pais");
                    var chaveNatural = ChaveNatural.de(nomeCompleto, nascimento, isoPais);
                    var resultado = jogadorService.sincronizarJogador(new DadosDeJogador(
                            slug, chaveNatural, ChaveNatural.semente(chaveNatural),
                            nomeCompleto,
                            linha.textoObrigatorio("nome_exibicao"),
                            nascimento,
                            contexto.paisPadraoId,
                            // As fixtures só têm brasileiros; quando um dataset trouxer
                            // estrangeiro, é aqui que iso_segunda_nacionalidade resolve.
                            null,
                            linha.inteiro("altura_cm"),
                            linha.inteiro("peso_kg"),
                            linha.textoObrigatorio("pe_preferido"),
                            posicaoId,
                            linha.textoObrigatorio("origem")));
                    contexto.jogadorPorSlug.put(slug, resultado.id());
                    contador.registrar(resultado);
                });
    }

    private void etapaPosicaoSecundaria(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador_posicao.csv", List.of("jogador_slug", "posicao", "ordem"),
                contexto, (linha, contador) -> {
                    var jogadorSlug = linha.textoObrigatorio("jogador_slug");
                    var codigoDaPosicao = linha.textoObrigatorio("posicao");
                    var jogadorId = contexto.jogadorPorSlug.get(jogadorSlug);
                    var posicaoId = contexto.posicaoPorCodigo.get(codigoDaPosicao);
                    if (jogadorId == null || posicaoId == null) {
                        contexto.ocorrencias.recusar("jogador_posicao", linha.numero(), jogadorSlug,
                                "jogador ou posição não encontrados");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(jogadorService.sincronizarPosicaoSecundaria(
                            new DadosDePosicaoSecundaria(jogadorId, posicaoId, linha.inteiro("ordem"))));
                });
    }

    private void etapaAtributo(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador_atributo.csv",
                List.of("jogador_slug", "temporada",
                        "ritmo", "forca", "folego", "salto", "agilidade",
                        "passe", "drible", "cruzamento", "frieza",
                        "finalizacao", "cabeceio", "falta", "penalti",
                        "desarme", "marcacao",
                        "gol_reflexo", "gol_posicionamento", "gol_manejo",
                        "potencial_base", "potencial_variacao", "fonte_atributo", "coletado_em"),
                contexto, (linha, contador) -> {
                    var jogadorSlug = linha.textoObrigatorio("jogador_slug");
                    var temporadaLabel = linha.textoObrigatorio("temporada");
                    var jogadorId = contexto.jogadorPorSlug.get(jogadorSlug);
                    var temporadaId = contexto.temporadaPorLabel.get(temporadaLabel);
                    if (jogadorId == null || temporadaId == null) {
                        contexto.ocorrencias.recusar("jogador_atributo", linha.numero(),
                                jogadorSlug + "@" + temporadaLabel,
                                "jogador ou temporada não encontrados no dataset");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(jogadorService.sincronizarAtributos(new DadosDeAtributos(
                            jogadorId, temporadaId,
                            linha.inteiro("ritmo"), linha.inteiro("forca"), linha.inteiro("folego"),
                            linha.inteiro("salto"), linha.inteiro("agilidade"), linha.inteiro("passe"),
                            linha.inteiro("drible"), linha.inteiro("cruzamento"), linha.inteiro("frieza"),
                            linha.inteiro("finalizacao"), linha.inteiro("cabeceio"), linha.inteiro("falta"),
                            linha.inteiro("penalti"), linha.inteiro("desarme"), linha.inteiro("marcacao"),
                            linha.inteiro("gol_reflexo"), linha.inteiro("gol_posicionamento"),
                            linha.inteiro("gol_manejo"),
                            linha.inteiro("potencial_base"), linha.inteiro("potencial_variacao"),
                            linha.textoObrigatorio("fonte_atributo"), linha.dataHora("coletado_em"))));
                });
    }

    private void etapaAtributoOculto(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador_atributo_oculto.csv",
                List.of("jogador_slug", "profissionalismo", "ambicao", "lealdade", "temperamento",
                        "lideranca", "regularidade", "propensao_lesao", "resistencia_pressao"),
                contexto, (linha, contador) -> {
                    var jogadorSlug = linha.textoObrigatorio("jogador_slug");
                    var jogadorId = contexto.jogadorPorSlug.get(jogadorSlug);
                    if (jogadorId == null) {
                        contexto.ocorrencias.recusar("jogador_atributo_oculto", linha.numero(),
                                jogadorSlug, "jogador não encontrado no dataset");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(jogadorService.sincronizarAtributosOcultos(
                            new DadosDeAtributosOcultos(jogadorId,
                                    linha.inteiro("profissionalismo"), linha.inteiro("ambicao"),
                                    linha.inteiro("lealdade"), linha.inteiro("temperamento"),
                                    linha.inteiro("lideranca"), linha.inteiro("regularidade"),
                                    linha.inteiro("propensao_lesao"),
                                    linha.inteiro("resistencia_pressao"))));
                });
    }

    private void etapaCaracteristica(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador_caracteristica.csv", List.of("jogador_slug", "caracteristica"),
                contexto, (linha, contador) -> {
                    var jogadorSlug = linha.textoObrigatorio("jogador_slug");
                    var codigo = linha.textoObrigatorio("caracteristica");
                    var jogadorId = contexto.jogadorPorSlug.get(jogadorSlug);
                    var caracteristicaId = idDaCaracteristica(contexto, codigo);
                    if (jogadorId == null || caracteristicaId == null) {
                        contexto.ocorrencias.recusar("jogador_caracteristica", linha.numero(),
                                jogadorSlug + "@" + codigo,
                                "jogador ou característica não encontrados");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(jogadorService.sincronizarCaracteristica(
                            new DadosDeCaracteristica(jogadorId, caracteristicaId)));
                });
    }

    private void etapaVinculo(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador_vinculo.csv",
                List.of("jogador_slug", "clube_slug", "temporada", "tipo", "numero_camisa",
                        "data_inicio", "data_fim", "valor_mercado_eur"),
                contexto, (linha, contador) -> {
                    var jogadorSlug = linha.textoObrigatorio("jogador_slug");
                    var clubeSlug = linha.textoObrigatorio("clube_slug");
                    var temporadaLabel = linha.textoObrigatorio("temporada");
                    var jogadorId = contexto.jogadorPorSlug.get(jogadorSlug);
                    var clubeId = contexto.clubePorSlug.get(clubeSlug);
                    var temporadaId = contexto.temporadaPorLabel.get(temporadaLabel);
                    if (jogadorId == null || clubeId == null || temporadaId == null) {
                        contexto.ocorrencias.recusar("jogador_vinculo", linha.numero(),
                                jogadorSlug + "@" + clubeSlug,
                                "jogador, clube ou temporada não encontrados no dataset");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(jogadorService.sincronizarVinculo(new DadosDeVinculo(
                            jogadorId, clubeId, temporadaId,
                            linha.textoObrigatorio("tipo"),
                            linha.inteiro("numero_camisa"),
                            linha.data("data_inicio"),
                            linha.data("data_fim"),
                            linha.decimal("valor_mercado_eur"))));
                });
    }

    // ---------- resolução ----------

    /** UF ausente do seed é AVISO, não recusa: o catálogo de geografia é seed versionado. */
    private Long estadoDaUf(Contexto contexto, String uf, String entidade, int linha, String chave) {
        if (uf == null) {
            return null;
        }
        var estadoId = contexto.estadoPorUf.get(uf);
        if (estadoId == null) {
            contexto.ocorrencias.avisar(entidade, linha, chave,
                    "UF '" + uf + "' não existe no catálogo de geografia");
        }
        return estadoId;
    }

    private Long idDaCaracteristica(Contexto contexto, String codigo) {
        // computeIfAbsent com valor null não memoriza a ausência: o custo é uma
        // consulta por linha para código inexistente, que só ocorre em dataset ruim.
        return contexto.caracteristicaPorCodigo.computeIfAbsent(codigo,
                chave -> jogadorService.buscarIdCaracteristicaPorCodigo(chave).orElse(null));
    }
}

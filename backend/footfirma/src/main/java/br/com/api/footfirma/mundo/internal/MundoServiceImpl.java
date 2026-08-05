package br.com.api.footfirma.mundo.internal;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.DadosDeAtributos;
import br.com.api.footfirma.jogador.dto.DadosDeAtributosOcultos;
import br.com.api.footfirma.jogador.dto.DadosDeJogador;
import br.com.api.footfirma.jogador.dto.DadosDeVinculo;
import br.com.api.footfirma.jogador.dto.PosicaoCatalogo;
import br.com.api.footfirma.mundo.MundoService;
import br.com.api.footfirma.mundo.dto.ContagemPorEntidade;
import br.com.api.footfirma.mundo.dto.RelatorioDeMundo;
import br.com.api.footfirma.tatica.TaticaService;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(PropriedadesDeMundo.class)
@RequiredArgsConstructor
class MundoServiceImpl implements MundoService {

    static final String TEMPORADA = "2026";
    private static final String ISO_PAIS = "BRA";

    private final TemporadaService temporadaService;
    private final CompeticaoService competicaoService;
    private final ClubeService clubeService;
    private final JogadorService jogadorService;
    private final AvaliacaoService avaliacaoService;
    private final TaticaService taticaService;
    private final GeografiaService geografiaService;
    private final PropriedadesDeMundo propriedades;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Sem transação envolvente, de propósito. Cada {@code sincronizar*} abre a sua, e
     * a geração inteira num único commit seria errada por dois motivos: seguraria a
     * conexão por minutos, e {@code AvaliacaoService.materializar} é
     * {@code NOT_SUPPORTED} — ela suspende a transação corrente, então não enxergaria
     * nenhum dos 1.520 atributos ainda não commitados e gravaria zero overall.
     *
     * <p>A escalação é o último passo pelo mesmo motivo, invertido: o escalador lê
     * {@code jogador_overall}, e antes de {@code materializar} essa tabela está vazia.
     * Chamado no meio da geração, ele escalaria onze jogadores com aptidão zero.
     *
     * <p>O preço é que uma falha no meio deixa mundo parcial. É o que
     * {@code footfirma.mundo.recriar} resolve.
     */
    @Override
    public RelatorioDeMundo gerar() {
        if (propriedades.recriar()) {
            LimpezaDoCatalogo.executar(jdbcTemplate);
        }
        var contagens = new ArrayList<ContagemPorEntidade>();
        var paisId = geografiaService.buscarPaisPorIso(ISO_PAIS)
                .orElseThrow(() -> new IllegalStateException(
                        "País BRA ausente — seed de geografia não aplicado"))
                .id();
        var temporadaId = criarTemporada(contagens);
        var edicaoPorDivisao = criarLigas(paisId, temporadaId, contagens);
        var idsPorSlug = new HashMap<String, Long>();
        var clubes = criarClubes(paisId, idsPorSlug, contagens);
        vincularParticipantes(clubes, idsPorSlug, edicaoPorDivisao, contagens);
        criarElencos(clubes, idsPorSlug, temporadaId, paisId, contagens);

        var materializacao = avaliacaoService.materializar(TEMPORADA);
        contagens.add(new ContagemPorEntidade("jogador_overall", materializacao.linhas(), 0));

        escalarClubes(clubes, idsPorSlug, temporadaId, contagens);
        return new RelatorioDeMundo(propriedades.semente(), TEMPORADA, List.copyOf(contagens));
    }

    private Long criarTemporada(List<ContagemPorEntidade> contagens) {
        var resultado = temporadaService.sincronizar(new DadosDeTemporada(TEMPORADA, 2026, 2026));
        contagens.add(new ContagemPorEntidade("temporada",
                resultado.criado() ? 1 : 0, resultado.criado() ? 0 : 1));
        return resultado.id();
    }

    private Map<Integer, Long> criarLigas(Long paisId, Long temporadaId,
                                          List<ContagemPorEntidade> contagens) {
        var primeira = criarLiga("primeira-divisao", "Primeira Divisão Nacional", 1,
                paisId, temporadaId);
        var segunda = criarLiga("segunda-divisao", "Segunda Divisão Nacional", 2,
                paisId, temporadaId);

        // Rebaixamento aponta para a divisão de destino; acesso, para a de origem.
        // Libertadores e Sul-Americana ficam fora: não há competição continental
        // neste mundo, e regra que aponta para o nada é dado falso.
        competicaoService.sincronizarRegra(
                new DadosDeRegra(primeira.edicaoId(), 17, 20, "REBAIXAMENTO", segunda.competicaoId()));
        competicaoService.sincronizarRegra(
                new DadosDeRegra(segunda.edicaoId(), 1, 4, "ACESSO", primeira.competicaoId()));

        contagens.add(new ContagemPorEntidade("competicao", 2, 0));
        contagens.add(new ContagemPorEntidade("edicao", 2, 0));
        return Map.of(1, primeira.edicaoId(), 2, segunda.edicaoId());
    }

    private LigaCriada criarLiga(String slug, String nome, int nivel, Long paisId, Long temporadaId) {
        var competicaoId = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao(slug, nome, paisId, "LIGA", nivel, "MASCULINO")).id();
        var edicaoId = competicaoService.sincronizarEdicao(new DadosDeEdicao(
                competicaoId, temporadaId, nome + " " + TEMPORADA,
                LocalDate.of(2026, 4, 4), LocalDate.of(2026, 12, 6))).id();
        competicaoService.sincronizarFase(new DadosDeFase(edicaoId, 1, "Turno e returno",
                "PONTOS_CORRIDOS", 2, false, false, false));
        return new LigaCriada(competicaoId, edicaoId);
    }

    private List<ClubeGerado> criarClubes(Long paisId, Map<String, Long> idsPorSlug,
                                          List<ContagemPorEntidade> contagens) {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(propriedades.semente()));
        var criados = 0;
        for (var clube : clubes) {
            var estadoId = geografiaService.buscarEstadoPorUf(ISO_PAIS, clube.uf())
                    .orElseThrow(() -> new IllegalStateException(
                            "UF %s ausente no seed de geografia".formatted(clube.uf())))
                    .id();
            var estadioId = clubeService.sincronizarEstadio(new DadosDeEstadio(
                    clube.estadio(), clube.cidade(), estadoId,
                    clube.capacidade(), clube.anoInauguracao())).id();
            var resultado = clubeService.sincronizarClube(new DadosDeClube(
                    clube.slug(), clube.nomeOficial(), clube.nomeCurto(), clube.apelido(),
                    clube.anoFundacao(), paisId, estadoId, estadioId,
                    clube.corPrimaria(), clube.corSecundaria(),
                    clube.reputacao(), clube.forcaFinanceira(), clube.qualidadeBase(), estadoId));
            if (resultado.criado()) {
                criados++;
            }
            idsPorSlug.put(clube.slug(), resultado.id());
        }
        contagens.add(new ContagemPorEntidade("clube", criados, clubes.size() - criados));
        return clubes;
    }

    private void vincularParticipantes(List<ClubeGerado> clubes, Map<String, Long> idsPorSlug,
                                       Map<Integer, Long> edicaoPorDivisao,
                                       List<ContagemPorEntidade> contagens) {
        // posicao_final fica nula: nenhuma temporada foi disputada, e classificação
        // inventada seria afirmar resultado que não aconteceu.
        clubes.forEach(clube -> competicaoService.sincronizarParticipante(new DadosDeParticipante(
                edicaoPorDivisao.get(clube.divisao()), idsPorSlug.get(clube.slug()), null)));
        contagens.add(new ContagemPorEntidade("edicao_participante", clubes.size(), 0));
    }

    private void criarElencos(List<ClubeGerado> clubes, Map<String, Long> idsPorSlug,
                              Long temporadaId, Long paisId, List<ContagemPorEntidade> contagens) {
        var posicoes = jogadorService.listarPosicoes().stream()
                .collect(Collectors.toMap(PosicaoCatalogo::codigo, PosicaoCatalogo::id));
        var chavesUsadas = new HashSet<String>();
        var total = 0;
        for (var clube : clubes) {
            // Um sub-gerador por clube: mexer no clube 7 não pode deslocar o 8, ou
            // qualquer ajuste reescreveria o mundo inteiro e o diff ficaria ilegível.
            var aleatorio = new SplittableRandom(propriedades.semente() + clube.slug().hashCode());
            for (var jogador : FabricaDeElenco.gerar(clube, aleatorio, chavesUsadas)) {
                gravarJogador(jogador, clube, idsPorSlug, posicoes, temporadaId, paisId, aleatorio);
                total++;
            }
        }
        contagens.add(new ContagemPorEntidade("jogador", total, 0));
    }

    private void gravarJogador(JogadorGerado jogador, ClubeGerado clube,
                               Map<String, Long> idsPorSlug, Map<String, Long> posicoes,
                               Long temporadaId, Long paisId, SplittableRandom aleatorio) {
        var jogadorId = jogadorService.sincronizarJogador(new DadosDeJogador(
                jogador.slug(), jogador.chaveNatural(), jogador.semente(),
                jogador.nomeCompleto(), jogador.nomeExibicao(), jogador.dataNascimento(),
                paisId, null, jogador.alturaCm(), jogador.pesoKg(), jogador.pePreferido(),
                posicoes.get(jogador.posicao()), "GERADO")).id();

        var skills = FabricaDeAtributos.gerar(jogador.posicao(), jogador.alvoOverall(), aleatorio);
        jogadorService.sincronizarAtributos(new DadosDeAtributos(
                jogadorId, temporadaId,
                skills.ritmo(), skills.forca(), skills.folego(), skills.salto(), skills.agilidade(),
                skills.passe(), skills.drible(), skills.cruzamento(), skills.frieza(),
                skills.finalizacao(), skills.cabeceio(), skills.falta(), skills.penalti(),
                skills.desarme(), skills.marcacao(), skills.golReflexo(),
                skills.golPosicionamento(), skills.golManejo(),
                jogador.potencialBase(), jogador.potencialVariacao(),
                "GERADO", OffsetDateTime.now()));

        // Atributo oculto nasce da semente do jogador, não do sorteio do clube: é
        // personalidade, e personalidade não muda de time.
        var daSemente = new SplittableRandom(jogador.semente());
        jogadorService.sincronizarAtributosOcultos(new DadosDeAtributosOcultos(
                jogadorId, daSemente.nextInt(20, 100), daSemente.nextInt(20, 100),
                daSemente.nextInt(20, 100), daSemente.nextInt(20, 100), daSemente.nextInt(20, 100),
                daSemente.nextInt(20, 100), daSemente.nextInt(20, 100), daSemente.nextInt(20, 100)));

        jogadorService.sincronizarVinculo(new DadosDeVinculo(
                jogadorId, idsPorSlug.get(clube.slug()), temporadaId, "CONTRATO",
                jogador.categoria(), jogador.numeroCamisa(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                valorDeMercado(jogador)));
    }

    /**
     * Dá a todo clube um plano tático vigente, escalado automaticamente.
     *
     * <p>A leitura extra existe só para o relatório: {@code garantirPlanoVigente} devolve
     * o plano sem dizer se o criou, e alargar essa assinatura degradaria o contrato que a
     * partida vai consumir por causa de uma contagem. São 40 leituras.
     *
     * <p>Se o elenco de um clube não fechar um time, {@code EscalacaoInvalidaException}
     * sobe e o mundo fica com parte dos clubes escalados — o mesmo mundo parcial que
     * qualquer outra falha aqui produz, e a mesma saída: gerar de novo com
     * {@code footfirma.mundo.recriar}.
     */
    private void escalarClubes(List<ClubeGerado> clubes, Map<String, Long> idsPorSlug,
                               Long temporadaId, List<ContagemPorEntidade> contagens) {
        var criados = 0;
        for (var clube : clubes) {
            var clubeId = idsPorSlug.get(clube.slug());
            if (taticaService.buscarPlanoVigente(clubeId, temporadaId).isEmpty()) {
                criados++;
            }
            taticaService.garantirPlanoVigente(clubeId, temporadaId);
        }
        contagens.add(new ContagemPorEntidade("plano_tatico", criados, clubes.size() - criados));
    }

    /**
     * Valor cresce com overall e com o espaço até o potencial, e cai com a idade.
     * Não depende da riqueza do clube: valor de mercado é atributo do jogador.
     */
    private BigDecimal valorDeMercado(JogadorGerado jogador) {
        var idade = 2026 - jogador.dataNascimento().getYear();
        var base = Math.pow(jogador.alvoOverall() / 10.0, 4) * 1_200;
        var promessa = 1 + (jogador.potencialBase() - jogador.alvoOverall()) * 0.05;
        var desgaste = idade <= 27 ? 1.0 : Math.max(0.25, 1 - (idade - 27) * 0.12);
        return BigDecimal.valueOf(base * promessa * desgaste).setScale(2, RoundingMode.HALF_UP);
    }

    record LigaCriada(Long competicaoId, Long edicaoId) {
    }
}

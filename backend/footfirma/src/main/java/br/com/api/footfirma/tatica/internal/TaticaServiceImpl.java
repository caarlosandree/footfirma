package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.dto.OverallDeJogador;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.JogadorDoElenco;
import br.com.api.footfirma.shared.exception.EscalacaoInvalidaException;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import br.com.api.footfirma.tatica.TaticaService;
import br.com.api.footfirma.tatica.domain.FormacaoSlot;
import br.com.api.footfirma.tatica.domain.PlanoEscalacao;
import br.com.api.footfirma.tatica.domain.PlanoTatico;
import br.com.api.footfirma.tatica.dto.Escalado;
import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.OrigemDoPlano;
import br.com.api.footfirma.tatica.dto.Papel;
import br.com.api.footfirma.tatica.dto.PlanoVigente;
import br.com.api.footfirma.tatica.repository.FormacaoRepository;
import br.com.api.footfirma.tatica.repository.FormacaoSlotRepository;
import br.com.api.footfirma.tatica.repository.PlanoEscalacaoRepository;
import br.com.api.footfirma.tatica.repository.PlanoTaticoRepository;
import br.com.api.footfirma.treinador.PerfilDeTreinador;
import br.com.api.footfirma.treinador.TreinadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TaticaServiceImpl implements TaticaService {

    private final PlanoTaticoRepository planos;
    private final PlanoEscalacaoRepository escalacoes;
    private final FormacaoSlotRepository formacaoSlots;
    private final FormacaoRepository formacoes;
    private final JogadorService jogadorService;
    private final AvaliacaoService avaliacaoService;
    private final TreinadorService treinadorService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public long salvarPlano(long clubeId, long temporadaId, NovoPlano novo) {
        var posicaoPorSlot = posicaoPorSlotDe(novo.formacaoId());
        var elenco = jogadorService.listarElencoParaEscalacao(clubeId, temporadaId);
        var principalPorJogador = elenco.stream().collect(Collectors.toMap(
                JogadorDoElenco::jogadorId, JogadorDoElenco::posicaoPrincipalId));

        ValidadorDeEscalacao.validar(novo, principalPorJogador.keySet(),
                idDaPosicaoDeGoleiro(), principalPorJogador);

        var overalls = tabelaDe(principalPorJogador.keySet(), temporadaId);
        return gravar(clubeId, temporadaId, novo, OrigemDoPlano.MANUAL,
                posicaoPorSlot, principalPorJogador, overalls);
    }

    @Override
    @Transactional
    public PlanoVigente garantirPlanoVigente(long clubeId, long temporadaId) {
        var vigente = planos.findByClubeIdAndTemporadaIdAndVigenteTrue(clubeId, temporadaId);
        if (vigente.isPresent()) {
            return montar(vigente.get(), temporadaId);
        }

        var elenco = jogadorService.listarElencoParaEscalacao(clubeId, temporadaId);
        var principalPorJogador = elenco.stream().collect(Collectors.toMap(
                JogadorDoElenco::jogadorId, JogadorDoElenco::posicaoPrincipalId));
        var overalls = tabelaDe(principalPorJogador.keySet(), temporadaId);
        var goleiro = idDaPosicaoDeGoleiro();

        var disponiveis = elenco.stream()
                .map(jogador -> new JogadorDisponivel(jogador.jogadorId(),
                        jogador.posicaoPrincipalId(), "BASE".equals(jogador.categoria())))
                .toList();

        // Sem vínculo ativo o clube escala mesmo assim, com repertório mínimo: um clube
        // sem treinador continua entrando em campo.
        var tatica = treinadorService.buscarPerfilDoClube(clubeId, temporadaId)
                .map(PerfilDeTreinador::tatica)
                .orElse(0);

        var novo = EscaladorAutomatico.montar(catalogoDeFormacoes(), tatica, disponiveis,
                        overalls, goleiro, reputacaoDoClube(clubeId), mediaDeReputacao())
                .orElseThrow(() -> new EscalacaoInvalidaException(
                        "elenco do clube %d não fecha um time na temporada %d"
                                .formatted(clubeId, temporadaId)));

        var planoId = gravar(clubeId, temporadaId, novo, OrigemDoPlano.AUTOMATICO,
                posicaoPorSlotDe(novo.formacaoId()), principalPorJogador, overalls);
        return montar(planos.findById(planoId).orElseThrow(), temporadaId);
    }

    private List<FormacaoCandidata> catalogoDeFormacoes() {
        return formacoes.findAllByOrderByOrdemAsc().stream()
                .map(formacao -> new FormacaoCandidata(formacao.getId(), formacao.getOrdem(),
                        formacaoSlots.findByFormacaoIdOrderByOrdemAsc(formacao.getId()).stream()
                                .map(FormacaoSlot::getPosicaoId)
                                .toList()))
                .toList();
    }

    private int reputacaoDoClube(long clubeId) {
        return jdbcTemplate.queryForObject(
                "select reputacao from clube where id = ?", Integer.class, clubeId);
    }

    /**
     * Média global, e não da divisão: saber a divisão exigiria depender de
     * {@code competicao}, e um clube pode disputar mais de uma competição.
     */
    private double mediaDeReputacao() {
        var media = jdbcTemplate.queryForObject(
                "select avg(reputacao) from clube", Double.class);
        return media == null ? 0.0 : media;
    }

    @Override
    public Optional<PlanoVigente> buscarPlanoVigente(long clubeId, long temporadaId) {
        return planos.findByClubeIdAndTemporadaIdAndVigenteTrue(clubeId, temporadaId)
                .map(plano -> montar(plano, temporadaId));
    }

    private PlanoVigente montar(PlanoTatico plano, long temporadaId) {
        var linhas = escalacoes.findByPlanoId(plano.getId());
        var jogadorIds = linhas.stream().map(PlanoEscalacao::getJogadorId).toList();

        var atuais = avaliacaoService.listarOveralls(jogadorIds, temporadaId).stream()
                .collect(Collectors.toMap(
                        overall -> new ChaveDeAptidao(overall.jogadorId(), overall.posicaoId()),
                        OverallDeJogador::overall));

        var comVinculo = jogadorService
                .listarElencoParaEscalacao(plano.getClubeId(), temporadaId).stream()
                .map(JogadorDoElenco::jogadorId)
                .collect(Collectors.toSet());

        var titulares = linhas.stream()
                .filter(linha -> linha.getPapel() == Papel.TITULAR)
                .sorted(Comparator.comparingInt(PlanoEscalacao::getSlotOrdem))
                .map(linha -> escalado(linha, atuais, comVinculo))
                .toList();

        var banco = linhas.stream()
                .filter(linha -> linha.getPapel() == Papel.RESERVA)
                .sorted(Comparator.comparingInt(PlanoEscalacao::getOrdemBanco))
                .map(linha -> escalado(linha, atuais, comVinculo))
                .toList();

        var formacao = formacoes.findById(plano.getFormacaoId()).orElseThrow();

        return new PlanoVigente(plano.getId(), plano.getClubeId(), plano.getTemporadaId(),
                plano.getVersao(), plano.getOrigem(), formacao.getId(), formacao.getCodigo(),
                plano.getMentalidade(), plano.getRitmo(), plano.getLinhaDefensiva(),
                plano.getPressao(), plano.getLargura(), plano.getCapitaoId(), titulares, banco);
    }

    private record ChaveDeAptidao(Long jogadorId, Long posicaoId) {
    }

    private static Escalado escalado(PlanoEscalacao linha,
                                     Map<ChaveDeAptidao, Integer> atuais,
                                     Set<Long> comVinculo) {
        var chave = new ChaveDeAptidao(linha.getJogadorId(), linha.getPosicaoId());
        return new Escalado(linha.getJogadorId(), linha.getPosicaoId(),
                linha.getSlotOrdem(), linha.getOrdemBanco(),
                linha.getAptidaoNoMomento(),
                // Sem linha atual — overall apagado —, o congelado é a melhor resposta.
                atuais.getOrDefault(chave, linha.getAptidaoNoMomento()),
                !comVinculo.contains(linha.getJogadorId()));
    }

    /**
     * Grava uma versão nova e a torna vigente.
     *
     * <p>O {@code flush} entre desmarcar o vigente e inserir o novo é obrigatório: sem
     * ele o Hibernate pode emitir o insert antes do update, e {@code uq_plano_vigente}
     * reprova.
     */
    private long gravar(long clubeId, long temporadaId, NovoPlano novo, OrigemDoPlano origem,
                        List<Long> posicaoPorSlot, Map<Long, Long> principalPorJogador,
                        TabelaDeOveralls overalls) {
        planos.findByClubeIdAndTemporadaIdAndVigenteTrue(clubeId, temporadaId)
                .ifPresent(anterior -> anterior.setVigente(false));
        planos.flush();

        var plano = new PlanoTatico();
        plano.setClubeId(clubeId);
        plano.setTemporadaId(temporadaId);
        plano.setVersao(planos.findMaxVersao(clubeId, temporadaId) + 1);
        plano.setVigente(true);
        plano.setFormacaoId(novo.formacaoId());
        plano.setCapitaoId(novo.capitaoId());
        plano.setOrigem(origem);
        plano.setMentalidade(novo.mentalidade());
        plano.setRitmo(novo.ritmo());
        plano.setLinhaDefensiva(novo.linhaDefensiva());
        plano.setPressao(novo.pressao());
        plano.setLargura(novo.largura());
        planos.saveAndFlush(plano);

        var linhas = new ArrayList<PlanoEscalacao>();
        for (var titular : novo.titulares()) {
            var posicaoId = posicaoPorSlot.get(titular.slotOrdem() - 1);
            linhas.add(linha(plano.getId(), titular.jogadorId(), Papel.TITULAR,
                    titular.slotOrdem(), null, posicaoId, overalls));
        }
        for (int i = 0; i < novo.banco().size(); i++) {
            var jogadorId = novo.banco().get(i);
            linhas.add(linha(plano.getId(), jogadorId, Papel.RESERVA,
                    null, i + 1, principalPorJogador.get(jogadorId), overalls));
        }
        escalacoes.saveAll(linhas);

        return plano.getId();
    }

    private static PlanoEscalacao linha(long planoId, long jogadorId, Papel papel,
                                        Integer slotOrdem, Integer ordemBanco, long posicaoId,
                                        TabelaDeOveralls overalls) {
        var linha = new PlanoEscalacao();
        linha.setPlanoId(planoId);
        linha.setJogadorId(jogadorId);
        linha.setPapel(papel);
        linha.setSlotOrdem(slotOrdem);
        linha.setOrdemBanco(ordemBanco);
        linha.setPosicaoId(posicaoId);
        linha.setAptidaoNoMomento(overalls.overall(jogadorId, posicaoId));
        return linha;
    }

    private List<Long> posicaoPorSlotDe(long formacaoId) {
        var posicoes = formacaoSlots.findByFormacaoIdOrderByOrdemAsc(formacaoId).stream()
                .map(FormacaoSlot::getPosicaoId)
                .toList();
        if (posicoes.size() != ConstantesDeTatica.TITULARES) {
            throw new RecursoNaoEncontradoException(
                    "formação %d não existe no catálogo".formatted(formacaoId));
        }
        return posicoes;
    }

    TabelaDeOveralls tabelaDe(Set<Long> jogadorIds, long temporadaId) {
        var porJogador = new HashMap<Long, Map<Long, Integer>>();
        for (var linha : avaliacaoService.listarOveralls(jogadorIds, temporadaId)) {
            porJogador.computeIfAbsent(linha.jogadorId(), id -> new HashMap<>())
                    .put(linha.posicaoId(), linha.overall());
        }
        return new TabelaDeOveralls(Map.copyOf(porJogador));
    }

    /**
     * O id de {@code GOL} vem do banco: {@code posicao} é catálogo fixo, mas o id é
     * gerado. Lido por {@code JdbcTemplate} porque {@code jogador.repository} é interno
     * ao módulo dono — e {@link OverallDeJogador} não carrega código de posição.
     */
    private long idDaPosicaoDeGoleiro() {
        return jdbcTemplate.queryForObject(
                "select id from posicao where codigo = 'GOL'", Long.class);
    }
}

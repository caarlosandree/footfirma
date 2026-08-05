package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.CalendarioService;
import br.com.api.footfirma.calendario.domain.Confronto;
import br.com.api.footfirma.calendario.domain.Jogo;
import br.com.api.footfirma.calendario.domain.Rodada;
import br.com.api.footfirma.calendario.dto.ConfrontoDetalhe;
import br.com.api.footfirma.calendario.dto.EdicaoParaGerar;
import br.com.api.footfirma.calendario.dto.JogoAgendado;
import br.com.api.footfirma.calendario.dto.PerfilDeCalendario;
import br.com.api.footfirma.calendario.dto.RegrasDeDesempate;
import br.com.api.footfirma.calendario.dto.RelatorioDeCalendario;
import br.com.api.footfirma.calendario.dto.ResultadoDoJogo;
import br.com.api.footfirma.calendario.dto.RodadaDetalhe;
import br.com.api.footfirma.calendario.repository.ConfrontoRepository;
import br.com.api.footfirma.calendario.repository.JogoRepository;
import br.com.api.footfirma.calendario.repository.RodadaRepository;
import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.FaseResumo;
import br.com.api.footfirma.shared.exception.CalendarioInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CalendarioServiceImpl implements CalendarioService {

    private final RodadaRepository rodadas;
    private final ConfrontoRepository confrontos;
    private final JogoRepository jogos;
    private final CompeticaoService competicaoService;
    private final ClubeService clubeService;

    @Override
    @Transactional
    public RelatorioDeCalendario gerarTemporada(long temporadaId, long semente,
                                                List<EdicaoParaGerar> edicoes) {
        var agenda = new AgendaEmMemoria();
        var total = Contagem.VAZIA;

        // A ordenação é a razão de este método receber a lista inteira em vez de uma
        // edição por chamada.
        var emOrdem = edicoes.stream()
                .sorted(Comparator.comparingInt(EdicaoParaGerar::precedencia))
                .toList();

        for (var edicao : emOrdem) {
            var janela = competicaoService.buscarJanelaDaEdicao(edicao.edicaoId())
                    .orElseThrow(() -> new CalendarioInvalidoException(
                            "Edição %d não existe ou não tem janela".formatted(edicao.edicaoId())));
            var fases = competicaoService.listarFasesDaEdicao(edicao.edicaoId());
            if (fases.isEmpty()) {
                throw new CalendarioInvalidoException(
                        "Edição %d não tem fase".formatted(edicao.edicaoId()));
            }

            // Uma semente por edição: mexer numa liga não desloca a outra, como o gerador
            // de mundo já faz por clube.
            var aleatorio = new SplittableRandom(semente + edicao.edicaoId());
            var diasDaJanela = (int) ChronoUnit.DAYS.between(janela.inicio(), janela.fim());

            for (var fase : fases) {
                total = total.mais(gerarFase(edicao, fase, janela.inicio(), diasDaJanela,
                        aleatorio, agenda));
            }
        }
        return new RelatorioDeCalendario(total.rodadas(), total.confrontos(), total.jogos());
    }

    private Contagem gerarFase(EdicaoParaGerar edicao, FaseResumo fase,
                               java.time.LocalDate inicio, int diasDaJanela,
                               SplittableRandom aleatorio, AgendaEmMemoria agenda) {
        var participantes = competicaoService.listarParticipantesDaFase(fase.id());
        if (participantes.isEmpty()) {
            throw new CalendarioInvalidoException(
                    "Fase %d não tem participante".formatted(fase.id()));
        }
        semear(participantes, agenda);

        return switch (fase.tipo()) {
            case "PONTOS_CORRIDOS" -> gerarPontosCorridos(
                    edicao, fase, participantes, inicio, diasDaJanela, aleatorio, agenda);
            // Grupos e eliminatória chegam no slice B. Falhar alto é melhor do que gerar
            // silenciosamente uma fase vazia que ninguém vai notar até a partida procurar
            // um jogo que não existe.
            default -> throw new CalendarioInvalidoException(
                    "Tipo de fase ainda não gerado: " + fase.tipo());
        };
    }

    private Contagem gerarPontosCorridos(EdicaoParaGerar edicao, FaseResumo fase,
                                         List<Long> participantes, java.time.LocalDate inicio,
                                         int diasDaJanela, SplittableRandom aleatorio,
                                         AgendaEmMemoria agenda) {
        var tabela = TabelaDeBerger.gerar(participantes.size(), fase.jogosPorConfronto() == 2);
        var tipos = TipadorDeRodadas.tipar(tabela.size(), diasDaJanela);
        var alvos = TipadorDeRodadas.datasAlvo(inicio, tipos);

        var confrontosCriados = 0;
        var jogosCriados = 0;

        for (var i = 0; i < tabela.size(); i++) {
            var rodada = gravarRodada(fase.id(), i + 1, tipos.get(i), alvos.get(i));
            var ordemDeDias = SorteadorDeDia.ordenarPorPeso(
                    edicao.perfil().pesos().get(tipos.get(i)), aleatorio);

            for (var par : tabela.get(i)) {
                var mandanteId = participantes.get(par.mandante());
                var visitanteId = participantes.get(par.visitante());

                var confronto = confrontos.save(
                        new Confronto(fase.id(), ++confrontosCriados, null));
                confronto.setClubeAId(mandanteId);
                confronto.setClubeBId(visitanteId);

                var data = AlocadorDeDatas.alocar(
                        rodada.getJanelaInicio(), rodada.getJanelaFim(), ordemDeDias,
                        agenda.de(mandanteId), agenda.de(visitanteId),
                        edicao.perfil().descansoMinimoEmDias());

                var jogo = new Jogo(confronto, rodada, 1);
                jogo.setMandanteId(mandanteId);
                jogo.setVisitanteId(visitanteId);
                jogo.setEstadioId(estadioDe(mandanteId));
                jogo.setDataJogo(data);
                jogos.save(jogo);

                agenda.ocupar(mandanteId, data);
                agenda.ocupar(visitanteId, data);
                jogosCriados++;
            }
        }
        return new Contagem(tabela.size(), confrontosCriados, jogosCriados);
    }

    private Rodada gravarRodada(Long faseId, int ordem,
                                br.com.api.footfirma.calendario.dto.TipoDeRodada tipo,
                                java.time.LocalDate alvo) {
        return rodadas.save(new Rodada(faseId, ordem, tipo, alvo,
                alvo.minusDays(ConstantesDeCalendario.RAIO_DA_JANELA_EM_DIAS),
                alvo.plusDays(ConstantesDeCalendario.RAIO_DA_JANELA_EM_DIAS)));
    }

    /**
     * Semeia a agenda com o que já está gravado para estes clubes em qualquer fase.
     *
     * <p>É o que faz a competição gerada depois enxergar a gerada antes — sem isso, cada
     * edição respeitaria só o próprio descanso e um clube em duas competições acabaria com
     * dois jogos no mesmo dia.
     */
    private void semear(List<Long> participantes, AgendaEmMemoria agenda) {
        var todasAsFases = rodadas.findAll().stream().map(Rodada::getFaseId).distinct().toList();
        if (todasAsFases.isEmpty()) {
            return;
        }
        participantes.forEach(clubeId ->
                agenda.semear(clubeId, jogos.findDatasDoClube(clubeId, todasAsFases)));
    }

    private Long estadioDe(long clubeId) {
        return clubeService.buscarEstadioPorClubeId(clubeId)
                .orElseThrow(() -> new CalendarioInvalidoException(
                        "Clube %d não tem estádio para mandar o jogo".formatted(clubeId)));
    }

    @Override
    @Transactional
    public void registrarResultado(long jogoId, ResultadoDoJogo resultado) {
        throw new UnsupportedOperationException("Chega na Task 7");
    }

    @Override
    public Optional<JogoAgendado> buscarJogo(long jogoId) {
        return jogos.findById(jogoId).map(jogo -> paraAgendado(jogo, regrasPorFase()));
    }

    @Override
    public List<JogoAgendado> listarAgendaDoClube(long clubeId, long temporadaId) {
        var fases = fasesDaTemporada(temporadaId);
        if (fases.isEmpty()) {
            return List.of();
        }
        var regras = regrasPorFase();
        return jogos.findAgendaDoClube(clubeId, fases).stream()
                .map(jogo -> paraAgendado(jogo, regras))
                .toList();
    }

    @Override
    public List<RodadaDetalhe> listarRodadas(long edicaoId) {
        var regras = regrasPorFase();
        return competicaoService.listarFasesDaEdicao(edicaoId).stream()
                .flatMap(fase -> rodadas.findByFaseIdOrderByOrdemAsc(fase.id()).stream())
                .map(rodada -> new RodadaDetalhe(
                        rodada.getId(), rodada.getOrdem(), rodada.getTipo(), rodada.getDataAlvo(),
                        jogos.findByRodadaIdOrderByDataJogoAsc(rodada.getId()).stream()
                                .map(jogo -> paraAgendado(jogo, regras))
                                .toList()))
                .toList();
    }

    @Override
    public List<ConfrontoDetalhe> listarChaveamento(long faseId) {
        var regras = regrasPorFase();
        return confrontos.findByFaseIdOrderByOrdemAsc(faseId).stream()
                .map(confronto -> new ConfrontoDetalhe(
                        confronto.getId(), confronto.getOrdem(), confronto.getChave(),
                        confronto.getClubeAId(), confronto.getClubeBId(),
                        confronto.getVencedorClubeId(),
                        jogos.findByConfrontoIdOrderByOrdemNoConfrontoAsc(confronto.getId()).stream()
                                .map(jogo -> paraAgendado(jogo, regras))
                                .toList()))
                .toList();
    }

    /** Todas as fases de todas as edições da temporada, para filtrar jogos por temporada. */
    private List<Long> fasesDaTemporada(long temporadaId) {
        return competicaoService.listarEdicoesDaTemporada(temporadaId).stream()
                .flatMap(edicaoId -> competicaoService.listarFasesDaEdicao(edicaoId).stream())
                .map(FaseResumo::id)
                .toList();
    }

    /**
     * As regras de desempate de cada fase que tem rodada gravada.
     *
     * <p>Montado de uma vez porque {@code JogoAgendado} carrega as regras e uma listagem
     * de 380 jogos consultaria a mesma fase 380 vezes.
     */
    private Map<Long, FaseResumo> regrasPorFase() {
        var porFase = new HashMap<Long, FaseResumo>();
        rodadas.findAll().stream().map(Rodada::getFaseId).distinct()
                .forEach(faseId -> competicaoService.buscarFase(faseId)
                        .ifPresent(fase -> porFase.put(faseId, fase)));
        return porFase;
    }

    private JogoAgendado paraAgendado(Jogo jogo, Map<Long, FaseResumo> regrasPorFase) {
        var fase = regrasPorFase.get(jogo.getRodada().getFaseId());
        var decisivo = fase == null || jogo.getOrdemNoConfronto().equals(fase.jogosPorConfronto());
        var desempate = fase == null
                ? new RegrasDeDesempate(false, false, false, true)
                : new RegrasDeDesempate(fase.temGolFora(), fase.temProrrogacao(),
                        fase.temPenaltis(), decisivo);

        return new JogoAgendado(jogo.getId(), jogo.getConfronto().getId(), jogo.getRodada().getId(),
                jogo.getOrdemNoConfronto(), jogo.getMandanteId(), jogo.getVisitanteId(),
                jogo.getEstadioId(), jogo.getDataJogo(), jogo.getSituacao(),
                jogo.getGolsMandante(), jogo.getGolsVisitante(), desempate);
    }
}

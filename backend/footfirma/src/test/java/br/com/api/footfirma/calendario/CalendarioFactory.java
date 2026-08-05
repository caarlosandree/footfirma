package br.com.api.footfirma.calendario;

import br.com.api.footfirma.calendario.dto.PerfilDeCalendario;
import br.com.api.footfirma.calendario.dto.TipoDeRodada;
import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Semeia temporada, competição, edição, fase e participantes para os testes de calendário.
 *
 * <p>Passa pelas portas públicas dos módulos donos, e não por insert direto: o que o
 * gerador lê precisa existir do jeito que o gerador o encontra em produção.
 */
@Component
@RequiredArgsConstructor
public class CalendarioFactory {

    /** A janela do mundo gerado: 246 dias, ou 35 semanas. */
    public static final LocalDate INICIO = LocalDate.of(2026, 4, 4);
    public static final LocalDate FIM = LocalDate.of(2026, 12, 6);

    /** Os pesos da Série A, medidos na tabela real da CBF. Nada na sexta. */
    public static final PerfilDeCalendario PERFIL = new PerfilDeCalendario(Map.of(
            TipoDeRodada.FIM_DE_SEMANA, Map.of(
                    DayOfWeek.SUNDAY, 45, DayOfWeek.SATURDAY, 40, DayOfWeek.MONDAY, 15),
            TipoDeRodada.MEIO_DE_SEMANA, Map.of(
                    DayOfWeek.WEDNESDAY, 60, DayOfWeek.THURSDAY, 40)), 3);

    private final TemporadaService temporadaService;
    private final CompeticaoService competicaoService;
    private final ClubeService clubeService;
    private final GeografiaService geografiaService;

    public record Cenario(long temporadaId, long edicaoId, long faseId, List<Long> clubeIds) {
    }

    public Cenario criar(String slug, int participantes, String tipo, int jogosPorConfronto) {
        return criar(slug, participantes, tipo, jogosPorConfronto, false, false, false);
    }

    /**
     * @param tipo              PONTOS_CORRIDOS, GRUPOS ou ELIMINATORIA
     * @param jogosPorConfronto 1 ou 2 — em pontos corridos, 2 significa turno e returno
     */
    public Cenario criar(String slug, int participantes, String tipo, int jogosPorConfronto,
                         boolean temGolFora, boolean temProrrogacao, boolean temPenaltis) {
        var temporadaId = temporadaService
                .sincronizar(new DadosDeTemporada("2026", 2026, 2026)).id();
        var paisId = geografiaService.buscarPaisPorIso("BRA").orElseThrow().id();
        var estadoId = geografiaService.buscarEstadoPorUf("BRA", "SP").orElseThrow().id();

        var competicaoId = competicaoService.sincronizarCompeticao(new DadosDeCompeticao(
                slug, "Competição " + slug, paisId, "LIGA", 1, "MASCULINO")).id();
        var edicaoId = competicaoService.sincronizarEdicao(new DadosDeEdicao(
                competicaoId, temporadaId, "Edição " + slug, INICIO, FIM)).id();
        competicaoService.sincronizarFase(new DadosDeFase(
                edicaoId, 1, "Fase única", tipo, jogosPorConfronto,
                temGolFora, temProrrogacao, temPenaltis));

        var clubeIds = new ArrayList<Long>(participantes);
        for (var i = 1; i <= participantes; i++) {
            var estadioId = clubeService.sincronizarEstadio(new DadosDeEstadio(
                    "Estádio %s %d".formatted(slug, i), "Cidade %s %d".formatted(slug, i),
                    estadoId, 30_000, 1950)).id();
            var clubeId = clubeService.sincronizarClube(new DadosDeClube(
                    "%s-clube-%d".formatted(slug, i), "Clube %s %d".formatted(slug, i),
                    "C%d".formatted(i), "Apelido %d".formatted(i), 1950,
                    paisId, estadoId, estadioId, "#000000", "#FFFFFF",
                    50, 50, 50, estadoId)).id();
            clubeIds.add(clubeId);
            competicaoService.sincronizarParticipante(
                    new DadosDeParticipante(edicaoId, clubeId, null));
        }

        var faseId = competicaoService.listarFasesDaEdicao(edicaoId).getFirst().id();
        return new Cenario(temporadaId, edicaoId, faseId, List.copyOf(clubeIds));
    }
}

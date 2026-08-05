package br.com.api.footfirma.calendario.web;

import br.com.api.footfirma.calendario.CalendarioService;
import br.com.api.footfirma.calendario.dto.JogoAgendado;
import br.com.api.footfirma.calendario.dto.RodadaDetalhe;
import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Leitura do calendário. Sem escrita, pelo ADR de catálogo read-only —
 * {@code registrarResultado} é porta de módulo, e quem a chama é {@code partida}.
 *
 * <p>Os dois paths estendem os recursos que já existem ({@code /competicoes} e
 * {@code /clubes}) em vez de abrir raízes novas: rodada é subrecurso da edição, e a edição
 * só existe dentro de uma competição.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Calendário", description = "Rodadas de uma edição e agenda de um clube")
class CalendarioController {

    private final CalendarioService calendarioService;
    private final CompeticaoService competicaoService;
    private final ClubeService clubeService;
    private final TemporadaService temporadaService;

    @GetMapping("/api/v1/competicoes/{slug}/edicoes/{temporada}/rodadas")
    @Operation(summary = "Lista as rodadas de uma edição, em ordem, com os jogos de cada uma")
    List<RodadaDetalhe> listarRodadas(@PathVariable String slug, @PathVariable String temporada) {
        var edicaoId = competicaoService.buscarEdicao(slug, temporada)
                .map(EdicaoDetalhe::id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Edição não encontrada: " + slug + " " + temporada));
        return calendarioService.listarRodadas(edicaoId);
    }

    @GetMapping("/api/v1/clubes/{slug}/jogos")
    @Operation(summary = "Lista os jogos de um clube numa temporada, em qualquer competição")
    List<JogoAgendado> listarJogosDoClube(@PathVariable String slug,
                                          @RequestParam(defaultValue = "2026") String temporada) {
        var clubeId = clubeService.buscarPorSlug(slug)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Clube não encontrado: " + slug))
                .id();
        var temporadaId = temporadaService.buscarPorLabel(temporada)
                .map(TemporadaResumo::id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Temporada não encontrada: " + temporada));
        return calendarioService.listarAgendaDoClube(clubeId, temporadaId);
    }
}

package br.com.api.footfirma.jogador.web;

import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jogadores")
@RequiredArgsConstructor
@Tag(name = "Jogadores", description = "Consulta ao catálogo de jogadores")
class JogadorController {

    private final JogadorService jogadorService;

    @GetMapping("/{slug}")
    @Operation(summary = "Busca um jogador pelo slug, com atributos da temporada informada")
    JogadorDetalhe buscar(@PathVariable String slug, @RequestParam String temporada) {
        return jogadorService.buscarPorSlug(slug, temporada)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Jogador não encontrado: " + slug));
    }

    // O elenco não é paginado: a lista tem limite natural de algumas dezenas de
    // jogadores, e paginar o que nunca cresce só complica o cliente.
    @GetMapping
    @Operation(summary = "Lista o elenco de um clube em uma temporada")
    List<JogadorResumo> listarElenco(@RequestParam String clube, @RequestParam String temporada) {
        return jogadorService.listarElenco(clube, temporada);
    }
}

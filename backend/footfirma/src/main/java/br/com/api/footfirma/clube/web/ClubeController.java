package br.com.api.footfirma.clube.web;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clubes")
@RequiredArgsConstructor
@Tag(name = "Clubes", description = "Consulta ao catálogo de clubes")
class ClubeController {

    private final ClubeService clubeService;

    @GetMapping
    @Operation(summary = "Lista clubes paginados, ordenados por nome")
    Page<ClubeResumo> listar(@ParameterObject @PageableDefault(size = 20) Pageable paginacao) {
        return clubeService.listar(paginacao);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Busca um clube pelo slug")
    ClubeDetalhe buscar(@PathVariable String slug) {
        return clubeService.buscarPorSlug(slug)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Clube não encontrado: " + slug));
    }
}

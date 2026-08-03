package br.com.api.footfirma.competicao.web;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
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
@RequestMapping("/api/v1/competicoes")
@RequiredArgsConstructor
@Tag(name = "Competições", description = "Consulta ao catálogo de competições e suas edições")
class CompeticaoController {

    private final CompeticaoService competicaoService;

    @GetMapping
    @Operation(summary = "Lista as competições de um país, da divisão mais alta para a mais baixa")
    List<CompeticaoResumo> listar(@RequestParam(defaultValue = "BRA") String pais) {
        return competicaoService.listarPorPais(pais);
    }

    @GetMapping("/{slug}/edicoes/{temporada}")
    @Operation(summary = "Busca a edição de uma competição em uma temporada, com fases e regras de classificação")
    EdicaoDetalhe buscarEdicao(@PathVariable String slug, @PathVariable String temporada) {
        return competicaoService.buscarEdicao(slug, temporada)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Edição não encontrada: " + slug + " " + temporada));
    }
}

package br.com.api.footfirma.avaliacao.web;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.dto.AvaliacoesDoJogador;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Avaliações", description = "Overall calculado por posição")
class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    // Sub-recurso de jogador servido por este módulo, e não pelo JogadorController:
    // embutir overall no catálogo inverteria a dependência entre os módulos.
    @GetMapping("/jogadores/{slug}/overall")
    @Operation(summary = "Overall de um jogador nas nove posições, na temporada informada")
    AvaliacoesDoJogador buscar(@PathVariable String slug, @RequestParam String temporada) {
        return avaliacaoService.buscarPorSlug(slug, temporada)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Sem overall para o jogador " + slug + " na temporada " + temporada));
    }

    // Recurso próprio: /jogadores/ranking colidiria com /jogadores/{slug}.
    @GetMapping("/rankings")
    @Operation(summary = "Ranking de jogadores por overall em uma posição")
    Page<ItemDeRanking> ranquear(@RequestParam String temporada,
                                 @RequestParam String posicao,
                                 @RequestParam(required = false) Integer overallMinimo,
                                 Pageable pageable) {
        return avaliacaoService.ranquear(temporada, posicao, overallMinimo, pageable);
    }
}

package br.com.api.footfirma.avaliacao.web;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.dto.AvaliacaoDePosicao;
import br.com.api.footfirma.avaliacao.dto.AvaliacoesDoJogador;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import br.com.api.footfirma.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig real: sem ele o slice responde 401 e o teste provaria a segurança
// em vez do contrato. Afrouxar a segurança no teste mascararia o problema.
@WebMvcTest(AvaliacaoController.class)
@Import(SecurityConfig.class)
class AvaliacaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AvaliacaoService avaliacaoService;

    @Test
    void deveRetornarOverallDoJogadorNasPosicoes() throws Exception {
        when(avaliacaoService.buscarPorSlug("raphael-veiga", "2025"))
                .thenReturn(Optional.of(new AvaliacoesDoJogador("raphael-veiga", "2025", List.of(
                        new AvaliacaoDePosicao("MEA", 84, 1),
                        new AvaliacaoDePosicao("MEC", 81, 1)))));

        mockMvc.perform(get("/api/v1/jogadores/raphael-veiga/overall?temporada=2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("raphael-veiga"))
                .andExpect(jsonPath("$.avaliacoes[0].posicao").value("MEA"))
                .andExpect(jsonPath("$.avaliacoes[0].overall").value(84))
                .andExpect(jsonPath("$.avaliacoes[0].perfilVersao").value(1));
    }

    @Test
    void deveRetornar404QuandoOJogadorNaoTemOverallNaTemporada() throws Exception {
        when(avaliacaoService.buscarPorSlug("inexistente", "2025")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jogadores/inexistente/overall?temporada=2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void deveRetornarRankingPaginado() throws Exception {
        var item = new ItemDeRanking("gabriel-barbosa", "Gabigol", 29, "ATA", "ATA", 84);
        when(avaliacaoService.ranquear(eq("2025"), eq("ATA"), eq(80), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/rankings?temporada=2025&posicao=ATA&overallMinimo=80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("gabriel-barbosa"))
                .andExpect(jsonPath("$.content[0].posicaoAvaliada").value("ATA"))
                .andExpect(jsonPath("$.content[0].overall").value(84));
    }

    @Test
    void deveAceitarRankingSemOverallMinimo() throws Exception {
        when(avaliacaoService.ranquear(eq("2025"), eq("ZAG"), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/rankings?temporada=2025&posicao=ZAG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}

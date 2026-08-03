package br.com.api.footfirma.clube.web;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import br.com.api.footfirma.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig é importado de propósito: @WebMvcTest não varre @Configuration,
// e sem ele o teste rodaria contra o default do Spring Security, não contra as
// regras reais de liberação das rotas de leitura.
@WebMvcTest(ClubeController.class)
@Import(SecurityConfig.class)
class ClubeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ClubeService clubeService;

    @Test
    void deveListarClubesPaginados() throws Exception {
        when(clubeService.listar(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new ClubeResumo(1L, "gremio", "Grêmio", 82))));

        mockMvc.perform(get("/api/v1/clubes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("gremio"))
                .andExpect(jsonPath("$.content[0].reputacao").value(82));
    }

    @Test
    void deveRetornarDetalheDoClube() throws Exception {
        when(clubeService.buscarPorSlug("gremio")).thenReturn(Optional.of(new ClubeDetalhe(
                1L, "gremio", "Grêmio Foot-Ball Porto Alegrense", "Grêmio", "Tricolor",
                1903, "Arena do Grêmio", 55662, 82, 75)));

        mockMvc.perform(get("/api/v1/clubes/gremio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeOficial").value("Grêmio Foot-Ball Porto Alegrense"))
                .andExpect(jsonPath("$.capacidadeEstadio").value(55662));
    }

    @Test
    void deveRetornar404QuandoSlugNaoExiste() throws Exception {
        when(clubeService.buscarPorSlug("inexistente")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/clubes/inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }
}

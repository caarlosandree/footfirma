package br.com.api.footfirma.competicao.web;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.competicao.dto.FaseResumo;
import br.com.api.footfirma.competicao.dto.RegraClassificacaoResumo;
import br.com.api.footfirma.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompeticaoController.class)
@Import(SecurityConfig.class)
class CompeticaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompeticaoService competicaoService;

    @Test
    void deveListarCompeticoesDoPais() throws Exception {
        when(competicaoService.listarPorPais("BRA")).thenReturn(List.of(
                new CompeticaoResumo(1L, "brasileirao-serie-a", "Brasileirão Série A", "LIGA", 1),
                new CompeticaoResumo(2L, "copa-do-brasil", "Copa do Brasil", "COPA", null)));

        mockMvc.perform(get("/api/v1/competicoes?pais=BRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("brasileirao-serie-a"))
                .andExpect(jsonPath("$[1].tipo").value("COPA"));
    }

    @Test
    void deveRetornarEdicaoComFasesERegras() throws Exception {
        when(competicaoService.buscarEdicao("brasileirao-serie-a", "2025")).thenReturn(Optional.of(
                new EdicaoDetalhe(1L, "Brasileirão Série A 2025", "Brasileirão Série A",
                        LocalDate.of(2025, 3, 29), LocalDate.of(2025, 12, 21),
                        List.of(new FaseResumo(1, "Fase única", "PONTOS_CORRIDOS", 1, false, false)),
                        List.of(new RegraClassificacaoResumo(17, 20, "REBAIXAMENTO")))));

        mockMvc.perform(get("/api/v1/competicoes/brasileirao-serie-a/edicoes/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases[0].tipo").value("PONTOS_CORRIDOS"))
                .andExpect(jsonPath("$.regras[0].tipo").value("REBAIXAMENTO"))
                .andExpect(jsonPath("$.regras[0].posicaoInicio").value(17));
    }

    @Test
    void deveRetornar404QuandoEdicaoNaoExiste() throws Exception {
        when(competicaoService.buscarEdicao("brasileirao-serie-a", "1998")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/competicoes/brasileirao-serie-a/edicoes/1998"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }
}

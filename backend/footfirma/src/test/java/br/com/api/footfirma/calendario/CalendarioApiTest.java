package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.calendario.dto.EdicaoParaGerar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalendarioApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    CalendarioFactory factory;

    CalendarioFactory.Cenario cenario;

    @BeforeAll
    void gerar() {
        cenario = factory.criar("api-liga", 4, "PONTOS_CORRIDOS", 2);
        calendarioService.gerarTemporada(cenario.temporadaId(), 3L,
                List.of(new EdicaoParaGerar(cenario.edicaoId(), 1, CalendarioFactory.PERFIL)));
    }

    @Test
    void deveListarAsRodadasDaEdicao() throws Exception {
        mockMvc.perform(get("/api/v1/competicoes/api-liga/edicoes/2026/rodadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].ordem").value(1))
                .andExpect(jsonPath("$[0].jogos", hasSize(2)));
    }

    @Test
    void deveDevolver404QuandoACompeticaoNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/competicoes/inexistente/edicoes/2026/rodadas"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveListarOsJogosDoClube() throws Exception {
        mockMvc.perform(get("/api/v1/clubes/api-liga-clube-1/jogos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].dataJogo").isNotEmpty());
    }

    @Test
    void deveDevolver404QuandoOClubeNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/clubes/nao-existe/jogos"))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoDeveAceitarEscritaNoCalendario() throws Exception {
        // O ADR de catálogo read-only vale aqui: nenhum verbo de escrita é exposto. Hoje a
        // resposta é 403 e não 405 porque o SecurityConfig barra antes do roteamento — o
        // que se trava é que a escrita não passa, não o código exato.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/clubes/api-liga-clube-1/jogos"))
                .andExpect(status().is4xxClientError());
    }
}

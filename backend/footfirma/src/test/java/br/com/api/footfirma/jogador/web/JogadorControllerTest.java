package br.com.api.footfirma.jogador.web;

import br.com.api.footfirma.config.SecurityConfig;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
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

@WebMvcTest(JogadorController.class)
@Import(SecurityConfig.class)
class JogadorControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JogadorService jogadorService;

    @Test
    void deveRetornarDetalheDoJogadorComAtributos() throws Exception {
        var atributos = new AtributosJogador(
                78, 70, 82, 66, 80, 84, 83, 79, 85,
                81, 62, 86, 88, 45, 40, 12, 11, 10, 87, "IMPORTADO");
        when(jogadorService.buscarPorSlug("raphael-veiga", "2025")).thenReturn(Optional.of(new JogadorDetalhe(
                1L, "raphael-veiga", "Raphael Cavalcante Veiga", "Raphael Veiga",
                LocalDate.of(1995, 6, 19), 31, 177, 74, "ESQUERDO", "MEA", "REAL",
                atributos, List.of("ESPECIALISTA_FALTA", "ESPECIALISTA_PENALTI"))));

        mockMvc.perform(get("/api/v1/jogadores/raphael-veiga?temporada=2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value("Raphael Veiga"))
                .andExpect(jsonPath("$.atributos.falta").value(86))
                .andExpect(jsonPath("$.caracteristicas[0]").value("ESPECIALISTA_FALTA"));
    }

    @Test
    void deveListarElencoDoClubeNaTemporada() throws Exception {
        when(jogadorService.listarElenco("palmeiras", "2025")).thenReturn(List.of(
                new JogadorResumo(1L, "raphael-veiga", "Raphael Veiga", 31, "MEA", 23)));

        mockMvc.perform(get("/api/v1/jogadores?clube=palmeiras&temporada=2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("raphael-veiga"))
                .andExpect(jsonPath("$[0].numeroCamisa").value(23));
    }

    @Test
    void deveRetornar404QuandoJogadorNaoExiste() throws Exception {
        when(jogadorService.buscarPorSlug("inexistente", "2025")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jogadores/inexistente?temporada=2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }
}

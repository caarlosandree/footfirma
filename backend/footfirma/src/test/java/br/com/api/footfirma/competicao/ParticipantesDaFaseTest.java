package br.com.api.footfirma.competicao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.calendario.CalendarioFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ParticipantesDaFaseTest {

    @Autowired
    CompeticaoService competicaoService;

    @Autowired
    CalendarioFactory factory;

    @Test
    void deveTrazerIdETemGolForaNoResumoDaFase() {
        var cenario = factory.criar("fases-teste", 4, "PONTOS_CORRIDOS", 2, true, true, true);

        var fases = competicaoService.listarFasesDaEdicao(cenario.edicaoId());

        assertThat(fases).hasSize(1);
        assertThat(fases.getFirst().id()).isEqualTo(cenario.faseId());
        assertThat(fases.getFirst().temGolFora()).isTrue();
        assertThat(fases.getFirst().temProrrogacao()).isTrue();
        assertThat(fases.getFirst().temPenaltis()).isTrue();
        assertThat(fases.getFirst().jogosPorConfronto()).isEqualTo(2);
    }

    @Test
    void deveTrazerOsDesempatesDesligadosQuandoAFaseNaoOsDeclara() {
        var cenario = factory.criar("fases-simples", 4, "PONTOS_CORRIDOS", 1);

        var fase = competicaoService.listarFasesDaEdicao(cenario.edicaoId()).getFirst();

        assertThat(fase.temGolFora()).isFalse();
        assertThat(fase.temProrrogacao()).isFalse();
        assertThat(fase.temPenaltis()).isFalse();
    }

    @Test
    void deveListarOsParticipantesEmOrdemDeId() {
        var cenario = factory.criar("participantes", 6, "PONTOS_CORRIDOS", 1);

        var participantes = competicaoService.listarParticipantesDaFase(cenario.faseId());

        assertThat(participantes).hasSize(6);
        assertThat(participantes).isSorted();
        assertThat(participantes).containsExactlyElementsOf(
                cenario.clubeIds().stream().sorted().toList());
    }

    @Test
    void deveTrazerAJanelaDaEdicao() {
        var cenario = factory.criar("janela", 2, "PONTOS_CORRIDOS", 1);

        var janela = competicaoService.buscarJanelaDaEdicao(cenario.edicaoId()).orElseThrow();

        assertThat(janela.inicio()).isEqualTo(CalendarioFactory.INICIO);
        assertThat(janela.fim()).isEqualTo(CalendarioFactory.FIM);
    }

    @Test
    void deveDevolverVazioQuandoAEdicaoOuAFaseNaoExiste() {
        assertThat(competicaoService.listarFasesDaEdicao(-1L)).isEmpty();
        assertThat(competicaoService.listarParticipantesDaFase(-1L)).isEmpty();
        assertThat(competicaoService.buscarJanelaDaEdicao(-1L)).isEmpty();
    }
}

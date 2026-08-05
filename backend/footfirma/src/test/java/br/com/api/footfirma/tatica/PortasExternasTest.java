package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.treinador.TreinadorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PortasExternasTest {

    @Autowired
    TreinadorService treinadores;

    @Autowired
    JogadorService jogadores;

    @Autowired
    AvaliacaoService avaliacoes;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void deveDarOPerfilDoTreinadorQueDirigeOClube() {
        var cenario = CenarioDeElenco.montar(jdbc);

        var perfil = treinadores.buscarPerfilDoClube(cenario.clubeId(), cenario.temporadaId());

        assertThat(perfil).isPresent();
        assertThat(perfil.get().tatica()).isEqualTo(cenario.taticaDoTreinador());
        assertThat(perfil.get().reputacao()).isEqualTo(cenario.reputacaoDoTreinador());
    }

    @Test
    void deveVoltarVazioQuandoOClubeNaoTemTreinadorAtivo() {
        var cenario = CenarioDeElenco.montar(jdbc);

        assertThat(treinadores.buscarPerfilDoClube(cenario.clubeSemTreinadorId(),
                cenario.temporadaId())).isEmpty();
    }

    @Test
    void deveListarOElencoComIdsCategoriaEPosicaoPrincipal() {
        var cenario = CenarioDeElenco.montar(jdbc);

        var elenco = jogadores.listarElencoParaEscalacao(cenario.clubeId(), cenario.temporadaId());

        assertThat(elenco).hasSize(cenario.tamanhoDoElenco());
        assertThat(elenco).allSatisfy(linha -> {
            assertThat(linha.jogadorId()).isNotNull();
            assertThat(linha.posicaoPrincipalId()).isNotNull();
            assertThat(linha.categoria()).isIn("PROFISSIONAL", "BASE");
        });
        assertThat(elenco).extracting("categoria").contains("BASE");
    }

    @Test
    void deveListarOveralsDeVariosJogadoresEmTodasAsPosicoes() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var ids = List.of(cenario.primeiroJogadorId(), cenario.segundoJogadorId());

        var overalls = avaliacoes.listarOveralls(ids, cenario.temporadaId());

        assertThat(overalls).hasSize(2 * 9);
        assertThat(overalls).extracting("jogadorId").containsOnly(ids.toArray());
        assertThat(overalls).allSatisfy(linha ->
                assertThat(linha.overall()).isBetween(0, 99));
    }

    @Test
    void deveVoltarListaVaziaQuandoNaoHaJogadorNenhum() {
        var cenario = CenarioDeElenco.montar(jdbc);

        assertThat(avaliacoes.listarOveralls(List.of(), cenario.temporadaId())).isEmpty();
    }
}

package br.com.api.footfirma.mundo.internal;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.mundo.MundoService;
import br.com.api.footfirma.mundo.dto.ContagemPorEntidade;
import br.com.api.footfirma.mundo.dto.RelatorioDeMundo;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@EnableConfigurationProperties(PropriedadesDeMundo.class)
@RequiredArgsConstructor
class MundoServiceImpl implements MundoService {

    static final String TEMPORADA = "2026";
    private static final String ISO_PAIS = "BRA";

    private final TemporadaService temporadaService;
    private final CompeticaoService competicaoService;
    private final GeografiaService geografiaService;
    private final PropriedadesDeMundo propriedades;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public RelatorioDeMundo gerar() {
        if (propriedades.recriar()) {
            LimpezaDoCatalogo.executar(jdbcTemplate);
        }
        var contagens = new ArrayList<ContagemPorEntidade>();
        var paisId = geografiaService.buscarPaisPorIso(ISO_PAIS)
                .orElseThrow(() -> new IllegalStateException(
                        "País BRA ausente — seed de geografia não aplicado"))
                .id();
        var temporadaId = criarTemporada(contagens);
        criarLigas(paisId, temporadaId, contagens);
        return new RelatorioDeMundo(propriedades.semente(), TEMPORADA, List.copyOf(contagens));
    }

    private Long criarTemporada(List<ContagemPorEntidade> contagens) {
        var resultado = temporadaService.sincronizar(new DadosDeTemporada(TEMPORADA, 2026, 2026));
        contagens.add(new ContagemPorEntidade("temporada",
                resultado.criado() ? 1 : 0, resultado.criado() ? 0 : 1));
        return resultado.id();
    }

    private Map<Integer, Long> criarLigas(Long paisId, Long temporadaId,
                                          List<ContagemPorEntidade> contagens) {
        var primeira = criarLiga("primeira-divisao", "Primeira Divisão Nacional", 1,
                paisId, temporadaId);
        var segunda = criarLiga("segunda-divisao", "Segunda Divisão Nacional", 2,
                paisId, temporadaId);

        // Rebaixamento aponta para a divisão de destino; acesso, para a de origem.
        // Libertadores e Sul-Americana ficam fora: não há competição continental
        // neste mundo, e regra que aponta para o nada é dado falso.
        competicaoService.sincronizarRegra(
                new DadosDeRegra(primeira.edicaoId(), 17, 20, "REBAIXAMENTO", segunda.competicaoId()));
        competicaoService.sincronizarRegra(
                new DadosDeRegra(segunda.edicaoId(), 1, 4, "ACESSO", primeira.competicaoId()));

        contagens.add(new ContagemPorEntidade("competicao", 2, 0));
        contagens.add(new ContagemPorEntidade("edicao", 2, 0));
        return Map.of(1, primeira.edicaoId(), 2, segunda.edicaoId());
    }

    private LigaCriada criarLiga(String slug, String nome, int nivel, Long paisId, Long temporadaId) {
        var competicaoId = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao(slug, nome, paisId, "LIGA", nivel, "MASCULINO")).id();
        var edicaoId = competicaoService.sincronizarEdicao(new DadosDeEdicao(
                competicaoId, temporadaId, nome + " " + TEMPORADA,
                LocalDate.of(2026, 4, 4), LocalDate.of(2026, 12, 6))).id();
        competicaoService.sincronizarFase(new DadosDeFase(edicaoId, 1, "Turno e returno",
                "PONTOS_CORRIDOS", 2, false, false, false));
        return new LigaCriada(competicaoId, edicaoId);
    }

    record LigaCriada(Long competicaoId, Long edicaoId) {
    }
}

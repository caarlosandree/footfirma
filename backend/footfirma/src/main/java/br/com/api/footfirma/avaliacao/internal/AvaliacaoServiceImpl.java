package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.dto.ResultadoMaterializacao;
import br.com.api.footfirma.avaliacao.repository.PerfilAvaliacaoRepository;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AvaliacaoServiceImpl implements AvaliacaoService {

    private static final int TAMANHO_DO_LOTE = 200;

    private final PerfilAvaliacaoRepository perfilAvaliacaoRepository;
    private final MaterializadorDeLote materializadorDeLote;
    private final JogadorService jogadorService;
    private final TemporadaService temporadaService;

    // NOT_SUPPORTED de propósito: este método não é uma unidade transacional, cada
    // lote é. Sem isso, a transação readOnly da classe englobaria a escrita e
    // seguraria uma conexão do início ao fim da carga.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoMaterializacao materializar(String labelTemporada) {
        var temporada = temporadaService.buscarPorLabel(labelTemporada);
        if (temporada.isEmpty()) {
            return new ResultadoMaterializacao(labelTemporada, 0, 0);
        }
        var temporadaId = temporada.map(TemporadaResumo::id).orElseThrow();
        var perfis = perfilAvaliacaoRepository.buscarAtivosComPesos();

        var jogadores = 0;
        var linhas = 0;
        var pagina = 0;
        boolean temProxima;
        do {
            // Ordenação total e estável: sem ela a paginação entre commits de lote
            // pode pular uma página, e o upsert não protege contra omissão.
            var lote = jogadorService.listarAtributosPorTemporada(labelTemporada,
                    PageRequest.of(pagina, TAMANHO_DO_LOTE, Sort.by(Sort.Direction.ASC, "jogador.id")));
            if (!lote.isEmpty()) {
                linhas += materializadorDeLote.gravarLote(lote.getContent(), temporadaId, perfis);
                jogadores += lote.getNumberOfElements();
            }
            temProxima = lote.hasNext();
            pagina++;
        } while (temProxima);

        return new ResultadoMaterializacao(labelTemporada, jogadores, linhas);
    }
}

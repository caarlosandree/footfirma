package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.importacao.dto.OcorrenciaRegistrada;

import java.util.ArrayList;
import java.util.List;

/**
 * Acumulador de linhas recusadas. Classe própria para que as etapas não precisem
 * carregar uma List mutável como parâmetro, e para que ocorrência e contagem de
 * recusados saiam sempre do mesmo lugar.
 */
final class ColecionadorDeOcorrencias {

    private final List<OcorrenciaRegistrada> ocorrencias = new ArrayList<>();

    void recusar(String entidade, int linha, String chave, String motivo) {
        ocorrencias.add(new OcorrenciaRegistrada(entidade, linha, chave, "ERRO", motivo));
    }

    void avisar(String entidade, int linha, String chave, String motivo) {
        ocorrencias.add(new OcorrenciaRegistrada(entidade, linha, chave, "AVISO", motivo));
    }

    List<OcorrenciaRegistrada> lista() {
        return List.copyOf(ocorrencias);
    }
}

package br.com.api.footfirma.mundo;

import br.com.api.footfirma.mundo.dto.RelatorioDeMundo;

public interface MundoService {

    /**
     * Gera o mundo inteiro a partir da semente configurada: duas ligas nacionais,
     * 40 clubes e seus elencos, encerrando com a materialização do overall.
     *
     * <p>Idempotente por slug: rodar duas vezes com a mesma semente atualiza em vez
     * de duplicar. Trocar a semente sem limpar deixa o mundo anterior órfão — é para
     * isso que existe {@code footfirma.mundo.recriar}.
     */
    RelatorioDeMundo gerar();
}

package br.com.api.footfirma.mundo.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code semente} tem default para que gerar o mundo não exija configuração; trocá-la
 * troca o mundo inteiro. {@code recriar} apaga o catálogo antes — necessário ao mudar
 * a semente, já que os slugs novos não colidem com os antigos e o mundo velho ficaria
 * órfão no banco.
 */
@ConfigurationProperties(prefix = "footfirma.mundo")
record PropriedadesDeMundo(Long semente, boolean recriar, boolean encerrarAoFinal) {

    static final long SEMENTE_PADRAO = 20260803L;

    PropriedadesDeMundo {
        if (semente == null) {
            semente = SEMENTE_PADRAO;
        }
    }
}

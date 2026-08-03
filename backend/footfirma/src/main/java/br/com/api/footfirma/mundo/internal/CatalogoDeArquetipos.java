package br.com.api.footfirma.mundo.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Os seis perfis e quantos clubes de cada um vive em cada divisão. É dado
 * declarado de propósito: rebalancear a liga é editar esta tabela, não caçar
 * coeficientes espalhados por fórmulas.
 */
final class CatalogoDeArquetipos {

    static final Arquetipo POTENCIA = new Arquetipo("POTENCIA", 82, 90, 85, 95, 55, 70);
    static final Arquetipo GIGANTE_ENDIVIDADO = new Arquetipo("GIGANTE_ENDIVIDADO", 75, 85, 35, 50, 60, 75);
    static final Arquetipo CELEIRO = new Arquetipo("CELEIRO", 50, 62, 30, 45, 85, 95);
    static final Arquetipo MEIO_DE_TABELA = new Arquetipo("MEIO_DE_TABELA", 55, 68, 55, 70, 45, 60);
    static final Arquetipo RECEM_PROMOVIDO = new Arquetipo("RECEM_PROMOVIDO", 45, 55, 45, 60, 40, 55);
    static final Arquetipo EM_QUEDA = new Arquetipo("EM_QUEDA", 40, 52, 25, 40, 30, 45);

    private static final List<QuotaDeArquetipo> PRIMEIRA_DIVISAO = List.of(
            new QuotaDeArquetipo(POTENCIA, 3),
            new QuotaDeArquetipo(GIGANTE_ENDIVIDADO, 3),
            new QuotaDeArquetipo(CELEIRO, 2),
            new QuotaDeArquetipo(MEIO_DE_TABELA, 6),
            new QuotaDeArquetipo(RECEM_PROMOVIDO, 3),
            new QuotaDeArquetipo(EM_QUEDA, 3));

    private static final List<QuotaDeArquetipo> SEGUNDA_DIVISAO = List.of(
            new QuotaDeArquetipo(GIGANTE_ENDIVIDADO, 1),
            new QuotaDeArquetipo(CELEIRO, 3),
            new QuotaDeArquetipo(MEIO_DE_TABELA, 4),
            new QuotaDeArquetipo(RECEM_PROMOVIDO, 5),
            new QuotaDeArquetipo(EM_QUEDA, 7));

    private CatalogoDeArquetipos() {
    }

    /** Devolve 20 arquétipos, um por clube da divisão, na ordem das quotas. */
    static List<Arquetipo> distribuicao(int divisao) {
        var quotas = divisao == 1 ? PRIMEIRA_DIVISAO : SEGUNDA_DIVISAO;
        var arquetipos = new ArrayList<Arquetipo>(20);
        quotas.forEach(quota -> {
            for (var i = 0; i < quota.quantidade(); i++) {
                arquetipos.add(quota.arquetipo());
            }
        });
        return List.copyOf(arquetipos);
    }

    record QuotaDeArquetipo(Arquetipo arquetipo, int quantidade) {
    }
}

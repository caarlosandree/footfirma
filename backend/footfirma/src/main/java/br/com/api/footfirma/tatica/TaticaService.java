package br.com.api.footfirma.tatica;

import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.PlanoVigente;

import java.util.Optional;

/**
 * A única porta pública do módulo.
 *
 * <p>Ela é o único caminho de escrita de propósito: "11 titulares, ninguém repetido,
 * todos do elenco, goleiro no banco" são invariantes de agregado, e {@code check} não
 * enxerga outras linhas. Quem escrever {@code plano_escalacao} por fora produz um time
 * de dez sem goleiro reserva, e o banco aceita.
 */
public interface TaticaService {

    /**
     * Grava uma versão nova do plano do clube e a torna vigente. Não existe update:
     * alterar é sempre versionar.
     *
     * @return o id do plano gravado
     * @throws br.com.api.footfirma.shared.exception.EscalacaoInvalidaException
     *         se qualquer invariante de agregado for violada
     */
    long salvarPlano(long clubeId, long temporadaId, NovoPlano plano);

    /**
     * O plano vigente do clube, com a aptidão congelada e a atual lado a lado.
     *
     * <p>Leitura pura: devolve vazio quando não há plano, e nunca cria um. Quem quer
     * garantir que exista chama {@code garantirPlanoVigente}.
     */
    Optional<PlanoVigente> buscarPlanoVigente(long clubeId, long temporadaId);
}

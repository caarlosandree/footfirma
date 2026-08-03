package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.CenarioDeTreinador;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O cenário do módulo mais o ouvinte.
 *
 * <p>Existe separado porque {@code OuvinteDePartida} é package-private de
 * {@code internal}, como os motores: a classe base mora no pacote raiz para servir também
 * aos testes da porta pública, e não pode sequer nomear este tipo.
 */
abstract class CenarioDeBorda extends CenarioDeTreinador {

    @Autowired
    OuvinteDePartida ouvinte;
}

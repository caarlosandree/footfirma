package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.jogador.dto.AtributosJogador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * overall(jogador, posição) = Σ (atributo × peso). Função pura: sem Spring, sem banco.
 *
 * <p>A conta permanece em BigDecimal do começo ao fim. Em double, somar dezoito
 * parcelas faria o resultado depender da ordem das parcelas — pequeno o bastante para
 * passar despercebido e grande o bastante para mover uma borda de arredondamento.
 */
final class CalculadoraDeOverall {

    private CalculadoraDeOverall() {
    }

    static int calcular(AtributosJogador atributos, Map<AtributoAvaliavel, BigDecimal> pesos) {
        var soma = BigDecimal.ZERO;
        for (var atributo : AtributoAvaliavel.values()) {
            var peso = pesos.get(atributo);
            if (peso == null) {
                throw new IllegalArgumentException(
                        "Perfil de avaliação não declara peso para o atributo " + atributo);
            }
            var valor = atributo.extrair(atributos);
            if (valor == null) {
                throw new IllegalArgumentException("Atributo nulo no jogador avaliado: " + atributo);
            }
            soma = soma.add(BigDecimal.valueOf(valor).multiply(peso));
        }
        // Com atributos limitados a 99 e pesos somando 1.0, ultrapassar 99 é
        // matematicamente impossível: não existe clamp defensivo aqui de propósito.
        return soma.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}

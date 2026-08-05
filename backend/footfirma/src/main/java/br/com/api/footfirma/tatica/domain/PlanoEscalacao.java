package br.com.api.footfirma.tatica.domain;

import br.com.api.footfirma.tatica.dto.Papel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Uma linha da escalação de um plano.
 *
 * <p>{@code posicaoId} é gravada e nunca inferida: é sobre ela que
 * {@code aptidaoNoMomento} foi congelada, e é ela que a leitura usa para recalcular a
 * aptidão atual. Derivar na leitura faria os dois números olharem posições diferentes
 * quando a formação do plano mudasse ou a posição principal do jogador fosse alterada.
 */
@Entity
@Table(name = "plano_escalacao")
@IdClass(PlanoEscalacaoId.class)
@Getter
@Setter
public class PlanoEscalacao {

    @Id
    @Column(name = "plano_id")
    private Long planoId;

    @Id
    @Column(name = "jogador_id")
    private Long jogadorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel;

    @Column(name = "slot_ordem")
    private Integer slotOrdem;

    @Column(name = "ordem_banco")
    private Integer ordemBanco;

    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    @Column(name = "aptidao_no_momento", nullable = false)
    private Integer aptidaoNoMomento;

    @Override
    public boolean equals(Object outro) {
        return outro instanceof PlanoEscalacao outra
                && Objects.equals(planoId, outra.planoId)
                && Objects.equals(jogadorId, outra.jogadorId);
    }

    @Override
    public int hashCode() {
        return PlanoEscalacao.class.hashCode();
    }
}

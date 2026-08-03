package br.com.api.footfirma.treinador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A relação com um jogador durante uma passagem — status declarado e moral resultante.
 *
 * <p>Ancorada no vínculo, não no par: quando a passagem acaba, esta linha morre. O que
 * sobrevive à troca de clube é {@link Afinidade}.
 */
@Entity
@Table(name = "treinador_jogador")
@Getter
@Setter
public class TreinadorJogador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vinculo_treinador_id", nullable = false)
    private VinculoTreinador vinculo;

    @Column(name = "jogador_id", nullable = false)
    private Long jogadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_confianca", nullable = false)
    private StatusConfianca statusConfianca;

    @Column(nullable = false)
    private Double moral;

    @Column(name = "minutos_acumulados", nullable = false)
    private Integer minutosAcumulados;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected TreinadorJogador() {
    }

    public TreinadorJogador(VinculoTreinador vinculo, Long jogadorId, double moral) {
        this.vinculo = vinculo;
        this.jogadorId = jogadorId;
        this.moral = moral;
        this.statusConfianca = StatusConfianca.ROTACAO;
        this.minutosAcumulados = 0;
    }

    public void acumularMinutos(int minutos) {
        this.minutosAcumulados += minutos;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof TreinadorJogador relacao)) {
            return false;
        }
        return id != null && id.equals(relacao.id);
    }

    @Override
    public int hashCode() {
        return TreinadorJogador.class.hashCode();
    }
}

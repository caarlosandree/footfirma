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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A passagem de um treinador por um clube numa temporada.
 *
 * <p>{@code clubeId} e {@code temporadaId} são {@code Long} cru, não {@code @ManyToOne}:
 * entidade JPA de outro módulo não pode vazar para cá, como {@code Clube} já faz com
 * {@code paisId}.
 */
@Entity
@Table(name = "vinculo_treinador")
@Getter
@Setter
public class VinculoTreinador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treinador_id", nullable = false)
    private Treinador treinador;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private LocalDate inicio;

    private LocalDate fim;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_fim")
    private MotivoFim motivoFim;

    @Column(nullable = false)
    private Double moral;

    @Column(name = "meta_posicao", nullable = false)
    private Integer metaPosicao;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected VinculoTreinador() {
    }

    public VinculoTreinador(Treinador treinador, Long clubeId, Long temporadaId,
                            LocalDate inicio, double moral, int metaPosicao) {
        this.treinador = treinador;
        this.clubeId = clubeId;
        this.temporadaId = temporadaId;
        this.inicio = inicio;
        this.moral = moral;
        this.metaPosicao = metaPosicao;
    }

    public boolean estaAtivo() {
        return fim == null;
    }

    /**
     * Encerra a passagem. Data e motivo andam juntos — o banco recusa um sem o outro,
     * porque vínculo encerrado sem motivo esconde por que a carreira virou.
     */
    public void encerrar(LocalDate quando, MotivoFim motivo) {
        this.fim = quando;
        this.motivoFim = motivo;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof VinculoTreinador vinculo)) {
            return false;
        }
        return id != null && id.equals(vinculo.id);
    }

    @Override
    public int hashCode() {
        return VinculoTreinador.class.hashCode();
    }
}

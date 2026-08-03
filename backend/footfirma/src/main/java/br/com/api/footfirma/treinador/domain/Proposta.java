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
 * Um clube querendo um treinador. Sem dinheiro: salário e multa exigem sistema financeiro,
 * e {@code forca_financeira} é índice 0-99, não caixa.
 *
 * <p>A {@code metaPosicao} viaja junto para que se saiba o que vai ser cobrado antes de
 * assinar, não depois.
 */
@Entity
@Table(name = "proposta")
@Getter
@Setter
public class Proposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treinador_id", nullable = false)
    private Treinador treinador;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(name = "meta_posicao", nullable = false)
    private Integer metaPosicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusProposta status;

    @Column(name = "criada_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "respondida_em")
    private OffsetDateTime respondidaEm;

    protected Proposta() {
    }

    public Proposta(Long clubeId, Treinador treinador, Long temporadaId,
                    int metaPosicao, OffsetDateTime expiraEm) {
        this.clubeId = clubeId;
        this.treinador = treinador;
        this.temporadaId = temporadaId;
        this.metaPosicao = metaPosicao;
        this.expiraEm = expiraEm;
        this.status = StatusProposta.ABERTA;
    }

    public void responder(StatusProposta desfecho, OffsetDateTime quando) {
        this.status = desfecho;
        this.respondidaEm = quando;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Proposta proposta)) {
            return false;
        }
        return id != null && id.equals(proposta.id);
    }

    @Override
    public int hashCode() {
        return Proposta.class.hashCode();
    }
}

package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "edicao_participante")
@Getter
@Setter
public class EdicaoParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "posicao_final")
    private Integer posicaoFinal;

    protected EdicaoParticipante() {
    }

    public EdicaoParticipante(Edicao edicao, Long clubeId) {
        this.edicao = edicao;
        this.clubeId = clubeId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EdicaoParticipante participante)) {
            return false;
        }
        return id != null && id.equals(participante.id);
    }

    @Override
    public int hashCode() {
        return EdicaoParticipante.class.hashCode();
    }
}

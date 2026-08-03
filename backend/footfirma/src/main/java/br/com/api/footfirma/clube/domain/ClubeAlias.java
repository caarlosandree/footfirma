package br.com.api.footfirma.clube.domain;

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

@Entity
@Table(name = "clube_alias")
@Getter
@Setter
public class ClubeAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clube_id", nullable = false)
    private Clube clube;

    @Column(nullable = false)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FonteExterna fonte;

    protected ClubeAlias() {
    }

    public ClubeAlias(Clube clube, String alias, FonteExterna fonte) {
        this.clube = clube;
        this.alias = alias;
        this.fonte = fonte;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ClubeAlias clubeAlias)) {
            return false;
        }
        return id != null && id.equals(clubeAlias.id);
    }

    @Override
    public int hashCode() {
        return ClubeAlias.class.hashCode();
    }
}

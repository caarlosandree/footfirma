package br.com.api.footfirma.geografia.domain;

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
@Table(name = "estado")
@Getter
@Setter
public class Estado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pais_id", nullable = false)
    private Pais pais;

    @Column(nullable = false)
    private String uf;

    @Column(nullable = false)
    private String nome;

    protected Estado() {
    }

    public Estado(Pais pais, String uf, String nome) {
        this.pais = pais;
        this.uf = uf;
        this.nome = nome;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Estado estado)) {
            return false;
        }
        return id != null && id.equals(estado.id);
    }

    @Override
    public int hashCode() {
        return Estado.class.hashCode();
    }
}

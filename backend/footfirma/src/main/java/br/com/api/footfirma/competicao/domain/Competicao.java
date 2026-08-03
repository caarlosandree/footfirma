package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "competicao")
@Getter
@Setter
public class Competicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String nome;

    @Column(name = "pais_id")
    private Long paisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCompeticao tipo;

    private Integer nivel;

    @Column(nullable = false)
    private String genero;

    protected Competicao() {
    }

    public Competicao(String slug, String nome, TipoCompeticao tipo) {
        this.slug = slug;
        this.nome = nome;
        this.tipo = tipo;
        this.genero = "MASCULINO";
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Competicao competicao)) {
            return false;
        }
        return id != null && id.equals(competicao.id);
    }

    @Override
    public int hashCode() {
        return Competicao.class.hashCode();
    }
}

package br.com.api.footfirma.jogador.domain;

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
@Table(name = "caracteristica")
@Getter
@Setter
public class Caracteristica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaCaracteristica categoria;

    @Column(nullable = false)
    private String descricao;

    protected Caracteristica() {
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Caracteristica caracteristica)) {
            return false;
        }
        return id != null && id.equals(caracteristica.id);
    }

    @Override
    public int hashCode() {
        return Caracteristica.class.hashCode();
    }
}

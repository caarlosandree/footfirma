package br.com.api.footfirma.clube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "estadio")
@Getter
@Setter
public class Estadio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String cidade;

    @Column(name = "estado_id")
    private Long estadoId;

    private Integer capacidade;

    @Column(name = "ano_inauguracao")
    private Integer anoInauguracao;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected Estadio() {
    }

    public Estadio(String nome, String cidade) {
        this.nome = nome;
        this.cidade = cidade;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Estadio estadio)) {
            return false;
        }
        return id != null && id.equals(estadio.id);
    }

    @Override
    public int hashCode() {
        return Estadio.class.hashCode();
    }
}

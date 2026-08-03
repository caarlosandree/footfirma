package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "jogador_caracteristica")
@IdClass(JogadorCaracteristica.Chave.class)
@Getter
@Setter
public class JogadorCaracteristica {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caracteristica_id", nullable = false)
    private Caracteristica caracteristica;

    protected JogadorCaracteristica() {
    }

    public JogadorCaracteristica(Jogador jogador, Caracteristica caracteristica) {
        this.jogador = jogador;
        this.caracteristica = caracteristica;
    }

    public static class Chave implements Serializable {

        private Long jogador;
        private Long caracteristica;

        public Chave() {
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(jogador, chave.jogador)
                    && Objects.equals(caracteristica, chave.caracteristica);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jogador, caracteristica);
        }
    }
}

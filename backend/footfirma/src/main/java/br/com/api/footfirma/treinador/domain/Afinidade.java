package br.com.api.footfirma.treinador.domain;

import jakarta.persistence.Column;
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
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A memória entre um treinador e um jogador, sem clube na chave.
 *
 * <p>É o que faz recontratar um ex-pupilo trazê-lo adiantado — sem regra especial, apenas
 * porque a linha sobreviveu à troca de clube.
 */
@Entity
@Table(name = "treinador_jogador_afinidade")
@IdClass(Afinidade.Chave.class)
@Getter
@Setter
public class Afinidade {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treinador_id", nullable = false)
    private Treinador treinador;

    @Id
    @Column(name = "jogador_id", nullable = false)
    private Long jogadorId;

    @Column(nullable = false)
    private Double afinidade;

    @Column(name = "jogos_juntos", nullable = false)
    private Integer jogosJuntos;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Afinidade() {
    }

    public Afinidade(Treinador treinador, Long jogadorId) {
        this.treinador = treinador;
        this.jogadorId = jogadorId;
        this.afinidade = 50.0;
        this.jogosJuntos = 0;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Afinidade outra)) {
            return false;
        }
        return Objects.equals(jogadorId, outra.jogadorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jogadorId);
    }

    @Getter
    @Setter
    public static class Chave implements Serializable {

        private Long treinador;
        private Long jogadorId;

        public Chave() {
        }

        public Chave(Long treinador, Long jogadorId) {
            this.treinador = treinador;
            this.jogadorId = jogadorId;
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(treinador, chave.treinador)
                    && Objects.equals(jogadorId, chave.jogadorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(treinador, jogadorId);
        }
    }
}

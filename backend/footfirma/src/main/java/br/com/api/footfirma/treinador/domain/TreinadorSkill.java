package br.com.api.footfirma.treinador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * O valor de uma skill numa temporada. Versionado como {@code jogador_atributo}: comparar
 * duas temporadas é um select, não uma reconstrução a partir de log.
 */
@Entity
@Table(name = "treinador_skill")
@IdClass(TreinadorSkill.Chave.class)
@Getter
@Setter
public class TreinadorSkill {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treinador_id", nullable = false)
    private Treinador treinador;

    @Id
    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Skill skill;

    @Column(nullable = false)
    private Integer valor;

    protected TreinadorSkill() {
    }

    public TreinadorSkill(Treinador treinador, Long temporadaId, Skill skill, Integer valor) {
        this.treinador = treinador;
        this.temporadaId = temporadaId;
        this.skill = skill;
        this.valor = valor;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof TreinadorSkill outra)) {
            return false;
        }
        return Objects.equals(temporadaId, outra.temporadaId) && skill == outra.skill;
    }

    @Override
    public int hashCode() {
        return Objects.hash(temporadaId, skill);
    }

    @Getter
    @Setter
    public static class Chave implements Serializable {

        private Long treinador;
        private Long temporadaId;
        private Skill skill;

        public Chave() {
        }

        public Chave(Long treinador, Long temporadaId, Skill skill) {
            this.treinador = treinador;
            this.temporadaId = temporadaId;
            this.skill = skill;
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
                    && Objects.equals(temporadaId, chave.temporadaId)
                    && skill == chave.skill;
        }

        @Override
        public int hashCode() {
            return Objects.hash(treinador, temporadaId, skill);
        }
    }
}

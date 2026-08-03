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
 * A marca de que um evento já foi aplicado a um vínculo.
 *
 * <p>Sem ela, a reentrega que o registro de publicações do Modulith faz no restart
 * aplicaria a moral da mesma partida duas vezes. Contar as linhas de um vínculo também
 * dá, de graça, quantas partidas ele já teve — a carência da política de demissão.
 */
@Entity
@Table(name = "treinador_evento_processado")
@IdClass(EventoProcessado.Chave.class)
@Getter
@Setter
public class EventoProcessado {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vinculo_id", nullable = false)
    private VinculoTreinador vinculo;

    @Id
    @Column(name = "chave_evento", nullable = false)
    private String chaveEvento;

    @Column(name = "processado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime processadoEm;

    protected EventoProcessado() {
    }

    public EventoProcessado(VinculoTreinador vinculo, String chaveEvento) {
        this.vinculo = vinculo;
        this.chaveEvento = chaveEvento;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EventoProcessado marca)) {
            return false;
        }
        return Objects.equals(chaveEvento, marca.chaveEvento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chaveEvento);
    }

    @Getter
    @Setter
    public static class Chave implements Serializable {

        private Long vinculo;
        private String chaveEvento;

        public Chave() {
        }

        public Chave(Long vinculo, String chaveEvento) {
            this.vinculo = vinculo;
            this.chaveEvento = chaveEvento;
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(vinculo, chave.vinculo)
                    && Objects.equals(chaveEvento, chave.chaveEvento);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vinculo, chaveEvento);
        }
    }
}

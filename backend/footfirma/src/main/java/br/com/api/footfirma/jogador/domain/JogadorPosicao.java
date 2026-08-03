package br.com.api.footfirma.jogador.domain;

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
import java.util.Objects;

@Entity
@Table(name = "jogador_posicao")
@IdClass(JogadorPosicao.Chave.class)
@Getter
@Setter
public class JogadorPosicao {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posicao_id", nullable = false)
    private Posicao posicao;

    @Column(nullable = false)
    private Integer ordem;

    protected JogadorPosicao() {
    }

    public JogadorPosicao(Jogador jogador, Posicao posicao, Integer ordem) {
        this.jogador = jogador;
        this.posicao = posicao;
        this.ordem = ordem;
    }

    public static class Chave implements Serializable {

        private Long jogador;
        private Long posicao;

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
            return Objects.equals(jogador, chave.jogador) && Objects.equals(posicao, chave.posicao);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jogador, posicao);
        }
    }
}

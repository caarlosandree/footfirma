package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "jogador_atributo_oculto")
@Getter
@Setter
public class JogadorAtributoOculto {

    @Id
    @Column(name = "jogador_id")
    private Long jogadorId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "jogador_id")
    private Jogador jogador;

    @Column(nullable = false)
    private Integer profissionalismo;

    @Column(nullable = false)
    private Integer ambicao;

    @Column(nullable = false)
    private Integer lealdade;

    @Column(nullable = false)
    private Integer temperamento;

    @Column(nullable = false)
    private Integer lideranca;

    @Column(nullable = false)
    private Integer regularidade;

    @Column(name = "propensao_lesao", nullable = false)
    private Integer propensaoLesao;

    @Column(name = "resistencia_pressao", nullable = false)
    private Integer resistenciaPressao;

    protected JogadorAtributoOculto() {
    }

    public JogadorAtributoOculto(Jogador jogador) {
        this.jogador = jogador;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorAtributoOculto atributoOculto)) {
            return false;
        }
        return jogadorId != null && jogadorId.equals(atributoOculto.jogadorId);
    }

    @Override
    public int hashCode() {
        return JogadorAtributoOculto.class.hashCode();
    }
}

package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "regra_classificacao")
@Getter
@Setter
public class RegraClassificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Column(name = "posicao_inicio", nullable = false)
    private Integer posicaoInicio;

    @Column(name = "posicao_fim", nullable = false)
    private Integer posicaoFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoClassificacao tipo;

    @Column(name = "competicao_destino_id")
    private Long competicaoDestinoId;

    protected RegraClassificacao() {
    }

    public RegraClassificacao(Edicao edicao, Integer posicaoInicio, Integer posicaoFim,
                              TipoClassificacao tipo, Long competicaoDestinoId) {
        this.edicao = edicao;
        this.posicaoInicio = posicaoInicio;
        this.posicaoFim = posicaoFim;
        this.tipo = tipo;
        this.competicaoDestinoId = competicaoDestinoId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof RegraClassificacao regra)) {
            return false;
        }
        return id != null && id.equals(regra.id);
    }

    @Override
    public int hashCode() {
        return RegraClassificacao.class.hashCode();
    }
}

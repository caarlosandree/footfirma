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
@Table(name = "fase")
@Getter
@Setter
public class Fase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Column(nullable = false)
    private Integer ordem;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFase tipo;

    @Column(name = "jogos_por_confronto", nullable = false)
    private Integer jogosPorConfronto;

    @Column(name = "tem_gol_fora", nullable = false)
    private Boolean temGolFora;

    @Column(name = "tem_prorrogacao", nullable = false)
    private Boolean temProrrogacao;

    @Column(name = "tem_penaltis", nullable = false)
    private Boolean temPenaltis;

    protected Fase() {
    }

    public Fase(Edicao edicao, Integer ordem, String nome, TipoFase tipo) {
        this.edicao = edicao;
        this.ordem = ordem;
        this.nome = nome;
        this.tipo = tipo;
        this.jogosPorConfronto = 1;
        this.temGolFora = false;
        this.temProrrogacao = false;
        this.temPenaltis = false;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Fase fase)) {
            return false;
        }
        return id != null && id.equals(fase.id);
    }

    @Override
    public int hashCode() {
        return Fase.class.hashCode();
    }
}

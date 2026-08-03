package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "edicao")
@Getter
@Setter
public class Edicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competicao_id", nullable = false)
    private Competicao competicao;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private String nome;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @OneToMany(mappedBy = "edicao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem")
    private List<Fase> fases = new ArrayList<>();

    protected Edicao() {
    }

    public Edicao(Competicao competicao, Long temporadaId, String nome) {
        this.competicao = competicao;
        this.temporadaId = temporadaId;
        this.nome = nome;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Edicao edicao)) {
            return false;
        }
        return id != null && id.equals(edicao.id);
    }

    @Override
    public int hashCode() {
        return Edicao.class.hashCode();
    }
}

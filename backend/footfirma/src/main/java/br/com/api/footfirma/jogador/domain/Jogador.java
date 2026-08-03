package br.com.api.footfirma.jogador.domain;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "jogador")
@Getter
@Setter
public class Jogador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "chave_natural", nullable = false, unique = true)
    private String chaveNatural;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "nome_exibicao", nullable = false)
    private String nomeExibicao;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "pais_id", nullable = false)
    private Long paisId;

    @Column(name = "segunda_nacionalidade_id")
    private Long segundaNacionalidadeId;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "peso_kg")
    private Integer pesoKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "pe_preferido", nullable = false)
    private PeParaChute pePreferido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posicao_principal_id", nullable = false)
    private Posicao posicaoPrincipal;

    @Column(nullable = false)
    private Long semente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemJogador origem;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Jogador() {
    }

    public Jogador(String slug, String chaveNatural, String nomeCompleto, String nomeExibicao,
                   LocalDate dataNascimento, Long paisId, Posicao posicaoPrincipal,
                   PeParaChute pePreferido, long semente) {
        this.slug = slug;
        this.chaveNatural = chaveNatural;
        this.nomeCompleto = nomeCompleto;
        this.nomeExibicao = nomeExibicao;
        this.dataNascimento = dataNascimento;
        this.paisId = paisId;
        this.posicaoPrincipal = posicaoPrincipal;
        this.pePreferido = pePreferido;
        this.semente = semente;
        this.origem = OrigemJogador.REAL;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Jogador jogador)) {
            return false;
        }
        return id != null && id.equals(jogador.id);
    }

    @Override
    public int hashCode() {
        return Jogador.class.hashCode();
    }
}

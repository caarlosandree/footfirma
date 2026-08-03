package br.com.api.footfirma.treinador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "treinador")
@Getter
@Setter
public class Treinador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "nome_exibicao", nullable = false)
    private String nomeExibicao;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "pais_id", nullable = false)
    private Long paisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTreinador tipo;

    @Column(nullable = false)
    private Integer reputacao;

    @Column(name = "pontos_disponiveis", nullable = false)
    private Integer pontosDisponiveis;

    @Column(nullable = false)
    private Long semente;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Treinador() {
    }

    public Treinador(String slug, String nomeCompleto, String nomeExibicao,
                     LocalDate dataNascimento, Long paisId, TipoTreinador tipo, Long semente) {
        this.slug = slug;
        this.nomeCompleto = nomeCompleto;
        this.nomeExibicao = nomeExibicao;
        this.dataNascimento = dataNascimento;
        this.paisId = paisId;
        this.tipo = tipo;
        this.semente = semente;
        this.reputacao = 50;
        this.pontosDisponiveis = 0;
    }

    public boolean ehHumano() {
        return tipo == TipoTreinador.HUMANO;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Treinador treinador)) {
            return false;
        }
        return id != null && id.equals(treinador.id);
    }

    @Override
    public int hashCode() {
        return Treinador.class.hashCode();
    }
}

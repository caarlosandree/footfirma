package br.com.api.footfirma.importacao.domain;

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

import java.time.OffsetDateTime;

/**
 * Linha que o importador recusou antes de tocar no banco — referência ausente,
 * valor fora do catálogo. Erro de banco não chega aqui: ele aborta a etapa.
 */
@Entity
@Table(name = "importacao_ocorrencia")
@Getter
@Setter
public class ImportacaoOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execucao_id", nullable = false)
    private Long execucaoId;

    @Column(nullable = false)
    private String entidade;

    @Column(nullable = false)
    private Integer linha;

    @Column(nullable = false)
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeveridadeOcorrencia severidade;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "registrada_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime registradaEm;

    protected ImportacaoOcorrencia() {
    }

    public ImportacaoOcorrencia(Long execucaoId, String entidade, Integer linha, String chave,
                                SeveridadeOcorrencia severidade, String motivo) {
        this.execucaoId = execucaoId;
        this.entidade = entidade;
        this.linha = linha;
        this.chave = chave;
        this.severidade = severidade;
        this.motivo = motivo;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ImportacaoOcorrencia ocorrencia)) {
            return false;
        }
        return id != null && id.equals(ocorrencia.id);
    }

    @Override
    public int hashCode() {
        return ImportacaoOcorrencia.class.hashCode();
    }
}

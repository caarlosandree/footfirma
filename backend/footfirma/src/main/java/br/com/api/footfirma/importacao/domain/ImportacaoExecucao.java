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

@Entity
@Table(name = "importacao_execucao")
@Getter
@Setter
public class ImportacaoExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_versao", nullable = false)
    private String datasetVersao;

    @Column(name = "schema_versao", nullable = false)
    private String schemaVersao;

    @Column(nullable = false)
    private String diretorio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusImportacao status;

    @Column(name = "iniciada_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime iniciadaEm;

    @Column(name = "finalizada_em")
    private OffsetDateTime finalizadaEm;

    @Column(name = "motivo_da_falha")
    private String motivoDaFalha;

    protected ImportacaoExecucao() {
    }

    public ImportacaoExecucao(String datasetVersao, String schemaVersao, String diretorio) {
        this.datasetVersao = datasetVersao;
        this.schemaVersao = schemaVersao;
        this.diretorio = diretorio;
        this.status = StatusImportacao.EM_ANDAMENTO;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ImportacaoExecucao execucao)) {
            return false;
        }
        return id != null && id.equals(execucao.id);
    }

    @Override
    public int hashCode() {
        return ImportacaoExecucao.class.hashCode();
    }
}

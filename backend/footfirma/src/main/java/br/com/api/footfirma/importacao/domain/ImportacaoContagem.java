package br.com.api.footfirma.importacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code execucaoId} é Long cru, não {@code @ManyToOne}, mesmo pertencendo ao próprio
 * módulo: a auditoria é escrita em lote no fim da carga, e uma associação gerenciada
 * só criaria oportunidade de flush no meio dela.
 */
@Entity
@Table(name = "importacao_contagem")
@Getter
@Setter
public class ImportacaoContagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execucao_id", nullable = false)
    private Long execucaoId;

    @Column(nullable = false)
    private String entidade;

    @Column(nullable = false)
    private Integer lidos;

    @Column(nullable = false)
    private Integer criados;

    @Column(nullable = false)
    private Integer atualizados;

    @Column(nullable = false)
    private Integer recusados;

    protected ImportacaoContagem() {
    }

    public ImportacaoContagem(Long execucaoId, String entidade,
                              Integer lidos, Integer criados, Integer atualizados, Integer recusados) {
        this.execucaoId = execucaoId;
        this.entidade = entidade;
        this.lidos = lidos;
        this.criados = criados;
        this.atualizados = atualizados;
        this.recusados = recusados;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ImportacaoContagem contagem)) {
            return false;
        }
        return id != null && id.equals(contagem.id);
    }

    @Override
    public int hashCode() {
        return ImportacaoContagem.class.hashCode();
    }
}

package br.com.api.footfirma.tatica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "formacao_slot")
@IdClass(FormacaoSlot.Chave.class)
@Getter
@Setter
public class FormacaoSlot {

    @Id
    @Column(name = "formacao_id")
    private Long formacaoId;

    @Id
    private Integer ordem;

    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    public record Chave(Long formacaoId, Integer ordem) implements Serializable {
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof FormacaoSlot outra
                && Objects.equals(formacaoId, outra.formacaoId)
                && Objects.equals(ordem, outra.ordem);
    }

    @Override
    public int hashCode() {
        return FormacaoSlot.class.hashCode();
    }
}

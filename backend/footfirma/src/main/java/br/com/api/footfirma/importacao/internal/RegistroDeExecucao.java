package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.importacao.domain.ImportacaoContagem;
import br.com.api.footfirma.importacao.domain.ImportacaoExecucao;
import br.com.api.footfirma.importacao.domain.ImportacaoOcorrencia;
import br.com.api.footfirma.importacao.domain.SeveridadeOcorrencia;
import br.com.api.footfirma.importacao.domain.StatusImportacao;
import br.com.api.footfirma.importacao.dto.ContagemDeEntidade;
import br.com.api.footfirma.importacao.dto.OcorrenciaRegistrada;
import br.com.api.footfirma.importacao.repository.ImportacaoContagemRepository;
import br.com.api.footfirma.importacao.repository.ImportacaoExecucaoRepository;
import br.com.api.footfirma.importacao.repository.ImportacaoOcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Grava a auditoria em transação própria.
 *
 * <p>{@code REQUIRES_NEW} é o ponto: o registro precisa sobreviver ao rollback da
 * etapa que falhou. Sem isso, uma carga que quebra na metade não deixa rastro — o
 * pior comportamento possível para uma tabela de auditoria.
 */
@Component
@RequiredArgsConstructor
class RegistroDeExecucao {

    private static final int LIMITE_DO_MOTIVO = 240;

    private final ImportacaoExecucaoRepository execucaoRepository;
    private final ImportacaoContagemRepository contagemRepository;
    private final ImportacaoOcorrenciaRepository ocorrenciaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Long abrir(String datasetVersao, String schemaVersao, String diretorio) {
        var execucao = execucaoRepository.save(
                new ImportacaoExecucao(datasetVersao, schemaVersao, diretorio));
        return execucao.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void concluir(Long execucaoId, List<ContagemDeEntidade> contagens,
                  List<OcorrenciaRegistrada> ocorrencias) {
        fechar(execucaoId, StatusImportacao.CONCLUIDA, null, contagens, ocorrencias);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void falhar(Long execucaoId, String motivo, List<ContagemDeEntidade> parciais,
                List<OcorrenciaRegistrada> ocorrencias) {
        // A constraint ck_importacao_execucao_falha exige motivo não nulo em FALHOU,
        // e exception sem mensagem existe.
        var descricao = motivo == null || motivo.isBlank() ? "falha sem mensagem" : motivo;
        fechar(execucaoId, StatusImportacao.FALHOU, truncar(descricao), parciais, ocorrencias);
    }

    private void fechar(Long execucaoId, StatusImportacao status, String motivo,
                        List<ContagemDeEntidade> contagens, List<OcorrenciaRegistrada> ocorrencias) {
        var execucao = execucaoRepository.findById(execucaoId).orElseThrow();
        execucao.setStatus(status);
        execucao.setMotivoDaFalha(motivo);
        execucao.setFinalizadaEm(OffsetDateTime.now());
        execucaoRepository.save(execucao);

        contagemRepository.saveAll(contagens.stream()
                .map(contagem -> new ImportacaoContagem(execucaoId, contagem.entidade(),
                        contagem.lidos(), contagem.criados(),
                        contagem.atualizados(), contagem.recusados()))
                .toList());

        ocorrenciaRepository.saveAll(ocorrencias.stream()
                .map(ocorrencia -> new ImportacaoOcorrencia(execucaoId, ocorrencia.entidade(),
                        ocorrencia.linha(), ocorrencia.chave(),
                        SeveridadeOcorrencia.valueOf(ocorrencia.severidade()),
                        truncar(ocorrencia.motivo())))
                .toList());
    }

    /** As colunas aceitam 240 (motivo) e 400 (falha); truncar evita que auditoria vire erro. */
    private static String truncar(String texto) {
        return texto.length() <= LIMITE_DO_MOTIVO ? texto : texto.substring(0, LIMITE_DO_MOTIVO);
    }
}

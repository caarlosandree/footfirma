package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.importacao.ImportacaoService;
import br.com.api.footfirma.importacao.dto.RelatorioDeImportacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Dispara a carga sob o profile {@code importacao} e só sob ele: subir a API
 * normalmente nunca deve escrever no catálogo.
 *
 * <p>FootfirmaApplication não tem {@code @ConfigurationPropertiesScan}, então o
 * registro das propriedades é local. Anotar a classe de aplicação também
 * funcionaria, mas ligaria o scan do sistema inteiro para servir um runner que só
 * roda sob profile.
 */
@Component
@Profile("importacao")
@EnableConfigurationProperties(PropriedadesDeImportacao.class)
@RequiredArgsConstructor
@Slf4j
class ExecutorDeImportacao implements ApplicationRunner {

    private final ImportacaoService importacaoService;
    private final PropriedadesDeImportacao propriedades;
    private final ApplicationContext contexto;

    @Override
    public void run(ApplicationArguments argumentos) {
        var relatorio = importacaoService.importar(Path.of(propriedades.diretorio()));
        registrar(relatorio);

        // Encerrar com código de saída é o comportamento esperado de um job: sem
        // isso a aplicação fica de pé e um script não sabe se a carga deu certo.
        if (propriedades.encerrarAoFinal()) {
            var codigo = "CONCLUIDA".equals(relatorio.status()) ? 0 : 1;
            System.exit(SpringApplication.exit(contexto, () -> codigo));
        }
    }

    private void registrar(RelatorioDeImportacao relatorio) {
        log.info("Importação {} — dataset {} — status {}",
                relatorio.execucaoId(), relatorio.datasetVersao(), relatorio.status());
        relatorio.contagens().forEach(contagem -> log.info(
                "  {}: {} lidos, {} criados, {} atualizados, {} recusados",
                contagem.entidade(), contagem.lidos(), contagem.criados(),
                contagem.atualizados(), contagem.recusados()));
        log.info("  overall: {} linhas materializadas", relatorio.overallsMaterializados());
        relatorio.ocorrencias().forEach(ocorrencia -> log.warn(
                "  [{}] {} linha {} ({}): {}",
                ocorrencia.severidade(), ocorrencia.entidade(), ocorrencia.linha(),
                ocorrencia.chave(), ocorrencia.motivo()));
        if (relatorio.motivoDaFalha() != null) {
            log.error("  motivo da falha: {}", relatorio.motivoDaFalha());
        }
    }
}

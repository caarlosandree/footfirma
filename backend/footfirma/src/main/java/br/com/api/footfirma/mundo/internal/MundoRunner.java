package br.com.api.footfirma.mundo.internal;

import br.com.api.footfirma.mundo.MundoService;
import br.com.api.footfirma.mundo.dto.RelatorioDeMundo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dispara a geração sob o profile {@code mundo} e só sob ele: subir a API
 * normalmente nunca deve escrever no catálogo.
 */
@Component
@Profile("mundo")
@RequiredArgsConstructor
@Slf4j
class MundoRunner implements ApplicationRunner {

    private final MundoService mundoService;
    private final PropriedadesDeMundo propriedades;
    private final ApplicationContext contexto;

    @Override
    public void run(ApplicationArguments argumentos) {
        registrar(mundoService.gerar());

        // Encerrar com código de saída é o comportamento esperado de um job: sem
        // isso a aplicação fica de pé e um script não sabe se a geração deu certo.
        if (propriedades.encerrarAoFinal()) {
            System.exit(SpringApplication.exit(contexto, () -> 0));
        }
    }

    private void registrar(RelatorioDeMundo relatorio) {
        log.info("Mundo gerado — semente {} — temporada {}",
                relatorio.semente(), relatorio.temporada());
        relatorio.contagens().forEach(contagem -> log.info("  {}: {} criados, {} atualizados",
                contagem.entidade(), contagem.criados(), contagem.atualizados()));
    }
}

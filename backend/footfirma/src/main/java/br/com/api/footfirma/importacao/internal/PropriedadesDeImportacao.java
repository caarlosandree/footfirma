package br.com.api.footfirma.importacao.internal;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "footfirma.importacao")
record PropriedadesDeImportacao(@NotBlank String diretorio, boolean encerrarAoFinal) {
}

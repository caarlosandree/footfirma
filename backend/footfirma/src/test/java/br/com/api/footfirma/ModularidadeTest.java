package br.com.api.footfirma;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularidadeTest {

    static final ApplicationModules modulos = ApplicationModules.of(FootfirmaApplication.class);

    @Test
    void naoDeveViolarFronteirasEntreModulos() {
        modulos.verify();
    }

    @Test
    void deveGerarDocumentacaoDosModulos() {
        new Documenter(modulos).writeDocumentation();
    }
}

package br.com.api.footfirma;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FootfirmaApplicationTests {

    @Test
    void contextLoads() {
    }

}

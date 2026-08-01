---
trigger: model_decision
globs: "src/test/**/*.java"
description: Estratégia de testes do backend FootFirma — JUnit 5, Testcontainers com Postgres e Redis, testes de modularidade Spring Modulith e slices do Boot 4.
---

# FootFirma Backend — Testes

Complementa `.rules/java-core.md`. Vale para tudo em `src/test/java/`.

## O que já existe

| Arquivo | Papel |
|---|---|
| `TestcontainersConfiguration` | `@TestConfiguration` com `PostgreSQLContainer` e um `GenericContainer` de Redis, ambos com `@ServiceConnection` |
| `TestFootfirmaApplication` | entrypoint para `./gradlew bootTestRun` (app local apoiada em containers) |
| `FootfirmaApplicationTests` | smoke test de contexto |

Reuse `TestcontainersConfiguration` via `@Import` — **não** instancie containers
soltos em cada teste.

## Regra fundamental: banco real, sempre

Testes que tocam persistência usam Postgres e Redis em container. **H2, banco em
memória e mock de `Repository` são proibidos** neste projeto: o schema é governado
por Flyway com dialeto PostgreSQL, e um substituto esconde exatamente as falhas que
importam (tipos, constraints, `ON CONFLICT`, índices parciais).

Isso exige Docker rodando. Se não estiver, o teste falha — não contorne trocando de
banco.

## Starters de teste no Boot 4

Este projeto usa os starters granulares, já declarados no `build.gradle`:
`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-data-redis-test`,
`-security-test`, `-validation-test`, `-actuator-test`, `-flyway-test`.
Não adicione `spring-boot-starter-test`.

## Pirâmide

| Tipo | Alvo | Anotação | Custo |
|---|---|---|---|
| Unitário | regra de negócio pura no service, mappers, value objects | nenhuma (JUnit + Mockito) | barato — a maior parte |
| Slice de persistência | repository, queries, migrations | `@DataJpaTest` + Testcontainers | médio |
| Slice web | controller, serialização, validação, status | `@WebMvcTest` | médio |
| Integração | fluxo ponta a ponta, eventos entre módulos | `@SpringBootTest` | caro — só para fluxos críticos |
| Modularidade | fronteiras entre módulos | `ApplicationModules.verify()` | barato — obrigatório |

## Nomenclatura e estrutura

- Classe: `<Alvo>Test` (`PartidaServiceTest`). Nunca `TestPartidaService`.
- Método: `deve<Comportamento>Quando<Condição>` em português —
  `deveRejeitarInscricaoQuandoPartidaEstaLotada`.
- Corpo em três blocos separados por linha em branco: preparação, execução, verificação.
- Um comportamento por teste. Teste que verifica cinco coisas esconde qual quebrou.
- Use `@DisplayName` quando o nome do método não couber legível.

```java
class PartidaServiceTest {

    private final PartidaRepository partidaRepository = mock(PartidaRepository.class);
    private final PartidaService partidaService = new PartidaServiceImpl(partidaRepository);

    @Test
    void deveRejeitarInscricaoQuandoPartidaEstaLotada() {
        var partida = PartidaFactory.lotada();
        when(partidaRepository.findById(partida.getId())).thenReturn(Optional.of(partida));

        var erro = assertThrows(PartidaLotadaException.class,
                () -> partidaService.inscrever(partida.getId(), UUID.randomUUID()));

        assertThat(erro.getMessage()).contains("lotada");
    }
}
```

## Teste de modularidade — obrigatório

Uma violação de fronteira entre módulos precisa quebrar o build, não passar
despercebida na review.

```java
package br.com.api.footfirma;

class ModularidadeTest {

    static final ApplicationModules modulos = ApplicationModules.of(FootfirmaApplication.class);

    @Test
    void moduloNaoViolaFronteiras() {
        modulos.verify();
    }

    @Test
    void geraDocumentacaoDosModulos() {
        new Documenter(modulos).writeDocumentation();
    }
}
```

Ao criar o primeiro módulo de domínio, crie também este teste. Quando ele falhar,
**corrija a dependência**, não relaxe a verificação.

## Teste de repository

```java
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PartidaRepositoryTest {

    @Autowired PartidaRepository partidaRepository;

    @Test
    void deveEncontrarPartidasAbertasOrdenadasPorInicio() { /* ... */ }
}
```

`Replace.NONE` é obrigatório — sem ele o Spring troca o container por banco embutido
e o teste deixa de valer.

## Teste de controller

```java
@WebMvcTest(PartidaController.class)
class PartidaControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PartidaService partidaService;

    @Test
    void deveRetornar422QuandoTituloEstaVazio() throws Exception {
        mockMvc.perform(post("/api/v1/partidas")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"titulo": "", "inicio": "2030-01-01T10:00:00", "vagas": 10}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }
}
```

- `@MockitoBean` (não `@MockBean`, removido).
- `@WebMvcTest` carrega o Spring Security: teste autenticado usa `@WithMockUser` ou
  `SecurityMockMvcRequestPostProcessors`. Não desabilite a segurança para o teste passar.

## Teste de eventos entre módulos

`spring-modulith-starter-test` permite verificar a publicação sem subir o consumidor.

```java
@ApplicationModuleTest
class PedidoModuleTest {

    @Test
    void devePublicarEventoAoCriarPedido(Scenario cenario) {
        cenario.stimulate(() -> pedidoService.criar(NovoPedidoFactory.valido()))
                .andWaitForEventOfType(PedidoCriado.class)
                .toArriveAndVerify(evento -> assertThat(evento.pedidoId()).isNotNull());
    }
}
```

## Factories de teste

Massa de teste repetida vira `PartidaFactory`, `UsuarioFactory` em
`src/test/java/.../<feature>/`. Cada método devolve um objeto válido por padrão e
aceita sobrescrita apenas do que o teste precisa destacar.

## Antipadrões

- Mockar `Repository` em teste de integração — se é integração, use o container.
- `Thread.sleep` para esperar assincronia — use `Awaitility` ou a API `Scenario` do Modulith.
- Teste que depende da ordem de execução ou de estado deixado por outro teste.
- Assertion sobre mensagem de log em vez de sobre comportamento.
- `@SpringBootTest` para testar uma regra que cabe num teste unitário.
- Baixar a cobertura ou remover assertion para "consertar" um teste vermelho.

## Execução

```bash
./gradlew test                                    # suíte completa (exige Docker)
./gradlew test --tests '*PartidaServiceTest'      # um teste
./gradlew build                                   # compila + testa
```

Não rode a suíte a cada edição. Rode o teste específico durante o trabalho e a
suíte antes do push.

## Módulos relacionados

- `.rules/java-core.md` — arquitetura e módulos
- `.rules/java-api.md` — contratos que os testes de controller verificam
- `.rules/database.md` — migrations exercitadas pelos testes
- `.rules/java-checklist.md` — checklist final

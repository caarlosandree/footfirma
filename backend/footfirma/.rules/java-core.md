---
trigger: always_on
globs: "src/**/*.java"
description: Arquitetura, módulos Spring Modulith, package by feature, nomenclatura, DI, transações e restrições operacionais do backend FootFirma.
---

# FootFirma Backend — Core

API do FootFirma. Spring Boot 4 sobre Java 25, organizada em módulos de domínio
verificados pelo Spring Modulith.

## Stack real deste projeto

**As versões exatas vivem no `build.gradle` — é ele a fonte da verdade.** Este
documento cita apenas a major, porque dependência sobe a qualquer momento. Se
precisar da versão precisa de algo, leia o `build.gradle`; não presuma pelo que está
escrito aqui.

| Item | Valor |
|---|---|
| Linguagem | Java 25 (toolchain declarada em `build.gradle`) |
| Framework | Spring Boot 4 |
| Modularização | Spring Modulith (`spring-modulith-starter-core`, `-starter-jpa`) |
| Build | Gradle (Groovy DSL, `build.gradle`) + wrapper `./gradlew` |
| Web | `spring-boot-starter-webmvc` + `spring-boot-starter-websocket` |
| Persistência | `spring-boot-starter-data-jpa` + PostgreSQL (`org.postgresql:postgresql`) |
| Cache / pub-sub | `spring-boot-starter-data-redis` |
| Migrations | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) |
| Segurança | `spring-boot-starter-security` |
| Validação | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| Mapeamento | MapStruct + Lombok (via `lombok-mapstruct-binding`) |
| Documentação | springdoc-openapi (`/swagger-ui.html`, `/v3/api-docs`) |
| Observabilidade | Actuator + `spring-modulith-observability-*` |
| Testes | JUnit 5, Testcontainers (Postgres + Redis), `spring-modulith-starter-test` |
| Pacote base | `br.com.api.footfirma` (group `br.com.api`) |

**Atenção ao Spring Boot 4:** os starters mudaram de nome em relação ao Boot 3.
Use `spring-boot-starter-webmvc` (não `-web`) e os starters de teste granulares
(`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, …) em vez do
antigo `spring-boot-starter-test`. Ao adicionar uma dependência nova, siga o padrão
já presente no `build.gradle` deste projeto — não copie snippets de Boot 3.

## Comandos canônicos

Sempre a partir de `backend/footfirma/`:

```bash
./gradlew compileJava        # validação rápida de compilação (use este, não build)
./gradlew build              # build completo (compila + testa)
./gradlew test               # suíte de testes (exige Docker rodando)
./gradlew bootRun            # sobe a API; spring-boot-docker-compose levanta Postgres e Redis
./gradlew bootTestRun        # sobe a API com Testcontainers (TestFootfirmaApplication)
```

## Arquitetura: package by feature com Spring Modulith

Cada pacote **diretamente abaixo** de `br.com.api.footfirma` é um módulo de aplicação
para o Spring Modulith. Isso não é convenção opcional aqui — é o que o
`ModularidadeTest` valida. Nunca crie pacotes técnicos globais (`controllers/`,
`services/`, `repositories/`, `entities/`) na raiz.

```
src/main/java/br/com/api/footfirma/
├── FootfirmaApplication.java
├── config/                          # cross-cutting, módulo aberto
│   ├── package-info.java            # @ApplicationModule(type = OPEN)
│   └── OpenApiConfig.java
├── shared/                          # tipos reutilizáveis entre módulos, módulo aberto
│   ├── package-info.java            # @ApplicationModule(type = OPEN)
│   ├── exception/
│   └── dto/
└── <feature>/                       # ← um módulo por domínio de negócio
    ├── package-info.java            # @ApplicationModule(displayName = "...")
    ├── <Feature>Service.java        # API PÚBLICA do módulo (raiz do pacote)
    ├── <Feature>Criado.java         # evento de domínio (record) — API pública
    ├── dto/                         # records de request/response expostos
    ├── web/       <Feature>Controller.java
    ├── domain/    <Feature>.java (@Entity), enums, value objects
    ├── repository/<Feature>Repository.java
    ├── mapper/    <Feature>Mapper.java
    └── internal/  detalhes que nenhum outro módulo pode enxergar
```

### Regra de visibilidade (a que mais causa falha de build)

No Spring Modulith, **tipos no pacote raiz do módulo são a API pública; tipos em
subpacotes são internos**. Consequências práticas:

- `pedido.web.PedidoController` **não pode** ser referenciado por outro módulo.
- `usuario.domain.Usuario` (entidade JPA) **não pode** vazar para o módulo `pedido`.
- Para expor algo, coloque no pacote raiz do módulo ou anote um subpacote com
  `@NamedInterface` no `package-info.java`.

```java
// ✅ br/com/api/footfirma/pedido/PedidoService.java — interface pública do módulo
package br.com.api.footfirma.pedido;

public interface PedidoService {
    PedidoResumo criar(NovoPedido comando);
}

// ✅ implementação escondida
// br/com/api/footfirma/pedido/internal/PedidoServiceImpl.java
```

### Comunicação entre módulos

Prefira **eventos de domínio** a chamadas diretas. O `spring-modulith-starter-jpa`
já está no classpath e persiste as publicações de evento.

```java
// Publicação — dentro da transação do produtor
@Service
@Transactional(readOnly = true)
public class PedidoServiceImpl implements PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ApplicationEventPublisher eventos;

    @Transactional
    public PedidoResumo criar(NovoPedido comando) {
        var pedido = pedidoRepository.save(Pedido.novo(comando));
        eventos.publishEvent(new PedidoCriado(pedido.getId()));
        return PedidoResumo.de(pedido);
    }
}

// Consumo — em outro módulo, transação separada e assíncrona
@Component
class NotificacaoDePedido {

    @ApplicationModuleListener
    void em(PedidoCriado evento) {
        // ...
    }
}
```

- Eventos são `record`, nomeados no passado (`PedidoCriado`, `PagamentoConfirmado`),
  carregam **identificadores**, nunca entidades JPA.
- `@ApplicationModuleListener` roda `@Async` + `@Transactional(REQUIRES_NEW)` +
  `@TransactionalEventListener` — não presuma a transação do produtor.
- Listeners devem ser **idempotentes**: o registro de publicação reenvia eventos
  não completados na reinicialização.
- A tabela `event_publication` usada pelo Modulith precisa existir via Flyway.
  Se ainda não houver migration para ela, crie antes do primeiro evento persistido.

## Camadas dentro do módulo

O fluxo é sempre `web → service → repository`. Nenhuma seta pula ou volta.

| Camada | Responsabilidade | Nunca faz |
|---|---|---|
| `web/` Controller | traduzir HTTP ↔ DTO, `@Valid`, status code | regra de negócio, acesso a repository, `@Transactional` |
| Service | orquestrar caso de uso, abrir transação, publicar eventos | conhecer `HttpServletRequest`, montar JSON |
| `repository/` | acesso a dados via Spring Data | decidir regra de negócio |
| `domain/` | entidade JPA, invariantes simples, enums | chamar service ou repository |

## Nomenclatura

- Pacotes: minúsculas, singular, nome de domínio em português (`pedido`, `usuario`, `partida`).
- Classes: `PascalCase`. Sufixos obrigatórios: `Controller`, `Service`, `Repository`,
  `Mapper`, `Test`. Sem prefixo `I` em interfaces.
- Métodos e variáveis: `camelCase`, descritivos (`buscarPorEmail`, não `get`/`buscar`).
- Constantes: `UPPER_SNAKE_CASE`.
- Enums: tipo em `PascalCase`, valores em `UPPER_SNAKE_CASE`.
- Eventos: substantivo + particípio no passado (`PedidoCancelado`).
- DTOs: `NovoPedido` (entrada de criação), `AtualizacaoPedido` (entrada de update),
  `PedidoResumo` / `PedidoDetalhe` (saída). Nunca um único DTO para tudo.

## Java 25 — use os recursos da linguagem

- `record` para todo DTO, evento e value object. DTO com `class` só com justificativa.
- `sealed interface` + `switch` com pattern matching para resultados com variantes.
- `Optional<T>` para retorno ausente; **nunca** retorne `null` de método público.
- Coleção vazia em vez de `null`.
- `var` apenas quando o tipo é evidente no lado direito.
- Virtual threads: habilite `spring.threads.virtual.enabled=true` antes de recorrer a
  pools manuais.

## Injeção de dependências

Somente por construtor. `@Autowired` em campo é proibido.

```java
// ✅ com Lombok, o padrão deste projeto
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartidaService {
    private final PartidaRepository partidaRepository;
    private final PartidaMapper partidaMapper;
}
```

Mais de 4~5 dependências no construtor é sinal de que a classe acumula
responsabilidades — divida antes de continuar.

## Lombok — uso restrito

Permitido: `@RequiredArgsConstructor`, `@Getter`, `@Setter`, `@Builder`, `@Slf4j`.

**Proibido em entidades JPA:** `@Data`, `@EqualsAndHashCode`, `@ToString` sem
`exclude`. Eles disparam lazy loading, quebram `equals`/`hashCode` de entidade e
geram `StackOverflowError` em relações bidirecionais. Em `@Entity`, implemente
`equals`/`hashCode` pelo identificador de negócio ou pelo id com verificação de
proxy.

## MapStruct — o build falha em mapeamento incompleto

`build.gradle` define `-Amapstruct.unmappedTargetPolicy=ERROR` e
`-Amapstruct.defaultComponentModel=spring`. Isso significa:

- Todo campo do alvo precisa de origem explícita, ou `@Mapping(target = "x", ignore = true)`.
- **Não** escreva `componentModel = "spring"` no `@Mapper` — já é o padrão.
- A ordem dos annotation processors no `build.gradle` (lombok → binding → mapstruct)
  é obrigatória. Não reordene.

```java
@Mapper
public interface PartidaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    Partida paraEntidade(NovaPartida dto);

    PartidaResumo paraResumo(Partida entidade);
}
```

## Transações

- `@Transactional(readOnly = true)` na classe do service.
- `@Transactional` no método que escreve.
- Nunca em controller.
- Nunca chame HTTP, Redis externo, envio de e-mail ou fila **dentro** da transação —
  publique um evento e trate no listener.
- Operações concorrentes sobre o mesmo recurso (agenda, saldo, vaga) exigem lock
  otimista (`@Version`), constraint única ou chave de idempotência.

## Configuração

- `src/main/resources/application.properties` é a configuração base.
- Segredos e endpoints por ambiente vêm de variáveis: `${DATABASE_URL}`,
  `${DATABASE_PASSWORD}`, `${REDIS_HOST}`. Nada de credencial literal.
- O `compose.yaml` na raiz do módulo contém credenciais de **desenvolvimento local**.
  Não replique esses valores em código nem em configuração de outro ambiente.
- Novas variáveis obrigatórias: documente neste repositório e valide na inicialização
  com `@ConfigurationProperties` + Bean Validation.
- Recomendado (ainda não configurado): `spring.jpa.hibernate.ddl-auto=validate`, para
  que o schema seja governado exclusivamente pelo Flyway.

## Restrições operacionais

- **Não suba serviços** (`docker compose up`, `./gradlew bootRun`) sem pedido explícito.
  `bootRun` levanta Postgres e Redis automaticamente via `spring-boot-docker-compose`.
- **Não rode `./gradlew test` a cada alteração** — os testes usam Testcontainers e são
  caros. Use `./gradlew compileJava` para validar e rode a suíte antes do push.
- **Migrations aplicadas são imutáveis.** Corrija com uma nova migration.
- **Não commite segredos**, `.env` real ou dump de banco.
- **Não altere a ordem dos annotation processors** nem os flags de compilação do
  MapStruct sem entender o impacto.
- Não gere código a partir de memória sobre Spring Boot 3 — confira a assinatura real
  no classpath quando a API divergir do esperado.

## Módulos relacionados

- `.rules/java-api.md` — controllers, DTOs, validação, erros com ProblemDetail, OpenAPI, paginação
- `.rules/java-testing.md` — JUnit 5, Testcontainers, testes de modularidade
- `.rules/database.md` — Flyway, schema PostgreSQL, JPA e Redis
- `.rules/security.md` — Spring Security, CORS, segredos, dados pessoais
- `.rules/java-checklist.md` — checklist antes de encerrar qualquer tarefa
- `.rules/commit.md` — padrão de commit

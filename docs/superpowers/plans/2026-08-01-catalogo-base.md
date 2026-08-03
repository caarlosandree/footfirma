# Catálogo Base — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o schema e a API REST read-only do catálogo de futebol brasileiro — temporadas, países, clubes, jogadores e competições — sobre o qual os planos de avaliação/progressão e de carga de dados serão construídos.

**Architecture:** Cinco módulos Spring Modulith (`temporada`, `geografia`, `clube`, `jogador`, `competicao`), cada um dono exclusivo das suas tabelas, com schema governado por Flyway e entidades JPA validadas contra ele (`ddl-auto=validate`). Cada módulo expõe uma interface de serviço no seu pacote raiz e mantém entidade, repositório, controller e mapper em subpacotes internos. A API é read-only: nenhuma escrita existe neste plano — ela chega no Plano 3, via importador.

**Tech Stack:** Java 25 · Spring Boot 4.1.0 · Spring Modulith 2.1.0 · PostgreSQL · Flyway · MapStruct 1.6.3 · Lombok · springdoc-openapi 3.0.3 · JUnit 5 · Testcontainers

## Global Constraints

Todo trabalho acontece em `backend/footfirma/`. Estas regras valem para **todas** as tarefas:

- **Pacote base:** `br.com.api.footfirma`. Cada pacote diretamente abaixo dele é um módulo Modulith.
- **Nomes em português** — pacotes, tabelas, colunas, classes, métodos. Exigência de `.rules/java-core.md` e `.rules/database.md`.
- **Estratégia de ID: `bigint generated always as identity`** em toda tabela, mapeado com `@GeneratedValue(strategy = GenerationType.IDENTITY)` e campo `Long`. O identificador público estável é o `slug`, não o id. Escolhido por volume (o Plano 2 cria ~10k linhas de overall) e por o catálogo ser dado importado, sem necessidade de gerar id no cliente.
- **Tipos:** `text` com `check` de tamanho (nunca `varchar(n)`), `timestamptz` para data-hora, `date` para data pura, `numeric(14,2)` para dinheiro, enum como `text` + `check` mapeado com `@Enumerated(EnumType.STRING)`.
- **Migrations:** `src/main/resources/db/migration/V{n}__descricao_snake_case.sql`, numeração sequencial sem buracos. **Migration aplicada nunca é editada** — o hook `.claude/hooks/guard.mjs` bloqueia isso.
- **Entidades JPA:** proibido `@Data`, `@EqualsAndHashCode`, `@ToString`. `equals`/`hashCode` escritos à mão pelo id. Construtor sem argumentos `protected` escrito à mão (não `@NoArgsConstructor`). Todo `@ManyToOne`/`@OneToOne` é `LAZY`.
- **Lombok permitido:** apenas `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`.
- **MapStruct:** `unmappedTargetPolicy=ERROR` está ativo — todo campo do alvo precisa de `@Mapping` explícito ou `ignore = true`, senão o build falha. **Nunca** escreva `componentModel = "spring"`; já é padrão do build.
- **Visibilidade Modulith:** tipos no pacote raiz do módulo são API pública; tipos em `web/`, `domain/`, `repository/`, `mapper/`, `internal/` são invisíveis para outros módulos. Entidade JPA nunca cruza fronteira de módulo.
- **DI só por construtor.** `@Autowired` em campo é proibido.
- **Transações:** `@Transactional(readOnly = true)` na classe do service. Nunca em controller.
- **Testes:** Testcontainers sempre. H2 e mock de repository são proibidos. `@DataJpaTest` exige `@AutoConfigureTestDatabase(replace = Replace.NONE)`. Use `@MockitoBean`, nunca `@MockBean`.
- **Nomes de teste:** `deve<Comportamento>Quando<Condição>`, em português.
- **Commits:** Conventional Commits em português, escopo = módulo. **Nunca** adicione `Co-authored-by:`.
- **Não rode `./gradlew test` inteiro a cada passo** — use `--tests '*NomeDoTest'`. Não rode `bootRun` (o guard bloqueia).

**Validação rápida durante o trabalho:** `./gradlew compileJava`

---

## Estrutura de arquivos

Cada módulo segue a mesma forma. Responsabilidade por arquivo:

```
src/main/java/br/com/api/footfirma/
├── config/
│   ├── package-info.java          # @ApplicationModule(type = OPEN)
│   ├── OpenApiConfig.java         # (já existe)
│   └── SecurityConfig.java        # rotas públicas, uma a uma
├── shared/
│   ├── package-info.java          # @ApplicationModule(type = OPEN)
│   └── exception/
│       ├── RecursoNaoEncontradoException.java
│       └── TratadorDeErros.java   # @RestControllerAdvice → ProblemDetail
└── <modulo>/
    ├── package-info.java          # @ApplicationModule(displayName = "...")
    ├── <Modulo>Service.java       # interface pública — a única porta do módulo
    ├── dto/                       # records expostos pelo service e pela API
    ├── domain/                    # @Entity + enums
    ├── repository/                # Spring Data
    ├── mapper/                    # MapStruct
    ├── web/                       # @RestController package-private
    └── internal/                  # implementação do service
```

Migrations, em ordem:

| Arquivo | Cria |
|---|---|
| `V1__cria_event_publication.sql` | `event_publication` (registro de eventos do Modulith) |
| `V2__cria_temporada.sql` | `temporada` |
| `V3__cria_geografia.sql` | `pais`, `estado` |
| `V4__popula_geografia.sql` | Brasil + 27 unidades federativas |
| `V5__cria_clube.sql` | `estadio`, `clube`, `clube_alias`, `clube_referencia_externa` |
| `V6__cria_jogador.sql` | `posicao`, `jogador`, `jogador_posicao`, `jogador_referencia_externa` |
| `V7__popula_posicao.sql` | as 9 posições |
| `V8__cria_jogador_atributo.sql` | `jogador_atributo`, `jogador_atributo_oculto` |
| `V9__cria_caracteristica_e_vinculo.sql` | `caracteristica`, `jogador_caracteristica`, `jogador_vinculo` |
| `V10__cria_competicao.sql` | `competicao`, `edicao`, `fase`, `competicao_referencia_externa` |
| `V11__cria_participante_e_regra.sql` | `edicao_participante`, `regra_classificacao` |

---

## Task 1: Fundação — modularidade, segurança e tratamento de erros

Prepara o terreno: declara os módulos abertos existentes, torna a configuração de Testcontainers reutilizável, cria o teste de modularidade que passa a guardar as fronteiras, e o tratador de erros que todas as APIs usarão.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/config/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/config/SecurityConfig.java`
- Create: `src/main/java/br/com/api/footfirma/shared/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/shared/exception/RecursoNaoEncontradoException.java`
- Create: `src/main/java/br/com/api/footfirma/shared/exception/TratadorDeErros.java`
- Create: `src/main/resources/db/migration/V1__cria_event_publication.sql`
- Modify: `src/test/java/br/com/api/footfirma/TestcontainersConfiguration.java` (visibilidade)
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/br/com/api/footfirma/ModularidadeTest.java`

**Interfaces:**
- Produces: `RecursoNaoEncontradoException(String mensagem)` — exceção lançada por todo service quando um slug não existe. `TestcontainersConfiguration` público, importável por qualquer teste via `@Import`.

- [x] **Step 1: Declarar os módulos abertos e tornar Testcontainers reutilizável**

`config/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Configuração"
)
package br.com.api.footfirma.config;
```

`shared/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Compartilhado"
)
package br.com.api.footfirma.shared;
```

Em `TestcontainersConfiguration.java`, troque a declaração da classe de package-private para pública, para que testes em outros pacotes possam importá-la:

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
```

- [x] **Step 2: Escrever o teste de modularidade**

`src/test/java/br/com/api/footfirma/ModularidadeTest.java`:

```java
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
```

- [x] **Step 3: Rodar o teste para confirmar que passa**

Run: `./gradlew test --tests '*ModularidadeTest'`
Expected: PASS — só existem `config` e `shared`, ambos abertos, sem violação possível ainda. Este teste é a rede que protegerá as tarefas seguintes.

- [x] **Step 4: Criar a exceção de recurso ausente e o tratador de erros**

`shared/exception/RecursoNaoEncontradoException.java`:

```java
package br.com.api.footfirma.shared.exception;

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
```

`shared/exception/TratadorDeErros.java`:

```java
package br.com.api.footfirma.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
class TratadorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excecao.getMessage());
        problema.setTitle("Recurso não encontrado");
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalido(MethodArgumentNotValidException excecao) {
        var problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problema.setTitle("Dados inválidos");
        problema.setProperty("campos", excecao.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        erro -> erro.getDefaultMessage() == null ? "inválido" : erro.getDefaultMessage(),
                        (primeiro, segundo) -> primeiro)));
        return problema;
    }
}
```

- [x] **Step 5: Configurar a segurança**

O catálogo é dado público de futebol, sem informação pessoal de usuário do sistema. As rotas de leitura são liberadas **uma a uma**, deliberadamente — nunca `anyRequest().permitAll()`.

`config/SecurityConfig.java`:

```java
package br.com.api.footfirma.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clubes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/jogadores/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/competicoes/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
```

`csrf().disable()` é válido porque a API é stateless e não autentica por cookie. Se isso mudar, CSRF volta a ser obrigatório.

- [x] **Step 6: Configurar Flyway e validação de schema**

Acrescente ao final de `src/main/resources/application.properties`:

```properties
# Schema governado exclusivamente por Flyway — ddl-auto nunca gera DDL
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.threads.virtual.enabled=true
```

`open-in-view=false` impede que associações LAZY sejam carregadas na serialização da resposta, que é a origem mais comum de N+1 silencioso.

- [x] **Step 7: Compilar e rodar os testes existentes**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest' --tests '*FootfirmaApplicationTests'`
Expected: PASS nos dois — a `V1__cria_event_publication.sql` é o que sustenta o
`FootfirmaApplicationTests` verde. Sem ela, `ddl-auto=validate` derruba o contexto com
`missing table [event_publication]`: o `spring-modulith-starter-jpa` mapeia a entidade
`DefaultJpaEventPublication` para essa tabela e ela precisa existir desde o começo.

- [x] **Step 8: Commit**

```bash
git add src/main/java/br/com/api/footfirma/config src/main/java/br/com/api/footfirma/shared \
        src/main/resources/application.properties \
        src/main/resources/db/migration/V1__cria_event_publication.sql \
        src/test/java/br/com/api/footfirma/ModularidadeTest.java \
        src/test/java/br/com/api/footfirma/TestcontainersConfiguration.java
git commit -m "feat(config): adiciona fundação de modularidade, segurança e erros

- Declara config e shared como módulos Modulith abertos
- Cria ModularidadeTest para guardar as fronteiras desde o início
- Adiciona TratadorDeErros com ProblemDetail (RFC 9457)
- SecurityConfig libera leitura do catálogo rota a rota
- Fixa ddl-auto=validate: o schema passa a ser só do Flyway
- Migration V1 cria event_publication, exigida pelo Modulith sob validate"
```

---

## Task 2: Módulo `temporada`

Menor módulo do sistema e o primeiro a ser criado porque `jogador` e `competicao` dependem dele. Estabelece o padrão que todos os outros módulos seguem.

**Files:**
- Create: `src/main/resources/db/migration/V2__cria_temporada.sql`
- Create: `src/main/java/br/com/api/footfirma/temporada/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/temporada/TemporadaService.java`
- Create: `src/main/java/br/com/api/footfirma/temporada/dto/TemporadaResumo.java`
- Create: `src/main/java/br/com/api/footfirma/temporada/domain/Temporada.java`
- Create: `src/main/java/br/com/api/footfirma/temporada/repository/TemporadaRepository.java`
- Create: `src/main/java/br/com/api/footfirma/temporada/mapper/TemporadaMapper.java`
- Create: `src/main/java/br/com/api/footfirma/temporada/internal/TemporadaServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/temporada/TemporadaRepositoryTest.java`

**Interfaces:**
- Produces: `TemporadaService.buscarPorLabel(String label) -> Optional<TemporadaResumo>` e `TemporadaService.listarOrdenadas() -> List<TemporadaResumo>`. `TemporadaResumo(Long id, String label, int anoInicio, int anoFim)`. Os módulos `jogador` e `competicao` consomem `TemporadaService`; suas entidades referenciam a tabela `temporada` por FK, mas **nunca** importam a classe `Temporada`.

- [x] **Step 1: Escrever a migration**

`src/main/resources/db/migration/V2__cria_temporada.sql`:

```sql
-- Temporada é referenciada por jogador_atributo, jogador_vinculo e edicao.
-- Vive em módulo próprio para que jogador não precise depender de competicao.
create table temporada (
    id         bigint generated always as identity primary key,
    label      text        not null unique check (length(label) between 4 and 9),
    ano_inicio integer     not null check (ano_inicio between 1900 and 2200),
    ano_fim    integer     not null check (ano_fim between 1900 and 2200),
    criado_em  timestamptz not null default now(),
    constraint ck_temporada_anos check (ano_fim >= ano_inicio)
);

comment on column temporada.label is 'Rótulo exibível: "2025" para temporada de ano civil, "2025/26" para temporada europeia';
```

- [x] **Step 2: Escrever o teste de repositório (vai falhar)**

`src/test/java/br/com/api/footfirma/temporada/TemporadaRepositoryTest.java`:

```java
package br.com.api.footfirma.temporada;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.temporada.domain.Temporada;
import br.com.api.footfirma.temporada.repository.TemporadaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TemporadaRepositoryTest {

    @Autowired
    TemporadaRepository temporadaRepository;

    @Test
    void deveEncontrarTemporadaPeloLabel() {
        temporadaRepository.save(novaTemporada("2025", 2025, 2025));

        var encontrada = temporadaRepository.findByLabel("2025");

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getAnoInicio()).isEqualTo(2025);
    }

    @Test
    void deveListarTemporadasDaMaisRecenteParaAMaisAntiga() {
        temporadaRepository.save(novaTemporada("2024", 2024, 2024));
        temporadaRepository.save(novaTemporada("2026", 2026, 2026));
        temporadaRepository.save(novaTemporada("2025", 2025, 2025));

        var temporadas = temporadaRepository.findAllByOrderByAnoInicioDesc();

        assertThat(temporadas).extracting(Temporada::getLabel)
                .containsExactly("2026", "2025", "2024");
    }

    private Temporada novaTemporada(String label, int anoInicio, int anoFim) {
        var temporada = new Temporada();
        temporada.setLabel(label);
        temporada.setAnoInicio(anoInicio);
        temporada.setAnoFim(anoFim);
        return temporada;
    }
}
```

- [x] **Step 3: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*TemporadaRepositoryTest'`
Expected: FAIL na compilação — `Temporada` e `TemporadaRepository` não existem.

- [x] **Step 4: Criar a entidade e o repositório**

`temporada/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Temporada")
package br.com.api.footfirma.temporada;
```

`temporada/domain/Temporada.java` — note o construtor protegido escrito à mão e o `equals`/`hashCode` seguro para proxy:

```java
package br.com.api.footfirma.temporada.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "temporada")
@Getter
@Setter
public class Temporada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String label;

    @Column(name = "ano_inicio", nullable = false)
    private Integer anoInicio;

    @Column(name = "ano_fim", nullable = false)
    private Integer anoFim;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected Temporada() {
    }

    public Temporada(String label, Integer anoInicio, Integer anoFim) {
        this.label = label;
        this.anoInicio = anoInicio;
        this.anoFim = anoFim;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Temporada temporada)) {
            return false;
        }
        return id != null && id.equals(temporada.id);
    }

    @Override
    public int hashCode() {
        return Temporada.class.hashCode();
    }
}
```

`temporada/repository/TemporadaRepository.java`:

```java
package br.com.api.footfirma.temporada.repository;

import br.com.api.footfirma.temporada.domain.Temporada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemporadaRepository extends JpaRepository<Temporada, Long> {

    Optional<Temporada> findByLabel(String label);

    List<Temporada> findAllByOrderByAnoInicioDesc();
}
```

- [x] **Step 5: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*TemporadaRepositoryTest'`
Expected: PASS nos dois testes. Se falhar com erro de validação de schema, compare os nomes de coluna da entidade com a migration — `ddl-auto=validate` está ativo justamente para pegar isso.

- [x] **Step 6: Criar o DTO, o mapper e o serviço público**

`temporada/dto/TemporadaResumo.java`:

```java
package br.com.api.footfirma.temporada.dto;

public record TemporadaResumo(Long id, String label, Integer anoInicio, Integer anoFim) {
}
```

`temporada/mapper/TemporadaMapper.java`:

```java
package br.com.api.footfirma.temporada.mapper;

import br.com.api.footfirma.temporada.domain.Temporada;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import org.mapstruct.Mapper;

@Mapper
public interface TemporadaMapper {

    TemporadaResumo paraResumo(Temporada temporada);
}
```

`temporada/TemporadaService.java` — a única porta pública do módulo:

```java
package br.com.api.footfirma.temporada;

import br.com.api.footfirma.temporada.dto.TemporadaResumo;

import java.util.List;
import java.util.Optional;

public interface TemporadaService {

    Optional<TemporadaResumo> buscarPorLabel(String label);

    List<TemporadaResumo> listarOrdenadas();
}
```

`temporada/internal/TemporadaServiceImpl.java`:

```java
package br.com.api.footfirma.temporada.internal;

import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import br.com.api.footfirma.temporada.mapper.TemporadaMapper;
import br.com.api.footfirma.temporada.repository.TemporadaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TemporadaServiceImpl implements TemporadaService {

    private final TemporadaRepository temporadaRepository;
    private final TemporadaMapper temporadaMapper;

    @Override
    public Optional<TemporadaResumo> buscarPorLabel(String label) {
        return temporadaRepository.findByLabel(label).map(temporadaMapper::paraResumo);
    }

    @Override
    public List<TemporadaResumo> listarOrdenadas() {
        return temporadaRepository.findAllByOrderByAnoInicioDesc().stream()
                .map(temporadaMapper::paraResumo)
                .toList();
    }
}
```

- [x] **Step 7: Compilar e rodar modularidade**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest' --tests '*TemporadaRepositoryTest'`
Expected: PASS. Se o MapStruct reclamar de campo não mapeado, é o `unmappedTargetPolicy=ERROR` — todo campo do `TemporadaResumo` precisa de origem no `Temporada`.

- [x] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V2__cria_temporada.sql \
        src/main/java/br/com/api/footfirma/temporada \
        src/test/java/br/com/api/footfirma/temporada
git commit -m "feat(temporada): cria módulo de temporada

Primeiro módulo de domínio do projeto. Estabelece o padrão que os demais
seguem: interface pública na raiz, entidade e repositório em subpacotes
internos, DTO via MapStruct.

- Migration V2 cria a tabela temporada
- TemporadaService expõe busca por label e listagem ordenada"
```

---

## Task 3: Módulo `geografia`

País e unidade federativa. Referenciado por `clube` e `jogador`. Inclui seed dos dados reais, porque país e UF são catálogo estável — não dependem do pipeline de importação.

**Files:**
- Create: `src/main/resources/db/migration/V3__cria_geografia.sql`
- Create: `src/main/resources/db/migration/V4__popula_geografia.sql`
- Create: `src/main/java/br/com/api/footfirma/geografia/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/GeografiaService.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/dto/PaisResumo.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/dto/EstadoResumo.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/domain/Pais.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/domain/Estado.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/domain/Confederacao.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/repository/PaisRepository.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/repository/EstadoRepository.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/mapper/GeografiaMapper.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/internal/GeografiaServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/geografia/GeografiaRepositoryTest.java`

**Interfaces:**
- Produces: `GeografiaService.buscarPaisPorIso(String iso) -> Optional<PaisResumo>`, `GeografiaService.listarEstadosDoPais(String iso) -> List<EstadoResumo>`. `PaisResumo(Long id, String isoCode, String nome, String confederacao)`, `EstadoResumo(Long id, String uf, String nome)`.

- [x] **Step 1: Escrever as migrations**

`src/main/resources/db/migration/V3__cria_geografia.sql`:

```sql
create table pais (
    id           bigint generated always as identity primary key,
    iso_code     text        not null unique check (length(iso_code) = 3),
    nome         text        not null check (length(nome) between 1 and 80),
    confederacao text        not null check (confederacao in ('CONMEBOL', 'UEFA', 'CONCACAF', 'CAF', 'AFC', 'OFC')),
    criado_em    timestamptz not null default now()
);

create table estado (
    id      bigint generated always as identity primary key,
    pais_id bigint not null references pais (id),
    uf      text   not null check (length(uf) = 2),
    nome    text   not null check (length(nome) between 1 and 60),
    constraint uq_estado_pais_uf unique (pais_id, uf)
);

create index idx_estado_pais on estado (pais_id);
```

`src/main/resources/db/migration/V4__popula_geografia.sql`:

```sql
-- País e unidade federativa são catálogo estável: não dependem do pipeline de
-- importação e por isso entram como seed versionado.
insert into pais (iso_code, nome, confederacao) values ('BRA', 'Brasil', 'CONMEBOL');

insert into estado (pais_id, uf, nome)
select p.id, dados.uf, dados.nome
from pais p,
     (values ('AC', 'Acre'), ('AL', 'Alagoas'), ('AP', 'Amapá'), ('AM', 'Amazonas'),
             ('BA', 'Bahia'), ('CE', 'Ceará'), ('DF', 'Distrito Federal'),
             ('ES', 'Espírito Santo'), ('GO', 'Goiás'), ('MA', 'Maranhão'),
             ('MT', 'Mato Grosso'), ('MS', 'Mato Grosso do Sul'), ('MG', 'Minas Gerais'),
             ('PA', 'Pará'), ('PB', 'Paraíba'), ('PR', 'Paraná'), ('PE', 'Pernambuco'),
             ('PI', 'Piauí'), ('RJ', 'Rio de Janeiro'), ('RN', 'Rio Grande do Norte'),
             ('RS', 'Rio Grande do Sul'), ('RO', 'Rondônia'), ('RR', 'Roraima'),
             ('SC', 'Santa Catarina'), ('SP', 'São Paulo'), ('SE', 'Sergipe'),
             ('TO', 'Tocantins')) as dados(uf, nome)
where p.iso_code = 'BRA';
```

- [x] **Step 2: Escrever o teste (vai falhar)**

`src/test/java/br/com/api/footfirma/geografia/GeografiaRepositoryTest.java`:

```java
package br.com.api.footfirma.geografia;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.geografia.domain.Confederacao;
import br.com.api.footfirma.geografia.repository.EstadoRepository;
import br.com.api.footfirma.geografia.repository.PaisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GeografiaRepositoryTest {

    @Autowired
    PaisRepository paisRepository;

    @Autowired
    EstadoRepository estadoRepository;

    @Test
    void deveEncontrarBrasilCarregadoPelaMigrationDeSeed() {
        var brasil = paisRepository.findByIsoCode("BRA");

        assertThat(brasil).isPresent();
        assertThat(brasil.get().getNome()).isEqualTo("Brasil");
        assertThat(brasil.get().getConfederacao()).isEqualTo(Confederacao.CONMEBOL);
    }

    @Test
    void deveCarregarAsVinteESeteUnidadesFederativas() {
        var estados = estadoRepository.findByPaisIsoCodeOrderByNome();

        assertThat(estados).hasSize(27);
        assertThat(estados.getFirst().getUf()).isEqualTo("AC");
    }
}
```

- [x] **Step 3: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*GeografiaRepositoryTest'`
Expected: FAIL na compilação — as classes de `geografia` não existem.

- [x] **Step 4: Criar o enum, as entidades e os repositórios**

`geografia/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Geografia")
package br.com.api.footfirma.geografia;
```

`geografia/domain/Confederacao.java`:

```java
package br.com.api.footfirma.geografia.domain;

public enum Confederacao {
    CONMEBOL, UEFA, CONCACAF, CAF, AFC, OFC
}
```

`geografia/domain/Pais.java`:

```java
package br.com.api.footfirma.geografia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pais")
@Getter
@Setter
public class Pais {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", nullable = false, unique = true)
    private String isoCode;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confederacao confederacao;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected Pais() {
    }

    public Pais(String isoCode, String nome, Confederacao confederacao) {
        this.isoCode = isoCode;
        this.nome = nome;
        this.confederacao = confederacao;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Pais pais)) {
            return false;
        }
        return id != null && id.equals(pais.id);
    }

    @Override
    public int hashCode() {
        return Pais.class.hashCode();
    }
}
```

`geografia/domain/Estado.java` — o `@ManyToOne` é `LAZY`, sem exceção:

```java
package br.com.api.footfirma.geografia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "estado")
@Getter
@Setter
public class Estado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pais_id", nullable = false)
    private Pais pais;

    @Column(nullable = false)
    private String uf;

    @Column(nullable = false)
    private String nome;

    protected Estado() {
    }

    public Estado(Pais pais, String uf, String nome) {
        this.pais = pais;
        this.uf = uf;
        this.nome = nome;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Estado estado)) {
            return false;
        }
        return id != null && id.equals(estado.id);
    }

    @Override
    public int hashCode() {
        return Estado.class.hashCode();
    }
}
```

`geografia/repository/PaisRepository.java`:

```java
package br.com.api.footfirma.geografia.repository;

import br.com.api.footfirma.geografia.domain.Pais;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaisRepository extends JpaRepository<Pais, Long> {

    Optional<Pais> findByIsoCode(String isoCode);
}
```

`geografia/repository/EstadoRepository.java` — a query com `join fetch` evita N+1 ao acessar o país:

```java
package br.com.api.footfirma.geografia.repository;

import br.com.api.footfirma.geografia.domain.Estado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EstadoRepository extends JpaRepository<Estado, Long> {

    @Query("select e from Estado e join fetch e.pais p where p.isoCode = :isoCode order by e.uf")
    List<Estado> findByPaisIsoCode(@Param("isoCode") String isoCode);

    @Query("select e from Estado e join fetch e.pais p where p.isoCode = 'BRA' order by e.uf")
    List<Estado> findByPaisIsoCodeOrderByNome();
}
```

- [x] **Step 5: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*GeografiaRepositoryTest'`
Expected: PASS. Os 27 estados vêm da migration de seed, não do teste — se vier 0, a `V4` não rodou; confira o log do Flyway na saída.

- [x] **Step 6: Criar DTOs, mapper e serviço público**

`geografia/dto/PaisResumo.java`:

```java
package br.com.api.footfirma.geografia.dto;

public record PaisResumo(Long id, String isoCode, String nome, String confederacao) {
}
```

`geografia/dto/EstadoResumo.java`:

```java
package br.com.api.footfirma.geografia.dto;

public record EstadoResumo(Long id, String uf, String nome) {
}
```

`geografia/mapper/GeografiaMapper.java`:

```java
package br.com.api.footfirma.geografia.mapper;

import br.com.api.footfirma.geografia.domain.Estado;
import br.com.api.footfirma.geografia.domain.Pais;
import br.com.api.footfirma.geografia.dto.EstadoResumo;
import br.com.api.footfirma.geografia.dto.PaisResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface GeografiaMapper {

    @Mapping(target = "confederacao", expression = "java(pais.getConfederacao().name())")
    PaisResumo paraResumo(Pais pais);

    EstadoResumo paraResumo(Estado estado);
}
```

`geografia/GeografiaService.java`:

```java
package br.com.api.footfirma.geografia;

import br.com.api.footfirma.geografia.dto.EstadoResumo;
import br.com.api.footfirma.geografia.dto.PaisResumo;

import java.util.List;
import java.util.Optional;

public interface GeografiaService {

    Optional<PaisResumo> buscarPaisPorIso(String isoCode);

    List<EstadoResumo> listarEstadosDoPais(String isoCode);
}
```

`geografia/internal/GeografiaServiceImpl.java`:

```java
package br.com.api.footfirma.geografia.internal;

import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.geografia.dto.EstadoResumo;
import br.com.api.footfirma.geografia.dto.PaisResumo;
import br.com.api.footfirma.geografia.mapper.GeografiaMapper;
import br.com.api.footfirma.geografia.repository.EstadoRepository;
import br.com.api.footfirma.geografia.repository.PaisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class GeografiaServiceImpl implements GeografiaService {

    private final PaisRepository paisRepository;
    private final EstadoRepository estadoRepository;
    private final GeografiaMapper geografiaMapper;

    @Override
    public Optional<PaisResumo> buscarPaisPorIso(String isoCode) {
        return paisRepository.findByIsoCode(isoCode).map(geografiaMapper::paraResumo);
    }

    @Override
    public List<EstadoResumo> listarEstadosDoPais(String isoCode) {
        return estadoRepository.findByPaisIsoCode(isoCode).stream()
                .map(geografiaMapper::paraResumo)
                .toList();
    }
}
```

- [x] **Step 7: Compilar e verificar modularidade**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest' --tests '*GeografiaRepositoryTest'`
Expected: PASS.

- [x] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V3__cria_geografia.sql \
        src/main/resources/db/migration/V4__popula_geografia.sql \
        src/main/java/br/com/api/footfirma/geografia \
        src/test/java/br/com/api/footfirma/geografia
git commit -m "feat(geografia): cria módulo de país e unidade federativa

- Migration V3 cria pais e estado
- Migration V4 popula Brasil e as 27 UFs como seed versionado, por serem
  catálogo estável que não depende do pipeline de importação
- GeografiaService expõe busca de país por ISO e listagem de estados"
```

---

## Task 4: Módulo `clube`

Clube, estádio, aliases de nome e referência externa. Os aliases resolvem "Atlético-MG" / "Atlético Mineiro" / "Clube Atlético Mineiro" para o mesmo clube — sem eles, o importador do Plano 3 duplica clubes a cada fonte nova.

**Files:**
- Create: `src/main/resources/db/migration/V5__cria_clube.sql`
- Create: `src/main/java/br/com/api/footfirma/clube/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/clube/ClubeService.java`
- Create: `src/main/java/br/com/api/footfirma/clube/dto/ClubeResumo.java`
- Create: `src/main/java/br/com/api/footfirma/clube/dto/ClubeDetalhe.java`
- Create: `src/main/java/br/com/api/footfirma/clube/domain/Clube.java`
- Create: `src/main/java/br/com/api/footfirma/clube/domain/Estadio.java`
- Create: `src/main/java/br/com/api/footfirma/clube/domain/ClubeAlias.java`
- Create: `src/main/java/br/com/api/footfirma/clube/domain/FonteExterna.java`
- Create: `src/main/java/br/com/api/footfirma/clube/repository/ClubeRepository.java`
- Create: `src/main/java/br/com/api/footfirma/clube/repository/EstadioRepository.java`
- Create: `src/main/java/br/com/api/footfirma/clube/repository/ClubeAliasRepository.java`
- Create: `src/main/java/br/com/api/footfirma/clube/mapper/ClubeMapper.java`
- Create: `src/main/java/br/com/api/footfirma/clube/internal/ClubeServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/clube/ClubeRepositoryTest.java`
- Test: `src/test/java/br/com/api/footfirma/clube/ClubeFactory.java`

**Interfaces:**
- Consumes: tabela `pais` e `estado` (Task 3) por FK. **Não** importa `Pais` nem `Estado` — as FKs são `Long` puros, porque entidade JPA não cruza fronteira de módulo.
- Produces: `ClubeService.buscarPorSlug(String slug) -> Optional<ClubeDetalhe>`, `ClubeService.listar(Pageable) -> Page<ClubeResumo>`, `ClubeService.resolverPorAlias(String alias) -> Optional<ClubeResumo>`. `ClubeResumo(Long id, String slug, String nomeCurto, Integer reputacao)`, `ClubeDetalhe(Long id, String slug, String nomeOficial, String nomeCurto, String apelido, Integer anoFundacao, String estadio, Integer capacidadeEstadio, Integer reputacao, Integer qualidadeBase)`.

**Decisão de modelagem:** as FKs para `pais` e `estado` são colunas `Long` (`paisId`, `estadoId`), não `@ManyToOne`. Um `@ManyToOne` para `Pais` obrigaria o módulo `clube` a importar `geografia.domain.Pais`, o que o `ModularidadeTest` reprova. Quando `clube` precisar do nome do estado, pede ao `GeografiaService`.

- [x] **Step 1: Escrever a migration**

`src/main/resources/db/migration/V5__cria_clube.sql`:

```sql
create table estadio (
    id              bigint generated always as identity primary key,
    nome            text        not null check (length(nome) between 1 and 120),
    cidade          text        not null check (length(cidade) between 1 and 80),
    estado_id       bigint      references estado (id),
    capacidade      integer     check (capacidade > 0),
    ano_inauguracao integer     check (ano_inauguracao between 1800 and 2200),
    criado_em       timestamptz not null default now()
);

create index idx_estadio_estado on estadio (estado_id);

create table clube (
    id             bigint generated always as identity primary key,
    slug           text        not null unique check (length(slug) between 2 and 80),
    nome_oficial   text        not null check (length(nome_oficial) between 1 and 120),
    nome_curto     text        not null check (length(nome_curto) between 1 and 40),
    apelido        text        check (length(apelido) between 1 and 60),
    ano_fundacao   integer     check (ano_fundacao between 1800 and 2200),
    pais_id        bigint      not null references pais (id),
    estado_id      bigint      references estado (id),
    estadio_id     bigint      references estadio (id),
    cor_primaria   text        check (cor_primaria ~ '^#[0-9A-Fa-f]{6}$'),
    cor_secundaria text        check (cor_secundaria ~ '^#[0-9A-Fa-f]{6}$'),
    reputacao      integer     not null default 50 check (reputacao between 0 and 99),
    qualidade_base integer     not null default 50 check (qualidade_base between 0 and 99),
    estado_base_id bigint      references estado (id),
    criado_em      timestamptz not null default now(),
    atualizado_em  timestamptz
);

create index idx_clube_pais on clube (pais_id);
create index idx_clube_estado on clube (estado_id);
create index idx_clube_estadio on clube (estadio_id);

comment on column clube.qualidade_base is 'Qualidade da categoria de base (0-99). Alimenta o gerador de jogadores no Plano 2';
comment on column clube.estado_base_id is 'Estado de onde a base do clube tende a recrutar. Nulo = sem viés regional';

create table clube_alias (
    id       bigint generated always as identity primary key,
    clube_id bigint not null references clube (id),
    alias    text   not null check (length(alias) between 1 and 120),
    fonte    text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT', 'MANUAL')),
    constraint uq_clube_alias unique (alias, fonte)
);

create index idx_clube_alias_clube on clube_alias (clube_id);

create table clube_referencia_externa (
    id         bigint generated always as identity primary key,
    clube_id   bigint not null references clube (id),
    fonte      text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT')),
    id_externo text   not null check (length(id_externo) between 1 and 60),
    constraint uq_clube_referencia_externa unique (fonte, id_externo)
);

create index idx_clube_referencia_externa_clube on clube_referencia_externa (clube_id);
```

- [x] **Step 2: Escrever a factory de teste**

Massa de teste repetida vira factory, conforme `.rules/java-testing.md`.

`src/test/java/br/com/api/footfirma/clube/ClubeFactory.java`:

```java
package br.com.api.footfirma.clube;

import br.com.api.footfirma.clube.domain.Clube;

final class ClubeFactory {

    private ClubeFactory() {
    }

    static Clube valido(String slug, String nomeCurto, Long paisId) {
        var clube = new Clube(slug, nomeCurto + " Futebol Clube", nomeCurto, paisId);
        clube.setAnoFundacao(1900);
        clube.setReputacao(70);
        clube.setQualidadeBase(60);
        return clube;
    }
}
```

- [x] **Step 3: Escrever o teste de repositório (vai falhar)**

`src/test/java/br/com/api/footfirma/clube/ClubeRepositoryTest.java`:

```java
package br.com.api.footfirma.clube;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.clube.domain.ClubeAlias;
import br.com.api.footfirma.clube.domain.FonteExterna;
import br.com.api.footfirma.clube.repository.ClubeAliasRepository;
import br.com.api.footfirma.clube.repository.ClubeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClubeRepositoryTest {

    @Autowired
    ClubeRepository clubeRepository;

    @Autowired
    ClubeAliasRepository clubeAliasRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveEncontrarClubePeloSlug() {
        clubeRepository.save(ClubeFactory.valido("gremio", "Grêmio", idDoBrasil()));

        var encontrado = clubeRepository.findBySlug("gremio");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getNomeCurto()).isEqualTo("Grêmio");
    }

    @Test
    void deveResolverClubePorAliasDeFonteExterna() {
        var clube = clubeRepository.save(ClubeFactory.valido("atletico-mg", "Atlético-MG", idDoBrasil()));
        clubeAliasRepository.save(new ClubeAlias(clube, "Clube Atlético Mineiro", FonteExterna.TRANSFERMARKT));

        var resolvido = clubeAliasRepository.findByAlias("Clube Atlético Mineiro");

        assertThat(resolvido).isPresent();
        assertThat(resolvido.get().getClube().getSlug()).isEqualTo("atletico-mg");
    }

    @Test
    void deveListarClubesPaginadosOrdenadosPorNome() {
        clubeRepository.save(ClubeFactory.valido("santos", "Santos", idDoBrasil()));
        clubeRepository.save(ClubeFactory.valido("bahia", "Bahia", idDoBrasil()));

        var pagina = clubeRepository.findAllByOrderByNomeCurto(PageRequest.of(0, 10));

        assertThat(pagina.getContent()).extracting(clube -> clube.getSlug())
                .containsExactly("bahia", "santos");
    }

    private Long idDoBrasil() {
        return jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
    }
}
```

- [x] **Step 4: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*ClubeRepositoryTest'`
Expected: FAIL na compilação — as classes de `clube` não existem.

- [x] **Step 5: Criar enum, entidades e repositórios**

`clube/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Clube")
package br.com.api.footfirma.clube;
```

`clube/domain/FonteExterna.java`:

```java
package br.com.api.footfirma.clube.domain;

public enum FonteExterna {
    EA_FC, TRANSFERMARKT, MANUAL
}
```

`clube/domain/Estadio.java`:

```java
package br.com.api.footfirma.clube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "estadio")
@Getter
@Setter
public class Estadio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String cidade;

    @Column(name = "estado_id")
    private Long estadoId;

    private Integer capacidade;

    @Column(name = "ano_inauguracao")
    private Integer anoInauguracao;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected Estadio() {
    }

    public Estadio(String nome, String cidade) {
        this.nome = nome;
        this.cidade = cidade;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Estadio estadio)) {
            return false;
        }
        return id != null && id.equals(estadio.id);
    }

    @Override
    public int hashCode() {
        return Estadio.class.hashCode();
    }
}
```

`clube/domain/Clube.java`:

```java
package br.com.api.footfirma.clube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "clube")
@Getter
@Setter
public class Clube {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "nome_oficial", nullable = false)
    private String nomeOficial;

    @Column(name = "nome_curto", nullable = false)
    private String nomeCurto;

    private String apelido;

    @Column(name = "ano_fundacao")
    private Integer anoFundacao;

    @Column(name = "pais_id", nullable = false)
    private Long paisId;

    @Column(name = "estado_id")
    private Long estadoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estadio_id")
    private Estadio estadio;

    @Column(name = "cor_primaria")
    private String corPrimaria;

    @Column(name = "cor_secundaria")
    private String corSecundaria;

    @Column(nullable = false)
    private Integer reputacao;

    @Column(name = "qualidade_base", nullable = false)
    private Integer qualidadeBase;

    @Column(name = "estado_base_id")
    private Long estadoBaseId;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Clube() {
    }

    public Clube(String slug, String nomeOficial, String nomeCurto, Long paisId) {
        this.slug = slug;
        this.nomeOficial = nomeOficial;
        this.nomeCurto = nomeCurto;
        this.paisId = paisId;
        this.reputacao = 50;
        this.qualidadeBase = 50;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Clube clube)) {
            return false;
        }
        return id != null && id.equals(clube.id);
    }

    @Override
    public int hashCode() {
        return Clube.class.hashCode();
    }
}
```

`clube/domain/ClubeAlias.java`:

```java
package br.com.api.footfirma.clube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "clube_alias")
@Getter
@Setter
public class ClubeAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clube_id", nullable = false)
    private Clube clube;

    @Column(nullable = false)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FonteExterna fonte;

    protected ClubeAlias() {
    }

    public ClubeAlias(Clube clube, String alias, FonteExterna fonte) {
        this.clube = clube;
        this.alias = alias;
        this.fonte = fonte;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ClubeAlias clubeAlias)) {
            return false;
        }
        return id != null && id.equals(clubeAlias.id);
    }

    @Override
    public int hashCode() {
        return ClubeAlias.class.hashCode();
    }
}
```

`clube/repository/ClubeRepository.java`:

```java
package br.com.api.footfirma.clube.repository;

import br.com.api.footfirma.clube.domain.Clube;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClubeRepository extends JpaRepository<Clube, Long> {

    @Query("select c from Clube c left join fetch c.estadio where c.slug = :slug")
    Optional<Clube> findBySlug(@Param("slug") String slug);

    Page<Clube> findAllByOrderByNomeCurto(Pageable paginacao);
}
```

`clube/repository/EstadioRepository.java`:

```java
package br.com.api.footfirma.clube.repository;

import br.com.api.footfirma.clube.domain.Estadio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadioRepository extends JpaRepository<Estadio, Long> {
}
```

`clube/repository/ClubeAliasRepository.java`:

```java
package br.com.api.footfirma.clube.repository;

import br.com.api.footfirma.clube.domain.ClubeAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClubeAliasRepository extends JpaRepository<ClubeAlias, Long> {

    @Query("select a from ClubeAlias a join fetch a.clube where a.alias = :alias")
    Optional<ClubeAlias> findByAlias(@Param("alias") String alias);
}
```

- [x] **Step 6: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*ClubeRepositoryTest'`
Expected: PASS nos três testes.

- [x] **Step 7: Criar DTOs, mapper e serviço público**

`clube/dto/ClubeResumo.java`:

```java
package br.com.api.footfirma.clube.dto;

public record ClubeResumo(Long id, String slug, String nomeCurto, Integer reputacao) {
}
```

`clube/dto/ClubeDetalhe.java`:

```java
package br.com.api.footfirma.clube.dto;

public record ClubeDetalhe(
        Long id,
        String slug,
        String nomeOficial,
        String nomeCurto,
        String apelido,
        Integer anoFundacao,
        String estadio,
        Integer capacidadeEstadio,
        Integer reputacao,
        Integer qualidadeBase
) {
}
```

`clube/mapper/ClubeMapper.java` — todo campo do alvo precisa de origem explícita, senão o build falha:

```java
package br.com.api.footfirma.clube.mapper;

import br.com.api.footfirma.clube.domain.Clube;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ClubeMapper {

    ClubeResumo paraResumo(Clube clube);

    @Mapping(target = "estadio", source = "estadio.nome")
    @Mapping(target = "capacidadeEstadio", source = "estadio.capacidade")
    ClubeDetalhe paraDetalhe(Clube clube);
}
```

`clube/ClubeService.java`:

```java
package br.com.api.footfirma.clube;

import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ClubeService {

    Optional<ClubeDetalhe> buscarPorSlug(String slug);

    Page<ClubeResumo> listar(Pageable paginacao);

    Optional<ClubeResumo> resolverPorAlias(String alias);
}
```

`clube/internal/ClubeServiceImpl.java`:

```java
package br.com.api.footfirma.clube.internal;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import br.com.api.footfirma.clube.mapper.ClubeMapper;
import br.com.api.footfirma.clube.repository.ClubeAliasRepository;
import br.com.api.footfirma.clube.repository.ClubeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ClubeServiceImpl implements ClubeService {

    private final ClubeRepository clubeRepository;
    private final ClubeAliasRepository clubeAliasRepository;
    private final ClubeMapper clubeMapper;

    @Override
    public Optional<ClubeDetalhe> buscarPorSlug(String slug) {
        return clubeRepository.findBySlug(slug).map(clubeMapper::paraDetalhe);
    }

    @Override
    public Page<ClubeResumo> listar(Pageable paginacao) {
        return clubeRepository.findAllByOrderByNomeCurto(paginacao).map(clubeMapper::paraResumo);
    }

    @Override
    public Optional<ClubeResumo> resolverPorAlias(String alias) {
        return clubeAliasRepository.findByAlias(alias)
                .map(clubeAlias -> clubeMapper.paraResumo(clubeAlias.getClube()));
    }
}
```

- [x] **Step 8: Compilar e verificar modularidade**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest' --tests '*ClubeRepositoryTest'`
Expected: PASS. Se `ModularidadeTest` acusar dependência de `clube` para `geografia.domain`, você usou `@ManyToOne` para `Pais` ou `Estado` — troque por coluna `Long`, conforme a decisão de modelagem desta tarefa.

- [x] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/V5__cria_clube.sql \
        src/main/java/br/com/api/footfirma/clube \
        src/test/java/br/com/api/footfirma/clube
git commit -m "feat(clube): cria módulo de clube, estádio e aliases

- Migration V5 cria estadio, clube, clube_alias e clube_referencia_externa
- Aliases resolvem variações de nome entre fontes, evitando clube duplicado
  na importação do Plano 3
- FKs para pais e estado são colunas Long, não @ManyToOne: entidade JPA não
  atravessa fronteira de módulo Modulith"
```

---

## Task 5: Módulo `jogador` — posição e dados pessoais

Primeira das três tarefas do módulo maior do sistema. Cria as posições (seed fixo) e a entidade central do jogo.

**Files:**
- Create: `src/main/resources/db/migration/V6__cria_jogador.sql`
- Create: `src/main/resources/db/migration/V7__popula_posicao.sql`
- Create: `src/main/java/br/com/api/footfirma/jogador/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/Posicao.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/Setor.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/PeParaChute.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/OrigemJogador.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/Jogador.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/JogadorPosicao.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/PosicaoRepository.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorRepository.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorFactory.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorRepositoryTest.java`

**Interfaces:**
- Consumes: tabela `pais` (FK `Long`), tabela `clube` (usada só na Task 7).
- Produces: entidade `Jogador` com `chaveNatural` — string única calculada pelo pipeline como `normalizar(nome_completo)|data_nascimento|iso_pais`. É a chave de idempotência do importador no Plano 3. Enum `Posicao.codigo` com os 9 valores `GOL, ZAG, LTD, LTE, VOL, MEC, MEA, PTA, ATA`.

- [x] **Step 1: Escrever as migrations**

`src/main/resources/db/migration/V6__cria_jogador.sql`:

```sql
create table posicao (
    id     bigint  generated always as identity primary key,
    codigo text    not null unique check (codigo in ('GOL', 'ZAG', 'LTD', 'LTE', 'VOL', 'MEC', 'MEA', 'PTA', 'ATA')),
    nome   text    not null check (length(nome) between 1 and 40),
    setor  text    not null check (setor in ('GOLEIRO', 'DEFESA', 'MEIO', 'ATAQUE')),
    ordem  integer not null unique check (ordem > 0)
);

create table jogador (
    id                       bigint      generated always as identity primary key,
    slug                     text        not null unique check (length(slug) between 2 and 120),
    chave_natural            text        not null unique check (length(chave_natural) between 5 and 220),
    nome_completo            text        not null check (length(nome_completo) between 1 and 160),
    nome_exibicao            text        not null check (length(nome_exibicao) between 1 and 80),
    data_nascimento          date        not null,
    pais_id                  bigint      not null references pais (id),
    segunda_nacionalidade_id bigint      references pais (id),
    altura_cm                integer     check (altura_cm between 140 and 230),
    peso_kg                  integer     check (peso_kg between 40 and 140),
    pe_preferido             text        not null check (pe_preferido in ('DIREITO', 'ESQUERDO', 'AMBIDESTRO')),
    posicao_principal_id     bigint      not null references posicao (id),
    semente                  bigint      not null,
    origem                   text        not null check (origem in ('REAL', 'GERADO')),
    criado_em                timestamptz not null default now(),
    atualizado_em            timestamptz
);

create index idx_jogador_pais on jogador (pais_id);
create index idx_jogador_posicao_principal on jogador (posicao_principal_id);

comment on column jogador.chave_natural is
    'normalizar(nome_completo)|data_nascimento|iso_pais — chave de idempotência do importador';
comment on column jogador.semente is
    'Semente determinística derivada da chave natural. Origem de todo atributo oculto e do ruído de crescimento';

create table jogador_posicao (
    jogador_id bigint  not null references jogador (id),
    posicao_id bigint  not null references posicao (id),
    ordem      integer not null check (ordem between 1 and 5),
    primary key (jogador_id, posicao_id)
);

create index idx_jogador_posicao_posicao on jogador_posicao (posicao_id);

create table jogador_referencia_externa (
    id         bigint generated always as identity primary key,
    jogador_id bigint not null references jogador (id),
    fonte      text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT')),
    id_externo text   not null check (length(id_externo) between 1 and 60),
    constraint uq_jogador_referencia_externa unique (fonte, id_externo)
);

create index idx_jogador_referencia_externa_jogador on jogador_referencia_externa (jogador_id);
```

`src/main/resources/db/migration/V7__popula_posicao.sql`:

```sql
-- As 9 posições são catálogo fixo: o modelo de overall do Plano 2 tem um perfil
-- de pesos por posição, e o conjunto não muda com importação de dados.
insert into posicao (codigo, nome, setor, ordem) values
    ('GOL', 'Goleiro',           'GOLEIRO', 1),
    ('ZAG', 'Zagueiro',          'DEFESA',  2),
    ('LTD', 'Lateral direito',   'DEFESA',  3),
    ('LTE', 'Lateral esquerdo',  'DEFESA',  4),
    ('VOL', 'Volante',           'MEIO',    5),
    ('MEC', 'Meia central',      'MEIO',    6),
    ('MEA', 'Meia atacante',     'MEIO',    7),
    ('PTA', 'Ponta',             'ATAQUE',  8),
    ('ATA', 'Atacante',          'ATAQUE',  9);
```

- [x] **Step 2: Escrever a factory de teste**

`src/test/java/br/com/api/footfirma/jogador/JogadorFactory.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.domain.PeParaChute;
import br.com.api.footfirma.jogador.domain.Posicao;

import java.time.LocalDate;

final class JogadorFactory {

    private JogadorFactory() {
    }

    static Jogador valido(String slug, String nome, LocalDate nascimento, Long paisId, Posicao posicao) {
        var jogador = new Jogador(
                slug,
                nome.toLowerCase().replace(" ", "-") + "|" + nascimento + "|BRA",
                nome,
                nome,
                nascimento,
                paisId,
                posicao,
                PeParaChute.DIREITO,
                nome.hashCode());
        jogador.setAlturaCm(180);
        jogador.setPesoKg(75);
        return jogador;
    }
}
```

- [x] **Step 3: Escrever o teste de repositório (vai falhar)**

`src/test/java/br/com/api/footfirma/jogador/JogadorRepositoryTest.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.domain.Posicao;
import br.com.api.footfirma.jogador.domain.Setor;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.PosicaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JogadorRepositoryTest {

    @Autowired
    JogadorRepository jogadorRepository;

    @Autowired
    PosicaoRepository posicaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCarregarAsNovePosicoesDaMigrationDeSeed() {
        var posicoes = posicaoRepository.findAllByOrderByOrdem();

        assertThat(posicoes).hasSize(9);
        assertThat(posicoes.getFirst().getCodigo()).isEqualTo("GOL");
        assertThat(posicoes.getFirst().getSetor()).isEqualTo(Setor.GOLEIRO);
        assertThat(posicoes.getLast().getCodigo()).isEqualTo("ATA");
    }

    @Test
    void deveEncontrarJogadorPeloSlug() {
        jogadorRepository.save(JogadorFactory.valido(
                "raphael-veiga", "Raphael Veiga", LocalDate.of(1995, 6, 19), idDoBrasil(), atacante()));

        var encontrado = jogadorRepository.findBySlug("raphael-veiga");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getNomeExibicao()).isEqualTo("Raphael Veiga");
    }

    @Test
    void deveRejeitarDoisJogadoresComAMesmaChaveNatural() {
        var nascimento = LocalDate.of(1997, 2, 5);
        jogadorRepository.saveAndFlush(JogadorFactory.valido(
                "vitor-roque", "Vitor Roque", nascimento, idDoBrasil(), atacante()));

        var duplicado = JogadorFactory.valido(
                "vitor-roque-2", "Vitor Roque", nascimento, idDoBrasil(), atacante());

        assertThatThrownBy(() -> jogadorRepository.saveAndFlush(duplicado))
                .isInstanceOf(Exception.class);
    }

    private Posicao atacante() {
        return posicaoRepository.findByCodigo("ATA").orElseThrow();
    }

    private Long idDoBrasil() {
        return jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
    }
}
```

O terceiro teste é o que prova que a reimportação do Plano 3 não pode duplicar jogador.

- [x] **Step 4: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*JogadorRepositoryTest'`
Expected: FAIL na compilação — as classes de `jogador` não existem.

- [x] **Step 5: Criar enums e a entidade Posicao**

`jogador/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Jogador")
package br.com.api.footfirma.jogador;
```

`jogador/domain/Setor.java`:

```java
package br.com.api.footfirma.jogador.domain;

public enum Setor {
    GOLEIRO, DEFESA, MEIO, ATAQUE
}
```

`jogador/domain/PeParaChute.java`:

```java
package br.com.api.footfirma.jogador.domain;

public enum PeParaChute {
    DIREITO, ESQUERDO, AMBIDESTRO
}
```

`jogador/domain/OrigemJogador.java`:

```java
package br.com.api.footfirma.jogador.domain;

public enum OrigemJogador {
    REAL, GERADO
}
```

`jogador/domain/Posicao.java`:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "posicao")
@Getter
@Setter
public class Posicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Setor setor;

    @Column(nullable = false, unique = true)
    private Integer ordem;

    protected Posicao() {
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Posicao posicao)) {
            return false;
        }
        return id != null && id.equals(posicao.id);
    }

    @Override
    public int hashCode() {
        return Posicao.class.hashCode();
    }
}
```

- [x] **Step 6: Criar a entidade Jogador e os repositórios**

`jogador/domain/Jogador.java`:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "jogador")
@Getter
@Setter
public class Jogador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "chave_natural", nullable = false, unique = true)
    private String chaveNatural;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "nome_exibicao", nullable = false)
    private String nomeExibicao;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "pais_id", nullable = false)
    private Long paisId;

    @Column(name = "segunda_nacionalidade_id")
    private Long segundaNacionalidadeId;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "peso_kg")
    private Integer pesoKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "pe_preferido", nullable = false)
    private PeParaChute pePreferido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posicao_principal_id", nullable = false)
    private Posicao posicaoPrincipal;

    @Column(nullable = false)
    private Long semente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemJogador origem;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Jogador() {
    }

    public Jogador(String slug, String chaveNatural, String nomeCompleto, String nomeExibicao,
                   LocalDate dataNascimento, Long paisId, Posicao posicaoPrincipal,
                   PeParaChute pePreferido, long semente) {
        this.slug = slug;
        this.chaveNatural = chaveNatural;
        this.nomeCompleto = nomeCompleto;
        this.nomeExibicao = nomeExibicao;
        this.dataNascimento = dataNascimento;
        this.paisId = paisId;
        this.posicaoPrincipal = posicaoPrincipal;
        this.pePreferido = pePreferido;
        this.semente = semente;
        this.origem = OrigemJogador.REAL;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Jogador jogador)) {
            return false;
        }
        return id != null && id.equals(jogador.id);
    }

    @Override
    public int hashCode() {
        return Jogador.class.hashCode();
    }
}
```

`jogador/domain/JogadorPosicao.java` — chave composta via `@IdClass` para a tabela de posições secundárias:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "jogador_posicao")
@IdClass(JogadorPosicao.Chave.class)
@Getter
@Setter
public class JogadorPosicao {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posicao_id", nullable = false)
    private Posicao posicao;

    @Column(nullable = false)
    private Integer ordem;

    protected JogadorPosicao() {
    }

    public JogadorPosicao(Jogador jogador, Posicao posicao, Integer ordem) {
        this.jogador = jogador;
        this.posicao = posicao;
        this.ordem = ordem;
    }

    public static class Chave implements Serializable {

        private Long jogador;
        private Long posicao;

        public Chave() {
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(jogador, chave.jogador) && Objects.equals(posicao, chave.posicao);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jogador, posicao);
        }
    }
}
```

`jogador/repository/PosicaoRepository.java`:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.Posicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PosicaoRepository extends JpaRepository<Posicao, Long> {

    Optional<Posicao> findByCodigo(String codigo);

    List<Posicao> findAllByOrderByOrdem();
}
```

`jogador/repository/JogadorRepository.java`:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.Jogador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JogadorRepository extends JpaRepository<Jogador, Long> {

    @Query("select j from Jogador j join fetch j.posicaoPrincipal where j.slug = :slug")
    Optional<Jogador> findBySlug(@Param("slug") String slug);

    Optional<Jogador> findByChaveNatural(String chaveNatural);
}
```

- [x] **Step 7: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*JogadorRepositoryTest'`
Expected: PASS nos três testes.

- [x] **Step 8: Compilar, verificar modularidade e commitar**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest'`
Expected: PASS.

```bash
git add src/main/resources/db/migration/V6__cria_jogador.sql \
        src/main/resources/db/migration/V7__popula_posicao.sql \
        src/main/java/br/com/api/footfirma/jogador \
        src/test/java/br/com/api/footfirma/jogador
git commit -m "feat(jogador): cria posição e dados pessoais do jogador

- Migration V6 cria posicao, jogador, jogador_posicao e a referência externa
- Migration V7 popula as 9 posições como catálogo fixo
- chave_natural com unique é a garantia de idempotência da importação:
  reimportar o mesmo jogador não pode criar uma segunda linha
- semente determinística será a origem dos atributos ocultos no Plano 2"
```

---

## Task 6: Módulo `jogador` — atributos de skill e atributos ocultos

As 18 skills versionadas por temporada, mais os 8 atributos ocultos de personalidade. É a tabela que o Plano 2 consome para calcular overall.

**Files:**
- Create: `src/main/resources/db/migration/V8__cria_jogador_atributo.sql`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/JogadorAtributo.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/JogadorAtributoOculto.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/FonteAtributo.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorAtributoRepository.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorAtributoOcultoRepository.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorAtributoRepositoryTest.java`

**Interfaces:**
- Consumes: `Jogador` e `Posicao` (Task 5); tabela `temporada` por FK `Long` (Task 2).
- Produces: `JogadorAtributo` com os 18 campos `Integer` (0–99) nomeados exatamente: `ritmo, forca, folego, salto, agilidade, passe, drible, cruzamento, frieza, finalizacao, cabeceio, falta, penalti, desarme, marcacao, golReflexo, golPosicionamento, golManejo` — mais `potencialBase`, `potencialVariacao`, `fonteAtributo`. **O Plano 2 depende desses nomes exatos** para montar os perfis de peso.

- [x] **Step 1: Escrever a migration**

`src/main/resources/db/migration/V8__cria_jogador_atributo.sql`:

```sql
-- Atributos são versionados por temporada: o mesmo jogador em 2025 e 2026 são
-- linhas distintas. Sem isso, recarregar a base no ano seguinte destrói o
-- histórico de que o modelo de progressão depende.
create table jogador_atributo (
    id                  bigint      generated always as identity primary key,
    jogador_id          bigint      not null references jogador (id),
    temporada_id        bigint      not null references temporada (id),

    ritmo               integer     not null check (ritmo between 0 and 99),
    forca               integer     not null check (forca between 0 and 99),
    folego              integer     not null check (folego between 0 and 99),
    salto               integer     not null check (salto between 0 and 99),
    agilidade           integer     not null check (agilidade between 0 and 99),

    passe               integer     not null check (passe between 0 and 99),
    drible              integer     not null check (drible between 0 and 99),
    cruzamento          integer     not null check (cruzamento between 0 and 99),
    frieza              integer     not null check (frieza between 0 and 99),

    finalizacao         integer     not null check (finalizacao between 0 and 99),
    cabeceio            integer     not null check (cabeceio between 0 and 99),
    falta               integer     not null check (falta between 0 and 99),
    penalti             integer     not null check (penalti between 0 and 99),

    desarme             integer     not null check (desarme between 0 and 99),
    marcacao            integer     not null check (marcacao between 0 and 99),

    gol_reflexo         integer     not null check (gol_reflexo between 0 and 99),
    gol_posicionamento  integer     not null check (gol_posicionamento between 0 and 99),
    gol_manejo          integer     not null check (gol_manejo between 0 and 99),

    potencial_base      integer     not null check (potencial_base between 0 and 99),
    potencial_variacao  integer     not null default 0 check (potencial_variacao between 0 and 20),
    fonte_atributo      text        not null check (fonte_atributo in ('IMPORTADO', 'ESTIMADO')),
    coletado_em         timestamptz not null,

    constraint uq_jogador_atributo unique (jogador_id, temporada_id)
);

create index idx_jogador_atributo_temporada on jogador_atributo (temporada_id);

comment on column jogador_atributo.fonte_atributo is
    'ESTIMADO marca dado derivado de idade/posição/valor quando a fonte não cobre o jogador. Dado inventado fica marcado como inventado';
comment on column jogador_atributo.potencial_variacao is
    'Amplitude da banda de potencial. A carreira sorteia o teto real dentro de potencial_base +/- esta variação';

-- Atributos ocultos são estáveis ao longo da carreira e gerados a partir da
-- semente do jogador, nunca a partir de juízo sobre a pessoa real.
create table jogador_atributo_oculto (
    jogador_id          bigint  primary key references jogador (id),
    profissionalismo    integer not null check (profissionalismo between 0 and 99),
    ambicao             integer not null check (ambicao between 0 and 99),
    lealdade            integer not null check (lealdade between 0 and 99),
    temperamento        integer not null check (temperamento between 0 and 99),
    lideranca           integer not null check (lideranca between 0 and 99),
    regularidade        integer not null check (regularidade between 0 and 99),
    propensao_lesao     integer not null check (propensao_lesao between 0 and 99),
    resistencia_pressao integer not null check (resistencia_pressao between 0 and 99)
);
```

- [x] **Step 2: Escrever o teste (vai falhar)**

`src/test/java/br/com/api/footfirma/jogador/JogadorAtributoRepositoryTest.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.domain.FonteAtributo;
import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.domain.JogadorAtributo;
import br.com.api.footfirma.jogador.repository.JogadorAtributoRepository;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.PosicaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JogadorAtributoRepositoryTest {

    @Autowired
    JogadorRepository jogadorRepository;

    @Autowired
    JogadorAtributoRepository jogadorAtributoRepository;

    @Autowired
    PosicaoRepository posicaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveGuardarAtributosDeTemporadasDiferentesParaOMesmoJogador() {
        var jogador = jogadorSalvo();
        jogadorAtributoRepository.save(atributos(jogador, temporada("2025"), 70));
        jogadorAtributoRepository.save(atributos(jogador, temporada("2026"), 74));

        var de2026 = jogadorAtributoRepository.findByJogadorIdAndTemporadaId(jogador.getId(), temporada("2026"));

        assertThat(de2026).isPresent();
        assertThat(de2026.get().getFinalizacao()).isEqualTo(74);
    }

    @Test
    void deveRejeitarDoisConjuntosDeAtributosNaMesmaTemporada() {
        var jogador = jogadorSalvo();
        var temporada = temporada("2025");
        jogadorAtributoRepository.saveAndFlush(atributos(jogador, temporada, 70));

        assertThatThrownBy(() -> jogadorAtributoRepository.saveAndFlush(atributos(jogador, temporada, 80)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deveRejeitarAtributoForaDaEscalaDeZeroANoventaENove() {
        var jogador = jogadorSalvo();
        var invalido = atributos(jogador, temporada("2025"), 70);
        invalido.setRitmo(120);

        assertThatThrownBy(() -> jogadorAtributoRepository.saveAndFlush(invalido))
                .isInstanceOf(Exception.class);
    }

    private Jogador jogadorSalvo() {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var atacante = posicaoRepository.findByCodigo("ATA").orElseThrow();
        return jogadorRepository.save(JogadorFactory.valido(
                "teste-jogador", "Jogador Teste", LocalDate.of(1998, 3, 10), paisId, atacante));
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }

    private JogadorAtributo atributos(Jogador jogador, Long temporadaId, int finalizacao) {
        var atributo = new JogadorAtributo(jogador, temporadaId, FonteAtributo.IMPORTADO);
        atributo.setRitmo(75);
        atributo.setForca(70);
        atributo.setFolego(80);
        atributo.setSalto(65);
        atributo.setAgilidade(78);
        atributo.setPasse(72);
        atributo.setDrible(76);
        atributo.setCruzamento(68);
        atributo.setFrieza(74);
        atributo.setFinalizacao(finalizacao);
        atributo.setCabeceio(60);
        atributo.setFalta(55);
        atributo.setPenalti(70);
        atributo.setDesarme(40);
        atributo.setMarcacao(38);
        atributo.setGolReflexo(10);
        atributo.setGolPosicionamento(10);
        atributo.setGolManejo(10);
        atributo.setPotencialBase(85);
        atributo.setPotencialVariacao(5);
        return atributo;
    }
}
```

- [x] **Step 3: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*JogadorAtributoRepositoryTest'`
Expected: FAIL na compilação — `JogadorAtributo` não existe.

- [x] **Step 4: Criar o enum e as entidades**

`jogador/domain/FonteAtributo.java`:

```java
package br.com.api.footfirma.jogador.domain;

public enum FonteAtributo {
    IMPORTADO, ESTIMADO
}
```

`jogador/domain/JogadorAtributo.java`:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "jogador_atributo")
@Getter
@Setter
public class JogadorAtributo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private Integer ritmo;

    @Column(nullable = false)
    private Integer forca;

    @Column(nullable = false)
    private Integer folego;

    @Column(nullable = false)
    private Integer salto;

    @Column(nullable = false)
    private Integer agilidade;

    @Column(nullable = false)
    private Integer passe;

    @Column(nullable = false)
    private Integer drible;

    @Column(nullable = false)
    private Integer cruzamento;

    @Column(nullable = false)
    private Integer frieza;

    @Column(nullable = false)
    private Integer finalizacao;

    @Column(nullable = false)
    private Integer cabeceio;

    @Column(nullable = false)
    private Integer falta;

    @Column(nullable = false)
    private Integer penalti;

    @Column(nullable = false)
    private Integer desarme;

    @Column(nullable = false)
    private Integer marcacao;

    @Column(name = "gol_reflexo", nullable = false)
    private Integer golReflexo;

    @Column(name = "gol_posicionamento", nullable = false)
    private Integer golPosicionamento;

    @Column(name = "gol_manejo", nullable = false)
    private Integer golManejo;

    @Column(name = "potencial_base", nullable = false)
    private Integer potencialBase;

    @Column(name = "potencial_variacao", nullable = false)
    private Integer potencialVariacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_atributo", nullable = false)
    private FonteAtributo fonteAtributo;

    @Column(name = "coletado_em", nullable = false)
    private OffsetDateTime coletadoEm;

    protected JogadorAtributo() {
    }

    public JogadorAtributo(Jogador jogador, Long temporadaId, FonteAtributo fonteAtributo) {
        this.jogador = jogador;
        this.temporadaId = temporadaId;
        this.fonteAtributo = fonteAtributo;
        this.potencialVariacao = 0;
        this.coletadoEm = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorAtributo jogadorAtributo)) {
            return false;
        }
        return id != null && id.equals(jogadorAtributo.id);
    }

    @Override
    public int hashCode() {
        return JogadorAtributo.class.hashCode();
    }
}
```

`jogador/domain/JogadorAtributoOculto.java` — a PK é o próprio `jogador_id`:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "jogador_atributo_oculto")
@Getter
@Setter
public class JogadorAtributoOculto {

    @Id
    @Column(name = "jogador_id")
    private Long jogadorId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "jogador_id")
    private Jogador jogador;

    @Column(nullable = false)
    private Integer profissionalismo;

    @Column(nullable = false)
    private Integer ambicao;

    @Column(nullable = false)
    private Integer lealdade;

    @Column(nullable = false)
    private Integer temperamento;

    @Column(nullable = false)
    private Integer lideranca;

    @Column(nullable = false)
    private Integer regularidade;

    @Column(name = "propensao_lesao", nullable = false)
    private Integer propensaoLesao;

    @Column(name = "resistencia_pressao", nullable = false)
    private Integer resistenciaPressao;

    protected JogadorAtributoOculto() {
    }

    public JogadorAtributoOculto(Jogador jogador) {
        this.jogador = jogador;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorAtributoOculto atributoOculto)) {
            return false;
        }
        return jogadorId != null && jogadorId.equals(atributoOculto.jogadorId);
    }

    @Override
    public int hashCode() {
        return JogadorAtributoOculto.class.hashCode();
    }
}
```

- [x] **Step 5: Criar os repositórios**

`jogador/repository/JogadorAtributoRepository.java`:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorAtributo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JogadorAtributoRepository extends JpaRepository<JogadorAtributo, Long> {

    Optional<JogadorAtributo> findByJogadorIdAndTemporadaId(Long jogadorId, Long temporadaId);
}
```

`jogador/repository/JogadorAtributoOcultoRepository.java`:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorAtributoOculto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JogadorAtributoOcultoRepository extends JpaRepository<JogadorAtributoOculto, Long> {
}
```

- [x] **Step 6: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*JogadorAtributoRepositoryTest'`
Expected: PASS nos três testes. O terceiro prova que o `check` de escala 0–99 está no banco, não só na aplicação.

- [x] **Step 7: Compilar e commitar**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest'`
Expected: PASS.

```bash
git add src/main/resources/db/migration/V8__cria_jogador_atributo.sql \
        src/main/java/br/com/api/footfirma/jogador \
        src/test/java/br/com/api/footfirma/jogador
git commit -m "feat(jogador): adiciona as 18 skills e os atributos ocultos

- Migration V8 cria jogador_atributo (versionado por temporada) e
  jogador_atributo_oculto (estável por jogador)
- Escala 0-99 garantida por check no banco, não só por validação Java
- unique (jogador_id, temporada_id) impede dois conjuntos na mesma temporada
- fonte_atributo distingue dado importado de dado estimado"
```

---

## Task 7: Módulo `jogador` — características especiais e vínculo com clube

Fecha o módulo `jogador`. As características são as skills especiais (voleio, bicicleta, cobrança de falta); o vínculo é o elenco de cada clube por temporada, e é o que a API de elenco consulta.

**Files:**
- Create: `src/main/resources/db/migration/V9__cria_caracteristica_e_vinculo.sql`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/Caracteristica.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/CategoriaCaracteristica.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/JogadorCaracteristica.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/JogadorVinculo.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/domain/TipoVinculo.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/CaracteristicaRepository.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorVinculoRepository.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorVinculoRepositoryTest.java`

**Interfaces:**
- Consumes: `Jogador` (Task 5); tabelas `clube` e `temporada` por FK `Long`.
- Produces: `JogadorVinculoRepository.buscarElenco(Long clubeId, Long temporadaId) -> List<JogadorVinculo>` com `join fetch` do jogador e da posição — a consulta que a API de elenco usa, e a que evitaria N+1 sobre 30 jogadores.

- [x] **Step 1: Escrever a migration**

`src/main/resources/db/migration/V9__cria_caracteristica_e_vinculo.sql`:

```sql
-- Características são as skills especiais importadas dos Traits/PlayStyles da
-- fonte externa. O efeito delas pertence ao motor de simulação; o catálogo só
-- as carrega.
create table caracteristica (
    id        bigint generated always as identity primary key,
    codigo    text   not null unique check (length(codigo) between 2 and 40),
    nome      text   not null check (length(nome) between 1 and 60),
    categoria text   not null check (categoria in ('ATAQUE', 'TECNICA', 'DEFESA', 'FISICO', 'MENTAL')),
    descricao text   not null check (length(descricao) between 1 and 240)
);

insert into caracteristica (codigo, nome, categoria, descricao) values
    ('DRIBLADOR',            'Driblador',              'TECNICA', 'Leva vantagem em duelos individuais com a bola dominada'),
    ('VOLEIO',               'Voleio',                 'ATAQUE',  'Finaliza bem antes de a bola tocar o chão'),
    ('BICICLETA',            'Bicicleta',              'ATAQUE',  'Tenta e converte finalizações acrobáticas'),
    ('CHUTE_DE_LONGE',       'Chute de longe',         'ATAQUE',  'Ameaça de fora da área'),
    ('CHUTE_COLOCADO',       'Chute colocado',         'ATAQUE',  'Prefere precisão a potência na finalização'),
    ('ESPECIALISTA_FALTA',   'Especialista em falta',  'ATAQUE',  'Cobrador de falta designado'),
    ('ESPECIALISTA_PENALTI', 'Especialista em pênalti','ATAQUE',  'Cobrador de pênalti designado'),
    ('ARMADOR',              'Armador',                'TECNICA', 'A equipe procura este jogador para construir as jogadas'),
    ('PASSE_LONGO',          'Passe longo',            'TECNICA', 'Inverte o jogo com precisão'),
    ('DESARME_LIMPO',        'Desarme limpo',          'DEFESA',  'Desarma com baixo risco de falta'),
    ('MARCADOR_AGRESSIVO',   'Marcador agressivo',     'DEFESA',  'Pressiona alto e disputa com intensidade'),
    ('CABECEIO_AEREO',       'Cabeceio aéreo',         'FISICO',  'Domina a bola aérea nas duas áreas'),
    ('LIDER',                'Líder',                  'MENTAL',  'Eleva a moral do elenco em campo');

create table jogador_caracteristica (
    jogador_id        bigint not null references jogador (id),
    caracteristica_id bigint not null references caracteristica (id),
    primary key (jogador_id, caracteristica_id)
);

create index idx_jogador_caracteristica_caracteristica on jogador_caracteristica (caracteristica_id);

-- O vínculo é o elenco por temporada. Um jogador pode ter mais de um vínculo na
-- mesma temporada (transferência no meio do ano), mas não dois no mesmo clube.
create table jogador_vinculo (
    id                bigint        generated always as identity primary key,
    jogador_id        bigint        not null references jogador (id),
    clube_id          bigint        not null references clube (id),
    temporada_id      bigint        not null references temporada (id),
    tipo              text          not null check (tipo in ('CONTRATO', 'EMPRESTIMO')),
    numero_camisa     integer       check (numero_camisa between 1 and 99),
    data_inicio       date,
    data_fim          date,
    valor_mercado_eur numeric(14,2) check (valor_mercado_eur >= 0),
    constraint uq_jogador_vinculo unique (jogador_id, temporada_id, clube_id),
    constraint ck_jogador_vinculo_datas check (data_fim is null or data_inicio is null or data_fim >= data_inicio)
);

create index idx_jogador_vinculo_clube_temporada on jogador_vinculo (clube_id, temporada_id);
create index idx_jogador_vinculo_jogador on jogador_vinculo (jogador_id);
```

- [x] **Step 2: Escrever o teste (vai falhar)**

`src/test/java/br/com/api/footfirma/jogador/JogadorVinculoRepositoryTest.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.domain.JogadorVinculo;
import br.com.api.footfirma.jogador.domain.TipoVinculo;
import br.com.api.footfirma.jogador.repository.CaracteristicaRepository;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.JogadorVinculoRepository;
import br.com.api.footfirma.jogador.repository.PosicaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JogadorVinculoRepositoryTest {

    @Autowired
    JogadorRepository jogadorRepository;

    @Autowired
    JogadorVinculoRepository jogadorVinculoRepository;

    @Autowired
    CaracteristicaRepository caracteristicaRepository;

    @Autowired
    PosicaoRepository posicaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCarregarAsCaracteristicasDaMigrationDeSeed() {
        var caracteristicas = caracteristicaRepository.findAll();

        assertThat(caracteristicas).hasSize(13);
        assertThat(caracteristicaRepository.findByCodigo("BICICLETA")).isPresent();
    }

    @Test
    void deveBuscarElencoDoClubeNaTemporada() {
        var clubeId = clube("flamengo");
        var temporadaId = temporada("2025");
        vinculo(jogador("jogador-um", "Jogador Um"), clubeId, temporadaId, 10);
        vinculo(jogador("jogador-dois", "Jogador Dois"), clubeId, temporadaId, 9);

        var elenco = jogadorVinculoRepository.buscarElenco(clubeId, temporadaId);

        assertThat(elenco).hasSize(2);
        assertThat(elenco).extracting(vinculo -> vinculo.getJogador().getNomeExibicao())
                .containsExactlyInAnyOrder("Jogador Um", "Jogador Dois");
    }

    @Test
    void naoDeveRetornarElencoDeOutraTemporada() {
        var clubeId = clube("palmeiras");
        vinculo(jogador("jogador-tres", "Jogador Três"), clubeId, temporada("2025"), 7);

        var elencoDe2026 = jogadorVinculoRepository.buscarElenco(clubeId, temporada("2026"));

        assertThat(elencoDe2026).isEmpty();
    }

    private Jogador jogador(String slug, String nome) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var atacante = posicaoRepository.findByCodigo("ATA").orElseThrow();
        return jogadorRepository.save(
                JogadorFactory.valido(slug, nome, LocalDate.of(1999, 1, 1), paisId, atacante));
    }

    private void vinculo(Jogador jogador, Long clubeId, Long temporadaId, int camisa) {
        var vinculo = new JogadorVinculo(jogador, clubeId, temporadaId, TipoVinculo.CONTRATO);
        vinculo.setNumeroCamisa(camisa);
        jogadorVinculoRepository.save(vinculo);
    }

    private Long clube(String slug) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id) values (?, ?, ?, ?)
                on conflict (slug) do nothing
                """, slug, slug + " oficial", slug, paisId);
        return jdbcTemplate.queryForObject("select id from clube where slug = ?", Long.class, slug);
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }
}
```

- [x] **Step 3: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*JogadorVinculoRepositoryTest'`
Expected: FAIL na compilação — `JogadorVinculo` e `Caracteristica` não existem.

- [x] **Step 4: Criar enums e entidades**

`jogador/domain/CategoriaCaracteristica.java`:

```java
package br.com.api.footfirma.jogador.domain;

public enum CategoriaCaracteristica {
    ATAQUE, TECNICA, DEFESA, FISICO, MENTAL
}
```

`jogador/domain/TipoVinculo.java`:

```java
package br.com.api.footfirma.jogador.domain;

public enum TipoVinculo {
    CONTRATO, EMPRESTIMO
}
```

`jogador/domain/Caracteristica.java`:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "caracteristica")
@Getter
@Setter
public class Caracteristica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaCaracteristica categoria;

    @Column(nullable = false)
    private String descricao;

    protected Caracteristica() {
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Caracteristica caracteristica)) {
            return false;
        }
        return id != null && id.equals(caracteristica.id);
    }

    @Override
    public int hashCode() {
        return Caracteristica.class.hashCode();
    }
}
```

`jogador/domain/JogadorCaracteristica.java`:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "jogador_caracteristica")
@IdClass(JogadorCaracteristica.Chave.class)
@Getter
@Setter
public class JogadorCaracteristica {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caracteristica_id", nullable = false)
    private Caracteristica caracteristica;

    protected JogadorCaracteristica() {
    }

    public JogadorCaracteristica(Jogador jogador, Caracteristica caracteristica) {
        this.jogador = jogador;
        this.caracteristica = caracteristica;
    }

    public static class Chave implements Serializable {

        private Long jogador;
        private Long caracteristica;

        public Chave() {
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(jogador, chave.jogador)
                    && Objects.equals(caracteristica, chave.caracteristica);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jogador, caracteristica);
        }
    }
}
```

`jogador/domain/JogadorVinculo.java`:

```java
package br.com.api.footfirma.jogador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "jogador_vinculo")
@Getter
@Setter
public class JogadorVinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVinculo tipo;

    @Column(name = "numero_camisa")
    private Integer numeroCamisa;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "valor_mercado_eur")
    private BigDecimal valorMercadoEur;

    protected JogadorVinculo() {
    }

    public JogadorVinculo(Jogador jogador, Long clubeId, Long temporadaId, TipoVinculo tipo) {
        this.jogador = jogador;
        this.clubeId = clubeId;
        this.temporadaId = temporadaId;
        this.tipo = tipo;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorVinculo jogadorVinculo)) {
            return false;
        }
        return id != null && id.equals(jogadorVinculo.id);
    }

    @Override
    public int hashCode() {
        return JogadorVinculo.class.hashCode();
    }
}
```

- [x] **Step 5: Criar os repositórios**

`jogador/repository/CaracteristicaRepository.java`:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.Caracteristica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaracteristicaRepository extends JpaRepository<Caracteristica, Long> {

    Optional<Caracteristica> findByCodigo(String codigo);
}
```

`jogador/repository/JogadorVinculoRepository.java` — o `join fetch` duplo é o que impede N+1 ao listar um elenco de 30 jogadores:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorVinculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JogadorVinculoRepository extends JpaRepository<JogadorVinculo, Long> {

    @Query("""
            select v from JogadorVinculo v
            join fetch v.jogador j
            join fetch j.posicaoPrincipal
            where v.clubeId = :clubeId and v.temporadaId = :temporadaId
            order by j.nomeExibicao
            """)
    List<JogadorVinculo> buscarElenco(@Param("clubeId") Long clubeId,
                                      @Param("temporadaId") Long temporadaId);

    List<JogadorVinculo> findByJogadorIdOrderByTemporadaIdDesc(Long jogadorId);
}
```

- [x] **Step 6: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*JogadorVinculoRepositoryTest'`
Expected: PASS nos três testes.

- [x] **Step 7: Compilar e commitar**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest'`
Expected: PASS.

```bash
git add src/main/resources/db/migration/V9__cria_caracteristica_e_vinculo.sql \
        src/main/java/br/com/api/footfirma/jogador \
        src/test/java/br/com/api/footfirma/jogador
git commit -m "feat(jogador): adiciona características especiais e vínculo com clube

- Migration V9 cria caracteristica (com as 13 do catálogo), a associação com
  jogador e jogador_vinculo
- buscarElenco usa join fetch duplo para evitar N+1 ao listar um elenco
- Vínculo é por temporada: o mesmo jogador pode trocar de clube no ano"
```

---

## Task 8: Módulo `competicao`

Competição, edição por temporada, fases e participantes — mais as regras de classificação, que são a decisão de design central deste módulo: acesso, rebaixamento e vaga continental são **dados**, não código. Um campeonato com formato novo é um `INSERT`, não uma classe nova.

**Files:**
- Create: `src/main/resources/db/migration/V10__cria_competicao.sql`
- Create: `src/main/resources/db/migration/V11__cria_participante_e_regra.sql`
- Create: `src/main/java/br/com/api/footfirma/competicao/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/CompeticaoService.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/CompeticaoResumo.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/EdicaoDetalhe.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/FaseResumo.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/RegraClassificacaoResumo.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/domain/` — `Competicao`, `Edicao`, `Fase`, `EdicaoParticipante`, `RegraClassificacao`, `TipoCompeticao`, `TipoFase`, `TipoClassificacao`
- Create: `src/main/java/br/com/api/footfirma/competicao/repository/` — `CompeticaoRepository`, `EdicaoRepository`, `RegraClassificacaoRepository`
- Create: `src/main/java/br/com/api/footfirma/competicao/mapper/CompeticaoMapper.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/internal/CompeticaoServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/competicao/CompeticaoRepositoryTest.java`

**Interfaces:**
- Consumes: tabelas `pais`, `clube`, `temporada` por FK `Long`.
- Produces: `CompeticaoService.listarPorPais(String isoPais) -> List<CompeticaoResumo>`, `CompeticaoService.buscarEdicao(String slugCompeticao, String labelTemporada) -> Optional<EdicaoDetalhe>`. `CompeticaoResumo(Long id, String slug, String nome, String tipo, Integer nivel)`, `EdicaoDetalhe(Long id, String nome, String competicao, LocalDate dataInicio, LocalDate dataFim, List<FaseResumo> fases, List<RegraClassificacaoResumo> regras)`.

- [x] **Step 1: Escrever as migrations**

`src/main/resources/db/migration/V10__cria_competicao.sql`:

```sql
create table competicao (
    id      bigint  generated always as identity primary key,
    slug    text    not null unique check (length(slug) between 2 and 80),
    nome    text    not null check (length(nome) between 1 and 120),
    pais_id bigint  references pais (id),
    tipo    text    not null check (tipo in ('LIGA', 'COPA', 'MISTA')),
    nivel   integer check (nivel between 1 and 10),
    genero  text    not null default 'MASCULINO' check (genero in ('MASCULINO', 'FEMININO'))
);

create index idx_competicao_pais on competicao (pais_id);

comment on column competicao.pais_id is 'Nulo para competição continental (Libertadores, Sul-Americana)';
comment on column competicao.nivel is 'Divisão na pirâmide nacional: 1 = Série A, 2 = Série B. Nulo para copa';

create table edicao (
    id            bigint generated always as identity primary key,
    competicao_id bigint not null references competicao (id),
    temporada_id  bigint not null references temporada (id),
    nome          text   not null check (length(nome) between 1 and 140),
    data_inicio   date,
    data_fim      date,
    constraint uq_edicao unique (competicao_id, temporada_id),
    constraint ck_edicao_datas check (data_fim is null or data_inicio is null or data_fim >= data_inicio)
);

create index idx_edicao_temporada on edicao (temporada_id);

-- Fase existe desde a v1 porque a Copa do Brasil obriga o modelo a suportar
-- mata-mata: sem ela, a limitação só apareceria ao adicionar a Libertadores.
create table fase (
    id                  bigint  generated always as identity primary key,
    edicao_id           bigint  not null references edicao (id),
    ordem               integer not null check (ordem > 0),
    nome                text    not null check (length(nome) between 1 and 60),
    tipo                text    not null check (tipo in ('PONTOS_CORRIDOS', 'GRUPOS', 'ELIMINATORIA')),
    jogos_por_confronto integer not null default 1 check (jogos_por_confronto between 1 and 2),
    tem_gol_fora        boolean not null default false,
    tem_prorrogacao     boolean not null default false,
    tem_penaltis        boolean not null default false,
    constraint uq_fase_ordem unique (edicao_id, ordem)
);

create table competicao_referencia_externa (
    id            bigint generated always as identity primary key,
    competicao_id bigint not null references competicao (id),
    fonte         text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT')),
    id_externo    text   not null check (length(id_externo) between 1 and 60),
    constraint uq_competicao_referencia_externa unique (fonte, id_externo)
);

create index idx_competicao_referencia_externa_competicao on competicao_referencia_externa (competicao_id);
```

`src/main/resources/db/migration/V11__cria_participante_e_regra.sql`:

```sql
create table edicao_participante (
    id            bigint  generated always as identity primary key,
    edicao_id     bigint  not null references edicao (id),
    clube_id      bigint  not null references clube (id),
    posicao_final integer check (posicao_final > 0),
    constraint uq_edicao_participante unique (edicao_id, clube_id)
);

create index idx_edicao_participante_clube on edicao_participante (clube_id);

-- Regra de classificação é dado, não código: acesso, rebaixamento e vaga
-- continental viram linhas. Um campeonato com formato novo é um INSERT.
create table regra_classificacao (
    id                    bigint  generated always as identity primary key,
    edicao_id             bigint  not null references edicao (id),
    posicao_inicio        integer not null check (posicao_inicio > 0),
    posicao_fim           integer not null check (posicao_fim > 0),
    tipo                  text    not null check (tipo in ('ACESSO', 'REBAIXAMENTO', 'LIBERTADORES_GRUPOS', 'LIBERTADORES_PRE', 'SULAMERICANA')),
    competicao_destino_id bigint  references competicao (id),
    constraint ck_regra_classificacao_faixa check (posicao_fim >= posicao_inicio),
    constraint uq_regra_classificacao unique (edicao_id, posicao_inicio, posicao_fim, tipo)
);

create index idx_regra_classificacao_edicao on regra_classificacao (edicao_id);
```

- [x] **Step 2: Escrever o teste (vai falhar)**

`src/test/java/br/com/api/footfirma/competicao/CompeticaoRepositoryTest.java`:

```java
package br.com.api.footfirma.competicao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.competicao.domain.Competicao;
import br.com.api.footfirma.competicao.domain.Edicao;
import br.com.api.footfirma.competicao.domain.Fase;
import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import br.com.api.footfirma.competicao.domain.TipoClassificacao;
import br.com.api.footfirma.competicao.domain.TipoCompeticao;
import br.com.api.footfirma.competicao.domain.TipoFase;
import br.com.api.footfirma.competicao.repository.CompeticaoRepository;
import br.com.api.footfirma.competicao.repository.EdicaoRepository;
import br.com.api.footfirma.competicao.repository.RegraClassificacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CompeticaoRepositoryTest {

    @Autowired
    CompeticaoRepository competicaoRepository;

    @Autowired
    EdicaoRepository edicaoRepository;

    @Autowired
    RegraClassificacaoRepository regraClassificacaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveListarCompeticoesDoPaisOrdenadasPorNivel() {
        competicaoRepository.save(competicao("brasileirao-serie-b", "Brasileirão Série B", TipoCompeticao.LIGA, 2));
        competicaoRepository.save(competicao("brasileirao-serie-a", "Brasileirão Série A", TipoCompeticao.LIGA, 1));

        var competicoes = competicaoRepository.findByPaisIso("BRA");

        assertThat(competicoes).extracting(Competicao::getSlug)
                .containsExactly("brasileirao-serie-a", "brasileirao-serie-b");
    }

    @Test
    void deveBuscarEdicaoComFasesPeloSlugEPelaTemporada() {
        var serieA = competicaoRepository.save(
                competicao("brasileirao-serie-a", "Brasileirão Série A", TipoCompeticao.LIGA, 1));
        var edicao = new Edicao(serieA, temporada("2025"), "Brasileirão Série A 2025");
        edicao.getFases().add(new Fase(edicao, 1, "Fase única", TipoFase.PONTOS_CORRIDOS));
        edicaoRepository.save(edicao);

        var encontrada = edicaoRepository.buscarPorSlugETemporadaId("brasileirao-serie-a", temporada("2025"));

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getFases()).hasSize(1);
        assertThat(encontrada.get().getFases().getFirst().getTipo()).isEqualTo(TipoFase.PONTOS_CORRIDOS);
    }

    @Test
    void deveGuardarRegraDeRebaixamentoComoDado() {
        var serieA = competicaoRepository.save(
                competicao("brasileirao-serie-a", "Brasileirão Série A", TipoCompeticao.LIGA, 1));
        var serieB = competicaoRepository.save(
                competicao("brasileirao-serie-b", "Brasileirão Série B", TipoCompeticao.LIGA, 2));
        var edicao = edicaoRepository.save(new Edicao(serieA, temporada("2025"), "Brasileirão Série A 2025"));

        regraClassificacaoRepository.save(
                new RegraClassificacao(edicao, 17, 20, TipoClassificacao.REBAIXAMENTO, serieB.getId()));
        regraClassificacaoRepository.save(
                new RegraClassificacao(edicao, 1, 4, TipoClassificacao.LIBERTADORES_GRUPOS, null));

        var regras = regraClassificacaoRepository.findByEdicaoIdOrderByPosicaoInicio(edicao.getId());

        assertThat(regras).hasSize(2);
        assertThat(regras.getFirst().getTipo()).isEqualTo(TipoClassificacao.LIBERTADORES_GRUPOS);
        assertThat(regras.getLast().getPosicaoInicio()).isEqualTo(17);
    }

    private Competicao competicao(String slug, String nome, TipoCompeticao tipo, Integer nivel) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var competicao = new Competicao(slug, nome, tipo);
        competicao.setPaisId(paisId);
        competicao.setNivel(nivel);
        return competicao;
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }
}
```

- [x] **Step 3: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*CompeticaoRepositoryTest'`
Expected: FAIL na compilação — as classes de `competicao` não existem.

- [x] **Step 4: Criar os enums e o package-info**

`competicao/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Competição")
package br.com.api.footfirma.competicao;
```

`competicao/domain/TipoCompeticao.java`:

```java
package br.com.api.footfirma.competicao.domain;

public enum TipoCompeticao {
    LIGA, COPA, MISTA
}
```

`competicao/domain/TipoFase.java`:

```java
package br.com.api.footfirma.competicao.domain;

public enum TipoFase {
    PONTOS_CORRIDOS, GRUPOS, ELIMINATORIA
}
```

`competicao/domain/TipoClassificacao.java`:

```java
package br.com.api.footfirma.competicao.domain;

public enum TipoClassificacao {
    ACESSO, REBAIXAMENTO, LIBERTADORES_GRUPOS, LIBERTADORES_PRE, SULAMERICANA
}
```

- [x] **Step 5: Criar as entidades**

`competicao/domain/Competicao.java`:

```java
package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "competicao")
@Getter
@Setter
public class Competicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String nome;

    @Column(name = "pais_id")
    private Long paisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCompeticao tipo;

    private Integer nivel;

    @Column(nullable = false)
    private String genero;

    protected Competicao() {
    }

    public Competicao(String slug, String nome, TipoCompeticao tipo) {
        this.slug = slug;
        this.nome = nome;
        this.tipo = tipo;
        this.genero = "MASCULINO";
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Competicao competicao)) {
            return false;
        }
        return id != null && id.equals(competicao.id);
    }

    @Override
    public int hashCode() {
        return Competicao.class.hashCode();
    }
}
```

`competicao/domain/Edicao.java` — o `@OneToMany` para fases usa cascade, porque fase não existe sem edição:

```java
package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "edicao")
@Getter
@Setter
public class Edicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competicao_id", nullable = false)
    private Competicao competicao;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private String nome;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @OneToMany(mappedBy = "edicao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem")
    private List<Fase> fases = new ArrayList<>();

    protected Edicao() {
    }

    public Edicao(Competicao competicao, Long temporadaId, String nome) {
        this.competicao = competicao;
        this.temporadaId = temporadaId;
        this.nome = nome;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Edicao edicao)) {
            return false;
        }
        return id != null && id.equals(edicao.id);
    }

    @Override
    public int hashCode() {
        return Edicao.class.hashCode();
    }
}
```

`competicao/domain/Fase.java`:

```java
package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "fase")
@Getter
@Setter
public class Fase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Column(nullable = false)
    private Integer ordem;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFase tipo;

    @Column(name = "jogos_por_confronto", nullable = false)
    private Integer jogosPorConfronto;

    @Column(name = "tem_gol_fora", nullable = false)
    private Boolean temGolFora;

    @Column(name = "tem_prorrogacao", nullable = false)
    private Boolean temProrrogacao;

    @Column(name = "tem_penaltis", nullable = false)
    private Boolean temPenaltis;

    protected Fase() {
    }

    public Fase(Edicao edicao, Integer ordem, String nome, TipoFase tipo) {
        this.edicao = edicao;
        this.ordem = ordem;
        this.nome = nome;
        this.tipo = tipo;
        this.jogosPorConfronto = 1;
        this.temGolFora = false;
        this.temProrrogacao = false;
        this.temPenaltis = false;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Fase fase)) {
            return false;
        }
        return id != null && id.equals(fase.id);
    }

    @Override
    public int hashCode() {
        return Fase.class.hashCode();
    }
}
```

`competicao/domain/EdicaoParticipante.java`:

```java
package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "edicao_participante")
@Getter
@Setter
public class EdicaoParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "posicao_final")
    private Integer posicaoFinal;

    protected EdicaoParticipante() {
    }

    public EdicaoParticipante(Edicao edicao, Long clubeId) {
        this.edicao = edicao;
        this.clubeId = clubeId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EdicaoParticipante participante)) {
            return false;
        }
        return id != null && id.equals(participante.id);
    }

    @Override
    public int hashCode() {
        return EdicaoParticipante.class.hashCode();
    }
}
```

`competicao/domain/RegraClassificacao.java`:

```java
package br.com.api.footfirma.competicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "regra_classificacao")
@Getter
@Setter
public class RegraClassificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Column(name = "posicao_inicio", nullable = false)
    private Integer posicaoInicio;

    @Column(name = "posicao_fim", nullable = false)
    private Integer posicaoFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoClassificacao tipo;

    @Column(name = "competicao_destino_id")
    private Long competicaoDestinoId;

    protected RegraClassificacao() {
    }

    public RegraClassificacao(Edicao edicao, Integer posicaoInicio, Integer posicaoFim,
                              TipoClassificacao tipo, Long competicaoDestinoId) {
        this.edicao = edicao;
        this.posicaoInicio = posicaoInicio;
        this.posicaoFim = posicaoFim;
        this.tipo = tipo;
        this.competicaoDestinoId = competicaoDestinoId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof RegraClassificacao regra)) {
            return false;
        }
        return id != null && id.equals(regra.id);
    }

    @Override
    public int hashCode() {
        return RegraClassificacao.class.hashCode();
    }
}
```

- [x] **Step 6: Criar os repositórios**

`competicao/repository/CompeticaoRepository.java`:

```java
package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.Competicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompeticaoRepository extends JpaRepository<Competicao, Long> {

    Optional<Competicao> findBySlug(String slug);

    @Query(value = """
            select c.* from competicao c
            join pais p on p.id = c.pais_id
            where p.iso_code = :isoPais
            order by c.nivel nulls last, c.nome
            """, nativeQuery = true)
    List<Competicao> findByPaisIso(@Param("isoPais") String isoPais);
}
```

A query é nativa porque `pais` pertence a outro módulo: JPQL exigiria mapear a entidade `Pais` aqui, o que o Modulith reprova. SQL nativo cruza a tabela sem cruzar a fronteira de código.

`competicao/repository/EdicaoRepository.java`:

```java
package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.Edicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EdicaoRepository extends JpaRepository<Edicao, Long> {

    @Query("""
            select e from Edicao e
            join fetch e.competicao c
            left join fetch e.fases
            where c.slug = :slug and e.temporadaId = :temporadaId
            """)
    Optional<Edicao> buscarPorSlugETemporadaId(@Param("slug") String slug,
                                               @Param("temporadaId") Long temporadaId);
}
```

A query recebe o `temporadaId` já resolvido, e não o label, por uma razão de arquitetura: uma subconsulta JPQL do tipo `(select t.id from Temporada t where t.label = :label)` referenciaria a entidade `Temporada`, que pertence a outro módulo, e faria o `ModularidadeTest` falhar. Quem traduz label → id é o service, chamando `TemporadaService.buscarPorLabel`.

`competicao/repository/RegraClassificacaoRepository.java`:

```java
package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegraClassificacaoRepository extends JpaRepository<RegraClassificacao, Long> {

    List<RegraClassificacao> findByEdicaoIdOrderByPosicaoInicio(Long edicaoId);
}
```

- [x] **Step 7: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*CompeticaoRepositoryTest'`
Expected: PASS nos três testes.

- [x] **Step 8: Criar DTOs, mapper e serviço público**

`competicao/dto/CompeticaoResumo.java`:

```java
package br.com.api.footfirma.competicao.dto;

public record CompeticaoResumo(Long id, String slug, String nome, String tipo, Integer nivel) {
}
```

`competicao/dto/FaseResumo.java`:

```java
package br.com.api.footfirma.competicao.dto;

public record FaseResumo(
        Integer ordem,
        String nome,
        String tipo,
        Integer jogosPorConfronto,
        Boolean temProrrogacao,
        Boolean temPenaltis
) {
}
```

`competicao/dto/RegraClassificacaoResumo.java`:

```java
package br.com.api.footfirma.competicao.dto;

public record RegraClassificacaoResumo(Integer posicaoInicio, Integer posicaoFim, String tipo) {
}
```

`competicao/dto/EdicaoDetalhe.java`:

```java
package br.com.api.footfirma.competicao.dto;

import java.time.LocalDate;
import java.util.List;

public record EdicaoDetalhe(
        Long id,
        String nome,
        String competicao,
        LocalDate dataInicio,
        LocalDate dataFim,
        List<FaseResumo> fases,
        List<RegraClassificacaoResumo> regras
) {
}
```

`competicao/mapper/CompeticaoMapper.java`:

```java
package br.com.api.footfirma.competicao.mapper;

import br.com.api.footfirma.competicao.domain.Competicao;
import br.com.api.footfirma.competicao.domain.Fase;
import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.FaseResumo;
import br.com.api.footfirma.competicao.dto.RegraClassificacaoResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface CompeticaoMapper {

    @Mapping(target = "tipo", expression = "java(competicao.getTipo().name())")
    CompeticaoResumo paraResumo(Competicao competicao);

    List<CompeticaoResumo> paraResumos(List<Competicao> competicoes);

    @Mapping(target = "tipo", expression = "java(fase.getTipo().name())")
    FaseResumo paraResumo(Fase fase);

    List<FaseResumo> paraFases(List<Fase> fases);

    @Mapping(target = "tipo", expression = "java(regra.getTipo().name())")
    RegraClassificacaoResumo paraResumo(RegraClassificacao regra);

    List<RegraClassificacaoResumo> paraRegras(List<RegraClassificacao> regras);
}
```

`competicao/CompeticaoService.java`:

```java
package br.com.api.footfirma.competicao;

import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;

import java.util.List;
import java.util.Optional;

public interface CompeticaoService {

    List<CompeticaoResumo> listarPorPais(String isoPais);

    Optional<EdicaoDetalhe> buscarEdicao(String slugCompeticao, String labelTemporada);
}
```

`competicao/internal/CompeticaoServiceImpl.java` — note a composição com `TemporadaService`, que é como um módulo consome outro:

```java
package br.com.api.footfirma.competicao.internal;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.competicao.mapper.CompeticaoMapper;
import br.com.api.footfirma.competicao.repository.EdicaoRepository;
import br.com.api.footfirma.competicao.repository.CompeticaoRepository;
import br.com.api.footfirma.competicao.repository.RegraClassificacaoRepository;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CompeticaoServiceImpl implements CompeticaoService {

    private final CompeticaoRepository competicaoRepository;
    private final EdicaoRepository edicaoRepository;
    private final RegraClassificacaoRepository regraClassificacaoRepository;
    private final TemporadaService temporadaService;
    private final CompeticaoMapper competicaoMapper;

    @Override
    public List<CompeticaoResumo> listarPorPais(String isoPais) {
        return competicaoMapper.paraResumos(competicaoRepository.findByPaisIso(isoPais));
    }

    @Override
    public Optional<EdicaoDetalhe> buscarEdicao(String slugCompeticao, String labelTemporada) {
        return temporadaService.buscarPorLabel(labelTemporada)
                .map(TemporadaResumo::id)
                .flatMap(temporadaId -> edicaoRepository.buscarPorSlugETemporadaId(slugCompeticao, temporadaId))
                .map(edicao -> new EdicaoDetalhe(
                        edicao.getId(),
                        edicao.getNome(),
                        edicao.getCompeticao().getNome(),
                        edicao.getDataInicio(),
                        edicao.getDataFim(),
                        competicaoMapper.paraFases(edicao.getFases()),
                        competicaoMapper.paraRegras(
                                regraClassificacaoRepository.findByEdicaoIdOrderByPosicaoInicio(edicao.getId()))));
    }
}
```

- [x] **Step 9: Compilar, verificar modularidade e commitar**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest' --tests '*CompeticaoRepositoryTest'`
Expected: PASS. `competicao` agora depende de `temporada` pela interface pública — dependência legítima e visível na documentação gerada pelo `Documenter`.

```bash
git add src/main/resources/db/migration/V10__cria_competicao.sql \
        src/main/resources/db/migration/V11__cria_participante_e_regra.sql \
        src/main/java/br/com/api/footfirma/competicao \
        src/test/java/br/com/api/footfirma/competicao
git commit -m "feat(competicao): cria módulo de competição, edição e fases

- Migrations V10 e V11 criam competicao, edicao, fase, edicao_participante
  e regra_classificacao
- Regra de acesso, rebaixamento e vaga continental é dado: campeonato com
  formato novo é INSERT, não classe nova
- Fase existe desde já porque a Copa do Brasil exige mata-mata
- CompeticaoService consome TemporadaService pela interface pública"
```

---

## Task 9: API REST de clubes

Primeiro endpoint do projeto. Estabelece o padrão de controller que as duas tarefas seguintes repetem.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/clube/web/ClubeController.java`
- Test: `src/test/java/br/com/api/footfirma/clube/web/ClubeControllerTest.java`

**Interfaces:**
- Consumes: `ClubeService.listar(Pageable)` e `ClubeService.buscarPorSlug(String)` (Task 4); `RecursoNaoEncontradoException` (Task 1).
- Produces: `GET /api/v1/clubes` (paginado) e `GET /api/v1/clubes/{slug}`.

- [x] **Step 1: Escrever o teste de controller (vai falhar)**

`src/test/java/br/com/api/footfirma/clube/web/ClubeControllerTest.java`:

```java
package br.com.api.footfirma.clube.web;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClubeController.class)
class ClubeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ClubeService clubeService;

    @Test
    void deveListarClubesPaginados() throws Exception {
        when(clubeService.listar(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new ClubeResumo(1L, "gremio", "Grêmio", 82))));

        mockMvc.perform(get("/api/v1/clubes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("gremio"))
                .andExpect(jsonPath("$.content[0].reputacao").value(82));
    }

    @Test
    void deveRetornarDetalheDoClube() throws Exception {
        when(clubeService.buscarPorSlug("gremio")).thenReturn(Optional.of(new ClubeDetalhe(
                1L, "gremio", "Grêmio Foot-Ball Porto Alegrense", "Grêmio", "Tricolor",
                1903, "Arena do Grêmio", 55662, 82, 75)));

        mockMvc.perform(get("/api/v1/clubes/gremio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeOficial").value("Grêmio Foot-Ball Porto Alegrense"))
                .andExpect(jsonPath("$.capacidadeEstadio").value(55662));
    }

    @Test
    void deveRetornar404QuandoSlugNaoExiste() throws Exception {
        when(clubeService.buscarPorSlug("inexistente")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/clubes/inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }
}
```

- [x] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*ClubeControllerTest'`
Expected: FAIL na compilação — `ClubeController` não existe.

- [x] **Step 3: Criar o controller**

`clube/web/ClubeController.java` — a classe é package-private de propósito: o controller é detalhe interno do módulo.

```java
package br.com.api.footfirma.clube.web;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clubes")
@RequiredArgsConstructor
@Tag(name = "Clubes", description = "Consulta ao catálogo de clubes")
class ClubeController {

    private final ClubeService clubeService;

    @GetMapping
    @Operation(summary = "Lista clubes paginados, ordenados por nome")
    Page<ClubeResumo> listar(@ParameterObject @PageableDefault(size = 20) Pageable paginacao) {
        return clubeService.listar(paginacao);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Busca um clube pelo slug")
    ClubeDetalhe buscar(@PathVariable String slug) {
        return clubeService.buscarPorSlug(slug)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Clube não encontrado: " + slug));
    }
}
```

- [x] **Step 4: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*ClubeControllerTest'`
Expected: PASS nos três testes. Se o terceiro falhar com 401 em vez de 404, a rota não foi liberada no `SecurityConfig` da Task 1 — confira a linha `GET /api/v1/clubes/**`.

- [x] **Step 5: Compilar e commitar**

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest'`
Expected: PASS.

```bash
git add src/main/java/br/com/api/footfirma/clube/web \
        src/test/java/br/com/api/footfirma/clube/web
git commit -m "feat(clube): expõe listagem e detalhe de clube em /api/v1/clubes

- Controller package-private: é detalhe interno do módulo Modulith
- Listagem paginada com @PageableDefault de 20
- Slug inexistente devolve 404 em ProblemDetail, não 200 com corpo de erro"
```

---

## Task 10: API REST de jogadores

Cria o `JogadorService` — que as Tasks 5 a 7 deixaram pendente de propósito, para que a interface pública nascesse desenhada pelo consumidor — e o endpoint de consulta.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/JogadorResumo.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/JogadorDetalhe.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/AtributosJogador.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/mapper/JogadorMapper.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/internal/JogadorServiceImpl.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/web/JogadorController.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/web/JogadorControllerTest.java`

**Interfaces:**
- Consumes: `JogadorRepository`, `JogadorAtributoRepository`, `JogadorVinculoRepository` (Tasks 5–7); `TemporadaService` (Task 2); `ClubeService.buscarPorSlug` (Task 4).
- Produces: `JogadorService.buscarPorSlug(String slug, String labelTemporada) -> Optional<JogadorDetalhe>` e `JogadorService.listarElenco(String slugClube, String labelTemporada) -> List<JogadorResumo>`.

- [x] **Step 1: Criar os DTOs**

`jogador/dto/JogadorResumo.java`:

```java
package br.com.api.footfirma.jogador.dto;

public record JogadorResumo(
        Long id,
        String slug,
        String nomeExibicao,
        Integer idade,
        String posicao,
        Integer numeroCamisa
) {
}
```

`jogador/dto/AtributosJogador.java` — os 18 campos, com os mesmos nomes que o Plano 2 vai consumir:

```java
package br.com.api.footfirma.jogador.dto;

public record AtributosJogador(
        Integer ritmo, Integer forca, Integer folego, Integer salto, Integer agilidade,
        Integer passe, Integer drible, Integer cruzamento, Integer frieza,
        Integer finalizacao, Integer cabeceio, Integer falta, Integer penalti,
        Integer desarme, Integer marcacao,
        Integer golReflexo, Integer golPosicionamento, Integer golManejo,
        Integer potencialBase, String fonteAtributo
) {
}
```

`jogador/dto/JogadorDetalhe.java`:

```java
package br.com.api.footfirma.jogador.dto;

import java.time.LocalDate;
import java.util.List;

public record JogadorDetalhe(
        Long id,
        String slug,
        String nomeCompleto,
        String nomeExibicao,
        LocalDate dataNascimento,
        Integer idade,
        Integer alturaCm,
        Integer pesoKg,
        String pePreferido,
        String posicaoPrincipal,
        String origem,
        AtributosJogador atributos,
        List<String> caracteristicas
) {
}
```

- [x] **Step 2: Escrever o teste de controller (vai falhar)**

`src/test/java/br/com/api/footfirma/jogador/web/JogadorControllerTest.java`:

```java
package br.com.api.footfirma.jogador.web;

import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JogadorController.class)
class JogadorControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JogadorService jogadorService;

    @Test
    void deveRetornarDetalheDoJogadorComAtributos() throws Exception {
        var atributos = new AtributosJogador(
                78, 70, 82, 66, 80, 84, 83, 79, 85,
                81, 62, 86, 88, 45, 40, 12, 11, 10, 87, "IMPORTADO");
        when(jogadorService.buscarPorSlug("raphael-veiga", "2025")).thenReturn(Optional.of(new JogadorDetalhe(
                1L, "raphael-veiga", "Raphael Cavalcante Veiga", "Raphael Veiga",
                LocalDate.of(1995, 6, 19), 31, 177, 74, "ESQUERDO", "MEA", "REAL",
                atributos, List.of("ESPECIALISTA_FALTA", "ESPECIALISTA_PENALTI"))));

        mockMvc.perform(get("/api/v1/jogadores/raphael-veiga?temporada=2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value("Raphael Veiga"))
                .andExpect(jsonPath("$.atributos.falta").value(86))
                .andExpect(jsonPath("$.caracteristicas[0]").value("ESPECIALISTA_FALTA"));
    }

    @Test
    void deveListarElencoDoClubeNaTemporada() throws Exception {
        when(jogadorService.listarElenco("palmeiras", "2025")).thenReturn(List.of(
                new JogadorResumo(1L, "raphael-veiga", "Raphael Veiga", 31, "MEA", 23)));

        mockMvc.perform(get("/api/v1/jogadores?clube=palmeiras&temporada=2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("raphael-veiga"))
                .andExpect(jsonPath("$[0].numeroCamisa").value(23));
    }

    @Test
    void deveRetornar404QuandoJogadorNaoExiste() throws Exception {
        when(jogadorService.buscarPorSlug("inexistente", "2025")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jogadores/inexistente?temporada=2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }
}
```

- [x] **Step 3: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*JogadorControllerTest'`
Expected: FAIL na compilação — `JogadorService` e `JogadorController` não existem.

- [x] **Step 4: Criar o mapper**

`jogador/mapper/JogadorMapper.java`:

```java
package br.com.api.footfirma.jogador.mapper;

import br.com.api.footfirma.jogador.domain.JogadorAtributo;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface JogadorMapper {

    @Mapping(target = "fonteAtributo", expression = "java(atributo.getFonteAtributo().name())")
    AtributosJogador paraAtributos(JogadorAtributo atributo);
}
```

`JogadorDetalhe` e `JogadorResumo` são montados à mão no service porque combinam dados de três repositórios — MapStruct não ajuda quando a origem é composta.

- [x] **Step 5: Criar o serviço público e a implementação**

`jogador/JogadorService.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;

import java.util.List;
import java.util.Optional;

public interface JogadorService {

    Optional<JogadorDetalhe> buscarPorSlug(String slug, String labelTemporada);

    List<JogadorResumo> listarElenco(String slugClube, String labelTemporada);
}
```

`jogador/internal/JogadorServiceImpl.java`:

```java
package br.com.api.footfirma.jogador.internal;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.jogador.mapper.JogadorMapper;
import br.com.api.footfirma.jogador.repository.JogadorAtributoRepository;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.JogadorVinculoRepository;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JogadorServiceImpl implements JogadorService {

    private final JogadorRepository jogadorRepository;
    private final JogadorAtributoRepository jogadorAtributoRepository;
    private final JogadorVinculoRepository jogadorVinculoRepository;
    private final TemporadaService temporadaService;
    private final ClubeService clubeService;
    private final JogadorMapper jogadorMapper;

    @Override
    public Optional<JogadorDetalhe> buscarPorSlug(String slug, String labelTemporada) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        if (temporadaId.isEmpty()) {
            return Optional.empty();
        }
        return jogadorRepository.findBySlug(slug)
                .map(jogador -> montarDetalhe(jogador, temporadaId.get()));
    }

    @Override
    public List<JogadorResumo> listarElenco(String slugClube, String labelTemporada) {
        var clube = clubeService.buscarPorSlug(slugClube);
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        if (clube.isEmpty() || temporadaId.isEmpty()) {
            return List.of();
        }
        return jogadorVinculoRepository.buscarElenco(clube.get().id(), temporadaId.get()).stream()
                .map(vinculo -> new JogadorResumo(
                        vinculo.getJogador().getId(),
                        vinculo.getJogador().getSlug(),
                        vinculo.getJogador().getNomeExibicao(),
                        idadeEm(vinculo.getJogador().getDataNascimento()),
                        vinculo.getJogador().getPosicaoPrincipal().getCodigo(),
                        vinculo.getNumeroCamisa()))
                .toList();
    }

    private JogadorDetalhe montarDetalhe(Jogador jogador, Long temporadaId) {
        var atributos = jogadorAtributoRepository
                .findByJogadorIdAndTemporadaId(jogador.getId(), temporadaId)
                .map(jogadorMapper::paraAtributos)
                .orElse(null);
        return new JogadorDetalhe(
                jogador.getId(),
                jogador.getSlug(),
                jogador.getNomeCompleto(),
                jogador.getNomeExibicao(),
                jogador.getDataNascimento(),
                idadeEm(jogador.getDataNascimento()),
                jogador.getAlturaCm(),
                jogador.getPesoKg(),
                jogador.getPePreferido().name(),
                jogador.getPosicaoPrincipal().getCodigo(),
                jogador.getOrigem().name(),
                atributos,
                List.of());
    }

    private Integer idadeEm(LocalDate nascimento) {
        return Period.between(nascimento, LocalDate.now()).getYears();
    }
}
```

A lista de características fica vazia neste plano — a associação existe no banco desde a Task 7, e populá-la só faz sentido quando o importador do Plano 3 a preencher. Devolver `List.of()` é honesto; inventar dado não seria.

- [x] **Step 6: Criar o controller**

`jogador/web/JogadorController.java`:

```java
package br.com.api.footfirma.jogador.web;

import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jogadores")
@RequiredArgsConstructor
@Tag(name = "Jogadores", description = "Consulta ao catálogo de jogadores")
class JogadorController {

    private final JogadorService jogadorService;

    @GetMapping("/{slug}")
    @Operation(summary = "Busca um jogador pelo slug, com atributos da temporada informada")
    JogadorDetalhe buscar(@PathVariable String slug, @RequestParam String temporada) {
        return jogadorService.buscarPorSlug(slug, temporada)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Jogador não encontrado: " + slug));
    }

    @GetMapping
    @Operation(summary = "Lista o elenco de um clube em uma temporada")
    List<JogadorResumo> listarElenco(@RequestParam String clube, @RequestParam String temporada) {
        return jogadorService.listarElenco(clube, temporada);
    }
}
```

O elenco não é paginado porque um elenco tem limite natural (algumas dezenas de jogadores) e paginar uma lista que nunca cresce só complica o cliente.

- [x] **Step 7: Rodar o teste e commitar**

Run: `./gradlew test --tests '*JogadorControllerTest'`
Expected: PASS nos três testes.

Run: `./gradlew compileJava && ./gradlew test --tests '*ModularidadeTest'`
Expected: PASS. `jogador` agora depende de `temporada` e `clube` pelas interfaces públicas.

```bash
git add src/main/java/br/com/api/footfirma/jogador \
        src/test/java/br/com/api/footfirma/jogador/web
git commit -m "feat(jogador): expõe consulta de jogador e elenco em /api/v1/jogadores

- JogadorService nasce desenhado pelo consumidor, depois das entidades
- Detalhe traz as 18 skills da temporada pedida
- Elenco não é paginado: a lista tem limite natural
- Características voltam vazias até o importador do Plano 3 preenchê-las"
```

---

## Task 11: API REST de competições e fechamento

Último endpoint e verificação final do plano inteiro.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/competicao/web/CompeticaoController.java`
- Test: `src/test/java/br/com/api/footfirma/competicao/web/CompeticaoControllerTest.java`
- Create: `docs/adr/2026-08-01-catalogo-read-only.md`

**Interfaces:**
- Consumes: `CompeticaoService` (Task 8).
- Produces: `GET /api/v1/competicoes?pais=BRA` e `GET /api/v1/competicoes/{slug}/edicoes/{temporada}`.

- [x] **Step 1: Escrever o teste de controller (vai falhar)**

`src/test/java/br/com/api/footfirma/competicao/web/CompeticaoControllerTest.java`:

```java
package br.com.api.footfirma.competicao.web;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.competicao.dto.FaseResumo;
import br.com.api.footfirma.competicao.dto.RegraClassificacaoResumo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompeticaoController.class)
class CompeticaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompeticaoService competicaoService;

    @Test
    void deveListarCompeticoesDoPais() throws Exception {
        when(competicaoService.listarPorPais("BRA")).thenReturn(List.of(
                new CompeticaoResumo(1L, "brasileirao-serie-a", "Brasileirão Série A", "LIGA", 1),
                new CompeticaoResumo(2L, "copa-do-brasil", "Copa do Brasil", "COPA", null)));

        mockMvc.perform(get("/api/v1/competicoes?pais=BRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("brasileirao-serie-a"))
                .andExpect(jsonPath("$[1].tipo").value("COPA"));
    }

    @Test
    void deveRetornarEdicaoComFasesERegras() throws Exception {
        when(competicaoService.buscarEdicao("brasileirao-serie-a", "2025")).thenReturn(Optional.of(
                new EdicaoDetalhe(1L, "Brasileirão Série A 2025", "Brasileirão Série A",
                        LocalDate.of(2025, 3, 29), LocalDate.of(2025, 12, 21),
                        List.of(new FaseResumo(1, "Fase única", "PONTOS_CORRIDOS", 1, false, false)),
                        List.of(new RegraClassificacaoResumo(17, 20, "REBAIXAMENTO")))));

        mockMvc.perform(get("/api/v1/competicoes/brasileirao-serie-a/edicoes/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases[0].tipo").value("PONTOS_CORRIDOS"))
                .andExpect(jsonPath("$.regras[0].tipo").value("REBAIXAMENTO"))
                .andExpect(jsonPath("$.regras[0].posicaoInicio").value(17));
    }

    @Test
    void deveRetornar404QuandoEdicaoNaoExiste() throws Exception {
        when(competicaoService.buscarEdicao("brasileirao-serie-a", "1998")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/competicoes/brasileirao-serie-a/edicoes/1998"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }
}
```

- [x] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*CompeticaoControllerTest'`
Expected: FAIL na compilação — `CompeticaoController` não existe.

- [x] **Step 3: Criar o controller**

`competicao/web/CompeticaoController.java`:

```java
package br.com.api.footfirma.competicao.web;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/competicoes")
@RequiredArgsConstructor
@Tag(name = "Competições", description = "Consulta ao catálogo de competições e suas edições")
class CompeticaoController {

    private final CompeticaoService competicaoService;

    @GetMapping
    @Operation(summary = "Lista as competições de um país, da divisão mais alta para a mais baixa")
    List<CompeticaoResumo> listar(@RequestParam(defaultValue = "BRA") String pais) {
        return competicaoService.listarPorPais(pais);
    }

    @GetMapping("/{slug}/edicoes/{temporada}")
    @Operation(summary = "Busca a edição de uma competição em uma temporada, com fases e regras de classificação")
    EdicaoDetalhe buscarEdicao(@PathVariable String slug, @PathVariable String temporada) {
        return competicaoService.buscarEdicao(slug, temporada)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Edição não encontrada: " + slug + " " + temporada));
    }
}
```

- [x] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*CompeticaoControllerTest'`
Expected: PASS nos três testes.

- [x] **Step 5: Registrar a decisão arquitetural**

O checklist (`.rules/java-checklist.md`, item 9) exige registrar decisão arquitetural relevante.

`docs/adr/2026-08-01-catalogo-read-only.md`:

```markdown
# Catálogo é read-only e separado do save

Status: verificado em 2026-08-01

## Decisão

Os módulos `temporada`, `geografia`, `clube`, `jogador` e `competicao` formam o
catálogo: dado do mundo real, compartilhado entre todas as carreiras. A API REST
os expõe apenas para leitura; a única escrita virá do importador (Plano 3).

O estado de carreira — jogador envelhecido, transferido, lesionado, tabela da
temporada em curso — pertencerá a um módulo `carreira` separado, que referencia o
catálogo sem alterá-lo.

## Por quê

Sem essa fronteira, dois saves se contaminam e não há como recomeçar uma carreira.
Ela é verificada por construção: nenhum controller do catálogo aceita POST, PUT ou
DELETE, e o `ModularidadeTest` reprova qualquer módulo que escreva em tabela alheia.

## Consequências

- Atributos de jogador são versionados por `(jogador_id, temporada_id)`, nunca
  sobrescritos.
- Perfis de peso do overall (Plano 2) são versionados: rebalancear cria versão nova
  em vez de alterar a existente, para não mudar o equilíbrio de carreiras em curso.
- `jogador.chave_natural` com `unique` garante que reimportar não duplique.

## Fontes

- `docs/superpowers/specs/2026-08-01-catalogo-futebol-brasileiro-design.md`
- `src/main/java/br/com/api/footfirma/*/web/` — nenhum método de escrita
- `src/test/java/br/com/api/footfirma/ModularidadeTest.java`

## Validação

```bash
./gradlew test --tests '*ModularidadeTest'
grep -rL "PostMapping\|PutMapping\|DeleteMapping" src/main/java/br/com/api/footfirma/*/web/
```
```

- [x] **Step 6: Rodar a suíte completa**

Este é o único ponto do plano em que a suíte inteira roda. Exige Docker.

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, com todos os testes verdes. Se algum falhar, corrija antes de commitar — não enfraqueça o teste.

- [x] **Step 7: Percorrer o checklist e commitar**

Percorra `.rules/java-checklist.md` inteiro. Itens que merecem atenção específica neste plano:

- Nenhum controller tem `@Transactional` nem acessa repository direto
- Nenhuma entidade JPA aparece em assinatura de controller ou dentro de DTO
- Todo `@ManyToOne` é `LAZY`
- Toda migration nova tem índice para as FKs que serão filtradas
- Nenhum segredo no diff

```bash
git add src/main/java/br/com/api/footfirma/competicao/web \
        src/test/java/br/com/api/footfirma/competicao/web \
        docs/adr/2026-08-01-catalogo-read-only.md
git commit -m "feat(competicao): expõe competições e edições em /api/v1/competicoes

- Edição retorna fases e regras de classificação juntas: é o que o motor de
  temporada vai consumir
- Registra ADR da fronteira catálogo/save em docs/adr/"
```

---

## Verificação final do plano

Ao terminar a Task 11, estes fatos devem ser verdadeiros:

| Verificação | Comando |
|---|---|
| Compila | `./gradlew compileJava` |
| Suíte completa verde | `./gradlew build` |
| 11 migrations aplicadas, nenhuma editada | `ls src/main/resources/db/migration/` |
| Fronteiras de módulo íntegras | `./gradlew test --tests '*ModularidadeTest'` |
| Catálogo é read-only | nenhum `@PostMapping`/`@PutMapping`/`@DeleteMapping` em `*/web/` |

**O que este plano deliberadamente não entrega:** cálculo de overall, arquétipos de crescimento, atributos ocultos populados, características associadas a jogadores, geração de jogadores de base, ETL e qualquer dado real no banco. O banco fica com o schema completo, países, estados, posições e características carregados por seed — e vazio de clubes e jogadores até o Plano 3.


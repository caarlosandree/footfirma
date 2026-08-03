# Avaliação e Overall — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o módulo `avaliacao` — perfis de peso versionados, cálculo de overall como função pura, materialização em lote e dois endpoints read-only — sem inverter a dependência do catálogo.

**Architecture:** Um módulo Spring Modulith novo (`avaliacao`) dono de `perfil_avaliacao`, `perfil_avaliacao_peso` e `jogador_overall`. Ele depende de `jogador` e `temporada` e lê ambos **apenas pela interface de serviço** — nunca por consulta a tabela alheia. O overall é `Σ(atributo × peso)` calculado por uma função pura sem Spring, materializado por lotes transacionais e servido por controller próprio, deixando o `JogadorController` intacto.

**Tech Stack:** Java 25 · Spring Boot 4.1.0 · Spring Modulith 2.1.0 · PostgreSQL · Flyway · MapStruct 1.6.3 · Lombok · springdoc-openapi 3.0.3 · JUnit 5 · AssertJ · Testcontainers

**Spec:** `docs/superpowers/specs/2026-08-02-avaliacao-overall-design.md`

## Global Constraints

Todo trabalho acontece em `backend/footfirma/`. Estas regras valem para **todas** as tarefas:

- **Pacote base:** `br.com.api.footfirma`. Cada pacote diretamente abaixo dele é um módulo Modulith.
- **Nomes em português** — pacotes, tabelas, colunas, classes, métodos, testes.
- **Referência a outro módulo é coluna `Long` crua, nunca `@ManyToOne`.** É o padrão já estabelecido no código: `Jogador.paisId` e `JogadorAtributo.temporadaId` são `Long`; só `posicaoPrincipal`, que vive no mesmo módulo, é associação. Como `jogador_id`, `temporada_id` e `posicao_id` de `jogador_overall` apontam para fora de `avaliacao`, **nenhuma delas vira `@ManyToOne`**. A integridade fica na FK do banco.
- **Estratégia de ID:** `bigint generated always as identity`, mapeado com `@GeneratedValue(strategy = GenerationType.IDENTITY)` e campo `Long`.
- **Tipos:** `text` com `check` de tamanho (nunca `varchar(n)`), `timestamptz` para data-hora, `date` para data pura, enum como `text` + `check` mapeado com `@Enumerated(EnumType.STRING)`.
- **Migrations:** `src/main/resources/db/migration/V{n}__descricao_snake_case.sql`, numeração sequencial sem buracos. **Migration aplicada nunca é editada** — o hook `.claude/hooks/guard.mjs` bloqueia. As próximas livres são `V12` e `V13`.
- **Entidades JPA:** proibido `@Data`, `@EqualsAndHashCode`, `@ToString`. `equals`/`hashCode` escritos à mão pelo id. Construtor sem argumentos `protected` escrito à mão. Todo `@ManyToOne`/`@OneToOne` é `LAZY`.
- **Lombok permitido:** apenas `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`.
- **MapStruct:** `unmappedTargetPolicy=ERROR` está ativo. **Nunca** escreva `componentModel = "spring"`; já é padrão do build.
- **Visibilidade Modulith:** tipos no pacote raiz do módulo são API pública; tipos em `web/`, `domain/`, `repository/`, `mapper/`, `internal/` são invisíveis para outros módulos. Um subpacote só cruza a fronteira com `@NamedInterface`. Entidade JPA nunca cruza fronteira.
- **DI só por construtor.** `@Autowired` em campo é proibido.
- **Transações:** `@Transactional(readOnly = true)` na classe do service. Nunca em controller.
- **Testes:** Testcontainers sempre, via `@Import(TestcontainersConfiguration.class)`. H2 e mock de repository são proibidos. `@DataJpaTest` exige `@AutoConfigureTestDatabase(replace = Replace.NONE)`. Use `@MockitoBean`, nunca `@MockBean`.
- **Pacotes de anotação do Boot 4** (snippet de Boot 3 não compila):
  - `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`
  - `@AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure`
  - `@WebMvcTest` → `org.springframework.boot.webmvc.test.autoconfigure`
  - `@MockitoBean` → `org.springframework.test.context.bean.override.mockito`
- **Nomes de teste:** `deve<Comportamento>Quando<Condição>`, em português. Corpo em três blocos separados por linha em branco.
- **Commits:** Conventional Commits em português, escopo = módulo. **Nunca** adicione `Co-authored-by:`.
- **Não rode `./gradlew test` inteiro a cada passo** — use `--tests '*NomeDoTest'`. Não rode `bootRun` (o guard bloqueia).

**Validação rápida durante o trabalho:** `./gradlew compileJava`

**Branch:** `feat/avaliacao-overall`, já criada a partir de `staging`.

---

## Estrutura de arquivos

```
src/main/java/br/com/api/footfirma/
├── config/
│   └── SecurityConfig.java              # MODIFICAR: libera GET /api/v1/rankings
├── jogador/
│   ├── JogadorService.java              # MODIFICAR: +4 métodos para a fronteira
│   ├── dto/
│   │   ├── package-info.java            # CRIAR: @NamedInterface("dto")
│   │   ├── PosicaoCatalogo.java         # CRIAR: (id, codigo, nome, setor)
│   │   └── JogadorComAtributos.java     # CRIAR: (jogadorId, atributos)
│   ├── repository/
│   │   ├── JogadorRepository.java       # MODIFICAR: buscarPorIds
│   │   ├── JogadorAtributoRepository.java # MODIFICAR: página por temporada
│   │   └── JogadorVinculoRepository.java  # MODIFICAR: vínculos por ids
│   └── internal/JogadorServiceImpl.java # MODIFICAR: implementa os 4 métodos
└── avaliacao/
    ├── package-info.java                # @ApplicationModule(displayName = "Avaliação")
    ├── AvaliacaoService.java            # porta pública: 3 métodos
    ├── dto/
    │   ├── AvaliacaoDePosicao.java
    │   ├── AvaliacoesDoJogador.java
    │   ├── ItemDeRanking.java
    │   └── ResultadoMaterializacao.java
    ├── domain/
    │   ├── AtributoAvaliavel.java       # enum de 18 constantes, cada uma com seu extrator
    │   ├── PerfilAvaliacao.java         # entidade + Map<AtributoAvaliavel, BigDecimal> dos pesos
    │   └── JogadorOverall.java
    ├── repository/
    │   ├── PerfilAvaliacaoRepository.java
    │   └── JogadorOverallRepository.java
    ├── web/AvaliacaoController.java     # package-private
    └── internal/
        ├── AvaliacaoServiceImpl.java
        ├── CalculadoraDeOverall.java    # função pura, sem Spring
        └── MaterializadorDeLote.java    # @Transactional por lote

src/main/resources/db/migration/
├── V12__cria_avaliacao.sql
└── V13__popula_perfil_avaliacao.sql
```

**`perfil_avaliacao_peso` não vira entidade.** É mapeada como `@ElementCollection` de
`PerfilAvaliacao`, num `Map<AtributoAvaliavel, BigDecimal>`. Motivo: os pesos nunca são
escritos pela aplicação (nascem do seed) e o que a calculadora precisa é exatamente um
mapa. Uma entidade separada com chave composta traria `@EmbeddedId` e nenhum ganho.

---

## Task 1: Abrir `jogador/dto` e expor o catálogo de posições

Sem `@NamedInterface` em `jogador/dto`, o Modulith trata o subpacote como interno e o
`ModularidadeTest` reprova `avaliacao` no primeiro build — antes de qualquer linha do
módulo novo existir. Esta tarefa é pré-requisito de compilação de tudo o que vem depois.

`listarPosicoes()` resolve os dois sentidos que `avaliacao` precisa: `"ATA"` → `id` na
consulta, e `id` → `"ATA"` na resposta. São nove linhas fixas de catálogo.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/PosicaoCatalogo.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/internal/JogadorServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorServiceCatalogoTest.java`

**Interfaces:**
- Consumes: `PosicaoRepository.findAllByOrderByOrdem()` (já existe), `Posicao.getSetor()` devolve o enum `Setor`.
- Produces: `PosicaoCatalogo(Long id, String codigo, String nome, String setor)` e `JogadorService.listarPosicoes()` retornando `List<PosicaoCatalogo>` ordenada por `posicao.ordem` (GOL primeiro, ATA último).

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/test/java/br/com/api/footfirma/jogador/JogadorServiceCatalogoTest.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JogadorServiceCatalogoTest {

    @Autowired
    JogadorService jogadorService;

    @Test
    void deveListarAsNovePosicoesNaOrdemDoCatalogo() {
        var posicoes = jogadorService.listarPosicoes();

        assertThat(posicoes).hasSize(9);
        assertThat(posicoes.getFirst())
                .extracting("codigo", "nome", "setor")
                .containsExactly("GOL", "Goleiro", "GOLEIRO");
        assertThat(posicoes.getLast().codigo()).isEqualTo("ATA");
    }

    @Test
    void deveDevolverIdentificadorDePosicaoUtilizavelComoChaveEstrangeira() {
        var posicoes = jogadorService.listarPosicoes();

        assertThat(posicoes)
                .extracting("id")
                .doesNotContainNull();
        assertThat(posicoes).extracting("codigo")
                .containsExactly("GOL", "ZAG", "LTD", "LTE", "VOL", "MEC", "MEA", "PTA", "ATA");
    }
}
```

- [ ] **Step 2: Rode o teste e confirme que ele não compila**

```bash
./gradlew test --tests '*JogadorServiceCatalogoTest'
```

Esperado: erro de compilação — `cannot find symbol: method listarPosicoes()`.

- [ ] **Step 3: Crie o `package-info.java` de `jogador/dto`**

```java
// Os records deste pacote são parte do contrato público do módulo: JogadorService os
// devolve, e avaliacao os consome ao calcular overall. Sem @NamedInterface o Modulith
// trata um subpacote como interno e reprova a dependência.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.jogador.dto;
```

- [ ] **Step 4: Crie o record `PosicaoCatalogo`**

```java
package br.com.api.footfirma.jogador.dto;

public record PosicaoCatalogo(Long id, String codigo, String nome, String setor) {
}
```

- [ ] **Step 5: Declare o método em `JogadorService`**

Adicione o import de `PosicaoCatalogo` e o método à interface:

```java
    List<PosicaoCatalogo> listarPosicoes();
```

- [ ] **Step 6: Implemente em `JogadorServiceImpl`**

Injete `PosicaoRepository` (campo `final` novo, o `@RequiredArgsConstructor` cuida do
construtor) e adicione:

```java
    @Override
    public List<PosicaoCatalogo> listarPosicoes() {
        return posicaoRepository.findAllByOrderByOrdem().stream()
                .map(posicao -> new PosicaoCatalogo(
                        posicao.getId(),
                        posicao.getCodigo(),
                        posicao.getNome(),
                        posicao.getSetor().name()))
                .toList();
    }
```

- [ ] **Step 7: Rode o teste e confirme que passa**

```bash
./gradlew test --tests '*JogadorServiceCatalogoTest'
```

Esperado: PASS, 2 testes.

- [ ] **Step 8: Confirme que a fronteira segue válida**

```bash
./gradlew test --tests '*ModularidadeTest'
```

Esperado: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/api/footfirma/jogador src/test/java/br/com/api/footfirma/jogador/JogadorServiceCatalogoTest.java
git commit -m "feat(jogador): expõe o catálogo de posições na API do módulo"
```

---

## Task 2: Schema, enum de atributos e entidades de avaliação

**Files:**
- Create: `src/main/resources/db/migration/V12__cria_avaliacao.sql`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/domain/AtributoAvaliavel.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/domain/PerfilAvaliacao.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/domain/JogadorOverall.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/repository/PerfilAvaliacaoRepository.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/repository/JogadorOverallRepository.java`
- Test: `src/test/java/br/com/api/footfirma/avaliacao/AtributoAvaliavelTest.java`
- Test: `src/test/java/br/com/api/footfirma/avaliacao/AvaliacaoSchemaTest.java`

**Interfaces:**
- Consumes: `AtributosJogador` de `jogador/dto` (20 componentes: 18 skills `Integer`, `potencialBase` `Integer`, `fonteAtributo` `String`).
- Produces:
  - `AtributoAvaliavel` — enum de 18 constantes, com `Integer extrair(AtributosJogador)`.
  - `PerfilAvaliacao` — `getId()`, `getPosicaoId()`, `getVersao()`, `getAtivo()`, `getVigenteDesde()`, `getPesos()` devolvendo `Map<AtributoAvaliavel, BigDecimal>`. O campo `ativo` é `Boolean`, então o Lombok gera `getAtivo()` e **não** `isAtivo()`.
  - `JogadorOverall` — construtor `(Long jogadorId, Long temporadaId, Long posicaoId)` e setters `setPerfilVersao`, `setOverall`, `setCalculadoEm`.
  - `PerfilAvaliacaoRepository.buscarAtivosComPesos()` → `List<PerfilAvaliacao>`.
  - `JogadorOverallRepository.findByTemporadaIdAndJogadorIdIn(Long, Collection<Long>)` → `List<JogadorOverall>`.

- [ ] **Step 1: Escreva o teste puro do enum**

Crie `src/test/java/br/com/api/footfirma/avaliacao/AtributoAvaliavelTest.java`:

```java
package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtributoAvaliavelTest {

    // Cada skill recebe um valor distinto para que uma extração trocada apareça
    // como número errado, e não como coincidência.
    private static final AtributosJogador ATRIBUTOS = new AtributosJogador(
            1, 2, 3, 4, 5,
            6, 7, 8, 9,
            10, 11, 12, 13,
            14, 15,
            16, 17, 18,
            99, "IMPORTADO");

    @Test
    void deveDeclararDezoitoAtributos() {
        assertThat(AtributoAvaliavel.values()).hasSize(18);
    }

    @Test
    void deveExtrairCadaAtributoDoRecordCorrespondente() {
        assertThat(AtributoAvaliavel.RITMO.extrair(ATRIBUTOS)).isEqualTo(1);
        assertThat(AtributoAvaliavel.AGILIDADE.extrair(ATRIBUTOS)).isEqualTo(5);
        assertThat(AtributoAvaliavel.FRIEZA.extrair(ATRIBUTOS)).isEqualTo(9);
        assertThat(AtributoAvaliavel.PENALTI.extrair(ATRIBUTOS)).isEqualTo(13);
        assertThat(AtributoAvaliavel.MARCACAO.extrair(ATRIBUTOS)).isEqualTo(15);
        assertThat(AtributoAvaliavel.GOL_MANEJO.extrair(ATRIBUTOS)).isEqualTo(18);
    }

    @Test
    void naoDeveExtrairPotencialNemFonteDoAtributo() {
        var extraidos = java.util.Arrays.stream(AtributoAvaliavel.values())
                .map(atributo -> atributo.extrair(ATRIBUTOS))
                .toList();

        assertThat(extraidos).containsExactlyInAnyOrder(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
    }
}
```

- [ ] **Step 2: Rode e confirme a falha**

```bash
./gradlew test --tests '*AtributoAvaliavelTest'
```

Esperado: erro de compilação — pacote `avaliacao.domain` não existe.

- [ ] **Step 3: Crie o `package-info.java` do módulo**

```java
@org.springframework.modulith.ApplicationModule(displayName = "Avaliação")
package br.com.api.footfirma.avaliacao;
```

- [ ] **Step 4: Crie o enum `AtributoAvaliavel`**

```java
package br.com.api.footfirma.avaliacao.domain;

import br.com.api.footfirma.jogador.dto.AtributosJogador;

import java.util.function.Function;

/**
 * As 18 skills que entram no cálculo de overall, cada uma sabendo se extrair do
 * record de atributos. A alternativa — um switch de 18 casos no calculador —
 * permitiria declarar a constante e esquecer de somá-la.
 */
public enum AtributoAvaliavel {

    RITMO(AtributosJogador::ritmo),
    FORCA(AtributosJogador::forca),
    FOLEGO(AtributosJogador::folego),
    SALTO(AtributosJogador::salto),
    AGILIDADE(AtributosJogador::agilidade),
    PASSE(AtributosJogador::passe),
    DRIBLE(AtributosJogador::drible),
    CRUZAMENTO(AtributosJogador::cruzamento),
    FRIEZA(AtributosJogador::frieza),
    FINALIZACAO(AtributosJogador::finalizacao),
    CABECEIO(AtributosJogador::cabeceio),
    FALTA(AtributosJogador::falta),
    PENALTI(AtributosJogador::penalti),
    DESARME(AtributosJogador::desarme),
    MARCACAO(AtributosJogador::marcacao),
    GOL_REFLEXO(AtributosJogador::golReflexo),
    GOL_POSICIONAMENTO(AtributosJogador::golPosicionamento),
    GOL_MANEJO(AtributosJogador::golManejo);

    private final Function<AtributosJogador, Integer> extrator;

    AtributoAvaliavel(Function<AtributosJogador, Integer> extrator) {
        this.extrator = extrator;
    }

    public Integer extrair(AtributosJogador atributos) {
        return extrator.apply(atributos);
    }
}
```

- [ ] **Step 5: Rode o teste do enum e confirme que passa**

```bash
./gradlew test --tests '*AtributoAvaliavelTest'
```

Esperado: PASS, 3 testes.

- [ ] **Step 6: Escreva o teste de schema**

Crie `src/test/java/br/com/api/footfirma/avaliacao/AvaliacaoSchemaTest.java`:

```java
package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import br.com.api.footfirma.avaliacao.repository.JogadorOverallRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AvaliacaoSchemaTest {

    @Autowired
    JogadorOverallRepository jogadorOverallRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveRejeitarDoisPerfisAtivosParaAMesmaPosicao() {
        var atacante = posicaoId("ATA");
        desativarPerfisExistentes(atacante);
        inserirPerfil(atacante, 7, true);

        assertThatThrownBy(() -> inserirPerfil(atacante, 8, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void devePermitirVersaoInativaAoLadoDaAtiva() {
        var ponta = posicaoId("PTA");
        desativarPerfisExistentes(ponta);
        inserirPerfil(ponta, 7, true);

        inserirPerfil(ponta, 8, false);

        var versoes = jdbcTemplate.queryForObject("""
                select count(*) from perfil_avaliacao where posicao_id = ? and versao in (7, 8)
                """, Integer.class, ponta);
        assertThat(versoes).isEqualTo(2);
    }

    @Test
    void deveRejeitarOverallDuplicadoParaMesmoJogadorTemporadaEPosicao() {
        var jogadorId = jogadorSalvo();
        var temporadaId = temporada("2031");
        var posicaoId = posicaoId("VOL");
        jogadorOverallRepository.saveAndFlush(overall(jogadorId, temporadaId, posicaoId, 70));

        assertThatThrownBy(() -> jogadorOverallRepository
                .saveAndFlush(overall(jogadorId, temporadaId, posicaoId, 80)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarOverallForaDaEscalaDeZeroANoventaENove() {
        var registro = overall(jogadorSalvo(), temporada("2032"), posicaoId("MEC"), 120);

        assertThatThrownBy(() -> jogadorOverallRepository.saveAndFlush(registro))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JogadorOverall overall(Long jogadorId, Long temporadaId, Long posicaoId, int valor) {
        var registro = new JogadorOverall(jogadorId, temporadaId, posicaoId);
        registro.setPerfilVersao(1);
        registro.setOverall(valor);
        registro.setCalculadoEm(OffsetDateTime.now());
        return registro;
    }

    private void inserirPerfil(Long posicaoId, int versao, boolean ativo) {
        jdbcTemplate.update("""
                insert into perfil_avaliacao (posicao_id, versao, ativo, vigente_desde)
                values (?, ?, ?, date '2026-08-02')
                """, posicaoId, versao, ativo);
    }

    // Na Task 2 não faz nada: a tabela está vazia. A partir da Task 3, o seed já
    // ocupa a versão 1 ativa de cada posição, e sem isto o primeiro insert do teste
    // colidiria com o índice parcial antes de chegar na asserção. @DataJpaTest
    // reverte a transação, então nenhum outro teste enxerga a desativação.
    private void desativarPerfisExistentes(Long posicaoId) {
        jdbcTemplate.update("update perfil_avaliacao set ativo = false where posicao_id = ?", posicaoId);
    }

    private Long posicaoId(String codigo) {
        return jdbcTemplate.queryForObject("select id from posicao where codigo = ?", Long.class, codigo);
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }

    private Long jogadorSalvo() {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = posicaoId("VOL");
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values ('teste-overall', 'teste|1998-03-10|BRA', 'Teste Overall', 'Teste Overall',
                        date '1998-03-10', ?, 'DIREITO', ?, 42, 'REAL')
                on conflict (slug) do nothing
                """, paisId, posicaoId);
        return jdbcTemplate.queryForObject(
                "select id from jogador where slug = 'teste-overall'", Long.class);
    }
}
```

- [ ] **Step 7: Rode e confirme a falha**

```bash
./gradlew test --tests '*AvaliacaoSchemaTest'
```

Esperado: erro de compilação — `JogadorOverall` e `JogadorOverallRepository` não existem.

- [ ] **Step 8: Crie a migration `V12__cria_avaliacao.sql`**

```sql
create table perfil_avaliacao (
    id            bigint  generated always as identity primary key,
    posicao_id    bigint  not null references posicao (id),
    versao        integer not null check (versao > 0),
    ativo         boolean not null default false,
    vigente_desde date    not null,
    constraint uq_perfil_avaliacao unique (posicao_id, versao)
);

-- Um único perfil ativo por posição, garantido pelo banco e não por convenção.
create unique index uq_perfil_avaliacao_ativo
    on perfil_avaliacao (posicao_id) where ativo;

comment on column perfil_avaliacao.vigente_desde is
    'Informativa: registra quando esta versão entrou em uso. A seleção do perfil é por ativo, nunca por data';

-- Rebalancear cria versão nova; nunca UPDATE. Sem isso não há como comparar o
-- equilíbrio antes e depois, nem impedir que uma carreira mude de regra no meio.
create table perfil_avaliacao_peso (
    perfil_id bigint       not null references perfil_avaliacao (id),
    atributo  text         not null check (atributo in ('RITMO','FORCA','FOLEGO','SALTO','AGILIDADE',
                                                        'PASSE','DRIBLE','CRUZAMENTO','FRIEZA',
                                                        'FINALIZACAO','CABECEIO','FALTA','PENALTI',
                                                        'DESARME','MARCACAO',
                                                        'GOL_REFLEXO','GOL_POSICIONAMENTO','GOL_MANEJO')),
    peso      numeric(5,4) not null check (peso >= 0 and peso <= 1),
    primary key (perfil_id, atributo)
);

-- Nove linhas por jogador e temporada, uma por posição — inclusive as que ele não
-- joga. É o que responde "esse volante serve como zagueiro?" sem cálculo extra.
create table jogador_overall (
    id            bigint      generated always as identity primary key,
    jogador_id    bigint      not null references jogador (id),
    temporada_id  bigint      not null references temporada (id),
    posicao_id    bigint      not null references posicao (id),
    perfil_versao integer     not null,
    overall       integer     not null check (overall between 0 and 99),
    calculado_em  timestamptz not null,
    constraint uq_jogador_overall unique (jogador_id, temporada_id, posicao_id)
);

-- jogador_id entra no índice porque o ORDER BY do ranking precisa dele como
-- desempate estável: só por overall, a paginação com empates não é determinística.
create index idx_jogador_overall_ranking
    on jogador_overall (temporada_id, posicao_id, overall desc, jogador_id);

comment on column jogador_overall.perfil_versao is
    'Versão do perfil usada neste cálculo. Gravada, nunca inferida: sem ela o versionamento de perfil perde a função';
```

- [ ] **Step 9: Crie a entidade `PerfilAvaliacao`**

```java
package br.com.api.footfirma.avaliacao.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Table(name = "perfil_avaliacao")
@Getter
@Setter
public class PerfilAvaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // posicao pertence ao módulo jogador: referência cruzada é coluna crua, não
    // associação JPA. A integridade fica na chave estrangeira do banco.
    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    @Column(nullable = false)
    private Integer versao;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    // Os pesos nunca são escritos pela aplicação: nascem do seed. Como mapa, são
    // exatamente a forma que a calculadora consome.
    @ElementCollection
    @CollectionTable(name = "perfil_avaliacao_peso", joinColumns = @JoinColumn(name = "perfil_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "atributo")
    @Column(name = "peso", nullable = false)
    private Map<AtributoAvaliavel, BigDecimal> pesos = new EnumMap<>(AtributoAvaliavel.class);

    protected PerfilAvaliacao() {
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof PerfilAvaliacao perfil)) {
            return false;
        }
        return id != null && id.equals(perfil.id);
    }

    @Override
    public int hashCode() {
        return PerfilAvaliacao.class.hashCode();
    }
}
```

- [ ] **Step 10: Crie a entidade `JogadorOverall`**

```java
package br.com.api.footfirma.avaliacao.domain;

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
@Table(name = "jogador_overall")
@Getter
@Setter
public class JogadorOverall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // As três referências apontam para fora do módulo avaliacao: coluna crua, sem
    // @ManyToOne, como Jogador.paisId e JogadorAtributo.temporadaId já fazem.
    @Column(name = "jogador_id", nullable = false)
    private Long jogadorId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    @Column(name = "perfil_versao", nullable = false)
    private Integer perfilVersao;

    @Column(nullable = false)
    private Integer overall;

    @Column(name = "calculado_em", nullable = false)
    private OffsetDateTime calculadoEm;

    protected JogadorOverall() {
    }

    public JogadorOverall(Long jogadorId, Long temporadaId, Long posicaoId) {
        this.jogadorId = jogadorId;
        this.temporadaId = temporadaId;
        this.posicaoId = posicaoId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorOverall registro)) {
            return false;
        }
        return id != null && id.equals(registro.id);
    }

    @Override
    public int hashCode() {
        return JogadorOverall.class.hashCode();
    }
}
```

- [ ] **Step 11: Crie os dois repositórios**

`repository/PerfilAvaliacaoRepository.java`:

```java
package br.com.api.footfirma.avaliacao.repository;

import br.com.api.footfirma.avaliacao.domain.PerfilAvaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PerfilAvaliacaoRepository extends JpaRepository<PerfilAvaliacao, Long> {

    // join fetch nos pesos: sem ele, materializar 1.200 jogadores dispararia uma
    // consulta de pesos por perfil por lote.
    @Query("select distinct p from PerfilAvaliacao p join fetch p.pesos where p.ativo = true")
    List<PerfilAvaliacao> buscarAtivosComPesos();
}
```

`repository/JogadorOverallRepository.java`:

```java
package br.com.api.footfirma.avaliacao.repository;

import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JogadorOverallRepository extends JpaRepository<JogadorOverall, Long> {

    List<JogadorOverall> findByTemporadaIdAndJogadorIdIn(Long temporadaId, Collection<Long> jogadorIds);
}
```

- [ ] **Step 12: Rode o teste de schema e confirme que passa**

```bash
./gradlew test --tests '*AvaliacaoSchemaTest'
```

Esperado: PASS, 4 testes. Se `ddl-auto=validate` reclamar de coluna, o mapeamento
divergiu da migration — corrija a entidade, **nunca** a migration já aplicada.

- [ ] **Step 13: Commit**

```bash
git add src/main/resources/db/migration/V12__cria_avaliacao.sql src/main/java/br/com/api/footfirma/avaliacao src/test/java/br/com/api/footfirma/avaliacao
git commit -m "feat(avaliacao): cria schema de perfis de peso e overall materializado"
```

---

## Task 3: Seed dos nove perfis e teste de integridade

O seed grava os 85 pesos não nulos explicitamente e completa os 77 restantes com zero,
fechando 18 linhas por perfil. Escrever as 162 à mão convidaria ao erro de digitação
que justamente o teste de integridade existe para pegar.

**Files:**
- Create: `src/main/resources/db/migration/V13__popula_perfil_avaliacao.sql`
- Test: `src/test/java/br/com/api/footfirma/avaliacao/PerfilAvaliacaoIntegridadeTest.java`

**Interfaces:**
- Consumes: `PerfilAvaliacaoRepository.buscarAtivosComPesos()` da Task 2.
- Produces: nove perfis `versao = 1`, `ativo = true`, um por posição, cada um com 18 linhas em `perfil_avaliacao_peso` somando exatamente `1.0000`.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/test/java/br/com/api/footfirma/avaliacao/PerfilAvaliacaoIntegridadeTest.java`:

```java
package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.avaliacao.repository.PerfilAvaliacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PerfilAvaliacaoIntegridadeTest {

    @Autowired
    PerfilAvaliacaoRepository perfilAvaliacaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveTerUmPerfilAtivoParaCadaUmaDasNovePosicoes() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        assertThat(ativos).hasSize(9);
        assertThat(ativos).extracting("posicaoId").doesNotHaveDuplicates();
    }

    // Este é o teste que pega peso quebrado no seed antes de ele virar overall
    // errado: a soma igual a 1.0 não é expressável como check de linha.
    @Test
    void deveSomarExatamenteUmEmTodoPerfilAtivo() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        for (var perfil : ativos) {
            var soma = perfil.getPesos().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(soma)
                    .as("soma dos pesos do perfil da posição %d", perfil.getPosicaoId())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(BigDecimal.ONE);
        }
    }

    @Test
    void deveDeclararOsDezoitoAtributosEmTodoPerfilAtivo() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        for (var perfil : ativos) {
            assertThat(perfil.getPesos().keySet())
                    .as("atributos do perfil da posição %d", perfil.getPosicaoId())
                    .containsExactlyInAnyOrder(AtributoAvaliavel.values());
        }
    }

    @Test
    void deveDarPesoZeroParaFaltaEPenaltiEmTodoPerfil() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        for (var perfil : ativos) {
            assertThat(perfil.getPesos().get(AtributoAvaliavel.FALTA))
                    .usingComparator(BigDecimal::compareTo).isEqualTo(BigDecimal.ZERO);
            assertThat(perfil.getPesos().get(AtributoAvaliavel.PENALTI))
                    .usingComparator(BigDecimal::compareTo).isEqualTo(BigDecimal.ZERO);
        }
    }

    @Test
    void deveDarAMaiorParteDoPesoDoGoleiroAsSkillsDeGoleiro() {
        var goleiro = jdbcTemplate.queryForObject("""
                select sum(peso) from perfil_avaliacao_peso w
                join perfil_avaliacao p on p.id = w.perfil_id
                join posicao pos on pos.id = p.posicao_id
                where pos.codigo = 'GOL' and p.ativo
                  and w.atributo in ('GOL_REFLEXO', 'GOL_POSICIONAMENTO', 'GOL_MANEJO')
                """, BigDecimal.class);

        assertThat(goleiro).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("0.8200"));
    }
}
```

- [ ] **Step 2: Rode e confirme a falha**

```bash
./gradlew test --tests '*PerfilAvaliacaoIntegridadeTest'
```

Esperado: FAIL — `buscarAtivosComPesos()` devolve lista vazia (`hasSize(9)` falha).

- [ ] **Step 3: Crie a migration `V13__popula_perfil_avaliacao.sql`**

```sql
-- Versão 1 dos perfis de peso, uma por posição. Rebalancear no futuro é inserir
-- versao = 2 e desativar esta, nunca dar UPDATE nos pesos: carreiras em curso não
-- podem mudar de regra no meio.
insert into perfil_avaliacao (posicao_id, versao, ativo, vigente_desde)
select id, 1, true, date '2026-08-02' from posicao;

-- Os 85 pesos não nulos. Falta e Pênalti não aparecem em nenhum perfil por decisão
-- de design: são habilidades de especialista, usadas pelo motor de simulação para
-- escolher quem cobra, não medida de qualidade do jogador.
insert into perfil_avaliacao_peso (perfil_id, atributo, peso)
select p.id, v.atributo, v.peso
from perfil_avaliacao p
join posicao pos on pos.id = p.posicao_id
join (values
    ('GOL', 'SALTO', 0.02), ('GOL', 'AGILIDADE', 0.08), ('GOL', 'PASSE', 0.03),
    ('GOL', 'FRIEZA', 0.05), ('GOL', 'GOL_REFLEXO', 0.30),
    ('GOL', 'GOL_POSICIONAMENTO', 0.28), ('GOL', 'GOL_MANEJO', 0.24),

    ('ZAG', 'RITMO', 0.06), ('ZAG', 'FORCA', 0.14), ('ZAG', 'FOLEGO', 0.01),
    ('ZAG', 'SALTO', 0.09), ('ZAG', 'AGILIDADE', 0.02), ('ZAG', 'PASSE', 0.07),
    ('ZAG', 'FRIEZA', 0.04), ('ZAG', 'CABECEIO', 0.13), ('ZAG', 'DESARME', 0.20),
    ('ZAG', 'MARCACAO', 0.24),

    -- LTD e LTE têm pesos idênticos de propósito: a diferença entre lateral direito
    -- e esquerdo é pé preferido, que vive em jogador.pe_preferido, não conjunto de
    -- habilidades. Não é copy-paste por descuido.
    ('LTD', 'RITMO', 0.14), ('LTD', 'FORCA', 0.04), ('LTD', 'FOLEGO', 0.13),
    ('LTD', 'AGILIDADE', 0.07), ('LTD', 'PASSE', 0.10), ('LTD', 'DRIBLE', 0.06),
    ('LTD', 'CRUZAMENTO', 0.15), ('LTD', 'FRIEZA', 0.02), ('LTD', 'DESARME', 0.13),
    ('LTD', 'MARCACAO', 0.16),

    ('LTE', 'RITMO', 0.14), ('LTE', 'FORCA', 0.04), ('LTE', 'FOLEGO', 0.13),
    ('LTE', 'AGILIDADE', 0.07), ('LTE', 'PASSE', 0.10), ('LTE', 'DRIBLE', 0.06),
    ('LTE', 'CRUZAMENTO', 0.15), ('LTE', 'FRIEZA', 0.02), ('LTE', 'DESARME', 0.13),
    ('LTE', 'MARCACAO', 0.16),

    ('VOL', 'RITMO', 0.03), ('VOL', 'FORCA', 0.11), ('VOL', 'FOLEGO', 0.12),
    ('VOL', 'AGILIDADE', 0.04), ('VOL', 'PASSE', 0.17), ('VOL', 'DRIBLE', 0.02),
    ('VOL', 'FRIEZA', 0.07), ('VOL', 'CABECEIO', 0.05), ('VOL', 'DESARME', 0.19),
    ('VOL', 'MARCACAO', 0.20),

    ('MEC', 'RITMO', 0.05), ('MEC', 'FORCA', 0.03), ('MEC', 'FOLEGO', 0.09),
    ('MEC', 'AGILIDADE', 0.11), ('MEC', 'PASSE', 0.26), ('MEC', 'DRIBLE', 0.15),
    ('MEC', 'CRUZAMENTO', 0.07), ('MEC', 'FRIEZA', 0.13), ('MEC', 'FINALIZACAO', 0.07),
    ('MEC', 'DESARME', 0.04),

    ('MEA', 'RITMO', 0.09), ('MEA', 'FORCA', 0.03), ('MEA', 'FOLEGO', 0.05),
    ('MEA', 'AGILIDADE', 0.12), ('MEA', 'PASSE', 0.19), ('MEA', 'DRIBLE', 0.18),
    ('MEA', 'CRUZAMENTO', 0.07), ('MEA', 'FRIEZA', 0.14), ('MEA', 'FINALIZACAO', 0.13),

    ('PTA', 'RITMO', 0.20), ('PTA', 'FORCA', 0.03), ('PTA', 'FOLEGO', 0.05),
    ('PTA', 'AGILIDADE', 0.14), ('PTA', 'PASSE', 0.08), ('PTA', 'DRIBLE', 0.20),
    ('PTA', 'CRUZAMENTO', 0.12), ('PTA', 'FRIEZA', 0.07), ('PTA', 'FINALIZACAO', 0.11),

    ('ATA', 'RITMO', 0.14), ('ATA', 'FORCA', 0.08), ('ATA', 'FOLEGO', 0.03),
    ('ATA', 'SALTO', 0.05), ('ATA', 'AGILIDADE', 0.07), ('ATA', 'PASSE', 0.07),
    ('ATA', 'DRIBLE', 0.12), ('ATA', 'FRIEZA', 0.10), ('ATA', 'FINALIZACAO', 0.24),
    ('ATA', 'CABECEIO', 0.10)
) as v(codigo, atributo, peso) on v.codigo = pos.codigo
where p.versao = 1;

-- Completa com zero os atributos restantes de cada perfil. Todo perfil fica com as
-- 18 linhas: sem NULL e sem caso especial no calculador. Skills de goleiro existem
-- em jogador de linha, e vice-versa — apenas com peso 0.
insert into perfil_avaliacao_peso (perfil_id, atributo, peso)
select p.id, a.atributo, 0
from perfil_avaliacao p
cross join (values
    ('RITMO'), ('FORCA'), ('FOLEGO'), ('SALTO'), ('AGILIDADE'),
    ('PASSE'), ('DRIBLE'), ('CRUZAMENTO'), ('FRIEZA'),
    ('FINALIZACAO'), ('CABECEIO'), ('FALTA'), ('PENALTI'),
    ('DESARME'), ('MARCACAO'),
    ('GOL_REFLEXO'), ('GOL_POSICIONAMENTO'), ('GOL_MANEJO')
) as a(atributo)
where p.versao = 1
  and not exists (
      select 1 from perfil_avaliacao_peso w
      where w.perfil_id = p.id and w.atributo = a.atributo
  );
```

- [ ] **Step 4: Rode o teste e confirme que passa**

```bash
./gradlew test --tests '*PerfilAvaliacaoIntegridadeTest'
```

Esperado: PASS, 5 testes. Se `deveSomarExatamenteUmEmTodoPerfilAtivo` falhar, a mensagem
traz o `posicaoId` do perfil quebrado — confira essa posição na tabela do spec.

- [ ] **Step 5: Confirme que o schema anterior continua válido**

```bash
./gradlew test --tests '*AvaliacaoSchemaTest'
```

Esperado: PASS. O seed agora ocupa a versão 1 de cada posição; por isso o teste de
schema usa versões 7 e 8, que continuam livres.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V13__popula_perfil_avaliacao.sql src/test/java/br/com/api/footfirma/avaliacao/PerfilAvaliacaoIntegridadeTest.java
git commit -m "feat(avaliacao): popula os nove perfis de peso da versão 1"
```

---

## Task 4: Calculadora de overall

Função pura: sem Spring, sem banco, sem repositório. É a peça mais testável do módulo e
a única que o motor de simulação vai reaproveitar sem carregar contexto.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/avaliacao/internal/CalculadoraDeOverall.java`
- Test: `src/test/java/br/com/api/footfirma/avaliacao/CalculadoraDeOverallTest.java`

**Interfaces:**
- Consumes: `AtributoAvaliavel` (Task 2), `AtributosJogador` de `jogador/dto`.
- Produces: `CalculadoraDeOverall.calcular(AtributosJogador, Map<AtributoAvaliavel, BigDecimal>)` → `int` entre 0 e 99. Classe e método são package-private: só `AvaliacaoServiceImpl` e `MaterializadorDeLote`, no mesmo pacote `internal`, a usam.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/test/java/br/com/api/footfirma/avaliacao/CalculadoraDeOverallTest.java`.
Note que o teste vive no pacote `...avaliacao.internal` para enxergar a classe
package-private:

```java
package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraDeOverallTest {

    @Test
    void deveSomarOsAtributosPonderadosPelosPesosDoPerfil() {
        // 80 × 0.5 + 60 × 0.5 = 70
        var atributos = atributosCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, 80,
                AtributoAvaliavel.RITMO, 60));
        var perfil = perfilCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, "0.5000",
                AtributoAvaliavel.RITMO, "0.5000"));

        var overall = CalculadoraDeOverall.calcular(atributos, perfil);

        assertThat(overall).isEqualTo(70);
    }

    // A consequência que o spec do catálogo destaca como desejada.
    @Test
    void deveDarNotasDiferentesAoMesmoJogadorEmPosicoesDiferentes() {
        var zagueiroDeOficio = atributosCom(Map.of(
                AtributoAvaliavel.MARCACAO, 88,
                AtributoAvaliavel.DESARME, 86,
                AtributoAvaliavel.CABECEIO, 84,
                AtributoAvaliavel.FORCA, 82,
                AtributoAvaliavel.CRUZAMENTO, 40,
                AtributoAvaliavel.RITMO, 62,
                AtributoAvaliavel.FOLEGO, 65));

        var comoZagueiro = CalculadoraDeOverall.calcular(zagueiroDeOficio, perfilDeZagueiro());
        var comoLateral = CalculadoraDeOverall.calcular(zagueiroDeOficio, perfilDeLateral());

        assertThat(comoZagueiro).isGreaterThan(comoLateral);
    }

    @Test
    void deveDevolverNoventaENoveQuandoTodosOsAtributosSaoMaximos() {
        var perfeito = atributosUniformes(99);

        var overall = CalculadoraDeOverall.calcular(perfeito, perfilDeZagueiro());

        assertThat(overall).isEqualTo(99);
    }

    @Test
    void deveDevolverZeroQuandoTodosOsAtributosSaoZero() {
        var nulo = atributosUniformes(0);

        var overall = CalculadoraDeOverall.calcular(nulo, perfilDeZagueiro());

        assertThat(overall).isZero();
    }

    @Test
    void deveArredondarMeioParaCima() {
        // 71 × 0.5 + 70 × 0.5 = 70.5 → 71
        var atributos = atributosCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, 71,
                AtributoAvaliavel.RITMO, 70));
        var perfil = perfilCom(Map.of(
                AtributoAvaliavel.FINALIZACAO, "0.5000",
                AtributoAvaliavel.RITMO, "0.5000"));

        var overall = CalculadoraDeOverall.calcular(atributos, perfil);

        assertThat(overall).isEqualTo(71);
    }

    @Test
    void deveFalharQuandoOPerfilNaoDeclaraUmDosAtributos() {
        var perfilIncompleto = new EnumMap<AtributoAvaliavel, BigDecimal>(AtributoAvaliavel.class);
        perfilIncompleto.put(AtributoAvaliavel.RITMO, BigDecimal.ONE);

        assertThatThrownBy(() -> CalculadoraDeOverall.calcular(atributosUniformes(70), perfilIncompleto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORCA");
    }

    // jogador_atributo declara as 18 colunas como not null: atributo nulo é erro de
    // dado, não caso a tratar. Falhar alto evita overall silenciosamente errado.
    @Test
    void deveFalharQuandoUmAtributoVemNulo() {
        var comNulo = new AtributosJogador(
                null, 70, 70, 70, 70, 70, 70, 70, 70,
                70, 70, 70, 70, 70, 70, 70, 70, 70, 80, "IMPORTADO");

        assertThatThrownBy(() -> CalculadoraDeOverall.calcular(comNulo, perfilDeZagueiro()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RITMO");
    }

    private static AtributosJogador atributosUniformes(int valor) {
        return new AtributosJogador(
                valor, valor, valor, valor, valor, valor, valor, valor, valor,
                valor, valor, valor, valor, valor, valor, valor, valor, valor,
                90, "IMPORTADO");
    }

    private static AtributosJogador atributosCom(Map<AtributoAvaliavel, Integer> valores) {
        var base = new EnumMap<AtributoAvaliavel, Integer>(AtributoAvaliavel.class);
        for (var atributo : AtributoAvaliavel.values()) {
            base.put(atributo, valores.getOrDefault(atributo, 0));
        }
        return new AtributosJogador(
                base.get(AtributoAvaliavel.RITMO), base.get(AtributoAvaliavel.FORCA),
                base.get(AtributoAvaliavel.FOLEGO), base.get(AtributoAvaliavel.SALTO),
                base.get(AtributoAvaliavel.AGILIDADE), base.get(AtributoAvaliavel.PASSE),
                base.get(AtributoAvaliavel.DRIBLE), base.get(AtributoAvaliavel.CRUZAMENTO),
                base.get(AtributoAvaliavel.FRIEZA), base.get(AtributoAvaliavel.FINALIZACAO),
                base.get(AtributoAvaliavel.CABECEIO), base.get(AtributoAvaliavel.FALTA),
                base.get(AtributoAvaliavel.PENALTI), base.get(AtributoAvaliavel.DESARME),
                base.get(AtributoAvaliavel.MARCACAO), base.get(AtributoAvaliavel.GOL_REFLEXO),
                base.get(AtributoAvaliavel.GOL_POSICIONAMENTO), base.get(AtributoAvaliavel.GOL_MANEJO),
                90, "IMPORTADO");
    }

    /** Completa com zero os atributos não informados, como o seed faz. */
    private static Map<AtributoAvaliavel, BigDecimal> perfilCom(Map<AtributoAvaliavel, String> pesos) {
        var perfil = new EnumMap<AtributoAvaliavel, BigDecimal>(AtributoAvaliavel.class);
        for (var atributo : AtributoAvaliavel.values()) {
            perfil.put(atributo, new BigDecimal(pesos.getOrDefault(atributo, "0.0000")));
        }
        return perfil;
    }

    private static Map<AtributoAvaliavel, BigDecimal> perfilDeZagueiro() {
        return perfilCom(Map.ofEntries(
                Map.entry(AtributoAvaliavel.RITMO, "0.0600"),
                Map.entry(AtributoAvaliavel.FORCA, "0.1400"),
                Map.entry(AtributoAvaliavel.FOLEGO, "0.0100"),
                Map.entry(AtributoAvaliavel.SALTO, "0.0900"),
                Map.entry(AtributoAvaliavel.AGILIDADE, "0.0200"),
                Map.entry(AtributoAvaliavel.PASSE, "0.0700"),
                Map.entry(AtributoAvaliavel.FRIEZA, "0.0400"),
                Map.entry(AtributoAvaliavel.CABECEIO, "0.1300"),
                Map.entry(AtributoAvaliavel.DESARME, "0.2000"),
                Map.entry(AtributoAvaliavel.MARCACAO, "0.2400")));
    }

    private static Map<AtributoAvaliavel, BigDecimal> perfilDeLateral() {
        return perfilCom(Map.ofEntries(
                Map.entry(AtributoAvaliavel.RITMO, "0.1400"),
                Map.entry(AtributoAvaliavel.FORCA, "0.0400"),
                Map.entry(AtributoAvaliavel.FOLEGO, "0.1300"),
                Map.entry(AtributoAvaliavel.AGILIDADE, "0.0700"),
                Map.entry(AtributoAvaliavel.PASSE, "0.1000"),
                Map.entry(AtributoAvaliavel.DRIBLE, "0.0600"),
                Map.entry(AtributoAvaliavel.CRUZAMENTO, "0.1500"),
                Map.entry(AtributoAvaliavel.FRIEZA, "0.0200"),
                Map.entry(AtributoAvaliavel.DESARME, "0.1300"),
                Map.entry(AtributoAvaliavel.MARCACAO, "0.1600")));
    }
}
```

- [ ] **Step 2: Rode e confirme a falha**

```bash
./gradlew test --tests '*CalculadoraDeOverallTest'
```

Esperado: erro de compilação — `CalculadoraDeOverall` não existe.

- [ ] **Step 3: Implemente a calculadora**

Crie `src/main/java/br/com/api/footfirma/avaliacao/internal/CalculadoraDeOverall.java`:

```java
package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.jogador.dto.AtributosJogador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * overall(jogador, posição) = Σ (atributo × peso). Função pura: sem Spring, sem banco.
 *
 * <p>A conta permanece em BigDecimal do começo ao fim. Em double, somar dezoito
 * parcelas faria o resultado depender da ordem das parcelas — pequeno o bastante para
 * passar despercebido e grande o bastante para mover uma borda de arredondamento.
 */
final class CalculadoraDeOverall {

    private CalculadoraDeOverall() {
    }

    static int calcular(AtributosJogador atributos, Map<AtributoAvaliavel, BigDecimal> pesos) {
        var soma = BigDecimal.ZERO;
        for (var atributo : AtributoAvaliavel.values()) {
            var peso = pesos.get(atributo);
            if (peso == null) {
                throw new IllegalArgumentException(
                        "Perfil de avaliação não declara peso para o atributo " + atributo);
            }
            var valor = atributo.extrair(atributos);
            if (valor == null) {
                throw new IllegalArgumentException("Atributo nulo no jogador avaliado: " + atributo);
            }
            soma = soma.add(BigDecimal.valueOf(valor).multiply(peso));
        }
        // Com atributos limitados a 99 e pesos somando 1.0, ultrapassar 99 é
        // matematicamente impossível: não existe clamp defensivo aqui de propósito.
        return soma.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
```

- [ ] **Step 4: Rode o teste e confirme que passa**

```bash
./gradlew test --tests '*CalculadoraDeOverallTest'
```

Esperado: PASS, 7 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/avaliacao/internal/CalculadoraDeOverall.java src/test/java/br/com/api/footfirma/avaliacao/internal
git commit -m "feat(avaliacao): calcula overall como função pura ponderada por perfil"
```

---

## Task 5: Leitura em lote no módulo `jogador`

Os três métodos que faltam para `avaliacao` ler o catálogo sem tocar em tabela alheia.
`listarResumosPorIds` existe especificamente para evitar o N+1 que resolver uma página
de vinte jogadores um a um produziria.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/JogadorComAtributos.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/internal/JogadorServiceImpl.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorAtributoRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorVinculoRepository.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorServiceLeituraEmLoteTest.java`

**Interfaces:**
- Consumes: `JogadorMapper.paraAtributos(JogadorAtributo)` (já existe), `TemporadaService.buscarPorLabel(String)`.
- Produces:
  - `JogadorComAtributos(Long jogadorId, AtributosJogador atributos)`
  - `JogadorService.listarAtributosPorTemporada(String labelTemporada, Pageable pageable)` → `Page<JogadorComAtributos>`; devolve página vazia se a temporada não existir.
  - `JogadorService.listarResumosPorIds(Collection<Long> ids, String labelTemporada)` → `List<JogadorResumo>`; **não garante ordem** — quem chama reordena.
  - `JogadorService.buscarIdPorSlug(String slug)` → `Optional<Long>`.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/test/java/br/com/api/footfirma/jogador/JogadorServiceLeituraEmLoteTest.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JogadorServiceLeituraEmLoteTest {

    @Autowired
    JogadorService jogadorService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararTresJogadoresComAtributos() {
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2040', 2040, 2040)
                on conflict (label) do nothing
                """);
        for (var indice = 1; indice <= 3; indice++) {
            inserirJogador("lote-" + indice, "Jogador Lote " + indice);
        }
    }

    @Test
    void deveDevolverAtributosPaginadosDaTemporada() {
        var primeiraPagina = jogadorService.listarAtributosPorTemporada(
                "2040", PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "jogador.id")));

        assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
        assertThat(primeiraPagina.getContent()).hasSize(2);
        assertThat(primeiraPagina.getContent().getFirst().atributos().finalizacao()).isEqualTo(70);
        assertThat(primeiraPagina.getContent().getFirst().jogadorId()).isNotNull();
    }

    @Test
    void deveDevolverPaginaVaziaQuandoTemporadaNaoExiste() {
        var pagina = jogadorService.listarAtributosPorTemporada("1900", PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    void deveResolverVariosJogadoresEmUmaChamadaSo() {
        var ids = jdbcTemplate.queryForList("select id from jogador order by id", Long.class);

        var resumos = jogadorService.listarResumosPorIds(ids, "2040");

        assertThat(resumos).hasSize(3);
        assertThat(resumos).extracting(JogadorResumo::slug)
                .containsExactlyInAnyOrder("lote-1", "lote-2", "lote-3");
        assertThat(resumos).extracting(JogadorResumo::posicao).containsOnly("ATA");
    }

    @Test
    void deveDevolverListaVaziaQuandoNenhumIdEInformado() {
        var resumos = jogadorService.listarResumosPorIds(List.of(), "2040");

        assertThat(resumos).isEmpty();
    }

    @Test
    void deveResolverOSlugParaOIdentificadorInterno() {
        var id = jogadorService.buscarIdPorSlug("lote-2");

        assertThat(id).isPresent();
        assertThat(jogadorService.buscarIdPorSlug("nao-existe")).isEmpty();
    }

    private void inserirJogador(String slug, String nome) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = jdbcTemplate.queryForObject(
                "select id from posicao where codigo = 'ATA'", Long.class);
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '1999-01-01', ?, 'DIREITO', ?, 1, 'REAL')
                """, slug, slug + "|1999-01-01|BRA", nome, nome, paisId, posicaoId);
        var jogadorId = jdbcTemplate.queryForObject(
                "select id from jogador where slug = ?", Long.class, slug);
        var temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2040'", Long.class);
        jdbcTemplate.update("""
                insert into jogador_atributo (
                    jogador_id, temporada_id,
                    ritmo, forca, folego, salto, agilidade,
                    passe, drible, cruzamento, frieza,
                    finalizacao, cabeceio, falta, penalti,
                    desarme, marcacao,
                    gol_reflexo, gol_posicionamento, gol_manejo,
                    potencial_base, potencial_variacao, fonte_atributo, coletado_em)
                values (?, ?, 75, 70, 80, 65, 78, 72, 76, 68, 74, 70, 60, 55, 70,
                        40, 38, 10, 10, 10, 85, 5, 'IMPORTADO', now())
                """, jogadorId, temporadaId);
    }
}
```

- [ ] **Step 2: Rode e confirme a falha**

```bash
./gradlew test --tests '*JogadorServiceLeituraEmLoteTest'
```

Esperado: erro de compilação — os três métodos não existem.

- [ ] **Step 3: Crie o record `JogadorComAtributos`**

```java
package br.com.api.footfirma.jogador.dto;

/**
 * Identifica o jogador junto de suas skills. Distinto de {@link AtributosJogador},
 * que traz apenas os valores. O id vem junto porque quem consome grava chave
 * estrangeira; em resposta REST o identificador continua sendo o slug.
 */
public record JogadorComAtributos(Long jogadorId, AtributosJogador atributos) {
}
```

- [ ] **Step 4: Acrescente as consultas aos três repositórios**

`JogadorAtributoRepository`:

```java
    Page<JogadorAtributo> findByTemporadaId(Long temporadaId, Pageable pageable);
```

Importe `org.springframework.data.domain.Page` e `org.springframework.data.domain.Pageable`.

`JogadorRepository`:

```java
    @Query("select j from Jogador j join fetch j.posicaoPrincipal where j.id in :ids")
    List<Jogador> buscarPorIds(@Param("ids") Collection<Long> ids);
```

Importe `java.util.Collection` e `java.util.List`.

`JogadorVinculoRepository`:

```java
    @Query("""
            select v from JogadorVinculo v
            where v.jogador.id in :ids and v.temporadaId = :temporadaId
            """)
    List<JogadorVinculo> buscarPorJogadoresETemporada(@Param("ids") Collection<Long> ids,
                                                      @Param("temporadaId") Long temporadaId);
```

Importe `java.util.Collection`.

- [ ] **Step 5: Declare os três métodos em `JogadorService`**

```java
    Page<JogadorComAtributos> listarAtributosPorTemporada(String labelTemporada, Pageable pageable);

    /** Não garante ordem: quem chama reordena conforme sua própria consulta. */
    List<JogadorResumo> listarResumosPorIds(Collection<Long> ids, String labelTemporada);

    Optional<Long> buscarIdPorSlug(String slug);
```

Importe `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`
e `java.util.Collection`.

- [ ] **Step 6: Implemente em `JogadorServiceImpl`**

```java
    @Override
    public Page<JogadorComAtributos> listarAtributosPorTemporada(String labelTemporada, Pageable pageable) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        if (temporadaId.isEmpty()) {
            return Page.empty(pageable);
        }
        // getJogador().getId() lê o id do proxy LAZY sem disparar select: não há N+1 aqui.
        return jogadorAtributoRepository.findByTemporadaId(temporadaId.get(), pageable)
                .map(atributo -> new JogadorComAtributos(
                        atributo.getJogador().getId(),
                        jogadorMapper.paraAtributos(atributo)));
    }

    @Override
    public List<JogadorResumo> listarResumosPorIds(Collection<Long> ids, String labelTemporada) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        // Duas consultas para a página inteira, nunca uma por jogador.
        var camisas = temporadaId
                .map(id -> jogadorVinculoRepository.buscarPorJogadoresETemporada(ids, id).stream()
                        .collect(Collectors.toMap(
                                vinculo -> vinculo.getJogador().getId(),
                                JogadorVinculo::getNumeroCamisa,
                                (primeiro, segundo) -> primeiro)))
                .orElseGet(Map::of);
        return jogadorRepository.buscarPorIds(ids).stream()
                .map(jogador -> new JogadorResumo(
                        jogador.getId(),
                        jogador.getSlug(),
                        jogador.getNomeExibicao(),
                        idadeEm(jogador.getDataNascimento()),
                        jogador.getPosicaoPrincipal().getCodigo(),
                        camisas.get(jogador.getId())))
                .toList();
    }

    @Override
    public Optional<Long> buscarIdPorSlug(String slug) {
        return jogadorRepository.findBySlug(slug).map(Jogador::getId);
    }
```

Adicione os imports: `br.com.api.footfirma.jogador.domain.JogadorVinculo`,
`br.com.api.footfirma.jogador.dto.JogadorComAtributos`,
`org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`,
`java.util.Collection`, `java.util.Map`, `java.util.stream.Collectors`.

- [ ] **Step 7: Rode o teste e confirme que passa**

```bash
./gradlew test --tests '*JogadorServiceLeituraEmLoteTest'
```

Esperado: PASS, 5 testes.

- [ ] **Step 8: Confirme que o módulo jogador não regrediu**

```bash
./gradlew test --tests '*Jogador*'
```

Esperado: PASS em todos.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/api/footfirma/jogador src/test/java/br/com/api/footfirma/jogador/JogadorServiceLeituraEmLoteTest.java
git commit -m "feat(jogador): abre leitura em lote de atributos, resumos e identificadores"
```

---

## Task 6: Materialização em lotes

**Files:**
- Create: `src/main/java/br/com/api/footfirma/avaliacao/AvaliacaoService.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/dto/ResultadoMaterializacao.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/dto/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/internal/MaterializadorDeLote.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/internal/AvaliacaoServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/avaliacao/MaterializacaoOverallTest.java`

**Interfaces:**
- Consumes: `JogadorService.listarAtributosPorTemporada` (Task 5), `PerfilAvaliacaoRepository.buscarAtivosComPesos` (Task 2), `CalculadoraDeOverall.calcular` (Task 4), `TemporadaService.buscarPorLabel`.
- Produces:
  - `ResultadoMaterializacao(String temporada, int jogadores, int linhas)`
  - `AvaliacaoService.materializar(String labelTemporada)` → `ResultadoMaterializacao`; grava nove linhas por jogador e é idempotente.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/test/java/br/com/api/footfirma/avaliacao/MaterializacaoOverallTest.java`:

```java
package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.AvaliacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MaterializacaoOverallTest {

    @Autowired
    AvaliacaoService avaliacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararDoisJogadores() {
        jdbcTemplate.update("delete from jogador_overall");
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2041', 2041, 2041)
                on conflict (label) do nothing
                """);
        inserirJogador("mat-1", "ATA", 88);
        inserirJogador("mat-2", "ZAG", 60);
    }

    @Test
    void deveGravarNoveLinhasDeOverallPorJogador() {
        var resultado = avaliacaoService.materializar("2041");

        assertThat(resultado.jogadores()).isEqualTo(2);
        assertThat(resultado.linhas()).isEqualTo(18);
        var gravadas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_overall", Integer.class);
        assertThat(gravadas).isEqualTo(18);
    }

    @Test
    void deveGravarAVersaoDoPerfilUsadaNoCalculo() {
        avaliacaoService.materializar("2041");

        var versoes = jdbcTemplate.queryForList(
                "select distinct perfil_versao from jogador_overall", Integer.class);

        assertThat(versoes).containsExactly(1);
    }

    // A garantia que o importador do Plano 3 depende: rodar duas vezes sobre o mesmo
    // estado não duplica nem muda nada.
    @Test
    void deveSerIdempotenteQuandoExecutadaDuasVezes() {
        avaliacaoService.materializar("2041");
        var primeiraLeitura = jdbcTemplate.queryForList(
                "select jogador_id, posicao_id, overall from jogador_overall order by jogador_id, posicao_id");

        avaliacaoService.materializar("2041");
        var segundaLeitura = jdbcTemplate.queryForList(
                "select jogador_id, posicao_id, overall from jogador_overall order by jogador_id, posicao_id");

        assertThat(segundaLeitura).hasSize(18);
        assertThat(segundaLeitura).isEqualTo(primeiraLeitura);
    }

    @Test
    void deveDarNotaMaiorAoAtacanteNaPosicaoDeAtacanteDoQueNaDeZagueiro() {
        avaliacaoService.materializar("2041");

        var comoAtacante = overallDe("mat-1", "ATA");
        var comoZagueiro = overallDe("mat-1", "ZAG");

        assertThat(comoAtacante).isGreaterThan(comoZagueiro);
    }

    @Test
    void naoDeveGravarNadaQuandoATemporadaNaoTemAtributos() {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2042', 2042, 2042)
                on conflict (label) do nothing
                """);

        var resultado = avaliacaoService.materializar("2042");

        assertThat(resultado.jogadores()).isZero();
        assertThat(resultado.linhas()).isZero();
    }

    private Integer overallDe(String slug, String codigoPosicao) {
        return jdbcTemplate.queryForObject("""
                select o.overall from jogador_overall o
                join jogador j on j.id = o.jogador_id
                join posicao p on p.id = o.posicao_id
                where j.slug = ? and p.codigo = ?
                """, Integer.class, slug, codigoPosicao);
    }

    private void inserirJogador(String slug, String codigoPosicao, int finalizacao) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = jdbcTemplate.queryForObject(
                "select id from posicao where codigo = ?", Long.class, codigoPosicao);
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '1999-01-01', ?, 'DIREITO', ?, 1, 'REAL')
                """, slug, slug + "|1999-01-01|BRA", slug, slug, paisId, posicaoId);
        var jogadorId = jdbcTemplate.queryForObject(
                "select id from jogador where slug = ?", Long.class, slug);
        var temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2041'", Long.class);
        jdbcTemplate.update("""
                insert into jogador_atributo (
                    jogador_id, temporada_id,
                    ritmo, forca, folego, salto, agilidade,
                    passe, drible, cruzamento, frieza,
                    finalizacao, cabeceio, falta, penalti,
                    desarme, marcacao,
                    gol_reflexo, gol_posicionamento, gol_manejo,
                    potencial_base, potencial_variacao, fonte_atributo, coletado_em)
                values (?, ?, 82, 70, 78, 66, 80, 68, 79, 60, 77, ?, 62, 50, 70,
                        35, 30, 10, 10, 10, 90, 5, 'IMPORTADO', now())
                """, jogadorId, temporadaId, finalizacao);
    }
}
```

- [ ] **Step 2: Rode e confirme a falha**

```bash
./gradlew test --tests '*MaterializacaoOverallTest'
```

Esperado: erro de compilação — `AvaliacaoService` não existe.

- [ ] **Step 3: Crie o `package-info.java` de `avaliacao/dto`**

```java
// Records devolvidos por AvaliacaoService. Sem @NamedInterface o Modulith trata o
// subpacote como interno; hoje ninguém consome de fora, mas o controller de web/ e
// o importador do Plano 3 vão.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.avaliacao.dto;
```

- [ ] **Step 4: Crie o record `ResultadoMaterializacao`**

```java
package br.com.api.footfirma.avaliacao.dto;

public record ResultadoMaterializacao(String temporada, int jogadores, int linhas) {
}
```

- [ ] **Step 5: Crie a interface `AvaliacaoService`**

```java
package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.avaliacao.dto.ResultadoMaterializacao;

public interface AvaliacaoService {

    /**
     * Recalcula e grava o overall de todos os jogadores com atributos na temporada,
     * em todas as nove posições. Idempotente: duas execuções sobre o mesmo estado
     * produzem resultado idêntico.
     *
     * <p>Não é exposta por REST — seria um POST no catálogo. Quem a chama é o
     * importador do Plano 3.
     */
    ResultadoMaterializacao materializar(String labelTemporada);
}
```

- [ ] **Step 6: Crie o `MaterializadorDeLote`**

```java
package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import br.com.api.footfirma.avaliacao.domain.PerfilAvaliacao;
import br.com.api.footfirma.avaliacao.repository.JogadorOverallRepository;
import br.com.api.footfirma.jogador.dto.JogadorComAtributos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Uma transação por lote, não uma de dez mil linhas. Bean separado porque
 * {@code @Transactional} só vale em chamada que passa pelo proxy do Spring.
 */
@Component
@RequiredArgsConstructor
class MaterializadorDeLote {

    private final JogadorOverallRepository jogadorOverallRepository;

    @Transactional
    int gravarLote(List<JogadorComAtributos> lote, Long temporadaId, List<PerfilAvaliacao> perfis) {
        var ids = lote.stream().map(JogadorComAtributos::jogadorId).toList();
        // Uma consulta para o lote inteiro: sem isso seriam nove selects por jogador.
        var existentes = jogadorOverallRepository
                .findByTemporadaIdAndJogadorIdIn(temporadaId, ids).stream()
                .collect(Collectors.toMap(
                        registro -> chave(registro.getJogadorId(), registro.getPosicaoId()),
                        Function.identity()));

        var gravadas = 0;
        for (var jogador : lote) {
            for (var perfil : perfis) {
                var overall = CalculadoraDeOverall.calcular(jogador.atributos(), perfil.getPesos());
                var registro = existentes.getOrDefault(
                        chave(jogador.jogadorId(), perfil.getPosicaoId()),
                        new JogadorOverall(jogador.jogadorId(), temporadaId, perfil.getPosicaoId()));
                registro.setPerfilVersao(perfil.getVersao());
                registro.setOverall(overall);
                registro.setCalculadoEm(OffsetDateTime.now());
                jogadorOverallRepository.save(registro);
                gravadas++;
            }
        }
        return gravadas;
    }

    private static Map.Entry<Long, Long> chave(Long jogadorId, Long posicaoId) {
        return Map.entry(jogadorId, posicaoId);
    }
}
```

- [ ] **Step 7: Crie o `AvaliacaoServiceImpl`**

```java
package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.dto.ResultadoMaterializacao;
import br.com.api.footfirma.avaliacao.repository.PerfilAvaliacaoRepository;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AvaliacaoServiceImpl implements AvaliacaoService {

    private static final int TAMANHO_DO_LOTE = 200;

    private final PerfilAvaliacaoRepository perfilAvaliacaoRepository;
    private final MaterializadorDeLote materializadorDeLote;
    private final JogadorService jogadorService;
    private final TemporadaService temporadaService;

    // NOT_SUPPORTED de propósito: este método não é uma unidade transacional, cada
    // lote é. Sem isso, a transação readOnly da classe englobaria a escrita e
    // seguraria uma conexão do início ao fim da carga.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoMaterializacao materializar(String labelTemporada) {
        var temporada = temporadaService.buscarPorLabel(labelTemporada);
        if (temporada.isEmpty()) {
            return new ResultadoMaterializacao(labelTemporada, 0, 0);
        }
        var temporadaId = temporada.map(TemporadaResumo::id).orElseThrow();
        var perfis = perfilAvaliacaoRepository.buscarAtivosComPesos();

        var jogadores = 0;
        var linhas = 0;
        var pagina = 0;
        boolean temProxima;
        do {
            // Ordenação total e estável: sem ela a paginação entre commits de lote
            // pode pular uma página, e o upsert não protege contra omissão.
            var lote = jogadorService.listarAtributosPorTemporada(labelTemporada,
                    PageRequest.of(pagina, TAMANHO_DO_LOTE, Sort.by(Sort.Direction.ASC, "jogador.id")));
            if (!lote.isEmpty()) {
                linhas += materializadorDeLote.gravarLote(lote.getContent(), temporadaId, perfis);
                jogadores += lote.getNumberOfElements();
            }
            temProxima = lote.hasNext();
            pagina++;
        } while (temProxima);

        return new ResultadoMaterializacao(labelTemporada, jogadores, linhas);
    }
}
```

- [ ] **Step 8: Rode o teste e confirme que passa**

```bash
./gradlew test --tests '*MaterializacaoOverallTest'
```

Esperado: PASS, 5 testes.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/api/footfirma/avaliacao src/test/java/br/com/api/footfirma/avaliacao/MaterializacaoOverallTest.java
git commit -m "feat(avaliacao): materializa overall por lotes de forma idempotente"
```

---

## Task 7: Consultas de overall e ranking

O `RankingPaginacaoTest` previsto no spec não vira classe separada: o cenário de
paginação com empates precisa exatamente da mesma preparação das outras consultas, e
`ConsultaDeOverallTest.deveManterAPaginacaoEstavelQuandoTodosEstaoEmpatados` o cobre.
Uma classe só para ele duplicaria o `@BeforeEach` inteiro.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/avaliacao/dto/AvaliacaoDePosicao.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/dto/AvaliacoesDoJogador.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/dto/ItemDeRanking.java`
- Modify: `src/main/java/br/com/api/footfirma/avaliacao/AvaliacaoService.java`
- Modify: `src/main/java/br/com/api/footfirma/avaliacao/repository/JogadorOverallRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/avaliacao/internal/AvaliacaoServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/avaliacao/ConsultaDeOverallTest.java`

**Interfaces:**
- Consumes: `JogadorService.buscarIdPorSlug`, `JogadorService.listarResumosPorIds`, `JogadorService.listarPosicoes` (Tasks 1 e 5).
- Produces:
  - `AvaliacaoDePosicao(String posicao, Integer overall, Integer perfilVersao)`
  - `AvaliacoesDoJogador(String slug, String temporada, List<AvaliacaoDePosicao> avaliacoes)`
  - `ItemDeRanking(String slug, String nomeExibicao, Integer idade, String posicaoPrincipal, String posicaoAvaliada, Integer overall)`
  - `AvaliacaoService.buscarPorSlug(String, String)` → `Optional<AvaliacoesDoJogador>`, avaliações em ordem decrescente de overall.
  - `AvaliacaoService.ranquear(String, String, Integer, Pageable)` → `Page<ItemDeRanking>`, ordenada por `overall desc, jogadorId asc`.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/test/java/br/com/api/footfirma/avaliacao/ConsultaDeOverallTest.java`:

```java
package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.dto.AvaliacaoDePosicao;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConsultaDeOverallTest {

    @Autowired
    AvaliacaoService avaliacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararSeisJogadoresEmpatados() {
        jdbcTemplate.update("delete from jogador_overall");
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2043', 2043, 2043)
                on conflict (label) do nothing
                """);
        // Todos com os mesmos atributos: o ranking sai inteiramente empatado, que é
        // exatamente a condição em que a paginação instável se manifesta.
        for (var indice = 1; indice <= 6; indice++) {
            inserirJogador("rank-" + indice);
        }
        avaliacaoService.materializar("2043");
    }

    @Test
    void deveDevolverOverallNasNovePosicoesOrdenadoDoMaiorParaOMenor() {
        var avaliacoes = avaliacaoService.buscarPorSlug("rank-1", "2043");

        assertThat(avaliacoes).isPresent();
        assertThat(avaliacoes.get().avaliacoes()).hasSize(9);
        assertThat(avaliacoes.get().avaliacoes())
                .extracting(AvaliacaoDePosicao::overall)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void deveDevolverVazioQuandoOJogadorNaoExiste() {
        assertThat(avaliacaoService.buscarPorSlug("nao-existe", "2043")).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoATemporadaNaoExiste() {
        assertThat(avaliacaoService.buscarPorSlug("rank-1", "1900")).isEmpty();
    }

    @Test
    void deveRanquearJogadoresNaPosicaoPedida() {
        var pagina = avaliacaoService.ranquear("2043", "ATA", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(6);
        assertThat(pagina.getContent()).extracting(ItemDeRanking::posicaoAvaliada).containsOnly("ATA");
        assertThat(pagina.getContent()).extracting(ItemDeRanking::nomeExibicao).doesNotContainNull();
    }

    // Com seis jogadores empatados, paginar sem desempate estável faria o mesmo
    // jogador aparecer duas vezes ou sumir.
    @Test
    void deveManterAPaginacaoEstavelQuandoTodosEstaoEmpatados() {
        var primeira = avaliacaoService.ranquear("2043", "ATA", null, PageRequest.of(0, 3));
        var segunda = avaliacaoService.ranquear("2043", "ATA", null, PageRequest.of(1, 3));

        var vistos = new ArrayList<String>();
        primeira.getContent().forEach(item -> vistos.add(item.slug()));
        segunda.getContent().forEach(item -> vistos.add(item.slug()));

        assertThat(vistos).hasSize(6);
        assertThat(vistos).doesNotHaveDuplicates();
    }

    @Test
    void deveFiltrarPeloOverallMinimo() {
        var comFiltroImpossivel = avaliacaoService.ranquear("2043", "ATA", 99, PageRequest.of(0, 10));

        assertThat(comFiltroImpossivel.getTotalElements()).isZero();
    }

    @Test
    void deveDevolverPaginaVaziaQuandoAPosicaoNaoExiste() {
        var pagina = avaliacaoService.ranquear("2043", "XXX", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isZero();
    }

    private void inserirJogador(String slug) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = jdbcTemplate.queryForObject(
                "select id from posicao where codigo = 'ATA'", Long.class);
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '1999-01-01', ?, 'DIREITO', ?, 1, 'REAL')
                """, slug, slug + "|1999-01-01|BRA", slug, slug, paisId, posicaoId);
        var jogadorId = jdbcTemplate.queryForObject(
                "select id from jogador where slug = ?", Long.class, slug);
        var temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2043'", Long.class);
        jdbcTemplate.update("""
                insert into jogador_atributo (
                    jogador_id, temporada_id,
                    ritmo, forca, folego, salto, agilidade,
                    passe, drible, cruzamento, frieza,
                    finalizacao, cabeceio, falta, penalti,
                    desarme, marcacao,
                    gol_reflexo, gol_posicionamento, gol_manejo,
                    potencial_base, potencial_variacao, fonte_atributo, coletado_em)
                values (?, ?, 80, 70, 78, 66, 80, 68, 79, 60, 77, 84, 62, 50, 70,
                        35, 30, 10, 10, 10, 90, 5, 'IMPORTADO', now())
                """, jogadorId, temporadaId);
    }
}
```

- [ ] **Step 2: Rode e confirme a falha**

```bash
./gradlew test --tests '*ConsultaDeOverallTest'
```

Esperado: erro de compilação — `buscarPorSlug` e `ranquear` não existem em `AvaliacaoService`.

- [ ] **Step 3: Crie os três records**

`dto/AvaliacaoDePosicao.java`:

```java
package br.com.api.footfirma.avaliacao.dto;

public record AvaliacaoDePosicao(String posicao, Integer overall, Integer perfilVersao) {
}
```

`dto/AvaliacoesDoJogador.java`:

```java
package br.com.api.footfirma.avaliacao.dto;

import java.util.List;

/** Plural porque devolve as nove posições, não um número só. */
public record AvaliacoesDoJogador(String slug, String temporada, List<AvaliacaoDePosicao> avaliacoes) {
}
```

`dto/ItemDeRanking.java`:

```java
package br.com.api.footfirma.avaliacao.dto;

/**
 * {@code posicaoPrincipal} é onde o jogador atua; {@code posicaoAvaliada} é a
 * posição do ranking. Um volante ranqueado como zagueiro traz as duas diferentes —
 * carregar ambas evita que a divergência pareça erro.
 */
public record ItemDeRanking(String slug, String nomeExibicao, Integer idade,
                            String posicaoPrincipal, String posicaoAvaliada, Integer overall) {
}
```

- [ ] **Step 4: Acrescente as consultas ao `JogadorOverallRepository`**

```java
    List<JogadorOverall> findByJogadorIdAndTemporadaIdOrderByOverallDesc(Long jogadorId, Long temporadaId);

    // O desempate por jogadorId é o que torna a paginação determinística com empates.
    @Query("""
            select o from JogadorOverall o
            where o.temporadaId = :temporadaId
              and o.posicaoId = :posicaoId
              and o.overall >= :overallMinimo
            order by o.overall desc, o.jogadorId asc
            """)
    Page<JogadorOverall> ranquear(@Param("temporadaId") Long temporadaId,
                                  @Param("posicaoId") Long posicaoId,
                                  @Param("overallMinimo") Integer overallMinimo,
                                  Pageable pageable);
```

Importe `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`,
`org.springframework.data.jpa.repository.Query` e `org.springframework.data.repository.query.Param`.

- [ ] **Step 5: Declare os dois métodos em `AvaliacaoService`**

```java
    Optional<AvaliacoesDoJogador> buscarPorSlug(String slug, String labelTemporada);

    /** {@code overallMinimo} nulo significa sem piso. */
    Page<ItemDeRanking> ranquear(String labelTemporada, String codigoPosicao,
                                 Integer overallMinimo, Pageable pageable);
```

Importe os dois DTOs, `org.springframework.data.domain.Page`,
`org.springframework.data.domain.Pageable` e `java.util.Optional`.

- [ ] **Step 6: Implemente em `AvaliacaoServiceImpl`**

```java
    @Override
    public Optional<AvaliacoesDoJogador> buscarPorSlug(String slug, String labelTemporada) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        var jogadorId = jogadorService.buscarIdPorSlug(slug);
        if (temporadaId.isEmpty() || jogadorId.isEmpty()) {
            return Optional.empty();
        }
        var registros = jogadorOverallRepository
                .findByJogadorIdAndTemporadaIdOrderByOverallDesc(jogadorId.get(), temporadaId.get());
        if (registros.isEmpty()) {
            return Optional.empty();
        }
        var codigoPorId = codigoPorIdDePosicao();
        var avaliacoes = registros.stream()
                .map(registro -> new AvaliacaoDePosicao(
                        codigoPorId.get(registro.getPosicaoId()),
                        registro.getOverall(),
                        registro.getPerfilVersao()))
                .toList();
        return Optional.of(new AvaliacoesDoJogador(slug, labelTemporada, avaliacoes));
    }

    @Override
    public Page<ItemDeRanking> ranquear(String labelTemporada, String codigoPosicao,
                                        Integer overallMinimo, Pageable pageable) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        var posicao = jogadorService.listarPosicoes().stream()
                .filter(candidata -> candidata.codigo().equals(codigoPosicao))
                .findFirst();
        if (temporadaId.isEmpty() || posicao.isEmpty()) {
            return Page.empty(pageable);
        }
        var pagina = jogadorOverallRepository.ranquear(
                temporadaId.get(), posicao.get().id(),
                overallMinimo == null ? 0 : overallMinimo, pageable);

        var ids = pagina.getContent().stream().map(JogadorOverall::getJogadorId).toList();
        // Uma chamada para a página inteira; listarResumosPorIds não garante ordem,
        // então a ordem do ranking é reimposta abaixo.
        var resumoPorId = jogadorService.listarResumosPorIds(ids, labelTemporada).stream()
                .collect(Collectors.toMap(JogadorResumo::id, Function.identity()));

        return pagina.map(registro -> {
            var resumo = resumoPorId.get(registro.getJogadorId());
            return new ItemDeRanking(
                    resumo == null ? null : resumo.slug(),
                    resumo == null ? null : resumo.nomeExibicao(),
                    resumo == null ? null : resumo.idade(),
                    resumo == null ? null : resumo.posicao(),
                    codigoPosicao,
                    registro.getOverall());
        });
    }

    private Map<Long, String> codigoPorIdDePosicao() {
        return jogadorService.listarPosicoes().stream()
                .collect(Collectors.toMap(PosicaoCatalogo::id, PosicaoCatalogo::codigo));
    }
```

Injete `JogadorOverallRepository` como campo `final` novo e adicione os imports:
`br.com.api.footfirma.avaliacao.domain.JogadorOverall`, os três DTOs novos,
`br.com.api.footfirma.avaliacao.repository.JogadorOverallRepository`,
`br.com.api.footfirma.jogador.dto.JogadorResumo`,
`br.com.api.footfirma.jogador.dto.PosicaoCatalogo`,
`org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`,
`java.util.Map`, `java.util.Optional`, `java.util.function.Function`,
`java.util.stream.Collectors`.

- [ ] **Step 7: Rode o teste e confirme que passa**

```bash
./gradlew test --tests '*ConsultaDeOverallTest'
```

Esperado: PASS, 7 testes.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/br/com/api/footfirma/avaliacao src/test/java/br/com/api/footfirma/avaliacao/ConsultaDeOverallTest.java
git commit -m "feat(avaliacao): consulta overall por jogador e ranking paginado"
```

---

## Task 8: API REST

`/api/v1/rankings` é recurso próprio, e não `/api/v1/jogadores/ranking`: o segundo
colide com `/api/v1/jogadores/{slug}` e só funciona porque o Spring prioriza o literal
sobre a variável de template — até existir um jogador com slug `ranking`.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/avaliacao/web/AvaliacaoController.java`
- Modify: `src/main/java/br/com/api/footfirma/config/SecurityConfig.java:22`
- Test: `src/test/java/br/com/api/footfirma/avaliacao/web/AvaliacaoControllerTest.java`

**Interfaces:**
- Consumes: `AvaliacaoService` (Tasks 6 e 7), `RecursoNaoEncontradoException` de `shared/exception`.
- Produces: `GET /api/v1/jogadores/{slug}/overall?temporada=` e `GET /api/v1/rankings?temporada=&posicao=&overallMinimo=`.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/test/java/br/com/api/footfirma/avaliacao/web/AvaliacaoControllerTest.java`:

```java
package br.com.api.footfirma.avaliacao.web;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.dto.AvaliacaoDePosicao;
import br.com.api.footfirma.avaliacao.dto.AvaliacoesDoJogador;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import br.com.api.footfirma.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig real: sem ele o slice responde 401 e o teste provaria a segurança
// em vez do contrato. Afrouxar a segurança no teste mascararia o problema.
@WebMvcTest(AvaliacaoController.class)
@Import(SecurityConfig.class)
class AvaliacaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AvaliacaoService avaliacaoService;

    @Test
    void deveRetornarOverallDoJogadorNasPosicoes() throws Exception {
        when(avaliacaoService.buscarPorSlug("raphael-veiga", "2025"))
                .thenReturn(Optional.of(new AvaliacoesDoJogador("raphael-veiga", "2025", List.of(
                        new AvaliacaoDePosicao("MEA", 84, 1),
                        new AvaliacaoDePosicao("MEC", 81, 1)))));

        mockMvc.perform(get("/api/v1/jogadores/raphael-veiga/overall?temporada=2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("raphael-veiga"))
                .andExpect(jsonPath("$.avaliacoes[0].posicao").value("MEA"))
                .andExpect(jsonPath("$.avaliacoes[0].overall").value(84))
                .andExpect(jsonPath("$.avaliacoes[0].perfilVersao").value(1));
    }

    @Test
    void deveRetornar404QuandoOJogadorNaoTemOverallNaTemporada() throws Exception {
        when(avaliacaoService.buscarPorSlug("inexistente", "2025")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jogadores/inexistente/overall?temporada=2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void deveRetornarRankingPaginado() throws Exception {
        var item = new ItemDeRanking("gabriel-barbosa", "Gabigol", 29, "ATA", "ATA", 84);
        when(avaliacaoService.ranquear(eq("2025"), eq("ATA"), eq(80), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/rankings?temporada=2025&posicao=ATA&overallMinimo=80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("gabriel-barbosa"))
                .andExpect(jsonPath("$.content[0].posicaoAvaliada").value("ATA"))
                .andExpect(jsonPath("$.content[0].overall").value(84));
    }

    @Test
    void deveAceitarRankingSemOverallMinimo() throws Exception {
        when(avaliacaoService.ranquear(eq("2025"), eq("ZAG"), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/rankings?temporada=2025&posicao=ZAG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
```

- [ ] **Step 2: Rode e confirme a falha**

```bash
./gradlew test --tests '*AvaliacaoControllerTest'
```

Esperado: erro de compilação — `AvaliacaoController` não existe.

- [ ] **Step 3: Crie o controller**

```java
package br.com.api.footfirma.avaliacao.web;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.dto.AvaliacoesDoJogador;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Avaliações", description = "Overall calculado por posição")
class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    // Sub-recurso de jogador servido por este módulo, e não pelo JogadorController:
    // embutir overall no catálogo inverteria a dependência entre os módulos.
    @GetMapping("/jogadores/{slug}/overall")
    @Operation(summary = "Overall de um jogador nas nove posições, na temporada informada")
    AvaliacoesDoJogador buscar(@PathVariable String slug, @RequestParam String temporada) {
        return avaliacaoService.buscarPorSlug(slug, temporada)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Sem overall para o jogador " + slug + " na temporada " + temporada));
    }

    // Recurso próprio: /jogadores/ranking colidiria com /jogadores/{slug}.
    @GetMapping("/rankings")
    @Operation(summary = "Ranking de jogadores por overall em uma posição")
    Page<ItemDeRanking> ranquear(@RequestParam String temporada,
                                 @RequestParam String posicao,
                                 @RequestParam(required = false) Integer overallMinimo,
                                 Pageable pageable) {
        return avaliacaoService.ranquear(temporada, posicao, overallMinimo, pageable);
    }
}
```

- [ ] **Step 4: Libere a rota nova em `SecurityConfig`**

Depois da linha `.requestMatchers(HttpMethod.GET, "/api/v1/competicoes/**").permitAll()`,
acrescente:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/rankings").permitAll()
```

`GET /api/v1/jogadores/**` já é `permitAll` e cobre o sub-recurso `/overall` — não
acrescente nada para ele.

- [ ] **Step 5: Rode o teste e confirme que passa**

```bash
./gradlew test --tests '*AvaliacaoControllerTest'
```

Esperado: PASS, 4 testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/avaliacao/web src/main/java/br/com/api/footfirma/config/SecurityConfig.java src/test/java/br/com/api/footfirma/avaliacao/web
git commit -m "feat(avaliacao): expõe overall e ranking em /api/v1"
```

---

## Task 9: Fechamento — build completo, ADR e checklist

**Files:**
- Create: `backend/footfirma/docs/adr/2026-08-02-overall-fora-do-catalogo.md`

**Interfaces:**
- Consumes: tudo das Tasks 1 a 8.
- Produces: build verde e a decisão de fronteira registrada com lastro verificável.

- [ ] **Step 1: Rode o build inteiro**

```bash
./gradlew clean build
```

Esperado: BUILD SUCCESSFUL. Exige Docker rodando.

- [ ] **Step 2: Confirme que o catálogo continua read-only**

```bash
grep -rn "PostMapping\|PutMapping\|PatchMapping\|DeleteMapping" \
    src/main/java/br/com/api/footfirma/*/web/
```

Esperado: nenhuma saída.

- [ ] **Step 3: Confirme que nenhuma associação cruza módulo**

```bash
grep -rn "ManyToOne\|OneToOne" src/main/java/br/com/api/footfirma/avaliacao/
```

Esperado: nenhuma saída. As três referências de `jogador_overall` são `Long` cru.

- [ ] **Step 4: Escreva o ADR**

Crie `backend/footfirma/docs/adr/2026-08-02-overall-fora-do-catalogo.md`:

```markdown
# Overall é servido por avaliacao, não pelo catálogo

Status: verificado em 2026-08-02

## Fontes

- `../../../../docs/superpowers/specs/2026-08-02-avaliacao-overall-design.md`
- `src/main/java/br/com/api/footfirma/avaliacao/web/AvaliacaoController.java`
- `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- `src/main/java/br/com/api/footfirma/avaliacao/internal/CalculadoraDeOverall.java`
- `src/main/resources/db/migration/V13__popula_perfil_avaliacao.sql`
- `src/test/java/br/com/api/footfirma/ModularidadeTest.java`

## Decisão

O overall é calculado e servido pelo módulo `avaliacao`, que depende de `jogador` e
`temporada`. O `JogadorController` não devolve overall, e `avaliacao` lê o catálogo
apenas pela interface `JogadorService` — nunca por consulta a tabela alheia.

## Por quê

Embutir overall em `JogadorDetalhe` inverteria a dependência: rebalancear pesos
passaria a alterar o contrato do catálogo. A regra de leitura é a simétrica da que o
spec do catálogo já impõe ao importador ("não escreve em tabela alheia"); sem ela, um
join contra `jogador` tornaria a fronteira decorativa.

## Consequências

- A tela de jogador faz duas chamadas: `/api/v1/jogadores/{slug}` e
  `/api/v1/jogadores/{slug}/overall`.
- `JogadorService` ganhou quatro métodos de leitura em lote. É o custo da fronteira.
- Referências a outros módulos são coluna `Long`, não `@ManyToOne`: a integridade
  fica na chave estrangeira do banco, seguindo `Jogador.paisId`.
- Rebalancear pesos cria `versao = 2`; o `UPDATE` em perfil ativo está fora de questão.
- O ranking é global por temporada. Filtrar por competição atravessaria `competicao` e
  `jogador`, e ficou fora deste ciclo.

## Como validar

```bash
./gradlew test --tests '*ModularidadeTest'
./gradlew test --tests '*PerfilAvaliacaoIntegridadeTest'

# Nenhuma associação JPA cruzando fronteira de módulo
grep -rn "ManyToOne\|OneToOne" src/main/java/br/com/api/footfirma/avaliacao/

# Catálogo segue sem endpoint de escrita
grep -rn "PostMapping\|PutMapping\|PatchMapping\|DeleteMapping" \
    src/main/java/br/com/api/footfirma/*/web/
```
```

- [ ] **Step 5: Percorra o checklist do repositório**

Abra `.rules/java-checklist.md` e confirme cada item para os arquivos criados.

- [ ] **Step 6: Commit**

```bash
git add docs/adr/2026-08-02-overall-fora-do-catalogo.md
git commit -m "docs(avaliacao): registra ADR da fronteira entre overall e catálogo"
```

---

## Ordem e dependências

```
Task 1 (jogador/dto aberto)  ──┬─→ Task 2 (schema + entidades) ──→ Task 3 (seed)
                               │              │
                               │              └─→ Task 4 (calculadora)
                               │                        │
                               └─→ Task 5 (leitura em lote) ──→ Task 6 (materialização)
                                                                      │
                                                                      └─→ Task 7 (consultas)
                                                                                │
                                                                                └─→ Task 8 (API)
                                                                                          │
                                                                                          └─→ Task 9 (fechamento)
```

A Task 1 é pré-requisito de compilação de tudo. As Tasks 3 e 4 podem ser feitas em
qualquer ordem entre si, desde que depois da Task 2.

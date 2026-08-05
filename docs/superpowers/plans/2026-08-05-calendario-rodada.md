# Calendário e rodada — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o módulo Spring Modulith `calendario` — rodadas, confrontos e jogos de uma edição, com data e mando atribuídos por um gerador determinístico que respeita descanso mínimo por clube, mais o registro de resultado, a resolução do confronto e a propagação do chaveamento.

**Architecture:** Núcleo de funções puras em `internal/` (sem Spring, sem JPA, sem relógio) que recebem records e `SplittableRandom` e devolvem records — Berger, tipagem de rodadas, sorteio de dia, alocação de datas e resolução de confronto. Uma porta pública `CalendarioService` que é o único caminho de escrita. `competicao` ganha dois ajustes para alimentar o gerador.

**Tech Stack:** Java 25 · Spring Boot 4 · Spring Modulith · PostgreSQL via Flyway · JPA · Lombok · MapStruct · JUnit 5 + AssertJ + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-05-calendario-rodada-design.md`

## Global Constraints

- Pacote base `br.com.api.footfirma`; um módulo por pacote direto abaixo dele.
- Tipos no pacote raiz do módulo são API pública; subpacotes são internos **salvo** se o `package-info.java` do subpacote declarar `@org.springframework.modulith.NamedInterface("dto")`.
- Referência a outro módulo é coluna `Long` crua, **nunca** `@ManyToOne`. Associação JPA só entre `Rodada`, `Confronto` e `Jogo`.
- Injeção **somente por construtor**, via `@RequiredArgsConstructor`. `@Autowired` em campo é proibido.
- `@Transactional(readOnly = true)` na classe do service, `@Transactional` no método que escreve.
- Lombok em `@Entity`: apenas `@Getter`/`@Setter`. `equals`/`hashCode` seguem `competicao/domain/Fase.java` — compara `id`, `hashCode` devolve `Classe.class.hashCode()`.
- Migrations aplicadas são **imutáveis**; o guard bloqueia edição. A próxima livre é `V22` — a última aplicada é `V21`.
- Enum de domínio em banco é `text` + `check (col in (...))`, mapeado com `@Enumerated(EnumType.STRING)`. Nunca tipo `enum` nativo do Postgres, nunca `EnumType.ORDINAL`.
- Toda FK usada em join ou filtro tem índice. O Postgres não o cria sozinho.
- Testes de persistência usam Testcontainers. H2, banco em memória e mock de `Repository` são proibidos.
- `@DataJpaTest` exige `@Import(TestcontainersConfiguration.class)` **e** `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`.
- Nomes de teste em português: `deve<Comportamento>Quando<Condição>`. Asserções com AssertJ.
- Commits em pt-BR, formato `<tipo>(calendario): <descrição imperativa>`, máximo 72 caracteres na primeira linha, **nunca** `Co-authored-by`.
- Rode `./gradlew compileJava` durante o trabalho e `./gradlew test --tests '*<Alvo>Test'` para o teste da tarefa. **Não** rode a suíte completa a cada passo — só nas Tasks 9 e 13.
- Não suba serviços (`bootRun`, `docker compose up`) — o guard bloqueia. Testcontainers sobe sozinho no `test`.
- Trabalhe a partir de `backend/footfirma/`. Todos os caminhos são relativos a esse diretório, exceto os de `docs/`.
- Branch atual: `feat/mundo-escala-clubes`. Não faça `git push` — o guard bloqueia.

## Fatiamento

O plano tem **dois slices**, e o corte é o do risco 2 do spec.

| Slice | Tasks | Entrega |
|---|---|---|
| **A — pontos corridos** | 1 a 9 | Schema `V22` inteiro, motores puros, geração das duas ligas do mundo, registro de resultado, API e integração com `mundo`. Ao fim da Task 9 o mundo gerado tem 760 jogos datados. |
| **B — grupos e eliminatória** | 10 a 13 | Sorteio de chaveamento, geração de grupos e mata-mata, propagação e realocação. |

O schema da `V22` nasce completo no slice A — `confronto.origem_lado_a`, `vencedor_clube_id` e as colunas de prorrogação e pênaltis existem desde o primeiro dia, sem uso. É a decisão do spec: fatiar a entrega, não o desenho.

**O teste de integridade da eliminatória vive na Task 12**, dentro do slice B. Ele é a única coisa que prova que a metade complexa funciona; deixá-lo no slice A o faria testar código que ainda não existe.

## Decisões que o spec delegou a este plano

**A auto-FK de `confronto` na limpeza — nem cascade, nem delete por fase.** `LimpezaDoCatalogo` quebra a auto-referência com um `update` antes do `forEach` (Task 9, Step 3). `on delete cascade` numa auto-referência faz apagar um confronto levar junto os que dependem dele — correto neste caso e perigoso como hábito; apagar por fase em ordem decrescente obrigaria a classe a abandonar a lista linear que a torna legível. O `update` custa uma linha e não toca o schema.

**A precedência de geração vira `gerarTemporada`, não um argumento solto.** Passar `precedencia` para `gerarParaEdicao` documenta a intenção sem verificar nada: quem chamar fora de ordem continua conseguindo. Em vez disso, a porta pública recebe a lista de edições com suas precedências e **ordena internamente** (Task 6). A ordem deixa de depender da sequência das linhas em `mundo` e passa a ser dado que o módulo controla — sem persistir nada.

**`FaseResumo` ganha um campo, não um DTO novo.** Ele já traz `tipo`, `jogosPorConfronto`, `temProrrogacao` e `temPenaltis`; falta `temGolFora`. O método novo em `CompeticaoService` existe só pelos participantes por fase (Task 2).

## File Structure

```
src/main/java/br/com/api/footfirma/
├── competicao/
│   ├── dto/FaseResumo.java                 # MODIFICAR: + temGolFora
│   ├── dto/ParticipanteDaFase.java         # criar
│   └── CompeticaoService.java              # MODIFICAR: + listarParticipantesDaFase
├── shared/exception/
│   ├── CalendarioInvalidoException.java    # criar
│   ├── ResultadoInvalidoException.java     # criar
│   └── TratadorDeErros.java                # MODIFICAR: + dois handlers 422
├── mundo/internal/
│   ├── MundoServiceImpl.java               # MODIFICAR: + gerarTemporada
│   └── LimpezaDoCatalogo.java              # MODIFICAR: + 3 tabelas e o update
└── calendario/
    ├── package-info.java                   # @ApplicationModule(displayName = "Calendário")
    ├── CalendarioService.java              # porta pública única
    ├── dto/
    │   ├── package-info.java               # @NamedInterface("dto")
    │   ├── TipoDeRodada · SituacaoDoJogo
    │   ├── PerfilDeCalendario · EdicaoParaGerar · ResultadoDoJogo      # entrada
    │   └── JogoAgendado · RodadaDetalhe · ConfrontoDetalhe
    │       RelatorioDeCalendario · RegrasDeDesempate                   # saída
    ├── domain/     Rodada · Confronto · Jogo
    ├── repository/ RodadaRepository · ConfrontoRepository · JogoRepository
    ├── mapper/     CalendarioMapper
    ├── web/        CalendarioController
    └── internal/   ConstantesDeCalendario · TabelaDeBerger · TipadorDeRodadas
                    SorteadorDeDia · AlocadorDeDatas · SorteioDeChaveamento
                    ResolvedorDeConfronto · CalendarioServiceImpl

src/main/resources/db/migration/V22__cria_calendario.sql

src/test/java/br/com/api/footfirma/calendario/
├── CalendarioSchemaTest.java · GeracaoDePontosCorridosTest.java
├── RegistroDeResultadoTest.java · ChaveamentoTest.java
├── EliminatoriaIntegridadeTest.java · CalendarioDeterminismoTest.java
└── internal/ TabelaDeBergerTest.java · TipagemDeRodadasTest.java
           SorteioDeDiaTest.java · AlocacaoDeDatasTest.java
           ResolucaoDeConfrontoTest.java
```

**Por que os enums vivem em `dto/`:** são o contrato — a partida vai ler `SituacaoDoJogo`. Em `domain/`, publicá-los exigiria `@NamedInterface` ali, o que exporia as três entidades JPA junto. Mesma armadilha que `tatica` contornou.

---

# Slice A — pontos corridos

### Task 1: Schema, entidades e repositórios

**Files:**
- Create: `src/main/resources/db/migration/V22__cria_calendario.sql`
- Create: `src/main/java/br/com/api/footfirma/calendario/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/calendario/dto/package-info.java`
- Create: `calendario/dto/{TipoDeRodada,SituacaoDoJogo}.java`
- Create: `calendario/domain/{Rodada,Confronto,Jogo}.java`
- Create: `calendario/repository/{RodadaRepository,ConfrontoRepository,JogoRepository}.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/CalendarioSchemaTest.java`

**Interfaces:**
- Consumes: nada (primeira tarefa)
- Produces: `Rodada` (getters `getId`, `getFaseId`, `getOrdem`, `getTipo`, `getDataAlvo`, `getJanelaInicio`, `getJanelaFim`), `Confronto` (`getId`, `getFaseId`, `getOrdem`, `getChave`, `getOrigemLadoA`, `getOrigemLadoB`, `getClubeAId`, `getClubeBId`, `getVencedorClubeId`), `Jogo` (`getId`, `getConfronto`, `getRodada`, `getOrdemNoConfronto`, `getMandanteId`, `getVisitanteId`, `getEstadioId`, `getDataJogo`, `getSituacao`, e os seis getters de placar), `RodadaRepository.findByFaseIdOrderByOrdemAsc(Long)`, `ConfrontoRepository.findByFaseIdOrderByOrdemAsc(Long)`, `ConfrontoRepository.findByOrigemLadoAOrOrigemLadoB(Long, Long)`, `JogoRepository.findByConfrontoIdOrderByOrdemNoConfrontoAsc(Long)`, `JogoRepository.findDatasDoClube(Long clubeId, List<Long> faseIds)`; e o helper de teste `CalendarioFactory`

- [ ] **Step 1: Escrever a migration**

Crie `V22__cria_calendario.sql` copiando **integralmente** os três blocos `create table` da seção "Schema — migration `V22`" do spec, com todos os `comment on`, todos os `check` e todos os índices. Confira quatro coisas que a revisão do spec fixou:

- `rodada.tipo` tem `check (tipo in ('FIM_DE_SEMANA', 'MEIO_DE_SEMANA'))`
- `jogo.situacao` tem `check (situacao in ('AGENDADO', 'ENCERRADO'))`
- existem os dois índices parciais de origem:

```sql
create index idx_confronto_origem_a on confronto (origem_lado_a) where origem_lado_a is not null;
create index idx_confronto_origem_b on confronto (origem_lado_b) where origem_lado_b is not null;
```

- `ck_jogo_clubes_distintos` existe **com** o `comment on constraint` que explica por que o par nulo passa

- [ ] **Step 2: Criar os package-info**

`calendario/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Calendário")
package br.com.api.footfirma.calendario;
```

`calendario/dto/package-info.java`:

```java
// Os records e enums deste pacote são o contrato do módulo: CalendarioService os devolve
// e o módulo partida vai consumi-los. Sem @NamedInterface o Modulith trata o subpacote
// como interno.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.calendario.dto;
```

- [ ] **Step 3: Criar os dois enums**

```java
package br.com.api.footfirma.calendario.dto;

/** Decide o leque de dias em que os jogos da rodada podem cair. */
public enum TipoDeRodada {
    FIM_DE_SEMANA, MEIO_DE_SEMANA
}
```

```java
package br.com.api.footfirma.calendario.dto;

/**
 * Não existe ADIADO: jogo fora da janela da rodada é apenas um jogo em outro dia, e
 * ninguém o adia por decisão externa neste sistema.
 */
public enum SituacaoDoJogo {
    AGENDADO, ENCERRADO
}
```

- [ ] **Step 4: Escrever o teste de schema**

```java
package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CalendarioSchemaTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCriarAsTresTabelasVazias() {
        assertThat(contar("rodada")).isZero();
        assertThat(contar("confronto")).isZero();
        assertThat(contar("jogo")).isZero();
    }

    @Test
    void deveRecusarTipoDeRodadaForaDoCheck() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into rodada (fase_id, ordem, tipo, data_alvo, janela_inicio, janela_fim)
                values (1, 1, 'SEXTA_FEIRA', '2026-04-05', '2026-04-03', '2026-04-07')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveTerOsDoisIndicesParciaisDeOrigem() {
        var indices = jdbcTemplate.queryForList("""
                select indexname from pg_indexes
                where tablename = 'confronto' and indexname like 'idx_confronto_origem%'
                """, String.class);
        assertThat(indices).containsExactlyInAnyOrder(
                "idx_confronto_origem_a", "idx_confronto_origem_b");
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
```

Importe `org.springframework.dao.DataIntegrityViolationException` e `static org.assertj.core.api.Assertions.assertThatThrownBy`.

- [ ] **Step 5: Rodar o teste e ver falhar**

Run: `./gradlew test --tests '*CalendarioSchemaTest'`
Expected: FAIL — a migration ainda não foi escrita ou as tabelas não existem.

- [ ] **Step 6: Criar as três entidades**

`Rodada` e `Confronto` referenciam `fase` por `Long` cru; `Jogo` tem `@ManyToOne` para as duas entidades do próprio módulo.

```java
package br.com.api.footfirma.calendario.domain;

import br.com.api.footfirma.calendario.dto.TipoDeRodada;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "rodada")
@Getter
@Setter
public class Rodada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fase_id", nullable = false)
    private Long faseId;

    @Column(nullable = false)
    private Integer ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDeRodada tipo;

    @Column(name = "data_alvo", nullable = false)
    private LocalDate dataAlvo;

    @Column(name = "janela_inicio", nullable = false)
    private LocalDate janelaInicio;

    @Column(name = "janela_fim", nullable = false)
    private LocalDate janelaFim;

    protected Rodada() {
    }

    public Rodada(Long faseId, Integer ordem, TipoDeRodada tipo,
                  LocalDate dataAlvo, LocalDate janelaInicio, LocalDate janelaFim) {
        this.faseId = faseId;
        this.ordem = ordem;
        this.tipo = tipo;
        this.dataAlvo = dataAlvo;
        this.janelaInicio = janelaInicio;
        this.janelaFim = janelaFim;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Rodada rodada)) {
            return false;
        }
        return id != null && id.equals(rodada.id);
    }

    @Override
    public int hashCode() {
        return Rodada.class.hashCode();
    }
}
```

`Confronto` segue o mesmo formato, com os campos `faseId`, `ordem`, `chave`, `origemLadoA`, `origemLadoB`, `clubeAId`, `clubeBId`, `vencedorClubeId` — todos `Long`/`String`/`Integer` simples, sem `@ManyToOne`, inclusive a auto-referência: `origem_lado_a` é coluna `Long`, porque associação para a própria entidade não paga o custo de lazy loading numa árvore de sete confrontos.

`Jogo` tem `@ManyToOne(fetch = FetchType.LAZY, optional = false)` para `Confronto` e `Rodada`, e colunas `Long` para `mandanteId`, `visitanteId`, `estadioId`.

- [ ] **Step 7: Criar os três repositórios**

```java
package br.com.api.footfirma.calendario.repository;

import br.com.api.footfirma.calendario.domain.Jogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByConfrontoIdOrderByOrdemNoConfrontoAsc(Long confrontoId);

    /**
     * As datas já ocupadas por um clube na temporada, em qualquer competição. É o que o
     * alocador consulta para verificar descanso — e o que listarAgendaDoClube expõe.
     */
    @Query("""
            select j.dataJogo from Jogo j
            where (j.mandanteId = :clubeId or j.visitanteId = :clubeId)
              and j.dataJogo is not null
              and j.rodada.faseId in :faseIds
            """)
    List<LocalDate> findDatasDoClube(@Param("clubeId") Long clubeId,
                                     @Param("faseIds") List<Long> faseIds);
}
```

`RodadaRepository.findByFaseIdOrderByOrdemAsc(Long)` e `ConfrontoRepository.findByFaseIdOrderByOrdemAsc(Long)` mais `ConfrontoRepository.findByOrigemLadoAOrOrigemLadoB(Long a, Long b)` são derivados de nome, sem `@Query`.

- [ ] **Step 8: Rodar o teste e ver passar**

Run: `./gradlew test --tests '*CalendarioSchemaTest'`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/V22__cria_calendario.sql \
        src/main/java/br/com/api/footfirma/calendario \
        src/test/java/br/com/api/footfirma/calendario
git commit -m "feat(calendario): cria o schema de rodada, confronto e jogo"
```

---

### Task 2: Ajustes em `competicao` e o helper de semeadura

**Files:**
- Modify: `src/main/java/br/com/api/footfirma/competicao/dto/FaseResumo.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/ParticipanteDaFase.java`
- Modify: `src/main/java/br/com/api/footfirma/competicao/CompeticaoService.java`
- Modify: `src/main/java/br/com/api/footfirma/competicao/internal/CompeticaoServiceImpl.java`
- Create: `src/test/java/br/com/api/footfirma/calendario/CalendarioFactory.java`
- Test: `src/test/java/br/com/api/footfirma/competicao/ParticipantesDaFaseTest.java`

**Interfaces:**
- Consumes: nada da Task 1
- Produces: `FaseResumo(Long id, Integer ordem, String nome, String tipo, Integer jogosPorConfronto, Boolean temGolFora, Boolean temProrrogacao, Boolean temPenaltis)`; `ParticipanteDaFase(Long clubeId, String slug, Long estadioId)`; `JanelaDaEdicao(LocalDate inicio, LocalDate fim)`; `CompeticaoService.listarFasesDaEdicao(long edicaoId)`; `CompeticaoService.listarParticipantesDaFase(long faseId)`; `CompeticaoService.buscarJanelaDaEdicao(long edicaoId)`; `CalendarioFactory.criar(String slug, int participantes, String tipo, int jogosPorConfronto)` devolvendo `CalendarioFactory.Cenario(long temporadaId, long edicaoId, long faseId, List<Long> clubeIds)`

**O helper de semeadura.** Todas as tasks seguintes precisam de uma edição com fase e participantes no banco. Sem ele, cada teste reinventa a montagem — é o papel que `PlanoFactory` já cumpre em `tatica`. Ele nasce aqui, e não na Task 1, porque depende de `listarFasesDaEdicao`. O código está abaixo; ele é escrito no **Step 7**.

`src/test/java/br/com/api/footfirma/calendario/CalendarioFactory.java`:

```java
package br.com.api.footfirma.calendario;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.*;
import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Semeia edição, fase e participantes para os testes de calendário.
 *
 * <p>Passa pelas portas públicas dos módulos donos, e não por insert direto: o que o
 * gerador lê precisa existir do jeito que o gerador o encontra em produção.
 */
@Component
@RequiredArgsConstructor
public class CalendarioFactory {

    private final TemporadaService temporadaService;
    private final CompeticaoService competicaoService;
    private final ClubeService clubeService;
    private final GeografiaService geografiaService;

    public record Cenario(long temporadaId, long edicaoId, long faseId, List<Long> clubeIds) {
    }

    /**
     * @param tipo             PONTOS_CORRIDOS, GRUPOS ou ELIMINATORIA
     * @param jogosPorConfronto 1 ou 2 — em pontos corridos, 2 significa turno e returno
     */
    public Cenario criar(String slug, int participantes, String tipo, int jogosPorConfronto) {
        var temporadaId = temporadaService
                .sincronizar(new DadosDeTemporada("2026", 2026, 2026)).id();
        var paisId = geografiaService.buscarPaisPorIso("BRA").orElseThrow().id();
        var estadoId = geografiaService.buscarEstadoPorUf("BRA", "SP").orElseThrow().id();

        var competicaoId = competicaoService.sincronizarCompeticao(new DadosDeCompeticao(
                slug, "Competição " + slug, paisId, "LIGA", 1, "MASCULINO")).id();
        var edicaoId = competicaoService.sincronizarEdicao(new DadosDeEdicao(
                competicaoId, temporadaId, "Edição " + slug,
                LocalDate.of(2026, 4, 4), LocalDate.of(2026, 12, 6))).id();
        competicaoService.sincronizarFase(new DadosDeFase(
                edicaoId, 1, "Fase única", tipo, jogosPorConfronto, false, false, false));

        var clubeIds = new ArrayList<Long>(participantes);
        for (var i = 1; i <= participantes; i++) {
            var estadioId = clubeService.sincronizarEstadio(new DadosDeEstadio(
                    "Estádio %s %d".formatted(slug, i), "São Paulo", estadoId, 30_000, 1950)).id();
            var clubeId = clubeService.sincronizarClube(new DadosDeClube(
                    "%s-clube-%d".formatted(slug, i), "Clube %d".formatted(i),
                    "C%d".formatted(i), "Apelido %d".formatted(i), 1950, paisId, estadoId,
                    estadioId, "#000000", "#FFFFFF",
                    BigDecimal.valueOf(50), BigDecimal.valueOf(50), BigDecimal.valueOf(50),
                    estadoId)).id();
            clubeIds.add(clubeId);
            competicaoService.sincronizarParticipante(
                    new DadosDeParticipante(edicaoId, clubeId, null));
        }

        var faseId = competicaoService.listarFasesDaEdicao(edicaoId).getFirst().id();
        return new Cenario(temporadaId, edicaoId, faseId, List.copyOf(clubeIds));
    }
}
```

Confira as assinaturas de `DadosDeClube` e `DadosDeEstadio` contra `MundoServiceImpl.criarClubes` antes de escrever — se elas divergirem, siga o código, não este plano.

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.competicao;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ParticipantesDaFaseTest {

    @Autowired
    CompeticaoService competicaoService;

    @Autowired
    TemporadaService temporadaService;

    @Autowired
    GeografiaService geografiaService;

    @Test
    void deveTrazerIdETemGolForaNoResumoDaFase() {
        var fases = competicaoService.listarFasesDaEdicao(edicaoSemeada());
        assertThat(fases).hasSize(1);
        assertThat(fases.getFirst().id()).isNotNull();
        assertThat(fases.getFirst().temGolFora()).isFalse();
        assertThat(fases.getFirst().jogosPorConfronto()).isEqualTo(2);
    }

    @Test
    void deveDevolverListaVaziaQuandoAEdicaoNaoExiste() {
        assertThat(competicaoService.listarFasesDaEdicao(-1L)).isEmpty();
    }

    @Test
    void deveDevolverListaVaziaQuandoAFaseNaoExiste() {
        assertThat(competicaoService.listarParticipantesDaFase(-1L)).isEmpty();
    }

    /** Semeadura mínima: só o que estes três testes exigem, sem clube nenhum. */
    private long edicaoSemeada() {
        var temporadaId = temporadaService
                .sincronizar(new DadosDeTemporada("2026", 2026, 2026)).id();
        var paisId = geografiaService.buscarPaisPorIso("BRA").orElseThrow().id();
        var competicaoId = competicaoService.sincronizarCompeticao(new DadosDeCompeticao(
                "fases-teste", "Fases Teste", paisId, "LIGA", 1, "MASCULINO")).id();
        var edicaoId = competicaoService.sincronizarEdicao(new DadosDeEdicao(
                competicaoId, temporadaId, "Fases Teste 2026",
                LocalDate.of(2026, 4, 4), LocalDate.of(2026, 12, 6))).id();
        competicaoService.sincronizarFase(new DadosDeFase(
                edicaoId, 1, "Turno e returno", "PONTOS_CORRIDOS", 2, false, false, false));
        return edicaoId;
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*ParticipantesDaFaseTest'`
Expected: FAIL — `listarFasesDaEdicao` e `listarParticipantesDaFase` não existem; não compila.

- [ ] **Step 3: Acrescentar `temGolFora` ao `FaseResumo`**

```java
package br.com.api.footfirma.competicao.dto;

public record FaseResumo(
        Long id,
        Integer ordem,
        String nome,
        String tipo,
        Integer jogosPorConfronto,
        Boolean temGolFora,
        Boolean temProrrogacao,
        Boolean temPenaltis
) {
}
```

O `id` também entra: `calendario` precisa dele para gravar `rodada.fase_id`, e `EdicaoDetalhe` nunca o expôs. `CompeticaoMapper.paraResumo(Fase)` mapeia os dois por nome, sem `@Mapping` novo — os campos da entidade se chamam `temGolFora` e `id`.

- [ ] **Step 4: Criar `ParticipanteDaFase`**

```java
package br.com.api.footfirma.competicao.dto;

/**
 * Um clube inscrito, com o que o gerador de calendário precisa para montar um jogo:
 * quem é e onde manda.
 */
public record ParticipanteDaFase(Long clubeId, String slug, Long estadioId) {
}
```

- [ ] **Step 5: Acrescentar os dois métodos à porta pública**

```java
    /** As fases de uma edição, em ordem, com id e regras de desempate. */
    List<FaseResumo> listarFasesDaEdicao(long edicaoId);

    /**
     * Os clubes inscritos na edição a que a fase pertence, em ordem de id.
     *
     * <p>Participante é da edição, não da fase — a fase herda todos. Devolve vazio
     * quando a fase não existe, em vez de lançar: quem gera calendário trata fase sem
     * participante como erro de calendário, não como recurso ausente.
     */
    List<ParticipanteDaFase> listarParticipantesDaFase(long faseId);

    /**
     * A janela em que a edição acontece. {@code EdicaoDetalhe} já traz as duas datas, mas
     * só é alcançável por slug e temporada — e quem gera calendário tem o id.
     */
    Optional<JanelaDaEdicao> buscarJanelaDaEdicao(long edicaoId);
```

```java
package br.com.api.footfirma.competicao.dto;

import java.time.LocalDate;

/** O intervalo em que uma edição se disputa. É o que o calendário tem para distribuir. */
public record JanelaDaEdicao(LocalDate inicio, LocalDate fim) {
}
```

- [ ] **Step 6: Implementar em `CompeticaoServiceImpl`**

```java
    @Override
    public List<FaseResumo> listarFasesDaEdicao(long edicaoId) {
        return edicaoRepository.findById(edicaoId)
                .map(edicao -> competicaoMapper.paraFases(edicao.getFases()))
                .orElseGet(List::of);
    }

    @Override
    public List<ParticipanteDaFase> listarParticipantesDaFase(long faseId) {
        return faseRepository.findById(faseId)
                .map(fase -> edicaoParticipanteRepository
                        .findByEdicaoIdOrderByClubeIdAsc(fase.getEdicao().getId()))
                .orElseGet(List::of)
                .stream()
                .map(participante -> new ParticipanteDaFase(
                        participante.getClubeId(),
                        clubeService.buscarSlugPorId(participante.getClubeId()).orElseThrow(),
                        clubeService.buscarEstadioPorClubeId(participante.getClubeId()).orElseThrow()))
                .toList();
    }
```

Se `ClubeService` ainda não expõe `buscarSlugPorId` e `buscarEstadioPorClubeId`, acrescente-os seguindo o formato de `buscarIdPorSlug` que `CompeticaoService` já tem — assinatura `Optional<String> buscarSlugPorId(long clubeId)` e `Optional<Long> buscarEstadioPorClubeId(long clubeId)`.

Acrescente `findByEdicaoIdOrderByClubeIdAsc(Long edicaoId)` a `EdicaoParticipanteRepository`. A ordenação por `clubeId` não é estética: é o que torna o sorteio de Berger reprodutível.

- [ ] **Step 7: Escrever o `CalendarioFactory`**

Copie o código do bloco no topo desta task. Ele é `@Component` no source set de teste, injetado por `@Autowired` nas classes das Tasks 6 em diante.

- [ ] **Step 8: Rodar e ver passar**

Run: `./gradlew test --tests '*ParticipantesDaFaseTest' --tests '*CompeticaoTest'`
Expected: PASS. Os testes existentes de `competicao` que constroem `FaseResumo` posicionalmente precisam ganhar os dois campos novos — corrija-os, não os enfraqueça.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/api/footfirma/competicao src/main/java/br/com/api/footfirma/clube \
        src/test/java/br/com/api/footfirma/competicao
git commit -m "feat(competicao): expõe fases com desempate e participantes da fase"
```

---

### Task 3: `TabelaDeBerger` — o round-robin

**Files:**
- Create: `calendario/internal/{ConstantesDeCalendario,TabelaDeBerger,ConfrontoDaRodada}.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/internal/TabelaDeBergerTest.java`

**Interfaces:**
- Consumes: nada
- Produces: `ConfrontoDaRodada(int mandante, int visitante)` (índices na lista de participantes, não ids); `TabelaDeBerger.gerar(int participantes, boolean returno)` devolvendo `List<List<ConfrontoDaRodada>>` — uma lista por rodada

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.calendario.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabelaDeBergerTest {

    @ParameterizedTest(name = "{0} participantes geram {0}-1 rodadas de turno")
    @ValueSource(ints = {4, 8, 20})
    void deveGerarUmaRodadaAMenosQueParticipantes(int participantes) {
        assertThat(TabelaDeBerger.gerar(participantes, false)).hasSize(participantes - 1);
    }

    @Test
    void deveDobrarAsRodadasComReturno() {
        assertThat(TabelaDeBerger.gerar(20, true)).hasSize(38);
    }

    @Test
    void deveFazerCadaUmEnfrentarTodosOsOutrosUmaVezNoTurno() {
        var pares = new HashSet<String>();
        for (var rodada : TabelaDeBerger.gerar(20, false)) {
            for (var jogo : rodada) {
                var chave = Math.min(jogo.mandante(), jogo.visitante())
                        + "-" + Math.max(jogo.mandante(), jogo.visitante());
                assertThat(pares.add(chave))
                        .as("par %s repetido no turno", chave).isTrue();
            }
        }
        assertThat(pares).hasSize(20 * 19 / 2);
    }

    @Test
    void naoDeveEscalarNinguemDuasVezesNaMesmaRodada() {
        for (var rodada : TabelaDeBerger.gerar(20, false)) {
            var vistos = new HashSet<Integer>();
            for (var jogo : rodada) {
                assertThat(vistos.add(jogo.mandante())).isTrue();
                assertThat(vistos.add(jogo.visitante())).isTrue();
            }
            assertThat(rodada).hasSize(10);
        }
    }

    @Test
    void deveInverterOMandoNoReturno() {
        var completo = TabelaDeBerger.gerar(20, true);
        var turno = completo.subList(0, 19);
        var returno = completo.subList(19, 38);

        for (var i = 0; i < 19; i++) {
            for (var j = 0; j < 10; j++) {
                var ida = turno.get(i).get(j);
                var volta = returno.get(i).get(j);
                assertThat(volta.mandante()).isEqualTo(ida.visitante());
                assertThat(volta.visitante()).isEqualTo(ida.mandante());
            }
        }
    }

    @Test
    void deveDistribuirOMandoQuaseIgualmenteNoTurno() {
        var mandos = new int[20];
        TabelaDeBerger.gerar(20, false)
                .forEach(rodada -> rodada.forEach(jogo -> mandos[jogo.mandante()]++));
        // 19 rodadas ímpares não dividem por igual; a diferença aceitável é 1.
        for (var mando : mandos) {
            assertThat(mando).isBetween(9, 10);
        }
    }

    @Test
    void deveRecusarNumeroImparDeParticipantes() {
        assertThatThrownBy(() -> TabelaDeBerger.gerar(19, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("par");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*TabelaDeBergerTest'`
Expected: FAIL — `TabelaDeBerger` não existe; não compila.

- [ ] **Step 3: Criar `ConfrontoDaRodada` e `ConstantesDeCalendario`**

```java
package br.com.api.footfirma.calendario.internal;

/** Um confronto por índice na lista de participantes. Ids só entram na gravação. */
record ConfrontoDaRodada(int mandante, int visitante) {
}
```

```java
package br.com.api.footfirma.calendario.internal;

/**
 * Números de balanceamento do módulo.
 *
 * <p>Nenhum tem verdade de referência além do descanso, que é o piso que o padrão de
 * fim de semana e meio de semana produz: domingo → quarta são 3 dias. Os testes travam
 * a forma das regras, não estes valores.
 */
final class ConstantesDeCalendario {

    private ConstantesDeCalendario() {
    }

    /** Domingo → quarta são exatamente 3 dias; quarta → domingo, 4. */
    static final int DESCANSO_MINIMO_EM_DIAS = 3;

    /** A janela da rodada é o alvo ± este número de dias. */
    static final int RAIO_DA_JANELA_EM_DIAS = 2;

    /** Teto de dias que um jogo pode ser empurrado além da janela antes de falhar. */
    static final int DESLOCAMENTO_MAXIMO_EM_DIAS = 21;
}
```

- [ ] **Step 4: Implementar `TabelaDeBerger`**

```java
package br.com.api.footfirma.calendario.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Round-robin pelo método do círculo: a primeira posição fica fixa e as demais rodam.
 *
 * <p>Puro de propósito — recebe um número e devolve índices. Quem traduz índice em clube
 * é o serviço, e é isso que permite testar o emparelhamento sem banco.
 *
 * <p>O mando alterna com a paridade da rodada. Sem isso o participante da posição fixa
 * seria mandante em todas as 19 rodadas do turno.
 */
final class TabelaDeBerger {

    private TabelaDeBerger() {
    }

    static List<List<ConfrontoDaRodada>> gerar(int participantes, boolean returno) {
        if (participantes < 2 || participantes % 2 != 0) {
            throw new IllegalArgumentException(
                    "Número de participantes precisa ser par e ao menos 2: " + participantes);
        }

        var mesa = new ArrayList<Integer>(participantes);
        for (var i = 0; i < participantes; i++) {
            mesa.add(i);
        }

        var turno = new ArrayList<List<ConfrontoDaRodada>>();
        for (var rodada = 0; rodada < participantes - 1; rodada++) {
            var jogos = new ArrayList<ConfrontoDaRodada>(participantes / 2);
            for (var i = 0; i < participantes / 2; i++) {
                var casa = mesa.get(i);
                var fora = mesa.get(participantes - 1 - i);
                // Alterna o mando pela paridade da rodada e do par: distribui os mandos
                // sem quebrar a propriedade de todo mundo enfrentar todo mundo.
                jogos.add((rodada + i) % 2 == 0
                        ? new ConfrontoDaRodada(casa, fora)
                        : new ConfrontoDaRodada(fora, casa));
            }
            turno.add(List.copyOf(jogos));
            rotacionar(mesa);
        }

        if (!returno) {
            return List.copyOf(turno);
        }

        var completo = new ArrayList<>(turno);
        turno.forEach(rodada -> completo.add(rodada.stream()
                .map(jogo -> new ConfrontoDaRodada(jogo.visitante(), jogo.mandante()))
                .toList()));
        return List.copyOf(completo);
    }

    /** A posição 0 é o eixo; as demais giram uma casa. */
    private static void rotacionar(List<Integer> mesa) {
        var ultimo = mesa.removeLast();
        mesa.add(1, ultimo);
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests '*TabelaDeBergerTest'`
Expected: PASS. Se `deveDistribuirOMandoQuaseIgualmenteNoTurno` falhar, o critério de alternância está errado — **ajuste a fórmula, não o teste**.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/calendario/internal \
        src/test/java/br/com/api/footfirma/calendario/internal/TabelaDeBergerTest.java
git commit -m "feat(calendario): gera o round-robin pelo método do círculo"
```

---

### Task 4: `TipadorDeRodadas` e `SorteadorDeDia`

**Files:**
- Create: `calendario/internal/{TipadorDeRodadas,SorteadorDeDia}.java`
- Create: `calendario/dto/PerfilDeCalendario.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/internal/{TipagemDeRodadasTest,SorteioDeDiaTest}.java`

**Interfaces:**
- Consumes: `TipoDeRodada` da Task 1
- Produces: `TipadorDeRodadas.tipar(int rodadas, int diasDaJanela)` devolvendo `List<TipoDeRodada>`; `TipadorDeRodadas.datasAlvo(LocalDate inicio, List<TipoDeRodada> tipos)` devolvendo `List<LocalDate>`; `SorteadorDeDia.ordenarPorPeso(Map<DayOfWeek, Integer> pesos, SplittableRandom aleatorio)` devolvendo `List<DayOfWeek>`; `PerfilDeCalendario(Map<TipoDeRodada, Map<DayOfWeek, Integer>> pesos, int descansoMinimoEmDias)`

- [ ] **Step 1: Escrever `TipagemDeRodadasTest`**

```java
package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.dto.TipoDeRodada;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TipagemDeRodadasTest {

    // 4/4/2026 a 6/12/2026 são 246 dias, ou 35 semanas inteiras.
    static final int JANELA_DO_MUNDO = 246;

    @Test
    void deveProduzirTresRodadasDeMeioDeSemanaQuandoTrintaEOitoNaoCabemEmTrintaECinco() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        assertThat(tipos).hasSize(38);
        assertThat(tipos.stream().filter(t -> t == TipoDeRodada.MEIO_DE_SEMANA)).hasSize(3);
    }

    @Test
    void naoDeveProduzirMeioDeSemanaQuandoAsRodadasCabemEmSemanas() {
        var tipos = TipadorDeRodadas.tipar(20, JANELA_DO_MUNDO);
        assertThat(tipos).containsOnly(TipoDeRodada.FIM_DE_SEMANA);
    }

    @Test
    void deveEspalharAsRodadasDeMeioDeSemanaEmVezDeAgrupaLasNoFim() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        var posicoes = new java.util.ArrayList<Integer>();
        for (var i = 0; i < tipos.size(); i++) {
            if (tipos.get(i) == TipoDeRodada.MEIO_DE_SEMANA) {
                posicoes.add(i);
            }
        }
        // Três rodadas em 38 posições: nenhuma deve cair nas duas pontas nem colar na outra.
        assertThat(posicoes.getFirst()).isGreaterThan(2);
        assertThat(posicoes.getLast()).isLessThan(35);
        for (var i = 1; i < posicoes.size(); i++) {
            assertThat(posicoes.get(i) - posicoes.get(i - 1)).isGreaterThan(3);
        }
    }

    @Test
    void deveAncorarFimDeSemanaNoDomingoEMeioDeSemanaNaQuarta() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        var datas = TipadorDeRodadas.datasAlvo(LocalDate.of(2026, 4, 4), tipos);

        assertThat(datas).hasSize(38);
        for (var i = 0; i < 38; i++) {
            var esperado = tipos.get(i) == TipoDeRodada.FIM_DE_SEMANA
                    ? DayOfWeek.SUNDAY : DayOfWeek.WEDNESDAY;
            assertThat(datas.get(i).getDayOfWeek())
                    .as("rodada %d", i + 1).isEqualTo(esperado);
        }
    }

    @Test
    void deveManterAsDatasEmOrdemCrescente() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        var datas = TipadorDeRodadas.datasAlvo(LocalDate.of(2026, 4, 4), tipos);
        for (var i = 1; i < datas.size(); i++) {
            assertThat(datas.get(i)).isAfter(datas.get(i - 1));
        }
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*TipagemDeRodadasTest'`
Expected: FAIL — `TipadorDeRodadas` não existe.

- [ ] **Step 3: Implementar `TipadorDeRodadas`**

```java
package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.dto.TipoDeRodada;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Decide quais rodadas são de meio de semana e onde cada uma cai.
 *
 * <p>A regra não é "o intervalo médio apertou": é aritmética de semanas. Cabem
 * {@code dias / 7} semanas na janela; o que passar disso vira rodada de meio de semana,
 * espalhada uniformemente para não amontoar no fim da temporada.
 */
final class TipadorDeRodadas {

    private TipadorDeRodadas() {
    }

    static List<TipoDeRodada> tipar(int rodadas, int diasDaJanela) {
        var semanas = diasDaJanela / 7;
        var meioDeSemana = Math.max(0, rodadas - semanas);

        var tipos = new ArrayList<TipoDeRodada>(rodadas);
        for (var i = 0; i < rodadas; i++) {
            tipos.add(TipoDeRodada.FIM_DE_SEMANA);
        }
        // Posições espaçadas por rodadas/(meioDeSemana+1): com 3 em 38, caem perto de
        // 9, 19 e 28 — nunca nas pontas, nunca coladas.
        for (var i = 1; i <= meioDeSemana; i++) {
            var posicao = Math.round((float) rodadas * i / (meioDeSemana + 1));
            tipos.set(Math.min(posicao, rodadas - 1), TipoDeRodada.MEIO_DE_SEMANA);
        }
        return List.copyOf(tipos);
    }

    /**
     * Fim de semana ocupa domingos consecutivos; meio de semana ocupa a quarta da semana
     * corrente e não consome o domingo — é isso que faz o clube jogar duas vezes na
     * mesma semana.
     */
    static List<LocalDate> datasAlvo(LocalDate inicio, List<TipoDeRodada> tipos) {
        var datas = new ArrayList<LocalDate>(tipos.size());
        var domingo = inicio.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        for (var tipo : tipos) {
            if (tipo == TipoDeRodada.MEIO_DE_SEMANA) {
                // A quarta que antecede o próximo domingo da sequência.
                datas.add(domingo.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY)));
                domingo = domingo.plusWeeks(1);
            } else {
                datas.add(domingo);
                domingo = domingo.plusWeeks(1);
            }
        }
        return List.copyOf(datas);
    }
}
```

Se `deveManterAsDatasEmOrdemCrescente` falhar, a quarta de uma rodada de meio de semana está caindo antes do domingo anterior — corrija o avanço, não o teste.

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests '*TipagemDeRodadasTest'`
Expected: PASS

- [ ] **Step 5: Escrever `SorteioDeDiaTest`**

```java
package br.com.api.footfirma.calendario.internal;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SorteioDeDiaTest {

    static final Map<DayOfWeek, Integer> FIM_DE_SEMANA_SERIE_A = Map.of(
            DayOfWeek.SUNDAY, 45, DayOfWeek.SATURDAY, 40, DayOfWeek.MONDAY, 15);

    @Test
    void deveDevolverTodosOsDiasDoLequeEmUmaOrdem() {
        var ordem = SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, new SplittableRandom(7));
        assertThat(ordem).containsExactlyInAnyOrder(
                DayOfWeek.SUNDAY, DayOfWeek.SATURDAY, DayOfWeek.MONDAY);
    }

    @Test
    void naoDeveIncluirDiaForaDoLeque() {
        var ordem = SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, new SplittableRandom(7));
        assertThat(ordem).doesNotContain(DayOfWeek.FRIDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    void deveRepetirASequenciaComAMesmaSemente() {
        assertThat(SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, new SplittableRandom(42)))
                .isEqualTo(SorteadorDeDia.ordenarPorPeso(
                        FIM_DE_SEMANA_SERIE_A, new SplittableRandom(42)));
    }

    @Test
    void devePreferirODeMaiorPesoNaMaioriaDasVezes() {
        var aleatorio = new SplittableRandom(1);
        var domingoPrimeiro = 0;
        for (var i = 0; i < 1_000; i++) {
            if (SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, aleatorio)
                    .getFirst() == DayOfWeek.SUNDAY) {
                domingoPrimeiro++;
            }
        }
        // 45% de peso: a faixa é generosa de propósito, o teste trava a tendência.
        assertThat(domingoPrimeiro).isBetween(380, 520);
    }
}
```

- [ ] **Step 6: Rodar e ver falhar, depois implementar**

Run: `./gradlew test --tests '*SorteioDeDiaTest'` → FAIL.

```java
package br.com.api.footfirma.calendario.internal;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Ordena os dias do leque por sorteio ponderado, sem repetição.
 *
 * <p>Devolve a ordem inteira, e não um dia só: o alocador tenta o primeiro, e desce a
 * lista quando o descanso não fecha. Sortear de novo a cada tentativa quebraria o
 * determinismo.
 */
final class SorteadorDeDia {

    private SorteadorDeDia() {
    }

    static List<DayOfWeek> ordenarPorPeso(Map<DayOfWeek, Integer> pesos,
                                          SplittableRandom aleatorio) {
        // Ordem estável antes do sorteio: HashMap não garante iteração reprodutível, e
        // sem isso a mesma semente daria calendários diferentes entre execuções.
        var candidatos = new ArrayList<>(pesos.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList());

        var ordem = new ArrayList<DayOfWeek>(candidatos.size());
        var restante = candidatos.stream().mapToInt(Map.Entry::getValue).sum();

        while (!candidatos.isEmpty()) {
            var sorteio = aleatorio.nextInt(Math.max(1, restante));
            var acumulado = 0;
            var escolhido = candidatos.size() - 1;
            for (var i = 0; i < candidatos.size(); i++) {
                acumulado += candidatos.get(i).getValue();
                if (sorteio < acumulado) {
                    escolhido = i;
                    break;
                }
            }
            var vencedor = candidatos.remove(escolhido);
            restante -= vencedor.getValue();
            ordem.add(vencedor.getKey());
        }
        return List.copyOf(ordem);
    }
}
```

- [ ] **Step 7: Criar `PerfilDeCalendario`**

```java
package br.com.api.footfirma.calendario.dto;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Os dias em que uma competição joga, por tipo de rodada, e o descanso que ela respeita.
 *
 * <p>É entrada de geração, não dado persistido — como a semente. Persistir exigiria
 * migration em {@code competicao} ou tabela nova para um dado que hoje tem dois valores.
 *
 * @param pesos peso relativo de cada dia dentro do leque do tipo. Não precisa somar 100.
 */
public record PerfilDeCalendario(
        Map<TipoDeRodada, Map<DayOfWeek, Integer>> pesos,
        int descansoMinimoEmDias) {
}
```

- [ ] **Step 8: Rodar os dois testes e commitar**

Run: `./gradlew test --tests '*TipagemDeRodadasTest' --tests '*SorteioDeDiaTest'`
Expected: PASS

```bash
git add src/main/java/br/com/api/footfirma/calendario \
        src/test/java/br/com/api/footfirma/calendario/internal
git commit -m "feat(calendario): tipa as rodadas e sorteia o dia por peso"
```

---

### Task 5: `AlocadorDeDatas` — a regra de descanso

**Files:**
- Create: `calendario/internal/AlocadorDeDatas.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/internal/AlocacaoDeDatasTest.java`

**Interfaces:**
- Consumes: `ConstantesDeCalendario` da Task 3
- Produces: `AlocadorDeDatas.alocar(LocalDate janelaInicio, LocalDate janelaFim, List<DayOfWeek> ordemDeDias, List<LocalDate> agendaMandante, List<LocalDate> agendaVisitante, int descansoMinimo)` devolvendo `LocalDate`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.calendario.internal;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlocacaoDeDatasTest {

    static final LocalDate INICIO = LocalDate.of(2026, 7, 3);   // sexta
    static final LocalDate FIM    = LocalDate.of(2026, 7, 7);   // terça
    static final List<DayOfWeek> DOMINGO_PRIMEIRO =
            List.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY, DayOfWeek.MONDAY);

    @Test
    void devePegarOPrimeiroDiaDaOrdemQuandoAAgendaEstaLivre() {
        var data = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, List.of(), List.of(), 3);
        assertThat(data).isEqualTo(LocalDate.of(2026, 7, 5));  // domingo
    }

    @Test
    void deveDescerNaOrdemQuandoOMandanteJogouRecente() {
        // Mandante jogou no domingo 5/7 em outra competição: 5/7 não serve.
        var data = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO,
                List.of(LocalDate.of(2026, 7, 5)), List.of(), 3);
        assertThat(data).isNotEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(data).isEqualTo(LocalDate.of(2026, 7, 4));  // sábado, 1 dia antes? não
    }

    @Test
    void deveRespeitarODescansoDosDoisClubes() {
        var data = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO,
                List.of(LocalDate.of(2026, 7, 5)),
                List.of(LocalDate.of(2026, 7, 4)), 3);
        assertThat(Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(2026, 7, 5), data))).isGreaterThanOrEqualTo(3);
        assertThat(Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(2026, 7, 4), data))).isGreaterThanOrEqualTo(3);
    }

    @Test
    void deveEmpurrarParaForaDaJanelaQuandoNenhumDiaDoLequeServe() {
        var ocupado = List.of(
                LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 6));
        var data = AlocadorDeDatas.alocar(INICIO, FIM, DOMINGO_PRIMEIRO, ocupado, List.of(), 3);
        assertThat(data).isAfter(FIM);
    }

    @Test
    void deveFalharQuandoNemMesmoOEmpurraoResolve() {
        var lotado = new java.util.ArrayList<LocalDate>();
        for (var i = 0; i < 60; i++) {
            lotado.add(INICIO.plusDays(i));
        }
        assertThatThrownBy(() -> AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, lotado, List.of(), 3))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

Corrija a expectativa de `deveDescerNaOrdemQuandoOMandanteJogouRecente` ao implementar: sábado 4/7 está a 1 dia de 5/7 e **também** viola o descanso. A asserção correta é que a data devolvida respeita 3 dias de 5/7 — reescreva-a assim antes do Step 3, mantendo o `isNotEqualTo`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*AlocacaoDeDatasTest'`
Expected: FAIL — `AlocadorDeDatas` não existe.

- [ ] **Step 3: Implementar**

```java
package br.com.api.footfirma.calendario.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Escolhe o dia de um jogo dentro da janela da rodada, respeitando o descanso dos dois
 * clubes contra tudo que já está na agenda deles na temporada.
 *
 * <p>Recebe as agendas já carregadas, e não o repositório: é o que mantém a regra pura e
 * testável sem banco, como as fábricas de {@code mundo}.
 *
 * <p>Guloso — escolhe dia a dia e não retrocede. Erra onde uma busca completa acertaria;
 * com uma competição por clube o erro é raro, e o teste de descanso pega a violação.
 */
final class AlocadorDeDatas {

    private AlocadorDeDatas() {
    }

    static LocalDate alocar(LocalDate janelaInicio, LocalDate janelaFim,
                            List<DayOfWeek> ordemDeDias,
                            List<LocalDate> agendaMandante, List<LocalDate> agendaVisitante,
                            int descansoMinimo) {
        for (var dia : ordemDeDias) {
            for (var data = janelaInicio; !data.isAfter(janelaFim); data = data.plusDays(1)) {
                if (data.getDayOfWeek() == dia
                        && descansa(data, agendaMandante, descansoMinimo)
                        && descansa(data, agendaVisitante, descansoMinimo)) {
                    return data;
                }
            }
        }

        // Leque esgotado: empurra para o primeiro dia posterior que sirva. A rodada não
        // desliza — deslizá-la propagaria em cascata por todas as seguintes.
        for (var i = 1; i <= ConstantesDeCalendario.DESLOCAMENTO_MAXIMO_EM_DIAS; i++) {
            var data = janelaFim.plusDays(i);
            if (descansa(data, agendaMandante, descansoMinimo)
                    && descansa(data, agendaVisitante, descansoMinimo)) {
                return data;
            }
        }

        throw new IllegalStateException(
                "Nenhuma data respeita o descanso de %d dias entre %s e %s + %d"
                        .formatted(descansoMinimo, janelaInicio, janelaFim,
                                ConstantesDeCalendario.DESLOCAMENTO_MAXIMO_EM_DIAS));
    }

    private static boolean descansa(LocalDate candidata, List<LocalDate> agenda, int minimo) {
        return agenda.stream()
                .allMatch(ocupada -> Math.abs(ChronoUnit.DAYS.between(ocupada, candidata)) >= minimo);
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests '*AlocacaoDeDatasTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/calendario/internal/AlocadorDeDatas.java \
        src/test/java/br/com/api/footfirma/calendario/internal/AlocacaoDeDatasTest.java
git commit -m "feat(calendario): aloca a data respeitando o descanso dos dois clubes"
```

---

### Task 6: `gerarTemporada` — pontos corridos ponta a ponta

**Files:**
- Create: `calendario/CalendarioService.java`
- Create: `calendario/dto/{EdicaoParaGerar,RelatorioDeCalendario,JogoAgendado,RodadaDetalhe,ConfrontoDetalhe,RegrasDeDesempate}.java`
- Create: `calendario/internal/CalendarioServiceImpl.java`
- Create: `src/main/java/br/com/api/footfirma/shared/exception/CalendarioInvalidoException.java`
- Modify: `src/main/java/br/com/api/footfirma/shared/exception/TratadorDeErros.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/GeracaoDePontosCorridosTest.java`

**Interfaces:**
- Consumes: `TabelaDeBerger`, `TipadorDeRodadas`, `SorteadorDeDia`, `AlocadorDeDatas`, `ConstantesDeCalendario`, `PerfilDeCalendario`, `CompeticaoService.listarFasesDaEdicao` e `listarParticipantesDaFase` (Task 2)
- Produces: `CalendarioService.gerarTemporada(long temporadaId, long semente, List<EdicaoParaGerar> edicoes)` devolvendo `RelatorioDeCalendario(int rodadas, int confrontos, int jogos)`; `EdicaoParaGerar(long edicaoId, int precedencia, PerfilDeCalendario perfil)`; `CalendarioService.listarAgendaDoClube(long clubeId, long temporadaId)` devolvendo `List<JogoAgendado>`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeracaoDePontosCorridosTest {

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CalendarioFactory factory;

    // Uma liga de 20 clubes, turno e returno, na janela do mundo gerado. Gerar uma vez
    // para a classe inteira: são 380 jogos, e repetir por teste multiplicaria a carga.
    @BeforeAll
    void gerar() {
        var cenario = factory.criar("liga-teste", 20, "PONTOS_CORRIDOS", 2);
        calendarioService.gerarTemporada(cenario.temporadaId(), 42L,
                List.of(new EdicaoParaGerar(cenario.edicaoId(), 1, PERFIL)));
    }

    static final PerfilDeCalendario PERFIL = new PerfilDeCalendario(Map.of(
            TipoDeRodada.FIM_DE_SEMANA, Map.of(
                    DayOfWeek.SUNDAY, 45, DayOfWeek.SATURDAY, 40, DayOfWeek.MONDAY, 15),
            TipoDeRodada.MEIO_DE_SEMANA, Map.of(
                    DayOfWeek.WEDNESDAY, 60, DayOfWeek.THURSDAY, 40)), 3);

    @Test
    void deveCriarTrintaEOitoRodadasETrezentosEOitentaJogos() {
        assertThat(contar("rodada")).isEqualTo(38);
        assertThat(contar("confronto")).isEqualTo(380);
        assertThat(contar("jogo")).isEqualTo(380);
    }

    @Test
    void deveDarUmJogoPorConfrontoEmPontosCorridos() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select confronto_id, count(*) as jogos from jogo group by confronto_id
                ) c where jogos <> 1
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void naoDeveMarcarNenhumJogoNaSexta() {
        var sextas = jdbcTemplate.queryForObject(
                "select count(*) from jogo where extract(dow from data_jogo) = 5", Integer.class);
        assertThat(sextas).isZero();
    }

    @Test
    void deveRespeitarODescansoMinimoDeTodoClube() {
        var violacoes = jdbcTemplate.queryForObject("""
                with agenda as (
                    select mandante_id as clube_id, data_jogo from jogo
                    union all
                    select visitante_id, data_jogo from jogo
                ),
                consecutivos as (
                    select clube_id, data_jogo,
                           lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
                    from agenda
                )
                select count(*) from consecutivos
                where anterior is not null and data_jogo - anterior < 3
                """, Integer.class);
        assertThat(violacoes).isZero();
    }

    @Test
    void naoDeveEscalarNenhumClubeDuasVezesNaMesmaRodada() {
        var repetidos = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select j.rodada_id, c.clube_id from jogo j
                    cross join lateral (values (j.mandante_id), (j.visitante_id)) as c(clube_id)
                    group by j.rodada_id, c.clube_id having count(*) > 1
                ) r
                """, Integer.class);
        assertThat(repetidos).isZero();
    }

    @Test
    void deveDarMandoEVisitanteAtodoJogoDePontosCorridos() {
        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from jogo
                where mandante_id is null or visitante_id is null
                   or estadio_id is null or data_jogo is null
                """, Integer.class);
        assertThat(incompletos).isZero();
    }

    @Test
    void deveDeixarVencedorNuloEmPontosCorridos() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from confronto where vencedor_clube_id is not null",
                Integer.class)).isZero();
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
```

Preencha o `@BeforeAll` com a montagem real: os 20 clubes precisam existir com estádio, porque `jogo.estadio_id` sai do mandante. Copie o padrão de semeadura de `MundoLigaTest`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*GeracaoDePontosCorridosTest'`
Expected: FAIL — `CalendarioService` não existe.

- [ ] **Step 3: Criar os DTOs de contrato**

```java
package br.com.api.footfirma.calendario.dto;

/**
 * Uma edição a gerar, com sua precedência na temporada.
 *
 * <p>A precedência existe porque a ordem de geração decide quem fica com os melhores
 * dias: quem gera primeiro ocupa, quem vem depois se acomoda. Como argumento ordenado
 * pelo próprio módulo, a dependência deixa de ser a sequência das linhas em quem chama.
 *
 * @param precedencia menor gera primeiro. Divisão mais alta antes da mais baixa; liga
 *                    antes de copa.
 */
public record EdicaoParaGerar(long edicaoId, int precedencia, PerfilDeCalendario perfil) {
}
```

```java
package br.com.api.footfirma.calendario.dto;

public record RelatorioDeCalendario(int rodadas, int confrontos, int jogos) {
}
```

```java
package br.com.api.footfirma.calendario.dto;

/**
 * As regras que decidem o confronto, lidas da fase. Vão junto do jogo para que a partida
 * simule tudo de uma vez, em vez de perguntar entre um tempo e outro.
 *
 * @param decisivo o jogo é o último do confronto — só nele prorrogação e pênaltis valem.
 */
public record RegrasDeDesempate(boolean temGolFora, boolean temProrrogacao,
                                boolean temPenaltis, boolean decisivo) {
}
```

```java
package br.com.api.footfirma.calendario.dto;

import java.time.LocalDate;

public record JogoAgendado(long jogoId, long confrontoId, long rodadaId, int ordemNoConfronto,
                           Long mandanteId, Long visitanteId, Long estadioId,
                           LocalDate dataJogo, SituacaoDoJogo situacao,
                           Integer golsMandante, Integer golsVisitante,
                           RegrasDeDesempate desempate) {
}
```

`RodadaDetalhe(long rodadaId, int ordem, TipoDeRodada tipo, LocalDate dataAlvo, List<JogoAgendado> jogos)` e `ConfrontoDetalhe(long confrontoId, int ordem, String chave, Long clubeAId, Long clubeBId, Long vencedorClubeId, List<JogoAgendado> jogos)` seguem o mesmo formato.

- [ ] **Step 4: Criar a exceção e o handler**

```java
package br.com.api.footfirma.shared.exception;

/**
 * A geração de calendário é impossível com os dados dados — fase sem participante,
 * número ímpar em eliminatória, janela curta demais para as rodadas exigidas.
 *
 * <p>Mora aqui, e não em {@code calendario}, pelo mesmo motivo de
 * {@link EscalacaoInvalidaException}: quem a traduz para HTTP é o {@code TratadorDeErros}
 * de {@code shared}, e ele não pode enxergar tipo interno de módulo de domínio.
 */
public class CalendarioInvalidoException extends RuntimeException {

    public CalendarioInvalidoException(String mensagem) {
        super(mensagem);
    }
}
```

No `TratadorDeErros`, copiando o formato do handler de `EscalacaoInvalidaException`:

```java
    @ExceptionHandler(CalendarioInvalidoException.class)
    ProblemDetail calendarioInvalido(CalendarioInvalidoException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
        problema.setTitle("Calendário inválido");
        return problema;
    }
```

- [ ] **Step 5: Criar a porta pública**

```java
package br.com.api.footfirma.calendario;

import br.com.api.footfirma.calendario.dto.*;

import java.util.List;
import java.util.Optional;

/**
 * A única porta pública do módulo, e o único caminho de escrita.
 *
 * <p>As invariantes do calendário são de agregado — "ninguém joga duas vezes na mesma
 * rodada", "todo clube descansa N dias" —, e {@code check} não enxerga outras linhas.
 */
public interface CalendarioService {

    /**
     * Gera o calendário das edições, na ordem da precedência de cada uma.
     *
     * <p>Recebe a lista inteira, e não uma edição por chamada, porque a ordem decide o
     * calendário: quem gera primeiro ocupa os melhores dias. Ordenar aqui dentro tira
     * essa dependência da sequência das linhas de quem chama.
     *
     * @throws br.com.api.footfirma.shared.exception.CalendarioInvalidoException
     *         se alguma fase não puder ser gerada
     */
    RelatorioDeCalendario gerarTemporada(long temporadaId, long semente,
                                         List<EdicaoParaGerar> edicoes);

    /**
     * Grava o placar, encerra o jogo e, em fase eliminatória, resolve o confronto e
     * propaga o vencedor.
     *
     * @throws br.com.api.footfirma.shared.exception.ResultadoInvalidoException
     *         se o desempate não for permitido pela fase, o jogo já estiver encerrado ou
     *         seus clubes ainda não estiverem definidos
     */
    void registrarResultado(long jogoId, ResultadoDoJogo resultado);

    Optional<JogoAgendado> buscarJogo(long jogoId);

    /** Os jogos do clube na temporada, em qualquer competição, por data. */
    List<JogoAgendado> listarAgendaDoClube(long clubeId, long temporadaId);

    List<RodadaDetalhe> listarRodadas(long edicaoId);

    List<ConfrontoDetalhe> listarChaveamento(long faseId);
}
```

- [ ] **Step 6: Implementar `gerarTemporada` para `PONTOS_CORRIDOS`**

Em `CalendarioServiceImpl`, anotado com `@Service`, `@RequiredArgsConstructor` e `@Transactional(readOnly = true)`:

```java
    @Override
    @Transactional
    public RelatorioDeCalendario gerarTemporada(long temporadaId, long semente,
                                                List<EdicaoParaGerar> edicoes) {
        var rodadas = 0;
        var confrontos = 0;
        var jogos = 0;

        // A ordenação é a razão de este método receber a lista inteira.
        for (var edicao : edicoes.stream()
                .sorted(Comparator.comparingInt(EdicaoParaGerar::precedencia)).toList()) {
            for (var fase : competicaoService.listarFasesDaEdicao(edicao.edicaoId())) {
                var contagem = gerarFase(edicao, fase, temporadaId, semente);
                rodadas += contagem.rodadas();
                confrontos += contagem.confrontos();
                jogos += contagem.jogos();
            }
        }
        return new RelatorioDeCalendario(rodadas, confrontos, jogos);
    }
```

`gerarFase` despacha por `fase.tipo()`. Nesta task, só `PONTOS_CORRIDOS` é implementado; `GRUPOS` e `ELIMINATORIA` lançam `CalendarioInvalidoException("Tipo de fase ainda não gerado: " + tipo)` — falha explícita, e não silêncio, até a Task 11.

O corpo de pontos corridos, em ordem:

1. `participantes = competicaoService.listarParticipantesDaFase(fase.id())`; se vazio, `CalendarioInvalidoException`.
2. `tabela = TabelaDeBerger.gerar(participantes.size(), fase.jogosPorConfronto() == 2)`.
3. `janela = competicaoService.buscarJanelaDaEdicao(edicao.edicaoId())`, lançando `CalendarioInvalidoException` se vazio; `tipos = TipadorDeRodadas.tipar(tabela.size(), (int) ChronoUnit.DAYS.between(janela.inicio(), janela.fim()))`.
4. `alvos = TipadorDeRodadas.datasAlvo(janela.inicio(), tipos)`.
5. Para cada rodada: grava `Rodada` com `janelaInicio = alvo.minusDays(RAIO)` e `janelaFim = alvo.plusDays(RAIO)`; para cada jogo, `SorteadorDeDia.ordenarPorPeso(perfil.pesos().get(tipo), aleatorio)`, depois `AlocadorDeDatas.alocar(...)` com as agendas dos dois clubes, grava `Confronto` e `Jogo`.
6. A agenda de cada clube é mantida num `Map<Long, List<LocalDate>>` em memória durante a geração, semeado com `jogoRepository.findDatasDoClube` das fases já existentes na temporada. Sem isso seria uma consulta por jogo — 760 idas ao banco.

O `SplittableRandom` é `new SplittableRandom(semente + edicao.edicaoId())`: mexer numa liga não desloca a outra, como `mundo` já faz por clube.

- [ ] **Step 7: Rodar e ver passar**

Run: `./gradlew test --tests '*GeracaoDePontosCorridosTest'`
Expected: PASS. Se `deveRespeitarODescansoMinimoDeTodoClube` falhar, o alocador não está recebendo a agenda acumulada — **corrija a alimentação, não o teste**.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/br/com/api/footfirma/calendario src/main/java/br/com/api/footfirma/shared \
        src/test/java/br/com/api/footfirma/calendario/GeracaoDePontosCorridosTest.java
git commit -m "feat(calendario): gera o calendário de pontos corridos"
```

---

### Task 7: `registrarResultado`

**Files:**
- Create: `calendario/dto/ResultadoDoJogo.java`
- Create: `calendario/internal/ResolvedorDeConfronto.java`
- Create: `src/main/java/br/com/api/footfirma/shared/exception/ResultadoInvalidoException.java`
- Modify: `calendario/internal/CalendarioServiceImpl.java`
- Modify: `src/main/java/br/com/api/footfirma/shared/exception/TratadorDeErros.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/internal/ResolucaoDeConfrontoTest.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/RegistroDeResultadoTest.java`

**Interfaces:**
- Consumes: `CalendarioService` da Task 6
- Produces: `ResultadoDoJogo(int golsMandante, int golsVisitante, Integer golsMandanteProrrogacao, Integer golsVisitanteProrrogacao, Integer penaltisMandante, Integer penaltisVisitante)`; `ResolvedorDeConfronto.resolver(List<PlacarDoConfronto> jogos, RegrasDeDesempate regras, long clubeA, long clubeB)` devolvendo `Optional<Long>` — o vencedor, vazio quando o confronto não decide

- [ ] **Step 1: Escrever `ResolucaoDeConfrontoTest`**

```java
package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.dto.RegrasDeDesempate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolucaoDeConfrontoTest {

    static final long A = 10L;
    static final long B = 20L;

    static final RegrasDeDesempate TUDO_LIGADO = new RegrasDeDesempate(true, true, true, true);
    static final RegrasDeDesempate SO_AGREGADO = new RegrasDeDesempate(false, false, false, true);

    @Test
    void deveDarOVencedorPeloAgregadoQuandoNaoHaEmpate() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 2, 1, null, null, null, null),
                            new PlacarDoConfronto(B, A, 1, 1, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(A);
    }

    @Test
    void deveUsarGolForaQuandoOAgregadoEmpataEARegraEstaLigada() {
        // A fez 1 fora, B fez 0 fora. Agregado 2 a 2.
        var jogos = List.of(new PlacarDoConfronto(A, B, 2, 1, null, null, null, null),
                            new PlacarDoConfronto(B, A, 1, 0, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(A);
    }

    @Test
    void naoDeveUsarGolForaQuandoARegraEstaDesligada() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 2, 1, null, null, null, null),
                            new PlacarDoConfronto(B, A, 1, 0, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, SO_AGREGADO, A, B)).isEmpty();
    }

    @Test
    void deveUsarProrrogacaoAntesDosPenaltis() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 1, 1, 1, 0, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(A);
    }

    @Test
    void deveCairNosPenaltisQuandoAProrrogacaoTambemEmpata() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 1, 1, 1, 1, 4, 5));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(B);
    }

    @Test
    void deveDevolverVazioQuandoOConfrontoAindaEmpataEnadaMaisDecide() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 1, 1, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, SO_AGREGADO, A, B)).isEmpty();
    }
}
```

`PlacarDoConfronto(long mandanteId, long visitanteId, int golsMandante, int golsVisitante, Integer prorrogacaoMandante, Integer prorrogacaoVisitante, Integer penaltisMandante, Integer penaltisVisitante)` é um record de `internal/`.

- [ ] **Step 2: Rodar, ver falhar, implementar `ResolvedorDeConfronto`**

Run: `./gradlew test --tests '*ResolucaoDeConfrontoTest'` → FAIL.

A ordem é agregado → gol fora → prorrogação → pênaltis, cada etapa só quando a anterior empata **e** a regra está ligada. Gol fora conta os gols que cada clube fez como visitante, somando os dois jogos.

- [ ] **Step 3: Criar `ResultadoDoJogo` e `ResultadoInvalidoException`**

```java
package br.com.api.footfirma.calendario.dto;

/**
 * O que aconteceu no jogo, tudo de uma vez. Prorrogação e pênaltis nulos quando não
 * houve — e o serviço recusa os que a fase não permite.
 */
public record ResultadoDoJogo(int golsMandante, int golsVisitante,
                              Integer golsMandanteProrrogacao, Integer golsVisitanteProrrogacao,
                              Integer penaltisMandante, Integer penaltisVisitante) {
}
```

`ResultadoInvalidoException` copia o formato de `CalendarioInvalidoException`, com o handler 422 correspondente e título "Resultado inválido".

- [ ] **Step 4: Escrever `RegistroDeResultadoTest`**

Com Testcontainers, sobre uma fase de pontos corridos semeada como na Task 6:

```java
    @Test
    void deveGravarOPlacarEEncerrarOJogo() {
        calendarioService.registrarResultado(jogoId, new ResultadoDoJogo(2, 1, null, null, null, null));
        var jogo = calendarioService.buscarJogo(jogoId).orElseThrow();
        assertThat(jogo.golsMandante()).isEqualTo(2);
        assertThat(jogo.situacao()).isEqualTo(SituacaoDoJogo.ENCERRADO);
    }

    @Test
    void deveRecusarPenaltisEmPontosCorridos() {
        assertThatThrownBy(() -> calendarioService.registrarResultado(
                outroJogoId, new ResultadoDoJogo(1, 1, null, null, 4, 3)))
                .isInstanceOf(ResultadoInvalidoException.class)
                .hasMessageContaining("pênaltis");
    }

    @Test
    void deveRecusarJogoJaEncerrado() {
        calendarioService.registrarResultado(terceiroJogoId, new ResultadoDoJogo(0, 0, null, null, null, null));
        assertThatThrownBy(() -> calendarioService.registrarResultado(
                terceiroJogoId, new ResultadoDoJogo(1, 0, null, null, null, null)))
                .isInstanceOf(ResultadoInvalidoException.class)
                .hasMessageContaining("encerrado");
    }

    @Test
    void naoDeveResolverConfrontoEmPontosCorridos() {
        calendarioService.registrarResultado(quartoJogoId, new ResultadoDoJogo(3, 0, null, null, null, null));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from confronto where vencedor_clube_id is not null",
                Integer.class)).isZero();
    }
```

- [ ] **Step 5: Implementar `registrarResultado`**

Valida na ordem: jogo existe, `situacao == AGENDADO`, clubes definidos, desempate permitido pela fase. Grava o placar, marca `ENCERRADO`, e chama `ResolvedorDeConfronto` **somente** se a fase for `ELIMINATORIA`. A propagação vem na Task 12 — aqui o vencedor apenas é gravado no próprio confronto.

- [ ] **Step 6: Rodar os dois testes e commitar**

Run: `./gradlew test --tests '*ResolucaoDeConfrontoTest' --tests '*RegistroDeResultadoTest'`
Expected: PASS

```bash
git add src/main/java/br/com/api/footfirma src/test/java/br/com/api/footfirma/calendario
git commit -m "feat(calendario): registra resultado e resolve o confronto"
```

---

### Task 8: API read-only

**Files:**
- Create: `calendario/web/CalendarioController.java`
- Create: `calendario/mapper/CalendarioMapper.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/CalendarioApiTest.java`

**Interfaces:**
- Consumes: `CalendarioService.listarRodadas` e `listarAgendaDoClube` da Task 6
- Produces: `GET /api/v1/competicoes/{slug}/edicoes/{temporada}/rodadas` e `GET /api/v1/clubes/{slug}/jogos`

- [ ] **Step 1: Escrever o teste que falha**

```java
    @Test
    void deveListarAsRodadasDaEdicao() throws Exception {
        mockMvc.perform(get("/api/v1/competicoes/primeira-divisao/edicoes/2026/rodadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(38)))
                .andExpect(jsonPath("$[0].ordem").value(1));
    }

    @Test
    void deveDevolver404QuandoACompeticaoNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/competicoes/inexistente/edicoes/2026/rodadas"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveListarOsJogosDoClube() throws Exception {
        mockMvc.perform(get("/api/v1/clubes/" + slug + "/jogos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(38)));
    }
```

- [ ] **Step 2: Rodar, ver falhar, implementar o controller**

O controller é fino: sem regra, sem repository, sem `@Transactional`. `@Tag` e `@Operation` presentes, como `CompeticaoController`. O 404 vem de `RecursoNaoEncontradoException`, lançada quando a competição ou o clube não resolve — não do módulo `calendario`, que devolve lista vazia.

O path de rodadas fica **em `CalendarioController` com o prefixo `/api/v1/competicoes`**, e não dentro de `CompeticaoController`: o recurso é do calendário, e pôr o método lá inverteria a dependência.

- [ ] **Step 3: Rodar e commitar**

Run: `./gradlew test --tests '*CalendarioApiTest'`
Expected: PASS

```bash
git add src/main/java/br/com/api/footfirma/calendario/web \
        src/main/java/br/com/api/footfirma/calendario/mapper \
        src/test/java/br/com/api/footfirma/calendario/CalendarioApiTest.java
git commit -m "feat(calendario): expõe rodadas da edição e jogos do clube"
```

---

### Task 9: Integração com `mundo` e fechamento do slice A

**Files:**
- Modify: `mundo/internal/MundoServiceImpl.java`
- Modify: `mundo/internal/LimpezaDoCatalogo.java`
- Modify: `src/test/java/br/com/api/footfirma/mundo/MundoIntegridadeTest.java`
- Modify: `docs/runbooks/mundo.md`
- Modify: `AGENTS.md` e `../../AGENTS.md`

**Interfaces:**
- Consumes: `CalendarioService.gerarTemporada` da Task 6
- Produces: mundo gerado com 760 jogos

- [ ] **Step 1: Escrever o teste que falha, em `MundoIntegridadeTest`**

```java
    @Test
    void deveGerarOCalendarioDasDuasLigas() {
        assertThat(contar("rodada")).isEqualTo(76);
        assertThat(contar("jogo")).isEqualTo(760);
    }

    @Test
    void naoDeveMarcarJogoNaSextaEmNenhumaDasDuasLigas() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from jogo where extract(dow from data_jogo) = 5",
                Integer.class)).isZero();
    }

    @Test
    void deveRespeitarODescansoDeTodoClubeNoMundoInteiro() {
        var violacoes = jdbcTemplate.queryForObject("""
                with agenda as (
                    select mandante_id as clube_id, data_jogo from jogo
                    union all
                    select visitante_id, data_jogo from jogo
                ),
                consecutivos as (
                    select clube_id, data_jogo,
                           lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
                    from agenda
                )
                select count(*) from consecutivos
                where anterior is not null and data_jogo - anterior < 3
                """, Integer.class);
        assertThat(violacoes).isZero();
    }
```

- [ ] **Step 2: Chamar `gerarTemporada` em `MundoServiceImpl`**

A chamada entra **logo depois de `vincularParticipantes`**, antes de `criarElencos`: o calendário precisa dos participantes e não precisa de overall nem de plano.

```java
    /**
     * Gera o calendário das duas ligas. A precedência põe a primeira divisão antes da
     * segunda — quem gera primeiro ocupa os melhores dias, e ordenar aqui deixaria a
     * dependência escondida na ordem das linhas.
     */
    private void gerarCalendario(Map<Integer, Long> edicaoPorDivisao, Long temporadaId,
                                 List<ContagemPorEntidade> contagens) {
        var relatorio = calendarioService.gerarTemporada(temporadaId, propriedades.semente(),
                List.of(new EdicaoParaGerar(edicaoPorDivisao.get(1), 1, PerfisDeLiga.PRIMEIRA),
                        new EdicaoParaGerar(edicaoPorDivisao.get(2), 2, PerfisDeLiga.SEGUNDA)));
        contagens.add(new ContagemPorEntidade("rodada", relatorio.rodadas(), 0));
        contagens.add(new ContagemPorEntidade("jogo", relatorio.jogos(), 0));
    }
```

Crie `mundo/internal/PerfisDeLiga.java` com os dois perfis, ao lado de `CatalogoDeArquetipos`, onde os julgamentos de balanceamento do mundo já vivem:

```java
package br.com.api.footfirma.mundo.internal;

import br.com.api.footfirma.calendario.dto.PerfilDeCalendario;
import br.com.api.footfirma.calendario.dto.TipoDeRodada;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Em que dias cada divisão joga. Os pesos vieram da distribuição real da tabela da CBF,
 * mas são julgamento e não medição: os testes travam a forma — nada na sexta, meio de
 * semana só em quarta e quinta —, não os percentuais.
 */
final class PerfisDeLiga {

    private PerfisDeLiga() {
    }

    static final PerfilDeCalendario PRIMEIRA = new PerfilDeCalendario(Map.of(
            TipoDeRodada.FIM_DE_SEMANA, Map.of(
                    DayOfWeek.SUNDAY, 45, DayOfWeek.SATURDAY, 40, DayOfWeek.MONDAY, 15),
            TipoDeRodada.MEIO_DE_SEMANA, Map.of(
                    DayOfWeek.WEDNESDAY, 60, DayOfWeek.THURSDAY, 40)), 3);

    static final PerfilDeCalendario SEGUNDA = new PerfilDeCalendario(Map.of(
            TipoDeRodada.FIM_DE_SEMANA, Map.of(
                    DayOfWeek.SATURDAY, 40, DayOfWeek.SUNDAY, 30,
                    DayOfWeek.MONDAY, 20, DayOfWeek.TUESDAY, 10),
            TipoDeRodada.MEIO_DE_SEMANA, Map.of(
                    DayOfWeek.WEDNESDAY, 60, DayOfWeek.THURSDAY, 40)), 3);
}
```

- [ ] **Step 3: Corrigir `LimpezaDoCatalogo`**

```java
    static void executar(JdbcTemplate jdbcTemplate) {
        // Quebra a auto-referência de confronto antes do delete linear: confronto de fase
        // posterior aponta para o de fase anterior, e apagar em ordem arbitrária violaria
        // a FK. Um update evita cascade na auto-referência e mantém a lista legível.
        jdbcTemplate.update("update confronto set origem_lado_a = null, origem_lado_b = null");
        TABELAS_EM_ORDEM.forEach(tabela -> jdbcTemplate.update("delete from " + tabela));
    }
```

E `jogo`, `confronto`, `rodada` abrem `TABELAS_EM_ORDEM`, antes de `plano_escalacao`. Atualize o javadoc da classe.

- [ ] **Step 4: Rodar a suíte completa**

Run: `./gradlew test`
Expected: PASS. É aqui que a `V22` encontra as cinco classes `Mundo*Test` pela primeira vez — se `recriar=true` falhar por FK, a lista ou o `update` do Step 3 estão errados.

- [ ] **Step 5: Atualizar a documentação**

`docs/runbooks/mundo.md`: as duas linhas novas na tabela de conteúdo esperado (`rodada` 76, `jogo` 760), a seção da limpeza com as três tabelas e o `update`, e `Status: verificado em` redatado. Os dois `AGENTS.md`: o gerador agora produz calendário.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/mundo src/test/java/br/com/api/footfirma/mundo \
        docs/runbooks/mundo.md AGENTS.md ../../AGENTS.md
git commit -m "feat(mundo): gera o calendário das duas ligas"
```

---

# Slice B — grupos e eliminatória

### Task 10: `SorteioDeChaveamento`

**Files:**
- Create: `calendario/internal/SorteioDeChaveamento.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/internal/SorteioDeChaveamentoTest.java`

**Interfaces:**
- Consumes: nada
- Produces: `SorteioDeChaveamento.montar(int participantes, SplittableRandom aleatorio)` devolvendo `List<ConfrontoDoChaveamento>`, onde `ConfrontoDoChaveamento(int ordem, Integer ladoA, Integer ladoB, Integer origemA, Integer origemB)` — índices de participante quando é a primeira fase, ordens de confronto quando é fase posterior

- [ ] **Step 1: Escrever o teste**

```java
    @Test
    void deveMontarSeteConfrontosParaOitoClubes() {
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(1));
        assertThat(chave).hasSize(7);
    }

    @Test
    void devePreencherApenasAPrimeiraFaseComClubes() {
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(1));
        assertThat(chave.stream().filter(c -> c.ladoA() != null)).hasSize(4);
        assertThat(chave.stream().filter(c -> c.origemA() != null)).hasSize(3);
    }

    @Test
    void deveApontarCadaConfrontoPosteriorParaDoisAnteriores() {
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(1));
        for (var confronto : chave.stream().filter(c -> c.origemA() != null).toList()) {
            assertThat(confronto.origemA()).isLessThan(confronto.ordem());
            assertThat(confronto.origemB()).isLessThan(confronto.ordem());
            assertThat(confronto.origemA()).isNotEqualTo(confronto.origemB());
        }
    }

    @Test
    void deveUsarCadaClubeUmaVezSoNaPrimeiraFase() {
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(1));
        var usados = new HashSet<Integer>();
        chave.stream().filter(c -> c.ladoA() != null).forEach(c -> {
            assertThat(usados.add(c.ladoA())).isTrue();
            assertThat(usados.add(c.ladoB())).isTrue();
        });
        assertThat(usados).hasSize(8);
    }

    @Test
    void deveRecusarNumeroQueNaoEPotenciaDeDois() {
        assertThatThrownBy(() -> SorteioDeChaveamento.montar(6, new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRepetirOChaveamentoComAMesmaSemente() {
        assertThat(SorteioDeChaveamento.montar(8, new SplittableRandom(9)))
                .isEqualTo(SorteioDeChaveamento.montar(8, new SplittableRandom(9)));
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*SorteioDeChaveamentoTest'`
Expected: FAIL — `SorteioDeChaveamento` não existe.

- [ ] **Step 3: Implementar**

```java
package br.com.api.footfirma.calendario.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Monta a árvore inteira do mata-mata no sorteio: a primeira fase com clubes, as
 * seguintes vazias apontando para quem as alimenta.
 *
 * <p>Índices, não ids — quem traduz é o serviço, e é isso que mantém o sorteio testável
 * sem banco.
 */
final class SorteioDeChaveamento {

    private SorteioDeChaveamento() {
    }

    static List<ConfrontoDoChaveamento> montar(int participantes, SplittableRandom aleatorio) {
        if (participantes < 2 || Integer.bitCount(participantes) != 1) {
            throw new IllegalArgumentException(
                    "Participantes de eliminatória precisam ser potência de dois: " + participantes);
        }

        var sorteados = new ArrayList<Integer>(participantes);
        for (var i = 0; i < participantes; i++) {
            sorteados.add(i);
        }
        // Fisher-Yates sobre a lista ordenada: Collections.shuffle exige Random, e
        // SplittableRandom é o que dá reprodutibilidade em todo o resto do sistema.
        for (var i = participantes - 1; i > 0; i--) {
            var j = aleatorio.nextInt(i + 1);
            var troca = sorteados.get(i);
            sorteados.set(i, sorteados.get(j));
            sorteados.set(j, troca);
        }

        var chave = new ArrayList<ConfrontoDoChaveamento>(participantes - 1);
        var ordem = 1;

        // Primeira fase: pares consecutivos da lista sorteada.
        for (var i = 0; i < participantes; i += 2) {
            chave.add(new ConfrontoDoChaveamento(
                    ordem++, sorteados.get(i), sorteados.get(i + 1), null, null));
        }

        // Fases seguintes: cada confronto consome dois da fase anterior, em ordem.
        var inicioDaFaseAnterior = 1;
        var confrontosNaFaseAnterior = participantes / 2;
        while (confrontosNaFaseAnterior > 1) {
            for (var i = 0; i < confrontosNaFaseAnterior; i += 2) {
                chave.add(new ConfrontoDoChaveamento(ordem++, null, null,
                        inicioDaFaseAnterior + i, inicioDaFaseAnterior + i + 1));
            }
            inicioDaFaseAnterior += confrontosNaFaseAnterior;
            confrontosNaFaseAnterior /= 2;
        }

        return List.copyOf(chave);
    }
}
```

```java
package br.com.api.footfirma.calendario.internal;

/**
 * Um slot da árvore. {@code ladoA}/{@code ladoB} são índices de participante na primeira
 * fase; {@code origemA}/{@code origemB} são ordens de confronto nas fases seguintes.
 * Nunca os quatro ao mesmo tempo.
 */
record ConfrontoDoChaveamento(int ordem, Integer ladoA, Integer ladoB,
                              Integer origemA, Integer origemB) {
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests '*SorteioDeChaveamentoTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(calendario): sorteia o chaveamento de mata-mata"
```

---

### Task 11: Gerar grupos e eliminatória

**Files:**
- Modify: `calendario/internal/CalendarioServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/ChaveamentoTest.java`

**Interfaces:**
- Consumes: `SorteioDeChaveamento` da Task 10, `gerarFase` da Task 6
- Produces: `gerarFase` deixando de lançar para `GRUPOS` e `ELIMINATORIA`

- [ ] **Step 1: Escrever o teste**

Com Testcontainers, uma edição com uma fase `ELIMINATORIA` de 8 participantes e `jogosPorConfronto = 2`:

```java
    @Test
    void deveCriarSeteConfrontosEQuatorzeJogos() {
        assertThat(contar("confronto")).isEqualTo(7);
        assertThat(contar("jogo")).isEqualTo(14);
    }

    @Test
    void deveDeixarOsConfrontosPosterioresSemClube() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from confronto where clube_a_id is null", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void deveDeixarOsJogosDosConfrontosVaziosSemMandanteEstadioEData() {
        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from jogo j
                join confronto c on c.id = j.confronto_id
                where c.clube_a_id is null
                  and (j.mandante_id is not null or j.estadio_id is not null)
                """, Integer.class);
        assertThat(incompletos).isZero();
    }

    @Test
    void deveInverterOMandoEntreIdaEVolta() {
        var errados = jdbcTemplate.queryForObject("""
                select count(*) from jogo ida
                join jogo volta on volta.confronto_id = ida.confronto_id
                                and volta.ordem_no_confronto = 2
                where ida.ordem_no_confronto = 1
                  and ida.mandante_id is not null
                  and (ida.mandante_id <> volta.visitante_id
                    or ida.visitante_id <> volta.mandante_id)
                """, Integer.class);
        assertThat(errados).isZero();
    }
```

E, para `GRUPOS`, uma fase com 8 participantes em 2 grupos: 12 confrontos (6 por grupo, turno único), todos com clubes definidos.

Mais o teste que fecha o único método da porta pública que nenhuma outra task exercita:

```java
    @Test
    void deveExporOChaveamentoInteiroPelaPortaPublica() {
        var chaveamento = calendarioService.listarChaveamento(faseId);
        assertThat(chaveamento).hasSize(7);
        assertThat(chaveamento).extracting(ConfrontoDetalhe::ordem)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(chaveamento.stream().filter(c -> c.clubeAId() != null)).hasSize(4);
        assertThat(chaveamento.getLast().jogos()).hasSize(2);
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*ChaveamentoTest'`
Expected: FAIL com `CalendarioInvalidoException: Tipo de fase ainda não gerado: ELIMINATORIA` — a barreira que a Task 6 deixou de propósito.

- [ ] **Step 3: Implementar o ramo `ELIMINATORIA`**

Em `CalendarioServiceImpl`, no despacho de `gerarFase`:

```java
    private Contagem gerarEliminatoria(EdicaoParaGerar edicao, FaseResumo fase,
                                       JanelaDaEdicao janela, List<ParticipanteDaFase> participantes,
                                       SplittableRandom aleatorio, AgendaEmMemoria agenda) {
        var chave = SorteioDeChaveamento.montar(participantes.size(), aleatorio);

        // Uma rodada por fase da árvore: 8 clubes dão 3 rodadas (quartas, semi, final),
        // e cada uma recebe jogosPorConfronto jogos por confronto.
        var rodadasDaArvore = Integer.numberOfTrailingZeros(participantes.size());
        var tipos = TipadorDeRodadas.tipar(rodadasDaArvore * fase.jogosPorConfronto(),
                (int) ChronoUnit.DAYS.between(janela.inicio(), janela.fim()));
        var alvos = TipadorDeRodadas.datasAlvo(janela.inicio(), tipos);
        var rodadas = gravarRodadas(fase.id(), tipos, alvos);

        var confrontosPorOrdem = new HashMap<Integer, Confronto>();
        var jogos = 0;

        for (var slot : chave) {
            var confronto = new Confronto();
            confronto.setFaseId(fase.id());
            confronto.setOrdem(slot.ordem());
            if (slot.ladoA() != null) {
                confronto.setClubeAId(participantes.get(slot.ladoA()).clubeId());
                confronto.setClubeBId(participantes.get(slot.ladoB()).clubeId());
            } else {
                confronto.setOrigemLadoA(confrontosPorOrdem.get(slot.origemA()).getId());
                confronto.setOrigemLadoB(confrontosPorOrdem.get(slot.origemB()).getId());
            }
            confrontos.save(confronto);
            confrontosPorOrdem.put(slot.ordem(), confronto);

            // Todos os jogos nascem datados, inclusive os de confronto vazio: a data é
            // provisória e será realocada quando os clubes se definirem (decisão 7).
            jogos += gravarJogosDoConfronto(confronto, fase, rodadas, edicao, aleatorio, agenda);
        }
        return new Contagem(rodadas.size(), chave.size(), jogos);
    }
```

`gravarJogosDoConfronto` cria `fase.jogosPorConfronto()` jogos. Quando o confronto tem clubes, preenche mandante, visitante e estádio e aloca a data pela regra de descanso; quando não tem, deixa os três nulos e grava só a data provisória do alvo da rodada. No jogo de volta, mandante e visitante invertem.

- [ ] **Step 4: Implementar o ramo `GRUPOS`**

```java
    private Contagem gerarGrupos(EdicaoParaGerar edicao, FaseResumo fase,
                                 JanelaDaEdicao janela, List<ParticipanteDaFase> participantes, int grupos,
                                 SplittableRandom aleatorio, AgendaEmMemoria agenda) {
        var porGrupo = participantes.size() / grupos;
        if (participantes.size() % grupos != 0) {
            throw new CalendarioInvalidoException(
                    "%d participantes não dividem em %d grupos".formatted(participantes.size(), grupos));
        }

        var sorteados = embaralhar(participantes, aleatorio);
        var tabela = TabelaDeBerger.gerar(porGrupo, fase.jogosPorConfronto() == 2);
        var tipos = TipadorDeRodadas.tipar(tabela.size(),
                (int) ChronoUnit.DAYS.between(janela.inicio(), janela.fim()));
        var alvos = TipadorDeRodadas.datasAlvo(janela.inicio(), tipos);

        // As rodadas são compartilhadas entre os grupos: na rodada 1, todos os grupos
        // jogam. Gravá-las por grupo daria 2x mais rodadas do que a fase tem.
        var rodadas = gravarRodadas(fase.id(), tipos, alvos);

        var confrontos = 0;
        var jogos = 0;
        for (var grupo = 0; grupo < grupos; grupo++) {
            var doGrupo = sorteados.subList(grupo * porGrupo, (grupo + 1) * porGrupo);
            var chave = Character.toString('A' + grupo);
            for (var r = 0; r < tabela.size(); r++) {
                for (var par : tabela.get(r)) {
                    gravarConfrontoSimples(fase, rodadas.get(r), chave,
                            doGrupo.get(par.mandante()), doGrupo.get(par.visitante()),
                            edicao, aleatorio, agenda);
                    confrontos++;
                    jogos++;
                }
            }
        }
        return new Contagem(rodadas.size(), confrontos, jogos);
    }
```

O número de grupos não está em `Fase` — use `participantes.size() / 4` como padrão e registre isso no javadoc. Quando houver competição real com grupos, ele vira coluna e esta linha some.

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests '*ChaveamentoTest'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(calendario): gera fases de grupos e de eliminatória"
```

---

### Task 12: Propagação e realocação

**Files:**
- Modify: `calendario/internal/CalendarioServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/calendario/EliminatoriaIntegridadeTest.java`

**Interfaces:**
- Consumes: Tasks 7, 10 e 11
- Produces: `registrarResultado` propagando o vencedor e realocando as datas do confronto que se completa

- [ ] **Step 1: Escrever o teste de integridade da eliminatória**

Este é o teste que sustenta o slice inteiro. Ele joga a chave de 8 clubes até o fim:

```java
    @Test
    void devePropagarAteAFinalEDarUmCampeao() {
        disputarTodaAChave();   // registra resultado de todos os jogos, fase por fase

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from confronto where vencedor_clube_id is null",
                Integer.class)).isZero();

        var final_ = confrontoDeMaiorOrdem();
        assertThat(final_.vencedorClubeId()).isNotNull();
    }

    @Test
    void devePreencherOsClubesDoConfrontoSeguinteAoResolverOAnterior() {
        disputarPrimeiraFase();
        var semifinais = jdbcTemplate.queryForObject(
                "select count(*) from confronto where origem_lado_a is not null and clube_a_id is not null",
                Integer.class);
        assertThat(semifinais).isEqualTo(2);
    }

    @Test
    void deveDarMandanteEstadioEDataAoJogoQuandoOConfrontoSeCompleta() {
        disputarPrimeiraFase();
        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from jogo j
                join confronto c on c.id = j.confronto_id
                where c.clube_a_id is not null and c.clube_b_id is not null
                  and (j.mandante_id is null or j.estadio_id is null or j.data_jogo is null)
                """, Integer.class);
        assertThat(incompletos).isZero();
    }

    @Test
    void deveRealocarADataQuandoOClubeClassificadoJogouRecente() {
        disputarPrimeiraFase();
        var violacoes = jdbcTemplate.queryForObject("""
                with agenda as (
                    select mandante_id as clube_id, data_jogo from jogo where data_jogo is not null
                    union all
                    select visitante_id, data_jogo from jogo where data_jogo is not null
                ),
                consecutivos as (
                    select clube_id, data_jogo,
                           lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
                    from agenda
                )
                select count(*) from consecutivos
                where anterior is not null and data_jogo - anterior < 3
                """, Integer.class);
        assertThat(violacoes).isZero();
    }

    @Test
    void deveRecusarResultadoDeJogoSemClubesDefinidos() {
        assertThatThrownBy(() -> calendarioService.registrarResultado(
                jogoDeSemifinalNaoResolvida, new ResultadoDoJogo(1, 0, null, null, null, null)))
                .isInstanceOf(ResultadoInvalidoException.class)
                .hasMessageContaining("clubes");
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*EliminatoriaIntegridadeTest'`
Expected: FAIL — o vencedor é gravado no próprio confronto desde a Task 7, mas nada o propaga: as semifinais continuam com `clube_a_id` nulo.

- [ ] **Step 3: Implementar a propagação**

Em `CalendarioServiceImpl`, chamado ao fim de `registrarResultado` quando o confronto resolve:

```java
    /**
     * Leva o vencedor aos confrontos que o esperam e, quando um deles fecha os dois
     * lados, materializa seus jogos: mandante, visitante, estádio e data.
     */
    private void propagar(Confronto resolvido) {
        var dependentes = confrontos.findByOrigemLadoAOrOrigemLadoB(
                resolvido.getId(), resolvido.getId());

        for (var dependente : dependentes) {
            if (resolvido.getId().equals(dependente.getOrigemLadoA())) {
                dependente.setClubeAId(resolvido.getVencedorClubeId());
            } else {
                dependente.setClubeBId(resolvido.getVencedorClubeId());
            }
            confrontos.save(dependente);

            if (dependente.getClubeAId() != null && dependente.getClubeBId() != null) {
                materializar(dependente);
            }
        }
    }

    /**
     * Dá clubes, estádio e data definitiva aos jogos de um confronto que acabou de fechar.
     *
     * <p>A data marcada no sorteio foi decidida sem saber quem jogaria — ou seja, o
     * descanso nunca foi verificado para estes dois clubes. Realocar aqui é o que mantém
     * a invariante válida para todo jogo do banco, sem exceção (decisão 7).
     */
    private void materializar(Confronto confronto) {
        var fase = faseDe(confronto.getFaseId());
        var perfil = perfilDe(confronto.getFaseId());
        var aleatorio = new SplittableRandom(confronto.getId());

        var ordem = 1;
        for (var jogo : jogos.findByConfrontoIdOrderByOrdemNoConfrontoAsc(confronto.getId())) {
            // Ida com A em casa, volta invertida.
            var mandanteId = ordem == 1 ? confronto.getClubeAId() : confronto.getClubeBId();
            var visitanteId = ordem == 1 ? confronto.getClubeBId() : confronto.getClubeAId();

            jogo.setMandanteId(mandanteId);
            jogo.setVisitanteId(visitanteId);
            jogo.setEstadioId(clubeService.buscarEstadioPorClubeId(mandanteId).orElseThrow());

            var rodada = jogo.getRodada();
            jogo.setDataJogo(AlocadorDeDatas.alocar(
                    rodada.getJanelaInicio(), rodada.getJanelaFim(),
                    SorteadorDeDia.ordenarPorPeso(perfil.pesos().get(rodada.getTipo()), aleatorio),
                    datasDoClube(mandanteId, confronto.getFaseId()),
                    datasDoClube(visitanteId, confronto.getFaseId()),
                    perfil.descansoMinimoEmDias()));

            jogos.save(jogo);
            ordem++;
        }
    }
```

O perfil não está persistido — é entrada de geração (decisão 6). Para a realocação, o módulo precisa de um perfil e não pode inventá-lo: use `PerfilDeCalendario` padrão declarado em `ConstantesDeCalendario` (`FIM_DE_SEMANA` com domingo e sábado, `MEIO_DE_SEMANA` com quarta e quinta, descanso 3). Registre no javadoc de `perfilDe` que é o padrão de realocação, e não o perfil da competição — se um dia isso incomodar, é sinal de que o perfil precisa ser persistido, e aí a decisão 6 se reabre com caso concreto.

Acrescente também a validação que o teste `deveRecusarResultadoDeJogoSemClubesDefinidos` exige, em `registrarResultado`:

```java
        if (jogo.getMandanteId() == null || jogo.getVisitanteId() == null) {
            throw new ResultadoInvalidoException(
                    "Jogo %d ainda não tem os dois clubes definidos".formatted(jogoId));
        }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests '*EliminatoriaIntegridadeTest'`
Expected: PASS. Se `deveRealocarADataQuandoOClubeClassificadoJogouRecente` falhar, `datasDoClube` não está incluindo os jogos da fase anterior — **corrija a consulta, não o teste**.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(calendario): propaga o chaveamento e realoca as datas"
```

---

### Task 13: Determinismo, suíte e documentação

**Files:**
- Test: `src/test/java/br/com/api/footfirma/calendario/CalendarioDeterminismoTest.java`
- Create: `docs/runbooks/calendario.md`

- [ ] **Step 1: Escrever o teste de determinismo**

Gerar duas vezes com a mesma semente e comparar o calendário inteiro — o mesmo que `MundoDeterminismoTest` faz para clubes e elencos.

```java
package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CalendarioDeterminismoTest {

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    CalendarioFactory factory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveProduzirOMesmoCalendarioComAMesmaSemente() {
        var primeira = gerarEAssinar("determinismo-a");
        var segunda = gerarEAssinar("determinismo-b");
        assertThat(segunda).isEqualTo(primeira);
    }

    @Test
    void deveProduzirCalendarioDiferenteComSementeDiferente() {
        var comQuarentaEDois = gerarEAssinar("semente-42", 42L);
        var comSete = gerarEAssinar("semente-7", 7L);
        assertThat(comSete).isNotEqualTo(comQuarentaEDois);
    }

    private List<String> gerarEAssinar(String slug) {
        return gerarEAssinar(slug, 42L);
    }

    /**
     * Duas edições distintas com a mesma semente relativa: a semente efetiva é
     * semente + edicaoId, então some o id da edição da assinatura para poder comparar.
     */
    private List<String> gerarEAssinar(String slug, long semente) {
        var cenario = factory.criar(slug, 8, "PONTOS_CORRIDOS", 2);
        calendarioService.gerarTemporada(cenario.temporadaId(), semente - cenario.edicaoId(),
                List.of(new EdicaoParaGerar(cenario.edicaoId(), 1, GeracaoDePontosCorridosTest.PERFIL)));

        // Assina por posição relativa, não por id: os ids diferem entre as duas gerações.
        return jdbcTemplate.queryForList("""
                select c.ordem || '|' ||
                       (select count(*) from clube x where x.id < j.mandante_id
                          and x.slug like ? ) || '|' ||
                       (select count(*) from clube x where x.id < j.visitante_id
                          and x.slug like ? ) || '|' ||
                       (j.data_jogo - ?::date)
                from jogo j join confronto c on c.id = j.confronto_id
                where c.fase_id = ?
                order by c.ordem
                """, String.class, slug + "%", slug + "%", "2026-04-04", cenario.faseId());
    }
}
```

Se a assinatura por posição relativa ficar frágil, troque por uma comparação direta de `(ordem do confronto, offset de dias)` — o que precisa ser provado é que a **estrutura** se repete, não que os ids se repetem.

- [ ] **Step 2: Rodar a suíte completa**

Run: `./gradlew test`
Expected: PASS, com `ModularidadeTest` verde — `calendario` não pode importar tipo de subpacote interno de outro módulo, e `mundo → calendario` precisa passar pela porta pública.

- [ ] **Step 3: Escrever o runbook**

`docs/runbooks/calendario.md`, no padrão obrigatório de `docs/README.md`: `Status: verificado em AAAA-MM-DD`, `## Fontes` com caminhos reais e um comando de validação. Cobre: o que o módulo gera, como a precedência decide a ordem, por que a data de mata-mata é provisória, e a query de conferência de descanso.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/br/com/api/footfirma/calendario/CalendarioDeterminismoTest.java \
        docs/runbooks/calendario.md
git commit -m "test(calendario): prova o determinismo e fecha o módulo"
```

---

## Verificação final

Antes de declarar o plano concluído, percorra `.rules/java-checklist.md` e produza o registro de fechamento que ele exige: arquivos alterados, validações executadas com o comando e o resultado real, validações não executadas e por quê, e alterações preexistentes no worktree.

Duas coisas que este plano **não** entrega e que não devem ser confundidas com pendência:

- **Tabela de classificação.** `RegraClassificacao` continua sem consumidor. Ordenar clubes por pontos é outro spec.
- **Copa no mundo gerado.** Grupos e eliminatória ficam exercitados só por teste até que uma copa seja acrescentada a `MundoServiceImpl`. É o risco 2 do spec, assumido de propósito.

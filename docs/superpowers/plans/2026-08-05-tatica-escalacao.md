# Tática e escalação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o módulo Spring Modulith `tatica` — plano tático vigente por clube e temporada, versionado a cada alteração, com formação, cinco instruções coletivas, onze titulares, banco e capitão, mais um escalador automático determinístico que atende os clubes de IA e cobre o humano que não escalou.

**Architecture:** Núcleo de funções puras em `internal/` (sem Spring, sem JPA, sem relógio) que recebem elenco e overalls e devolvem um onze. Uma porta pública `TaticaService` que é o único caminho de escrita, porque as invariantes da escalação são de agregado e nenhum `check` de linha as enxerga. Três portas públicas de outros módulos ganham um método cada para alimentar o escalador.

**Tech Stack:** Java 25 · Spring Boot 4 · Spring Modulith · PostgreSQL via Flyway · JPA · Lombok · JUnit 5 + AssertJ + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-05-tatica-escalacao-design.md`

## Global Constraints

- Pacote base `br.com.api.footfirma`; um módulo por pacote direto abaixo dele.
- Tipos no pacote raiz do módulo são API pública; subpacotes são internos **salvo** se o `package-info.java` do subpacote declarar `@org.springframework.modulith.NamedInterface("dto")`. `jogador/dto` e `avaliacao/dto` já declaram; `treinador/dto` **não**.
- Injeção **somente por construtor**, via `@RequiredArgsConstructor`. `@Autowired` em campo é proibido.
- `@Transactional(readOnly = true)` na classe do service, `@Transactional` no método que escreve.
- Lombok em `@Entity`: apenas `@Getter`/`@Setter`. `@Data`, `@EqualsAndHashCode` e `@ToString` são proibidos; `equals`/`hashCode` seguem `clube/domain/Clube.java` (compara `id`, `hashCode` devolve `Classe.class.hashCode()`).
- Migrations aplicadas são **imutáveis**; o guard bloqueia edição. Erro em migration versionada se corrige com migration nova. As próximas livres são `V20` e `V21` — a última aplicada é `V19`.
- Testes de persistência usam Testcontainers. H2, banco em memória e mock de `Repository` são proibidos.
- `@DataJpaTest` exige `@Import(TestcontainersConfiguration.class)` **e** `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`.
- Nomes de teste em português: `deve<Comportamento>Quando<Condição>`. Asserções com AssertJ.
- Commits em pt-BR, formato `<tipo>(tatica): <descrição imperativa>`, máximo 72 caracteres na primeira linha, **nunca** `Co-authored-by`.
- Rode `./gradlew compileJava` durante o trabalho e `./gradlew test --tests '*<Alvo>Test'` para o teste da tarefa. **Não** rode a suíte completa a cada passo — só na Task 10.
- Não suba serviços (`bootRun`, `docker compose up`) — o guard bloqueia. Testcontainers sobe sozinho no `test`.
- Trabalhe a partir de `backend/footfirma/`. Todos os caminhos deste plano são relativos a esse diretório, exceto os de `docs/`.
- Branch atual: `feat/modulo-tatica`. Não faça `git push` — o guard bloqueia.

## File Structure

```
src/main/java/br/com/api/footfirma/
├── treinador/
│   ├── PerfilDeTreinador.java              # MODIFICAR módulo: record novo no pacote raiz
│   └── TreinadorService.java               # MODIFICAR: + buscarPerfilDoClube
├── jogador/
│   ├── dto/JogadorDoElenco.java            # MODIFICAR módulo: record novo
│   └── JogadorService.java                 # MODIFICAR: + listarElencoParaEscalacao
├── avaliacao/
│   ├── dto/OverallDeJogador.java           # MODIFICAR módulo: record novo
│   └── AvaliacaoService.java               # MODIFICAR: + listarOveralls
├── shared/exception/
│   ├── EscalacaoInvalidaException.java     # criar
│   └── TratadorDeErros.java                # MODIFICAR: + handler 422
└── tatica/
    ├── package-info.java                   # @ApplicationModule(displayName = "Tática")
    ├── TaticaService.java                  # porta pública única
    ├── dto/
    │   ├── package-info.java               # @NamedInterface("dto")
    │   ├── Mentalidade · Ritmo · LinhaDefensiva · Pressao · Largura   # enums do contrato
    │   ├── OrigemDoPlano · Papel
    │   ├── NovoPlano · TitularEscalado     # entrada
    │   └── PlanoVigente · Escalado         # saída
    ├── domain/    Formacao · FormacaoSlot · PlanoTatico · PlanoEscalacao
    │              PlanoEscalacaoId
    ├── repository/ FormacaoRepository · FormacaoSlotRepository
    │              PlanoTaticoRepository · PlanoEscalacaoRepository
    └── internal/  ConstantesDeTatica · TabelaDeOveralls · JogadorDisponivel
                   FormacaoCandidata · SlotPreenchido · Onze
                   PreenchedorDeSlots · EscolhedorDeFormacao · EscolhedorDeInstrucoes
                   ValidadorDeEscalacao · EscaladorAutomatico · TaticaServiceImpl

src/main/resources/db/migration/
├── V20__cria_tatica.sql
└── V21__popula_formacao.sql

src/test/java/br/com/api/footfirma/tatica/
├── PlanoFactory.java
├── CatalogoDeFormacaoTest.java · PlanoIntegridadeTest.java
├── EscalacaoInvalidaTest.java · VersionamentoTest.java
├── AptidaoCongeladaTest.java · EscaladorDeterministicoTest.java
└── internal/ PreenchimentoDeSlotsTest.java · EscolhaDeFormacaoTest.java
             InstrucoesDaIaTest.java
```

**Por que os enums vivem em `dto/` e não em `domain/`:** eles são o contrato — a partida vai ler `Mentalidade`. Se ficassem em `domain/`, publicá-los exigiria `@NamedInterface` em `domain/`, o que exporia junto as quatro entidades JPA. É exatamente a armadilha em que `treinador` caiu com `Skill`, e que a Task 2 contorna em vez de repetir.

---

### Task 1: Catálogo de formações — schema, entidades e seed

**Files:**
- Create: `src/main/resources/db/migration/V20__cria_tatica.sql`
- Create: `src/main/resources/db/migration/V21__popula_formacao.sql`
- Create: `src/main/java/br/com/api/footfirma/tatica/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/tatica/dto/package-info.java`
- Create: `tatica/dto/{Mentalidade,Ritmo,LinhaDefensiva,Pressao,Largura,OrigemDoPlano,Papel}.java`
- Create: `tatica/domain/{Formacao,FormacaoSlot}.java`
- Create: `tatica/repository/{FormacaoRepository,FormacaoSlotRepository}.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/CatalogoDeFormacaoTest.java`

**Interfaces:**
- Consumes: nada (primeira tarefa)
- Produces: `Formacao` (getters `getId`, `getCodigo`, `getNome`, `getOrdem`), `FormacaoSlot` (`getFormacaoId`, `getOrdem`, `getPosicaoId`), `FormacaoRepository.findAllByOrderByOrdemAsc()`, `FormacaoSlotRepository.findByFormacaoIdOrderByOrdemAsc(Long)`, e os sete enums de `tatica.dto`

- [ ] **Step 1: Escrever a migration de schema**

Crie `V20__cria_tatica.sql` copiando **integralmente** os três blocos `create table` da seção "Schema — migrations `V20` e `V21`" do spec, com todos os `comment on column` e todos os índices. Confira três coisas que a revisão do spec fixou:

- `plano_escalacao.aptidao_no_momento` é `integer`, **não** `numeric(4,2)`
- `plano_escalacao.posicao_id` existe e é `not null references posicao (id)`
- existem **dois** índices parciais de unicidade em `plano_escalacao`:

```sql
create unique index uq_escalacao_slot  on plano_escalacao (plano_id, slot_ordem)  where slot_ordem  is not null;
create unique index uq_escalacao_banco on plano_escalacao (plano_id, ordem_banco) where ordem_banco is not null;
```

e um em `plano_tatico`:

```sql
create unique index uq_plano_vigente on plano_tatico (clube_id, temporada_id) where vigente;
```

- [ ] **Step 2: Escrever a migration de seed**

```sql
-- Seis formações. A ordem é o repertório: EscolhedorDeFormacao avalia as
-- 1 + TATICA/2 primeiras, então a de ordem 1 precisa ser a mais genérica.
insert into formacao (codigo, nome, ordem) values
    ('4-4-2',   'Quatro-quatro-dois',        1),
    ('4-3-3',   'Quatro-três-três',          2),
    ('4-2-3-1', 'Quatro-dois-três-um',       3),
    ('3-5-2',   'Três-cinco-dois',           4),
    ('5-3-2',   'Cinco-três-dois',           5),
    ('4-1-4-1', 'Quatro-um-quatro-um',       6);

insert into formacao_slot (formacao_id, ordem, posicao_id)
select f.id, s.ordem, p.id
from (values
    ('4-4-2',    1, 'GOL'), ('4-4-2',    2, 'ZAG'), ('4-4-2',    3, 'ZAG'),
    ('4-4-2',    4, 'LTD'), ('4-4-2',    5, 'LTE'), ('4-4-2',    6, 'VOL'),
    ('4-4-2',    7, 'MEC'), ('4-4-2',    8, 'PTA'), ('4-4-2',    9, 'PTA'),
    ('4-4-2',   10, 'ATA'), ('4-4-2',   11, 'ATA'),

    ('4-3-3',    1, 'GOL'), ('4-3-3',    2, 'ZAG'), ('4-3-3',    3, 'ZAG'),
    ('4-3-3',    4, 'LTD'), ('4-3-3',    5, 'LTE'), ('4-3-3',    6, 'VOL'),
    ('4-3-3',    7, 'MEC'), ('4-3-3',    8, 'MEC'), ('4-3-3',    9, 'PTA'),
    ('4-3-3',   10, 'PTA'), ('4-3-3',   11, 'ATA'),

    ('4-2-3-1',  1, 'GOL'), ('4-2-3-1',  2, 'ZAG'), ('4-2-3-1',  3, 'ZAG'),
    ('4-2-3-1',  4, 'LTD'), ('4-2-3-1',  5, 'LTE'), ('4-2-3-1',  6, 'VOL'),
    ('4-2-3-1',  7, 'VOL'), ('4-2-3-1',  8, 'MEA'), ('4-2-3-1',  9, 'PTA'),
    ('4-2-3-1', 10, 'PTA'), ('4-2-3-1', 11, 'ATA'),

    ('3-5-2',    1, 'GOL'), ('3-5-2',    2, 'ZAG'), ('3-5-2',    3, 'ZAG'),
    ('3-5-2',    4, 'ZAG'), ('3-5-2',    5, 'LTD'), ('3-5-2',    6, 'LTE'),
    ('3-5-2',    7, 'VOL'), ('3-5-2',    8, 'MEC'), ('3-5-2',    9, 'MEA'),
    ('3-5-2',   10, 'ATA'), ('3-5-2',   11, 'ATA'),

    ('5-3-2',    1, 'GOL'), ('5-3-2',    2, 'ZAG'), ('5-3-2',    3, 'ZAG'),
    ('5-3-2',    4, 'ZAG'), ('5-3-2',    5, 'LTD'), ('5-3-2',    6, 'LTE'),
    ('5-3-2',    7, 'VOL'), ('5-3-2',    8, 'MEC'), ('5-3-2',    9, 'MEC'),
    ('5-3-2',   10, 'ATA'), ('5-3-2',   11, 'ATA'),

    ('4-1-4-1',  1, 'GOL'), ('4-1-4-1',  2, 'ZAG'), ('4-1-4-1',  3, 'ZAG'),
    ('4-1-4-1',  4, 'LTD'), ('4-1-4-1',  5, 'LTE'), ('4-1-4-1',  6, 'VOL'),
    ('4-1-4-1',  7, 'MEC'), ('4-1-4-1',  8, 'MEC'), ('4-1-4-1',  9, 'PTA'),
    ('4-1-4-1', 10, 'PTA'), ('4-1-4-1', 11, 'ATA')
) as s(codigo, ordem, posicao)
join formacao f on f.codigo = s.codigo
join posicao  p on p.codigo = s.posicao;
```

- [ ] **Step 3: Escrever o teste do catálogo (vai falhar — não há entidade)**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.tatica.repository.FormacaoRepository;
import br.com.api.footfirma.tatica.repository.FormacaoSlotRepository;
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
class CatalogoDeFormacaoTest {

    @Autowired
    FormacaoRepository formacoes;

    @Autowired
    FormacaoSlotRepository slots;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveSeedarSeisFormacoesEmOrdemContinua() {
        var todas = formacoes.findAllByOrderByOrdemAsc();

        assertThat(todas).hasSize(6);
        assertThat(todas).extracting("ordem").containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(todas.getFirst().getCodigo())
                .as("a de ordem 1 alimenta o repertório mínimo e precisa ser a mais genérica")
                .isEqualTo("4-4-2");
    }

    @Test
    void deveDarOnzeSlotsEmOrdemContinuaParaCadaFormacao() {
        for (var formacao : formacoes.findAllByOrderByOrdemAsc()) {
            var doJogo = slots.findByFormacaoIdOrderByOrdemAsc(formacao.getId());

            assertThat(doJogo)
                    .as("formação %s", formacao.getCodigo())
                    .hasSize(11)
                    .extracting("ordem")
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        }
    }

    @Test
    void deveDarExatamenteUmGoleiroPorFormacao() {
        var porFormacao = jdbcTemplate.queryForList("""
                select f.codigo, count(*) as goleiros
                from formacao f
                join formacao_slot s on s.formacao_id = f.id
                join posicao p       on p.id = s.posicao_id
                where p.codigo = 'GOL'
                group by f.codigo
                """);

        assertThat(porFormacao).hasSize(6);
        assertThat(porFormacao).allSatisfy(linha ->
                assertThat(linha.get("goleiros")).isEqualTo(1L));
    }
}
```

- [ ] **Step 4: Rodar o teste para confirmar que falha**

Run: `./gradlew test --tests '*CatalogoDeFormacaoTest'`
Expected: FAIL na compilação — `FormacaoRepository` não existe.

- [ ] **Step 5: Criar os `package-info.java` e os sete enums**

```java
// tatica/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Tática")
package br.com.api.footfirma.tatica;
```

```java
// tatica/dto/package-info.java
// Os records e enums deste pacote são o contrato do módulo: TaticaService os devolve e
// o módulo partida vai consumi-los. Sem @NamedInterface o Modulith trata o subpacote
// como interno — é o que hoje impede treinador.dto de atravessar a fronteira.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.tatica.dto;
```

```java
// tatica/dto/Mentalidade.java
package br.com.api.footfirma.tatica.dto;

public enum Mentalidade { MUITO_DEFENSIVA, DEFENSIVA, EQUILIBRADA, OFENSIVA, MUITO_OFENSIVA }
```

```java
// tatica/dto/Ritmo.java
package br.com.api.footfirma.tatica.dto;

public enum Ritmo { LENTO, EQUILIBRADO, INTENSO }
```

```java
// tatica/dto/LinhaDefensiva.java
package br.com.api.footfirma.tatica.dto;

public enum LinhaDefensiva { RECUADA, MEDIA, ADIANTADA }
```

```java
// tatica/dto/Pressao.java
package br.com.api.footfirma.tatica.dto;

public enum Pressao { BAIXA, MEDIA, ALTA }
```

```java
// tatica/dto/Largura.java
package br.com.api.footfirma.tatica.dto;

public enum Largura { ESTREITA, MEDIA, ABERTA }
```

```java
// tatica/dto/OrigemDoPlano.java
package br.com.api.footfirma.tatica.dto;

public enum OrigemDoPlano { MANUAL, AUTOMATICO }
```

```java
// tatica/dto/Papel.java
package br.com.api.footfirma.tatica.dto;

public enum Papel { TITULAR, RESERVA }
```

- [ ] **Step 6: Criar as entidades do catálogo**

```java
// tatica/domain/Formacao.java
package br.com.api.footfirma.tatica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "formacao")
@Getter
@Setter
public class Formacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private Integer ordem;

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof Formacao outra && id != null && id.equals(outra.id);
    }

    @Override
    public int hashCode() {
        return Formacao.class.hashCode();
    }
}
```

```java
// tatica/domain/FormacaoSlot.java
package br.com.api.footfirma.tatica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "formacao_slot")
@IdClass(FormacaoSlot.Chave.class)
@Getter
@Setter
public class FormacaoSlot {

    @Id
    @Column(name = "formacao_id")
    private Long formacaoId;

    @Id
    private Integer ordem;

    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    public record Chave(Long formacaoId, Integer ordem) implements Serializable {
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof FormacaoSlot outra
                && Objects.equals(formacaoId, outra.formacaoId)
                && Objects.equals(ordem, outra.ordem);
    }

    @Override
    public int hashCode() {
        return FormacaoSlot.class.hashCode();
    }
}
```

- [ ] **Step 7: Criar os repositories**

```java
// tatica/repository/FormacaoRepository.java
package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.Formacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormacaoRepository extends JpaRepository<Formacao, Long> {

    List<Formacao> findAllByOrderByOrdemAsc();
}
```

```java
// tatica/repository/FormacaoSlotRepository.java
package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.FormacaoSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormacaoSlotRepository
        extends JpaRepository<FormacaoSlot, FormacaoSlot.Chave> {

    List<FormacaoSlot> findByFormacaoIdOrderByOrdemAsc(Long formacaoId);
}
```

- [ ] **Step 8: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*CatalogoDeFormacaoTest'`
Expected: PASS, três testes.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/V20__cria_tatica.sql \
        src/main/resources/db/migration/V21__popula_formacao.sql \
        src/main/java/br/com/api/footfirma/tatica \
        src/test/java/br/com/api/footfirma/tatica/CatalogoDeFormacaoTest.java
git commit -m "feat(tatica): cria schema e seed do catálogo de formações"
```

---

### Task 2: As três portas dos outros módulos

**Files:**
- Create: `src/main/java/br/com/api/footfirma/treinador/PerfilDeTreinador.java`
- Modify: `src/main/java/br/com/api/footfirma/treinador/TreinadorService.java`
- Modify: `src/main/java/br/com/api/footfirma/treinador/internal/TreinadorServiceImpl.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/JogadorDoElenco.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/internal/JogadorServiceImpl.java`
- Create: `src/main/java/br/com/api/footfirma/avaliacao/dto/OverallDeJogador.java`
- Modify: `src/main/java/br/com/api/footfirma/avaliacao/AvaliacaoService.java`
- Modify: `src/main/java/br/com/api/footfirma/avaliacao/internal/AvaliacaoServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/PortasExternasTest.java`

**Interfaces:**
- Consumes: nada da Task 1
- Produces: `PerfilDeTreinador(long treinadorId, int reputacao, int tatica)`; `TreinadorService.buscarPerfilDoClube(long, long) -> Optional<PerfilDeTreinador>`; `JogadorDoElenco(Long jogadorId, Long posicaoPrincipalId, String categoria, Integer numeroCamisa)`; `JogadorService.listarElencoParaEscalacao(long, long) -> List<JogadorDoElenco>`; `OverallDeJogador(Long jogadorId, Long posicaoId, Integer overall)`; `AvaliacaoService.listarOveralls(Collection<Long>, long) -> List<OverallDeJogador>`

Esta tarefa não toca em `tatica`. Ela existe separada porque um revisor pode aprovar as três portas e rejeitar o escalador, ou o contrário.

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.treinador.TreinadorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PortasExternasTest {

    @Autowired TreinadorService treinadores;
    @Autowired JogadorService jogadores;
    @Autowired AvaliacaoService avaliacoes;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deveDarOPerfilDoTreinadorQueDirigeOClube() {
        var cenario = CenarioDeElenco.montar(jdbc);

        var perfil = treinadores.buscarPerfilDoClube(cenario.clubeId(), cenario.temporadaId());

        assertThat(perfil).isPresent();
        assertThat(perfil.get().tatica()).isEqualTo(cenario.taticaDoTreinador());
        assertThat(perfil.get().reputacao()).isEqualTo(cenario.reputacaoDoTreinador());
    }

    @Test
    void deveVoltarVazioQuandoOClubeNaoTemTreinadorAtivo() {
        var cenario = CenarioDeElenco.montar(jdbc);

        assertThat(treinadores.buscarPerfilDoClube(cenario.clubeSemTreinadorId(),
                cenario.temporadaId())).isEmpty();
    }

    @Test
    void deveListarOElencoComIdsCategoriaEPosicaoPrincipal() {
        var cenario = CenarioDeElenco.montar(jdbc);

        var elenco = jogadores.listarElencoParaEscalacao(cenario.clubeId(), cenario.temporadaId());

        assertThat(elenco).hasSize(cenario.tamanhoDoElenco());
        assertThat(elenco).allSatisfy(linha -> {
            assertThat(linha.jogadorId()).isNotNull();
            assertThat(linha.posicaoPrincipalId()).isNotNull();
            assertThat(linha.categoria()).isIn("PROFISSIONAL", "BASE");
        });
        assertThat(elenco).extracting("categoria").contains("BASE");
    }

    @Test
    void deveListarOveralsDeVariosJogadoresEmTodasAsPosicoes() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var ids = List.of(cenario.primeiroJogadorId(), cenario.segundoJogadorId());

        var overalls = avaliacoes.listarOveralls(ids, cenario.temporadaId());

        assertThat(overalls).hasSize(2 * 9);
        assertThat(overalls).extracting("jogadorId").containsOnly(ids.toArray());
        assertThat(overalls).allSatisfy(linha ->
                assertThat(linha.overall()).isBetween(0, 99));
    }

    @Test
    void deveVoltarListaVaziaQuandoNaoHaJogadorNenhum() {
        var cenario = CenarioDeElenco.montar(jdbc);

        assertThat(avaliacoes.listarOveralls(List.of(), cenario.temporadaId())).isEmpty();
    }
}
```

`CenarioDeElenco` é o helper do Step 2. Ele existe para que este teste e os das Tasks 7-10 montem o mesmo mundo mínimo sem duplicar SQL.

- [ ] **Step 2: Escrever o helper de cenário**

Crie `src/test/java/br/com/api/footfirma/tatica/CenarioDeElenco.java`. Ele insere via `JdbcTemplate` — não via repository, porque precisa escrever em tabelas de módulos cujos repositories são internos.

```java
package br.com.api.footfirma.tatica;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Mundo mínimo para os testes de escalação: uma temporada, dois clubes, um treinador
 * vinculado ao primeiro, um elenco de 20 profissionais mais 2 da base, e o overall
 * materializado nas nove posições para todos eles.
 *
 * <p>Escreve por JDBC porque os repositories de jogador, clube e avaliacao são
 * internos aos seus módulos — o teste vive em tatica.
 */
record CenarioDeElenco(long temporadaId, long clubeId, long clubeSemTreinadorId,
                       long primeiroJogadorId, long segundoJogadorId,
                       int tamanhoDoElenco, int taticaDoTreinador, int reputacaoDoTreinador,
                       List<Long> posicoesEmOrdem) {

    static final int TAMANHO_DO_ELENCO = 22;
    static final int TATICA = 6;
    static final int REPUTACAO = 70;

    static CenarioDeElenco montar(JdbcTemplate jdbc) {
        limpar(jdbc);

        var temporadaId = jdbc.queryForObject("""
                insert into temporada (label, ano_inicio, ano_fim, atual)
                values ('2026', 2026, 2026, true) returning id
                """, Long.class);

        var paisId = jdbc.queryForObject(
                "select id from pais where codigo_iso3 = 'BRA'", Long.class);

        var clubeId = inserirClube(jdbc, "clube-um", "Clube Um", paisId, 70);
        var clubeSemTreinadorId = inserirClube(jdbc, "clube-dois", "Clube Dois", paisId, 40);

        var treinadorId = jdbc.queryForObject("""
                insert into treinador (slug, nome_completo, nome_exibicao, data_nascimento,
                                       pais_id, tipo, reputacao, pontos_disponiveis, semente)
                values ('tec-um', 'Técnico Um', 'Um', date '1975-03-11', ?, 'IA', ?, 0, 1)
                returning id
                """, Long.class, paisId, REPUTACAO);

        jdbc.update("""
                insert into vinculo_treinador (treinador_id, clube_id, temporada_id,
                                               inicio, moral, meta_posicao)
                values (?, ?, ?, date '2026-01-01', 50.00, 5)
                """, treinadorId, clubeId, temporadaId);

        jdbc.update("""
                insert into treinador_skill (treinador_id, temporada_id, skill, valor)
                values (?, ?, 'TATICA', ?)
                """, treinadorId, temporadaId, TATICA);
        for (var skill : List.of("VISAO_DE_JOGO", "PRELECAO", "LIDERANCA", "TREINAMENTO", "NEGOCIACAO")) {
            jdbc.update("""
                    insert into treinador_skill (treinador_id, temporada_id, skill, valor)
                    values (?, ?, ?, 2)
                    """, treinadorId, temporadaId, skill);
        }

        var posicoes = jdbc.queryForList(
                "select id from posicao order by ordem", Long.class);

        var jogadorIds = new java.util.ArrayList<Long>();
        for (int i = 0; i < TAMANHO_DO_ELENCO; i++) {
            // Distribui pelas nove posições, garantindo ao menos dois goleiros.
            var posicaoPrincipal = i < 2 ? posicoes.getFirst() : posicoes.get(i % posicoes.size());
            var categoria = i >= TAMANHO_DO_ELENCO - 2 ? "BASE" : "PROFISSIONAL";
            jogadorIds.add(inserirJogador(jdbc, temporadaId, clubeId, paisId,
                    posicaoPrincipal, posicoes, categoria, i));
        }

        return new CenarioDeElenco(temporadaId, clubeId, clubeSemTreinadorId,
                jogadorIds.get(0), jogadorIds.get(1), TAMANHO_DO_ELENCO,
                TATICA, REPUTACAO, posicoes);
    }

    private static long inserirClube(JdbcTemplate jdbc, String slug, String nome,
                                     long paisId, int reputacao) {
        return jdbc.queryForObject("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id, reputacao,
                                   forca_financeira)
                values (?, ?, ?, ?, ?, 50) returning id
                """, Long.class, slug, nome, nome, paisId, reputacao);
    }

    private static long inserirJogador(JdbcTemplate jdbc, long temporadaId, long clubeId,
                                       long paisId, long posicaoPrincipalId,
                                       List<Long> posicoes, String categoria, int indice) {
        var jogadorId = jdbc.queryForObject("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao,
                                     data_nascimento, pais_id, pe_preferido,
                                     posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '2000-01-01', ?, 'DIREITO', ?, ?, 'GERADO')
                returning id
                """, Long.class,
                "jog-" + indice, "jog-" + indice + "|2000-01-01|BRA",
                "Jogador " + indice, "J" + indice, paisId, posicaoPrincipalId, (long) indice);

        jdbc.update("""
                insert into jogador_vinculo (jogador_id, clube_id, temporada_id, tipo,
                                             numero_camisa, categoria)
                values (?, ?, ?, 'CONTRATO', ?, ?)
                """, jogadorId, clubeId, temporadaId, indice + 1, categoria);

        // Overall determinístico: alto na posição principal, decrescente nas demais.
        for (var posicaoId : posicoes) {
            var overall = posicaoId.equals(posicaoPrincipalId) ? 70 + (indice % 20) : 30 + (indice % 10);
            jdbc.update("""
                    insert into jogador_overall (jogador_id, temporada_id, posicao_id,
                                                 perfil_versao, overall, calculado_em)
                    values (?, ?, ?, 1, ?, now())
                    """, jogadorId, temporadaId, posicaoId, overall);
        }

        return jogadorId;
    }

    private static void limpar(JdbcTemplate jdbc) {
        jdbc.execute("""
                truncate plano_escalacao, plano_tatico, jogador_overall, jogador_vinculo,
                         treinador_skill, vinculo_treinador, treinador, jogador, clube,
                         temporada restart identity cascade
                """);
    }
}
```

Se algum `insert` falhar por coluna obrigatória que este plano não previu, leia a migration correspondente e acrescente a coluna com um valor fixo — não invente default no schema.

- [ ] **Step 3: Rodar o teste para confirmar que falha**

Run: `./gradlew test --tests '*PortasExternasTest'`
Expected: FAIL na compilação — os três métodos não existem.

- [ ] **Step 4: Implementar a porta do `treinador`**

```java
// treinador/PerfilDeTreinador.java — pacote raiz, ao lado dos records de evento
package br.com.api.footfirma.treinador;

/**
 * O que outro módulo precisa saber sobre quem dirige um clube.
 *
 * <p>Mora no pacote raiz, e não em {@code dto/}, porque {@code treinador.dto} não é
 * uma interface nomeada do Modulith — referenciá-lo de fora reprova em
 * {@code ModularidadeTest}. Três inteiros não justificam publicar os seis DTOs e os
 * onze tipos de domínio do módulo.
 */
public record PerfilDeTreinador(long treinadorId, int reputacao, int tatica) {
}
```

Em `TreinadorService`, acrescente ao fim da interface:

```java
    /**
     * O perfil de quem dirige o clube na temporada. Vazio quando o clube está sem
     * treinador — situação normal entre a demissão e a próxima contratação.
     */
    Optional<PerfilDeTreinador> buscarPerfilDoClube(long clubeId, long temporadaId);
```

Em `TreinadorServiceImpl`, implemente lendo o vínculo ativo e a skill `TATICA` da temporada. Use os repositories que já existem no módulo; se `VinculoTreinadorRepository` não tiver uma busca por clube com `fim is null`, acrescente:

```java
    Optional<VinculoTreinador> findByClubeIdAndTemporadaIdAndFimIsNull(Long clubeId, Long temporadaId);
```

e no service:

```java
    @Override
    public Optional<PerfilDeTreinador> buscarPerfilDoClube(long clubeId, long temporadaId) {
        return vinculoTreinadorRepository
                .findByClubeIdAndTemporadaIdAndFimIsNull(clubeId, temporadaId)
                .map(vinculo -> {
                    var treinador = vinculo.getTreinador();
                    var tatica = treinadorSkillRepository
                            .findByTreinadorIdAndTemporadaIdAndSkill(
                                    treinador.getId(), temporadaId, Skill.TATICA)
                            .map(TreinadorSkill::getValor)
                            .orElse(0);
                    return new PerfilDeTreinador(treinador.getId(),
                            treinador.getReputacao(), tatica);
                });
    }
```

Se `findByTreinadorIdAndTemporadaIdAndSkill` não existir em `TreinadorSkillRepository`, acrescente-o. Se `VinculoTreinador` guardar `treinadorId` como `Long` em vez de `@ManyToOne`, ajuste o `map` para buscar o treinador pelo id.

- [ ] **Step 5: Implementar a porta do `jogador`**

```java
// jogador/dto/JogadorDoElenco.java
package br.com.api.footfirma.jogador.dto;

/**
 * O elenco cru para quem escala: ids em vez de slugs, e a categoria que
 * {@code JogadorResumo} não carrega.
 *
 * <p>{@code categoria} é {@code String} porque {@code CategoriaDeElenco} vive em
 * {@code jogador.domain}, que é interno — mesma travessia que {@code DadosDeVinculo}
 * já faz na entrada.
 */
public record JogadorDoElenco(Long jogadorId, Long posicaoPrincipalId,
                              String categoria, Integer numeroCamisa) {
}
```

Em `JogadorService`:

```java
    /**
     * O elenco do clube na temporada, por id. Ordem não garantida — quem escala
     * reordena pelo próprio critério.
     */
    List<JogadorDoElenco> listarElencoParaEscalacao(long clubeId, long temporadaId);
```

Em `JogadorServiceImpl`, com uma query no `JogadorVinculoRepository` (o nome do repository pode diferir; use o que o módulo já tem para `jogador_vinculo`):

```java
    @Query("""
            select new br.com.api.footfirma.jogador.dto.JogadorDoElenco(
                       j.id, j.posicaoPrincipal.id, cast(v.categoria as string), v.numeroCamisa)
            from JogadorVinculo v
            join v.jogador j
            where v.clubeId = :clubeId and v.temporadaId = :temporadaId
            """)
    List<JogadorDoElenco> buscarElencoParaEscalacao(@Param("clubeId") Long clubeId,
                                                    @Param("temporadaId") Long temporadaId);
```

Se `JogadorVinculo` referenciar `jogador` por id em vez de associação, troque o `join` por um `join Jogador j on j.id = v.jogadorId`. Se `posicaoPrincipal` for `Long posicaoPrincipalId`, use-o direto. O service apenas delega.

- [ ] **Step 6: Implementar a porta do `avaliacao`**

```java
// avaliacao/dto/OverallDeJogador.java
package br.com.api.footfirma.avaliacao.dto;

/**
 * Uma linha de {@code jogador_overall} por id, sem passar por slug.
 *
 * <p>Existe porque montar um onze exige as nove posições de ~30 jogadores, e
 * {@code buscarPorSlug} devolve as nove de um só.
 */
public record OverallDeJogador(Long jogadorId, Long posicaoId, Integer overall) {
}
```

Em `AvaliacaoService`:

```java
    /**
     * O overall de vários jogadores em todas as posições da temporada.
     *
     * <p>Devolve lista vazia para coleção vazia — não estoura e não consulta o banco.
     */
    List<OverallDeJogador> listarOveralls(Collection<Long> jogadorIds, long temporadaId);
```

Em `AvaliacaoServiceImpl`:

```java
    @Override
    public List<OverallDeJogador> listarOveralls(Collection<Long> jogadorIds, long temporadaId) {
        if (jogadorIds.isEmpty()) {
            return List.of();
        }
        return jogadorOverallRepository.buscarPorJogadores(jogadorIds, temporadaId);
    }
```

e em `JogadorOverallRepository`:

```java
    @Query("""
            select new br.com.api.footfirma.avaliacao.dto.OverallDeJogador(
                       o.jogadorId, o.posicaoId, o.overall)
            from JogadorOverall o
            where o.jogadorId in :jogadorIds and o.temporadaId = :temporadaId
            """)
    List<OverallDeJogador> buscarPorJogadores(@Param("jogadorIds") Collection<Long> jogadorIds,
                                              @Param("temporadaId") long temporadaId);
```

Ajuste os nomes de campo à entidade `JogadorOverall` real.

- [ ] **Step 7: Rodar o teste e o de modularidade**

Run: `./gradlew test --tests '*PortasExternasTest' --tests '*ModularidadeTest'`
Expected: PASS nos dois. Se `ModularidadeTest` reprovar citando `treinador.dto` ou `treinador.domain`, o Step 4 devolveu `TreinadorDetalhe` em vez de `PerfilDeTreinador` — corrija o retorno, não anote os pacotes.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador \
        src/main/java/br/com/api/footfirma/jogador \
        src/main/java/br/com/api/footfirma/avaliacao \
        src/test/java/br/com/api/footfirma/tatica
git commit -m "feat(tatica): abre as portas de treinador, jogador e avaliacao"
```

---

### Task 3: Entidades do plano e integridade do banco

**Files:**
- Create: `tatica/domain/{PlanoTatico,PlanoEscalacao,PlanoEscalacaoId}.java`
- Create: `tatica/repository/{PlanoTaticoRepository,PlanoEscalacaoRepository}.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/PlanoIntegridadeTest.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/PlanoFactory.java`

**Interfaces:**
- Consumes: os enums de `tatica.dto` (Task 1), `CenarioDeElenco` (Task 2)
- Produces: `PlanoTatico` (getters/setters de todos os campos da tabela), `PlanoEscalacao`, `PlanoTaticoRepository.findByClubeIdAndTemporadaIdAndVigenteTrue(Long, Long)`, `PlanoTaticoRepository.findMaxVersao(Long, Long)`, `PlanoEscalacaoRepository.findByPlanoId(Long)`, e `PlanoFactory` para os testes

- [ ] **Step 1: Escrever o teste de integridade (vai falhar — não há entidade)**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.tatica.repository.PlanoEscalacaoRepository;
import br.com.api.footfirma.tatica.repository.PlanoTaticoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlanoIntegridadeTest {

    @Autowired PlanoTaticoRepository planos;
    @Autowired PlanoEscalacaoRepository escalacoes;
    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deveRejeitarSegundoPlanoVigenteNoMesmoClubeETemporada() {
        var cenario = CenarioDeElenco.montar(jdbc);
        planos.save(PlanoFactory.vigente(cenario, 1));
        em.flush();

        var segundo = PlanoFactory.vigente(cenario, 2);

        assertThatThrownBy(() -> planos.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_plano_vigente");
    }

    @Test
    void devePermitirSegundoPlanoQuandoOPrimeiroDeixaDeSerVigente() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var primeiro = planos.save(PlanoFactory.vigente(cenario, 1));
        primeiro.setVigente(false);
        em.flush();

        var segundo = planos.saveAndFlush(PlanoFactory.vigente(cenario, 2));

        assertThat(segundo.getId()).isNotNull();
    }

    @Test
    void deveRejeitarTitularSemSlot() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, 1));

        var invalido = PlanoFactory.titular(plano.getId(), cenario.primeiroJogadorId(),
                cenario.posicoesEmOrdem().getFirst(), 1, 70);
        invalido.setSlotOrdem(null);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(invalido))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_papel_coerente");
    }

    @Test
    void deveRejeitarReservaComSlot() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, 1));

        var invalido = PlanoFactory.reserva(plano.getId(), cenario.primeiroJogadorId(),
                cenario.posicoesEmOrdem().getFirst(), 1, 70);
        invalido.setSlotOrdem(3);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(invalido))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_papel_coerente");
    }

    @Test
    void deveRejeitarDoisTitularesNoMesmoSlot() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, 1));
        var posicao = cenario.posicoesEmOrdem().getFirst();

        escalacoes.saveAndFlush(PlanoFactory.titular(plano.getId(),
                cenario.primeiroJogadorId(), posicao, 1, 70));

        var colidente = PlanoFactory.titular(plano.getId(),
                cenario.segundoJogadorId(), posicao, 1, 68);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(colidente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_escalacao_slot");
    }

    @Test
    void deveRejeitarDoisReservasNaMesmaOrdemDeBanco() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = planos.saveAndFlush(PlanoFactory.vigente(cenario, 1));
        var posicao = cenario.posicoesEmOrdem().getFirst();

        escalacoes.saveAndFlush(PlanoFactory.reserva(plano.getId(),
                cenario.primeiroJogadorId(), posicao, 3, 60));

        var colidente = PlanoFactory.reserva(plano.getId(),
                cenario.segundoJogadorId(), posicao, 3, 59);

        assertThatThrownBy(() -> escalacoes.saveAndFlush(colidente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_escalacao_banco");
    }
}
```

- [ ] **Step 2: Rodar o teste para confirmar que falha**

Run: `./gradlew test --tests '*PlanoIntegridadeTest'`
Expected: FAIL na compilação — `PlanoTaticoRepository` não existe.

- [ ] **Step 3: Criar as entidades**

```java
// tatica/domain/PlanoTatico.java
package br.com.api.footfirma.tatica.domain;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.OrigemDoPlano;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;
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
@Table(name = "plano_tatico")
@Getter
@Setter
public class PlanoTatico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private Integer versao;

    @Column(nullable = false)
    private Boolean vigente;

    @Column(name = "formacao_id", nullable = false)
    private Long formacaoId;

    @Column(name = "capitao_id")
    private Long capitaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemDoPlano origem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mentalidade mentalidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ritmo ritmo;

    @Enumerated(EnumType.STRING)
    @Column(name = "linha_defensiva", nullable = false)
    private LinhaDefensiva linhaDefensiva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pressao pressao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Largura largura;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof PlanoTatico outra && id != null && id.equals(outra.id);
    }

    @Override
    public int hashCode() {
        return PlanoTatico.class.hashCode();
    }
}
```

```java
// tatica/domain/PlanoEscalacaoId.java
package br.com.api.footfirma.tatica.domain;

import java.io.Serializable;

public record PlanoEscalacaoId(Long planoId, Long jogadorId) implements Serializable {
}
```

```java
// tatica/domain/PlanoEscalacao.java
package br.com.api.footfirma.tatica.domain;

import br.com.api.footfirma.tatica.dto.Papel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "plano_escalacao")
@IdClass(PlanoEscalacaoId.class)
@Getter
@Setter
public class PlanoEscalacao {

    @Id
    @Column(name = "plano_id")
    private Long planoId;

    @Id
    @Column(name = "jogador_id")
    private Long jogadorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel;

    @Column(name = "slot_ordem")
    private Integer slotOrdem;

    @Column(name = "ordem_banco")
    private Integer ordemBanco;

    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    @Column(name = "aptidao_no_momento", nullable = false)
    private Integer aptidaoNoMomento;

    @Override
    public boolean equals(Object outro) {
        return outro instanceof PlanoEscalacao outra
                && Objects.equals(planoId, outra.planoId)
                && Objects.equals(jogadorId, outra.jogadorId);
    }

    @Override
    public int hashCode() {
        return PlanoEscalacao.class.hashCode();
    }
}
```

- [ ] **Step 4: Criar os repositories**

```java
// tatica/repository/PlanoTaticoRepository.java
package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.PlanoTatico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlanoTaticoRepository extends JpaRepository<PlanoTatico, Long> {

    Optional<PlanoTatico> findByClubeIdAndTemporadaIdAndVigenteTrue(Long clubeId, Long temporadaId);

    @Query("""
            select coalesce(max(p.versao), 0) from PlanoTatico p
            where p.clubeId = :clubeId and p.temporadaId = :temporadaId
            """)
    int findMaxVersao(@Param("clubeId") Long clubeId, @Param("temporadaId") Long temporadaId);
}
```

```java
// tatica/repository/PlanoEscalacaoRepository.java
package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.PlanoEscalacao;
import br.com.api.footfirma.tatica.domain.PlanoEscalacaoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanoEscalacaoRepository
        extends JpaRepository<PlanoEscalacao, PlanoEscalacaoId> {

    List<PlanoEscalacao> findByPlanoId(Long planoId);
}
```

- [ ] **Step 5: Criar a `PlanoFactory` de teste**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.tatica.domain.PlanoEscalacao;
import br.com.api.footfirma.tatica.domain.PlanoTatico;
import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.OrigemDoPlano;
import br.com.api.footfirma.tatica.dto.Papel;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;

final class PlanoFactory {

    private PlanoFactory() {
    }

    static PlanoTatico vigente(CenarioDeElenco cenario, int versao) {
        var plano = new PlanoTatico();
        plano.setClubeId(cenario.clubeId());
        plano.setTemporadaId(cenario.temporadaId());
        plano.setVersao(versao);
        plano.setVigente(true);
        plano.setFormacaoId(1L);
        plano.setOrigem(OrigemDoPlano.MANUAL);
        plano.setMentalidade(Mentalidade.EQUILIBRADA);
        plano.setRitmo(Ritmo.EQUILIBRADO);
        plano.setLinhaDefensiva(LinhaDefensiva.MEDIA);
        plano.setPressao(Pressao.MEDIA);
        plano.setLargura(Largura.MEDIA);
        return plano;
    }

    static PlanoEscalacao titular(long planoId, long jogadorId, long posicaoId,
                                  int slotOrdem, int aptidao) {
        var linha = base(planoId, jogadorId, posicaoId, aptidao);
        linha.setPapel(Papel.TITULAR);
        linha.setSlotOrdem(slotOrdem);
        return linha;
    }

    static PlanoEscalacao reserva(long planoId, long jogadorId, long posicaoId,
                                  int ordemBanco, int aptidao) {
        var linha = base(planoId, jogadorId, posicaoId, aptidao);
        linha.setPapel(Papel.RESERVA);
        linha.setOrdemBanco(ordemBanco);
        return linha;
    }

    private static PlanoEscalacao base(long planoId, long jogadorId, long posicaoId, int aptidao) {
        var linha = new PlanoEscalacao();
        linha.setPlanoId(planoId);
        linha.setJogadorId(jogadorId);
        linha.setPosicaoId(posicaoId);
        linha.setAptidaoNoMomento(aptidao);
        return linha;
    }
}
```

`vigente` fixa `formacaoId = 1L` porque `V21` semeia o 4-4-2 com `ordem = 1` numa tabela `identity` recém-criada. Se o teste falhar por FK, troque por uma busca em `FormacaoRepository`.

- [ ] **Step 6: Rodar o teste**

Run: `./gradlew test --tests '*PlanoIntegridadeTest'`
Expected: PASS, seis testes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/api/footfirma/tatica src/test/java/br/com/api/footfirma/tatica
git commit -m "feat(tatica): cria entidades do plano e prova a integridade no banco"
```

---

### Task 4: `PreenchedorDeSlots` — o guloso por escassez

**Files:**
- Create: `tatica/internal/ConstantesDeTatica.java`
- Create: `tatica/internal/{JogadorDisponivel,TabelaDeOveralls,FormacaoCandidata,SlotPreenchido,Onze}.java`
- Create: `tatica/internal/PreenchedorDeSlots.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/internal/PreenchimentoDeSlotsTest.java`

**Interfaces:**
- Consumes: nada — funções puras, sem Spring, sem JPA
- Produces: `PreenchedorDeSlots.preencher(FormacaoCandidata, List<JogadorDisponivel>, TabelaDeOveralls) -> Optional<Onze>`; `Onze(List<SlotPreenchido> slots, int somaDeAptidao)`; `SlotPreenchido(int slotOrdem, long posicaoId, long jogadorId, int aptidao)`; `JogadorDisponivel(long jogadorId, long posicaoPrincipalId, boolean daBase)`; `TabelaDeOveralls.overall(long jogadorId, long posicaoId) -> int`; `FormacaoCandidata(long formacaoId, int ordem, List<Long> posicaoPorSlot)`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.tatica.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreenchimentoDeSlotsTest {

    static final long GOL = 1L;
    static final long ZAG = 2L;
    static final long ATA = 9L;

    /** Formação de três slots: um por posição. Suficiente para provar a regra. */
    static final FormacaoCandidata TRES_SLOTS =
            new FormacaoCandidata(1L, 1, List.of(GOL, ZAG, ATA));

    @Test
    void devePreencherAntesOSlotMaisEscasso() {
        // O jogador 1 é o melhor tanto em ZAG (80) quanto em ATA (79).
        // Em ZAG o segundo melhor é 40 (escassez 40); em ATA é 78 (escassez 1).
        // O guloso por escassez precisa dar o jogador 1 ao ZAG, não ao ATA.
        var elenco = List.of(
                disponivel(1, ZAG), disponivel(2, ZAG), disponivel(3, ATA), disponivel(4, GOL));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 10, ZAG, 80, ATA, 79),
                2L, Map.of(GOL, 10, ZAG, 40, ATA, 20),
                3L, Map.of(GOL, 10, ZAG, 30, ATA, 78),
                4L, Map.of(GOL, 70, ZAG, 10, ATA, 10)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 2)).as("slot ZAG").isEqualTo(1L);
        assertThat(jogadorNoSlot(onze, 3)).as("slot ATA").isEqualTo(3L);
        assertThat(onze.somaDeAptidao()).isEqualTo(70 + 80 + 78);
    }

    @Test
    void deveDesempatarPeloMenorIdQuandoAsAptidoesEmpatam() {
        var elenco = List.of(
                disponivel(9, ZAG), disponivel(4, ZAG), disponivel(7, GOL), disponivel(8, ATA));
        var overalls = tabela(Map.of(
                9L, Map.of(GOL, 10, ZAG, 50, ATA, 10),
                4L, Map.of(GOL, 10, ZAG, 50, ATA, 10),
                7L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                8L, Map.of(GOL, 10, ZAG, 10, ATA, 60)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 2)).isEqualTo(4L);
    }

    @Test
    void naoDeveUsarJogadorDaBaseQuandoOsProfissionaisFechamOTime() {
        var elenco = List.of(
                disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA), daBase(4, ATA));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 60, ATA, 10),
                3L, Map.of(GOL, 10, ZAG, 10, ATA, 50),
                4L, Map.of(GOL, 10, ZAG, 10, ATA, 99)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 3))
                .as("o garoto de 99 fica fora porque os profissionais fecham o time")
                .isEqualTo(3L);
    }

    @Test
    void deveUsarJogadorDaBaseQuandoOsProfissionaisNaoFecham() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), daBase(4, ATA));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 60, ATA, 10),
                4L, Map.of(GOL, 10, ZAG, 10, ATA, 55)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 3)).isEqualTo(4L);
    }

    @Test
    void deveVoltarVazioQuandoNaoHaJogadoresSuficientes() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 60, ATA, 10)));

        assertThat(PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls)).isEmpty();
    }

    private static long jogadorNoSlot(Onze onze, int slotOrdem) {
        return onze.slots().stream()
                .filter(slot -> slot.slotOrdem() == slotOrdem)
                .findFirst().orElseThrow().jogadorId();
    }

    private static JogadorDisponivel disponivel(long id, long posicaoPrincipal) {
        return new JogadorDisponivel(id, posicaoPrincipal, false);
    }

    private static JogadorDisponivel daBase(long id, long posicaoPrincipal) {
        return new JogadorDisponivel(id, posicaoPrincipal, true);
    }

    private static TabelaDeOveralls tabela(Map<Long, Map<Long, Integer>> valores) {
        return new TabelaDeOveralls(valores);
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*PreenchimentoDeSlotsTest'`
Expected: FAIL na compilação.

- [ ] **Step 3: Criar as constantes e os tipos de apoio**

```java
// tatica/internal/ConstantesDeTatica.java
package br.com.api.footfirma.tatica.internal;

/**
 * Números de balanceamento do módulo.
 *
 * <p>Nenhum tem verdade de referência: são julgamento de domínio, como os coeficientes
 * de {@code ConstantesDeTreinador}. Os testes travam a forma das regras e a ordem
 * relativa dos casos, não estes valores. Rebalancear é editar aqui.
 */
final class ConstantesDeTatica {

    private ConstantesDeTatica() {
    }

    static final int TITULARES = 11;

    static final int BANCO_MINIMO = 5;
    static final int BANCO_MAXIMO = 12;

    /** Repertório do escalador de IA: {@code 1 + TATICA / DIVISOR_DE_REPERTORIO}. */
    static final int REPERTORIO_BASE = 1;
    static final int DIVISOR_DE_REPERTORIO = 2;

    /** Distância da média de reputação que separa uma mentalidade da seguinte. */
    static final int FAIXA_DE_MENTALIDADE = 10;
}
```

```java
// tatica/internal/JogadorDisponivel.java
package br.com.api.footfirma.tatica.internal;

/** Um jogador do elenco, do ponto de vista de quem monta o onze. */
record JogadorDisponivel(long jogadorId, long posicaoPrincipalId, boolean daBase) {
}
```

```java
// tatica/internal/TabelaDeOveralls.java
package br.com.api.footfirma.tatica.internal;

import java.util.Map;

/**
 * Overall por {@code (jogador, posição)}.
 *
 * <p>Devolve 0 para par ausente em vez de estourar: um jogador sem overall
 * materializado é escalável, só nunca será o escolhido.
 */
record TabelaDeOveralls(Map<Long, Map<Long, Integer>> valores) {

    int overall(long jogadorId, long posicaoId) {
        return valores.getOrDefault(jogadorId, Map.of()).getOrDefault(posicaoId, 0);
    }
}
```

```java
// tatica/internal/FormacaoCandidata.java
package br.com.api.footfirma.tatica.internal;

import java.util.List;

/**
 * Uma formação do catálogo, achatada para o preenchimento.
 *
 * @param posicaoPorSlot índice 0 é o slot de ordem 1.
 */
record FormacaoCandidata(long formacaoId, int ordem, List<Long> posicaoPorSlot) {
}
```

```java
// tatica/internal/SlotPreenchido.java
package br.com.api.footfirma.tatica.internal;

record SlotPreenchido(int slotOrdem, long posicaoId, long jogadorId, int aptidao) {
}
```

```java
// tatica/internal/Onze.java
package br.com.api.footfirma.tatica.internal;

import java.util.List;

record Onze(List<SlotPreenchido> slots, int somaDeAptidao) {
}
```

- [ ] **Step 4: Implementar o preenchedor**

```java
// tatica/internal/PreenchedorDeSlots.java
package br.com.api.footfirma.tatica.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Monta o melhor onze que esta formação permite, por guloso por escassez.
 *
 * <p>A cada rodada mede, para cada slot ainda vazio, a distância entre a melhor e a
 * segunda melhor aptidão disponível, e preenche primeiro o slot onde essa distância é
 * maior — é lá que errar custa mais caro. Não é ótimo: a atribuição húngara acertaria
 * casos que este erra. A troca está registrada no risco 3 do spec.
 *
 * <p>Jogador da base só é considerado quando os profissionais não fecham o time. A
 * decisão é por rodada, não global: basta faltar profissional para o slot da vez.
 */
final class PreenchedorDeSlots {

    private PreenchedorDeSlots() {
    }

    static Optional<Onze> preencher(FormacaoCandidata formacao,
                                    List<JogadorDisponivel> elenco,
                                    TabelaDeOveralls overalls) {
        if (elenco.size() < formacao.posicaoPorSlot().size()) {
            return Optional.empty();
        }

        var usados = new LinkedHashSet<Long>();
        var preenchidos = new ArrayList<SlotPreenchido>();
        var pendentes = new ArrayList<Integer>();
        for (int i = 1; i <= formacao.posicaoPorSlot().size(); i++) {
            pendentes.add(i);
        }

        while (!pendentes.isEmpty()) {
            var maisEscasso = pendentes.stream()
                    .max(Comparator.comparingInt(slot ->
                            escassez(formacao, slot, elenco, overalls, usados)))
                    .orElseThrow();

            var posicaoId = posicaoDoSlot(formacao, maisEscasso);
            var escolhido = melhorPara(posicaoId, elenco, overalls, usados);
            if (escolhido.isEmpty()) {
                return Optional.empty();
            }

            usados.add(escolhido.get().jogadorId());
            preenchidos.add(new SlotPreenchido(maisEscasso, posicaoId,
                    escolhido.get().jogadorId(),
                    overalls.overall(escolhido.get().jogadorId(), posicaoId)));
            pendentes.remove(Integer.valueOf(maisEscasso));
        }

        preenchidos.sort(Comparator.comparingInt(SlotPreenchido::slotOrdem));
        var soma = preenchidos.stream().mapToInt(SlotPreenchido::aptidao).sum();
        return Optional.of(new Onze(List.copyOf(preenchidos), soma));
    }

    /** Quanto se perde ao não dar a este slot o seu melhor jogador. */
    private static int escassez(FormacaoCandidata formacao, int slot,
                                List<JogadorDisponivel> elenco, TabelaDeOveralls overalls,
                                Set<Long> usados) {
        var posicaoId = posicaoDoSlot(formacao, slot);
        var aptidoes = candidatos(elenco, usados)
                .map(jogador -> overalls.overall(jogador.jogadorId(), posicaoId))
                .sorted(Comparator.reverseOrder())
                .limit(2)
                .toList();

        if (aptidoes.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        if (aptidoes.size() == 1) {
            return aptidoes.getFirst();
        }
        return aptidoes.get(0) - aptidoes.get(1);
    }

    private static Optional<JogadorDisponivel> melhorPara(long posicaoId,
                                                          List<JogadorDisponivel> elenco,
                                                          TabelaDeOveralls overalls,
                                                          Set<Long> usados) {
        var profissionais = candidatos(elenco, usados).filter(jogador -> !jogador.daBase()).toList();
        var pool = profissionais.isEmpty() ? candidatos(elenco, usados).toList() : profissionais;

        return pool.stream().max(Comparator
                .comparingInt((JogadorDisponivel jogador) ->
                        overalls.overall(jogador.jogadorId(), posicaoId))
                .thenComparing(Comparator.comparingLong(JogadorDisponivel::jogadorId).reversed()));
    }

    private static java.util.stream.Stream<JogadorDisponivel> candidatos(
            List<JogadorDisponivel> elenco, Set<Long> usados) {
        return elenco.stream().filter(jogador -> !usados.contains(jogador.jogadorId()));
    }

    private static long posicaoDoSlot(FormacaoCandidata formacao, int slotOrdem) {
        return formacao.posicaoPorSlot().get(slotOrdem - 1);
    }
}
```

O `.thenComparing(...reversed())` no desempate existe porque `max` devolve o **último** máximo: invertendo a ordem por id, o último máximo passa a ser o de menor id, que é o que o teste exige.

- [ ] **Step 5: Rodar o teste**

Run: `./gradlew test --tests '*PreenchimentoDeSlotsTest'`
Expected: PASS, cinco testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/tatica/internal \
        src/test/java/br/com/api/footfirma/tatica/internal
git commit -m "feat(tatica): monta o onze por guloso de escassez"
```

---

### Task 5: `EscolhedorDeFormacao` — o repertório

**Files:**
- Create: `tatica/internal/EscolhedorDeFormacao.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/internal/EscolhaDeFormacaoTest.java`

**Interfaces:**
- Consumes: `FormacaoCandidata`, `JogadorDisponivel`, `TabelaDeOveralls`, `Onze`, `PreenchedorDeSlots.preencher` (Task 4)
- Produces: `EscolhedorDeFormacao.tamanhoDoRepertorio(int tatica) -> int`; `EscolhedorDeFormacao.escolher(List<FormacaoCandidata>, int tatica, List<JogadorDisponivel>, TabelaDeOveralls) -> Optional<Escolha>`; `Escolha(FormacaoCandidata formacao, Onze onze)`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.tatica.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EscolhaDeFormacaoTest {

    static final long GOL = 1L;
    static final long ZAG = 2L;
    static final long ATA = 9L;

    @ParameterizedTest(name = "TATICA {0} avalia {1} formações")
    @CsvSource({"1, 1", "2, 2", "3, 2", "4, 3", "5, 3", "6, 4", "7, 4", "8, 5", "9, 5", "10, 6"})
    void deveCrescerORepertorioComATatica(int tatica, int esperado) {
        assertThat(EscolhedorDeFormacao.tamanhoDoRepertorio(tatica)).isEqualTo(esperado);
    }

    @Test
    void deveCairNoRepertorioMinimoQuandoATaticaEZero() {
        assertThat(EscolhedorDeFormacao.tamanhoDoRepertorio(0)).isEqualTo(1);
    }

    @Test
    void deveIgnorarFormacaoForaDoRepertorio() {
        // A segunda formação é estritamente melhor para este elenco, mas TATICA 1
        // só enxerga a primeira.
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA));
        var overalls = new TabelaDeOveralls(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 20, ATA, 90),
                3L, Map.of(GOL, 10, ZAG, 20, ATA, 90)));

        var escolha = EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 1, elenco, overalls).orElseThrow();

        assertThat(escolha.formacao().formacaoId()).isEqualTo(defensiva().formacaoId());
    }

    @Test
    void deveEscolherAFormacaoDeMaiorSomaDentroDoRepertorio() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA));
        var overalls = new TabelaDeOveralls(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 20, ATA, 90),
                3L, Map.of(GOL, 10, ZAG, 20, ATA, 90)));

        var escolha = EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 10, elenco, overalls).orElseThrow();

        assertThat(escolha.formacao().formacaoId()).isEqualTo(ofensiva().formacaoId());
    }

    @Test
    void deveDesempatarPelaOrdemDoCatalogo() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA));
        // Simétrico: as duas formações somam o mesmo.
        var overalls = new TabelaDeOveralls(Map.of(
                1L, Map.of(GOL, 50, ZAG, 50, ATA, 50),
                2L, Map.of(GOL, 50, ZAG, 50, ATA, 50),
                3L, Map.of(GOL, 50, ZAG, 50, ATA, 50)));

        var escolha = EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 10, elenco, overalls).orElseThrow();

        assertThat(escolha.formacao().ordem()).isEqualTo(1);
    }

    @Test
    void deveVoltarVazioQuandoNenhumaFormacaoFecha() {
        var elenco = List.of(disponivel(1, GOL));
        var overalls = new TabelaDeOveralls(Map.of(1L, Map.of(GOL, 60, ZAG, 10, ATA, 10)));

        assertThat(EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 10, elenco, overalls)).isEmpty();
    }

    private static FormacaoCandidata defensiva() {
        return new FormacaoCandidata(1L, 1, List.of(GOL, ZAG, ZAG));
    }

    private static FormacaoCandidata ofensiva() {
        return new FormacaoCandidata(2L, 2, List.of(GOL, ATA, ATA));
    }

    private static JogadorDisponivel disponivel(long id, long posicaoPrincipal) {
        return new JogadorDisponivel(id, posicaoPrincipal, false);
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*EscolhaDeFormacaoTest'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar**

```java
// tatica/internal/EscolhedorDeFormacao.java
package br.com.api.footfirma.tatica.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Escolhe a formação do treinador de IA.
 *
 * <p>A skill TATICA entra como <b>repertório</b>, não como multiplicador: o treinador
 * avalia as {@code 1 + TATICA/2} primeiras formações do catálogo e fica com a de maior
 * soma de aptidão. Um multiplicador exigiria calibrar um coeficiente contra um motor de
 * partida que não existe; repertório não exige calibragem nenhuma — treinador de TATICA
 * baixa simplesmente só conhece o 4-4-2.
 */
final class EscolhedorDeFormacao {

    private EscolhedorDeFormacao() {
    }

    record Escolha(FormacaoCandidata formacao, Onze onze) {
    }

    static int tamanhoDoRepertorio(int tatica) {
        return ConstantesDeTatica.REPERTORIO_BASE
                + Math.max(0, tatica) / ConstantesDeTatica.DIVISOR_DE_REPERTORIO;
    }

    /**
     * @param catalogo formações em ordem de catálogo — a primeira é a mais genérica.
     */
    static Optional<Escolha> escolher(List<FormacaoCandidata> catalogo, int tatica,
                                      List<JogadorDisponivel> elenco,
                                      TabelaDeOveralls overalls) {
        return catalogo.stream()
                .sorted(Comparator.comparingInt(FormacaoCandidata::ordem))
                .limit(tamanhoDoRepertorio(tatica))
                .flatMap(formacao -> PreenchedorDeSlots.preencher(formacao, elenco, overalls)
                        .map(onze -> new Escolha(formacao, onze))
                        .stream())
                // max devolve o último máximo; invertendo a ordem, o último máximo é o
                // de menor ordem de catálogo — que é o desempate que o spec pede.
                .max(Comparator.comparingInt((Escolha escolha) -> escolha.onze().somaDeAptidao())
                        .thenComparing(Comparator.comparingInt(
                                (Escolha escolha) -> escolha.formacao().ordem()).reversed()));
    }
}
```

- [ ] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*EscolhaDeFormacaoTest'`
Expected: PASS, 15 execuções (10 do parametrizado + 5 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/tatica/internal/EscolhedorDeFormacao.java \
        src/test/java/br/com/api/footfirma/tatica/internal/EscolhaDeFormacaoTest.java
git commit -m "feat(tatica): escolhe formação pelo repertório da skill TATICA"
```

---

### Task 6: `EscolhedorDeInstrucoes` — mentalidade, coerência e capitão

**Files:**
- Create: `tatica/internal/EscolhedorDeInstrucoes.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/internal/InstrucoesDaIaTest.java`

**Interfaces:**
- Consumes: `Onze`, `SlotPreenchido` (Task 4); os enums de `tatica.dto` (Task 1)
- Produces: `EscolhedorDeInstrucoes.mentalidadeDe(int reputacaoDoClube, double mediaDeReputacao) -> Mentalidade`; `EscolhedorDeInstrucoes.instrucoesDe(Mentalidade) -> Instrucoes`; `EscolhedorDeInstrucoes.capitaoDe(Onze) -> long`; `Instrucoes(Mentalidade mentalidade, Ritmo ritmo, LinhaDefensiva linhaDefensiva, Pressao pressao, Largura largura)`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.Pressao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstrucoesDaIaTest {

    @Test
    void deveDarMentalidadeOfensivaAoClubeMuitoAcimaDaMedia() {
        assertThat(EscolhedorDeInstrucoes.mentalidadeDe(88, 55.0))
                .isEqualTo(Mentalidade.MUITO_OFENSIVA);
    }

    @Test
    void deveDarMentalidadeDefensivaAoClubeMuitoAbaixoDaMedia() {
        assertThat(EscolhedorDeInstrucoes.mentalidadeDe(35, 55.0))
                .isEqualTo(Mentalidade.MUITO_DEFENSIVA);
    }

    @Test
    void deveDarMentalidadeEquilibradaAoClubeNaMedia() {
        assertThat(EscolhedorDeInstrucoes.mentalidadeDe(55, 55.0))
                .isEqualTo(Mentalidade.EQUILIBRADA);
    }

    @Test
    void deveOrdenarAsMentalidadesPelaDistanciaDaMedia() {
        var muitoAbaixo = EscolhedorDeInstrucoes.mentalidadeDe(30, 55.0);
        var abaixo = EscolhedorDeInstrucoes.mentalidadeDe(48, 55.0);
        var naMedia = EscolhedorDeInstrucoes.mentalidadeDe(55, 55.0);
        var acima = EscolhedorDeInstrucoes.mentalidadeDe(62, 55.0);
        var muitoAcima = EscolhedorDeInstrucoes.mentalidadeDe(80, 55.0);

        assertThat(List.of(muitoAbaixo, abaixo, naMedia, acima, muitoAcima))
                .containsExactly(Mentalidade.MUITO_DEFENSIVA, Mentalidade.DEFENSIVA,
                        Mentalidade.EQUILIBRADA, Mentalidade.OFENSIVA,
                        Mentalidade.MUITO_OFENSIVA);
    }

    @ParameterizedTest
    @EnumSource(Mentalidade.class)
    void deveDarInstrucoesCoerentesParaTodaMentalidade(Mentalidade mentalidade) {
        var instrucoes = EscolhedorDeInstrucoes.instrucoesDe(mentalidade);

        assertThat(instrucoes.mentalidade()).isEqualTo(mentalidade);
        assertThat(instrucoes.ritmo()).isNotNull();
        assertThat(instrucoes.largura()).isNotNull();

        var ofensiva = mentalidade == Mentalidade.OFENSIVA
                || mentalidade == Mentalidade.MUITO_OFENSIVA;
        if (ofensiva) {
            assertThat(instrucoes.linhaDefensiva())
                    .as("time ofensivo não joga com linha recuada")
                    .isNotEqualTo(LinhaDefensiva.RECUADA);
            assertThat(instrucoes.pressao()).isNotEqualTo(Pressao.BAIXA);
            assertThat(instrucoes.largura()).isNotEqualTo(Largura.ESTREITA);
        }

        var defensiva = mentalidade == Mentalidade.DEFENSIVA
                || mentalidade == Mentalidade.MUITO_DEFENSIVA;
        if (defensiva) {
            assertThat(instrucoes.linhaDefensiva()).isNotEqualTo(LinhaDefensiva.ADIANTADA);
            assertThat(instrucoes.pressao()).isNotEqualTo(Pressao.ALTA);
        }
    }

    @Test
    void deveDarABracadeiraAoTitularDeMaiorAptidao() {
        var onze = new Onze(List.of(
                new SlotPreenchido(1, 1L, 10L, 62),
                new SlotPreenchido(2, 2L, 20L, 81),
                new SlotPreenchido(3, 9L, 30L, 74)), 217);

        assertThat(EscolhedorDeInstrucoes.capitaoDe(onze)).isEqualTo(20L);
    }

    @Test
    void deveDesempatarACapitaniaPeloMenorId() {
        var onze = new Onze(List.of(
                new SlotPreenchido(1, 1L, 30L, 70),
                new SlotPreenchido(2, 2L, 12L, 70)), 140);

        assertThat(EscolhedorDeInstrucoes.capitaoDe(onze)).isEqualTo(12L);
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*InstrucoesDaIaTest'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar**

```java
// tatica/internal/EscolhedorDeInstrucoes.java
package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;

import java.util.Comparator;
import java.util.Map;

/**
 * As cinco instruções coletivas do treinador de IA, e a braçadeira.
 *
 * <p>A mentalidade sai da reputação do clube comparada à média global — não à da
 * divisão, que exigiria depender de {@code competicao} e seria ambígua para quem
 * disputa mais de uma competição. Os outros quatro eixos saem da tabela de coerência:
 * cinco eixos sorteados de forma independente produziriam um time ofensivo com linha
 * recuada, e é essa tabela que impede a IA de se contradizer.
 */
final class EscolhedorDeInstrucoes {

    private EscolhedorDeInstrucoes() {
    }

    record Instrucoes(Mentalidade mentalidade, Ritmo ritmo, LinhaDefensiva linhaDefensiva,
                      Pressao pressao, Largura largura) {
    }

    private static final Map<Mentalidade, Instrucoes> COERENCIA = Map.of(
            Mentalidade.MUITO_DEFENSIVA, new Instrucoes(Mentalidade.MUITO_DEFENSIVA,
                    Ritmo.LENTO, LinhaDefensiva.RECUADA, Pressao.BAIXA, Largura.ESTREITA),
            Mentalidade.DEFENSIVA, new Instrucoes(Mentalidade.DEFENSIVA,
                    Ritmo.LENTO, LinhaDefensiva.RECUADA, Pressao.MEDIA, Largura.MEDIA),
            Mentalidade.EQUILIBRADA, new Instrucoes(Mentalidade.EQUILIBRADA,
                    Ritmo.EQUILIBRADO, LinhaDefensiva.MEDIA, Pressao.MEDIA, Largura.MEDIA),
            Mentalidade.OFENSIVA, new Instrucoes(Mentalidade.OFENSIVA,
                    Ritmo.INTENSO, LinhaDefensiva.MEDIA, Pressao.ALTA, Largura.ABERTA),
            Mentalidade.MUITO_OFENSIVA, new Instrucoes(Mentalidade.MUITO_OFENSIVA,
                    Ritmo.INTENSO, LinhaDefensiva.ADIANTADA, Pressao.ALTA, Largura.ABERTA));

    static Mentalidade mentalidadeDe(int reputacaoDoClube, double mediaDeReputacao) {
        var distancia = reputacaoDoClube - mediaDeReputacao;
        var faixa = ConstantesDeTatica.FAIXA_DE_MENTALIDADE;

        if (distancia <= -2.0 * faixa) {
            return Mentalidade.MUITO_DEFENSIVA;
        }
        if (distancia < 0) {
            return Mentalidade.DEFENSIVA;
        }
        if (distancia == 0) {
            return Mentalidade.EQUILIBRADA;
        }
        if (distancia < 2.0 * faixa) {
            return Mentalidade.OFENSIVA;
        }
        return Mentalidade.MUITO_OFENSIVA;
    }

    static Instrucoes instrucoesDe(Mentalidade mentalidade) {
        return COERENCIA.get(mentalidade);
    }

    /** A braçadeira vai para o titular de maior aptidão; empate resolve pelo menor id. */
    static long capitaoDe(Onze onze) {
        return onze.slots().stream()
                .max(Comparator.comparingInt(SlotPreenchido::aptidao)
                        .thenComparing(Comparator.comparingLong(SlotPreenchido::jogadorId).reversed()))
                .orElseThrow()
                .jogadorId();
    }
}
```

Se `deveOrdenarAsMentalidadesPelaDistanciaDaMedia` reprovar, ajuste os cortes de `mentalidadeDe` até a ordem bater — a **forma** (cinco faixas monotônicas) é o que o teste trava, os cortes são de `ConstantesDeTatica`.

- [ ] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*InstrucoesDaIaTest'`
Expected: PASS, 11 execuções (5 do parametrizado + 6 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/tatica/internal/EscolhedorDeInstrucoes.java \
        src/test/java/br/com/api/footfirma/tatica/internal/InstrucoesDaIaTest.java
git commit -m "feat(tatica): deriva instruções coerentes e a braçadeira"
```

---

### Task 7: `TaticaService.salvarPlano` e as invariantes de agregado

**Files:**
- Create: `src/main/java/br/com/api/footfirma/shared/exception/EscalacaoInvalidaException.java`
- Modify: `src/main/java/br/com/api/footfirma/shared/exception/TratadorDeErros.java`
- Create: `tatica/dto/{NovoPlano,TitularEscalado}.java`
- Create: `tatica/TaticaService.java`
- Create: `tatica/internal/{ValidadorDeEscalacao,TaticaServiceImpl}.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/EscalacaoInvalidaTest.java`

**Interfaces:**
- Consumes: `PlanoTaticoRepository`, `PlanoEscalacaoRepository` (Task 3); `JogadorService.listarElencoParaEscalacao`, `AvaliacaoService.listarOveralls` (Task 2); `FormacaoSlotRepository` (Task 1)
- Produces: `TaticaService.salvarPlano(long clubeId, long temporadaId, NovoPlano) -> long` (id do plano); `NovoPlano(long formacaoId, Long capitaoId, Mentalidade, Ritmo, LinhaDefensiva, Pressao, Largura, List<TitularEscalado> titulares, List<Long> banco)`; `TitularEscalado(long jogadorId, int slotOrdem)`; `EscalacaoInvalidaException`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.shared.exception.EscalacaoInvalidaException;
import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.TitularEscalado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EscalacaoInvalidaTest {

    @Autowired TaticaService tatica;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deveAceitarUmPlanoValido() {
        var cenario = CenarioDeElenco.montar(jdbc);

        assertThatCode(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario))).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarDezTitulares() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var dezTitulares = new ArrayList<>(plano.titulares());
        dezTitulares.removeLast();

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, dezTitulares, plano.banco(), plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("11");
    }

    @Test
    void deveRejeitarBancoSemGoleiroReserva() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        // PlanoDeTeste põe o goleiro reserva na primeira posição do banco. Tirá-lo deixa
        // o banco com cinco, ainda dentro do mínimo, e sem goleiro — que é o caso do teste.
        var semGoleiro = new ArrayList<>(plano.banco());
        semGoleiro.removeFirst();
        assertThat(semGoleiro).hasSize(5);

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), semGoleiro, plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("goleiro");
    }

    @Test
    void deveRejeitarSlotForaDaFaixa() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var comSlotInvalido = new ArrayList<>(plano.titulares());
        comSlotInvalido.set(10, new TitularEscalado(
                comSlotInvalido.getLast().jogadorId(), 12));

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, comSlotInvalido, plano.banco(), plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("slot");
    }

    @Test
    void deveRejeitarJogadorDeOutroClube() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var forasteiro = new ArrayList<>(plano.titulares());
        forasteiro.set(10, new TitularEscalado(999_999L, 11));

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, forasteiro, plano.banco(), plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("vínculo");
    }

    @Test
    void deveRejeitarCapitaoQueEstaNoBanco() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), plano.banco(), plano.banco().getFirst())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("capitão");
    }

    @Test
    void deveRejeitarBancoMenorQueOMinimo() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), List.of(plano.banco().getFirst()),
                        plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("banco");
    }

    @Test
    void deveRejeitarJogadorRepetidoEntreTitularEBanco() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        var bancoComTitular = new ArrayList<>(plano.banco());
        bancoComTitular.set(0, plano.titulares().getFirst().jogadorId());

        assertThatThrownBy(() -> tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(plano, plano.titulares(), bancoComTitular, plano.capitaoId())))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("repetido");
    }

    @Test
    void deveProvarQueOBancoSozinhoAceitaAsViolacoesDoServico() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var planoId = jdbc.queryForObject("""
                insert into plano_tatico (clube_id, temporada_id, versao, vigente, formacao_id,
                                          origem, mentalidade, ritmo, linha_defensiva,
                                          pressao, largura)
                values (?, ?, 1, true, 1, 'MANUAL', 'EQUILIBRADA', 'EQUILIBRADO', 'MEDIA',
                        'MEDIA', 'MEDIA') returning id
                """, Long.class, cenario.clubeId(), cenario.temporadaId());

        // Um único titular, sem goleiro, sem banco: o banco de dados aceita.
        jdbc.update("""
                insert into plano_escalacao (plano_id, jogador_id, papel, slot_ordem,
                                             posicao_id, aptidao_no_momento)
                values (?, ?, 'TITULAR', 1, ?, 70)
                """, planoId, cenario.primeiroJogadorId(), cenario.posicoesEmOrdem().getFirst());

        assertThat(jdbc.queryForObject(
                "select count(*) from plano_escalacao where plano_id = ?", Long.class, planoId))
                .as("é por isso que as cinco invariantes vivem no serviço")
                .isEqualTo(1L);
    }
}
```

- [ ] **Step 2: Escrever o helper `PlanoDeTeste`**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;
import br.com.api.footfirma.tatica.dto.TitularEscalado;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Um plano manual válido para o cenário: 4-4-2, os 11 primeiros jogadores nos 11 slots
 * e os 6 seguintes no banco.
 */
final class PlanoDeTeste {

    private PlanoDeTeste() {
    }

    static NovoPlano valido(JdbcTemplate jdbc, CenarioDeElenco cenario) {
        var formacaoId = jdbc.queryForObject(
                "select id from formacao where codigo = '4-4-2'", Long.class);

        // O slot 1 do 4-4-2 é GOL e os outros dez não são. Escalar por ordem de id
        // poria os dois goleiros do cenário nos slots 1 e 2 — plano inválido.
        var goleiros = jdbc.queryForList("""
                select v.jogador_id from jogador_vinculo v
                join jogador j  on j.id = v.jogador_id
                join posicao p  on p.id = j.posicao_principal_id
                where v.clube_id = ? and v.temporada_id = ? and p.codigo = 'GOL'
                order by v.jogador_id
                """, Long.class, cenario.clubeId(), cenario.temporadaId());

        var linha = jdbc.queryForList("""
                select v.jogador_id from jogador_vinculo v
                join jogador j  on j.id = v.jogador_id
                join posicao p  on p.id = j.posicao_principal_id
                where v.clube_id = ? and v.temporada_id = ? and p.codigo <> 'GOL'
                order by v.jogador_id
                """, Long.class, cenario.clubeId(), cenario.temporadaId());

        var titulares = new ArrayList<TitularEscalado>();
        titulares.add(new TitularEscalado(goleiros.getFirst(), 1));
        for (int slot = 2; slot <= 11; slot++) {
            titulares.add(new TitularEscalado(linha.get(slot - 2), slot));
        }

        // Banco de seis: o goleiro reserva mais os cinco jogadores de linha seguintes.
        var banco = new ArrayList<Long>();
        banco.add(goleiros.get(1));
        banco.addAll(linha.subList(10, 15));

        return new NovoPlano(formacaoId, goleiros.getFirst(),
                Mentalidade.EQUILIBRADA, Ritmo.EQUILIBRADO, LinhaDefensiva.MEDIA,
                Pressao.MEDIA, Largura.MEDIA, List.copyOf(titulares), List.copyOf(banco));
    }

    static NovoPlano com(NovoPlano base, List<TitularEscalado> titulares,
                         List<Long> banco, Long capitaoId) {
        return new NovoPlano(base.formacaoId(), capitaoId, base.mentalidade(), base.ritmo(),
                base.linhaDefensiva(), base.pressao(), base.largura(), titulares, banco);
    }
}
```

`CenarioDeElenco` garante os dois goleiros (índices 0 e 1) e 20 jogadores de linha, então `linha` tem ao menos 15 elementos e os `subList` acima não estouram. `deveRejeitarDoisGoleirosEntreOsTitulares`, no Step 1, monta o caso inválido de propósito trocando o slot 2 pelo segundo goleiro.

- [ ] **Step 3: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*EscalacaoInvalidaTest'`
Expected: FAIL na compilação — `TaticaService` não existe.

- [ ] **Step 4: Criar a exceção e o handler**

```java
// shared/exception/EscalacaoInvalidaException.java
package br.com.api.footfirma.shared.exception;

/**
 * A escalação violou uma invariante de agregado — número de titulares, goleiro,
 * vínculo, capitão, banco.
 *
 * <p>Mora aqui, e não no módulo {@code tatica}, pelo mesmo motivo de
 * {@link DistribuicaoInvalidaException}: quem a traduz para HTTP é o
 * {@code TratadorDeErros} de {@code shared}, e ele não pode enxergar tipo interno de
 * módulo de domínio.
 */
public class EscalacaoInvalidaException extends RuntimeException {

    public EscalacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
```

Em `TratadorDeErros`, acrescente o handler ao lado dos que já existem:

```java
    @ExceptionHandler(EscalacaoInvalidaException.class)
    ProblemDetail escalacaoInvalida(EscalacaoInvalidaException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
        problema.setTitle("Escalação inválida");
        return problema;
    }
```

- [ ] **Step 5: Criar os DTOs de entrada, a porta e a implementação**

```java
// tatica/dto/TitularEscalado.java
package br.com.api.footfirma.tatica.dto;

public record TitularEscalado(long jogadorId, int slotOrdem) {
}
```

```java
// tatica/dto/NovoPlano.java
package br.com.api.footfirma.tatica.dto;

import java.util.List;

/**
 * @param banco ids em ordem de banco — a posição na lista vira {@code ordem_banco}.
 * @param capitaoId opcional; quando informado precisa estar entre os titulares.
 */
public record NovoPlano(long formacaoId, Long capitaoId,
                        Mentalidade mentalidade, Ritmo ritmo, LinhaDefensiva linhaDefensiva,
                        Pressao pressao, Largura largura,
                        List<TitularEscalado> titulares, List<Long> banco) {
}
```

```java
// tatica/TaticaService.java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.tatica.dto.NovoPlano;

/**
 * A única porta pública do módulo.
 *
 * <p>Ela é o único caminho de escrita de propósito: "11 titulares, exatamente um
 * goleiro, ninguém repetido, todos do elenco" são invariantes de agregado, e
 * {@code check} não enxerga outras linhas. Quem escrever {@code plano_escalacao} por
 * fora produz um time de dez com dois goleiros, e o banco aceita.
 */
public interface TaticaService {

    /**
     * Grava uma versão nova do plano do clube e a torna vigente. Não existe update:
     * alterar é sempre versionar.
     *
     * @return o id do plano gravado
     * @throws br.com.api.footfirma.shared.exception.EscalacaoInvalidaException
     *         se qualquer invariante de agregado for violada
     */
    long salvarPlano(long clubeId, long temporadaId, NovoPlano plano);
}
```

```java
// tatica/internal/ValidadorDeEscalacao.java
package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.shared.exception.EscalacaoInvalidaException;
import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.TitularEscalado;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * As cinco invariantes que nenhum {@code check} de linha alcança, porque nenhuma
 * enxerga as outras linhas do agregado.
 */
final class ValidadorDeEscalacao {

    private ValidadorDeEscalacao() {
    }

    static void validar(NovoPlano plano, Set<Long> elencoDoClube, long posicaoDeGoleiro,
                        Map<Long, Long> principalPorJogador, List<Long> posicaoPorSlot) {
        if (plano.titulares().size() != ConstantesDeTatica.TITULARES) {
            throw new EscalacaoInvalidaException(
                    "%d titulares, esperados %d".formatted(
                            plano.titulares().size(), ConstantesDeTatica.TITULARES));
        }

        var slots = plano.titulares().stream()
                .map(TitularEscalado::slotOrdem)
                .collect(Collectors.toSet());
        if (slots.size() != ConstantesDeTatica.TITULARES) {
            throw new EscalacaoInvalidaException("dois titulares no mesmo slot");
        }
        if (slots.stream().anyMatch(slot -> slot < 1 || slot > ConstantesDeTatica.TITULARES)) {
            throw new EscalacaoInvalidaException("slot fora da faixa 1-11");
        }

        var escalados = new HashSet<Long>();
        for (var titular : plano.titulares()) {
            if (!escalados.add(titular.jogadorId())) {
                throw new EscalacaoInvalidaException(
                        "jogador %d repetido na escalação".formatted(titular.jogadorId()));
            }
        }
        for (var reserva : plano.banco()) {
            if (!escalados.add(reserva)) {
                throw new EscalacaoInvalidaException(
                        "jogador %d repetido na escalação".formatted(reserva));
            }
        }

        for (var jogadorId : escalados) {
            if (!elencoDoClube.contains(jogadorId)) {
                throw new EscalacaoInvalidaException(
                        "jogador %d não tem vínculo com o clube nesta temporada".formatted(jogadorId));
            }
        }

        if (plano.banco().size() < ConstantesDeTatica.BANCO_MINIMO
                || plano.banco().size() > ConstantesDeTatica.BANCO_MAXIMO) {
            throw new EscalacaoInvalidaException(
                    "banco com %d jogadores, esperado entre %d e %d".formatted(
                            plano.banco().size(), ConstantesDeTatica.BANCO_MINIMO,
                            ConstantesDeTatica.BANCO_MAXIMO));
        }

        var goleiroNoBanco = plano.banco().stream()
                .anyMatch(reserva -> posicaoDeGoleiro == principalPorJogador.getOrDefault(reserva, -1L));
        if (!goleiroNoBanco) {
            throw new EscalacaoInvalidaException("banco sem goleiro reserva");
        }

        if (plano.capitaoId() != null) {
            var titular = plano.titulares().stream()
                    .anyMatch(escalado -> escalado.jogadorId() == plano.capitaoId());
            if (!titular) {
                throw new EscalacaoInvalidaException(
                        "capitão %d não está entre os titulares".formatted(plano.capitaoId()));
            }
        }
    }
}
```

**Por que não há uma checagem de "exatamente um goleiro entre os titulares".** O spec nomeia essa invariante, mas ela é inalcançável: `posicaoPorSlot` vem da formação, toda formação tem exatamente um slot `GOL` (travado por `CatalogoDeFormacaoTest`), e os slots são validados como exatamente `{1..11}`. Logo exatamente um titular ocupa o slot de goleiro, sempre — não existe entrada que produza dois. Escalar um zagueiro no gol continua permitido e continua custando caro pela aptidão, como a decisão 4 do spec quer.

A invariante que sobra de pé é a do banco: um time sem goleiro reserva é um problema real, e é o que a assinatura acima passa a exigir. `validar` recebe por isso um parâmetro a mais, `Map<Long, Long> principalPorJogador`, que `TaticaServiceImpl` já monta.

`TaticaServiceImpl` implementa `salvarPlano` com esta sequência, tudo em um método `@Transactional`:

```java
// tatica/internal/TaticaServiceImpl.java (esqueleto do método)
    @Override
    @Transactional
    public long salvarPlano(long clubeId, long temporadaId, NovoPlano novo) {
        var posicaoPorSlot = formacaoSlotRepository
                .findByFormacaoIdOrderByOrdemAsc(novo.formacaoId()).stream()
                .map(FormacaoSlot::getPosicaoId)
                .toList();
        if (posicaoPorSlot.size() != ConstantesDeTatica.TITULARES) {
            throw new RecursoNaoEncontradoException(
                    "formação %d não existe no catálogo".formatted(novo.formacaoId()));
        }

        var elenco = jogadorService.listarElencoParaEscalacao(clubeId, temporadaId);
        var idsDoElenco = elenco.stream().map(JogadorDoElenco::jogadorId)
                .collect(Collectors.toSet());
        var principalPorJogador = elenco.stream().collect(Collectors.toMap(
                JogadorDoElenco::jogadorId, JogadorDoElenco::posicaoPrincipalId));

        ValidadorDeEscalacao.validar(novo, idsDoElenco, idDaPosicaoGoleiro(),
                principalPorJogador, posicaoPorSlot);

        var overalls = tabelaDe(idsDoElenco, temporadaId);

        planoTaticoRepository.findByClubeIdAndTemporadaIdAndVigenteTrue(clubeId, temporadaId)
                .ifPresent(anterior -> anterior.setVigente(false));
        planoTaticoRepository.flush();

        var plano = novaVersao(clubeId, temporadaId, novo, OrigemDoPlano.MANUAL);
        planoTaticoRepository.save(plano);

        gravarLinhas(plano, novo, posicaoPorSlot, principalPorJogador, overalls);
        return plano.getId();
    }
```

`idDaPosicaoGoleiro()` lê `select id from posicao where codigo = 'GOL'` uma vez e guarda em campo — a tabela é catálogo fixo. Use `JdbcTemplate` ou um repository próprio; **não** alcance `jogador.repository`.

O `flush()` entre desmarcar o vigente e inserir o novo é obrigatório: sem ele o Hibernate pode emitir o insert antes do update e `uq_plano_vigente` reprova.

- [ ] **Step 6: Rodar o teste**

Run: `./gradlew test --tests '*EscalacaoInvalidaTest'`
Expected: PASS, nove testes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/api/footfirma/shared/exception \
        src/main/java/br/com/api/footfirma/tatica \
        src/test/java/br/com/api/footfirma/tatica
git commit -m "feat(tatica): valida a escalação na única porta de escrita"
```

---

### Task 8: Leitura do plano vigente e o par congelado/atual

**Files:**
- Create: `tatica/dto/{PlanoVigente,Escalado}.java`
- Modify: `tatica/TaticaService.java` (+ `buscarPlanoVigente`)
- Modify: `tatica/internal/TaticaServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/AptidaoCongeladaTest.java`

**Interfaces:**
- Consumes: `salvarPlano` (Task 7); `AvaliacaoService.listarOveralls` (Task 2)
- Produces: `TaticaService.buscarPlanoVigente(long clubeId, long temporadaId) -> Optional<PlanoVigente>`; `PlanoVigente`; `Escalado(long jogadorId, long posicaoId, Integer slotOrdem, Integer ordemBanco, int aptidaoNoMomento, int aptidaoAtual, boolean irregular)`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AptidaoCongeladaTest {

    @Autowired TaticaService tatica;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deveManterOCongeladoEMoverOAtualQuandoOOverallMuda() {
        var cenario = CenarioDeElenco.montar(jdbc);
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario));

        var antes = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId()).orElseThrow();
        var titular = antes.titulares().getFirst();
        assertThat(titular.aptidaoNoMomento()).isEqualTo(titular.aptidaoAtual());

        jdbc.update("""
                update jogador_overall set overall = 99
                where jogador_id = ? and temporada_id = ? and posicao_id = ?
                """, titular.jogadorId(), cenario.temporadaId(), titular.posicaoId());

        var depois = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId()).orElseThrow();
        var mesmo = depois.titulares().getFirst();

        assertThat(mesmo.aptidaoNoMomento())
                .as("o histórico não se reescreve")
                .isEqualTo(titular.aptidaoNoMomento());
        assertThat(mesmo.aptidaoAtual())
                .as("a partida não joga com dado velho")
                .isEqualTo(99);
    }

    @Test
    void naoDeveMoverNenhumaDasDuasQuandoAPosicaoPrincipalDoReservaMuda() {
        var cenario = CenarioDeElenco.montar(jdbc);
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario));

        var antes = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId()).orElseThrow();
        var reserva = antes.banco().getFirst();

        var outraPosicao = cenario.posicoesEmOrdem().stream()
                .filter(id -> !id.equals(reserva.posicaoId()))
                .findFirst().orElseThrow();
        jdbc.update("update jogador set posicao_principal_id = ? where id = ?",
                outraPosicao, reserva.jogadorId());

        var depois = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId()).orElseThrow();
        var mesmo = depois.banco().getFirst();

        assertThat(mesmo.posicaoId())
                .as("a posição do congelamento é lida de plano_escalacao, não derivada")
                .isEqualTo(reserva.posicaoId());
        assertThat(mesmo.aptidaoNoMomento()).isEqualTo(reserva.aptidaoNoMomento());
        assertThat(mesmo.aptidaoAtual()).isEqualTo(reserva.aptidaoAtual());
    }

    @Test
    void deveMarcarComoIrregularOTitularQueSaiuDoClube() {
        var cenario = CenarioDeElenco.montar(jdbc);
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.valido(jdbc, cenario));

        var antes = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId()).orElseThrow();
        assertThat(antes.titulares()).allSatisfy(escalado ->
                assertThat(escalado.irregular()).isFalse());

        var saindo = antes.titulares().getFirst().jogadorId();
        jdbc.update("delete from jogador_vinculo where jogador_id = ? and clube_id = ?",
                saindo, cenario.clubeId());

        var depois = tatica.buscarPlanoVigente(cenario.clubeId(), cenario.temporadaId()).orElseThrow();

        assertThat(depois.titulares()).filteredOn(escalado -> escalado.jogadorId() == saindo)
                .singleElement()
                .satisfies(escalado -> assertThat(escalado.irregular()).isTrue());
    }

    @Test
    void deveVoltarVazioQuandoOClubeNaoTemPlano() {
        var cenario = CenarioDeElenco.montar(jdbc);

        assertThat(tatica.buscarPlanoVigente(cenario.clubeSemTreinadorId(), cenario.temporadaId()))
                .isEmpty();
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*AptidaoCongeladaTest'`
Expected: FAIL na compilação — `buscarPlanoVigente` não existe.

- [ ] **Step 3: Criar os DTOs de saída**

```java
// tatica/dto/Escalado.java
package br.com.api.footfirma.tatica.dto;

/**
 * Uma linha da escalação, do ponto de vista de quem lê o plano.
 *
 * @param aptidaoNoMomento o overall congelado na gravação — o que o treinador viu.
 * @param aptidaoAtual     o overall de hoje na mesma posição — o que vale em campo.
 * @param irregular        o jogador não tem mais vínculo com o clube na temporada.
 */
public record Escalado(long jogadorId, long posicaoId, Integer slotOrdem, Integer ordemBanco,
                       int aptidaoNoMomento, int aptidaoAtual, boolean irregular) {
}
```

```java
// tatica/dto/PlanoVigente.java
package br.com.api.footfirma.tatica.dto;

import java.util.List;

/**
 * O contrato que o módulo {@code partida} vai consumir.
 *
 * @param titulares em ordem de slot.
 * @param banco     em ordem de banco.
 */
public record PlanoVigente(long planoId, long clubeId, long temporadaId, int versao,
                           OrigemDoPlano origem, long formacaoId, String formacaoCodigo,
                           Mentalidade mentalidade, Ritmo ritmo, LinhaDefensiva linhaDefensiva,
                           Pressao pressao, Largura largura, Long capitaoId,
                           List<Escalado> titulares, List<Escalado> banco) {
}
```

- [ ] **Step 4: Implementar a leitura**

Em `TaticaService`:

```java
    /**
     * O plano vigente do clube, com a aptidão congelada e a atual lado a lado.
     *
     * <p>Leitura pura: devolve vazio quando não há plano, e nunca cria um. Quem quer
     * garantir que exista chama {@code garantirPlanoVigente}.
     */
    Optional<PlanoVigente> buscarPlanoVigente(long clubeId, long temporadaId);
```

Em `TaticaServiceImpl`:

```java
    @Override
    public Optional<PlanoVigente> buscarPlanoVigente(long clubeId, long temporadaId) {
        return planoTaticoRepository
                .findByClubeIdAndTemporadaIdAndVigenteTrue(clubeId, temporadaId)
                .map(plano -> montar(plano, temporadaId));
    }

    private PlanoVigente montar(PlanoTatico plano, long temporadaId) {
        var linhas = planoEscalacaoRepository.findByPlanoId(plano.getId());
        var jogadorIds = linhas.stream().map(PlanoEscalacao::getJogadorId).toList();

        var atuais = avaliacaoService.listarOveralls(jogadorIds, temporadaId).stream()
                .collect(Collectors.toMap(
                        overall -> new AbstractMap.SimpleEntry<>(overall.jogadorId(), overall.posicaoId()),
                        OverallDeJogador::overall));

        var comVinculo = jogadorService
                .listarElencoParaEscalacao(plano.getClubeId(), temporadaId).stream()
                .map(JogadorDoElenco::jogadorId)
                .collect(Collectors.toSet());

        var titulares = linhas.stream()
                .filter(linha -> linha.getPapel() == Papel.TITULAR)
                .sorted(Comparator.comparingInt(PlanoEscalacao::getSlotOrdem))
                .map(linha -> escalado(linha, atuais, comVinculo))
                .toList();

        var banco = linhas.stream()
                .filter(linha -> linha.getPapel() == Papel.RESERVA)
                .sorted(Comparator.comparingInt(PlanoEscalacao::getOrdemBanco))
                .map(linha -> escalado(linha, atuais, comVinculo))
                .toList();

        var formacao = formacaoRepository.findById(plano.getFormacaoId()).orElseThrow();

        return new PlanoVigente(plano.getId(), plano.getClubeId(), plano.getTemporadaId(),
                plano.getVersao(), plano.getOrigem(), formacao.getId(), formacao.getCodigo(),
                plano.getMentalidade(), plano.getRitmo(), plano.getLinhaDefensiva(),
                plano.getPressao(), plano.getLargura(), plano.getCapitaoId(), titulares, banco);
    }

    private Escalado escalado(PlanoEscalacao linha,
                              Map<Map.Entry<Long, Long>, Integer> atuais,
                              Set<Long> comVinculo) {
        var chave = new AbstractMap.SimpleEntry<>(linha.getJogadorId(), linha.getPosicaoId());
        return new Escalado(linha.getJogadorId(), linha.getPosicaoId(),
                linha.getSlotOrdem(), linha.getOrdemBanco(),
                linha.getAptidaoNoMomento(),
                atuais.getOrDefault(chave, linha.getAptidaoNoMomento()),
                !comVinculo.contains(linha.getJogadorId()));
    }
```

`getOrDefault(chave, linha.getAptidaoNoMomento())` cobre o caso em que o overall foi apagado: sem linha atual, o congelado é a melhor resposta disponível.

- [ ] **Step 5: Rodar o teste**

Run: `./gradlew test --tests '*AptidaoCongeladaTest'`
Expected: PASS, quatro testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/tatica \
        src/test/java/br/com/api/footfirma/tatica/AptidaoCongeladaTest.java
git commit -m "feat(tatica): lê o plano vigente com aptidão congelada e atual"
```

---

### Task 9: `garantirPlanoVigente` — o escalador automático completo

**Files:**
- Create: `tatica/internal/EscaladorAutomatico.java`
- Modify: `tatica/TaticaService.java` (+ `garantirPlanoVigente`)
- Modify: `tatica/internal/TaticaServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/tatica/EscaladorDeterministicoTest.java`

**Interfaces:**
- Consumes: `EscolhedorDeFormacao.escolher` (Task 5); `EscolhedorDeInstrucoes` (Task 6); `TreinadorService.buscarPerfilDoClube` (Task 2); `salvarPlano`/`buscarPlanoVigente` (Tasks 7-8)
- Produces: `TaticaService.garantirPlanoVigente(long clubeId, long temporadaId) -> PlanoVigente`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.shared.exception.EscalacaoInvalidaException;
import br.com.api.footfirma.tatica.dto.Escalado;
import br.com.api.footfirma.tatica.dto.OrigemDoPlano;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EscaladorDeterministicoTest {

    @Autowired TaticaService tatica;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deveMontarOMesmoOnzeEmDuasExecucoes() {
        var cenario = CenarioDeElenco.montar(jdbc);

        var primeiro = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());
        jdbc.update("delete from plano_escalacao");
        jdbc.update("delete from plano_tatico");
        var segundo = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(ids(segundo.titulares())).isEqualTo(ids(primeiro.titulares()));
        assertThat(ids(segundo.banco())).isEqualTo(ids(primeiro.banco()));
        assertThat(segundo.formacaoId()).isEqualTo(primeiro.formacaoId());
        assertThat(segundo.mentalidade()).isEqualTo(primeiro.mentalidade());
        assertThat(segundo.capitaoId()).isEqualTo(primeiro.capitaoId());
    }

    @Test
    void deveDevolverOPlanoExistenteSemCriarOutro() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var criado = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        var segunda = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(segunda.planoId()).isEqualTo(criado.planoId());
        assertThat(jdbc.queryForObject("select count(*) from plano_tatico", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void deveGravarOPlanoComOrigemAutomatica() {
        var cenario = CenarioDeElenco.montar(jdbc);

        var plano = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(plano.origem()).isEqualTo(OrigemDoPlano.AUTOMATICO);
        assertThat(plano.titulares()).hasSize(11);
        assertThat(plano.banco()).isNotEmpty();
        assertThat(plano.capitaoId()).isNotNull();
    }

    @Test
    void deveCairNoRepertorioMinimoQuandoNaoHaSkillGravada() {
        var cenario = CenarioDeElenco.montar(jdbc);
        jdbc.update("delete from treinador_skill where temporada_id = ?", cenario.temporadaId());

        var plano = tatica.garantirPlanoVigente(cenario.clubeId(), cenario.temporadaId());

        assertThat(plano.titulares()).hasSize(11);
        assertThat(plano.formacaoCodigo())
                .as("sem skill, o repertório é só a primeira formação do catálogo")
                .isEqualTo("4-4-2");
    }

    @Test
    void deveEstourarQuandoOElencoNaoFechaOTime() {
        var cenario = CenarioDeElenco.montar(jdbc);
        jdbc.update("delete from jogador_vinculo where clube_id = ?", cenario.clubeId());

        assertThatThrownBy(() -> tatica.garantirPlanoVigente(cenario.clubeId(),
                cenario.temporadaId()))
                .isInstanceOf(EscalacaoInvalidaException.class)
                .hasMessageContaining("elenco");
    }

    private static List<Long> ids(List<Escalado> escalados) {
        return escalados.stream().map(Escalado::jogadorId).toList();
    }
}
```

Note que os cinco testes usam `clubeId`, nunca `clubeSemTreinadorId`: aquele clube existe no cenário sem elenco, e serve só para provar o caminho vazio de `buscarPerfilDoClube` (Task 2) e de `buscarPlanoVigente` (Task 8). Escalar um clube sem elenco é o caso de `deveEstourarQuandoOElencoNaoFechaOTime`, que apaga os vínculos do clube que tem um.

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*EscaladorDeterministicoTest'`
Expected: FAIL na compilação — `garantirPlanoVigente` não existe.

- [ ] **Step 3: Implementar o escalador**

```java
// tatica/internal/EscaladorAutomatico.java
package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.TitularEscalado;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Monta um plano completo sem intervenção humana: formação pelo repertório da skill
 * TATICA, onze pelo guloso de escassez, instruções pela reputação do clube e banco
 * pelos melhores restantes na posição principal.
 *
 * <p>Determinístico de ponta a ponta — nenhum sorteio, nenhum relógio. É o que
 * {@code EscaladorDeterministicoTest} trava.
 */
final class EscaladorAutomatico {

    private EscaladorAutomatico() {
    }

    static Optional<NovoPlano> montar(List<FormacaoCandidata> catalogo, int tatica,
                                      List<JogadorDisponivel> elenco, TabelaDeOveralls overalls,
                                      long posicaoDeGoleiroId,
                                      int reputacaoDoClube, double mediaDeReputacao) {
        var escolha = EscolhedorDeFormacao.escolher(catalogo, tatica, elenco, overalls);
        if (escolha.isEmpty()) {
            return Optional.empty();
        }

        var onze = escolha.get().onze();
        var titulares = onze.slots().stream()
                .map(slot -> new TitularEscalado(slot.jogadorId(), slot.slotOrdem()))
                .toList();

        var escalados = onze.slots().stream()
                .map(SlotPreenchido::jogadorId)
                .collect(Collectors.toSet());

        var banco = montarBanco(elenco, overalls, escalados, posicaoDeGoleiroId);
        if (banco.size() < ConstantesDeTatica.BANCO_MINIMO) {
            return Optional.empty();
        }

        var instrucoes = EscolhedorDeInstrucoes.instrucoesDe(
                EscolhedorDeInstrucoes.mentalidadeDe(reputacaoDoClube, mediaDeReputacao));

        return Optional.of(new NovoPlano(escolha.get().formacao().formacaoId(),
                EscolhedorDeInstrucoes.capitaoDe(onze),
                instrucoes.mentalidade(), instrucoes.ritmo(), instrucoes.linhaDefensiva(),
                instrucoes.pressao(), instrucoes.largura(), titulares, banco));
    }

    /**
     * Os melhores restantes pelo overall na posição principal, com ao menos um goleiro.
     * O goleiro entra primeiro para nunca ser cortado pelo teto do banco.
     */
    private static List<Long> montarBanco(List<JogadorDisponivel> elenco,
                                          TabelaDeOveralls overalls, Set<Long> jaEscalados,
                                          long posicaoDeGoleiroId) {
        var sobra = elenco.stream()
                .filter(jogador -> !jaEscalados.contains(jogador.jogadorId()))
                .sorted(Comparator
                        .comparingInt((JogadorDisponivel jogador) ->
                                -overalls.overall(jogador.jogadorId(), jogador.posicaoPrincipalId()))
                        .thenComparingLong(JogadorDisponivel::jogadorId))
                .toList();

        var banco = new ArrayList<Long>();
        sobra.stream()
                .filter(jogador -> jogador.posicaoPrincipalId() == posicaoDeGoleiroId)
                .findFirst()
                .ifPresent(goleiro -> banco.add(goleiro.jogadorId()));

        for (var jogador : sobra) {
            if (banco.size() >= ConstantesDeTatica.BANCO_MAXIMO) {
                break;
            }
            if (!banco.contains(jogador.jogadorId())) {
                banco.add(jogador.jogadorId());
            }
        }

        return List.copyOf(banco);
    }
}
```

O goleiro entra no banco antes do laço para nunca ser cortado pelo teto: sem isso, um elenco com muitos jogadores de linha melhores empurraria o goleiro reserva para fora, e `ValidadorDeEscalacao` reprovaria o plano que o próprio escalador montou.

- [ ] **Step 4: Ligar no service**

Em `TaticaService`:

```java
    /**
     * Devolve o plano vigente do clube, criando um automaticamente se não houver.
     *
     * <p>Método explícito, e não efeito colateral de leitura: quem chama é o gerador de
     * mundo e, no futuro, a partida no apito inicial.
     *
     * @throws br.com.api.footfirma.shared.exception.EscalacaoInvalidaException
     *         se o elenco não fecha um time — falhar alto é melhor do que a partida
     *         descobrir isso em campo
     */
    PlanoVigente garantirPlanoVigente(long clubeId, long temporadaId);
```

Em `TaticaServiceImpl`, dentro de um método `@Transactional`: devolve o vigente se houver; se não, lê o catálogo, o elenco, os overalls, o perfil do treinador (`buscarPerfilDoClube`, caindo em `tatica = 0` quando vazio), a reputação do clube e a média global, chama `EscaladorAutomatico.montar` e grava por um caminho interno que reusa a mesma gravação de `salvarPlano` com `OrigemDoPlano.AUTOMATICO`. Quando `montar` devolver vazio, lance:

```java
    throw new EscalacaoInvalidaException(
            "elenco do clube %d não fecha um time na temporada %d".formatted(clubeId, temporadaId));
```

A média global sai de uma query simples — `select avg(reputacao) from clube`. Não alcance `clube.repository`: use `JdbcTemplate`, como o `idDaPosicaoGoleiro()` da Task 7.

- [ ] **Step 5: Rodar o teste**

Run: `./gradlew test --tests '*EscaladorDeterministicoTest'`
Expected: PASS, cinco testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/tatica \
        src/test/java/br/com/api/footfirma/tatica
git commit -m "feat(tatica): escala automaticamente e de forma determinística"
```

---

### Task 10: Versionamento, suíte completa e fechamento

**Files:**
- Test: `src/test/java/br/com/api/footfirma/tatica/VersionamentoTest.java`
- Modify: `docs/superpowers/specs/2026-08-05-tatica-escalacao-design.md` (só o `Status:`)
- Modify: `AGENTS.md` (seção "Estado atual")

**Interfaces:**
- Consumes: tudo das Tasks 1-9
- Produces: nada — é a tarefa de fechamento

- [ ] **Step 1: Escrever o teste de versionamento**

```java
package br.com.api.footfirma.tatica;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VersionamentoTest {

    @Autowired TaticaService tatica;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deveGerarVersaoNovaEDeixarSoAUltimaVigente() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var primeiro = PlanoDeTeste.valido(jdbc, cenario);
        var primeiroId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), primeiro);

        // Troca o último titular por alguém do banco: escalação diferente, plano novo.
        var titulares = new ArrayList<>(primeiro.titulares());
        var banco = new ArrayList<>(primeiro.banco());
        var entrando = banco.removeFirst();
        var saindo = titulares.removeLast();
        titulares.add(new br.com.api.footfirma.tatica.dto.TitularEscalado(
                entrando, saindo.slotOrdem()));
        banco.add(saindo.jogadorId());

        var segundoId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(),
                PlanoDeTeste.com(primeiro, titulares, banco, primeiro.capitaoId()));

        assertThat(segundoId).isNotEqualTo(primeiroId);

        var versoes = jdbc.queryForList("""
                select versao, vigente from plano_tatico
                where clube_id = ? and temporada_id = ? order by versao
                """, cenario.clubeId(), cenario.temporadaId());

        assertThat(versoes).hasSize(2);
        assertThat(versoes.get(0).get("versao")).isEqualTo(1);
        assertThat(versoes.get(0).get("vigente")).isEqualTo(false);
        assertThat(versoes.get(1).get("versao")).isEqualTo(2);
        assertThat(versoes.get(1).get("vigente")).isEqualTo(true);
    }

    @Test
    void devePreservarAsLinhasEAAptidaoCongeladaDaVersaoAntiga() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var primeiro = PlanoDeTeste.valido(jdbc, cenario);
        var primeiroId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), primeiro);

        var antigas = jdbc.queryForList(
                "select jogador_id, aptidao_no_momento from plano_escalacao where plano_id = ?",
                primeiroId);

        jdbc.update("update jogador_overall set overall = 5 where temporada_id = ?",
                cenario.temporadaId());
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), primeiro);

        var depois = jdbc.queryForList(
                "select jogador_id, aptidao_no_momento from plano_escalacao where plano_id = ?",
                primeiroId);

        assertThat(depois)
                .as("a versão antiga é registro histórico e não se reescreve")
                .isEqualTo(antigas);
    }

    @Test
    void deveGravarAptidaoNovaNaVersaoNova() {
        var cenario = CenarioDeElenco.montar(jdbc);
        var plano = PlanoDeTeste.valido(jdbc, cenario);
        tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), plano);

        jdbc.update("update jogador_overall set overall = 5 where temporada_id = ?",
                cenario.temporadaId());
        var segundoId = tatica.salvarPlano(cenario.clubeId(), cenario.temporadaId(), plano);

        assertThat(jdbc.queryForList(
                "select distinct aptidao_no_momento from plano_escalacao where plano_id = ?",
                Integer.class, segundoId))
                .containsExactly(5);
    }
}
```

- [ ] **Step 2: Rodar o teste**

Run: `./gradlew test --tests '*VersionamentoTest'`
Expected: PASS, três testes. Se `deveGerarVersaoNovaEDeixarSoAUltimaVigente` reprovar com violação de `uq_plano_vigente`, falta o `flush()` entre desmarcar e inserir — veja o Step 5 da Task 7.

- [ ] **Step 3: Rodar a suíte completa**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Exige Docker rodando.

Esta é a primeira vez no plano que a suíte inteira roda. `ModularidadeTest` é o teste a observar: se ele reprovar citando `tatica.internal` ou `tatica.domain`, algum tipo interno vazou para a assinatura pública de `TaticaService`.

- [ ] **Step 4: Marcar o spec como implementado**

Em `docs/superpowers/specs/2026-08-05-tatica-escalacao-design.md`, troque a linha do cabeçalho:

```
Status: aprovado, não implementado
```

por:

```
Status: implementado em 2026-08-05
```

- [ ] **Step 5: Atualizar o `AGENTS.md`**

Na seção "Estado atual", o backend passa a ter **nove** módulos de domínio — acrescente `tatica` à lista ao lado de `treinador`. Atualize o número de migrations (de dezesseis para vinte e uma) e o de testes, lendo o total real de `./gradlew build`. Atualize também a data de `Status: verificado em`.

Se a seção ainda disser que a progressão entre temporadas é "o próximo passo natural", corrija: as camadas restantes até a partida são **partida** (camada 3) e, antes dela, calendário e rodada, que `competicao` não tem.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/br/com/api/footfirma/tatica/VersionamentoTest.java \
        ../../docs/superpowers/specs/2026-08-05-tatica-escalacao-design.md \
        ../../AGENTS.md
git commit -m "test(tatica): prova o versionamento e fecha o módulo"
```

- [ ] **Step 7: Registro de fechamento**

Escreva no final da conversa: arquivos alterados, validações executadas (com o resultado real de `./gradlew build`), validações não executadas e por quê, e alterações preexistentes no worktree. Não diga "pronto" sem ter rodado o comando que sustenta a frase.

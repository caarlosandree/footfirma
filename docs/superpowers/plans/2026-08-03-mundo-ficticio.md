# Mundo fictício gerado — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remover o módulo `importacao` e entregar o módulo `mundo`, que gera duas ligas nacionais fictícias com 40 clubes e 1.520 jogadores balanceados a partir de uma semente.

**Architecture:** `mundo` orquestra e não escreve em tabela alheia: toda escrita passa pelos métodos `sincronizar*` das interfaces públicas de `temporada`, `clube`, `competicao` e `jogador`, exatamente como o importador fazia. As fábricas de conteúdo (arquétipos, nomes, elenco, atributos) são **puras e determinísticas** — recebem um `SplittableRandom` e devolvem records, sem tocar em Spring nem em banco —, o que mantém o orquestrador com poucas dependências e permite testes unitários sem Docker.

**Tech Stack:** Java 25 · Spring Boot 4.1 · Spring Modulith 2.1 · PostgreSQL · Flyway · Gradle · JUnit 5 + AssertJ + Testcontainers

**Spec:** `docs/superpowers/specs/2026-08-03-mundo-ficticio-design.md`

## Global Constraints

- Trabalhe em `backend/footfirma/`. Todo caminho relativo neste plano parte daí.
- Pacote raiz: `br.com.api.footfirma`. Módulo novo: `br.com.api.footfirma.mundo`.
- **Nomenclatura em português**: classes, métodos, variáveis, colunas e mensagens de commit.
- **Nenhum módulo importa subpacote `internal` de outro módulo.** `ModularidadeTest` verifica isso e precisa ficar verde em toda task.
- **Migration aplicada é imutável.** Toda mudança de schema é um arquivo `V{n}__descricao.sql` novo. O guard bloqueia editar migration já versionada.
- Injeção **somente por construtor**, com `@RequiredArgsConstructor` do Lombok. `@Autowired` em campo é proibido.
- `record` para todo DTO e value object. `Optional<T>` para retorno ausente; nunca `null` de método público.
- Testes de persistência usam **Testcontainers**, nunca H2 e nunca mock de repository.
- Commits em **Conventional Commits, em português, sem `Co-authored-by:`**.
- **Não suba serviços** (`./gradlew bootRun`, `docker compose up`) — o guard bloqueia. Validação é via `./gradlew compileJava` e `./gradlew test --tests '*Nome*'`.
- **Não faça `git push`.** Commits locais apenas.
- Comandos canônicos: `./gradlew compileJava` (rápido) · `./gradlew test --tests '*X*'` (exige Docker) · `./gradlew build` (suíte completa).

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `mundo/package-info.java` | declara o módulo Modulith |
| `mundo/MundoService.java` | interface pública: `gerar()` |
| `mundo/dto/RelatorioDeMundo.java` | semente usada e contagens |
| `mundo/dto/ContagemPorEntidade.java` | entidade, criados, atualizados |
| `mundo/internal/MundoServiceImpl.java` | orquestrador: traduz records gerados em `DadosDe*` e chama os módulos |
| `mundo/internal/CatalogoDeArquetipos.java` | os seis arquétipos e a distribuição por divisão |
| `mundo/internal/Arquetipo.java` | record de faixas |
| `mundo/internal/ClubeGerado.java` | clube antes de virar linha |
| `mundo/internal/JogadorGerado.java` | jogador antes de virar linha |
| `mundo/internal/AtributosGerados.java` | as 18 skills |
| `mundo/internal/PapelNoElenco.java` | enum dos papéis e suas faixas |
| `mundo/internal/GeradorDeNomes.java` | lista curada de clubes, cidades e pools de nomes de jogador |
| `mundo/internal/FabricaDeClubes.java` | arquétipo + sorteio → `ClubeGerado` |
| `mundo/internal/FabricaDeElenco.java` | clube → 38 `JogadorGerado` |
| `mundo/internal/FabricaDeAtributos.java` | alvo + posição → `AtributosGerados` |
| `mundo/internal/ChaveDeJogador.java` | chave natural e semente (movido de `importacao`) |
| `mundo/internal/LimpezaDoCatalogo.java` | apaga o catálogo na ordem inversa das FKs |
| `mundo/internal/PropriedadesDeMundo.java` | `footfirma.mundo.*` |
| `mundo/internal/MundoRunner.java` | `ApplicationRunner` sob profile `mundo` |

---

### Task 1: Remover o módulo `importacao`

**Files:**
- Delete: `src/main/java/br/com/api/footfirma/importacao/` (diretório inteiro, 25 arquivos)
- Delete: `src/test/java/br/com/api/footfirma/importacao/` (diretório inteiro, 10 arquivos)
- Delete: `fixtures/` (diretório inteiro: `README.md` e `v1/` com 15 CSVs e `manifest.json`)
- Delete: `docs/adr/2026-08-03-ingestao-pelos-modulos.md`
- Delete: `docs/runbooks/importacao.md`
- Create: `src/main/resources/db/migration/V15__remove_importacao.sql`
- Modify: `build.gradle` (remover a task `gerarFixtures` e a `systemProperty` das fixtures)
- Modify: `src/main/java/br/com/api/footfirma/competicao/dto/package-info.java`
- Modify: `src/main/java/br/com/api/footfirma/geografia/dto/package-info.java`

**Interfaces:**
- Consumes: nada.
- Produces: um repositório sem `ImportacaoService`, sem `ChaveNatural` e sem as três tabelas `importacao_*`. As interfaces `ClubeService`, `JogadorService`, `CompeticaoService`, `TemporadaService`, `GeografiaService` e `AvaliacaoService` ficam intactas — são elas que a Task 3 em diante consome.

- [ ] **Step 1: Apagar código, testes, fixtures e documentos**

```bash
cd backend/footfirma
git rm -r --quiet src/main/java/br/com/api/footfirma/importacao
git rm -r --quiet src/test/java/br/com/api/footfirma/importacao
git rm -r --quiet fixtures
git rm --quiet docs/adr/2026-08-03-ingestao-pelos-modulos.md
git rm --quiet docs/runbooks/importacao.md
```

- [ ] **Step 2: Remover a task `gerarFixtures` e a system property do `build.gradle`**

Apague este bloco inteiro do fim de `build.gradle`:

```groovy
// Regenera fixtures/v1. Não roda na suíte: o resultado é versionado, e regerar a
// cada build produziria diff em todo commit.
tasks.register('gerarFixtures', JavaExec) {
    group = 'footfirma'
    description = 'Regenera fixtures/v1 (15 CSVs + manifest.json)'
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'br.com.api.footfirma.importacao.internal.GeradorDeFixtures'
    args = [file('fixtures/v1').absolutePath]
}
```

E reduza a task `test` para:

```groovy
tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Limpar as menções ao importador nos dois `package-info`**

Em `competicao/dto/package-info.java` e `geografia/dto/package-info.java`, troque toda menção a "importador" por "gerador de mundo". Encontre-as com:

```bash
grep -n "importador\|importação" src/main/java/br/com/api/footfirma/competicao/dto/package-info.java \
                                src/main/java/br/com/api/footfirma/geografia/dto/package-info.java
```

Faça o mesmo nos javadocs de `TemporadaService.sincronizar` e `GeografiaService.buscarEstadoPorUf`, que citam o importador nominalmente.

- [ ] **Step 4: Escrever a migration que dropa as tabelas de importação**

Crie `src/main/resources/db/migration/V15__remove_importacao.sql`:

```sql
-- O importador existia para desconfiar de dado externo: manifesto, checksum e
-- ocorrência por linha recusada. Sem fonte externa não há do que desconfiar, e as
-- três tabelas de auditoria de carga perdem o sujeito.
--
-- A ordem é a inversa das dependências: ocorrência e contagem apontam para execução.
drop table if exists importacao_ocorrencia;
drop table if exists importacao_contagem;
drop table if exists importacao_execucao;
```

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. Se falhar por símbolo não resolvido, sobrou referência ao pacote `importacao` — encontre com `grep -rn "footfirma.importacao" src/`.

- [ ] **Step 6: Rodar a suíte inteira**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. Os testes de importação não existem mais; os demais não dependiam deles.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(importacao)!: remove a ingestão por CSV

O importador existe para desconfiar de dado que vem de fora. O mundo passa a
ser gerado, e manifesto, checksum e ocorrência viram cerimônia sobre um arquivo
que o próprio sistema acabou de escrever.

Saem 25 classes de produção, 10 de teste, o dataset fixtures/v1, a task
gerarFixtures, o ADR de ingestão e o runbook de carga. V15 dropa as três
tabelas de auditoria.

BREAKING CHANGE: o profile `importacao` e as tabelas importacao_* não existem mais.
EOF
)"
```

---

### Task 2: Colunas de riqueza e de categoria de elenco

**Files:**
- Create: `src/main/resources/db/migration/V16__adiciona_forca_financeira_e_categoria.sql`
- Modify: `src/main/java/br/com/api/footfirma/clube/domain/Clube.java`
- Modify: `src/main/java/br/com/api/footfirma/clube/dto/DadosDeClube.java`
- Modify: `src/main/java/br/com/api/footfirma/clube/internal/ClubeServiceImpl.java:69-96`
- Modify: `src/main/java/br/com/api/footfirma/jogador/domain/JogadorVinculo.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeVinculo.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/internal/JogadorServiceImpl.java:301-316`
- Test: `src/test/java/br/com/api/footfirma/clube/ClubeServiceSincronizacaoTest.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorServiceSincronizacaoTest.java`

**Interfaces:**
- Consumes: `ResultadoDeSincronizacao`, `ClubeService.sincronizarClube`, `JogadorService.sincronizarVinculo`.
- Produces:
  - `DadosDeClube(String slug, String nomeOficial, String nomeCurto, String apelido, Integer anoFundacao, Long paisId, Long estadoId, Long estadioId, String corPrimaria, String corSecundaria, Integer reputacao, Integer forcaFinanceira, Integer qualidadeBase, Long estadoBaseId)` — `forcaFinanceira` entra **entre** `reputacao` e `qualidadeBase`.
  - `DadosDeVinculo(Long jogadorId, Long clubeId, Long temporadaId, String tipo, String categoria, Integer numeroCamisa, LocalDate dataInicio, LocalDate dataFim, BigDecimal valorMercadoEur)` — `categoria` entra depois de `tipo`.

- [ ] **Step 1: Escrever o teste que falha — clube com força financeira**

Em `ClubeServiceSincronizacaoTest.java`, acrescente:

```java
@Test
void deveGravarForcaFinanceiraDoClube() {
    var dados = new DadosDeClube("sinc-omega", "Omega Futebol Clube", "Omega", null,
            1912, paisId, null, null, "#101010", "#FFFFFF", 71, 44, 88, null);

    var resultado = clubeService.sincronizarClube(dados);

    var forca = jdbcTemplate.queryForObject(
            "select forca_financeira from clube where id = ?", Integer.class, resultado.id());
    assertThat(forca).isEqualTo(44);
}
```

Se a classe ainda não tiver `JdbcTemplate`, injete-o com `@Autowired JdbcTemplate jdbcTemplate;` e importe `org.springframework.jdbc.core.JdbcTemplate`.

- [ ] **Step 2: Escrever o teste que falha — vínculo de base**

Em `JogadorServiceSincronizacaoTest.java`, acrescente:

```java
@Test
void deveGravarVinculoDeBase() {
    var dados = new DadosDeVinculo(jogadorId, clubeId, temporadaId, "CONTRATO", "BASE",
            null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

    var resultado = jogadorService.sincronizarVinculo(dados);

    var categoria = jdbcTemplate.queryForObject(
            "select categoria from jogador_vinculo where id = ?", String.class, resultado.id());
    assertThat(categoria).isEqualTo("BASE");
}
```

- [ ] **Step 3: Rodar os dois testes e ver falhar**

Run: `./gradlew test --tests '*ClubeServiceSincronizacaoTest' --tests '*JogadorServiceSincronizacaoTest'`
Expected: FAIL na compilação do teste — o construtor de `DadosDeClube` recebe 13 argumentos, não 14.

- [ ] **Step 4: Escrever a migration**

Crie `src/main/resources/db/migration/V16__adiciona_forca_financeira_e_categoria.sql`:

```sql
-- Riqueza como índice 0-99, irmão de reputacao e qualidade_base. Orçamento em
-- valor monetário não entra enquanto não existir transferência que o gaste.
alter table clube add column forca_financeira integer not null default 50
    check (forca_financeira between 0 and 99);

comment on column clube.forca_financeira is
    'Capacidade financeira do clube (0-99). Alimenta o nível do elenco no gerador de mundo';

-- Base e profissional convivem na mesma tabela: o vínculo já é versionado por
-- temporada, e promover um garoto é gravar o vínculo do ano seguinte como
-- PROFISSIONAL, não migrar linha entre tabelas.
alter table jogador_vinculo add column categoria text not null default 'PROFISSIONAL'
    check (categoria in ('PROFISSIONAL', 'BASE'));

create index idx_jogador_vinculo_categoria on jogador_vinculo (clube_id, categoria);

-- 'IMPORTADO' e 'ESTIMADO' cobriam dado vindo de fora e dado inferido. Atributo
-- gerado não é nenhum dos dois: não foi importado de lugar nenhum nem estimado a
-- partir de observação. 'IMPORTADO' permanece no conjunto porque linhas antigas
-- ainda o usam — o check é sobre o que existe, não sobre o que deveria existir.
alter table jogador_atributo drop constraint jogador_atributo_fonte_atributo_check;
alter table jogador_atributo add constraint jogador_atributo_fonte_atributo_check
    check (fonte_atributo in ('IMPORTADO', 'ESTIMADO', 'GERADO'));
```

Confirme o nome da constraint antes de rodar — o Postgres o gera como
`<tabela>_<coluna>_check`, mas vale conferir:

```bash
grep -n "fonte_atributo" src/main/resources/db/migration/V8__cria_jogador_atributo.sql
```

- [ ] **Step 5: Adicionar o campo à entidade `Clube`**

Em `Clube.java`, depois do campo `reputacao`:

```java
    @Column(name = "forca_financeira", nullable = false)
    private Integer forcaFinanceira;
```

E no construtor público, junto dos outros defaults:

```java
    public Clube(String slug, String nomeOficial, String nomeCurto, Long paisId) {
        this.slug = slug;
        this.nomeOficial = nomeOficial;
        this.nomeCurto = nomeCurto;
        this.paisId = paisId;
        this.reputacao = 50;
        this.forcaFinanceira = 50;
        this.qualidadeBase = 50;
    }
```

- [ ] **Step 6: Adicionar o campo ao `DadosDeClube` e gravá-lo no upsert**

`DadosDeClube.java`:

```java
public record DadosDeClube(String slug, String nomeOficial, String nomeCurto, String apelido,
                           Integer anoFundacao, Long paisId, Long estadoId, Long estadioId,
                           String corPrimaria, String corSecundaria,
                           Integer reputacao, Integer forcaFinanceira, Integer qualidadeBase,
                           Long estadoBaseId) {
}
```

Em `ClubeServiceImpl.sincronizarClube`, entre `setReputacao` e `setQualidadeBase`:

```java
        clube.setForcaFinanceira(dados.forcaFinanceira());
```

- [ ] **Step 7: Adicionar `categoria` ao vínculo**

Crie o enum `src/main/java/br/com/api/footfirma/jogador/domain/CategoriaDeElenco.java`:

```java
package br.com.api.footfirma.jogador.domain;

/** Profissional e base convivem na mesma tabela, distinguidos por esta coluna. */
public enum CategoriaDeElenco {
    PROFISSIONAL,
    BASE
}
```

Em `JogadorVinculo.java`, depois do campo `tipo`:

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaDeElenco categoria;
```

E no construtor, o default:

```java
    public JogadorVinculo(Jogador jogador, Long clubeId, Long temporadaId, TipoVinculo tipo) {
        this.jogador = jogador;
        this.clubeId = clubeId;
        this.temporadaId = temporadaId;
        this.tipo = tipo;
        this.categoria = CategoriaDeElenco.PROFISSIONAL;
    }
```

`DadosDeVinculo.java`:

```java
public record DadosDeVinculo(Long jogadorId, Long clubeId, Long temporadaId, String tipo,
                             String categoria, Integer numeroCamisa, LocalDate dataInicio,
                             LocalDate dataFim, BigDecimal valorMercadoEur) {
}
```

Em `JogadorServiceImpl.sincronizarVinculo`, depois de `vinculo.setTipo(...)`:

```java
        vinculo.setCategoria(CategoriaDeElenco.valueOf(dados.categoria()));
```

Importe `br.com.api.footfirma.jogador.domain.CategoriaDeElenco` na classe.

- [ ] **Step 8: Corrigir as chamadas existentes nos testes**

Os dois testes de sincronização já constroem esses records. Acrescente o argumento novo em cada chamada: `null` não serve — as colunas são `not null`. Use `50` para `forcaFinanceira` e `"PROFISSIONAL"` para `categoria` nas chamadas preexistentes. Encontre todas com:

```bash
grep -rn "new DadosDeClube(\|new DadosDeVinculo(" src/test
```

- [ ] **Step 9: Rodar os testes e ver passar**

Run: `./gradlew test --tests '*ClubeServiceSincronizacaoTest' --tests '*JogadorServiceSincronizacaoTest'`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(clube): adiciona força financeira e categoria de elenco

Riqueza vira índice 0-99 no clube, irmão de reputacao e qualidade_base — não
um sistema financeiro, que seria número que nada lê enquanto não houver
mercado de transferências.

Categoria no vínculo separa profissional de base sem tabela nova: o vínculo já
é versionado por temporada, então promover é gravar o ano seguinte.
EOF
)"
```

---

### Task 3: Esqueleto do módulo `mundo` e as duas ligas

**Files:**
- Create: `src/main/java/br/com/api/footfirma/mundo/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/MundoService.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/dto/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/dto/RelatorioDeMundo.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/dto/ContagemPorEntidade.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/MundoServiceImpl.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/LimpezaDoCatalogo.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/PropriedadesDeMundo.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/MundoRunner.java`
- Test: `src/test/java/br/com/api/footfirma/mundo/MundoLigaTest.java`

**Interfaces:**
- Consumes: `TemporadaService.sincronizar(DadosDeTemporada)`, `CompeticaoService.sincronizarCompeticao/sincronizarEdicao/sincronizarFase/sincronizarRegra`, `GeografiaService.buscarPaisPorIso(String)`.
- Produces:
  - `MundoService.gerar()` → `RelatorioDeMundo`
  - `RelatorioDeMundo(long semente, String temporada, List<ContagemPorEntidade> contagens)`
  - `ContagemPorEntidade(String entidade, int criados, int atualizados)`
  - `LimpezaDoCatalogo.executar(JdbcTemplate)` — usado pelas Tasks 5, 7 e 8.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/mundo/MundoLigaTest.java`:

```java
package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MundoLigaTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCriarAsDuasDivisoesComEdicaoDeDoisMilEVinteESeis() {
        mundoService.gerar();

        var slugs = jdbcTemplate.queryForList(
                "select slug from competicao order by nivel", String.class);
        assertThat(slugs).containsExactly("primeira-divisao", "segunda-divisao");
        assertThat(contar("edicao")).isEqualTo(2);
        assertThat(contar("fase")).isEqualTo(2);
    }

    @Test
    void deveCriarAsRegrasDeAcessoEDeRebaixamento() {
        mundoService.gerar();

        var tipos = jdbcTemplate.queryForList("""
                select r.tipo from regra_classificacao r
                join edicao e on e.id = r.edicao_id
                join competicao c on c.id = e.competicao_id
                order by c.nivel
                """, String.class);
        assertThat(tipos).containsExactly("REBAIXAMENTO", "ACESSO");
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
```

- [ ] **Step 2: Rodar o teste e ver falhar**

Run: `./gradlew test --tests '*MundoLigaTest'`
Expected: FAIL na compilação — `MundoService` não existe.

- [ ] **Step 3: Criar o módulo e seus DTOs**

`mundo/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Mundo")
package br.com.api.footfirma.mundo;
```

`mundo/dto/package-info.java`:

```java
/** Saída do gerador de mundo. Entrada não existe: o mundo vem de semente, não de arquivo. */
package br.com.api.footfirma.mundo.dto;
```

`mundo/dto/ContagemPorEntidade.java`:

```java
package br.com.api.footfirma.mundo.dto;

/** {@code atualizados} distingue reexecução de carga nova sem consultar o banco. */
public record ContagemPorEntidade(String entidade, int criados, int atualizados) {
}
```

`mundo/dto/RelatorioDeMundo.java`:

```java
package br.com.api.footfirma.mundo.dto;

import java.util.List;

/**
 * A semente volta no relatório porque é o único jeito de reproduzir um mundo:
 * sem ela, um bug observado em execução passada é irrecuperável.
 */
public record RelatorioDeMundo(long semente, String temporada, List<ContagemPorEntidade> contagens) {
}
```

`mundo/MundoService.java`:

```java
package br.com.api.footfirma.mundo;

import br.com.api.footfirma.mundo.dto.RelatorioDeMundo;

public interface MundoService {

    /**
     * Gera o mundo inteiro a partir da semente configurada: duas ligas nacionais,
     * 40 clubes e seus elencos, encerrando com a materialização do overall.
     *
     * <p>Idempotente por slug: rodar duas vezes com a mesma semente atualiza em vez
     * de duplicar. Trocar a semente sem limpar deixa o mundo anterior órfão — é para
     * isso que existe {@code footfirma.mundo.recriar}.
     */
    RelatorioDeMundo gerar();
}
```

- [ ] **Step 4: Escrever a limpeza do catálogo**

`mundo/internal/LimpezaDoCatalogo.java`:

```java
package br.com.api.footfirma.mundo.internal;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Esvazia o catálogo antes de gerar. A ordem é a inversa das dependências —
 * inverter uma linha faz falhar por chave estrangeira.
 *
 * <p>Exceção deliberada à regra de não tocar tabela alheia: apagar o catálogo
 * inteiro é operação de infraestrutura sobre o banco, não escrita de domínio. A
 * alternativa seria expor um método destrutivo na interface pública de cinco
 * módulos — superfície pior que esta lista.
 *
 * <p>País, estado, posição, característica e perfil de avaliação não são apagados:
 * são seed de migration, e o gerador depende deles.
 */
final class LimpezaDoCatalogo {

    private static final List<String> TABELAS_EM_ORDEM = List.of(
            "jogador_overall",
            "jogador_vinculo",
            "jogador_atributo",
            "jogador_atributo_oculto",
            "jogador_caracteristica",
            "jogador_posicao",
            "jogador_referencia_externa",
            "jogador",
            "regra_classificacao",
            "edicao_participante",
            "fase",
            "edicao",
            "competicao_referencia_externa",
            "competicao",
            "clube_alias",
            "clube_referencia_externa",
            "clube",
            "estadio");

    private LimpezaDoCatalogo() {
    }

    static void executar(JdbcTemplate jdbcTemplate) {
        TABELAS_EM_ORDEM.forEach(tabela -> jdbcTemplate.update("delete from " + tabela));
    }
}
```

- [ ] **Step 5: Escrever as propriedades e o runner**

`mundo/internal/PropriedadesDeMundo.java`:

```java
package br.com.api.footfirma.mundo.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code semente} tem default para que gerar o mundo não exija configuração; trocá-la
 * troca o mundo inteiro. {@code recriar} apaga o catálogo antes — necessário ao mudar
 * a semente, já que os slugs novos não colidem com os antigos e o mundo velho ficaria
 * órfão no banco.
 */
@ConfigurationProperties(prefix = "footfirma.mundo")
record PropriedadesDeMundo(Long semente, boolean recriar, boolean encerrarAoFinal) {

    static final long SEMENTE_PADRAO = 20260803L;

    PropriedadesDeMundo {
        if (semente == null) {
            semente = SEMENTE_PADRAO;
        }
    }
}
```

`mundo/internal/MundoRunner.java`:

```java
package br.com.api.footfirma.mundo.internal;

import br.com.api.footfirma.mundo.MundoService;
import br.com.api.footfirma.mundo.dto.RelatorioDeMundo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dispara a geração sob o profile {@code mundo} e só sob ele: subir a API
 * normalmente nunca deve escrever no catálogo.
 */
@Component
@Profile("mundo")
@EnableConfigurationProperties(PropriedadesDeMundo.class)
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
```

- [ ] **Step 6: Escrever o orquestrador com temporada e ligas**

`mundo/internal/MundoServiceImpl.java`:

```java
package br.com.api.footfirma.mundo.internal;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.mundo.MundoService;
import br.com.api.footfirma.mundo.dto.ContagemPorEntidade;
import br.com.api.footfirma.mundo.dto.RelatorioDeMundo;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class MundoServiceImpl implements MundoService {

    static final String TEMPORADA = "2026";
    private static final String ISO_PAIS = "BRA";

    private final TemporadaService temporadaService;
    private final CompeticaoService competicaoService;
    private final GeografiaService geografiaService;
    private final PropriedadesDeMundo propriedades;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public RelatorioDeMundo gerar() {
        if (propriedades.recriar()) {
            LimpezaDoCatalogo.executar(jdbcTemplate);
        }
        var contagens = new ArrayList<ContagemPorEntidade>();
        var paisId = geografiaService.buscarPaisPorIso(ISO_PAIS)
                .orElseThrow(() -> new IllegalStateException(
                        "País BRA ausente — seed de geografia não aplicado"))
                .id();
        var temporadaId = criarTemporada(contagens);
        criarLigas(paisId, temporadaId, contagens);
        return new RelatorioDeMundo(propriedades.semente(), TEMPORADA, List.copyOf(contagens));
    }

    private Long criarTemporada(List<ContagemPorEntidade> contagens) {
        var resultado = temporadaService.sincronizar(new DadosDeTemporada(TEMPORADA, 2026, 2026));
        contagens.add(new ContagemPorEntidade("temporada",
                resultado.criado() ? 1 : 0, resultado.criado() ? 0 : 1));
        return resultado.id();
    }

    private void criarLigas(Long paisId, Long temporadaId, List<ContagemPorEntidade> contagens) {
        var primeira = criarLiga("primeira-divisao", "Primeira Divisão Nacional", 1,
                paisId, temporadaId);
        var segunda = criarLiga("segunda-divisao", "Segunda Divisão Nacional", 2,
                paisId, temporadaId);

        // Rebaixamento aponta para a divisão de destino; acesso, para a de origem.
        // Libertadores e Sul-Americana ficam fora: não há competição continental
        // neste mundo, e regra que aponta para o nada é dado falso.
        competicaoService.sincronizarRegra(
                new DadosDeRegra(primeira.edicaoId(), 17, 20, "REBAIXAMENTO", segunda.competicaoId()));
        competicaoService.sincronizarRegra(
                new DadosDeRegra(segunda.edicaoId(), 1, 4, "ACESSO", primeira.competicaoId()));

        contagens.add(new ContagemPorEntidade("competicao", 2, 0));
        contagens.add(new ContagemPorEntidade("edicao", 2, 0));
    }

    private LigaCriada criarLiga(String slug, String nome, int nivel, Long paisId, Long temporadaId) {
        var competicaoId = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao(slug, nome, paisId, "LIGA", nivel, "MASCULINO")).id();
        var edicaoId = competicaoService.sincronizarEdicao(new DadosDeEdicao(
                competicaoId, temporadaId, nome + " " + TEMPORADA,
                LocalDate.of(2026, 4, 4), LocalDate.of(2026, 12, 6))).id();
        competicaoService.sincronizarFase(new DadosDeFase(edicaoId, 1, "Turno e returno",
                "PONTOS_CORRIDOS", 2, false, false, false));
        return new LigaCriada(competicaoId, edicaoId);
    }

    record LigaCriada(Long competicaoId, Long edicaoId) {
    }
}
```

- [ ] **Step 7: Registrar as propriedades para o teste**

`PropriedadesDeMundo` é injetada em `MundoServiceImpl`, que não roda sob profile. Anote a implementação para que o bean exista fora do profile `mundo`: acrescente `@EnableConfigurationProperties(PropriedadesDeMundo.class)` em `MundoServiceImpl`, importando `org.springframework.boot.context.properties.EnableConfigurationProperties`.

- [ ] **Step 8: Rodar o teste e ver passar**

Run: `./gradlew test --tests '*MundoLigaTest'`
Expected: PASS

- [ ] **Step 9: Verificar a fronteira entre módulos**

Run: `./gradlew test --tests '*ModularidadeTest'`
Expected: PASS. Se falhar, `mundo` está importando um tipo de subpacote `internal` de outro módulo — só interfaces públicas e `dto` são permitidas.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(mundo): cria o módulo e as duas ligas nacionais

MundoService gera temporada, competições, edições, fases e as regras de acesso
e rebaixamento. A geração roda sob o profile `mundo` e só sob ele.

Libertadores e Sul-Americana ficam fora do enum usado: regra que aponta para
competição inexistente é dado falso.
EOF
)"
```

---

### Task 4: Arquétipos, nomes e a fábrica de clubes

Fábricas puras: nada de Spring, nada de banco. Os testes desta task rodam em milissegundos.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/Arquetipo.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/CatalogoDeArquetipos.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/ClubeGerado.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/GeradorDeNomes.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/FabricaDeClubes.java`
- Test: `src/test/java/br/com/api/footfirma/mundo/internal/FabricaDeClubesTest.java`

**Interfaces:**
- Consumes: nada dos módulos de catálogo.
- Produces:
  - `Arquetipo(String nome, int reputacaoMin, int reputacaoMax, int financasMin, int financasMax, int baseMin, int baseMax)`
  - `CatalogoDeArquetipos.distribuicao(int divisao)` → `List<Arquetipo>` com 20 elementos
  - `ClubeGerado(String slug, String nomeOficial, String nomeCurto, String apelido, int anoFundacao, String cidade, String uf, String estadio, int capacidade, int anoInauguracao, String corPrimaria, String corSecundaria, int reputacao, int forcaFinanceira, int qualidadeBase, int divisao)` com o método `int nivelElenco()`
  - `FabricaDeClubes.gerar(SplittableRandom)` → `List<ClubeGerado>` com 40 elementos, os 20 primeiros da divisão 1

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/mundo/internal/FabricaDeClubesTest.java`:

```java
package br.com.api.footfirma.mundo.internal;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FabricaDeClubesTest {

    private static final long SEMENTE = 20260803L;

    @Test
    void deveGerarQuarentaClubesMetadeEmCadaDivisao() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes).hasSize(40);
        assertThat(clubes).filteredOn(clube -> clube.divisao() == 1).hasSize(20);
        assertThat(clubes).filteredOn(clube -> clube.divisao() == 2).hasSize(20);
    }

    @Test
    void deveGerarSlugsECidadesUnicos() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes).extracting(ClubeGerado::slug).doesNotHaveDuplicates();
        assertThat(clubes).extracting(ClubeGerado::cidade).doesNotHaveDuplicates();
    }

    @Test
    void deveRespeitarAsFaixasDoArquetipo() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes).allSatisfy(clube -> {
            assertThat(clube.reputacao()).isBetween(0, 99);
            assertThat(clube.forcaFinanceira()).isBetween(0, 99);
            assertThat(clube.qualidadeBase()).isBetween(0, 99);
        });
    }

    @Test
    void deveExistirCeleiroPobreComBaseExcelente() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes)
                .filteredOn(clube -> clube.forcaFinanceira() < 50 && clube.qualidadeBase() >= 85)
                .isNotEmpty();
    }

    @Test
    void deveExistirGiganteEndividadoComReputacaoAltaEDinheiroBaixo() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes)
                .filteredOn(clube -> clube.reputacao() >= 75 && clube.forcaFinanceira() <= 50)
                .isNotEmpty();
    }

    @Test
    void deveSerDeterministica() {
        var primeira = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));
        var segunda = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(primeira).isEqualTo(segunda);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*FabricaDeClubesTest'`
Expected: FAIL na compilação — `FabricaDeClubes` não existe.

- [ ] **Step 3: Escrever `Arquetipo` e o catálogo**

`mundo/internal/Arquetipo.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.util.SplittableRandom;

/**
 * Faixas de um perfil de clube. Reputação, dinheiro e base variam de forma
 * independente dentro delas — é o descasamento entre os três que produz um mundo
 * com vantagens e desvantagens em vez de uma escala única de "melhor a pior".
 */
record Arquetipo(String nome,
                 int reputacaoMin, int reputacaoMax,
                 int financasMin, int financasMax,
                 int baseMin, int baseMax) {

    int sortearReputacao(SplittableRandom aleatorio) {
        return aleatorio.nextInt(reputacaoMin, reputacaoMax + 1);
    }

    int sortearFinancas(SplittableRandom aleatorio) {
        return aleatorio.nextInt(financasMin, financasMax + 1);
    }

    int sortearBase(SplittableRandom aleatorio) {
        return aleatorio.nextInt(baseMin, baseMax + 1);
    }
}
```

`mundo/internal/CatalogoDeArquetipos.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Os seis perfis e quantos clubes de cada um vive em cada divisão. É dado
 * declarado de propósito: rebalancear a liga é editar esta tabela, não caçar
 * coeficientes espalhados por fórmulas.
 */
final class CatalogoDeArquetipos {

    static final Arquetipo POTENCIA = new Arquetipo("POTENCIA", 82, 90, 85, 95, 55, 70);
    static final Arquetipo GIGANTE_ENDIVIDADO = new Arquetipo("GIGANTE_ENDIVIDADO", 75, 85, 35, 50, 60, 75);
    static final Arquetipo CELEIRO = new Arquetipo("CELEIRO", 50, 62, 30, 45, 85, 95);
    static final Arquetipo MEIO_DE_TABELA = new Arquetipo("MEIO_DE_TABELA", 55, 68, 55, 70, 45, 60);
    static final Arquetipo RECEM_PROMOVIDO = new Arquetipo("RECEM_PROMOVIDO", 45, 55, 45, 60, 40, 55);
    static final Arquetipo EM_QUEDA = new Arquetipo("EM_QUEDA", 40, 52, 25, 40, 30, 45);

    private static final List<QuotaDeArquetipo> PRIMEIRA_DIVISAO = List.of(
            new QuotaDeArquetipo(POTENCIA, 3),
            new QuotaDeArquetipo(GIGANTE_ENDIVIDADO, 3),
            new QuotaDeArquetipo(CELEIRO, 2),
            new QuotaDeArquetipo(MEIO_DE_TABELA, 6),
            new QuotaDeArquetipo(RECEM_PROMOVIDO, 3),
            new QuotaDeArquetipo(EM_QUEDA, 3));

    private static final List<QuotaDeArquetipo> SEGUNDA_DIVISAO = List.of(
            new QuotaDeArquetipo(GIGANTE_ENDIVIDADO, 1),
            new QuotaDeArquetipo(CELEIRO, 3),
            new QuotaDeArquetipo(MEIO_DE_TABELA, 4),
            new QuotaDeArquetipo(RECEM_PROMOVIDO, 5),
            new QuotaDeArquetipo(EM_QUEDA, 7));

    private CatalogoDeArquetipos() {
    }

    /** Devolve 20 arquétipos, um por clube da divisão, na ordem das quotas. */
    static List<Arquetipo> distribuicao(int divisao) {
        var quotas = divisao == 1 ? PRIMEIRA_DIVISAO : SEGUNDA_DIVISAO;
        var arquetipos = new ArrayList<Arquetipo>(20);
        quotas.forEach(quota -> {
            for (var i = 0; i < quota.quantidade(); i++) {
                arquetipos.add(quota.arquetipo());
            }
        });
        return List.copyOf(arquetipos);
    }

    record QuotaDeArquetipo(Arquetipo arquetipo, int quantidade) {
    }
}
```

- [ ] **Step 4: Escrever `ClubeGerado`**

`mundo/internal/ClubeGerado.java`:

```java
package br.com.api.footfirma.mundo.internal;

/** Um clube antes de virar linha. O gerador o traduz em {@code DadosDeClube}. */
record ClubeGerado(String slug, String nomeOficial, String nomeCurto, String apelido,
                   int anoFundacao, String cidade, String uf,
                   String estadio, int capacidade, int anoInauguracao,
                   String corPrimaria, String corSecundaria,
                   int reputacao, int forcaFinanceira, int qualidadeBase, int divisao) {

    /**
     * Reputação pesa mais que dinheiro porque história atrai jogador que salário não
     * paga — mas dinheiro pesa o bastante para um gigante endividado decair.
     */
    int nivelElenco() {
        return (int) Math.round(0.6 * reputacao + 0.4 * forcaFinanceira);
    }
}
```

- [ ] **Step 5: Escrever `GeradorDeNomes` com as listas curadas**

`mundo/internal/GeradorDeNomes.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Nome de clube é lista curada, não combinação de pools: são só 40, e um nome
 * sorteado tem chance real de cair num clube profissional existente. Nome de
 * jogador é combinatório — 1.520 nomes não se revisa à mão, e nenhum clube deste
 * mundo é real, então coincidência de nome não sugere identificação de pessoa.
 */
final class GeradorDeNomes {

    record NomeDeClube(String nomeCurto, String nomeOficial, String apelido,
                       String cidade, String uf) {
    }

    static final List<NomeDeClube> CLUBES = List.of(
            new NomeDeClube("Marena", "Sociedade Esportiva Marena", "Verdão da Serra", "São Paulo", "SP"),
            new NomeDeClube("Vulcano", "Vulcano Futebol Clube", "Fogo Azul", "Campinas", "SP"),
            new NomeDeClube("Serrazul", "Clube Atlético Serrazul", "Azulão", "Santos", "SP"),
            new NomeDeClube("Ipanorte", "Grêmio Ipanorte", "Tricolor do Norte", "Ribeirão Preto", "SP"),
            new NomeDeClube("Belarco", "Esporte Clube Belarco", "Arqueiro", "Rio de Janeiro", "RJ"),
            new NomeDeClube("Portomar", "Portomar Futebol Clube", "Marujo", "Niterói", "RJ"),
            new NomeDeClube("Alvorada", "Alvorada Atlético Clube", "Aurora", "Campos dos Goytacazes", "RJ"),
            new NomeDeClube("Riomanso", "Riomanso Futebol Clube", "Manso", "Belo Horizonte", "MG"),
            new NomeDeClube("Ventania", "Associação Atlética Ventania", "Vendaval", "Uberlândia", "MG"),
            new NomeDeClube("Aurinegro", "Clube Esportivo Aurinegro", "Ouro e Breu", "Juiz de Fora", "MG"),
            new NomeDeClube("Farol", "Farol Esporte Clube", "Luzeiro", "Porto Alegre", "RS"),
            new NomeDeClube("Pedra Alta", "Pedra Alta Futebol Clube", "Rochoso", "Caxias do Sul", "RS"),
            new NomeDeClube("Trevo", "Trevo Atlético Clube", "Sortudo", "Pelotas", "RS"),
            new NomeDeClube("Barra Fria", "Associação Desportiva Barra Fria", "Gelo", "Curitiba", "PR"),
            new NomeDeClube("Corvina", "Corvina Futebol Clube", "Peixe Prata", "Londrina", "PR"),
            new NomeDeClube("Lagoa Nova", "Lagoa Nova Esporte Clube", "Lagoense", "Maringá", "PR"),
            new NomeDeClube("Guaraciara", "Guaraciara Futebol Clube", "Guará", "Florianópolis", "SC"),
            new NomeDeClube("Salinas", "Salinas Atlético Clube", "Salineiro", "Joinville", "SC"),
            new NomeDeClube("Monte Claro", "Monte Claro Esporte Clube", "Montanhês", "Chapecó", "SC"),
            new NomeDeClube("Ferronorte", "Ferronorte Futebol Clube", "Trilho", "Salvador", "BA"),
            new NomeDeClube("Itapicuru", "Itapicuru Esporte Clube", "Ita", "Feira de Santana", "BA"),
            new NomeDeClube("Varjão", "Varjão Atlético Clube", "Varjonense", "Recife", "PE"),
            new NomeDeClube("Cabo Sul", "Cabo Sul Futebol Clube", "Cabista", "Caruaru", "PE"),
            new NomeDeClube("Rochedo", "Rochedo Esporte Clube", "Penhasco", "Fortaleza", "CE"),
            new NomeDeClube("Jatobá", "Jatobá Futebol Clube", "Jatobazeiro", "Juazeiro do Norte", "CE"),
            new NomeDeClube("Palmarina", "Sociedade Esportiva Palmarina", "Palma", "Natal", "RN"),
            new NomeDeClube("Vale Verde", "Vale Verde Atlético Clube", "Valeiro", "João Pessoa", "PB"),
            new NomeDeClube("Campo Belo", "Campo Belo Futebol Clube", "Belo", "Maceió", "AL"),
            new NomeDeClube("Icaraí", "Icaraí Atlético Clube", "Icaraiense", "Aracaju", "SE"),
            new NomeDeClube("Sertaneja", "Associação Atlética Sertaneja", "Sertão", "São Luís", "MA"),
            new NomeDeClube("Marimbondo", "Marimbondo Esporte Clube", "Ferrão", "Teresina", "PI"),
            new NomeDeClube("Nova Aurora", "Nova Aurora Futebol Clube", "Novato", "Belém", "PA"),
            new NomeDeClube("Cristalina", "Cristalina Esporte Clube", "Cristal", "Santarém", "PA"),
            new NomeDeClube("Piçarra", "Piçarra Atlético Clube", "Piçarrense", "Manaus", "AM"),
            new NomeDeClube("Bela Vista", "Bela Vista Futebol Clube", "Mirante", "Goiânia", "GO"),
            new NomeDeClube("Areial", "Areial Esporte Clube", "Areia", "Anápolis", "GO"),
            new NomeDeClube("Tamandiba", "Tamandiba Futebol Clube", "Tamandi", "Brasília", "DF"),
            new NomeDeClube("Ouro Fino", "Ouro Fino Atlético Clube", "Ourinho", "Cuiabá", "MT"),
            new NomeDeClube("Camboa", "Camboa Esporte Clube", "Camboense", "Campo Grande", "MS"),
            new NomeDeClube("Vila Rica", "Vila Rica Futebol Clube", "Vilarico", "Vitória", "ES"));

    static final List<String> PRENOMES = List.of(
            "Adriano", "Alan", "Anderson", "Bruno", "Caio", "Carlos", "Cauã", "Cléber",
            "Daniel", "Danilo", "Diego", "Edson", "Eduardo", "Emerson", "Fábio", "Felipe",
            "Fernando", "Gabriel", "Geraldo", "Gustavo", "Heitor", "Henrique", "Igor", "Ítalo",
            "Jonas", "Kaique", "Leandro", "Lucas", "Luiz", "Marcelo", "Mateus", "Murilo",
            "Nélson", "Otávio", "Paulo", "Rafael", "Renan", "Ricardo", "Rodrigo", "Samuel",
            "Sérgio", "Thiago", "Vinícius", "Wesley");

    static final List<String> SOBRENOMES = List.of(
            "Albuquerque", "Almeida", "Andrade", "Aragão", "Barbosa", "Bastos", "Bezerra",
            "Cardoso", "Carvalho", "Cavalcanti", "Correia", "Dantas", "Duarte", "Esteves",
            "Farias", "Fonseca", "Furtado", "Gonçalves", "Guimarães", "Leal", "Lacerda",
            "Macedo", "Machado", "Marinho", "Medeiros", "Meireles", "Nogueira", "Novaes",
            "Peixoto", "Pontes", "Quadros", "Rezende", "Sampaio", "Siqueira", "Tavares",
            "Teixeira", "Valente", "Vasconcelos", "Veloso", "Xavier");

    private GeradorDeNomes() {
    }

    /** Slug de API: legível, estável e derivado do nome. */
    static String slug(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
```

- [ ] **Step 6: Escrever `FabricaDeClubes`**

`mundo/internal/FabricaDeClubes.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Monta os 40 clubes casando a lista curada de nomes com a distribuição de
 * arquétipos. Os 20 primeiros nomes vão para a primeira divisão — fixo, não
 * sorteado: mundo em que a divisão de um clube muda a cada regeração é mundo que
 * ninguém consegue depurar.
 */
final class FabricaDeClubes {

    private static final List<String> CORES = List.of(
            "#C62828", "#1565C0", "#2E7D32", "#F9A825", "#6A1B9A",
            "#00838F", "#EF6C00", "#37474F", "#AD1457", "#4E342E");

    private FabricaDeClubes() {
    }

    static List<ClubeGerado> gerar(SplittableRandom aleatorio) {
        var clubes = new ArrayList<ClubeGerado>(40);
        clubes.addAll(gerarDivisao(1, 0, aleatorio));
        clubes.addAll(gerarDivisao(2, 20, aleatorio));
        return List.copyOf(clubes);
    }

    private static List<ClubeGerado> gerarDivisao(int divisao, int deslocamento,
                                                  SplittableRandom aleatorio) {
        var arquetipos = CatalogoDeArquetipos.distribuicao(divisao);
        var clubes = new ArrayList<ClubeGerado>(20);
        for (var i = 0; i < 20; i++) {
            var nome = GeradorDeNomes.CLUBES.get(deslocamento + i);
            var arquetipo = arquetipos.get(i);
            var primeira = CORES.get(aleatorio.nextInt(CORES.size()));
            var segunda = CORES.get(aleatorio.nextInt(CORES.size()));
            clubes.add(new ClubeGerado(
                    GeradorDeNomes.slug(nome.nomeCurto()),
                    nome.nomeOficial(), nome.nomeCurto(), nome.apelido(),
                    aleatorio.nextInt(1902, 1979),
                    nome.cidade(), nome.uf(),
                    "Arena " + nome.nomeCurto(),
                    aleatorio.nextInt(12_000, 68_000),
                    aleatorio.nextInt(1955, 2019),
                    primeira, primeira.equals(segunda) ? "#FFFFFF" : segunda,
                    arquetipo.sortearReputacao(aleatorio),
                    arquetipo.sortearFinancas(aleatorio),
                    arquetipo.sortearBase(aleatorio),
                    divisao));
        }
        return clubes;
    }
}
```

- [ ] **Step 7: Rodar os testes e ver passar**

Run: `./gradlew test --tests '*FabricaDeClubesTest'`
Expected: PASS, os seis testes.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(mundo): declara arquétipos, nomes e a fábrica de clubes

Seis perfis com faixas próprias para reputação, dinheiro e base. É o
descasamento entre os três eixos que produz Celeiro pobre com base excelente e
Gigante Endividado vivendo de passado.

Nome de clube é lista curada de 40, não combinatória: um nome sorteado tem
chance real de cair num clube profissional existente.
EOF
)"
```

---

### Task 5: Persistir os 40 clubes e seus participantes

**Files:**
- Modify: `src/main/java/br/com/api/footfirma/mundo/internal/MundoServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/mundo/MundoClubeTest.java`

**Interfaces:**
- Consumes: `ClubeService.sincronizarEstadio(DadosDeEstadio)`, `ClubeService.sincronizarClube(DadosDeClube)` (14 campos, ver Task 2), `CompeticaoService.sincronizarParticipante(DadosDeParticipante)`, `GeografiaService.buscarEstadoPorUf(String, String)`, `FabricaDeClubes.gerar(SplittableRandom)`.
- Produces: método privado `List<ClubeGerado> criarClubes(...)` internamente; nada de novo na interface pública.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/mundo/MundoClubeTest.java`:

```java
package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MundoClubeTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCriarQuarentaClubesComEstadioProprio() {
        mundoService.gerar();

        assertThat(contar("clube")).isEqualTo(40);
        assertThat(contar("estadio")).isEqualTo(40);
        assertThat(contar("edicao_participante")).isEqualTo(40);
    }

    @Test
    void deveDistribuirVinteClubesPorDivisao() {
        mundoService.gerar();

        var porDivisao = jdbcTemplate.queryForList("""
                select c.nivel, count(*) as total
                from edicao_participante p
                join edicao e on e.id = p.edicao_id
                join competicao c on c.id = e.competicao_id
                group by c.nivel order by c.nivel
                """);
        assertThat(porDivisao).hasSize(2);
        assertThat(porDivisao.getFirst().get("total")).isEqualTo(20L);
        assertThat(porDivisao.getLast().get("total")).isEqualTo(20L);
    }

    @Test
    void deveResolverOEstadoDeCadaClube() {
        mundoService.gerar();

        assertThat(contar("clube where estado_id is null")).isZero();
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*MundoClubeTest'`
Expected: FAIL — `contar("clube")` devolve 0.

- [ ] **Step 3: Persistir os clubes no orquestrador**

Em `MundoServiceImpl`, injete `ClubeService` e acrescente ao método `gerar()`, depois de `criarLigas(...)`:

```java
        var edicaoPorDivisao = criarLigas(paisId, temporadaId, contagens);
        var idsPorSlug = new HashMap<String, Long>();
        var clubes = criarClubes(paisId, idsPorSlug, contagens);
        vincularParticipantes(clubes, idsPorSlug, edicaoPorDivisao, contagens);
```

`criarLigas` passa a devolver `Map<Integer, Long>` — divisão → id da edição — em vez de `void`.

`idsPorSlug` e `edicaoPorDivisao` são **variáveis locais de `gerar()` passadas por parâmetro, nunca campos de instância**: o service é singleton, e estado mutável em singleton corrompe na primeira execução concorrente.

```java
    private List<ClubeGerado> criarClubes(Long paisId, Map<String, Long> idsPorSlug,
                                          List<ContagemPorEntidade> contagens) {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(propriedades.semente()));
        var criados = 0;
        for (var clube : clubes) {
            var estadoId = geografiaService.buscarEstadoPorUf(ISO_PAIS, clube.uf())
                    .orElseThrow(() -> new IllegalStateException(
                            "UF %s ausente no seed de geografia".formatted(clube.uf())))
                    .id();
            var estadioId = clubeService.sincronizarEstadio(new DadosDeEstadio(
                    clube.estadio(), clube.cidade(), estadoId,
                    clube.capacidade(), clube.anoInauguracao())).id();
            var resultado = clubeService.sincronizarClube(new DadosDeClube(
                    clube.slug(), clube.nomeOficial(), clube.nomeCurto(), clube.apelido(),
                    clube.anoFundacao(), paisId, estadoId, estadioId,
                    clube.corPrimaria(), clube.corSecundaria(),
                    clube.reputacao(), clube.forcaFinanceira(), clube.qualidadeBase(), estadoId));
            if (resultado.criado()) {
                criados++;
            }
            idsPorSlug.put(clube.slug(), resultado.id());
        }
        contagens.add(new ContagemPorEntidade("clube", criados, clubes.size() - criados));
        return clubes;
    }

    private void vincularParticipantes(List<ClubeGerado> clubes, Map<String, Long> idsPorSlug,
                                       Map<Integer, Long> edicaoPorDivisao,
                                       List<ContagemPorEntidade> contagens) {
        clubes.forEach(clube -> competicaoService.sincronizarParticipante(new DadosDeParticipante(
                edicaoPorDivisao.get(clube.divisao()), idsPorSlug.get(clube.slug()), null)));
        contagens.add(new ContagemPorEntidade("edicao_participante", clubes.size(), 0));
    }
```

`posicaoFinal` é `null` de propósito: nenhuma temporada foi disputada, e gravar uma classificação inventada seria afirmar um resultado que não aconteceu.

- [ ] **Step 4: Rodar os testes e ver passar**

Run: `./gradlew test --tests '*MundoClubeTest' --tests '*MundoLigaTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(mundo): grava os 40 clubes e os inscreve nas divisões

Cada clube ganha estádio próprio e estado resolvido pelo seed de geografia.
posicao_final fica nula: nenhuma temporada foi disputada, e classificação
inventada seria afirmar resultado que não aconteceu.
EOF
)"
```

---

### Task 6: Fábricas de elenco e de atributos

Ainda puras — os testes desta task não sobem Spring.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/PapelNoElenco.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/JogadorGerado.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/AtributosGerados.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/ChaveDeJogador.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/FabricaDeElenco.java`
- Create: `src/main/java/br/com/api/footfirma/mundo/internal/FabricaDeAtributos.java`
- Test: `src/test/java/br/com/api/footfirma/mundo/internal/FabricaDeElencoTest.java`
- Test: `src/test/java/br/com/api/footfirma/mundo/internal/FabricaDeAtributosTest.java`

**Interfaces:**
- Consumes: `ClubeGerado` (Task 4), `GeradorDeNomes.PRENOMES/SOBRENOMES/slug` (Task 4).
- Produces:
  - `PapelNoElenco` — enum com `deltaMinimo()`, `deltaMaximo()`, `idadeMinima()`, `idadeMaxima()`, `categoria()`
  - `JogadorGerado(String slug, String chaveNatural, long semente, String nomeCompleto, String nomeExibicao, LocalDate dataNascimento, String posicao, String pePreferido, int alturaCm, int pesoKg, int alvoOverall, int potencialBase, int potencialVariacao, String categoria, Integer numeroCamisa)`
  - `AtributosGerados` — record com as 18 skills como `int`
  - `FabricaDeElenco.gerar(ClubeGerado, SplittableRandom, Set<String> chavesUsadas)` → `List<JogadorGerado>` com 38 elementos
  - `FabricaDeAtributos.gerar(String posicao, int alvoOverall, SplittableRandom)` → `AtributosGerados`
  - `ChaveDeJogador.de(String, LocalDate, String)` e `ChaveDeJogador.semente(String)`

- [ ] **Step 1: Escrever o teste da fábrica de elenco**

Crie `src/test/java/br/com/api/footfirma/mundo/internal/FabricaDeElencoTest.java`:

```java
package br.com.api.footfirma.mundo.internal;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FabricaDeElencoTest {

    private static final ClubeGerado CELEIRO = new ClubeGerado(
            "celeiro", "Celeiro Futebol Clube", "Celeiro", "Fábrica", 1930,
            "Sorocaba", "SP", "Arena Celeiro", 20_000, 1970,
            "#C62828", "#FFFFFF", 56, 38, 91, 2);

    private static final ClubeGerado POTENCIA = new ClubeGerado(
            "potencia", "Potência Futebol Clube", "Potência", "Gigante", 1910,
            "São Paulo", "SP", "Arena Potência", 60_000, 1980,
            "#1565C0", "#FFFFFF", 86, 90, 62, 1);

    @Test
    void deveGerarVinteESeisProfissionaisEDozeDaBase() {
        var elenco = gerar(POTENCIA);

        assertThat(elenco).hasSize(38);
        assertThat(elenco).filteredOn(j -> "PROFISSIONAL".equals(j.categoria())).hasSize(26);
        assertThat(elenco).filteredOn(j -> "BASE".equals(j.categoria())).hasSize(12);
    }

    @Test
    void deveCobrirAsNovePosicoesNoElencoProfissional() {
        var profissionais = gerar(POTENCIA).stream()
                .filter(j -> "PROFISSIONAL".equals(j.categoria()))
                .toList();

        assertThat(profissionais).extracting(JogadorGerado::posicao)
                .contains("GOL", "ZAG", "LTD", "LTE", "VOL", "MEC", "MEA", "PTA", "ATA");
        assertThat(profissionais).filteredOn(j -> "GOL".equals(j.posicao())).hasSize(3);
        assertThat(profissionais).filteredOn(j -> "ZAG".equals(j.posicao())).hasSize(5);
    }

    @Test
    void deveDarCamisasUnicasAosProfissionaisENenhumaAosDaBase() {
        var elenco = gerar(POTENCIA);

        assertThat(elenco).filteredOn(j -> "PROFISSIONAL".equals(j.categoria()))
                .extracting(JogadorGerado::numeroCamisa).doesNotHaveDuplicates();
        assertThat(elenco).filteredOn(j -> "BASE".equals(j.categoria()))
                .allSatisfy(j -> assertThat(j.numeroCamisa()).isNull());
    }

    @Test
    void deveTerEstrelaAcimaDaMediaDoProprioElenco() {
        var profissionais = gerar(CELEIRO).stream()
                .filter(j -> "PROFISSIONAL".equals(j.categoria()))
                .toList();
        var media = profissionais.stream().mapToInt(JogadorGerado::alvoOverall).average().orElseThrow();
        var melhor = profissionais.stream().mapToInt(JogadorGerado::alvoOverall).max().orElseThrow();

        assertThat(melhor).isGreaterThanOrEqualTo((int) media + 8);
    }

    @Test
    void deveEsconderJoiaDePotencialAltoNaBaseDeClubePobre() {
        var base = gerar(CELEIRO).stream()
                .filter(j -> "BASE".equals(j.categoria()))
                .toList();

        assertThat(base).filteredOn(j -> j.potencialBase() >= 88).isNotEmpty();
    }

    @Test
    void devePorPotencialNuncaAbaixoDoOverallAtual() {
        assertThat(gerar(POTENCIA)).allSatisfy(jogador ->
                assertThat(jogador.potencialBase()).isGreaterThanOrEqualTo(jogador.alvoOverall()));
    }

    @Test
    void deveGerarChavesNaturaisUnicasEntreClubes() {
        var chavesUsadas = new HashSet<String>();
        var aleatorio = new SplittableRandom(7L);
        var primeiro = FabricaDeElenco.gerar(POTENCIA, aleatorio, chavesUsadas);
        var segundo = FabricaDeElenco.gerar(CELEIRO, aleatorio, chavesUsadas);

        assertThat(chavesUsadas).hasSize(primeiro.size() + segundo.size());
    }

    private List<JogadorGerado> gerar(ClubeGerado clube) {
        return FabricaDeElenco.gerar(clube, new SplittableRandom(42L), new HashSet<>());
    }
}
```

- [ ] **Step 2: Escrever o teste da fábrica de atributos**

Crie `src/test/java/br/com/api/footfirma/mundo/internal/FabricaDeAtributosTest.java`:

```java
package br.com.api.footfirma.mundo.internal;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FabricaDeAtributosTest {

    @Test
    void deveManterTodaSkillNaFaixaValida() {
        var atributos = FabricaDeAtributos.gerar("MEC", 74, new SplittableRandom(1L));

        assertThat(atributos.todas().values()).allSatisfy(valor ->
                assertThat(valor).isBetween(1, 99));
    }

    @Test
    void deveConcentrarQualidadeNasSkillsDeGoleiroQuandoAPosicaoEGol() {
        var atributos = FabricaDeAtributos.gerar("GOL", 80, new SplittableRandom(1L));

        assertThat(atributos.golReflexo()).isBetween(77, 83);
        assertThat(atributos.golPosicionamento()).isBetween(77, 83);
        assertThat(atributos.finalizacao()).isLessThan(40);
    }

    @Test
    void deveDeixarSkillDeGoleiroBaixaEmJogadorDeLinha() {
        var atributos = FabricaDeAtributos.gerar("ATA", 82, new SplittableRandom(1L));

        assertThat(atributos.golReflexo()).isLessThan(30);
        assertThat(atributos.finalizacao()).isBetween(79, 85);
    }

    @Test
    void deveSerDeterministica() {
        var primeira = FabricaDeAtributos.gerar("ZAG", 70, new SplittableRandom(9L));
        var segunda = FabricaDeAtributos.gerar("ZAG", 70, new SplittableRandom(9L));

        assertThat(primeira).isEqualTo(segunda);
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --tests '*FabricaDeElencoTest' --tests '*FabricaDeAtributosTest'`
Expected: FAIL na compilação — as fábricas não existem.

- [ ] **Step 4: Escrever `ChaveDeJogador`**

`mundo/internal/ChaveDeJogador.java` — mesma regra que vivia em `importacao`, agora sob o único dono que restou:

```java
package br.com.api.footfirma.mundo.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Define a identidade de um jogador. A semente derivada daqui é a origem de todo
 * atributo oculto — SHA-256 em vez de {@code hashCode()} porque 32 bits colidem com
 * frequência observável em milhares de jogadores, e colisão significa dois jogadores
 * com a mesma personalidade.
 */
final class ChaveDeJogador {

    private ChaveDeJogador() {
    }

    static String de(String nomeCompleto, LocalDate nascimento, String isoPais) {
        return normalizar(nomeCompleto) + "|" + nascimento + "|" + isoPais.toUpperCase(Locale.ROOT);
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    static long semente(String chaveNatural) {
        var digest = digestSha256(chaveNatural.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong() & Long.MAX_VALUE;
    }

    private static byte[] digestSha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", erro);
        }
    }
}
```

- [ ] **Step 5: Escrever `PapelNoElenco` e `JogadorGerado`**

`mundo/internal/PapelNoElenco.java`:

```java
package br.com.api.footfirma.mundo.internal;

/**
 * As cotas de papel são o que garante que todo clube tenha alguém para se olhar.
 * Uma distribuição gaussiana pura permitiria um elenco inteiro dentro de seis
 * pontos — tecnicamente correto e sem graça.
 *
 * <p>Os deltas do elenco profissional são relativos ao nível do clube; os da base,
 * à qualidade de formação.
 */
enum PapelNoElenco {

    ESTRELA(1, 10, 14, 24, 30, "PROFISSIONAL"),
    TITULAR(9, 2, 6, 21, 33, "PROFISSIONAL"),
    ROTATIVO(8, -2, 2, 21, 33, "PROFISSIONAL"),
    RESERVA(6, -8, -3, 19, 35, "PROFISSIONAL"),
    CRIA(2, -6, 0, 18, 20, "PROFISSIONAL"),
    JOIA(2, -20, -20, 16, 19, "BASE"),
    PROMESSA(4, -15, -15, 15, 19, "BASE"),
    GAROTO(6, -22, -22, 15, 18, "BASE");

    private final int quantidade;
    private final int deltaMinimo;
    private final int deltaMaximo;
    private final int idadeMinima;
    private final int idadeMaxima;
    private final String categoria;

    PapelNoElenco(int quantidade, int deltaMinimo, int deltaMaximo,
                  int idadeMinima, int idadeMaxima, String categoria) {
        this.quantidade = quantidade;
        this.deltaMinimo = deltaMinimo;
        this.deltaMaximo = deltaMaximo;
        this.idadeMinima = idadeMinima;
        this.idadeMaxima = idadeMaxima;
        this.categoria = categoria;
    }

    int quantidade() {
        return quantidade;
    }

    int deltaMinimo() {
        return deltaMinimo;
    }

    int deltaMaximo() {
        return deltaMaximo;
    }

    int idadeMinima() {
        return idadeMinima;
    }

    int idadeMaxima() {
        return idadeMaxima;
    }

    String categoria() {
        return categoria;
    }

    boolean daBase() {
        return "BASE".equals(categoria);
    }

    /** Potencial das joias: é o que faz um clube pobre valer a pena olhar. */
    int potencialMinimo() {
        return switch (this) {
            case JOIA -> 88;
            case PROMESSA -> 75;
            case GAROTO -> 60;
            default -> 0;
        };
    }

    int potencialMaximo() {
        return switch (this) {
            case JOIA -> 95;
            case PROMESSA -> 85;
            case GAROTO -> 72;
            default -> 0;
        };
    }
}
```

`mundo/internal/JogadorGerado.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.time.LocalDate;

/** Um jogador antes de virar linha. O gerador o traduz em {@code DadosDeJogador}. */
record JogadorGerado(String slug, String chaveNatural, long semente,
                     String nomeCompleto, String nomeExibicao, LocalDate dataNascimento,
                     String posicao, String pePreferido, int alturaCm, int pesoKg,
                     int alvoOverall, int potencialBase, int potencialVariacao,
                     String categoria, Integer numeroCamisa) {
}
```

- [ ] **Step 6: Escrever `FabricaDeElenco`**

`mundo/internal/FabricaDeElenco.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Monta os 38 jogadores de um clube: 26 profissionais e 12 na base. A qualidade do
 * elenco vem de reputação e dinheiro; a da base, só da formação — é o descasamento
 * que põe joia em time pobre.
 */
final class FabricaDeElenco {

    private static final int ANO_DA_TEMPORADA = 2026;
    private static final String ISO_PAIS = "BRA";

    /** 26 vagas cobrindo as nove posições com reserva real em cada setor. */
    private static final List<String> POSICOES_PROFISSIONAIS = List.of(
            "GOL", "GOL", "GOL",
            "ZAG", "ZAG", "ZAG", "ZAG", "ZAG",
            "LTD", "LTD", "LTE", "LTE",
            "VOL", "VOL", "VOL", "VOL",
            "MEC", "MEC", "MEC", "MEA", "MEA",
            "PTA", "PTA", "PTA", "ATA", "ATA");

    private static final List<String> POSICOES_DA_BASE = List.of(
            "GOL", "ZAG", "ZAG", "LTD", "LTE", "VOL", "VOL", "MEC", "MEC", "MEA", "PTA", "ATA");

    private static final List<String> PES = List.of("DIREITO", "ESQUERDO", "AMBIDESTRO");

    private FabricaDeElenco() {
    }

    static List<JogadorGerado> gerar(ClubeGerado clube, SplittableRandom aleatorio,
                                     Set<String> chavesUsadas) {
        var jogadores = new ArrayList<JogadorGerado>(38);
        var papeisProfissionais = expandir(false);
        var papeisDaBase = expandir(true);

        for (var i = 0; i < POSICOES_PROFISSIONAIS.size(); i++) {
            jogadores.add(criar(clube, papeisProfissionais.get(i), POSICOES_PROFISSIONAIS.get(i),
                    i + 1, aleatorio, chavesUsadas));
        }
        for (var i = 0; i < POSICOES_DA_BASE.size(); i++) {
            jogadores.add(criar(clube, papeisDaBase.get(i), POSICOES_DA_BASE.get(i),
                    null, aleatorio, chavesUsadas));
        }
        return List.copyOf(jogadores);
    }

    private static List<PapelNoElenco> expandir(boolean base) {
        var papeis = new ArrayList<PapelNoElenco>();
        for (var papel : PapelNoElenco.values()) {
            if (papel.daBase() == base) {
                for (var i = 0; i < papel.quantidade(); i++) {
                    papeis.add(papel);
                }
            }
        }
        return papeis;
    }

    private static JogadorGerado criar(ClubeGerado clube, PapelNoElenco papel, String posicao,
                                       Integer camisa, SplittableRandom aleatorio,
                                       Set<String> chavesUsadas) {
        var referencia = papel.daBase() ? clube.qualidadeBase() : clube.nivelElenco();
        var alvo = limitar(referencia + entre(aleatorio, papel.deltaMinimo(), papel.deltaMaximo()));
        var idade = entre(aleatorio, papel.idadeMinima(), papel.idadeMaxima());
        var nascimento = LocalDate.of(ANO_DA_TEMPORADA - idade,
                entre(aleatorio, 1, 12), entre(aleatorio, 1, 28));

        var nome = sortearNomeInedito(aleatorio, nascimento, chavesUsadas);
        var chaveNatural = ChaveDeJogador.de(nome, nascimento, ISO_PAIS);
        chavesUsadas.add(chaveNatural);

        return new JogadorGerado(
                GeradorDeNomes.slug(nome) + "-" + Integer.toHexString(chaveNatural.hashCode() & 0xFFFF),
                chaveNatural,
                ChaveDeJogador.semente(chaveNatural),
                nome,
                nomeDeExibicao(nome),
                nascimento,
                posicao,
                PES.get(aleatorio.nextInt(PES.size())),
                entre(aleatorio, 165, 197),
                entre(aleatorio, 62, 92),
                alvo,
                potencial(papel, alvo, idade, aleatorio),
                entre(aleatorio, 3, 8),
                papel.categoria(),
                camisa);
    }

    /**
     * O espaço entre overall e potencial encolhe com a idade: aos 32 o jogador já é
     * o que vai ser. É o que faz "jovem promissor" significar alguma coisa.
     */
    private static int potencial(PapelNoElenco papel, int alvo, int idade,
                                 SplittableRandom aleatorio) {
        if (papel.daBase()) {
            return limitar(Math.max(alvo, entre(aleatorio, papel.potencialMinimo(), papel.potencialMaximo())));
        }
        var folgaMaxima = Math.max(0, 32 - idade);
        return limitar(alvo + entre(aleatorio, 0, folgaMaxima));
    }

    private static String sortearNomeInedito(SplittableRandom aleatorio, LocalDate nascimento,
                                             Set<String> chavesUsadas) {
        // Chave natural duplicada viola a unicidade da tabela jogador e derrubaria a
        // carga inteira. Re-sortear é mais barato que descobrir isso em produção.
        for (var tentativa = 0; tentativa < 100; tentativa++) {
            var nome = sortearNome(aleatorio);
            if (!chavesUsadas.contains(ChaveDeJogador.de(nome, nascimento, ISO_PAIS))) {
                return nome;
            }
        }
        throw new IllegalStateException("Pool de nomes esgotado para " + nascimento);
    }

    private static String sortearNome(SplittableRandom aleatorio) {
        var prenome = GeradorDeNomes.PRENOMES.get(aleatorio.nextInt(GeradorDeNomes.PRENOMES.size()));
        var primeiro = GeradorDeNomes.SOBRENOMES.get(aleatorio.nextInt(GeradorDeNomes.SOBRENOMES.size()));
        var segundo = GeradorDeNomes.SOBRENOMES.get(aleatorio.nextInt(GeradorDeNomes.SOBRENOMES.size()));
        return primeiro.equals(segundo)
                ? prenome + " " + primeiro
                : prenome + " " + primeiro + " " + segundo;
    }

    private static String nomeDeExibicao(String nomeCompleto) {
        var partes = nomeCompleto.split(" ");
        return partes[0] + " " + partes[partes.length - 1];
    }

    private static int entre(SplittableRandom aleatorio, int minimo, int maximo) {
        return minimo >= maximo ? minimo : aleatorio.nextInt(minimo, maximo + 1);
    }

    private static int limitar(int valor) {
        return Math.clamp(valor, 25, 95);
    }
}
```

- [ ] **Step 7: Escrever `AtributosGerados` e `FabricaDeAtributos`**

`mundo/internal/AtributosGerados.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/** As 18 skills, na ordem em que o schema as declara. */
record AtributosGerados(int ritmo, int forca, int folego, int salto, int agilidade,
                        int passe, int drible, int cruzamento, int frieza, int finalizacao,
                        int cabeceio, int falta, int penalti, int desarme, int marcacao,
                        int golReflexo, int golPosicionamento, int golManejo) {

    /** Usado pelos testes para varrer as 18 sem repetir o nome de cada uma. */
    Map<String, Integer> todas() {
        var mapa = new LinkedHashMap<String, Integer>();
        mapa.put("RITMO", ritmo);
        mapa.put("FORCA", forca);
        mapa.put("FOLEGO", folego);
        mapa.put("SALTO", salto);
        mapa.put("AGILIDADE", agilidade);
        mapa.put("PASSE", passe);
        mapa.put("DRIBLE", drible);
        mapa.put("CRUZAMENTO", cruzamento);
        mapa.put("FRIEZA", frieza);
        mapa.put("FINALIZACAO", finalizacao);
        mapa.put("CABECEIO", cabeceio);
        mapa.put("FALTA", falta);
        mapa.put("PENALTI", penalti);
        mapa.put("DESARME", desarme);
        mapa.put("MARCACAO", marcacao);
        mapa.put("GOL_REFLEXO", golReflexo);
        mapa.put("GOL_POSICIONAMENTO", golPosicionamento);
        mapa.put("GOL_MANEJO", golManejo);
        return mapa;
    }
}
```

`mundo/internal/FabricaDeAtributos.java`:

```java
package br.com.api.footfirma.mundo.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Gera as 18 skills a partir de um alvo de overall. O overall não é escrito: o
 * módulo `avaliacao` o deriva por média ponderada, e como os pesos de cada perfil
 * somam 1,0, gerar em torno do alvo as skills que têm peso faz o overall cair perto
 * dele sem inverter fórmula nenhuma.
 *
 * <p>O conjunto de skills relevantes por posição duplica o que a V13 declara. É
 * duplicação consciente: a alternativa seria expor os pesos na interface pública de
 * `avaliacao` e acoplar a geração à versão do perfil de avaliação.
 */
final class FabricaDeAtributos {

    private static final Set<String> SKILLS_DE_GOLEIRO =
            Set.of("GOL_REFLEXO", "GOL_POSICIONAMENTO", "GOL_MANEJO");

    private static final Map<String, Set<String>> RELEVANTES_POR_POSICAO = Map.of(
            "GOL", Set.of("SALTO", "AGILIDADE", "PASSE", "FRIEZA",
                    "GOL_REFLEXO", "GOL_POSICIONAMENTO", "GOL_MANEJO"),
            "ZAG", Set.of("RITMO", "FORCA", "FOLEGO", "SALTO", "AGILIDADE", "PASSE",
                    "FRIEZA", "CABECEIO", "DESARME", "MARCACAO"),
            "LTD", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "DESARME", "MARCACAO"),
            "LTE", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "DESARME", "MARCACAO"),
            "VOL", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "FRIEZA", "CABECEIO", "DESARME", "MARCACAO"),
            "MEC", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "FINALIZACAO", "DESARME"),
            "MEA", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "FINALIZACAO"),
            "PTA", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "FINALIZACAO"),
            "ATA", Set.of("RITMO", "FORCA", "FOLEGO", "SALTO", "AGILIDADE", "PASSE",
                    "DRIBLE", "FRIEZA", "FINALIZACAO", "CABECEIO"));

    private static final List<String> ORDEM = List.of(
            "RITMO", "FORCA", "FOLEGO", "SALTO", "AGILIDADE", "PASSE", "DRIBLE",
            "CRUZAMENTO", "FRIEZA", "FINALIZACAO", "CABECEIO", "FALTA", "PENALTI",
            "DESARME", "MARCACAO", "GOL_REFLEXO", "GOL_POSICIONAMENTO", "GOL_MANEJO");

    private FabricaDeAtributos() {
    }

    static AtributosGerados gerar(String posicao, int alvoOverall, SplittableRandom aleatorio) {
        var relevantes = RELEVANTES_POR_POSICAO.get(posicao);
        var goleiro = "GOL".equals(posicao);
        var valores = ORDEM.stream()
                .map(skill -> valorDe(skill, relevantes, goleiro, alvoOverall, aleatorio))
                .toList();
        return new AtributosGerados(
                valores.get(0), valores.get(1), valores.get(2), valores.get(3), valores.get(4),
                valores.get(5), valores.get(6), valores.get(7), valores.get(8), valores.get(9),
                valores.get(10), valores.get(11), valores.get(12), valores.get(13),
                valores.get(14), valores.get(15), valores.get(16), valores.get(17));
    }

    private static int valorDe(String skill, Set<String> relevantes, boolean goleiro,
                               int alvo, SplittableRandom aleatorio) {
        if (relevantes.contains(skill)) {
            return limitar(alvo + aleatorio.nextInt(-3, 4));
        }
        // Skill fora do perfil não entra no overall, mas aparece na ficha: um
        // goleiro com finalização 70 seria absurdo visível.
        var forasteira = goleiro != SKILLS_DE_GOLEIRO.contains(skill);
        return forasteira
                ? limitar(aleatorio.nextInt(8, 26))
                : limitar(aleatorio.nextInt(30, 61));
    }

    private static int limitar(int valor) {
        return Math.clamp(valor, 1, 99);
    }
}
```

- [ ] **Step 8: Rodar os testes e ver passar**

Run: `./gradlew test --tests '*FabricaDeElencoTest' --tests '*FabricaDeAtributosTest'`
Expected: PASS, os onze testes.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(mundo): monta elenco por papel e atributos por alvo de overall

Cada elenco tem cotas fixas — estrela, titulares, rotativos, reservas, crias —
para que todo clube tenha um destaque. Gaussiana pura permitiria elenco inteiro
dentro de seis pontos, correto e sem graça.

Os atributos saem do alvo de overall: como os pesos de cada perfil somam 1,0,
gerar em torno do alvo as skills que pesam basta, sem inverter fórmula.
EOF
)"
```

---

### Task 7: Persistir os elencos e materializar o overall

**Files:**
- Modify: `src/main/java/br/com/api/footfirma/mundo/internal/MundoServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/mundo/MundoIntegridadeTest.java`

**Interfaces:**
- Consumes: `JogadorService.listarPosicoes()` → `List<PosicaoCatalogo(Long id, String codigo, String nome, String setor)>`, `JogadorService.sincronizarJogador/sincronizarAtributos/sincronizarAtributosOcultos/sincronizarVinculo`, `AvaliacaoService.materializar(String)` → `ResultadoMaterializacao`, `FabricaDeElenco.gerar(...)`, `FabricaDeAtributos.gerar(...)`.
- Produces: mundo completo no banco. Nada de novo na interface pública.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/mundo/MundoIntegridadeTest.java`:

```java
package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MundoIntegridadeTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // Gerar uma vez para a classe inteira: são 1.520 jogadores, e repetir por teste
    // multiplicaria um minuto de carga por seis sem checar nada de novo.
    @BeforeAll
    void gerarOMundo() {
        mundoService.gerar();
    }

    @Test
    void deveCriarMilQuinhentosEVinteJogadores() {
        assertThat(contar("jogador")).isEqualTo(1_520);
        assertThat(contar("jogador_atributo")).isEqualTo(1_520);
        assertThat(contar("jogador_atributo_oculto")).isEqualTo(1_520);
        assertThat(contar("jogador_vinculo")).isEqualTo(1_520);
    }

    @Test
    void deveDarVinteESeisProfissionaisEDozeDaBaseACadaClube() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id,
                           count(*) filter (where categoria = 'PROFISSIONAL') as profissionais,
                           count(*) filter (where categoria = 'BASE') as base
                    from jogador_vinculo group by clube_id
                ) elenco where profissionais <> 26 or base <> 12
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void deveCobrirAsNovePosicoesEmTodoElencoProfissional() {
        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select v.clube_id, count(distinct p.codigo) as posicoes
                    from jogador_vinculo v
                    join jogador j on j.id = v.jogador_id
                    join posicao p on p.id = j.posicao_principal_id
                    where v.categoria = 'PROFISSIONAL'
                    group by v.clube_id
                ) cobertura where posicoes <> 9
                """, Integer.class);
        assertThat(incompletos).isZero();
    }

    @Test
    void deveDarCamisasUnicasDentroDeCadaClube() {
        var duplicadas = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id, numero_camisa from jogador_vinculo
                    where numero_camisa is not null
                    group by clube_id, numero_camisa having count(*) > 1
                ) repetidas
                """, Integer.class);
        assertThat(duplicadas).isZero();
    }

    @Test
    void deveMaterializarOverallDeTodoJogadorNasNovePosicoes() {
        assertThat(contar("jogador_overall")).isEqualTo(1_520 * 9);
    }

    @Test
    void deveManterOOverallProximoDoAlvoEmTodaPosicao() {
        // O alvo não é gravado; o que se verifica é que o overall na posição
        // principal ficou dentro da faixa que a curva de papéis produz.
        var forasteiros = jdbcTemplate.queryForObject("""
                select count(*) from jogador_overall o
                join jogador j on j.id = o.jogador_id
                where o.posicao_id = j.posicao_principal_id
                  and (o.overall < 20 or o.overall > 97)
                """, Integer.class);
        assertThat(forasteiros).isZero();
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*MundoIntegridadeTest'`
Expected: FAIL — `contar("jogador")` devolve 0.

- [ ] **Step 3: Persistir os elencos**

Em `MundoServiceImpl`, injete `JogadorService` e `AvaliacaoService` e acrescente ao fim de `gerar()`, antes do `return`:

```java
        criarElencos(clubes, idsPorSlug, temporadaId, paisId, contagens);
        var materializacao = avaliacaoService.materializar(TEMPORADA);
        contagens.add(new ContagemPorEntidade("jogador_overall", materializacao.linhas(), 0));
```

`ResultadoMaterializacao` é `record ResultadoMaterializacao(String temporada, int jogadores, int linhas)` — `linhas()` é a contagem de linhas de overall gravadas, nove por jogador.

Os métodos novos:

```java
    private void criarElencos(List<ClubeGerado> clubes, Map<String, Long> idsPorSlug,
                              Long temporadaId, Long paisId, List<ContagemPorEntidade> contagens) {
        var posicoes = jogadorService.listarPosicoes().stream()
                .collect(Collectors.toMap(PosicaoCatalogo::codigo, PosicaoCatalogo::id));
        var chavesUsadas = new HashSet<String>();
        var total = 0;
        for (var clube : clubes) {
            // Um sub-gerador por clube: mexer no clube 7 não pode deslocar o 8, ou
            // qualquer ajuste reescreveria o mundo inteiro e o diff ficaria ilegível.
            var aleatorio = new SplittableRandom(propriedades.semente() + clube.slug().hashCode());
            for (var jogador : FabricaDeElenco.gerar(clube, aleatorio, chavesUsadas)) {
                gravarJogador(jogador, clube, idsPorSlug, posicoes, temporadaId, paisId, aleatorio);
                total++;
            }
        }
        contagens.add(new ContagemPorEntidade("jogador", total, 0));
    }

    private void gravarJogador(JogadorGerado jogador, ClubeGerado clube,
                               Map<String, Long> idsPorSlug, Map<String, Long> posicoes,
                               Long temporadaId, Long paisId, SplittableRandom aleatorio) {
        var jogadorId = jogadorService.sincronizarJogador(new DadosDeJogador(
                jogador.slug(), jogador.chaveNatural(), jogador.semente(),
                jogador.nomeCompleto(), jogador.nomeExibicao(), jogador.dataNascimento(),
                paisId, null, jogador.alturaCm(), jogador.pesoKg(), jogador.pePreferido(),
                posicoes.get(jogador.posicao()), "GERADO")).id();

        var skills = FabricaDeAtributos.gerar(jogador.posicao(), jogador.alvoOverall(), aleatorio);
        jogadorService.sincronizarAtributos(new DadosDeAtributos(
                jogadorId, temporadaId,
                skills.ritmo(), skills.forca(), skills.folego(), skills.salto(), skills.agilidade(),
                skills.passe(), skills.drible(), skills.cruzamento(), skills.frieza(),
                skills.finalizacao(), skills.cabeceio(), skills.falta(), skills.penalti(),
                skills.desarme(), skills.marcacao(), skills.golReflexo(),
                skills.golPosicionamento(), skills.golManejo(),
                jogador.potencialBase(), jogador.potencialVariacao(),
                "GERADO", OffsetDateTime.now()));

        // Atributo oculto nasce da semente do jogador, não do sorteio do clube: é
        // personalidade, e personalidade não muda de time.
        var daSemente = new SplittableRandom(jogador.semente());
        jogadorService.sincronizarAtributosOcultos(new DadosDeAtributosOcultos(
                jogadorId, daSemente.nextInt(20, 100), daSemente.nextInt(20, 100),
                daSemente.nextInt(20, 100), daSemente.nextInt(20, 100), daSemente.nextInt(20, 100),
                daSemente.nextInt(20, 100), daSemente.nextInt(20, 100), daSemente.nextInt(20, 100)));

        jogadorService.sincronizarVinculo(new DadosDeVinculo(
                jogadorId, idsPorSlug.get(clube.slug()), temporadaId, "CONTRATO",
                jogador.categoria(), jogador.numeroCamisa(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                valorDeMercado(jogador)));
    }

    /**
     * Valor cresce com overall e com o espaço até o potencial, e cai com a idade.
     * Não depende da riqueza do clube: valor de mercado é atributo do jogador.
     */
    private BigDecimal valorDeMercado(JogadorGerado jogador) {
        var idade = 2026 - jogador.dataNascimento().getYear();
        var base = Math.pow(jogador.alvoOverall() / 10.0, 4) * 1_200;
        var promessa = 1 + (jogador.potencialBase() - jogador.alvoOverall()) * 0.05;
        var desgaste = idade <= 27 ? 1.0 : Math.max(0.25, 1 - (idade - 27) * 0.12);
        return BigDecimal.valueOf(base * promessa * desgaste).setScale(2, RoundingMode.HALF_UP);
    }
```

O construtor de `MundoServiceImpl` chega a sete dependências. É o orquestrador, e cada uma é um módulo do catálogo — dividi-lo só moveria a lista de lugar. Registre isso no ADR da Task 9.

- [ ] **Step 4: Rodar o teste e ver passar**

Run: `./gradlew test --tests '*MundoIntegridadeTest'`
Expected: PASS. Se falhar com violação de unicidade em `jogador.slug`, o sufixo hexadecimal do slug colidiu — aumente-o para `0xFFFFFF` em `FabricaDeElenco.criar`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(mundo): grava os 1.520 jogadores e materializa o overall

Um sub-gerador por clube: mexer no clube 7 não desloca o 8, e um ajuste de
faixa não reescreve o mundo inteiro.

Atributo oculto nasce da semente do jogador, não do sorteio do clube —
personalidade não muda de time.
EOF
)"
```

---

### Task 8: Testes de balanceamento, determinismo e limpeza

Esta task não muda produção. Ela existe porque balanceamento sem teste degrada em ruído no primeiro ajuste de faixa.

**Files:**
- Test: `src/test/java/br/com/api/footfirma/mundo/MundoBalanceamentoTest.java`
- Test: `src/test/java/br/com/api/footfirma/mundo/MundoDeterminismoTest.java`

**Interfaces:**
- Consumes: `MundoService.gerar()`, `RelatorioDeMundo`.
- Produces: nada — é rede de proteção.

- [ ] **Step 1: Escrever o teste de balanceamento**

Crie `src/test/java/br/com/api/footfirma/mundo/MundoBalanceamentoTest.java`:

```java
package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MundoBalanceamentoTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeAll
    void gerarOMundo() {
        mundoService.gerar();
    }

    @Test
    void devePorAPrimeiraDivisaoAcimaDaSegunda() {
        var medias = jdbcTemplate.queryForList("""
                select c.nivel, avg(o.overall) as media
                from jogador_overall o
                join jogador j on j.id = o.jogador_id and j.posicao_principal_id = o.posicao_id
                join jogador_vinculo v on v.jogador_id = j.id and v.categoria = 'PROFISSIONAL'
                join edicao_participante p on p.clube_id = v.clube_id
                join edicao e on e.id = p.edicao_id
                join competicao c on c.id = e.competicao_id
                group by c.nivel order by c.nivel
                """);
        var primeira = ((Number) medias.getFirst().get("media")).doubleValue();
        var segunda = ((Number) medias.getLast().get("media")).doubleValue();
        assertThat(primeira).isGreaterThan(segunda + 4);
    }

    @Test
    void deveDarUmDestaqueACadaClube() {
        // Todo clube precisa de alguém para se olhar, por pior que seja o elenco.
        var semDestaque = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select v.clube_id, max(o.overall) as melhor, avg(o.overall) as media
                    from jogador_overall o
                    join jogador j on j.id = o.jogador_id and j.posicao_principal_id = o.posicao_id
                    join jogador_vinculo v on v.jogador_id = j.id and v.categoria = 'PROFISSIONAL'
                    group by v.clube_id
                ) elenco where melhor < media + 8
                """, Integer.class);
        assertThat(semDestaque).isZero();
    }

    @Test
    void deveEsconderJoiaNaBaseDeClubePobre() {
        var clubesComJoia = jdbcTemplate.queryForObject("""
                select count(distinct v.clube_id)
                from jogador_vinculo v
                join clube c on c.id = v.clube_id
                join jogador_atributo a on a.jogador_id = v.jogador_id
                where v.categoria = 'BASE' and c.forca_financeira < 50 and a.potencial_base >= 88
                """, Integer.class);
        assertThat(clubesComJoia).isPositive();
    }

    @Test
    void deveFazerOOverallMedioSeguirONivelDoElenco() {
        // Correlação, não igualdade: o overall é derivado dos atributos, e o ruído
        // por skill desloca o resultado alguns pontos.
        var discrepantes = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select c.id,
                           round(0.6 * c.reputacao + 0.4 * c.forca_financeira) as nivel,
                           avg(o.overall) as media
                    from clube c
                    join jogador_vinculo v on v.clube_id = c.id and v.categoria = 'PROFISSIONAL'
                    join jogador j on j.id = v.jogador_id
                    join jogador_overall o on o.jogador_id = j.id
                                          and o.posicao_id = j.posicao_principal_id
                    group by c.id, c.reputacao, c.forca_financeira
                ) comparacao where abs(media - nivel) > 8
                """, Integer.class);
        assertThat(discrepantes).isZero();
    }

    @Test
    void deveTerGiganteEndividadoComElencoAbaixoDaReputacao() {
        var gigantes = jdbcTemplate.queryForObject("""
                select count(*) from clube
                where reputacao >= 75 and forca_financeira <= 50
                """, Integer.class);
        assertThat(gigantes).isPositive();
    }
}
```

- [ ] **Step 2: Escrever o teste de determinismo e de limpeza**

Crie `src/test/java/br/com/api/footfirma/mundo/MundoDeterminismoTest.java`:

```java
package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "footfirma.mundo.recriar=true")
class MundoDeterminismoTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveProduzirOMesmoMundoNaSegundaGeracao() {
        mundoService.gerar();
        var primeira = fotografia();

        mundoService.gerar();
        var segunda = fotografia();

        assertThat(segunda).isEqualTo(primeira);
    }

    @Test
    void naoDeveDuplicarNemDeixarOrfaoAoRegerar() {
        mundoService.gerar();
        mundoService.gerar();

        assertThat(contar("clube")).isEqualTo(40);
        assertThat(contar("jogador")).isEqualTo(1_520);
        assertThat(contar("jogador_vinculo")).isEqualTo(1_520);
        assertThat(contar("""
                jogador j where not exists (
                    select 1 from jogador_vinculo v where v.jogador_id = j.id)
                """)).isZero();
    }

    @Test
    void deveRelatarASementeUsada() {
        var relatorio = mundoService.gerar();

        assertThat(relatorio.semente()).isEqualTo(20260803L);
        assertThat(relatorio.temporada()).isEqualTo("2026");
        assertThat(relatorio.contagens()).isNotEmpty();
    }

    private List<String> fotografia() {
        return jdbcTemplate.queryForList("""
                select j.slug || ':' || o.overall
                from jogador j
                join jogador_overall o on o.jogador_id = j.id
                                      and o.posicao_id = j.posicao_principal_id
                order by j.slug
                """, String.class);
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
```

- [ ] **Step 3: Rodar os dois e ver o resultado**

Run: `./gradlew test --tests '*MundoBalanceamentoTest' --tests '*MundoDeterminismoTest'`
Expected: PASS.

Se `deveFazerOOverallMedioSeguirONivelDoElenco` falhar, o desvio entre alvo e overall materializado é maior que o previsto — **não afrouxe o limite de 8**. Investigue primeiro em `FabricaDeAtributos`: skill relevante gerada fora da faixa `alvo ± 3`, ou uma posição cujo conjunto de skills relevantes diverge da `V13`. Compare com:

```bash
grep -n "'MEC'\|'MEA'\|'PTA'\|'ATA'" src/main/resources/db/migration/V13__popula_perfil_avaliacao.sql
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
test(mundo): cobre balanceamento, determinismo e regeração

Balanceamento sem teste degrada em ruído no primeiro ajuste de faixa: um erro
de sinal produziria mundo plausível à primeira vista e sem nenhuma das
propriedades pedidas.
EOF
)"
```

---

### Task 9: Documentação e fechamento

**Files:**
- Create: `backend/footfirma/docs/adr/2026-08-03-mundo-gerado.md`
- Create: `backend/footfirma/docs/runbooks/mundo.md`
- Modify: `AGENTS.md` (raiz — seção "Estado atual")
- Modify: `backend/footfirma/src/main/resources/application.properties`

**Interfaces:**
- Consumes: tudo o que as tasks anteriores produziram.
- Produces: documentação com lastro no código, conforme o padrão de `docs/README.md`.

- [ ] **Step 1: Declarar as propriedades com valor explícito**

Em `application.properties`, acrescente ao fim:

```properties
# Geração do mundo — só tem efeito sob o profile `mundo`
footfirma.mundo.semente=20260803
footfirma.mundo.recriar=false
footfirma.mundo.encerrar-ao-final=true
```

`recriar=false` é o default seguro: subir com o profile e apagar o catálogo por descuido é pior que gerar por cima.

- [ ] **Step 2: Escrever o ADR**

Crie `backend/footfirma/docs/adr/2026-08-03-mundo-gerado.md`, seguindo o formato dos ADRs existentes (`## Fontes`, `## Decisão`, `## Por quê`, `## Consequências`, `## Como validar`). Ele precisa registrar, com estas justificativas:

1. **O mundo é gerado, não importado** — sem fonte externa não há do que desconfiar, e manifesto e checksum viram cerimônia sobre arquivo próprio.
2. **As fábricas são puras** — `FabricaDeClubes`, `FabricaDeElenco`, `FabricaDeAtributos` e `GeradorDeNomes` não tocam Spring nem banco, o que dá teste unitário sem Docker e mantém o orquestrador enxuto.
3. **`LimpezaDoCatalogo` usa `JdbcTemplate` sobre tabela alheia** — exceção deliberada: apagar o catálogo inteiro é operação de infraestrutura, e a alternativa seria expor método destrutivo na interface pública de cinco módulos.
4. **`MundoServiceImpl` tem sete dependências**, acima do limite de 4~5 de `.rules/java-core.md` — é o orquestrador, cada uma é um módulo do catálogo, e dividi-lo só moveria a lista de lugar. Mesmo precedente do importador que ele substitui.
5. **O conjunto de skills relevantes por posição duplica a `V13`** — a alternativa seria expor os pesos na interface pública de `avaliacao` e acoplar a geração à versão do perfil.

Em `## Como validar`:

```bash
./gradlew test --tests '*ModularidadeTest' \
               --tests '*MundoBalanceamentoTest' \
               --tests '*MundoDeterminismoTest'
```

- [ ] **Step 3: Escrever o runbook**

Crie `backend/footfirma/docs/runbooks/mundo.md` com `Status: verificado em 2026-08-03`, a seção `## Fontes` apontando para `MundoServiceImpl`, `CatalogoDeArquetipos`, `FabricaDeElenco` e `PropriedadesDeMundo`, e cobrindo:

- **Como gerar**: `./gradlew bootRun --args='--spring.profiles.active=mundo'` — com o aviso de que o guard bloqueia subir serviço, então **quem roda é o usuário**, com `! <comando>` na própria sessão.
- **Como trocar o mundo**: `--footfirma.mundo.semente=N --footfirma.mundo.recriar=true`. Trocar a semente sem `recriar` deixa o mundo anterior órfão, porque os slugs novos não colidem com os antigos.
- **O que é apagado e o que não é**: a lista de `LimpezaDoCatalogo`; geografia, posição, característica e perfil de avaliação sobrevivem por serem seed de migration.
- **Como rebalancear**: editar `CatalogoDeArquetipos` (faixas e quotas) ou `PapelNoElenco` (cotas e deltas), e rodar `MundoBalanceamentoTest`.
- **Conteúdo esperado**: 2 competições, 40 clubes, 40 estádios, 1.520 jogadores, 13.680 linhas de overall.

- [ ] **Step 4: Atualizar o `AGENTS.md` da raiz**

Na seção "Estado atual", substitua o parágrafo sobre `importacao` e fixtures. O texto novo precisa dizer:

- Os módulos de domínio agora são `temporada`, `geografia`, `clube`, `jogador`, `competicao`, `avaliacao` e **`mundo`** (o `importacao` saiu).
- São dezesseis migrations Flyway.
- Em base zerada o banco continua vazio de clubes e jogadores; rodar o gerador sob o profile `mundo` popula **duas ligas fictícias, 40 clubes e 1.520 jogadores, todos inventados** — não há mais dado real de clube nem de competição.
- O pipeline Python de dados reais saiu do roadmap: o mundo é gerado.

Atualize também a contagem de testes citada, medindo-a de fato:

```bash
./gradlew test 2>&1 | grep -c "tests completed" || ./gradlew test --info | tail -20
```

Se o número exato não sair do comando, conte os arquivos de teste e escreva a contagem de classes em vez da de casos — não invente número.

- [ ] **Step 5: Rodar a suíte completa**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Este é o portão final: compila e roda tudo, incluindo `ModularidadeTest`.

- [ ] **Step 6: Percorrer o checklist do repositório**

Abra `backend/footfirma/.rules/java-checklist.md` e percorra os dez itens. Os que se aplicam aqui e merecem atenção:

- Seção 2: `mundo` não importa `internal` de outro módulo.
- Seção 5: `V15` e `V16` acompanham as mudanças; nenhuma migration existente foi editada.
- Seção 8: nenhum teste foi enfraquecido para passar.
- Seção 9: ADR e runbook escritos; `AGENTS.md` atualizado.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
docs(mundo): registra ADR do gerador e runbook de geração

Documenta as três exceções deliberadas: limpeza por JdbcTemplate sobre tabela
alheia, sete dependências no orquestrador e o conjunto de skills por posição
duplicado da V13.
EOF
)"
```

---

## Registro de fechamento

Ao terminar a Task 9, a resposta final precisa declarar, conforme o `java-checklist.md`:

1. **Arquivos principais alterados** — os que importam, não a lista inteira.
2. **Validações executadas** — comando e resultado real. Se `./gradlew build` falhou, mostre a saída.
3. **Validações não executadas e por quê** — "não rodei os testes de integração porque o Docker está parado" é informação útil; omitir é enganoso.
4. **Alterações preexistentes no worktree** — o que já estava modificado antes de começar.

Não escreva "pronto" sem ter rodado o comando que sustenta a frase.

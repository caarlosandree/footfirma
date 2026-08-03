# Treinador — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o módulo Spring Modulith `treinador`, com moral relativa à expectativa, demissão, mercado de propostas, seis skills distribuíveis e vínculo individual com cada jogador — construído e testado sem que o módulo `partida` exista.

**Architecture:** Núcleo de funções puras (`internal/`, sem Spring, sem JPA, sem relógio) que recebem estado e fato e devolvem estado novo. Borda de `@ApplicationModuleListener` consumindo eventos de domínio. Os testes publicam eventos sintéticos; quando `partida` for escrita, ela publica os mesmos eventos e nada no módulo muda.

**Tech Stack:** Java 25 · Spring Boot 4 · Spring Modulith · PostgreSQL via Flyway · JPA · MapStruct · Lombok · JUnit 5 + AssertJ + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-03-treinador-design.md`

## Global Constraints

- Pacote base `br.com.api.footfirma`; um módulo por pacote direto abaixo dele.
- Tipos no pacote raiz do módulo são API pública; subpacotes são internos. Nada de `treinador.domain.*` vazando para outro módulo.
- Injeção **somente por construtor**, via `@RequiredArgsConstructor`. `@Autowired` em campo é proibido.
- `@Transactional(readOnly = true)` na classe do service, `@Transactional` no método que escreve. Nunca em controller.
- Lombok em `@Entity`: apenas `@Getter`/`@Setter`. `@Data`, `@EqualsAndHashCode` e `@ToString` são proibidos; `equals`/`hashCode` seguem o padrão de `clube/domain/Clube.java` (compara `id`, `hashCode` retorna `Classe.class.hashCode()`).
- MapStruct roda com `unmappedTargetPolicy=ERROR`: todo campo do alvo precisa de origem ou `ignore = true`. Não escreva `componentModel = "spring"` — já é padrão.
- Migrations aplicadas são **imutáveis**; o guard bloqueia edição. Erro em migration versionada se corrige com migration nova.
- Testes de persistência usam Testcontainers. H2, banco em memória e mock de `Repository` são proibidos.
- `@DataJpaTest` exige `@Import(TestcontainersConfiguration.class)` **e** `@AutoConfigureTestDatabase(replace = Replace.NONE)`.
- Nomes de teste em português: `deve<Comportamento>Quando<Condição>`. Asserções com AssertJ.
- Commits em pt-BR, formato `<tipo>(treinador): <descrição imperativa>`, máximo 72 caracteres na primeira linha, **nunca** `Co-authored-by`.
- Rode `./gradlew compileJava` durante o trabalho e `./gradlew test --tests '*<Alvo>Test'` para o teste da tarefa. **Não** rode a suíte completa a cada passo.
- Não suba serviços (`bootRun`, `docker compose up`) — o guard bloqueia. Testcontainers sobe sozinho no `test`.

## File Structure

```
src/main/java/br/com/api/footfirma/
├── shared/evento/
│   └── PartidaEncerrada.java          # evento sem produtor ainda; shared é OPEN
│   └── TemporadaEncerrada.java
└── treinador/
    ├── package-info.java              # @ApplicationModule(displayName = "Treinador")
    ├── TreinadorService.java          # porta pública
    ├── TreinadorDemitido.java         # eventos publicados (records, raiz do módulo)
    ├── TreinadorSobPressao.java
    ├── PropostaEnviada.java
    ├── VinculoIniciado.java · VinculoEncerrado.java · JogadorInsatisfeito.java
    ├── domain/      Treinador · VinculoTreinador · TreinadorSkill · TreinadorJogador
    │                Afinidade · Proposta · Skill · StatusConfianca · Resultado
    │                TipoTreinador · MotivoFim · StatusProposta
    ├── dto/         NovoTreinador · TreinadorResumo · TreinadorDetalhe
    │                DistribuicaoDeSkills · VinculoResumo · PropostaResumo
    ├── repository/  TreinadorRepository · VinculoTreinadorRepository
    │                TreinadorSkillRepository · TreinadorJogadorRepository
    │                AfinidadeRepository · PropostaRepository
    ├── mapper/      TreinadorMapper
    ├── web/         TreinadorController
    └── internal/    ConstantesDeTreinador · FatoDeResultado · FatoDeMinutagem
                     MotorDeMoral · MotorDeConfianca · PoliticaDeDemissao
                     CalculadoraDeExpectativa · GeradorDePropostas
                     OuvinteDePartida · OuvinteDeTemporada · TreinadorServiceImpl

src/main/resources/db/migration/V17__cria_treinador.sql
```

**Por que os eventos consumidos moram em `shared/evento/`:** no Modulith o evento pertence ao módulo produtor, e `partida` não existe. `shared` é `@ApplicationModule(type = OPEN)`, então ambos os lados o alcançam sem violar fronteira. Quando `partida` for escrita, mover os dois records para lá é um refactor de import — registrado como dívida no spec.

---

### Task 1: Schema, entidades e integridade do banco

**Files:**
- Create: `src/main/resources/db/migration/V17__cria_treinador.sql`
- Create: `src/main/java/br/com/api/footfirma/treinador/package-info.java`
- Create: `treinador/domain/{Skill,StatusConfianca,Resultado,TipoTreinador,MotivoFim,StatusProposta}.java`
- Create: `treinador/domain/{Treinador,VinculoTreinador,TreinadorSkill,TreinadorJogador,Afinidade,Proposta}.java`
- Create: `treinador/repository/{TreinadorRepository,VinculoTreinadorRepository,TreinadorSkillRepository,TreinadorJogadorRepository,AfinidadeRepository,PropostaRepository}.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/VinculoIntegridadeTest.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/TreinadorFactory.java`

**Interfaces:**
- Consumes: nada (primeira tarefa)
- Produces: as seis entidades e os seis repositories; os enums `Skill`, `StatusConfianca` (com `escala()` e `minutosEsperados()`), `Resultado`, `TipoTreinador`, `MotivoFim`, `StatusProposta`

- [ ] **Step 1: Escrever a migration**

Crie `V17__cria_treinador.sql` copiando **integralmente** o bloco de schema da seção "Schema — migration `V17`" do spec, incluindo todos os `comment on column`. Confira que `vinculo_treinador.moral`, `treinador_jogador.moral` e `treinador_jogador_afinidade.afinidade` são `numeric(4,2)`, não `integer`.

Os dois índices que carregam a integridade:

```sql
create unique index uq_vinculo_clube_ativo     on vinculo_treinador (clube_id)     where fim is null;
create unique index uq_vinculo_treinador_ativo on vinculo_treinador (treinador_id) where fim is null;
```

- [ ] **Step 2: Escrever o teste de integridade (vai falhar — não há entidade)**

```java
package br.com.api.footfirma.treinador;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VinculoIntegridadeTest {

    @Autowired VinculoTreinadorRepository vinculos;
    @Autowired TreinadorRepository treinadores;
    @Autowired TestEntityManager em;

    @Test
    void deveRejeitarSegundoVinculoAtivoNoMesmoClube() {
        var primeiro = TreinadorFactory.vinculoAtivo(treinadores.save(TreinadorFactory.humano("ana-souza")), 1L);
        vinculos.save(primeiro);
        em.flush();

        var segundo = TreinadorFactory.vinculoAtivo(treinadores.save(TreinadorFactory.humano("bia-lima")), 1L);
        vinculos.save(segundo);

        assertThatThrownBy(em::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void deveRejeitarTreinadorDirigindoDoisClubes() {
        var treinador = treinadores.save(TreinadorFactory.humano("caio-melo"));
        vinculos.save(TreinadorFactory.vinculoAtivo(treinador, 1L));
        em.flush();

        vinculos.save(TreinadorFactory.vinculoAtivo(treinador, 2L));

        assertThatThrownBy(em::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void devePermitirNovoVinculoAposEncerrarOAnterior() {
        var treinador = treinadores.save(TreinadorFactory.humano("davi-rocha"));
        var primeiro = vinculos.save(TreinadorFactory.vinculoAtivo(treinador, 1L));
        primeiro.setFim(LocalDate.of(2026, 6, 30));
        primeiro.setMotivoFim(MotivoFim.DEMISSAO);
        em.flush();

        var segundo = vinculos.save(TreinadorFactory.vinculoAtivo(treinador, 2L));
        em.flush();

        assertThat(segundo.getId()).isNotNull();
    }
}
```

- [ ] **Step 3: Rodar o teste para confirmar que falha**

Run: `./gradlew test --tests '*VinculoIntegridadeTest'`
Expected: FAIL na compilação — `TreinadorFactory`, `VinculoTreinadorRepository` e as entidades não existem.

- [ ] **Step 4: Criar os enums**

```java
package br.com.api.footfirma.treinador.domain;

public enum Skill { VISAO_DE_JOGO, PRELECAO, LIDERANCA, TREINAMENTO, TATICA, NEGOCIACAO }
```

```java
package br.com.api.footfirma.treinador.domain;

public enum StatusConfianca {
    INDISCUTIVEL(5, 85),
    IMPORTANTE(4, 65),
    ROTACAO(3, 40),
    PROMESSA(2, 20),
    FORA_DOS_PLANOS(1, 0);

    private final int escala;
    private final int minutosEsperados;

    StatusConfianca(int escala, int minutosEsperados) {
        this.escala = escala;
        this.minutosEsperados = minutosEsperados;
    }

    public int escala() {
        return escala;
    }

    public int minutosEsperados() {
        return minutosEsperados;
    }
}
```

E, no mesmo pacote: `Resultado { VITORIA, EMPATE, DERROTA }`, `TipoTreinador { HUMANO, IA }`, `MotivoFim { DEMISSAO, PEDIDO_DEMISSAO, FIM_DE_CONTRATO, ACEITOU_PROPOSTA }`, `StatusProposta { ABERTA, ACEITA, RECUSADA, EXPIRADA }`.

- [ ] **Step 5: Criar as entidades no padrão de `clube/domain/Clube.java`**

```java
package br.com.api.footfirma.treinador.domain;

@Entity
@Table(name = "vinculo_treinador")
@Getter
@Setter
public class VinculoTreinador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treinador_id", nullable = false)
    private Treinador treinador;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private LocalDate inicio;

    private LocalDate fim;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_fim")
    private MotivoFim motivoFim;

    @Column(nullable = false)
    private Double moral;

    @Column(name = "meta_posicao", nullable = false)
    private Integer metaPosicao;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected VinculoTreinador() {
    }

    public VinculoTreinador(Treinador treinador, Long clubeId, Long temporadaId,
                            LocalDate inicio, double moral, int metaPosicao) {
        this.treinador = treinador;
        this.clubeId = clubeId;
        this.temporadaId = temporadaId;
        this.inicio = inicio;
        this.moral = moral;
        this.metaPosicao = metaPosicao;
    }

    public boolean estaAtivo() {
        return fim == null;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof VinculoTreinador vinculo)) {
            return false;
        }
        return id != null && id.equals(vinculo.id);
    }

    @Override
    public int hashCode() {
        return VinculoTreinador.class.hashCode();
    }
}
```

`clube_id`, `temporada_id` e `jogador_id` são `Long` cru, **não** `@ManyToOne` para `Clube`/`Temporada`/`Jogador`: entidade JPA de outro módulo não pode vazar para cá. É o mesmo que `Clube` faz com `paisId` e `estadoId`.

Aplique o mesmo formato a `Treinador`, `TreinadorSkill` (`@IdClass` ou `@EmbeddedId` para a chave tripla), `TreinadorJogador`, `Afinidade` (chave composta `treinador_id` + `jogador_id`) e `Proposta`.

- [ ] **Step 6: Criar os repositories**

```java
package br.com.api.footfirma.treinador.repository;

public interface VinculoTreinadorRepository extends JpaRepository<VinculoTreinador, Long> {

    Optional<VinculoTreinador> findByTreinadorIdAndFimIsNull(Long treinadorId);

    Optional<VinculoTreinador> findByClubeIdAndFimIsNull(Long clubeId);

    List<VinculoTreinador> findByTemporadaIdAndFimIsNull(Long temporadaId);
}
```

Os demais seguem o padrão de `ClubeRepository`: só os métodos que alguma tarefa deste plano consome.

- [ ] **Step 7: Criar `package-info.java` e a factory de teste**

```java
@org.springframework.modulith.ApplicationModule(displayName = "Treinador")
package br.com.api.footfirma.treinador;
```

`TreinadorFactory` devolve objetos válidos por padrão, no padrão de `clube/ClubeFactory.java`:

```java
package br.com.api.footfirma.treinador;

public final class TreinadorFactory {

    private TreinadorFactory() {
    }

    public static Treinador humano(String slug) {
        var treinador = new Treinador(slug, "Nome " + slug, "Nome", LocalDate.of(1980, 1, 1),
                1L, TipoTreinador.HUMANO, 12345L);
        treinador.setReputacao(50);
        return treinador;
    }

    public static VinculoTreinador vinculoAtivo(Treinador treinador, Long clubeId) {
        return new VinculoTreinador(treinador, clubeId, 1L, LocalDate.of(2026, 1, 1), 50.0, 10);
    }
}
```

- [ ] **Step 8: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*VinculoIntegridadeTest'`
Expected: PASS nos três testes. Se `devePermitirNovoVinculoAposEncerrarOAnterior` falhar, o índice parcial saiu sem o `where fim is null`.

- [ ] **Step 9: Rodar o teste de modularidade**

Run: `./gradlew test --tests '*ModularidadeTest'`
Expected: PASS. Se falhar, alguma entidade está referenciando tipo `domain` de outro módulo — troque por `Long`.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/db/migration/V17__cria_treinador.sql \
        src/main/java/br/com/api/footfirma/treinador \
        src/test/java/br/com/api/footfirma/treinador
git commit -m "feat(treinador): cria schema, entidades e repositories do módulo"
```

---

### Task 2: Constantes e MotorDeMoral

**Files:**
- Create: `treinador/internal/ConstantesDeTreinador.java`
- Create: `treinador/internal/FatoDeResultado.java`
- Create: `treinador/internal/MotorDeMoral.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/internal/MotorDeMoralTest.java`

**Interfaces:**
- Consumes: `Resultado` (Task 1)
- Produces: `FatoDeResultado(int reputacaoMeuClube, int reputacaoAdversario, Resultado resultado, int posicaoAtual, int metaPosicao)`; `MotorDeMoral.delta(FatoDeResultado) → double`; `MotorDeMoral.aplicar(double moralAtual, FatoDeResultado) → double`; `ConstantesDeTreinador` com todos os coeficientes

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.treinador.internal;

class MotorDeMoralTest {

    private static FatoDeResultado fato(int meu, int adversario, Resultado resultado) {
        return new FatoDeResultado(meu, adversario, resultado, 10, 10);
    }

    @Test
    void deveDarMoralAltaQuandoClubePequenoVenceGigante() {
        var delta = MotorDeMoral.delta(fato(35, 88, Resultado.VITORIA));

        assertThat(delta).isCloseTo(5.9, within(0.1));
    }

    @Test
    void deveDarMoralBaixaQuandoGiganteVenceClubePequeno() {
        var delta = MotorDeMoral.delta(fato(88, 35, Resultado.VITORIA));

        assertThat(delta).isCloseTo(0.8, within(0.1));
    }

    @Test
    void devePunirGiganteQuandoEmpataComClubePequeno() {
        var delta = MotorDeMoral.delta(fato(88, 35, Resultado.EMPATE));

        assertThat(delta).isCloseTo(-3.5, within(0.1));
    }

    @Test
    void devePremiarClubePequenoQuandoEmpataComGigante() {
        var delta = MotorDeMoral.delta(fato(35, 88, Resultado.EMPATE));

        assertThat(delta).isCloseTo(1.7, within(0.1));
    }

    @Test
    void devePunirPoucoQuandoClubePequenoPerdeParaGigante() {
        var delta = MotorDeMoral.delta(fato(35, 88, Resultado.DERROTA));

        assertThat(delta).isCloseTo(-0.8, within(0.1));
    }

    @Test
    void devePunirMuitoQuandoGigantePerdeParaClubePequeno() {
        var delta = MotorDeMoral.delta(fato(88, 35, Resultado.DERROTA));

        assertThat(delta).isCloseTo(-5.9, within(0.1));
    }

    @Test
    void deveAmplificarApenasDeltaNegativoQuandoCampanhaEstaAbaixoDaMeta() {
        var atrasado = new FatoDeResultado(50, 50, Resultado.DERROTA, 12, 4);
        var noAlvo = new FatoDeResultado(50, 50, Resultado.DERROTA, 4, 4);

        assertThat(MotorDeMoral.delta(atrasado)).isLessThan(MotorDeMoral.delta(noAlvo));
    }

    @Test
    void naoDeveAmplificarDeltaPositivoQuandoCampanhaEstaAbaixoDaMeta() {
        var atrasado = new FatoDeResultado(50, 50, Resultado.VITORIA, 12, 4);
        var noAlvo = new FatoDeResultado(50, 50, Resultado.VITORIA, 4, 4);

        assertThat(MotorDeMoral.delta(atrasado)).isEqualTo(MotorDeMoral.delta(noAlvo));
    }

    @Test
    void deveManterMoralDentroDaFaixaZeroNoventaENove() {
        var golead = fato(88, 35, Resultado.DERROTA);

        assertThat(MotorDeMoral.aplicar(2.0, golead)).isBetween(0.0, 99.0);
        assertThat(MotorDeMoral.aplicar(98.0, fato(35, 88, Resultado.VITORIA))).isBetween(0.0, 99.0);
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*MotorDeMoralTest'`
Expected: FAIL — `MotorDeMoral` não existe.

- [ ] **Step 3: Implementar as constantes**

```java
package br.com.api.footfirma.treinador.internal;

/**
 * Coeficientes de balanceamento. Nenhum tem verdade de referência — são julgamento de
 * domínio, como as faixas dos arquétipos do gerador de mundo. Rebalancear é editar aqui.
 */
final class ConstantesDeTreinador {

    private ConstantesDeTreinador() {
    }

    static final double BASE_VITORIA = 3.0;
    static final double BASE_EMPATE = -1.0;
    static final double BASE_DERROTA = -3.0;

    static final double PESO_ADVERSARIO = 0.9;
    static final double PESO_ADVERSARIO_MIN = 0.25;
    static final double PESO_ADVERSARIO_MAX = 2.5;

    static final double EMPATE_INCLINACAO = 2.5;
    static final double EMPATE_MIN = -3.5;
    static final double EMPATE_MAX = 2.0;

    static final double META_INCLINACAO = 0.05;
    static final double META_MIN = 0.7;
    static final double META_MAX = 1.5;

    static final double MORAL_MIN = 0.0;
    static final double MORAL_MAX = 99.0;

    static double clamp(double valor, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }
}
```

- [ ] **Step 4: Implementar `FatoDeResultado` e `MotorDeMoral`**

```java
package br.com.api.footfirma.treinador.internal;

record FatoDeResultado(int reputacaoMeuClube, int reputacaoAdversario,
                       Resultado resultado, int posicaoAtual, int metaPosicao) {

    double diferencaDeForca() {
        return (reputacaoAdversario - reputacaoMeuClube) / 50.0;
    }
}
```

```java
package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.*;

final class MotorDeMoral {

    private MotorDeMoral() {
    }

    static double delta(FatoDeResultado fato) {
        var base = base(fato);
        return base < 0 ? base * fatorMeta(fato) : base;
    }

    static double aplicar(double moralAtual, FatoDeResultado fato) {
        return clamp(moralAtual + delta(fato), MORAL_MIN, MORAL_MAX);
    }

    private static double base(FatoDeResultado fato) {
        var d = fato.diferencaDeForca();
        return switch (fato.resultado()) {
            case VITORIA -> BASE_VITORIA * peso(1 + d * PESO_ADVERSARIO);
            case DERROTA -> BASE_DERROTA * peso(1 - d * PESO_ADVERSARIO);
            case EMPATE -> clamp(BASE_EMPATE + d * EMPATE_INCLINACAO, EMPATE_MIN, EMPATE_MAX);
        };
    }

    private static double peso(double bruto) {
        return clamp(bruto, PESO_ADVERSARIO_MIN, PESO_ADVERSARIO_MAX);
    }

    private static double fatorMeta(FatoDeResultado fato) {
        var bruto = 1 + (fato.posicaoAtual() - fato.metaPosicao()) * META_INCLINACAO;
        return clamp(bruto, META_MIN, META_MAX);
    }
}
```

- [ ] **Step 5: Rodar o teste**

Run: `./gradlew test --tests '*MotorDeMoralTest'`
Expected: PASS nos nove testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador/internal \
        src/test/java/br/com/api/footfirma/treinador/internal
git commit -m "feat(treinador): adiciona motor de moral relativo à expectativa"
```

---

### Task 3: CalculadoraDeExpectativa

**Files:**
- Create: `treinador/internal/CalculadoraDeExpectativa.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/internal/CalculadoraDeExpectativaTest.java`

**Interfaces:**
- Consumes: `ConstantesDeTreinador.clamp` (Task 2)
- Produces: `CalculadoraDeExpectativa.metaPosicao(int reputacaoDoClube, List<Integer> reputacoesDosParticipantes) → int`; `CalculadoraDeExpectativa.moralInicialDoVinculo(int reputacaoTreinador, int reputacaoClube) → double`; `CalculadoraDeExpectativa.moralInicialDoJogador(double afinidade) → double`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.treinador.internal;

class CalculadoraDeExpectativaTest {

    private static final List<Integer> DIVISAO = List.of(88, 75, 62, 50, 35);

    @Test
    void deveDarMetaDePrimeiroAoClubeMaisReputadoDaDivisao() {
        assertThat(CalculadoraDeExpectativa.metaPosicao(88, DIVISAO)).isEqualTo(1);
    }

    @Test
    void deveDarMetaDeUltimoAoClubeMenosReputadoDaDivisao() {
        assertThat(CalculadoraDeExpectativa.metaPosicao(35, DIVISAO)).isEqualTo(5);
    }

    @Test
    void deveDarMetaIntermediariaAoClubeDoMeioDaTabela() {
        assertThat(CalculadoraDeExpectativa.metaPosicao(62, DIVISAO)).isEqualTo(3);
    }

    @Test
    void deveDarMoralAltaQuandoVencedorAssumeClubeMedio() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(85, 40)).isCloseTo(68.0, within(0.1));
    }

    @Test
    void deveDarMoralBaixaQuandoDesconhecidoAssumeGigante() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(40, 85)).isCloseTo(32.0, within(0.1));
    }

    @Test
    void deveDarMoralNeutraQuandoReputacoesSaoParelhas() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(60, 60)).isCloseTo(50.0, within(0.1));
    }

    @Test
    void deveLimitarMoralInicialDoVinculoEntreVinteCincoEOitentaECinco() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(99, 0)).isEqualTo(85.0);
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(0, 99)).isEqualTo(25.0);
    }

    @Test
    void deveAdiantarMoralDoJogadorQuandoJaFoiPupilo() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(90.0)).isCloseTo(74.0, within(0.1));
    }

    @Test
    void deveDarMoralNeutraQuandoJogadorNuncaFoiDirigido() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(50.0)).isCloseTo(50.0, within(0.1));
    }

    @Test
    void devePenalizarMoralDoJogadorQueFoiQueimadoNoBanco() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(20.0)).isCloseTo(32.0, within(0.1));
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*CalculadoraDeExpectativaTest'`
Expected: FAIL — classe não existe.

- [ ] **Step 3: Adicionar as constantes que faltam em `ConstantesDeTreinador`**

```java
    static final double MORAL_VINCULO_INCLINACAO = 0.4;
    static final double MORAL_VINCULO_MIN = 25.0;
    static final double MORAL_VINCULO_MAX = 85.0;

    static final double MORAL_JOGADOR_INCLINACAO = 0.6;
    static final double MORAL_JOGADOR_MIN = 20.0;
    static final double MORAL_JOGADOR_MAX = 90.0;

    static final double NEUTRO = 50.0;
```

- [ ] **Step 4: Implementar a calculadora**

```java
package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.*;

final class CalculadoraDeExpectativa {

    private CalculadoraDeExpectativa() {
    }

    /** Rank do clube por reputação entre os participantes da edição. Maior reputação, meta 1. */
    static int metaPosicao(int reputacaoDoClube, List<Integer> reputacoesDosParticipantes) {
        var acima = reputacoesDosParticipantes.stream()
                .filter(reputacao -> reputacao > reputacaoDoClube)
                .count();
        return (int) acima + 1;
    }

    static double moralInicialDoVinculo(int reputacaoTreinador, int reputacaoClube) {
        var bruto = NEUTRO + (reputacaoTreinador - reputacaoClube) * MORAL_VINCULO_INCLINACAO;
        return clamp(bruto, MORAL_VINCULO_MIN, MORAL_VINCULO_MAX);
    }

    static double moralInicialDoJogador(double afinidade) {
        var bruto = NEUTRO + (afinidade - NEUTRO) * MORAL_JOGADOR_INCLINACAO;
        return clamp(bruto, MORAL_JOGADOR_MIN, MORAL_JOGADOR_MAX);
    }
}
```

- [ ] **Step 5: Rodar o teste**

Run: `./gradlew test --tests '*CalculadoraDeExpectativaTest'`
Expected: PASS nos dez testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador/internal \
        src/test/java/br/com/api/footfirma/treinador/internal
git commit -m "feat(treinador): calcula meta da temporada e morais iniciais"
```

---

### Task 4: PoliticaDeDemissao

**Files:**
- Create: `treinador/internal/PoliticaDeDemissao.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/internal/PoliticaDeDemissaoTest.java`

**Interfaces:**
- Consumes: `ConstantesDeTreinador` (Task 2)
- Produces: `enum Veredito { SEGURO, SOB_PRESSAO, DEMITIDO }`; `PoliticaDeDemissao.avaliar(double moral, int reputacaoDoClube, int partidasNoVinculo) → Veredito`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.treinador.internal;

class PoliticaDeDemissaoTest {

    @Test
    void deveManterSeguroQuandoMoralEstaConfortavel() {
        assertThat(PoliticaDeDemissao.avaliar(60.0, 50, 20)).isEqualTo(Veredito.SEGURO);
    }

    @Test
    void deveSinalizarPressaoQuandoMoralEntraNaZonaIntermediaria() {
        assertThat(PoliticaDeDemissao.avaliar(20.0, 50, 20)).isEqualTo(Veredito.SOB_PRESSAO);
    }

    @Test
    void deveDemitirQuandoMoralCaiAbaixoDoLimiar() {
        assertThat(PoliticaDeDemissao.avaliar(10.0, 50, 20)).isEqualTo(Veredito.DEMITIDO);
    }

    @Test
    void naoDeveDemitirDentroDaCarenciaDeCincoPartidas() {
        assertThat(PoliticaDeDemissao.avaliar(2.0, 50, 4)).isNotEqualTo(Veredito.DEMITIDO);
    }

    @Test
    void deveDemitirNoGiganteEManterNoClubePequenoComAMesmaMoral() {
        var moral = 16.0;

        assertThat(PoliticaDeDemissao.avaliar(moral, 88, 20)).isEqualTo(Veredito.DEMITIDO);
        assertThat(PoliticaDeDemissao.avaliar(moral, 35, 20)).isNotEqualTo(Veredito.DEMITIDO);
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*PoliticaDeDemissaoTest'`
Expected: FAIL — classe não existe.

- [ ] **Step 3: Adicionar constantes**

```java
    static final double LIMIAR_BASE = 15.0;
    static final double LIMIAR_INCLINACAO = 0.1;
    static final double FAIXA_DE_PRESSAO = 10.0;
    static final int CARENCIA_PARTIDAS = 5;
```

- [ ] **Step 4: Implementar**

```java
package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.*;

enum Veredito { SEGURO, SOB_PRESSAO, DEMITIDO }

final class PoliticaDeDemissao {

    private PoliticaDeDemissao() {
    }

    /**
     * @param reputacaoDoClube vem do evento, nunca do banco — ver decisão 8 do spec.
     */
    static Veredito avaliar(double moral, int reputacaoDoClube, int partidasNoVinculo) {
        var limiar = LIMIAR_BASE + (reputacaoDoClube - NEUTRO) * LIMIAR_INCLINACAO;

        if (moral >= limiar + FAIXA_DE_PRESSAO) {
            return Veredito.SEGURO;
        }
        if (moral >= limiar || partidasNoVinculo < CARENCIA_PARTIDAS) {
            return Veredito.SOB_PRESSAO;
        }
        return Veredito.DEMITIDO;
    }
}
```

- [ ] **Step 5: Rodar o teste**

Run: `./gradlew test --tests '*PoliticaDeDemissaoTest'`
Expected: PASS nos cinco testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador/internal \
        src/test/java/br/com/api/footfirma/treinador/internal
git commit -m "feat(treinador): adiciona política de demissão com carência"
```

---

### Task 5: MotorDeConfianca

**Files:**
- Create: `treinador/internal/FatoDeMinutagem.java`
- Create: `treinador/internal/MotorDeConfianca.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/internal/MotorDeConfiancaTest.java`

**Interfaces:**
- Consumes: `StatusConfianca` (Task 1), `ConstantesDeTreinador` (Task 2)
- Produces: `FatoDeMinutagem(StatusConfianca status, int minutosRecebidos, int lideranca)`; `MotorDeConfianca.delta(FatoDeMinutagem) → double`; `MotorDeConfianca.deltaPorMudancaDeStatus(StatusConfianca anterior, StatusConfianca novo) → double`; `MotorDeConfianca.aplicar(double moralAtual, FatoDeMinutagem) → double`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.treinador.internal;

class MotorDeConfiancaTest {

    @Test
    void devePunirForteIndiscutivelNoBancoComTreinadorSemLideranca() {
        var delta = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.INDISCUTIVEL, 0, 1));

        assertThat(delta).isCloseTo(-3.2, within(0.1));
    }

    @Test
    void deveAmortecerQuedaQuandoTreinadorTemLiderancaMaxima() {
        var delta = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.INDISCUTIVEL, 0, 10));

        assertThat(delta).isCloseTo(-1.4, within(0.1));
    }

    @Test
    void devePremiarPromessaQueJogouAPartidaInteira() {
        var delta = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.PROMESSA, 90, 1));

        assertThat(delta).isEqualTo(2.0);
    }

    @Test
    void naoDeveMexerNaMoralDeQuemFoiAvisadoQueNaoJoga() {
        var delta = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.FORA_DOS_PLANOS, 0, 1));

        assertThat(delta).isEqualTo(0.0);
    }

    @Test
    void naoDeveAmortecerDeltaPositivoComLideranca() {
        var comLideranca = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.PROMESSA, 60, 10));
        var semLideranca = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.PROMESSA, 60, 1));

        assertThat(comLideranca).isEqualTo(semLideranca);
    }

    @Test
    void deveCobrarDozePontosAoRebaixarIndiscutivelParaForaDosPlanos() {
        var delta = MotorDeConfianca.deltaPorMudancaDeStatus(
                StatusConfianca.INDISCUTIVEL, StatusConfianca.FORA_DOS_PLANOS);

        assertThat(delta).isEqualTo(-12.0);
    }

    @Test
    void devePagarDozePontosAoPromoverForaDosPlanosParaIndiscutivel() {
        var delta = MotorDeConfianca.deltaPorMudancaDeStatus(
                StatusConfianca.FORA_DOS_PLANOS, StatusConfianca.INDISCUTIVEL);

        assertThat(delta).isEqualTo(12.0);
    }

    @Test
    void devePunirMaisDoQuePremiarNaMesmaDistanciaDeMinutos() {
        var frustracao = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.INDISCUTIVEL, 0, 1));
        var satisfacao = MotorDeConfianca.delta(new FatoDeMinutagem(StatusConfianca.FORA_DOS_PLANOS, 85, 1));

        assertThat(Math.abs(frustracao)).isGreaterThan(Math.abs(satisfacao));
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*MotorDeConfiancaTest'`
Expected: FAIL — classes não existem.

- [ ] **Step 3: Adicionar constantes**

```java
    static final double MINUTOS_INCLINACAO = 0.04;
    static final double CONFIANCA_MIN = -4.0;
    static final double CONFIANCA_MAX = 2.0;
    static final double AMORTECIMENTO_POR_LIDERANCA = 0.06;
    static final double CUSTO_POR_DEGRAU_DE_STATUS = 3.0;
```

- [ ] **Step 4: Implementar**

```java
package br.com.api.footfirma.treinador.internal;

record FatoDeMinutagem(StatusConfianca status, int minutosRecebidos, int lideranca) {

    int diferenca() {
        return minutosRecebidos - status.minutosEsperados();
    }
}
```

```java
package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.*;

final class MotorDeConfianca {

    private MotorDeConfianca() {
    }

    static double delta(FatoDeMinutagem fato) {
        var bruto = clamp(fato.diferenca() * MINUTOS_INCLINACAO, CONFIANCA_MIN, CONFIANCA_MAX);
        return bruto < 0 ? bruto * amortecimento(fato.lideranca()) : bruto;
    }

    static double deltaPorMudancaDeStatus(StatusConfianca anterior, StatusConfianca novo) {
        return (novo.escala() - anterior.escala()) * CUSTO_POR_DEGRAU_DE_STATUS;
    }

    static double aplicar(double moralAtual, FatoDeMinutagem fato) {
        return clamp(moralAtual + delta(fato), MORAL_MIN, MORAL_MAX);
    }

    private static double amortecimento(int lideranca) {
        return 1 - lideranca * AMORTECIMENTO_POR_LIDERANCA;
    }
}
```

- [ ] **Step 5: Rodar o teste**

Run: `./gradlew test --tests '*MotorDeConfiancaTest'`
Expected: PASS nos oito testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador/internal \
        src/test/java/br/com/api/footfirma/treinador/internal
git commit -m "feat(treinador): adiciona motor de confiança por minutagem"
```

---

### Task 6: Eventos de domínio e OuvinteDePartida

**Files:**
- Create: `shared/evento/PartidaEncerrada.java`
- Create: `shared/evento/TemporadaEncerrada.java`
- Create: `treinador/{TreinadorDemitido,TreinadorSobPressao,VinculoEncerrado,JogadorInsatisfeito}.java`
- Create: `treinador/internal/OuvinteDePartida.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/OuvinteDePartidaTest.java`

**Interfaces:**
- Consumes: `MotorDeMoral` (Task 2), `PoliticaDeDemissao` (Task 4), `MotorDeConfianca` (Task 5), repositories (Task 1)
- Produces: `PartidaEncerrada`; `TemporadaEncerrada(long temporadaId, Instant ocorridoEm)`; os quatro eventos publicados; `OuvinteDePartida` como `@Component`

- [ ] **Step 1: Criar os records de evento**

```java
package br.com.api.footfirma.shared.evento;

/**
 * Publicado pelo módulo partida, que ainda não existe. Vive em shared (módulo OPEN)
 * porque o produtor não foi escrito; migra para partida quando ele existir.
 *
 * <p>As reputações viajam no evento em vez de serem consultadas no banco: sem isso,
 * reprocessar a campanha depois que a reputação do clube mudar devolveria uma moral
 * diferente da que aconteceu. Ver decisão 8 do spec.
 */
public record PartidaEncerrada(
        long edicaoId,
        long clubeMandanteId,
        long clubeVisitanteId,
        int golsMandante,
        int golsVisitante,
        int reputacaoMandante,
        int reputacaoVisitante,
        Map<Long, Integer> minutosPorJogador,
        Instant ocorridoEm) {
}
```

Os eventos publicados são records simples com identificadores, nomeados no passado:

```java
package br.com.api.footfirma.treinador;

public record TreinadorDemitido(long treinadorId, long clubeId, long vinculoId, double moralFinal) {
}
```

- [ ] **Step 2: Escrever o teste que falha**

```java
package br.com.api.footfirma.treinador;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OuvinteDePartidaTest {

    @Autowired OuvinteDePartida ouvinte;
    @Autowired VinculoTreinadorRepository vinculos;
    @Autowired TreinadorRepository treinadores;

    @Test
    void deveElevarMoralQuandoClubePequenoVenceGigante() {
        var vinculo = vinculos.save(TreinadorFactory.vinculoComMoral(
                treinadores.save(TreinadorFactory.humano("ana-souza")), 1L, 50.0));

        ouvinte.em(EventoFactory.vitoriaDoMandante(1L, 35, 2L, 88));

        assertThat(vinculos.findById(vinculo.getId()).orElseThrow().getMoral())
                .isCloseTo(55.9, within(0.2));
    }

    @Test
    void deveDemitirQuandoMoralCaiAbaixoDoLimiarAposACarencia() {
        var vinculo = vinculos.save(TreinadorFactory.vinculoComMoral(
                treinadores.save(TreinadorFactory.humano("bia-lima")), 1L, 12.0));
        TreinadorFactory.comPartidasJogadas(vinculo, 20);

        ouvinte.em(EventoFactory.derrotaDoMandante(1L, 88, 2L, 35));

        assertThat(vinculos.findById(vinculo.getId()).orElseThrow().getFim()).isNotNull();
        assertThat(vinculos.findById(vinculo.getId()).orElseThrow().getMotivoFim())
                .isEqualTo(MotivoFim.DEMISSAO);
    }

    @Test
    void deveIgnorarPartidaDeClubeSemTreinadorVinculado() {
        assertThatNoException()
                .isThrownBy(() -> ouvinte.em(EventoFactory.vitoriaDoMandante(99L, 50, 98L, 50)));
    }

    @Test
    void deveSerIdempotenteQuandoOMesmoEventoChegaDuasVezes() {
        var vinculo = vinculos.save(TreinadorFactory.vinculoComMoral(
                treinadores.save(TreinadorFactory.humano("caio-melo")), 1L, 50.0));
        var evento = EventoFactory.vitoriaDoMandante(1L, 50, 2L, 50);

        ouvinte.em(evento);
        var aposPrimeira = vinculos.findById(vinculo.getId()).orElseThrow().getMoral();
        ouvinte.em(evento);

        assertThat(vinculos.findById(vinculo.getId()).orElseThrow().getMoral())
                .isEqualTo(aposPrimeira);
    }
}
```

A idempotência não é opcional: `@ApplicationModuleListener` reenvia eventos não completados na reinicialização, e sem chave de deduplicação a moral seria aplicada duas vezes.

- [ ] **Step 3: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*OuvinteDePartidaTest'`
Expected: FAIL — `OuvinteDePartida` e `EventoFactory` não existem.

- [ ] **Step 4: Criar a tabela de deduplicação (migration `V18`)**

```sql
-- Chave de idempotência do listener. @ApplicationModuleListener reenvia eventos não
-- completados no restart; sem esta tabela a moral da partida seria aplicada de novo.
create table treinador_evento_processado (
    vinculo_id   bigint      not null references vinculo_treinador (id),
    chave_evento text        not null check (length(chave_evento) between 1 and 120),
    processado_em timestamptz not null default now(),
    primary key (vinculo_id, chave_evento)
);
```

- [ ] **Step 5: Implementar o ouvinte**

```java
package br.com.api.footfirma.treinador.internal;

@Component
@RequiredArgsConstructor
class OuvinteDePartida {

    private final VinculoTreinadorRepository vinculos;
    private final TreinadorJogadorRepository confiancas;
    private final EventoProcessadoRepository processados;
    private final ApplicationEventPublisher eventos;

    @ApplicationModuleListener
    void em(PartidaEncerrada evento) {
        aplicar(evento, evento.clubeMandanteId(), evento.reputacaoMandante(),
                evento.reputacaoVisitante(), resultadoDoMandante(evento));
        aplicar(evento, evento.clubeVisitanteId(), evento.reputacaoVisitante(),
                evento.reputacaoMandante(), resultadoDoMandante(evento).invertido());
    }

    private void aplicar(PartidaEncerrada evento, long clubeId, int minhaReputacao,
                         int reputacaoAdversario, Resultado resultado) {
        var vinculo = vinculos.findByClubeIdAndFimIsNull(clubeId).orElse(null);
        if (vinculo == null || jaProcessado(vinculo, evento)) {
            return;
        }

        var fato = new FatoDeResultado(minhaReputacao, reputacaoAdversario, resultado,
                vinculo.getMetaPosicao(), vinculo.getMetaPosicao());
        vinculo.setMoral(MotorDeMoral.aplicar(vinculo.getMoral(), fato));

        aplicarConfianca(vinculo, evento);
        avaliarDemissao(vinculo, minhaReputacao);
        marcarProcessado(vinculo, evento);
    }
    // ... avaliarDemissao publica TreinadorDemitido/TreinadorSobPressao conforme o Veredito
}
```

`posicaoAtual` recebe `metaPosicao` porque classificação não existe ainda — é o que mantém `fator_meta` em 1,0, como o spec previu. Quando a tabela existir, só esta linha muda.

- [ ] **Step 6: Rodar o teste**

Run: `./gradlew test --tests '*OuvinteDePartidaTest'`
Expected: PASS nos quatro testes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/api/footfirma/shared/evento \
        src/main/java/br/com/api/footfirma/treinador \
        src/main/resources/db/migration/V18__cria_evento_processado.sql \
        src/test/java/br/com/api/footfirma/treinador
git commit -m "feat(treinador): consome PartidaEncerrada e aplica moral e demissão"
```

---

### Task 7: Campanha simulada, reprocessamento e congelamento

**Files:**
- Test: `src/test/java/br/com/api/footfirma/treinador/CampanhaSimuladaTest.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/CongelamentoTest.java`
- Create: `src/test/java/br/com/api/footfirma/treinador/EventoFactory.java`

**Interfaces:**
- Consumes: `OuvinteDePartida` (Task 6)
- Produces: `EventoFactory.campanha(long clubeId, int reputacaoPropria, List<Resultado>) → List<PartidaEncerrada>`

Esta é a tarefa que prova o spec. Ela não adiciona produção — só testes — e por isso o reviewer deve rejeitá-la se os dois cenários não discordarem.

- [ ] **Step 1: Escrever o teste da campanha**

```java
package br.com.api.footfirma.treinador;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CampanhaSimuladaTest {

    /** 14 vitórias, 10 empates, 14 derrotas — campanha de meio de tabela. */
    private static final List<Resultado> CAMPANHA_MEDIANA = EventoFactory.campanhaMediana();

    @Test
    void deveManterTreinadorDoClubePequenoAposCampanhaMediana() {
        var vinculo = celeiro(metaPosicao = 16);

        CAMPANHA_MEDIANA.forEach(resultado -> ouvinte.em(EventoFactory.contra(vinculo, resultado, 60)));

        var apos = vinculos.findById(vinculo.getId()).orElseThrow();
        assertThat(apos.getFim()).isNull();
        assertThat(apos.getMoral()).isGreaterThan(18.5);
    }

    @Test
    void deveDemitirTreinadorDoGiganteAposAMesmaCampanha() {
        var vinculo = gigante(metaPosicao = 1);

        CAMPANHA_MEDIANA.forEach(resultado -> ouvinte.em(EventoFactory.contra(vinculo, resultado, 60)));

        assertThat(vinculos.findById(vinculo.getId()).orElseThrow().getFim()).isNotNull();
    }
}
```

- [ ] **Step 2: Rodar e observar qual dos dois falha**

Run: `./gradlew test --tests '*CampanhaSimuladaTest'`
Expected: FAIL. Se **ambos** passarem de primeira, desconfie: verifique que os dois cenários usam a mesma `CAMPANHA_MEDIANA` e reputações realmente distintas (35 contra 88).

- [ ] **Step 3: Ajustar `EventoFactory` até os dois cenários discordarem**

Não altere as fórmulas dos motores para fazer o teste passar — elas já estão cravadas pelos testes das Tasks 2 a 5. Se o gigante não for demitido, o problema está na campanha sintética (empates demais contra adversário fraco) ou na `metaPosicao` usada.

- [ ] **Step 4: Escrever o teste de congelamento**

```java
    @Test
    void deveDevolverAMoralOriginalAoReprocessarComReputacaoDoClubeAlterada() {
        var vinculo = celeiro(metaPosicao = 16);
        var eventos = EventoFactory.campanha(vinculo, CAMPANHA_MEDIANA, 60);
        eventos.forEach(ouvinte::em);
        var moralOriginal = vinculos.findById(vinculo.getId()).orElseThrow().getMoral();

        clubes.findById(1L).orElseThrow().setReputacao(85);   // o Celeiro virou gigante
        var recalculada = reprocessar(eventos);

        assertThat(recalculada).isEqualTo(moralOriginal);
    }
```

- [ ] **Step 5: Rodar os dois testes**

Run: `./gradlew test --tests '*CampanhaSimuladaTest' --tests '*CongelamentoTest'`
Expected: PASS. `CongelamentoTest` falhando significa que algum motor está lendo `clube.reputacao` em vez do evento — corrija o motor, não o teste.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/br/com/api/footfirma/treinador
git commit -m "test(treinador): prova régua relativa e determinismo da campanha"
```

---

### Task 8: Criação de treinador e distribuição de skills

**Files:**
- Create: `treinador/TreinadorService.java`
- Create: `treinador/dto/{NovoTreinador,DistribuicaoDeSkills,TreinadorResumo,TreinadorDetalhe}.java`
- Create: `treinador/internal/TreinadorServiceImpl.java`
- Create: `treinador/mapper/TreinadorMapper.java`
- Create: `shared/exception/DistribuicaoInvalidaException.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/SkillIntegridadeTest.java`

**Interfaces:**
- Consumes: repositories (Task 1)
- Produces: `TreinadorService.criar(NovoTreinador) → TreinadorResumo`; `TreinadorService.distribuirPontos(long treinadorId, long temporadaId, Map<Skill,Integer>) → TreinadorDetalhe`; constante `PONTOS_INICIAIS = 20`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.api.footfirma.treinador;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SkillIntegridadeTest {

    @Autowired TreinadorService treinadorService;

    @Test
    void deveCriarTreinadorQuandoSomaDasSkillsEhVinte() {
        var distribuicao = Map.of(
                Skill.VISAO_DE_JOGO, 10, Skill.PRELECAO, 5, Skill.LIDERANCA, 2,
                Skill.TREINAMENTO, 1, Skill.TATICA, 1, Skill.NEGOCIACAO, 1);

        var resumo = treinadorService.criar(new NovoTreinador("ana-souza", "Ana Souza", "Ana",
                LocalDate.of(1980, 1, 1), 1L, TipoTreinador.HUMANO, distribuicao));

        assertThat(resumo.slug()).isEqualTo("ana-souza");
    }

    @Test
    void deveRejeitarQuandoSomaDasSkillsPassaDeVinte() {
        var distribuicao = Map.of(
                Skill.VISAO_DE_JOGO, 10, Skill.PRELECAO, 10, Skill.LIDERANCA, 2,
                Skill.TREINAMENTO, 1, Skill.TATICA, 1, Skill.NEGOCIACAO, 1);

        assertThatThrownBy(() -> treinadorService.criar(novoCom(distribuicao)))
                .isInstanceOf(DistribuicaoInvalidaException.class)
                .hasMessageContaining("20");
    }

    @Test
    void deveRejeitarQuandoAlgumaSkillPassaDoTetoDez() {
        var distribuicao = Map.of(
                Skill.VISAO_DE_JOGO, 15, Skill.PRELECAO, 1, Skill.LIDERANCA, 1,
                Skill.TREINAMENTO, 1, Skill.TATICA, 1, Skill.NEGOCIACAO, 1);

        assertThatThrownBy(() -> treinadorService.criar(novoCom(distribuicao)))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }

    @Test
    void deveRejeitarQuandoFaltaAlgumaDasSeisSkills() {
        var distribuicao = Map.of(Skill.VISAO_DE_JOGO, 15, Skill.PRELECAO, 5);

        assertThatThrownBy(() -> treinadorService.criar(novoCom(distribuicao)))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }

    @Test
    void deveRejeitarDistribuicaoAcimaDosPontosDisponiveis() {
        var treinador = criarValido();

        assertThatThrownBy(() -> treinadorService.distribuirPontos(
                treinador.id(), 1L, Map.of(Skill.TATICA, 3)))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*SkillIntegridadeTest'`
Expected: FAIL — `TreinadorService` não existe.

- [ ] **Step 3: Implementar a validação no service**

```java
    private static final int PONTOS_INICIAIS = 20;
    private static final int TETO_POR_SKILL = 10;

    private void validar(Map<Skill, Integer> distribuicao, int pontosDisponiveis) {
        if (distribuicao.size() != Skill.values().length) {
            throw new DistribuicaoInvalidaException("as seis skills precisam ser informadas");
        }
        if (distribuicao.values().stream().anyMatch(valor -> valor < 1 || valor > TETO_POR_SKILL)) {
            throw new DistribuicaoInvalidaException("cada skill fica entre 1 e " + TETO_POR_SKILL);
        }
        var soma = distribuicao.values().stream().mapToInt(Integer::intValue).sum();
        if (soma != pontosDisponiveis) {
            throw new DistribuicaoInvalidaException(
                    "a soma das skills precisa ser " + pontosDisponiveis + ", veio " + soma);
        }
    }
```

Esta validação é a única guarda da invariante — `check` não enxerga outras linhas, e o spec registra isso como decisão consciente. O caminho de escrita precisa passar obrigatoriamente por aqui.

- [ ] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*SkillIntegridadeTest'`
Expected: PASS nos cinco testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador src/main/java/br/com/api/footfirma/shared \
        src/test/java/br/com/api/footfirma/treinador
git commit -m "feat(treinador): cria treinador com distribuição validada de skills"
```

---

### Task 9: Afinidade — consolidação e semeadura

**Files:**
- Create: `treinador/internal/MotorDeAfinidade.java`
- Modify: `treinador/internal/TreinadorServiceImpl.java` (semear ao iniciar vínculo)
- Test: `src/test/java/br/com/api/footfirma/treinador/AfinidadeTest.java`

**Interfaces:**
- Consumes: `CalculadoraDeExpectativa.moralInicialDoJogador` (Task 3)
- Produces: `MotorDeAfinidade.consolidar(double afinidadeAnterior, double moralFinal, int jogosJuntos) → double`

- [ ] **Step 1: Escrever o teste que falha**

```java
class AfinidadeTest {

    @Test
    void devePesarPoucoQuandoAPassagemFoiCurta() {
        var nova = MotorDeAfinidade.consolidar(50.0, 90.0, 3);

        assertThat(nova).isCloseTo(54.0, within(0.5));
    }

    @Test
    void devePesarMuitoQuandoAPassagemDurouTemporadas() {
        var nova = MotorDeAfinidade.consolidar(50.0, 90.0, 114);

        assertThat(nova).isCloseTo(78.0, within(0.5));
    }

    @Test
    void naoDeveApagarHistoricoAntigoNumUnicoAnoRuim() {
        var nova = MotorDeAfinidade.consolidar(90.0, 10.0, 500);

        assertThat(nova).isGreaterThan(30.0);
    }

    @Test
    void deveSemearMoralDoPupiloAoRecontratarEmOutroClube() {
        var treinador = criarValido();
        encerrarVinculoCom(treinador, jogadorId = 7L, afinidadeFinal = 90.0);

        var novoVinculo = treinadorService.iniciarVinculo(treinador.id(), clubeId = 2L, 1L);
        registrarNoElenco(novoVinculo, 7L);

        assertThat(confiancas.buscar(novoVinculo.id(), 7L).moral()).isCloseTo(74.0, within(0.5));
    }

    @Test
    void deveComecarEmCinquentaQuandoJogadorNuncaFoiDirigido() {
        var novoVinculo = treinadorService.iniciarVinculo(criarValido().id(), 2L, 1L);
        registrarNoElenco(novoVinculo, 99L);

        assertThat(confiancas.buscar(novoVinculo.id(), 99L).moral()).isCloseTo(50.0, within(0.5));
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*AfinidadeTest'`
Expected: FAIL — `MotorDeAfinidade` não existe.

- [ ] **Step 3: Adicionar constantes e implementar**

```java
    static final double AFINIDADE_JOGOS_DE_REFERENCIA = 60.0;
    static final double AFINIDADE_PESO_MIN = 0.10;
    static final double AFINIDADE_PESO_MAX = 0.70;
```

```java
package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.*;

final class MotorDeAfinidade {

    private MotorDeAfinidade() {
    }

    static double consolidar(double afinidadeAnterior, double moralFinal, int jogosJuntos) {
        var peso = clamp(jogosJuntos / AFINIDADE_JOGOS_DE_REFERENCIA,
                AFINIDADE_PESO_MIN, AFINIDADE_PESO_MAX);
        return afinidadeAnterior * (1 - peso) + moralFinal * peso;
    }
}
```

- [ ] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*AfinidadeTest'`
Expected: PASS nos cinco testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador \
        src/test/java/br/com/api/footfirma/treinador
git commit -m "feat(treinador): consolida afinidade e semeia moral do pupilo"
```

---

### Task 10: GeradorDePropostas e mercado

**Files:**
- Create: `treinador/internal/GeradorDePropostas.java`
- Create: `treinador/PropostaEnviada.java` · `treinador/dto/PropostaResumo.java`
- Modify: `treinador/TreinadorService.java` (aceitar/recusar proposta)
- Test: `src/test/java/br/com/api/footfirma/treinador/GeradorDePropostasTest.java`

**Interfaces:**
- Consumes: `PropostaRepository`, `VinculoTreinadorRepository` (Task 1)
- Produces: `GeradorDePropostas.candidatos(int reputacaoDoClube, List<Treinador> livres) → List<Treinador>`; `GeradorDePropostas.deveAssediar(int reputacaoInteressado, int reputacaoAtual, double moral, int reputacaoTreinador) → boolean`; `TreinadorService.aceitarProposta(long propostaId) → VinculoResumo`

- [ ] **Step 1: Escrever o teste que falha**

```java
class GeradorDePropostasTest {

    @Test
    void deveAceitarCandidatoDentroDaFaixaDeVinteECinco() {
        var candidatos = GeradorDePropostas.candidatos(60, List.of(
                comReputacao(40), comReputacao(60), comReputacao(80), comReputacao(20)));

        assertThat(candidatos).hasSize(3);
    }

    @Test
    void deveAssediarQuandoClubeEhMaiorETreinadorVaiBem() {
        assertThat(GeradorDePropostas.deveAssediar(80, 60, 70.0, 70)).isTrue();
    }

    @Test
    void naoDeveAssediarQuandoMoralEstaAbaixoDeSessenta() {
        assertThat(GeradorDePropostas.deveAssediar(80, 60, 45.0, 70)).isFalse();
    }

    @Test
    void naoDeveAssediarQuandoClubeNaoEhSuficientementeMaior() {
        assertThat(GeradorDePropostas.deveAssediar(65, 60, 70.0, 70)).isFalse();
    }

    @Test
    void naoDeveAssediarQuandoTreinadorEhPequenoDemaisParaOClube() {
        assertThat(GeradorDePropostas.deveAssediar(90, 60, 70.0, 55)).isFalse();
    }

    @Test
    void deveGerarTreinadorDeIaQuandoNaoHaCandidatoParaOClube() {
        var proposta = geradorDePropostas.preencherVaga(clubeId = 1L, reputacaoDoClube = 95,
                List.of(comReputacao(20)));

        assertThat(proposta.treinador().tipo()).isEqualTo(TipoTreinador.IA);
    }

    @Test
    void deveEncerrarVinculoAnteriorAoAceitarProposta() {
        var vinculoAntigo = vinculoAtivoCom(moral = 70.0, clubeId = 1L);
        var proposta = propostaDe(clubeId = 2L, paraOTreinadorDe(vinculoAntigo));

        treinadorService.aceitarProposta(proposta.id());

        assertThat(vinculos.findById(vinculoAntigo.getId()).orElseThrow().getMotivoFim())
                .isEqualTo(MotivoFim.ACEITOU_PROPOSTA);
    }
}
```

O último teste também é a prova de que os índices parciais únicos da Task 1 seguram: se o vínculo antigo não for encerrado antes de gravar o novo, o banco rejeita e o teste quebra com violação de constraint em vez de asserção.

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*GeradorDePropostasTest'`
Expected: FAIL — classe não existe.

- [ ] **Step 3: Adicionar constantes e implementar**

```java
    static final int FAIXA_DE_COMPATIBILIDADE = 25;
    static final int ASSEDIO_DIFERENCA_MINIMA = 10;
    static final double ASSEDIO_MORAL_MINIMA = 60.0;
    static final int ASSEDIO_MARGEM_DE_REPUTACAO = 20;
```

- [ ] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*GeradorDePropostasTest'`
Expected: PASS nos sete testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador \
        src/test/java/br/com/api/footfirma/treinador
git commit -m "feat(treinador): adiciona mercado de propostas e assédio"
```

---

### Task 11: OuvinteDeTemporada — evolução de skills e reputação

**Files:**
- Create: `treinador/internal/OuvinteDeTemporada.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/OuvinteDeTemporadaTest.java`

**Interfaces:**
- Consumes: `TemporadaEncerrada` (Task 6), repositories (Task 1), `MotorDeAfinidade` (Task 9)
- Produces: `OuvinteDeTemporada` como `@Component`

- [ ] **Step 1: Escrever o teste que falha**

```java
class OuvinteDeTemporadaTest {

    @Test
    void deveDarTresPontosQuandoTreinadorBateuAMeta() {
        var vinculo = vinculoCom(metaPosicao = 8, posicaoFinal = 4);

        ouvinte.em(new TemporadaEncerrada(1L, Instant.now()));

        assertThat(treinadores.findById(idDe(vinculo)).orElseThrow().getPontosDisponiveis())
                .isEqualTo(3);
    }

    @Test
    void deveDarDoisPontosQuandoNaoBateuAMetaMasSobreviveu() {
        var vinculo = vinculoCom(metaPosicao = 4, posicaoFinal = 9);

        ouvinte.em(new TemporadaEncerrada(1L, Instant.now()));

        assertThat(treinadores.findById(idDe(vinculo)).orElseThrow().getPontosDisponiveis())
                .isEqualTo(2);
    }

    @Test
    void deveDarUmPontoQuandoFoiDemitido() {
        var vinculo = vinculoDemitido(metaPosicao = 4);

        ouvinte.em(new TemporadaEncerrada(1L, Instant.now()));

        assertThat(treinadores.findById(idDe(vinculo)).orElseThrow().getPontosDisponiveis())
                .isEqualTo(1);
    }

    @Test
    void deveElevarReputacaoQuandoSuperouAMeta() {
        var vinculo = vinculoCom(metaPosicao = 16, posicaoFinal = 3);
        var antes = treinadores.findById(idDe(vinculo)).orElseThrow().getReputacao();

        ouvinte.em(new TemporadaEncerrada(1L, Instant.now()));

        assertThat(treinadores.findById(idDe(vinculo)).orElseThrow().getReputacao())
                .isGreaterThan(antes);
    }

    @Test
    void deveConsolidarAfinidadeDeTodoJogadorDoElencoAoEncerrarVinculo() {
        var vinculo = vinculoComElenco(jogadores = List.of(7L, 8L), moralFinal = 80.0);

        ouvinte.em(new TemporadaEncerrada(1L, Instant.now()));

        assertThat(afinidades.buscar(idDe(vinculo), 7L).afinidade()).isGreaterThan(50.0);
        assertThat(afinidades.buscar(idDe(vinculo), 8L).afinidade()).isGreaterThan(50.0);
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*OuvinteDeTemporadaTest'`
Expected: FAIL — classe não existe.

- [ ] **Step 3: Implementar**

Adicione as constantes `PONTOS_META_BATIDA = 3`, `PONTOS_SOBREVIVEU = 2`, `PONTOS_DEMITIDO = 1` e a fórmula de reputação (`(metaPosicao − posicaoFinal) × 0,8`, clamp `[−8, +10]`, arredondada para inteiro).

- [ ] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*OuvinteDeTemporadaTest'`
Expected: PASS nos cinco testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador \
        src/test/java/br/com/api/footfirma/treinador
git commit -m "feat(treinador): evolui skills e reputação ao fim da temporada"
```

---

### Task 12: API REST

**Files:**
- Create: `treinador/web/TreinadorController.java`
- Modify: `treinador/mapper/TreinadorMapper.java`
- Test: `src/test/java/br/com/api/footfirma/treinador/web/TreinadorControllerTest.java`

**Interfaces:**
- Consumes: `TreinadorService` (Task 8), DTOs
- Produces: `GET /api/v1/treinadores`, `GET /api/v1/treinadores/{slug}`, `POST /api/v1/treinadores`, `POST /api/v1/treinadores/{slug}/skills`, `GET /api/v1/treinadores/{slug}/propostas`, `POST /api/v1/propostas/{id}/aceitar`

- [ ] **Step 1: Escrever o teste que falha**

```java
@WebMvcTest(TreinadorController.class)
@Import(SecurityConfig.class)
class TreinadorControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TreinadorService treinadorService;

    @Test
    void deveRetornar422QuandoSomaDasSkillsNaoEhVinte() throws Exception {
        when(treinadorService.criar(any()))
                .thenThrow(new DistribuicaoInvalidaException("a soma das skills precisa ser 20"));

        mockMvc.perform(post("/api/v1/treinadores")
                        .contentType(APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deveRetornar404QuandoSlugNaoExiste() throws Exception {
        when(treinadorService.buscarPorSlug("inexistente")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/treinadores/inexistente"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400QuandoNomeEstaVazio() throws Exception {
        mockMvc.perform(post("/api/v1/treinadores")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"slug": "ana", "nomeCompleto": "", "nomeExibicao": "Ana"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
```

`@Import(SecurityConfig.class)` é obrigatório: sem ele `@WebMvcTest` não varre `@Configuration`, o default do Spring Security assume e toda rota responde 401. Não afrouxe a segurança para o teste passar.

- [ ] **Step 2: Rodar para confirmar que falha**

Run: `./gradlew test --tests '*TreinadorControllerTest'`
Expected: FAIL — controller não existe.

- [ ] **Step 3: Implementar o controller e o mapper**

Siga `clube/web/ClubeController.java`: `@RestController`, `@RequestMapping("/api/v1/treinadores")`, `@RequiredArgsConstructor`, `@Valid` nos corpos, `ResponseEntity` com o status certo, **sem** `@Transactional`. `DistribuicaoInvalidaException` vira 422 via `@ExceptionHandler` em `shared/exception`.

- [ ] **Step 4: Rodar o teste**

Run: `./gradlew test --tests '*TreinadorControllerTest'`
Expected: PASS nos três testes.

- [ ] **Step 5: Rodar a suíte completa**

Run: `./gradlew build`
Expected: PASS. Este é o momento de rodar tudo — as tarefas anteriores rodaram só o teste próprio.

- [ ] **Step 6: Percorrer o checklist do repositório**

Abra `.rules/java-checklist.md` e cumpra cada item antes de encerrar.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/api/footfirma/treinador \
        src/test/java/br/com/api/footfirma/treinador
git commit -m "feat(treinador): expõe API REST de treinador e propostas"
```

---

## Self-Review

**Cobertura do spec** — cada seção tem tarefa:

| Seção do spec | Tarefa |
|---|---|
| Schema `V17`, índices parciais | 1 |
| Decisão 7 (motor puro) | 2, 3, 4, 5, 9 |
| Decisão 8 (congelamento) | 6, 7 |
| Moral por partida, `fator_meta` | 2 |
| Meta da temporada, morais iniciais | 3 |
| Demissão, limiar, carência | 4 |
| Minutos esperados, mudança de status | 5 |
| Eventos, bordas, idempotência | 6 |
| `CampanhaSimuladaTest`, `CongelamentoTest` | 7 |
| Skills, criação, invariante da soma | 8 |
| Afinidade, semeadura do pupilo | 9 |
| Mercado, assédio, treinador de IA | 10 |
| Evolução de skills e reputação | 11 |
| API REST | 12 |

**Lacuna encontrada e corrigida durante a revisão:** o spec não previa deduplicação de
evento, mas `@ApplicationModuleListener` reenvia publicações não completadas no
restart — sem chave de idempotência, a moral de uma partida seria aplicada duas vezes
após qualquer reinício. A Task 6 ganhou a migration `V18` e o teste
`deveSerIdempotenteQuandoOMesmoEventoChegaDuasVezes`.

**Consistência de tipos** — `moral` é `double` em todo o caminho (motores, entidade,
asserções) contra `numeric(4,2)` na coluna; `reputacao` é `int`; `afinidade` é `double`.
`StatusConfianca.escala()` e `.minutosEsperados()` têm o mesmo nome na Task 1 (definição)
e nas Tasks 5 e 9 (uso).

**Ordem de dependência** — nenhuma tarefa consome tipo definido em tarefa posterior. As
Tasks 2 a 5 são independentes entre si e podem ser paralelizadas; 6 depende de 2, 4 e 5;
7 depende de 6; 9 depende de 3; 11 depende de 9.

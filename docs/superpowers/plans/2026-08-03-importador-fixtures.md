# Importador e Fixtures Fictícias — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tirar o catálogo do vazio — definir o formato do dataset, criar fixtures fictícias versionadas e escrever o importador Java que faz upsert por chave natural, registra o que aconteceu e materializa o overall no fim.

**Architecture:** Um módulo Spring Modulith novo (`importacao`), dono de três tabelas de auditoria. Ele **orquestra e não escreve em tabela alheia**: cada módulo de catálogo ganha métodos `sincronizar*` na sua interface pública, e o importador só os chama na ordem de dependência, resolvendo slug → id em mapas de memória. A carga é disparada por um `ApplicationRunner` sob profile `importacao`, lendo CSVs validados por manifesto com SHA-256.

**Tech Stack:** Java 25 · Spring Boot 4.1.0 · Spring Modulith 2.1.0 · PostgreSQL · Flyway · Lombok · Jackson (já no classpath via starter web) · JUnit 5 · AssertJ · Testcontainers

**Spec:** `docs/superpowers/specs/2026-08-01-catalogo-futebol-brasileiro-design.md`, seções "Pipeline de dados" e "Importador".

## Global Constraints

Todo trabalho acontece em `backend/footfirma/`. Os caminhos das tasks são relativos a esse diretório, salvo onde estiver escrito `(raiz)`. Estas regras valem para **todas** as tarefas:

- **Pacote base:** `br.com.api.footfirma`. Cada pacote diretamente abaixo dele é um módulo Modulith.
- **Nomes em português** — pacotes, tabelas, colunas, classes, métodos, testes, colunas de CSV.
- **Referência a outro módulo é coluna `Long` crua, nunca `@ManyToOne`.** Associação JPA só dentro do mesmo módulo. A integridade fica na FK da migration.
- **Estratégia de ID:** `bigint generated always as identity`, mapeado com `@GeneratedValue(strategy = GenerationType.IDENTITY)` e campo `Long`.
- **Tipos:** `text` com `check` de tamanho (nunca `varchar(n)`), `timestamptz` para data-hora, `date` para data pura, `numeric` para dinheiro, enum como `text` + `check` mapeado com `@Enumerated(EnumType.STRING)`.
- **Migrations:** `src/main/resources/db/migration/V{n}__descricao_snake_case.sql`, numeração sequencial sem buracos. **Migration aplicada nunca é editada** — o hook `.claude/hooks/guard.mjs` bloqueia. A próxima livre é **`V14`**.
- **Entidades JPA:** proibido `@Data`, `@EqualsAndHashCode`, `@ToString`. `equals`/`hashCode` escritos à mão pelo id. Construtor sem argumentos `protected`. Todo `@ManyToOne`/`@OneToOne` é `LAZY`.
- **Lombok permitido:** apenas `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`.
- **MapStruct:** `unmappedTargetPolicy=ERROR` está ativo. **Nunca** escreva `componentModel = "spring"`. Este plano não cria mapper novo: a conversão CSV → domínio parte de `String`, não de um tipo, e MapStruct não a cobre.
- **Visibilidade Modulith:** tipos no pacote raiz do módulo são API pública; `web/`, `domain/`, `repository/`, `mapper/`, `internal/` são invisíveis de fora. Um subpacote só cruza a fronteira com `@NamedInterface`. Entidade JPA e enum de `domain/` nunca cruzam fronteira.
- **DI só por construtor.** `@Autowired` em campo é proibido.
- **Transações:** `@Transactional(readOnly = true)` na classe do service, `@Transactional` no método que escreve. Nunca em controller.
- **Testes:** Testcontainers sempre, via `@Import(TestcontainersConfiguration.class)`. H2 e mock de repository são proibidos. `@DataJpaTest` exige `@AutoConfigureTestDatabase(replace = Replace.NONE)`. `@SpringBootTest` não faz rollback — limpe o estado em `@BeforeEach` com `JdbcTemplate`, como faz `MaterializacaoOverallTest`.
- **Pacotes de anotação do Boot 4** (snippet de Boot 3 não compila):
  - `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`
  - `@AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure`
  - `@SpringBootTest` → `org.springframework.boot.test.context`
  - `@MockitoBean` → `org.springframework.test.context.bean.override.mockito`
- **Nomes de teste:** `deve<Comportamento>Quando<Condição>`, em português. Corpo em três blocos separados por linha em branco: preparação, execução, verificação.
- **Labels de temporada em teste:** use a faixa `209x` para não colidir com fixtures (`2025`, `2026`) nem com testes existentes (`2041`).
- **Nenhuma dependência nova no `build.gradle`.** CSV é lido por parser próprio; JSON, pelo Jackson do starter web.
- **A API continua read-only.** Nenhum controller novo, nenhum `@PostMapping`. O importador é `ApplicationRunner`, não endpoint.
- **Commits:** Conventional Commits em português, escopo = módulo. Descrição no imperativo, minúscula, sem ponto final, ≤72 caracteres. **Nunca** adicione `Co-authored-by:`.
- **Não rode `./gradlew test` inteiro a cada passo** — use `--tests '*NomeDoTest'`. Não rode `bootRun`: o guard bloqueia, e quem dispara a carga é o usuário com `! <comando>`.

**Validação rápida durante o trabalho:** `./gradlew compileJava`

**Branch:** `feat/importador-fixtures`, criada a partir de `staging`.

---

## Escopo: o que este plano NÃO faz

O spec descreve o Plano 3 inteiro — pipeline Python em quatro estágios, extração de EA FC e Transfermarkt, matching entre fontes com `rapidfuzz` e `overrides.yaml`. **Nada disso entra aqui.**

Este plano executa apenas a metade que não depende de fonte externa: o **formato do dataset**, um dataset **fictício** que o cumpre, e o **importador** que o consome. Quando (e se) o pipeline Python nascer, ele emite no mesmo formato e o importador não muda uma linha.

Consequências deliberadas:

- Nenhum dado de fonte licenciada entra no repositório — as fixtures são inventadas, e o risco jurídico descrito em "Restrição legal" do spec não se materializa.
- `data-pipeline/` não é criado. Um diretório Python vazio seria promessa, não entrega.
- `attribute_mapping.yaml` e `club_aliases.yaml` não existem: sem fonte externa, não há 35 atributos para mapear em 18 nem nome de clube para desambiguar. As fixtures já nascem no vocabulário do domínio.
- As três tabelas `*_referencia_externa` continuam vazias. Elas existem para amarrar o catálogo a uma fonte; sem fonte, ficam sem uso — e isso é correto, não pendência.

---

## Decisões que carregam justificativa

Sete decisões deste plano não saem diretamente do spec. A implementação depende delas.

**1. O importador não escreve em tabela alheia — e isso custa superfície pública.**

O spec exige. O preço é concreto: ~15 métodos `sincronizar*` distribuídos por 5 interfaces, e um record `DadosDe*` por entidade.

A alternativa — importador com acesso direto aos repositories — economizaria esse código e destruiria o significado do `ModularidadeTest`: um módulo que escreve em todas as tabelas torna a verificação de fronteira decorativa. Paga-se a superfície.

**2. `sincronizar*` é upsert por chave natural e devolve o que fez.**

Assinatura uniforme: `ResultadoDeSincronizacao sincronizarX(DadosDeX dados)`, com `ResultadoDeSincronizacao(Long id, boolean criado)`.

O `id` volta porque o importador precisa dele para ligar as entidades seguintes — o CSV referencia por slug, o banco por id. O `criado` volta porque o relatório distingue inserção de atualização; sem isso não há como provar que a segunda execução não criou nada, que é exatamente o que a Task 12 testa.

**3. Ocorrência é falha detectada ANTES do banco; falha do banco derruba a etapa.**

A decisão mais consequente do plano.

Uma linha de `jogador_vinculo.csv` apontando para `clube_slug` inexistente é detectável em memória, antes de qualquer `INSERT`: o importador tem o mapa slug → id. Vira `importacao_ocorrencia` e a carga prossegue.

Já uma violação de constraint marca a transação como *rollback-only* no JPA. Capturar essa exception e continuar produziria uma transação zumbi cujo commit falha no fim — gravando nada e reportando sucesso. Por isso: **erro de banco aborta a etapa**, a execução termina com status `FALHOU`, e o motivo fica registrado.

O efeito é bom: dado ruim previsível vira relatório, dado ruim imprevisto vira falha ruidosa. O que não existe é falha silenciosa.

**4. Uma transação por etapa, não por linha nem por carga.**

Cada arquivo CSV é uma etapa, e cada etapa roda dentro de um `TransactionTemplate` no importador; os `sincronizar*` dos módulos participam com `REQUIRED`. Assim `jogador.csv` inteiro entra ou não entra.

Uma transação para a carga toda seguraria uma conexão por minutos e faria um erro em `regra_classificacao.csv` desfazer 176 jogadores. Uma por linha multiplicaria o custo por ~1.500 sem ganho: metade dos jogadores importados é estado inútil de qualquer forma.

**5. `estadio` ganha chave natural na V14.**

`V5__cria_clube.sql` criou `estadio` sem nenhuma constraint única. Sem chave natural não existe upsert idempotente — reimportar duplicaria todo estádio. A V14 acrescenta `uq_estadio_nome_cidade`, sem editar a V5.

**6. `chave_natural` e `semente` são derivadas, não vêm no CSV.**

O importador calcula:

```
chave_natural = normalizar(nome_completo) | data_nascimento | iso_pais
semente       = primeiros 8 bytes do SHA-256(chave_natural), sem sinal
```

São invariantes do sistema, não dados da fonte. Se viessem no arquivo, um dataset mal gerado daria sementes diferentes ao mesmo jogador entre execuções — e a semente é a origem de todo atributo oculto e de todo ruído de crescimento. Derivar remove a possibilidade.

`slug` **vem** no CSV: é identificador de API, escolha editorial, e precisa ser legível.

**7. As fixtures são geradas por código determinístico e versionadas como resultado.**

176 jogadores × 18 atributos não se escreve à mão. Um `GeradorDeFixtures` em `src/test/java`, com semente fixa, emite os 15 CSVs e o manifesto; o resultado é commitado.

O gerador **não** roda na suíte. O que a suíte verifica é outra coisa: que os CSVs versionados batem com os checksums do manifesto. Isso pega a fixture editada à mão sem regerar — a falha real — sem acoplar o teste ao gerador.

---

## Estrutura de arquivos

Cada arquivo tem uma responsabilidade. Os do módulo `importacao` são pequenos de propósito: o parser não conhece domínio, o validador não conhece banco, o orquestrador não conhece formato de arquivo.

```
backend/footfirma/
├── build.gradle                              # MODIFICAR: task gerarFixtures + systemProperty no test
├── fixtures/
│   ├── README.md                             # CRIAR: o formato do dataset, coluna por coluna
│   └── v1/                                   # CRIAR (gerado): 15 CSVs + manifest.json
│
├── src/main/resources/
│   ├── application.properties                # MODIFICAR: bloco do profile importacao
│   └── db/migration/V14__cria_importacao.sql # CRIAR: 3 tabelas + uq_estadio_nome_cidade
│
└── src/main/java/br/com/api/footfirma/
    ├── shared/dto/
    │   ├── package-info.java                 # CRIAR
    │   └── ResultadoDeSincronizacao.java     # CRIAR: contrato de todo sincronizar*
    │
    ├── temporada/
    │   ├── TemporadaService.java             # MODIFICAR: +sincronizar
    │   ├── dto/DadosDeTemporada.java         # CRIAR
    │   └── internal/TemporadaServiceImpl.java # MODIFICAR
    │
    ├── geografia/
    │   ├── GeografiaService.java             # MODIFICAR: +buscarEstadoPorUf
    │   ├── dto/package-info.java             # CRIAR: @NamedInterface("dto")
    │   ├── repository/EstadoRepository.java  # MODIFICAR: +buscarPorIsoEUf
    │   └── internal/GeografiaServiceImpl.java # MODIFICAR
    │
    ├── clube/
    │   ├── ClubeService.java                 # MODIFICAR: +3 sincronizar
    │   ├── dto/DadosDeEstadio.java           # CRIAR
    │   ├── dto/DadosDeClube.java             # CRIAR
    │   ├── dto/DadosDeAlias.java             # CRIAR
    │   ├── repository/EstadioRepository.java # MODIFICAR: +findByNomeAndCidade
    │   ├── repository/ClubeAliasRepository.java # MODIFICAR: +findByAliasAndFonte
    │   └── internal/ClubeServiceImpl.java    # MODIFICAR
    │
    ├── jogador/
    │   ├── JogadorService.java               # MODIFICAR: +6 sincronizar +2 busca
    │   ├── dto/DadosDeJogador.java           # CRIAR
    │   ├── dto/DadosDePosicaoSecundaria.java # CRIAR
    │   ├── dto/DadosDeAtributos.java         # CRIAR
    │   ├── dto/DadosDeAtributosOcultos.java  # CRIAR
    │   ├── dto/DadosDeCaracteristica.java    # CRIAR
    │   ├── dto/DadosDeVinculo.java           # CRIAR
    │   ├── repository/JogadorPosicaoRepository.java        # CRIAR
    │   ├── repository/JogadorCaracteristicaRepository.java # CRIAR
    │   ├── repository/JogadorVinculoRepository.java        # MODIFICAR
    │   └── internal/JogadorServiceImpl.java  # MODIFICAR
    │
    ├── competicao/
    │   ├── CompeticaoService.java            # MODIFICAR: +5 sincronizar +1 busca
    │   ├── dto/package-info.java             # CRIAR: @NamedInterface("dto")
    │   ├── dto/DadosDeCompeticao.java        # CRIAR
    │   ├── dto/DadosDeEdicao.java            # CRIAR
    │   ├── dto/DadosDeFase.java              # CRIAR
    │   ├── dto/DadosDeParticipante.java      # CRIAR
    │   ├── dto/DadosDeRegra.java             # CRIAR
    │   ├── repository/FaseRepository.java              # CRIAR
    │   ├── repository/EdicaoParticipanteRepository.java # CRIAR
    │   ├── repository/EdicaoRepository.java            # MODIFICAR
    │   ├── repository/RegraClassificacaoRepository.java # MODIFICAR
    │   └── internal/CompeticaoServiceImpl.java # MODIFICAR
    │
    └── importacao/                           # MÓDULO NOVO
        ├── package-info.java
        ├── ImportacaoService.java            # porta pública: importar(Path)
        ├── dto/
        │   ├── package-info.java             # @NamedInterface("dto")
        │   ├── RelatorioDeImportacao.java
        │   ├── ContagemDeEntidade.java
        │   └── OcorrenciaRegistrada.java
        ├── domain/
        │   ├── ImportacaoExecucao.java
        │   ├── ImportacaoContagem.java
        │   ├── ImportacaoOcorrencia.java
        │   ├── StatusImportacao.java         # EM_ANDAMENTO | CONCLUIDA | FALHOU
        │   └── SeveridadeOcorrencia.java     # AVISO | ERRO
        ├── repository/
        │   ├── ImportacaoExecucaoRepository.java
        │   ├── ImportacaoContagemRepository.java
        │   └── ImportacaoOcorrenciaRepository.java
        └── internal/
            ├── ChaveNatural.java             # normalização + semente — puro
            ├── LinhaDeCsv.java               # acesso tipado a uma linha — puro
            ├── LeitorDeCsv.java              # parser — puro
            ├── Manifesto.java                # record do manifest.json — puro
            ├── DatasetInvalidoException.java
            ├── ValidadorDeDataset.java       # checksums e cobertura — puro
            ├── ColecionadorDeOcorrencias.java # acumulador — puro
            ├── RegistroDeExecucao.java       # grava auditoria em REQUIRES_NEW
            ├── ImportacaoServiceImpl.java    # orquestra as 15 etapas
            ├── PropriedadesDeImportacao.java # @ConfigurationProperties
            └── ExecutorDeImportacao.java     # ApplicationRunner @Profile("importacao")

src/test/java/br/com/api/footfirma/
├── temporada/TemporadaServiceSincronizacaoTest.java
├── clube/ClubeServiceSincronizacaoTest.java
├── jogador/JogadorServiceSincronizacaoTest.java
├── competicao/CompeticaoServiceSincronizacaoTest.java
└── importacao/
    ├── ImportacaoSchemaTest.java             # @DataJpaTest — só schema
    ├── CopiaDeDataset.java                   # helper: copia e regrava manifesto
    ├── ImportacaoIdempotenciaTest.java       # @SpringBootTest — API pública
    ├── ImportacaoOcorrenciaTest.java         # @SpringBootTest — API pública
    └── internal/                             # o que toca classe package-private
        ├── ChaveNaturalTest.java             # puro
        ├── LeitorDeCsvTest.java              # puro + @TempDir
        ├── ValidadorDeDatasetTest.java       # puro + @TempDir
        ├── FixturesIntegridadeTest.java      # puro
        └── GeradorDeFixtures.java            # main(), roda por task Gradle
```

```
backend/footfirma/docs/
├── adr/2026-08-03-ingestao-pelos-modulos.md  # CRIAR
└── runbooks/importacao.md                    # CRIAR
```

**A divisão de `src/test` entre `importacao/` e `importacao/internal/` não é estética.** `ChaveNatural`, `LeitorDeCsv`, `Manifesto`, `ValidadorDeDataset` e `DatasetInvalidoException` são package-private; um teste fora do pacote não os enxerga. Já `ImportacaoService` e os DTOs são públicos, e testá-los de `importacao/` prova que a API do módulo funciona de fora — que é o ponto. `GeradorDeFixtures` fica em `internal` porque usa `ValidadorDeDataset.sha256`.

**Atenção aos dois `docs/`.** O repositório tem dois, e confundi-los espalha nota no lugar errado: `docs/` na **raiz** guarda specs e planos (`docs/superpowers/`); `backend/footfirma/docs/` guarda ADRs e runbooks do backend.

---

## O formato do dataset

Quinze CSVs e um manifesto, em `fixtures/v1/`. UTF-8, `LF`, separador `,`, aspas duplas quando o campo contém vírgula ou aspas (escapadas por duplicação). Cabeçalho obrigatório e validado. Campo vazio significa `null`.

Referências entre arquivos são **sempre por chave de negócio** — slug, label, código — nunca por id. O dataset não conhece os ids do banco, e é isso que o torna reimportável em base zerada.

| # | Arquivo | Colunas | Chave de upsert |
|---|---|---|---|
| 1 | `temporada.csv` | `label,ano_inicio,ano_fim` | `label` |
| 2 | `estadio.csv` | `chave,nome,cidade,uf,capacidade,ano_inauguracao` | `(nome, cidade)` |
| 3 | `clube.csv` | `slug,nome_oficial,nome_curto,apelido,ano_fundacao,iso_pais,uf,estadio_chave,cor_primaria,cor_secundaria,reputacao,qualidade_base,uf_base` | `slug` |
| 4 | `clube_alias.csv` | `clube_slug,alias,fonte` | `(alias, fonte)` |
| 5 | `competicao.csv` | `slug,nome,iso_pais,tipo,nivel,genero` | `slug` |
| 6 | `edicao.csv` | `competicao_slug,temporada,nome,data_inicio,data_fim` | `(competicao, temporada)` |
| 7 | `fase.csv` | `competicao_slug,temporada,ordem,nome,tipo,jogos_por_confronto,tem_gol_fora,tem_prorrogacao,tem_penaltis` | `(edicao, ordem)` |
| 8 | `edicao_participante.csv` | `competicao_slug,temporada,clube_slug,posicao_final` | `(edicao, clube)` |
| 9 | `regra_classificacao.csv` | `competicao_slug,temporada,posicao_inicio,posicao_fim,tipo,competicao_destino_slug` | `(edicao, faixa, tipo)` |
| 10 | `jogador.csv` | `slug,nome_completo,nome_exibicao,data_nascimento,iso_pais,iso_segunda_nacionalidade,altura_cm,peso_kg,pe_preferido,posicao_principal,origem` | `chave_natural` (derivada) |
| 11 | `jogador_posicao.csv` | `jogador_slug,posicao,ordem` | `(jogador, posicao)` |
| 12 | `jogador_atributo.csv` | `jogador_slug,temporada,` + 18 skills + `potencial_base,potencial_variacao,fonte_atributo,coletado_em` | `(jogador, temporada)` |
| 13 | `jogador_atributo_oculto.csv` | `jogador_slug,profissionalismo,ambicao,lealdade,temperamento,lideranca,regularidade,propensao_lesao,resistencia_pressao` | `jogador` |
| 14 | `jogador_caracteristica.csv` | `jogador_slug,caracteristica` | `(jogador, caracteristica)` |
| 15 | `jogador_vinculo.csv` | `jogador_slug,clube_slug,temporada,tipo,numero_camisa,data_inicio,data_fim,valor_mercado_eur` | `(jogador, temporada, clube)` |

As 18 skills, nesta ordem exata: `ritmo,forca,folego,salto,agilidade,passe,drible,cruzamento,frieza,finalizacao,cabeceio,falta,penalti,desarme,marcacao,gol_reflexo,gol_posicionamento,gol_manejo`.

`estadio.chave` existe só dentro do dataset, para `clube.estadio_chave` apontar. Não vira coluna no banco.

`iso_pais` e `uf` resolvem contra o seed de `V4__popula_geografia.sql`; `posicao` contra `V7__popula_posicao.sql`; `caracteristica` contra `V9__cria_caracteristica_e_vinculo.sql`. Valor ausente nesses três catálogos vira ocorrência, não erro — o dataset é que está fora do catálogo, e o catálogo é seed versionado.

### `manifest.json`

```json
{
  "schemaVersao": "1",
  "datasetVersao": "fixtures-v1",
  "geradoEm": "2026-08-03",
  "arquivos": [
    { "nome": "temporada.csv", "linhas": 2, "sha256": "9f86d081884c7d65..." }
  ]
}
```

Quatro regras de validação, todas **antes** de qualquer escrita, nesta ordem:

1. `schemaVersao` é exatamente `"1"` — as demais checagens não fazem sentido em schema desconhecido.
2. Todo arquivo listado existe e seu SHA-256 confere.
3. Toda contagem de linhas confere (sem contar o cabeçalho).
4. Todo `.csv` presente no diretório está listado no manifesto — sem essa regra, um arquivo esquecido pelo gerador seria silenciosamente ignorado.

---

## As fixtures

Fictícias por decisão, não por conveniência. Nenhum nome de clube, jogador ou competição corresponde a entidade real; a escolha remove o problema de licenciamento descrito no spec e torna o dataset publicável junto do código.

**Volume:** 2 temporadas (2025, 2026) · 3 competições · 8 clubes · 8 estádios · 176 jogadores (22 por clube) · 352 linhas de atributo · 352 vínculos.

**Os 8 clubes** (inventados):

| slug | nome curto | UF | reputação | qualidade base |
|---|---|---|---|---|
| `atletico-serrano` | Serrano | MG | 78 | 72 |
| `guarani-portuario` | Portuário | SP | 74 | 68 |
| `sociedade-ipanema` | Ipanema | RJ | 71 | 65 |
| `esporte-clube-varzea` | Várzea | RS | 68 | 70 |
| `nacional-do-cerrado` | Cerrado | GO | 64 | 58 |
| `uniao-litoranea` | Litorânea | SC | 61 | 62 |
| `real-sertanejo` | Sertanejo | BA | 57 | 55 |
| `avante-fluvial` | Fluvial | PA | 52 | 50 |

**As 3 competições:**

- `serie-ouro` — `LIGA`, nível 1. Edição em 2025 e 2026, os 8 clubes, `posicao_final` 1–8, uma fase `PONTOS_CORRIDOS`. Regras: 1–4 `LIBERTADORES_GRUPOS`, 5–6 `SULAMERICANA`, 7–8 `REBAIXAMENTO` com destino `serie-prata`.
- `serie-prata` — `LIGA`, nível 2. **Sem edição**, de propósito: existe como destino de rebaixamento e prova que `regra_classificacao.competicao_destino_id` aponta para competição sem edição carregada.
- `copa-nacional` — `COPA`, sem nível. Edição em 2026 com os 8 clubes e três fases `ELIMINATORIA`: quartas e semifinal com `jogos_por_confronto = 2`, `tem_prorrogacao = true`, `tem_penaltis = true`; final com `jogos_por_confronto = 1`.

Os dois formatos do modelo — pontos corridos e mata-mata — ficam exercitados, que é a razão de a Copa do Brasil estar na carga inicial no spec.

**Os jogadores.** 22 por clube, cobrindo as 9 posições (3 GOL, 4 ZAG, 2 LTD, 2 LTE, 3 VOL, 2 MEC, 2 MEA, 2 PTA, 2 ATA). Nomes montados por combinação determinística de pools de prenomes e sobrenomes comuns no Brasil — soam brasileiros sem corresponder a ninguém. Idades entre 17 e 38.

Atributos correlacionados com `clube.reputacao` e com a posição: o goleiro do Serrano tem `gol_reflexo` alto e `finalizacao` baixa; o atacante do Fluvial é pior que o do Serrano. Isso importa porque o ranking de overall precisa produzir ordem com significado, não ruído uniforme.

**Entre 2025 e 2026:** atributos evoluem por regra determinística ligada à idade (jovem sobe, veterano cai), e **3 jogadores trocam de clube**. Um jogador tem vínculo `EMPRESTIMO`.

**A fixture versionada é íntegra.** O teste de ocorrência (Task 12) monta em `@TempDir` uma cópia com defeito e regenera o manifesto. Um dataset de referência com erro embutido seria armadilha para quem o usa como exemplo.

---

## Task 1: Contrato de sincronização e abertura das fronteiras de `dto`

O tipo devolvido por todos os `sincronizar*` precisa existir antes deles, e dois módulos ainda não abriram seu `dto/` — `competicao` e `geografia`. Sem `@NamedInterface`, o `ModularidadeTest` reprova `importacao` no primeiro build, antes de qualquer linha do módulo novo existir.

Não há teste próprio: `ResultadoDeSincronizacao` é um record sem comportamento, e um teste de construtor não prova nada. A verificação desta task é o `ModularidadeTest`, que já existe e passa a cobrir os pacotes novos.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/shared/dto/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/shared/dto/ResultadoDeSincronizacao.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/geografia/dto/package-info.java`

**Interfaces:**
- Consumes: nada.
- Produces: `br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao(Long id, boolean criado)`, com os factories estáticos `criado(Long id)` e `atualizado(Long id)`. Todas as tasks 2–5 devolvem esse tipo; a Task 10 lê `criado()` para montar as contagens.

- [ ] **Step 1: Criar `ResultadoDeSincronizacao`**

Crie `src/main/java/br/com/api/footfirma/shared/dto/ResultadoDeSincronizacao.java`:

```java
package br.com.api.footfirma.shared.dto;

/**
 * Devolvido por todo método {@code sincronizar*} de módulo de catálogo.
 *
 * <p>O {@code id} volta porque o importador liga as entidades seguintes por chave
 * estrangeira, e o dataset só conhece slug. O {@code criado} volta porque o
 * relatório distingue inserção de atualização — sem isso não há como provar que
 * uma segunda importação do mesmo dataset não criou nada.
 */
public record ResultadoDeSincronizacao(Long id, boolean criado) {

    public static ResultadoDeSincronizacao criado(Long id) {
        return new ResultadoDeSincronizacao(id, true);
    }

    public static ResultadoDeSincronizacao atualizado(Long id) {
        return new ResultadoDeSincronizacao(id, false);
    }
}
```

- [ ] **Step 2: Criar o `package-info.java` de `shared/dto`**

Crie `src/main/java/br/com/api/footfirma/shared/dto/package-info.java`:

```java
// shared é um módulo OPEN, então este subpacote já seria visível sem a anotação.
// Ela entra assim mesmo para declarar a intenção: no dia em que shared virar um
// módulo fechado, cinco módulos quebrariam de uma vez sem esta linha.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.shared.dto;
```

- [ ] **Step 3: Criar o `package-info.java` de `competicao/dto`**

Crie `src/main/java/br/com/api/footfirma/competicao/dto/package-info.java`:

```java
// Os records deste pacote são parte do contrato público do módulo: CompeticaoService
// os devolve e os recebe, e importacao os consome ao carregar o dataset. Sem
// @NamedInterface o Modulith trata um subpacote como interno e reprova a dependência.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.competicao.dto;
```

- [ ] **Step 4: Criar o `package-info.java` de `geografia/dto`**

Crie `src/main/java/br/com/api/footfirma/geografia/dto/package-info.java`:

```java
// GeografiaService devolve estes records, e importacao os consome para traduzir
// iso_pais e uf em id antes de chamar os demais módulos. Sem @NamedInterface o
// Modulith trata um subpacote como interno e reprova a dependência.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.geografia.dto;
```

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Rodar o teste de modularidade**

Run: `./gradlew test --tests '*ModularidadeTest'`
Expected: PASS — os dois testes verdes

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/api/footfirma/shared/dto \
        src/main/java/br/com/api/footfirma/competicao/dto/package-info.java \
        src/main/java/br/com/api/footfirma/geografia/dto/package-info.java
git commit -m "feat(shared): adiciona contrato ResultadoDeSincronizacao"
```

---

## Task 2: Ingestão em `temporada` e resolução de UF em `geografia`

Temporada é a primeira etapa da ordem de dependência e a mais simples — serve de referência de formato para as três tasks seguintes. `geografia` não ganha escrita: país e estado são seed, e o importador só precisa traduzir `uf` → `estadoId`.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/temporada/dto/DadosDeTemporada.java`
- Modify: `src/main/java/br/com/api/footfirma/temporada/TemporadaService.java`
- Modify: `src/main/java/br/com/api/footfirma/temporada/internal/TemporadaServiceImpl.java`
- Modify: `src/main/java/br/com/api/footfirma/geografia/GeografiaService.java`
- Modify: `src/main/java/br/com/api/footfirma/geografia/internal/GeografiaServiceImpl.java`
- Modify: `src/main/java/br/com/api/footfirma/geografia/repository/EstadoRepository.java`
- Test: `src/test/java/br/com/api/footfirma/temporada/TemporadaServiceSincronizacaoTest.java`

**Interfaces:**
- Consumes: `ResultadoDeSincronizacao` (Task 1); `TemporadaRepository.findByLabel(String)` e `GeografiaMapper.paraResumo(Estado)`, ambos já existentes.
- Produces:
  - `TemporadaService.sincronizar(DadosDeTemporada) → ResultadoDeSincronizacao`
  - `DadosDeTemporada(String label, Integer anoInicio, Integer anoFim)`
  - `GeografiaService.buscarEstadoPorUf(String isoPais, String uf) → Optional<EstadoResumo>`, onde `EstadoResumo(Long id, String uf, String nome)` já existe.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/temporada/TemporadaServiceSincronizacaoTest.java`:

```java
package br.com.api.footfirma.temporada;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TemporadaServiceSincronizacaoTest {

    @Autowired
    TemporadaService temporadaService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparTemporadasDeTeste() {
        jdbcTemplate.update("delete from temporada where label like '209%'");
    }

    @Test
    void deveCriarTemporadaQuandoLabelNaoExiste() {
        var dados = new DadosDeTemporada("2091", 2091, 2091);

        var resultado = temporadaService.sincronizar(dados);

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveDevolverOMesmoIdQuandoLabelJaExiste() {
        var primeiro = temporadaService.sincronizar(new DadosDeTemporada("2092", 2092, 2092));

        var segundo = temporadaService.sincronizar(new DadosDeTemporada("2092", 2092, 2093));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveGravarOsAnosAtualizadosQuandoTemporadaJaExiste() {
        temporadaService.sincronizar(new DadosDeTemporada("2093", 2093, 2093));

        temporadaService.sincronizar(new DadosDeTemporada("2093", 2093, 2094));

        var anoFim = jdbcTemplate.queryForObject(
                "select ano_fim from temporada where label = '2093'", Integer.class);
        assertThat(anoFim).isEqualTo(2094);
    }

    @Test
    void deveManterUmaUnicaLinhaQuandoSincronizaDuasVezes() {
        temporadaService.sincronizar(new DadosDeTemporada("2094", 2094, 2094));
        temporadaService.sincronizar(new DadosDeTemporada("2094", 2094, 2094));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from temporada where label = '2094'", Integer.class);
        assertThat(linhas).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*TemporadaServiceSincronizacaoTest'`
Expected: FAIL na compilação — `DadosDeTemporada` não existe e `TemporadaService` não tem `sincronizar`

- [ ] **Step 3: Criar `DadosDeTemporada`**

Crie `src/main/java/br/com/api/footfirma/temporada/dto/DadosDeTemporada.java`:

```java
package br.com.api.footfirma.temporada.dto;

/** Entrada de ingestão do importador. Espelha uma linha de {@code temporada.csv}. */
public record DadosDeTemporada(String label, Integer anoInicio, Integer anoFim) {
}
```

- [ ] **Step 4: Declarar `sincronizar` na interface**

Em `src/main/java/br/com/api/footfirma/temporada/TemporadaService.java`, acrescente os imports de `DadosDeTemporada` e `ResultadoDeSincronizacao` e o método:

```java
    /**
     * Upsert por {@code label}. Chamado apenas pelo importador — a API REST do
     * catálogo é read-only por decisão registrada em
     * {@code docs/adr/2026-08-01-catalogo-read-only.md}.
     */
    ResultadoDeSincronizacao sincronizar(DadosDeTemporada dados);
```

- [ ] **Step 5: Implementar em `TemporadaServiceImpl`**

Em `src/main/java/br/com/api/footfirma/temporada/internal/TemporadaServiceImpl.java`, acrescente:

```java
    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizar(DadosDeTemporada dados) {
        var existente = temporadaRepository.findByLabel(dados.label());
        if (existente.isPresent()) {
            var temporada = existente.get();
            temporada.setAnoInicio(dados.anoInicio());
            temporada.setAnoFim(dados.anoFim());
            return ResultadoDeSincronizacao.atualizado(temporada.getId());
        }
        var nova = temporadaRepository.save(
                new Temporada(dados.label(), dados.anoInicio(), dados.anoFim()));
        return ResultadoDeSincronizacao.criado(nova.getId());
    }
```

Imports a acrescentar: `br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao`, `br.com.api.footfirma.temporada.dto.DadosDeTemporada`, `br.com.api.footfirma.temporada.domain.Temporada`.

- [ ] **Step 6: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*TemporadaServiceSincronizacaoTest'`
Expected: PASS — 4 testes verdes

- [ ] **Step 7: Acrescentar a query de UF em `EstadoRepository`**

Em `src/main/java/br/com/api/footfirma/geografia/repository/EstadoRepository.java`, acrescente:

```java
    @Query("select e from Estado e join fetch e.pais p where p.isoCode = :isoCode and e.uf = :uf")
    Optional<Estado> buscarPorIsoEUf(@Param("isoCode") String isoCode, @Param("uf") String uf);
```

Import a acrescentar: `java.util.Optional`.

- [ ] **Step 8: Declarar e implementar `buscarEstadoPorUf`**

Em `src/main/java/br/com/api/footfirma/geografia/GeografiaService.java`:

```java
    /**
     * Traduz {@code uf} em id de estado. O importador precisa disso porque o
     * dataset referencia estado por sigla, e as tabelas por chave estrangeira.
     */
    Optional<EstadoResumo> buscarEstadoPorUf(String isoPais, String uf);
```

Em `src/main/java/br/com/api/footfirma/geografia/internal/GeografiaServiceImpl.java`:

```java
    @Override
    public Optional<EstadoResumo> buscarEstadoPorUf(String isoPais, String uf) {
        return estadoRepository.buscarPorIsoEUf(isoPais, uf).map(geografiaMapper::paraResumo);
    }
```

- [ ] **Step 9: Compilar e rodar os testes de geografia existentes**

Run: `./gradlew test --tests '*GeografiaRepositoryTest' --tests '*TemporadaServiceSincronizacaoTest'`
Expected: PASS — nenhuma regressão em geografia

- [ ] **Step 10: Commit**

```bash
git add src/main/java/br/com/api/footfirma/temporada \
        src/main/java/br/com/api/footfirma/geografia \
        src/test/java/br/com/api/footfirma/temporada
git commit -m "feat(temporada): adiciona upsert por label e busca de estado por UF"
```

---

## Task 3: Ingestão em `clube` — estádio, clube e alias

Três upserts. O de estádio depende de `uq_estadio_nome_cidade`, criada na Task 6; aqui a busca é por repository, e o teste passa mesmo antes da constraint existir. As duas tasks só precisam estar no mesmo merge, não na mesma ordem.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/clube/dto/DadosDeEstadio.java`
- Create: `src/main/java/br/com/api/footfirma/clube/dto/DadosDeClube.java`
- Create: `src/main/java/br/com/api/footfirma/clube/dto/DadosDeAlias.java`
- Modify: `src/main/java/br/com/api/footfirma/clube/ClubeService.java`
- Modify: `src/main/java/br/com/api/footfirma/clube/internal/ClubeServiceImpl.java`
- Modify: `src/main/java/br/com/api/footfirma/clube/repository/EstadioRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/clube/repository/ClubeAliasRepository.java`
- Test: `src/test/java/br/com/api/footfirma/clube/ClubeServiceSincronizacaoTest.java`

**Interfaces:**
- Consumes: `ResultadoDeSincronizacao` (Task 1); `ClubeRepository.findBySlug(String)`, já existente.
- Produces:
  - `ClubeService.sincronizarEstadio(DadosDeEstadio) → ResultadoDeSincronizacao`
  - `ClubeService.sincronizarClube(DadosDeClube) → ResultadoDeSincronizacao`
  - `ClubeService.sincronizarAlias(DadosDeAlias) → ResultadoDeSincronizacao`
  - `DadosDeEstadio(String nome, String cidade, Long estadoId, Integer capacidade, Integer anoInauguracao)`
  - `DadosDeClube(String slug, String nomeOficial, String nomeCurto, String apelido, Integer anoFundacao, Long paisId, Long estadoId, Long estadioId, String corPrimaria, String corSecundaria, Integer reputacao, Integer qualidadeBase, Long estadoBaseId)`
  - `DadosDeAlias(Long clubeId, String alias, String fonte)`

Os records recebem **ids já resolvidos**, não `iso_pais`/`uf`/`estadio_chave`. A tradução chave-de-negócio → id é do importador, que tem os mapas; empurrá-la para dentro de `clube` faria o módulo depender de `geografia` para escrever e obrigaria cada módulo a repetir a mesma resolução.

`fonte` trafega como `String` porque `FonteExterna` vive em `clube/domain/`, que é interno — enum de domínio não cruza fronteira de módulo.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/clube/ClubeServiceSincronizacaoTest.java`:

```java
package br.com.api.footfirma.clube;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.clube.dto.DadosDeAlias;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClubeServiceSincronizacaoTest {

    @Autowired
    ClubeService clubeService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long paisId;

    @BeforeEach
    void limparClubesDeTeste() {
        jdbcTemplate.update("delete from clube_alias where alias like 'sinc-%'");
        jdbcTemplate.update("delete from clube where slug like 'sinc-%'");
        jdbcTemplate.update("delete from estadio where nome like 'Estádio Sinc%'");
        paisId = jdbcTemplate.queryForObject(
                "select id from pais where iso_code = 'BRA'", Long.class);
    }

    @Test
    void deveCriarClubeQuandoSlugNaoExiste() {
        var resultado = clubeService.sincronizarClube(clube("sinc-alfa", "Alfa", 70));

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveDevolverOMesmoIdQuandoSlugJaExiste() {
        var primeiro = clubeService.sincronizarClube(clube("sinc-beta", "Beta", 70));

        var segundo = clubeService.sincronizarClube(clube("sinc-beta", "Beta", 80));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveGravarAReputacaoAtualizadaQuandoClubeJaExiste() {
        clubeService.sincronizarClube(clube("sinc-gama", "Gama", 70));

        clubeService.sincronizarClube(clube("sinc-gama", "Gama", 85));

        var reputacao = jdbcTemplate.queryForObject(
                "select reputacao from clube where slug = 'sinc-gama'", Integer.class);
        assertThat(reputacao).isEqualTo(85);
    }

    @Test
    void deveManterUmaUnicaLinhaQuandoSincronizaClubeDuasVezes() {
        clubeService.sincronizarClube(clube("sinc-delta", "Delta", 70));
        clubeService.sincronizarClube(clube("sinc-delta", "Delta", 70));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from clube where slug = 'sinc-delta'", Integer.class);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveReaproveitarEstadioQuandoNomeECidadeCoincidem() {
        var primeiro = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Um", "Belo Horizonte", null, 40000, 1965));

        var segundo = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Um", "Belo Horizonte", null, 45000, 1965));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveCriarEstadiosDistintosQuandoCidadeDifere() {
        var mineiro = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Dois", "Salvador", null, 30000, 1970));

        var paulista = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Dois", "Santos", null, 30000, 1970));

        assertThat(paulista.criado()).isTrue();
        assertThat(paulista.id()).isNotEqualTo(mineiro.id());
    }

    @Test
    void deveReaproveitarAliasQuandoAliasEFonteCoincidem() {
        var clubeId = clubeService.sincronizarClube(clube("sinc-epsilon", "Epsilon", 70)).id();
        var primeiro = clubeService.sincronizarAlias(
                new DadosDeAlias(clubeId, "sinc-epsilon-apelido", "MANUAL"));

        var segundo = clubeService.sincronizarAlias(
                new DadosDeAlias(clubeId, "sinc-epsilon-apelido", "MANUAL"));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveLigarOEstadioAoClubeQuandoInformado() {
        var estadioId = clubeService.sincronizarEstadio(
                new DadosDeEstadio("Estádio Sinc Três", "Curitiba", null, 25000, 1980)).id();
        var dados = new DadosDeClube("sinc-zeta", "Zeta Futebol Clube", "Zeta", null,
                1912, paisId, null, estadioId, "#FF0000", "#FFFFFF", 70, 60, null);

        clubeService.sincronizarClube(dados);

        var gravado = jdbcTemplate.queryForObject(
                "select estadio_id from clube where slug = 'sinc-zeta'", Long.class);
        assertThat(gravado).isEqualTo(estadioId);
    }

    private DadosDeClube clube(String slug, String nomeCurto, int reputacao) {
        return new DadosDeClube(slug, nomeCurto + " Futebol Clube", nomeCurto, null,
                1900, paisId, null, null, "#0000FF", "#FFFFFF", reputacao, 60, null);
    }
}
```

- [ ] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*ClubeServiceSincronizacaoTest'`
Expected: FAIL na compilação — os três records e os três métodos não existem

- [ ] **Step 3: Criar os três records**

Crie `src/main/java/br/com/api/footfirma/clube/dto/DadosDeEstadio.java`:

```java
package br.com.api.footfirma.clube.dto;

/** Entrada de ingestão. Espelha uma linha de {@code estadio.csv}, com a UF já resolvida em id. */
public record DadosDeEstadio(String nome, String cidade, Long estadoId,
                             Integer capacidade, Integer anoInauguracao) {
}
```

Crie `src/main/java/br/com/api/footfirma/clube/dto/DadosDeClube.java`:

```java
package br.com.api.footfirma.clube.dto;

/**
 * Entrada de ingestão. Espelha uma linha de {@code clube.csv}, com país, estado e
 * estádio já resolvidos em id pelo importador — resolver aqui faria o módulo clube
 * depender de geografia para escrever.
 */
public record DadosDeClube(String slug, String nomeOficial, String nomeCurto, String apelido,
                           Integer anoFundacao, Long paisId, Long estadoId, Long estadioId,
                           String corPrimaria, String corSecundaria,
                           Integer reputacao, Integer qualidadeBase, Long estadoBaseId) {
}
```

Crie `src/main/java/br/com/api/footfirma/clube/dto/DadosDeAlias.java`:

```java
package br.com.api.footfirma.clube.dto;

/**
 * Entrada de ingestão. {@code fonte} é String e não o enum FonteExterna: o enum
 * vive em clube/domain, que é interno, e tipo de domínio não cruza fronteira de módulo.
 */
public record DadosDeAlias(Long clubeId, String alias, String fonte) {
}
```

- [ ] **Step 4: Acrescentar as buscas nos repositories**

Em `src/main/java/br/com/api/footfirma/clube/repository/EstadioRepository.java`:

```java
    Optional<Estadio> findByNomeAndCidade(String nome, String cidade);
```

Import a acrescentar: `java.util.Optional`.

Em `src/main/java/br/com/api/footfirma/clube/repository/ClubeAliasRepository.java`:

```java
    Optional<ClubeAlias> findByAliasAndFonte(String alias, FonteExterna fonte);
```

Import a acrescentar: `br.com.api.footfirma.clube.domain.FonteExterna`.

- [ ] **Step 5: Declarar os três métodos na interface**

Em `src/main/java/br/com/api/footfirma/clube/ClubeService.java`:

```java
    /** Upsert por {@code (nome, cidade)} — a chave natural criada em V14. */
    ResultadoDeSincronizacao sincronizarEstadio(DadosDeEstadio dados);

    /** Upsert por {@code slug}. */
    ResultadoDeSincronizacao sincronizarClube(DadosDeClube dados);

    /** Upsert por {@code (alias, fonte)}. */
    ResultadoDeSincronizacao sincronizarAlias(DadosDeAlias dados);
```

- [ ] **Step 6: Implementar em `ClubeServiceImpl`**

Em `src/main/java/br/com/api/footfirma/clube/internal/ClubeServiceImpl.java`, acrescente `EstadioRepository` ao construtor (via campo `final`, o `@RequiredArgsConstructor` cuida do resto) e os três métodos:

```java
    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarEstadio(DadosDeEstadio dados) {
        var existente = estadioRepository.findByNomeAndCidade(dados.nome(), dados.cidade());
        var estadio = existente.orElseGet(() -> new Estadio(dados.nome(), dados.cidade()));
        estadio.setEstadoId(dados.estadoId());
        estadio.setCapacidade(dados.capacidade());
        estadio.setAnoInauguracao(dados.anoInauguracao());
        var salvo = estadioRepository.save(estadio);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarClube(DadosDeClube dados) {
        var existente = clubeRepository.findBySlug(dados.slug());
        var clube = existente.orElseGet(() -> new Clube(
                dados.slug(), dados.nomeOficial(), dados.nomeCurto(), dados.paisId()));
        clube.setNomeOficial(dados.nomeOficial());
        clube.setNomeCurto(dados.nomeCurto());
        clube.setApelido(dados.apelido());
        clube.setAnoFundacao(dados.anoFundacao());
        clube.setPaisId(dados.paisId());
        clube.setEstadoId(dados.estadoId());
        clube.setEstadio(dados.estadioId() == null
                ? null
                : estadioRepository.getReferenceById(dados.estadioId()));
        clube.setCorPrimaria(dados.corPrimaria());
        clube.setCorSecundaria(dados.corSecundaria());
        clube.setReputacao(dados.reputacao());
        clube.setQualidadeBase(dados.qualidadeBase());
        clube.setEstadoBaseId(dados.estadoBaseId());
        if (existente.isPresent()) {
            clube.setAtualizadoEm(OffsetDateTime.now());
        }
        var salvo = clubeRepository.save(clube);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarAlias(DadosDeAlias dados) {
        var fonte = FonteExterna.valueOf(dados.fonte());
        var existente = clubeAliasRepository.findByAliasAndFonte(dados.alias(), fonte);
        if (existente.isPresent()) {
            return ResultadoDeSincronizacao.atualizado(existente.get().getId());
        }
        var alias = clubeAliasRepository.save(new ClubeAlias(
                clubeRepository.getReferenceById(dados.clubeId()), dados.alias(), fonte));
        return ResultadoDeSincronizacao.criado(alias.getId());
    }
```

Imports a acrescentar: `br.com.api.footfirma.clube.domain.{Clube, ClubeAlias, Estadio, FonteExterna}`, `br.com.api.footfirma.clube.dto.{DadosDeAlias, DadosDeClube, DadosDeEstadio}`, `br.com.api.footfirma.clube.repository.EstadioRepository`, `br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao`, `java.time.OffsetDateTime`.

`getReferenceById` em vez de `findById`: o alvo é gravar uma chave estrangeira, e um SELECT por clube em 176 vínculos é desperdício.

- [ ] **Step 7: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*ClubeServiceSincronizacaoTest'`
Expected: PASS — 8 testes verdes

- [ ] **Step 8: Rodar os testes de clube existentes**

Run: `./gradlew test --tests '*ClubeRepositoryTest' --tests '*ClubeControllerTest'`
Expected: PASS — nenhuma regressão

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/api/footfirma/clube src/test/java/br/com/api/footfirma/clube
git commit -m "feat(clube): adiciona upsert de estádio, clube e alias"
```

---

## Task 4: Ingestão em `jogador` — a maior superfície

Seis upserts e o único ponto do sistema que grava `chave_natural` e `semente`.

Ambas chegam **já calculadas** pelo importador (Task 7). O módulo `jogador` não as deriva: se derivasse, a regra de identidade viveria em dois lugares no dia em que um pipeline externo também precisar dela.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeJogador.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/DadosDePosicaoSecundaria.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeAtributos.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeAtributosOcultos.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeCaracteristica.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeVinculo.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorPosicaoRepository.java`
- Create: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorCaracteristicaRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/repository/JogadorVinculoRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- Modify: `src/main/java/br/com/api/footfirma/jogador/internal/JogadorServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/jogador/JogadorServiceSincronizacaoTest.java`

**Interfaces:**
- Consumes: `ResultadoDeSincronizacao` (Task 1); `JogadorRepository.findByChaveNatural(String)`, `PosicaoRepository`, `CaracteristicaRepository.findByCodigo(String)`, `JogadorAtributoRepository.findByJogadorIdAndTemporadaId(Long, Long)`, `JogadorAtributoOcultoRepository` — todos já existentes.
- Produces:
  - `JogadorService.sincronizarJogador(DadosDeJogador) → ResultadoDeSincronizacao`
  - `JogadorService.sincronizarPosicaoSecundaria(DadosDePosicaoSecundaria) → ResultadoDeSincronizacao`
  - `JogadorService.sincronizarAtributos(DadosDeAtributos) → ResultadoDeSincronizacao`
  - `JogadorService.sincronizarAtributosOcultos(DadosDeAtributosOcultos) → ResultadoDeSincronizacao`
  - `JogadorService.sincronizarCaracteristica(DadosDeCaracteristica) → ResultadoDeSincronizacao`
  - `JogadorService.sincronizarVinculo(DadosDeVinculo) → ResultadoDeSincronizacao`
  - `JogadorService.buscarIdCaracteristicaPorCodigo(String codigo) → Optional<Long>`
  - Os seis records `DadosDe*`, com as assinaturas do Step 3.

`pePreferido`, `origem`, `fonteAtributo` e `tipo` trafegam como `String` e viram enum dentro do módulo dono. Passar o enum exigiria expô-lo de `domain/`, que é interno — e é exatamente o que o Modulith impede.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/jogador/JogadorServiceSincronizacaoTest.java`:

```java
package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.dto.DadosDeAtributos;
import br.com.api.footfirma.jogador.dto.DadosDeAtributosOcultos;
import br.com.api.footfirma.jogador.dto.DadosDeCaracteristica;
import br.com.api.footfirma.jogador.dto.DadosDeJogador;
import br.com.api.footfirma.jogador.dto.DadosDePosicaoSecundaria;
import br.com.api.footfirma.jogador.dto.DadosDeVinculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JogadorServiceSincronizacaoTest {

    @Autowired
    JogadorService jogadorService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long paisId;
    private Long atacanteId;
    private Long zagueiroId;
    private Long temporadaA;
    private Long temporadaB;
    private Long clubeA;
    private Long clubeB;

    @BeforeEach
    void prepararCatalogo() {
        jdbcTemplate.update("delete from jogador_overall where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_vinculo where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_atributo where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_atributo_oculto where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_caracteristica where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador_posicao where jogador_id in (select id from jogador where slug like 'sinc-%')");
        jdbcTemplate.update("delete from jogador where slug like 'sinc-%'");
        jdbcTemplate.update("delete from clube where slug like 'sincj-%'");
        jdbcTemplate.update("delete from temporada where label in ('2095', '2096')");

        paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        atacanteId = jdbcTemplate.queryForObject("select id from posicao where codigo = 'ATA'", Long.class);
        zagueiroId = jdbcTemplate.queryForObject("select id from posicao where codigo = 'ZAG'", Long.class);
        temporadaA = inserirTemporada("2095");
        temporadaB = inserirTemporada("2096");
        clubeA = inserirClube("sincj-a");
        clubeB = inserirClube("sincj-b");
    }

    @Test
    void deveCriarJogadorQuandoChaveNaturalNaoExiste() {
        var resultado = jogadorService.sincronizarJogador(jogador("sinc-um", "sinc um"));

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveGravarASementeInformadaPeloImportador() {
        jogadorService.sincronizarJogador(jogador("sinc-dois", "sinc dois"));

        var semente = jdbcTemplate.queryForObject(
                "select semente from jogador where slug = 'sinc-dois'", Long.class);
        assertThat(semente).isEqualTo(4242424242L);
    }

    @Test
    void deveAtualizarOSlugQuandoAChaveNaturalJaExiste() {
        var primeiro = jogadorService.sincronizarJogador(jogador("sinc-tres", "sinc tres"));

        var segundo = jogadorService.sincronizarJogador(
                new DadosDeJogador("sinc-tres-renomeado", "sinc tres|1999-03-14|BRA", 4242424242L,
                        "Sinc Tres", "Sinc Tres", LocalDate.of(1999, 3, 14), paisId, null,
                        180, 75, "DIREITO", atacanteId, "REAL"));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
        var slug = jdbcTemplate.queryForObject(
                "select slug from jogador where id = ?", String.class, primeiro.id());
        assertThat(slug).isEqualTo("sinc-tres-renomeado");
    }

    @Test
    void deveManterUmaUnicaLinhaQuandoSincronizaJogadorDuasVezes() {
        jogadorService.sincronizarJogador(jogador("sinc-quatro", "sinc quatro"));
        jogadorService.sincronizarJogador(jogador("sinc-quatro", "sinc quatro"));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador where slug = 'sinc-quatro'", Integer.class);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveGuardarAtributosDeDuasTemporadasParaOMesmoJogador() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-cinco", "sinc cinco")).id();

        jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaA, 70));
        jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaB, 78));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_atributo where jogador_id = ?", Integer.class, jogadorId);
        assertThat(linhas).isEqualTo(2);
    }

    @Test
    void deveAtualizarAtributosQuandoJogadorETemporadaJaTemLinha() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-seis", "sinc seis")).id();
        jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaA, 70));

        var segundo = jogadorService.sincronizarAtributos(atributos(jogadorId, temporadaA, 82));

        assertThat(segundo.criado()).isFalse();
        var finalizacao = jdbcTemplate.queryForObject(
                "select finalizacao from jogador_atributo where jogador_id = ? and temporada_id = ?",
                Integer.class, jogadorId, temporadaA);
        assertThat(finalizacao).isEqualTo(82);
    }

    @Test
    void deveSubstituirAtributosOcultosQuandoJogadorJaTemRegistro() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-sete", "sinc sete")).id();
        jogadorService.sincronizarAtributosOcultos(
                new DadosDeAtributosOcultos(jogadorId, 60, 60, 60, 60, 60, 60, 60, 60));

        var segundo = jogadorService.sincronizarAtributosOcultos(
                new DadosDeAtributosOcultos(jogadorId, 80, 60, 60, 60, 60, 60, 60, 60));

        assertThat(segundo.criado()).isFalse();
        var profissionalismo = jdbcTemplate.queryForObject(
                "select profissionalismo from jogador_atributo_oculto where jogador_id = ?",
                Integer.class, jogadorId);
        assertThat(profissionalismo).isEqualTo(80);
    }

    @Test
    void deveAceitarDoisVinculosNaMesmaTemporadaEmClubesDiferentes() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-oito", "sinc oito")).id();

        jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeA, temporadaA, 9));
        jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeB, temporadaA, 11));

        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_vinculo where jogador_id = ?", Integer.class, jogadorId);
        assertThat(linhas).isEqualTo(2);
    }

    @Test
    void deveReaproveitarVinculoQuandoJogadorTemporadaEClubeCoincidem() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-nove", "sinc nove")).id();
        var primeiro = jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeA, temporadaA, 9));

        var segundo = jogadorService.sincronizarVinculo(vinculo(jogadorId, clubeA, temporadaA, 10));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveReaproveitarPosicaoSecundariaQuandoJogadorEPosicaoCoincidem() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-dez", "sinc dez")).id();
        jogadorService.sincronizarPosicaoSecundaria(
                new DadosDePosicaoSecundaria(jogadorId, zagueiroId, 2));

        var segundo = jogadorService.sincronizarPosicaoSecundaria(
                new DadosDePosicaoSecundaria(jogadorId, zagueiroId, 3));

        assertThat(segundo.criado()).isFalse();
        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_posicao where jogador_id = ?", Integer.class, jogadorId);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveVincularCaracteristicaDoCatalogoAoJogador() {
        var jogadorId = jogadorService.sincronizarJogador(jogador("sinc-onze", "sinc onze")).id();
        var caracteristicaId = jogadorService.buscarIdCaracteristicaPorCodigo("DRIBLADOR").orElseThrow();

        var resultado = jogadorService.sincronizarCaracteristica(
                new DadosDeCaracteristica(jogadorId, caracteristicaId));

        assertThat(resultado.criado()).isTrue();
    }

    @Test
    void deveDevolverVazioQuandoCodigoDeCaracteristicaNaoExiste() {
        var encontrada = jogadorService.buscarIdCaracteristicaPorCodigo("NAO_EXISTE");

        assertThat(encontrada).isEmpty();
    }

    private DadosDeJogador jogador(String slug, String nomeNormalizado) {
        return new DadosDeJogador(slug, nomeNormalizado + "|1999-03-14|BRA", 4242424242L,
                "Sinc " + slug, "Sinc " + slug, LocalDate.of(1999, 3, 14), paisId, null,
                180, 75, "DIREITO", atacanteId, "REAL");
    }

    private DadosDeAtributos atributos(Long jogadorId, Long temporadaId, int finalizacao) {
        return new DadosDeAtributos(jogadorId, temporadaId,
                70, 70, 70, 70, 70, 70, 70, 70, 70,
                finalizacao, 70, 70, 70, 70, 70, 30, 30, 30,
                85, 5, "IMPORTADO", OffsetDateTime.parse("2026-01-15T10:00:00Z"));
    }

    private DadosDeVinculo vinculo(Long jogadorId, Long clubeId, Long temporadaId, int camisa) {
        return new DadosDeVinculo(jogadorId, clubeId, temporadaId, "CONTRATO", camisa,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), new BigDecimal("1500000.00"));
    }

    private Long inserirTemporada(String label) {
        jdbcTemplate.update(
                "insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)",
                label, Integer.valueOf(label), Integer.valueOf(label));
        return jdbcTemplate.queryForObject(
                "select id from temporada where label = ?", Long.class, label);
    }

    private Long inserirClube(String slug) {
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id, reputacao, qualidade_base)
                values (?, ?, ?, ?, 70, 60)
                """, slug, slug + " Futebol Clube", slug, paisId);
        return jdbcTemplate.queryForObject("select id from clube where slug = ?", Long.class, slug);
    }
}
```

- [ ] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*JogadorServiceSincronizacaoTest'`
Expected: FAIL na compilação — os seis records e os sete métodos não existem

- [ ] **Step 3: Criar os seis records**

Crie `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeJogador.java`:

```java
package br.com.api.footfirma.jogador.dto;

import java.time.LocalDate;

/**
 * Entrada de ingestão. {@code chaveNatural} e {@code semente} chegam calculadas pelo
 * importador: são invariantes do sistema, não dados da fonte, e derivá-las aqui
 * duplicaria a regra de identidade no dia em que um pipeline externo precisar dela.
 */
public record DadosDeJogador(String slug, String chaveNatural, long semente,
                             String nomeCompleto, String nomeExibicao, LocalDate dataNascimento,
                             Long paisId, Long segundaNacionalidadeId,
                             Integer alturaCm, Integer pesoKg, String pePreferido,
                             Long posicaoPrincipalId, String origem) {
}
```

Crie `src/main/java/br/com/api/footfirma/jogador/dto/DadosDePosicaoSecundaria.java`:

```java
package br.com.api.footfirma.jogador.dto;

/** Entrada de ingestão. Espelha uma linha de {@code jogador_posicao.csv}. */
public record DadosDePosicaoSecundaria(Long jogadorId, Long posicaoId, Integer ordem) {
}
```

Crie `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeAtributos.java`:

```java
package br.com.api.footfirma.jogador.dto;

import java.time.OffsetDateTime;

/**
 * As 18 skills mais potencial e procedência, versionadas por temporada.
 * {@code coletadoEm} vem do dataset, não de {@code now()}: é quando o dado foi
 * coletado, não quando foi importado.
 */
public record DadosDeAtributos(Long jogadorId, Long temporadaId,
                               Integer ritmo, Integer forca, Integer folego, Integer salto,
                               Integer agilidade, Integer passe, Integer drible,
                               Integer cruzamento, Integer frieza, Integer finalizacao,
                               Integer cabeceio, Integer falta, Integer penalti,
                               Integer desarme, Integer marcacao, Integer golReflexo,
                               Integer golPosicionamento, Integer golManejo,
                               Integer potencialBase, Integer potencialVariacao,
                               String fonteAtributo, OffsetDateTime coletadoEm) {
}
```

Crie `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeAtributosOcultos.java`:

```java
package br.com.api.footfirma.jogador.dto;

/** Entrada de ingestão. Um registro por jogador — a PK da tabela é o próprio jogador_id. */
public record DadosDeAtributosOcultos(Long jogadorId, Integer profissionalismo, Integer ambicao,
                                      Integer lealdade, Integer temperamento, Integer lideranca,
                                      Integer regularidade, Integer propensaoLesao,
                                      Integer resistenciaPressao) {
}
```

Crie `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeCaracteristica.java`:

```java
package br.com.api.footfirma.jogador.dto;

/** Entrada de ingestão. O id vem resolvido por {@code buscarIdCaracteristicaPorCodigo}. */
public record DadosDeCaracteristica(Long jogadorId, Long caracteristicaId) {
}
```

Crie `src/main/java/br/com/api/footfirma/jogador/dto/DadosDeVinculo.java`:

```java
package br.com.api.footfirma.jogador.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Entrada de ingestão. Espelha uma linha de {@code jogador_vinculo.csv}. */
public record DadosDeVinculo(Long jogadorId, Long clubeId, Long temporadaId, String tipo,
                             Integer numeroCamisa, LocalDate dataInicio, LocalDate dataFim,
                             BigDecimal valorMercadoEur) {
}
```

- [ ] **Step 4: Criar os dois repositories e a query de vínculo**

Crie `src/main/java/br/com/api/footfirma/jogador/repository/JogadorPosicaoRepository.java`:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorPosicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JogadorPosicaoRepository extends JpaRepository<JogadorPosicao, JogadorPosicao.Chave> {

    Optional<JogadorPosicao> findByJogadorIdAndPosicaoId(Long jogadorId, Long posicaoId);
}
```

Crie `src/main/java/br/com/api/footfirma/jogador/repository/JogadorCaracteristicaRepository.java`:

```java
package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorCaracteristica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JogadorCaracteristicaRepository
        extends JpaRepository<JogadorCaracteristica, JogadorCaracteristica.Chave> {

    Optional<JogadorCaracteristica> findByJogadorIdAndCaracteristicaId(Long jogadorId, Long caracteristicaId);
}
```

Em `src/main/java/br/com/api/footfirma/jogador/repository/JogadorVinculoRepository.java`, acrescente:

```java
    Optional<JogadorVinculo> findByJogadorIdAndTemporadaIdAndClubeId(Long jogadorId, Long temporadaId, Long clubeId);
```

Import a acrescentar: `java.util.Optional`.

- [ ] **Step 5: Declarar os sete métodos na interface**

Em `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`:

```java
    /** Upsert por {@code chaveNatural}. O slug é atualizado, nunca usado como identidade. */
    ResultadoDeSincronizacao sincronizarJogador(DadosDeJogador dados);

    /** Upsert por {@code (jogador, posicao)}. */
    ResultadoDeSincronizacao sincronizarPosicaoSecundaria(DadosDePosicaoSecundaria dados);

    /** Upsert por {@code (jogador, temporada)} — atributos são versionados por temporada. */
    ResultadoDeSincronizacao sincronizarAtributos(DadosDeAtributos dados);

    /** Upsert por jogador: a PK da tabela é o próprio {@code jogador_id}. */
    ResultadoDeSincronizacao sincronizarAtributosOcultos(DadosDeAtributosOcultos dados);

    /** Upsert por {@code (jogador, caracteristica)}. */
    ResultadoDeSincronizacao sincronizarCaracteristica(DadosDeCaracteristica dados);

    /** Upsert por {@code (jogador, temporada, clube)} — dois clubes na mesma temporada são válidos. */
    ResultadoDeSincronizacao sincronizarVinculo(DadosDeVinculo dados);

    /** Resolve {@code codigo -> id}; o dataset referencia característica por código. */
    Optional<Long> buscarIdCaracteristicaPorCodigo(String codigo);
```

- [ ] **Step 6: Implementar em `JogadorServiceImpl`**

Acrescente os campos `final` dos repositories novos (`JogadorPosicaoRepository`, `JogadorCaracteristicaRepository`, `JogadorAtributoOcultoRepository`, `CaracteristicaRepository`) e os métodos:

```java
    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarJogador(DadosDeJogador dados) {
        var existente = jogadorRepository.findByChaveNatural(dados.chaveNatural());
        var jogador = existente.orElseGet(() -> new Jogador(
                dados.slug(), dados.chaveNatural(), dados.nomeCompleto(), dados.nomeExibicao(),
                dados.dataNascimento(), dados.paisId(),
                posicaoRepository.getReferenceById(dados.posicaoPrincipalId()),
                PeParaChute.valueOf(dados.pePreferido()), dados.semente()));
        jogador.setSlug(dados.slug());
        jogador.setNomeCompleto(dados.nomeCompleto());
        jogador.setNomeExibicao(dados.nomeExibicao());
        jogador.setDataNascimento(dados.dataNascimento());
        jogador.setPaisId(dados.paisId());
        jogador.setSegundaNacionalidadeId(dados.segundaNacionalidadeId());
        jogador.setAlturaCm(dados.alturaCm());
        jogador.setPesoKg(dados.pesoKg());
        jogador.setPePreferido(PeParaChute.valueOf(dados.pePreferido()));
        jogador.setPosicaoPrincipal(posicaoRepository.getReferenceById(dados.posicaoPrincipalId()));
        jogador.setSemente(dados.semente());
        jogador.setOrigem(OrigemJogador.valueOf(dados.origem()));
        if (existente.isPresent()) {
            jogador.setAtualizadoEm(OffsetDateTime.now());
        }
        var salvo = jogadorRepository.save(jogador);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarPosicaoSecundaria(DadosDePosicaoSecundaria dados) {
        var existente = jogadorPosicaoRepository
                .findByJogadorIdAndPosicaoId(dados.jogadorId(), dados.posicaoId());
        if (existente.isPresent()) {
            existente.get().setOrdem(dados.ordem());
            return ResultadoDeSincronizacao.atualizado(dados.jogadorId());
        }
        jogadorPosicaoRepository.save(new JogadorPosicao(
                jogadorRepository.getReferenceById(dados.jogadorId()),
                posicaoRepository.getReferenceById(dados.posicaoId()),
                dados.ordem()));
        return ResultadoDeSincronizacao.criado(dados.jogadorId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarAtributos(DadosDeAtributos dados) {
        var existente = jogadorAtributoRepository
                .findByJogadorIdAndTemporadaId(dados.jogadorId(), dados.temporadaId());
        var atributo = existente.orElseGet(() -> new JogadorAtributo(
                jogadorRepository.getReferenceById(dados.jogadorId()), dados.temporadaId(),
                FonteAtributo.valueOf(dados.fonteAtributo())));
        atributo.setRitmo(dados.ritmo());
        atributo.setForca(dados.forca());
        atributo.setFolego(dados.folego());
        atributo.setSalto(dados.salto());
        atributo.setAgilidade(dados.agilidade());
        atributo.setPasse(dados.passe());
        atributo.setDrible(dados.drible());
        atributo.setCruzamento(dados.cruzamento());
        atributo.setFrieza(dados.frieza());
        atributo.setFinalizacao(dados.finalizacao());
        atributo.setCabeceio(dados.cabeceio());
        atributo.setFalta(dados.falta());
        atributo.setPenalti(dados.penalti());
        atributo.setDesarme(dados.desarme());
        atributo.setMarcacao(dados.marcacao());
        atributo.setGolReflexo(dados.golReflexo());
        atributo.setGolPosicionamento(dados.golPosicionamento());
        atributo.setGolManejo(dados.golManejo());
        atributo.setPotencialBase(dados.potencialBase());
        atributo.setPotencialVariacao(dados.potencialVariacao());
        atributo.setFonteAtributo(FonteAtributo.valueOf(dados.fonteAtributo()));
        atributo.setColetadoEm(dados.coletadoEm());
        var salvo = jogadorAtributoRepository.save(atributo);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarAtributosOcultos(DadosDeAtributosOcultos dados) {
        var existente = jogadorAtributoOcultoRepository.findById(dados.jogadorId());
        var ocultos = existente.orElseGet(() -> new JogadorAtributoOculto(
                jogadorRepository.getReferenceById(dados.jogadorId())));
        ocultos.setProfissionalismo(dados.profissionalismo());
        ocultos.setAmbicao(dados.ambicao());
        ocultos.setLealdade(dados.lealdade());
        ocultos.setTemperamento(dados.temperamento());
        ocultos.setLideranca(dados.lideranca());
        ocultos.setRegularidade(dados.regularidade());
        ocultos.setPropensaoLesao(dados.propensaoLesao());
        ocultos.setResistenciaPressao(dados.resistenciaPressao());
        jogadorAtributoOcultoRepository.save(ocultos);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(dados.jogadorId())
                : ResultadoDeSincronizacao.criado(dados.jogadorId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarCaracteristica(DadosDeCaracteristica dados) {
        var existente = jogadorCaracteristicaRepository
                .findByJogadorIdAndCaracteristicaId(dados.jogadorId(), dados.caracteristicaId());
        if (existente.isPresent()) {
            return ResultadoDeSincronizacao.atualizado(dados.jogadorId());
        }
        jogadorCaracteristicaRepository.save(new JogadorCaracteristica(
                jogadorRepository.getReferenceById(dados.jogadorId()),
                caracteristicaRepository.getReferenceById(dados.caracteristicaId())));
        return ResultadoDeSincronizacao.criado(dados.jogadorId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarVinculo(DadosDeVinculo dados) {
        var existente = jogadorVinculoRepository.findByJogadorIdAndTemporadaIdAndClubeId(
                dados.jogadorId(), dados.temporadaId(), dados.clubeId());
        var vinculo = existente.orElseGet(() -> new JogadorVinculo(
                jogadorRepository.getReferenceById(dados.jogadorId()),
                dados.clubeId(), dados.temporadaId(), TipoVinculo.valueOf(dados.tipo())));
        vinculo.setTipo(TipoVinculo.valueOf(dados.tipo()));
        vinculo.setNumeroCamisa(dados.numeroCamisa());
        vinculo.setDataInicio(dados.dataInicio());
        vinculo.setDataFim(dados.dataFim());
        vinculo.setValorMercadoEur(dados.valorMercadoEur());
        var salvo = jogadorVinculoRepository.save(vinculo);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    public Optional<Long> buscarIdCaracteristicaPorCodigo(String codigo) {
        return caracteristicaRepository.findByCodigo(codigo).map(Caracteristica::getId);
    }
```

`sincronizarPosicaoSecundaria`, `sincronizarAtributosOcultos` e `sincronizarCaracteristica` devolvem `jogadorId` no lugar de um id próprio: as três tabelas têm chave composta ou compartilhada, não têm coluna `id`, e o importador não usa esse valor para ligar nada.

Imports a acrescentar em `JogadorServiceImpl`: os seis records de `jogador.dto`, `br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao`, `br.com.api.footfirma.jogador.domain.{Caracteristica, FonteAtributo, Jogador, JogadorAtributo, JogadorAtributoOculto, JogadorCaracteristica, JogadorPosicao, JogadorVinculo, OrigemJogador, PeParaChute, TipoVinculo}`, os repositories novos e `java.time.OffsetDateTime`.

- [ ] **Step 7: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*JogadorServiceSincronizacaoTest'`
Expected: PASS — 12 testes verdes

- [ ] **Step 8: Rodar os testes de jogador existentes**

Run: `./gradlew test --tests '*Jogador*Test'`
Expected: PASS — nenhuma regressão nos seis testes de jogador já existentes

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/api/footfirma/jogador src/test/java/br/com/api/footfirma/jogador
git commit -m "feat(jogador): adiciona upsert de jogador, atributos e vínculo"
```

---

## Task 5: Ingestão em `competicao` — cinco upserts encadeados

Competição → edição → fase / participante / regra. As três últimas dependem do id da edição, que o importador guarda em mapa `(competicaoSlug, temporadaLabel)` → `edicaoId`.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeCompeticao.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeEdicao.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeFase.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeParticipante.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeRegra.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/repository/FaseRepository.java`
- Create: `src/main/java/br/com/api/footfirma/competicao/repository/EdicaoParticipanteRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/competicao/repository/EdicaoRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/competicao/repository/RegraClassificacaoRepository.java`
- Modify: `src/main/java/br/com/api/footfirma/competicao/CompeticaoService.java`
- Modify: `src/main/java/br/com/api/footfirma/competicao/internal/CompeticaoServiceImpl.java`
- Test: `src/test/java/br/com/api/footfirma/competicao/CompeticaoServiceSincronizacaoTest.java`

**Interfaces:**
- Consumes: `ResultadoDeSincronizacao` (Task 1); `CompeticaoRepository.findBySlug(String)`, já existente.
- Produces:
  - `CompeticaoService.sincronizarCompeticao(DadosDeCompeticao) → ResultadoDeSincronizacao`
  - `CompeticaoService.sincronizarEdicao(DadosDeEdicao) → ResultadoDeSincronizacao`
  - `CompeticaoService.sincronizarFase(DadosDeFase) → ResultadoDeSincronizacao`
  - `CompeticaoService.sincronizarParticipante(DadosDeParticipante) → ResultadoDeSincronizacao`
  - `CompeticaoService.sincronizarRegra(DadosDeRegra) → ResultadoDeSincronizacao`
  - `CompeticaoService.buscarIdPorSlug(String slug) → Optional<Long>`
  - Os cinco records `DadosDe*`, com as assinaturas do Step 3.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/competicao/CompeticaoServiceSincronizacaoTest.java`:

```java
package br.com.api.footfirma.competicao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CompeticaoServiceSincronizacaoTest {

    @Autowired
    CompeticaoService competicaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long paisId;
    private Long temporadaId;
    private Long clubeId;

    @BeforeEach
    void prepararCatalogo() {
        jdbcTemplate.update("delete from regra_classificacao where edicao_id in (select e.id from edicao e join competicao c on c.id = e.competicao_id where c.slug like 'sinc-%')");
        jdbcTemplate.update("delete from edicao_participante where edicao_id in (select e.id from edicao e join competicao c on c.id = e.competicao_id where c.slug like 'sinc-%')");
        jdbcTemplate.update("delete from fase where edicao_id in (select e.id from edicao e join competicao c on c.id = e.competicao_id where c.slug like 'sinc-%')");
        jdbcTemplate.update("delete from edicao where competicao_id in (select id from competicao where slug like 'sinc-%')");
        jdbcTemplate.update("delete from competicao where slug like 'sinc-%'");
        jdbcTemplate.update("delete from clube where slug like 'sincc-%'");
        jdbcTemplate.update("delete from temporada where label = '2097'");

        paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        jdbcTemplate.update("insert into temporada (label, ano_inicio, ano_fim) values ('2097', 2097, 2097)");
        temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2097'", Long.class);
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id, reputacao, qualidade_base)
                values ('sincc-um', 'Sincc Um Futebol Clube', 'Sincc Um', ?, 70, 60)
                """, paisId);
        clubeId = jdbcTemplate.queryForObject(
                "select id from clube where slug = 'sincc-um'", Long.class);
    }

    @Test
    void deveCriarCompeticaoQuandoSlugNaoExiste() {
        var resultado = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao("sinc-liga", "Liga Sinc", paisId, "LIGA", 1, "MASCULINO"));

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.id()).isNotNull();
    }

    @Test
    void deveDevolverOMesmoIdQuandoSlugDeCompeticaoJaExiste() {
        var primeiro = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao("sinc-liga2", "Liga Sinc 2", paisId, "LIGA", 1, "MASCULINO"));

        var segundo = competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao("sinc-liga2", "Liga Sinc 2 Renomeada", paisId, "LIGA", 2, "MASCULINO"));

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.id()).isEqualTo(primeiro.id());
    }

    @Test
    void deveReaproveitarEdicaoQuandoCompeticaoETemporadaCoincidem() {
        var competicaoId = novaCompeticao("sinc-liga3");
        var primeira = competicaoService.sincronizarEdicao(
                new DadosDeEdicao(competicaoId, temporadaId, "Liga Sinc 3 2097", null, null));

        var segunda = competicaoService.sincronizarEdicao(
                new DadosDeEdicao(competicaoId, temporadaId, "Liga Sinc 3 2097",
                        LocalDate.of(2097, 4, 1), LocalDate.of(2097, 12, 5)));

        assertThat(segunda.criado()).isFalse();
        assertThat(segunda.id()).isEqualTo(primeira.id());
    }

    @Test
    void deveManterFaseUnicaPorOrdemNaEdicao() {
        var edicaoId = novaEdicao("sinc-liga4");
        var primeira = competicaoService.sincronizarFase(
                new DadosDeFase(edicaoId, 1, "Fase única", "PONTOS_CORRIDOS", 1, false, false, false));

        var segunda = competicaoService.sincronizarFase(
                new DadosDeFase(edicaoId, 1, "Turno único", "PONTOS_CORRIDOS", 1, false, false, false));

        assertThat(segunda.criado()).isFalse();
        assertThat(segunda.id()).isEqualTo(primeira.id());
        var linhas = jdbcTemplate.queryForObject(
                "select count(*) from fase where edicao_id = ?", Integer.class, edicaoId);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void deveAtualizarPosicaoFinalDoParticipanteSemDuplicar() {
        var edicaoId = novaEdicao("sinc-liga5");
        competicaoService.sincronizarParticipante(new DadosDeParticipante(edicaoId, clubeId, 5));

        var segundo = competicaoService.sincronizarParticipante(
                new DadosDeParticipante(edicaoId, clubeId, 2));

        assertThat(segundo.criado()).isFalse();
        var posicao = jdbcTemplate.queryForObject(
                "select posicao_final from edicao_participante where edicao_id = ? and clube_id = ?",
                Integer.class, edicaoId, clubeId);
        assertThat(posicao).isEqualTo(2);
    }

    @Test
    void deveAceitarRegraApontandoParaCompeticaoSemEdicao() {
        var edicaoId = novaEdicao("sinc-liga6");
        var destinoId = novaCompeticao("sinc-liga-destino");

        var resultado = competicaoService.sincronizarRegra(
                new DadosDeRegra(edicaoId, 7, 8, "REBAIXAMENTO", destinoId));

        assertThat(resultado.criado()).isTrue();
        var edicoesDoDestino = jdbcTemplate.queryForObject(
                "select count(*) from edicao where competicao_id = ?", Integer.class, destinoId);
        assertThat(edicoesDoDestino).isZero();
    }

    @Test
    void deveReaproveitarRegraQuandoFaixaETipoCoincidem() {
        var edicaoId = novaEdicao("sinc-liga7");
        var primeira = competicaoService.sincronizarRegra(
                new DadosDeRegra(edicaoId, 1, 4, "LIBERTADORES_GRUPOS", null));

        var segunda = competicaoService.sincronizarRegra(
                new DadosDeRegra(edicaoId, 1, 4, "LIBERTADORES_GRUPOS", null));

        assertThat(segunda.criado()).isFalse();
        assertThat(segunda.id()).isEqualTo(primeira.id());
    }

    @Test
    void deveResolverIdPorSlug() {
        var competicaoId = novaCompeticao("sinc-liga8");

        var encontrado = competicaoService.buscarIdPorSlug("sinc-liga8");

        assertThat(encontrado).contains(competicaoId);
    }

    private Long novaCompeticao(String slug) {
        return competicaoService.sincronizarCompeticao(
                new DadosDeCompeticao(slug, "Competição " + slug, paisId, "LIGA", 1, "MASCULINO")).id();
    }

    private Long novaEdicao(String slug) {
        var competicaoId = novaCompeticao(slug);
        return competicaoService.sincronizarEdicao(
                new DadosDeEdicao(competicaoId, temporadaId, "Edição " + slug, null, null)).id();
    }
}
```

- [ ] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*CompeticaoServiceSincronizacaoTest'`
Expected: FAIL na compilação — os cinco records e os seis métodos não existem

- [ ] **Step 3: Criar os cinco records**

Crie `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeCompeticao.java`:

```java
package br.com.api.footfirma.competicao.dto;

/** Entrada de ingestão. {@code paisId} é nulo em competição continental. */
public record DadosDeCompeticao(String slug, String nome, Long paisId, String tipo,
                                Integer nivel, String genero) {
}
```

Crie `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeEdicao.java`:

```java
package br.com.api.footfirma.competicao.dto;

import java.time.LocalDate;

/** Entrada de ingestão. Chave de upsert: {@code (competicaoId, temporadaId)}. */
public record DadosDeEdicao(Long competicaoId, Long temporadaId, String nome,
                            LocalDate dataInicio, LocalDate dataFim) {
}
```

Crie `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeFase.java`:

```java
package br.com.api.footfirma.competicao.dto;

/** Entrada de ingestão. Chave de upsert: {@code (edicaoId, ordem)}. */
public record DadosDeFase(Long edicaoId, Integer ordem, String nome, String tipo,
                          Integer jogosPorConfronto, boolean temGolFora,
                          boolean temProrrogacao, boolean temPenaltis) {
}
```

Crie `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeParticipante.java`:

```java
package br.com.api.footfirma.competicao.dto;

/** Entrada de ingestão. {@code posicaoFinal} é nulo em edição ainda não disputada. */
public record DadosDeParticipante(Long edicaoId, Long clubeId, Integer posicaoFinal) {
}
```

Crie `src/main/java/br/com/api/footfirma/competicao/dto/DadosDeRegra.java`:

```java
package br.com.api.footfirma.competicao.dto;

/**
 * Entrada de ingestão. {@code competicaoDestinoId} pode apontar para competição sem
 * edição carregada — é o caso do destino de rebaixamento.
 */
public record DadosDeRegra(Long edicaoId, Integer posicaoInicio, Integer posicaoFim,
                           String tipo, Long competicaoDestinoId) {
}
```

- [ ] **Step 4: Criar e ampliar os repositories**

Crie `src/main/java/br/com/api/footfirma/competicao/repository/FaseRepository.java`:

```java
package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.Fase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FaseRepository extends JpaRepository<Fase, Long> {

    Optional<Fase> findByEdicaoIdAndOrdem(Long edicaoId, Integer ordem);
}
```

Crie `src/main/java/br/com/api/footfirma/competicao/repository/EdicaoParticipanteRepository.java`:

```java
package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.EdicaoParticipante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EdicaoParticipanteRepository extends JpaRepository<EdicaoParticipante, Long> {

    Optional<EdicaoParticipante> findByEdicaoIdAndClubeId(Long edicaoId, Long clubeId);
}
```

Em `src/main/java/br/com/api/footfirma/competicao/repository/EdicaoRepository.java`, acrescente:

```java
    // Sem join fetch, ao contrário de buscarPorSlugETemporadaId: aqui o alvo é
    // upsert, não montar DTO de leitura.
    Optional<Edicao> findByCompeticaoIdAndTemporadaId(Long competicaoId, Long temporadaId);
```

Em `src/main/java/br/com/api/footfirma/competicao/repository/RegraClassificacaoRepository.java`, acrescente:

```java
    Optional<RegraClassificacao> findByEdicaoIdAndPosicaoInicioAndPosicaoFimAndTipo(
            Long edicaoId, Integer posicaoInicio, Integer posicaoFim, TipoClassificacao tipo);
```

Imports a acrescentar: `br.com.api.footfirma.competicao.domain.TipoClassificacao` e `java.util.Optional`.

- [ ] **Step 5: Declarar os seis métodos na interface**

Em `src/main/java/br/com/api/footfirma/competicao/CompeticaoService.java`:

```java
    /** Upsert por {@code slug}. */
    ResultadoDeSincronizacao sincronizarCompeticao(DadosDeCompeticao dados);

    /** Upsert por {@code (competicao, temporada)}. */
    ResultadoDeSincronizacao sincronizarEdicao(DadosDeEdicao dados);

    /** Upsert por {@code (edicao, ordem)}. */
    ResultadoDeSincronizacao sincronizarFase(DadosDeFase dados);

    /** Upsert por {@code (edicao, clube)}. */
    ResultadoDeSincronizacao sincronizarParticipante(DadosDeParticipante dados);

    /** Upsert por {@code (edicao, faixa, tipo)}. */
    ResultadoDeSincronizacao sincronizarRegra(DadosDeRegra dados);

    /** Resolve {@code slug -> id}; regra de classificação aponta para competição destino. */
    Optional<Long> buscarIdPorSlug(String slug);
```

- [ ] **Step 6: Implementar em `CompeticaoServiceImpl`**

Acrescente os campos `final` de `FaseRepository` e `EdicaoParticipanteRepository` e os métodos:

```java
    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarCompeticao(DadosDeCompeticao dados) {
        var existente = competicaoRepository.findBySlug(dados.slug());
        var competicao = existente.orElseGet(() -> new Competicao(
                dados.slug(), dados.nome(), TipoCompeticao.valueOf(dados.tipo())));
        competicao.setNome(dados.nome());
        competicao.setPaisId(dados.paisId());
        competicao.setTipo(TipoCompeticao.valueOf(dados.tipo()));
        competicao.setNivel(dados.nivel());
        competicao.setGenero(dados.genero());
        var salva = competicaoRepository.save(competicao);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salva.getId())
                : ResultadoDeSincronizacao.criado(salva.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarEdicao(DadosDeEdicao dados) {
        var existente = edicaoRepository
                .findByCompeticaoIdAndTemporadaId(dados.competicaoId(), dados.temporadaId());
        var edicao = existente.orElseGet(() -> new Edicao(
                competicaoRepository.getReferenceById(dados.competicaoId()),
                dados.temporadaId(), dados.nome()));
        edicao.setNome(dados.nome());
        edicao.setDataInicio(dados.dataInicio());
        edicao.setDataFim(dados.dataFim());
        var salva = edicaoRepository.save(edicao);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salva.getId())
                : ResultadoDeSincronizacao.criado(salva.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarFase(DadosDeFase dados) {
        var existente = faseRepository.findByEdicaoIdAndOrdem(dados.edicaoId(), dados.ordem());
        var fase = existente.orElseGet(() -> new Fase(
                edicaoRepository.getReferenceById(dados.edicaoId()),
                dados.ordem(), dados.nome(), TipoFase.valueOf(dados.tipo())));
        fase.setNome(dados.nome());
        fase.setTipo(TipoFase.valueOf(dados.tipo()));
        fase.setJogosPorConfronto(dados.jogosPorConfronto());
        fase.setTemGolFora(dados.temGolFora());
        fase.setTemProrrogacao(dados.temProrrogacao());
        fase.setTemPenaltis(dados.temPenaltis());
        var salva = faseRepository.save(fase);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salva.getId())
                : ResultadoDeSincronizacao.criado(salva.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarParticipante(DadosDeParticipante dados) {
        var existente = edicaoParticipanteRepository
                .findByEdicaoIdAndClubeId(dados.edicaoId(), dados.clubeId());
        var participante = existente.orElseGet(() -> new EdicaoParticipante(
                edicaoRepository.getReferenceById(dados.edicaoId()), dados.clubeId()));
        participante.setPosicaoFinal(dados.posicaoFinal());
        var salvo = edicaoParticipanteRepository.save(participante);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarRegra(DadosDeRegra dados) {
        var tipo = TipoClassificacao.valueOf(dados.tipo());
        var existente = regraClassificacaoRepository
                .findByEdicaoIdAndPosicaoInicioAndPosicaoFimAndTipo(
                        dados.edicaoId(), dados.posicaoInicio(), dados.posicaoFim(), tipo);
        if (existente.isPresent()) {
            existente.get().setCompeticaoDestinoId(dados.competicaoDestinoId());
            return ResultadoDeSincronizacao.atualizado(existente.get().getId());
        }
        var regra = regraClassificacaoRepository.save(new RegraClassificacao(
                edicaoRepository.getReferenceById(dados.edicaoId()),
                dados.posicaoInicio(), dados.posicaoFim(), tipo, dados.competicaoDestinoId()));
        return ResultadoDeSincronizacao.criado(regra.getId());
    }

    @Override
    public Optional<Long> buscarIdPorSlug(String slug) {
        return competicaoRepository.findBySlug(slug).map(Competicao::getId);
    }
```

Imports a acrescentar: os cinco records de `competicao.dto`, `br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao`, `br.com.api.footfirma.competicao.domain.{Competicao, Edicao, EdicaoParticipante, Fase, RegraClassificacao, TipoClassificacao, TipoCompeticao, TipoFase}`, os dois repositories novos.

- [ ] **Step 7: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*CompeticaoServiceSincronizacaoTest'`
Expected: PASS — 8 testes verdes

- [ ] **Step 8: Rodar os testes de competição existentes**

Run: `./gradlew test --tests '*Competicao*Test'`
Expected: PASS — nenhuma regressão

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/api/footfirma/competicao src/test/java/br/com/api/footfirma/competicao
git commit -m "feat(competicao): adiciona upsert de competição, edição, fase e regra"
```

---

## Task 6: Migration V14 e as entidades de auditoria

Três tabelas novas mais a constraint que faltava em `estadio`.

**Files:**
- Create: `src/main/resources/db/migration/V14__cria_importacao.sql`
- Create: `src/main/java/br/com/api/footfirma/importacao/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/domain/StatusImportacao.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/domain/SeveridadeOcorrencia.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/domain/ImportacaoExecucao.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/domain/ImportacaoContagem.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/domain/ImportacaoOcorrencia.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/repository/ImportacaoExecucaoRepository.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/repository/ImportacaoContagemRepository.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/repository/ImportacaoOcorrenciaRepository.java`
- Test: `src/test/java/br/com/api/footfirma/importacao/ImportacaoSchemaTest.java`

**Interfaces:**
- Consumes: nada dos módulos anteriores — `importacao` ainda não depende de ninguém nesta task.
- Produces: as três entidades e os três repositories, consumidos pela Task 10 via `RegistroDeExecucao`. `StatusImportacao` tem as constantes `EM_ANDAMENTO`, `CONCLUIDA`, `FALHOU`; `SeveridadeOcorrencia` tem `AVISO` e `ERRO`.

- [ ] **Step 1: Escrever a migration**

Crie `src/main/resources/db/migration/V14__cria_importacao.sql`:

```sql
-- O importador registra o que fez. Sem isso, "a carga rodou" é afirmação sem
-- lastro: não há como saber qual dataset entrou, quantas linhas foram criadas
-- nem quais foram recusadas.
create table importacao_execucao (
    id              bigint      generated always as identity primary key,
    dataset_versao  text        not null check (length(dataset_versao) between 1 and 60),
    schema_versao   text        not null check (length(schema_versao) between 1 and 10),
    diretorio       text        not null check (length(diretorio) between 1 and 400),
    status          text        not null check (status in ('EM_ANDAMENTO', 'CONCLUIDA', 'FALHOU')),
    iniciada_em     timestamptz not null default now(),
    finalizada_em   timestamptz,
    motivo_da_falha text        check (length(motivo_da_falha) between 1 and 400),
    constraint ck_importacao_execucao_falha
        check ((status = 'FALHOU') = (motivo_da_falha is not null))
);

create index idx_importacao_execucao_iniciada on importacao_execucao (iniciada_em desc);

comment on column importacao_execucao.dataset_versao is
    'Vem do manifest.json. É o que liga um estado do banco ao dataset que o produziu';

-- Contagem por entidade em tabela, não em jsonb: o resto do schema é tipado e
-- consultável, e "quantos jogadores foram criados na última carga" deve ser um
-- where, não uma extração de JSON.
create table importacao_contagem (
    id          bigint  generated always as identity primary key,
    execucao_id bigint  not null references importacao_execucao (id),
    entidade    text    not null check (length(entidade) between 1 and 60),
    lidos       integer not null default 0 check (lidos >= 0),
    criados     integer not null default 0 check (criados >= 0),
    atualizados integer not null default 0 check (atualizados >= 0),
    recusados   integer not null default 0 check (recusados >= 0),
    constraint uq_importacao_contagem unique (execucao_id, entidade)
);

-- Linha que o importador recusou antes de tocar no banco. Erro de banco não vira
-- ocorrência: ele aborta a etapa, porque uma transação marcada como rollback-only
-- não pode prosseguir fingindo que gravou.
create table importacao_ocorrencia (
    id            bigint      generated always as identity primary key,
    execucao_id   bigint      not null references importacao_execucao (id),
    entidade      text        not null check (length(entidade) between 1 and 60),
    linha         integer     not null check (linha > 0),
    chave         text        not null check (length(chave) between 1 and 220),
    severidade    text        not null check (severidade in ('AVISO', 'ERRO')),
    motivo        text        not null check (length(motivo) between 1 and 240),
    registrada_em timestamptz not null default now()
);

create index idx_importacao_ocorrencia_execucao on importacao_ocorrencia (execucao_id);

-- estadio nasceu sem chave natural em V5. Sem ela não existe upsert idempotente:
-- reimportar o mesmo dataset duplicaria todo estádio. Constraint nova em migration
-- nova — V5 permanece intocada.
alter table estadio add constraint uq_estadio_nome_cidade unique (nome, cidade);
```

- [ ] **Step 2: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/importacao/ImportacaoSchemaTest.java`:

```java
package br.com.api.footfirma.importacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ImportacaoSchemaTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveRejeitarStatusForaDoDominio() {
        assertThatThrownBy(() -> inserirExecucao("INVENTADO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveExigirMotivoQuandoStatusEFalhou() {
        assertThatThrownBy(() -> inserirExecucao("FALHOU", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarMotivoQuandoStatusEConcluida() {
        assertThatThrownBy(() -> inserirExecucao("CONCLUIDA", "não deveria ter motivo"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveAceitarExecucaoConcluidaSemMotivo() {
        var execucaoId = inserirExecucao("CONCLUIDA", null);

        assertThat(execucaoId).isNotNull();
    }

    @Test
    void deveRejeitarSegundaContagemDaMesmaEntidadeNaMesmaExecucao() {
        var execucaoId = inserirExecucao("CONCLUIDA", null);
        inserirContagem(execucaoId, "clube");

        assertThatThrownBy(() -> inserirContagem(execucaoId, "clube"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarSeveridadeForaDoDominio() {
        var execucaoId = inserirExecucao("CONCLUIDA", null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into importacao_ocorrencia (execucao_id, entidade, linha, chave, severidade, motivo)
                values (?, 'clube', 3, 'sinc-alfa', 'CATASTROFE', 'motivo qualquer')
                """, execucaoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarEstadioDuplicadoPorNomeECidade() {
        jdbcTemplate.update(
                "insert into estadio (nome, cidade) values ('Estádio Schema', 'Recife')");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into estadio (nome, cidade) values ('Estádio Schema', 'Recife')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long inserirExecucao(String status, String motivo) {
        jdbcTemplate.update("""
                insert into importacao_execucao
                    (dataset_versao, schema_versao, diretorio, status, motivo_da_falha)
                values ('fixtures-v1', '1', 'fixtures/v1', ?, ?)
                """, status, motivo);
        return jdbcTemplate.queryForObject(
                "select max(id) from importacao_execucao", Long.class);
    }

    private void inserirContagem(Long execucaoId, String entidade) {
        jdbcTemplate.update("""
                insert into importacao_contagem (execucao_id, entidade, lidos, criados)
                values (?, ?, 8, 8)
                """, execucaoId, entidade);
    }
}
```

- [ ] **Step 3: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*ImportacaoSchemaTest'`
Expected: FAIL — `relation "importacao_execucao" does not exist` (a migration ainda não foi aplicada ao container)

Se a migration do Step 1 já tiver sido criada antes de rodar, este passo passa direto; nesse caso, confirme que o Flyway aplicou a V14 com `select version from flyway_schema_history order by installed_rank desc limit 1`.

- [ ] **Step 4: Criar os dois enums**

Crie `src/main/java/br/com/api/footfirma/importacao/domain/StatusImportacao.java`:

```java
package br.com.api.footfirma.importacao.domain;

public enum StatusImportacao {
    EM_ANDAMENTO, CONCLUIDA, FALHOU
}
```

Crie `src/main/java/br/com/api/footfirma/importacao/domain/SeveridadeOcorrencia.java`:

```java
package br.com.api.footfirma.importacao.domain;

public enum SeveridadeOcorrencia {
    AVISO, ERRO
}
```

- [ ] **Step 5: Criar `ImportacaoExecucao`**

Crie `src/main/java/br/com/api/footfirma/importacao/domain/ImportacaoExecucao.java`:

```java
package br.com.api.footfirma.importacao.domain;

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
@Table(name = "importacao_execucao")
@Getter
@Setter
public class ImportacaoExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_versao", nullable = false)
    private String datasetVersao;

    @Column(name = "schema_versao", nullable = false)
    private String schemaVersao;

    @Column(nullable = false)
    private String diretorio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusImportacao status;

    @Column(name = "iniciada_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime iniciadaEm;

    @Column(name = "finalizada_em")
    private OffsetDateTime finalizadaEm;

    @Column(name = "motivo_da_falha")
    private String motivoDaFalha;

    protected ImportacaoExecucao() {
    }

    public ImportacaoExecucao(String datasetVersao, String schemaVersao, String diretorio) {
        this.datasetVersao = datasetVersao;
        this.schemaVersao = schemaVersao;
        this.diretorio = diretorio;
        this.status = StatusImportacao.EM_ANDAMENTO;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ImportacaoExecucao execucao)) {
            return false;
        }
        return id != null && id.equals(execucao.id);
    }

    @Override
    public int hashCode() {
        return ImportacaoExecucao.class.hashCode();
    }
}
```

- [ ] **Step 6: Criar `ImportacaoContagem` e `ImportacaoOcorrencia`**

Crie `src/main/java/br/com/api/footfirma/importacao/domain/ImportacaoContagem.java`:

```java
package br.com.api.footfirma.importacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code execucaoId} é Long cru, não @ManyToOne, mesmo pertencendo ao próprio
 * módulo: a auditoria é escrita em lote no fim da carga, e uma associação
 * gerenciada só criaria oportunidade de flush no meio dela.
 */
@Entity
@Table(name = "importacao_contagem")
@Getter
@Setter
public class ImportacaoContagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execucao_id", nullable = false)
    private Long execucaoId;

    @Column(nullable = false)
    private String entidade;

    @Column(nullable = false)
    private Integer lidos;

    @Column(nullable = false)
    private Integer criados;

    @Column(nullable = false)
    private Integer atualizados;

    @Column(nullable = false)
    private Integer recusados;

    protected ImportacaoContagem() {
    }

    public ImportacaoContagem(Long execucaoId, String entidade,
                              Integer lidos, Integer criados, Integer atualizados, Integer recusados) {
        this.execucaoId = execucaoId;
        this.entidade = entidade;
        this.lidos = lidos;
        this.criados = criados;
        this.atualizados = atualizados;
        this.recusados = recusados;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ImportacaoContagem contagem)) {
            return false;
        }
        return id != null && id.equals(contagem.id);
    }

    @Override
    public int hashCode() {
        return ImportacaoContagem.class.hashCode();
    }
}
```

Crie `src/main/java/br/com/api/footfirma/importacao/domain/ImportacaoOcorrencia.java` no mesmo formato, com os campos `execucaoId` (`Long`, coluna `execucao_id`), `entidade` (`String`), `linha` (`Integer`), `chave` (`String`), `severidade` (`SeveridadeOcorrencia`, `@Enumerated(EnumType.STRING)`), `motivo` (`String`) e `registradaEm` (`OffsetDateTime`, coluna `registrada_em`, `insertable = false, updatable = false`). Construtor público:

```java
    public ImportacaoOcorrencia(Long execucaoId, String entidade, Integer linha, String chave,
                                SeveridadeOcorrencia severidade, String motivo) {
        this.execucaoId = execucaoId;
        this.entidade = entidade;
        this.linha = linha;
        this.chave = chave;
        this.severidade = severidade;
        this.motivo = motivo;
    }
```

- [ ] **Step 7: Criar o `package-info.java` e os três repositories**

Crie `src/main/java/br/com/api/footfirma/importacao/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "Importação")
package br.com.api.footfirma.importacao;
```

Crie os três repositories, cada um estendendo `JpaRepository<Entidade, Long>` sem método adicional:

```java
package br.com.api.footfirma.importacao.repository;

import br.com.api.footfirma.importacao.domain.ImportacaoExecucao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoExecucaoRepository extends JpaRepository<ImportacaoExecucao, Long> {
}
```

E os equivalentes `ImportacaoContagemRepository` e `ImportacaoOcorrenciaRepository`.

- [ ] **Step 8: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*ImportacaoSchemaTest'`
Expected: PASS — 7 testes verdes

- [ ] **Step 9: Rodar o teste de modularidade**

Run: `./gradlew test --tests '*ModularidadeTest'`
Expected: PASS — `importacao` aparece como módulo novo, ainda sem dependências

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/db/migration/V14__cria_importacao.sql \
        src/main/java/br/com/api/footfirma/importacao \
        src/test/java/br/com/api/footfirma/importacao
git commit -m "feat(importacao): cria tabelas de auditoria e chave natural de estádio"
```

---

## Task 7: `ChaveNatural` — identidade e semente

Função pura, sem Spring, sem banco. O único lugar do sistema que define o que faz dois registros serem a mesma pessoa.

O teste vive em `importacao/internal/` porque a classe é package-private — mesmo padrão de `CalculadoraDeOverallTest`, que já está em `avaliacao/internal/`.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/ChaveNatural.java`
- Test: `src/test/java/br/com/api/footfirma/importacao/internal/ChaveNaturalTest.java`

**Interfaces:**
- Consumes: nada.
- Produces (todos package-private, estáticos):
  - `ChaveNatural.de(String nomeCompleto, LocalDate nascimento, String isoPais) → String`
  - `ChaveNatural.normalizar(String texto) → String`
  - `ChaveNatural.semente(String chaveNatural) → long`

  A Task 10 chama `de(...)` e `semente(...)` ao montar cada `DadosDeJogador`.

- [ ] **Step 1: Escrever o teste que falha**

Crie `src/test/java/br/com/api/footfirma/importacao/internal/ChaveNaturalTest.java`:

```java
package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ChaveNaturalTest {

    private static final LocalDate NASCIMENTO = LocalDate.of(1998, 7, 21);

    @Test
    void deveIgnorarAcentosAoNormalizar() {
        var comAcento = ChaveNatural.de("Antônio Gonçalves", NASCIMENTO, "BRA");
        var semAcento = ChaveNatural.de("Antonio Goncalves", NASCIMENTO, "BRA");

        assertThat(comAcento).isEqualTo(semAcento);
    }

    @Test
    void deveIgnorarCaixaEEspacoDuplicado() {
        var bagunçado = ChaveNatural.de("  JOÃO   DA SILVA ", NASCIMENTO, "BRA");
        var limpo = ChaveNatural.de("joão da silva", NASCIMENTO, "BRA");

        assertThat(bagunçado).isEqualTo(limpo);
    }

    @Test
    void deveDiferenciarHomonimosComDataDeNascimentoDiferente() {
        var mais_velho = ChaveNatural.de("Carlos Souza", LocalDate.of(1990, 1, 1), "BRA");
        var mais_novo = ChaveNatural.de("Carlos Souza", LocalDate.of(2001, 1, 1), "BRA");

        assertThat(mais_velho).isNotEqualTo(mais_novo);
    }

    @Test
    void deveDiferenciarHomonimosDeNacionalidadeDiferente() {
        var brasileiro = ChaveNatural.de("Carlos Souza", NASCIMENTO, "BRA");
        var portugues = ChaveNatural.de("Carlos Souza", NASCIMENTO, "PRT");

        assertThat(brasileiro).isNotEqualTo(portugues);
    }

    @Test
    void deveProduzirSempreAMesmaSementeParaAMesmaChave() {
        var chave = ChaveNatural.de("Rafael Andrade", NASCIMENTO, "BRA");

        assertThat(ChaveNatural.semente(chave)).isEqualTo(ChaveNatural.semente(chave));
    }

    @Test
    void deveProduzirSementeNaoNegativa() {
        for (var indice = 0; indice < 500; indice++) {
            var chave = ChaveNatural.de("Jogador Numero " + indice, NASCIMENTO, "BRA");

            assertThat(ChaveNatural.semente(chave)).isNotNegative();
        }
    }

    @Test
    void deveProduzirSementesDistintasParaMilChavesDistintas() {
        var sementes = new HashSet<Long>();

        for (var indice = 0; indice < 1_000; indice++) {
            sementes.add(ChaveNatural.semente(
                    ChaveNatural.de("Jogador Sintetico " + indice, NASCIMENTO, "BRA")));
        }

        assertThat(sementes).hasSize(1_000);
    }

    @Test
    void deveCaberNoLimiteDaColunaComONomeMaisLongoPermitido() {
        var nomeDe160Caracteres = "a".repeat(160);

        var chave = ChaveNatural.de(nomeDe160Caracteres, NASCIMENTO, "BRA");

        assertThat(chave.length()).isBetween(5, 220);
    }

    @Test
    void deveNormalizarPontuacaoParaEspaco() {
        var comPontuacao = ChaveNatural.normalizar("D'Alessandro-Filho Jr.");

        assertThat(comPontuacao).isEqualTo("d alessandro filho jr");
    }
}
```

- [ ] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*ChaveNaturalTest'`
Expected: FAIL na compilação — `ChaveNatural` não existe

- [ ] **Step 3: Implementar `ChaveNatural`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/ChaveNatural.java`:

```java
package br.com.api.footfirma.importacao.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Define a identidade de um jogador entre execuções. Pura de propósito: quando um
 * pipeline externo existir, ele precisará da mesma regra, e identidade duplicada em
 * dois lugares diverge no primeiro ajuste.
 */
final class ChaveNatural {

    private ChaveNatural() {
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

    /**
     * SHA-256 em vez de {@code String.hashCode()}: 32 bits colidem com frequência
     * observável em milhares de jogadores, e a semente é a origem de todo atributo
     * oculto — colisão significa dois jogadores com a mesma personalidade.
     *
     * <p>O sinal é descartado com {@code & Long.MAX_VALUE} porque semente negativa
     * complica todo uso em aritmética modular a jusante.
     */
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

- [ ] **Step 4: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*ChaveNaturalTest'`
Expected: PASS — 9 testes verdes

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/api/footfirma/importacao/internal/ChaveNatural.java \
        src/test/java/br/com/api/footfirma/importacao/internal/ChaveNaturalTest.java
git commit -m "feat(importacao): deriva chave natural e semente do jogador"
```

---

## Task 8: Leitura de CSV e validação do dataset

Quatro peças puras. Nenhuma toca banco nem Spring; todas falham cedo, com mensagem que aponta arquivo e linha.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/DatasetInvalidoException.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/LinhaDeCsv.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/LeitorDeCsv.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/Manifesto.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/ValidadorDeDataset.java`
- Test: `src/test/java/br/com/api/footfirma/importacao/internal/LeitorDeCsvTest.java`
- Test: `src/test/java/br/com/api/footfirma/importacao/internal/ValidadorDeDatasetTest.java`

**Interfaces:**
- Consumes: nada.
- Produces (package-private):
  - `LeitorDeCsv.ler(Path arquivo, List<String> cabecalhoEsperado) → List<LinhaDeCsv>`
  - `LinhaDeCsv` com `numero()`, `texto(String)`, `textoObrigatorio(String)`, `inteiro(String)`, `numeroLongo(String)`, `decimal(String)`, `data(String)`, `dataHora(String)`, `booleano(String)`
  - `Manifesto(String schemaVersao, String datasetVersao, String geradoEm, List<Manifesto.ArquivoDoManifesto> arquivos)` e `Manifesto.ArquivoDoManifesto(String nome, int linhas, String sha256)`, com a constante `Manifesto.SCHEMA_SUPORTADO = "1"`
  - `ValidadorDeDataset.validar(Path diretorio) → Manifesto`
  - `ValidadorDeDataset.sha256(Path arquivo) → String` (usada pelo gerador da Task 9 e pelo helper de teste da Task 12)
  - `DatasetInvalidoException extends RuntimeException`

**Limitação conhecida e aceita:** o parser lê linha a linha, então um campo com quebra de linha *dentro* das aspas não é suportado. O formato do dataset proíbe esse caso (nenhuma coluna é texto livre multilinha), e suportá-lo exigiria um parser de stream com o dobro de complexidade. Se algum dia uma coluna precisar de multilinha, esta é a decisão a revisitar.

- [ ] **Step 1: Escrever `LeitorDeCsvTest`**

Crie `src/test/java/br/com/api/footfirma/importacao/internal/LeitorDeCsvTest.java`:

```java
package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorDeCsvTest {

    private static final List<String> CABECALHO = List.of("slug", "nome", "reputacao");

    @TempDir
    Path diretorio;

    @Test
    void deveLerAsLinhasDeUmArquivoSimples() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa,70
                beta,Beta,64
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas).hasSize(2);
        assertThat(linhas.getFirst().texto("nome")).isEqualTo("Alfa");
        assertThat(linhas.getLast().inteiro("reputacao")).isEqualTo(64);
    }

    @Test
    void deveLerCampoEntreAspasContendoVirgula() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,"Alfa, Beta e Gama",70
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("nome")).isEqualTo("Alfa, Beta e Gama");
    }

    @Test
    void deveInterpretarAspasDuplicadasComoAspaLiteral() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,"O ""Grande"" Alfa",70
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("nome")).isEqualTo("O \\"Grande\\" Alfa");
    }

    @Test
    void deveTratarCampoVazioComoNulo() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,,70
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("nome")).isNull();
        assertThat(linhas.getFirst().inteiro("nome")).isNull();
    }

    @Test
    void deveDevolverListaVaziaQuandoArquivoSoTemCabecalho() throws IOException {
        var arquivo = escrever("slug,nome,reputacao\n");

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas).isEmpty();
    }

    @Test
    void deveIgnorarBomNoInicioDoArquivo() throws IOException {
        var arquivo = escrever("﻿slug,nome,reputacao\nalfa,Alfa,70\n");

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("slug")).isEqualTo("alfa");
    }

    @Test
    void deveRejeitarArquivoComCabecalhoDivergente() throws IOException {
        var arquivo = escrever("""
                slug,nome
                alfa,Alfa
                """);

        assertThatThrownBy(() -> LeitorDeCsv.ler(arquivo, CABECALHO))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("cabeçalho");
    }

    @Test
    void deveRejeitarLinhaComNumeroDeCamposDiferenteDoCabecalho() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa
                """);

        assertThatThrownBy(() -> LeitorDeCsv.ler(arquivo, CABECALHO))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("linha 2");
    }

    @Test
    void deveIndicarONumeroDaLinhaContandoOCabecalho() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa,70
                beta,Beta,64
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().numero()).isEqualTo(2);
        assertThat(linhas.getLast().numero()).isEqualTo(3);
    }

    @Test
    void deveRejeitarAcessoAColunaInexistente() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa,70
                """);
        var linha = LeitorDeCsv.ler(arquivo, CABECALHO).getFirst();

        assertThatThrownBy(() -> linha.texto("inexistente"))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("inexistente");
    }

    @Test
    void deveRejeitarCampoObrigatorioVazio() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                ,Alfa,70
                """);
        var linha = LeitorDeCsv.ler(arquivo, CABECALHO).getFirst();

        assertThatThrownBy(() -> linha.textoObrigatorio("slug"))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("slug");
    }

    private Path escrever(String conteudo) throws IOException {
        var arquivo = diretorio.resolve("teste.csv");
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        return arquivo;
    }
}
```

- [ ] **Step 2: Rodar o teste para verificar que falha**

Run: `./gradlew test --tests '*LeitorDeCsvTest'`
Expected: FAIL na compilação — `LeitorDeCsv`, `LinhaDeCsv` e `DatasetInvalidoException` não existem

- [ ] **Step 3: Criar `DatasetInvalidoException`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/DatasetInvalidoException.java`:

```java
package br.com.api.footfirma.importacao.internal;

/**
 * Defeito estrutural do dataset: manifesto inconsistente, checksum divergente,
 * cabeçalho errado, coluna faltando. Sempre aborta antes de qualquer escrita —
 * é diferente de ocorrência, que é linha recusada com a carga prosseguindo.
 */
class DatasetInvalidoException extends RuntimeException {

    DatasetInvalidoException(String mensagem) {
        super(mensagem);
    }

    DatasetInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
```

- [ ] **Step 4: Criar `LinhaDeCsv`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/LinhaDeCsv.java`:

```java
package br.com.api.footfirma.importacao.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uma linha já dividida, com acesso por nome de coluna. Carrega arquivo e número
 * para que toda mensagem de erro aponte o lugar exato — sem isso, "valor inválido"
 * em 352 linhas de atributo é inútil.
 */
record LinhaDeCsv(String arquivo, int numero, List<String> cabecalho, List<String> campos) {

    String texto(String coluna) {
        var valor = campos.get(indice(coluna));
        return valor.isEmpty() ? null : valor;
    }

    String textoObrigatorio(String coluna) {
        var valor = texto(coluna);
        if (valor == null) {
            throw new DatasetInvalidoException(
                    "%s, linha %d: coluna '%s' é obrigatória e está vazia".formatted(arquivo, numero, coluna));
        }
        return valor;
    }

    Integer inteiro(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : Integer.valueOf(converter(coluna, valor, Integer::valueOf));
    }

    Long numeroLongo(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, Long::valueOf);
    }

    BigDecimal decimal(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, BigDecimal::new);
    }

    LocalDate data(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, LocalDate::parse);
    }

    OffsetDateTime dataHora(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, OffsetDateTime::parse);
    }

    boolean booleano(String coluna) {
        return "true".equalsIgnoreCase(texto(coluna));
    }

    private <T> T converter(String coluna, String valor, java.util.function.Function<String, T> conversao) {
        try {
            return conversao.apply(valor);
        } catch (RuntimeException erro) {
            throw new DatasetInvalidoException(
                    "%s, linha %d: valor '%s' inválido na coluna '%s'".formatted(arquivo, numero, valor, coluna),
                    erro);
        }
    }

    private int indice(String coluna) {
        var indice = cabecalho.indexOf(coluna);
        if (indice < 0) {
            throw new DatasetInvalidoException(
                    "%s: coluna '%s' não existe no cabeçalho %s".formatted(arquivo, coluna, cabecalho));
        }
        return indice;
    }
}
```

- [ ] **Step 5: Criar `LeitorDeCsv`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/LeitorDeCsv.java`:

```java
package br.com.api.footfirma.importacao.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser de CSV suficiente para o formato do dataset: separador vírgula, aspas
 * duplas com escape por duplicação, campo vazio como nulo.
 *
 * <p>Não suporta quebra de linha dentro de aspas — o formato proíbe, e suportá-la
 * dobraria a complexidade do parser sem caso de uso.
 */
final class LeitorDeCsv {

    private static final char ASPAS = '"';
    private static final char SEPARADOR = ',';
    private static final char BOM = '﻿';

    private LeitorDeCsv() {
    }

    static List<LinhaDeCsv> ler(Path arquivo, List<String> cabecalhoEsperado) {
        var nome = arquivo.getFileName().toString();
        var brutas = lerTodasAsLinhas(arquivo, nome);
        if (brutas.isEmpty()) {
            throw new DatasetInvalidoException(nome + " está vazio: falta o cabeçalho");
        }

        var cabecalho = dividir(semBom(brutas.getFirst()));
        if (!cabecalho.equals(cabecalhoEsperado)) {
            throw new DatasetInvalidoException(
                    "%s: cabeçalho %s difere do esperado %s".formatted(nome, cabecalho, cabecalhoEsperado));
        }

        var linhas = new ArrayList<LinhaDeCsv>();
        for (var indice = 1; indice < brutas.size(); indice++) {
            var bruta = brutas.get(indice);
            if (bruta.isBlank()) {
                continue;
            }
            var numero = indice + 1;
            var campos = dividir(bruta);
            if (campos.size() != cabecalho.size()) {
                throw new DatasetInvalidoException(
                        "%s, linha %d: %d campos, esperados %d"
                                .formatted(nome, numero, campos.size(), cabecalho.size()));
            }
            linhas.add(new LinhaDeCsv(nome, numero, cabecalho, campos));
        }
        return linhas;
    }

    private static List<String> lerTodasAsLinhas(Path arquivo, String nome) {
        try {
            return Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível ler " + nome, erro);
        }
    }

    private static String semBom(String linha) {
        return linha.isEmpty() || linha.charAt(0) != BOM ? linha : linha.substring(1);
    }

    private static List<String> dividir(String linha) {
        var campos = new ArrayList<String>();
        var atual = new StringBuilder();
        var dentroDeAspas = false;

        for (var indice = 0; indice < linha.length(); indice++) {
            var caractere = linha.charAt(indice);
            if (dentroDeAspas) {
                if (caractere != ASPAS) {
                    atual.append(caractere);
                } else if (indice + 1 < linha.length() && linha.charAt(indice + 1) == ASPAS) {
                    atual.append(ASPAS);
                    indice++;
                } else {
                    dentroDeAspas = false;
                }
            } else if (caractere == ASPAS) {
                dentroDeAspas = true;
            } else if (caractere == SEPARADOR) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(caractere);
            }
        }
        campos.add(atual.toString());
        return campos;
    }
}
```

- [ ] **Step 6: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*LeitorDeCsvTest'`
Expected: PASS — 11 testes verdes

- [ ] **Step 7: Escrever `ValidadorDeDatasetTest`**

Crie `src/test/java/br/com/api/footfirma/importacao/internal/ValidadorDeDatasetTest.java`:

```java
package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorDeDatasetTest {

    private static final String CSV = "label,ano_inicio,ano_fim\n2025,2025,2025\n";

    @TempDir
    Path diretorio;

    @Test
    void deveAceitarDatasetIntegro() throws IOException {
        montarDatasetValido();

        var manifesto = ValidadorDeDataset.validar(diretorio);

        assertThat(manifesto.datasetVersao()).isEqualTo("fixtures-teste");
        assertThat(manifesto.arquivos()).hasSize(1);
    }

    @Test
    void deveRejeitarSchemaVersaoNaoSuportada() throws IOException {
        escreverCsv();
        escreverManifesto("99", ValidadorDeDataset.sha256(diretorio.resolve("temporada.csv")), 1);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("schema");
    }

    @Test
    void deveRejeitarQuandoChecksumDivergeDoArquivo() throws IOException {
        montarDatasetValido();
        Files.writeString(diretorio.resolve("temporada.csv"),
                CSV + "2026,2026,2026\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void deveRejeitarQuandoArquivoListadoNaoExiste() throws IOException {
        montarDatasetValido();
        Files.delete(diretorio.resolve("temporada.csv"));

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("temporada.csv");
    }

    @Test
    void deveRejeitarQuandoContagemDeLinhasDiverge() throws IOException {
        escreverCsv();
        escreverManifesto("1", ValidadorDeDataset.sha256(diretorio.resolve("temporada.csv")), 7);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("linhas");
    }

    @Test
    void deveRejeitarCsvPresenteNoDiretorioEAusenteDoManifesto() throws IOException {
        montarDatasetValido();
        Files.writeString(diretorio.resolve("esquecido.csv"), "a\n1\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("esquecido.csv");
    }

    @Test
    void deveRejeitarDiretorioSemManifesto() {
        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("manifest.json");
    }

    private void montarDatasetValido() throws IOException {
        escreverCsv();
        escreverManifesto("1", ValidadorDeDataset.sha256(diretorio.resolve("temporada.csv")), 1);
    }

    private void escreverCsv() throws IOException {
        Files.writeString(diretorio.resolve("temporada.csv"), CSV, StandardCharsets.UTF_8);
    }

    private void escreverManifesto(String schemaVersao, String sha256, int linhas) throws IOException {
        Files.writeString(diretorio.resolve("manifest.json"), """
                {
                  "schemaVersao": "%s",
                  "datasetVersao": "fixtures-teste",
                  "geradoEm": "2026-08-03",
                  "arquivos": [
                    { "nome": "temporada.csv", "linhas": %d, "sha256": "%s" }
                  ]
                }
                """.formatted(schemaVersao, linhas, sha256), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 8: Criar `Manifesto` e `ValidadorDeDataset`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/Manifesto.java`:

```java
package br.com.api.footfirma.importacao.internal;

import java.util.List;

/** Espelha `manifest.json`. Desserializado por Jackson pelo nome dos componentes. */
record Manifesto(String schemaVersao, String datasetVersao, String geradoEm,
                 List<ArquivoDoManifesto> arquivos) {

    static final String SCHEMA_SUPORTADO = "1";

    record ArquivoDoManifesto(String nome, int linhas, String sha256) {
    }
}
```

Crie `src/main/java/br/com/api/footfirma/importacao/internal/ValidadorDeDataset.java`:

```java
package br.com.api.footfirma.importacao.internal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Valida o dataset inteiro antes de qualquer escrita. A ordem das quatro regras
 * importa: sem schema conhecido, as demais checagens não significam nada.
 */
final class ValidadorDeDataset {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ARQUIVO_DO_MANIFESTO = "manifest.json";

    private ValidadorDeDataset() {
    }

    static Manifesto validar(Path diretorio) {
        var manifesto = lerManifesto(diretorio);

        if (!Manifesto.SCHEMA_SUPORTADO.equals(manifesto.schemaVersao())) {
            throw new DatasetInvalidoException(
                    "schema versão '%s' não suportada; esperada '%s'"
                            .formatted(manifesto.schemaVersao(), Manifesto.SCHEMA_SUPORTADO));
        }

        var listados = new HashSet<String>();
        for (var arquivo : manifesto.arquivos()) {
            listados.add(arquivo.nome());
            var caminho = diretorio.resolve(arquivo.nome());
            if (!Files.isRegularFile(caminho)) {
                throw new DatasetInvalidoException(
                        "manifesto lista %s, que não existe no diretório".formatted(arquivo.nome()));
            }
            var checksum = sha256(caminho);
            if (!checksum.equals(arquivo.sha256())) {
                throw new DatasetInvalidoException(
                        "checksum de %s não confere: manifesto diz %s, arquivo tem %s"
                                .formatted(arquivo.nome(), arquivo.sha256(), checksum));
            }
            var linhas = contarLinhasDeDados(caminho);
            if (linhas != arquivo.linhas()) {
                throw new DatasetInvalidoException(
                        "%s tem %d linhas de dados; manifesto declara %d"
                                .formatted(arquivo.nome(), linhas, arquivo.linhas()));
            }
        }

        for (var csv : csvsDoDiretorio(diretorio)) {
            if (!listados.contains(csv)) {
                throw new DatasetInvalidoException(
                        "%s existe no diretório mas não está no manifesto".formatted(csv));
            }
        }
        return manifesto;
    }

    static String sha256(Path arquivo) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(arquivo));
            return HexFormat.of().formatHex(digest);
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível ler " + arquivo.getFileName(), erro);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", erro);
        }
    }

    private static Manifesto lerManifesto(Path diretorio) {
        var caminho = diretorio.resolve(ARQUIVO_DO_MANIFESTO);
        if (!Files.isRegularFile(caminho)) {
            throw new DatasetInvalidoException(
                    "%s não encontrado em %s".formatted(ARQUIVO_DO_MANIFESTO, diretorio));
        }
        try {
            return JSON.readValue(Files.readString(caminho, StandardCharsets.UTF_8), Manifesto.class);
        } catch (IOException erro) {
            throw new DatasetInvalidoException(ARQUIVO_DO_MANIFESTO + " é inválido", erro);
        }
    }

    private static int contarLinhasDeDados(Path arquivo) {
        try (var linhas = Files.lines(arquivo, StandardCharsets.UTF_8)) {
            return (int) linhas.skip(1).filter(linha -> !linha.isBlank()).count();
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível ler " + arquivo.getFileName(), erro);
        }
    }

    private static List<String> csvsDoDiretorio(Path diretorio) {
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            return arquivos
                    .map(caminho -> caminho.getFileName().toString())
                    .filter(nome -> nome.endsWith(".csv"))
                    .sorted()
                    .toList();
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível listar " + diretorio, erro);
        }
    }
}
```

- [ ] **Step 9: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*ValidadorDeDatasetTest'`
Expected: PASS — 7 testes verdes

- [ ] **Step 10: Commit**

```bash
git add src/main/java/br/com/api/footfirma/importacao/internal \
        src/test/java/br/com/api/footfirma/importacao/internal
git commit -m "feat(importacao): lê CSV e valida dataset por manifesto"
```

---

## Task 9: Gerador de fixtures, CSVs versionados e o `fixtures/README.md`

Produz o dataset. Roda uma vez por task Gradle; o resultado é commitado.

**Files:**
- Modify: `build.gradle`
- Create: `src/test/java/br/com/api/footfirma/importacao/internal/GeradorDeFixtures.java`
- Create: `fixtures/v1/*.csv` e `fixtures/v1/manifest.json` (gerados)
- Create: `fixtures/README.md`
- Test: `src/test/java/br/com/api/footfirma/importacao/internal/FixturesIntegridadeTest.java`

**Interfaces:**
- Consumes: `ValidadorDeDataset.sha256(Path)` e `ValidadorDeDataset.validar(Path)` (Task 8). As duas são package-private, e é por isso que o gerador e o teste de integridade vivem em `importacao/internal/` dentro de `src/test/java` — o pacote precisa bater com o da classe usada.
- Produces: o diretório `fixtures/v1` completo, consumido pelas Tasks 10–12, e a propriedade de sistema `footfirma.fixtures` apontando para ele durante os testes.

- [ ] **Step 1: Ajustar o `build.gradle`**

Em `build.gradle`, substitua o bloco `tasks.named('test')` e acrescente a task nova:

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

tasks.named('test') {
    useJUnitPlatform()
    // Torna o caminho das fixtures independente do working directory do teste
    systemProperty 'footfirma.fixtures', file('fixtures/v1').absolutePath
}
```

- [ ] **Step 2: Escrever o gerador**

Crie `src/test/java/br/com/api/footfirma/importacao/internal/GeradorDeFixtures.java`. A classe é determinística: usa `new Random(SEMENTE)` com semente constante e **nunca** `Math.random()`, `Instant.now()` ou `LocalDate.now()`.

```java
package br.com.api.footfirma.importacao.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;

/**
 * Emite o dataset de fixtures. Determinístico: mesma semente, mesmos arquivos.
 * Nomes de clube e jogador são inventados — o dataset é versionado no repositório
 * e não pode carregar dado licenciado.
 *
 * <p>Roda por {@code ./gradlew gerarFixtures}, nunca pela suíte de testes.
 */
public final class GeradorDeFixtures {

    private static final long SEMENTE = 20260803L;
    private static final String GERADO_EM = "2026-08-03";
    private static final String DATASET_VERSAO = "fixtures-v1";
    private static final int JOGADORES_POR_CLUBE = 22;

    private static final List<String> PRENOMES = List.of(
            "Adriano", "Alisson", "Anderson", "Bruno", "Caio", "Danilo", "Diego", "Douglas",
            "Eduardo", "Emerson", "Fabrício", "Felipe", "Gabriel", "Gustavo", "Henrique",
            "Igor", "Ítalo", "João", "Kleber", "Leandro", "Lucas", "Marcelo", "Mateus",
            "Murilo", "Nathan", "Otávio", "Patrick", "Rafael", "Renan", "Ricardo",
            "Rodrigo", "Samuel", "Thiago", "Vinícius", "Wallace", "Wesley", "Yuri");

    private static final List<String> SOBRENOMES = List.of(
            "Albuquerque", "Almeida", "Andrade", "Aragão", "Azevedo", "Barbosa", "Batista",
            "Bezerra", "Braga", "Cardoso", "Carvalho", "Castro", "Cavalcanti", "Correia",
            "Cunha", "Dantas", "Duarte", "Esteves", "Farias", "Fernandes", "Ferreira",
            "Fonseca", "Freitas", "Furtado", "Gonçalves", "Guimarães", "Lacerda", "Leite",
            "Lima", "Macedo", "Machado", "Magalhães", "Marinho", "Medeiros", "Meireles",
            "Moraes", "Nogueira", "Nunes", "Pacheco", "Peixoto", "Pontes", "Queiroz",
            "Rabelo", "Ramalho", "Rezende", "Sampaio", "Siqueira", "Tavares", "Teixeira",
            "Valadares", "Vasconcelos", "Xavier");

    /** slug, nomeCurto, nomeOficial, uf, cidade, estádio, reputação, qualidade de base, cores. */
    private record ClubeFicticio(String slug, String nomeCurto, String nomeOficial, String uf,
                                 String cidade, String estadio, int reputacao, int qualidadeBase,
                                 String corPrimaria, String corSecundaria) {
    }

    private static final List<ClubeFicticio> CLUBES = List.of(
            new ClubeFicticio("atletico-serrano", "Serrano", "Atlético Clube Serrano", "MG",
                    "Juiz de Fora", "Arena da Serra", 78, 72, "#1B4D3E", "#FFFFFF"),
            new ClubeFicticio("guarani-portuario", "Portuário", "Guarani Portuário Futebol Clube", "SP",
                    "Santos", "Estádio das Docas", 74, 68, "#0A2D6E", "#F2C230"),
            new ClubeFicticio("sociedade-ipanema", "Ipanema", "Sociedade Esportiva Ipanema", "RJ",
                    "Niterói", "Estádio Beira-Mar", 71, 65, "#7B1E3A", "#FFFFFF"),
            new ClubeFicticio("esporte-clube-varzea", "Várzea", "Esporte Clube Várzea", "RS",
                    "Pelotas", "Estádio Campo Novo", 68, 70, "#0F5132", "#111111"),
            new ClubeFicticio("nacional-do-cerrado", "Cerrado", "Nacional do Cerrado", "GO",
                    "Anápolis", "Arena do Planalto", 64, 58, "#B33A00", "#FFFFFF"),
            new ClubeFicticio("uniao-litoranea", "Litorânea", "União Litorânea Futebol Clube", "SC",
                    "Itajaí", "Estádio Marítimo", 61, 62, "#00557F", "#9FD8EF"),
            new ClubeFicticio("real-sertanejo", "Sertanejo", "Real Sertanejo Esporte Clube", "BA",
                    "Feira de Santana", "Arena do Sertão", 57, 55, "#4B2E83", "#F5F5F5"),
            new ClubeFicticio("avante-fluvial", "Fluvial", "Avante Fluvial Clube", "PA",
                    "Santarém", "Estádio Rio Grande", 52, 50, "#006B54", "#EAEAEA"));

    /** Elenco por posição: 3 GOL, 4 ZAG, 2 LTD, 2 LTE, 3 VOL, 2 MEC, 2 MEA, 2 PTA, 2 ATA. */
    private static final List<String> ELENCO = List.of(
            "GOL", "GOL", "GOL", "ZAG", "ZAG", "ZAG", "ZAG", "LTD", "LTD", "LTE", "LTE",
            "VOL", "VOL", "VOL", "MEC", "MEC", "MEA", "MEA", "PTA", "PTA", "ATA", "ATA");

    private GeradorDeFixtures() {
    }

    public static void main(String[] argumentos) throws IOException {
        if (argumentos.length != 1) {
            throw new IllegalArgumentException("Uso: GeradorDeFixtures <diretorio-de-saida>");
        }
        var diretorio = Path.of(argumentos[0]);
        Files.createDirectories(diretorio);

        var arquivos = new ArrayList<String>();
        arquivos.add(escrever(diretorio, "temporada.csv", temporadas()));
        arquivos.add(escrever(diretorio, "estadio.csv", estadios()));
        arquivos.add(escrever(diretorio, "clube.csv", clubes()));
        arquivos.add(escrever(diretorio, "clube_alias.csv", aliases()));
        arquivos.add(escrever(diretorio, "competicao.csv", competicoes()));
        arquivos.add(escrever(diretorio, "edicao.csv", edicoes()));
        arquivos.add(escrever(diretorio, "fase.csv", fases()));
        arquivos.add(escrever(diretorio, "edicao_participante.csv", participantes()));
        arquivos.add(escrever(diretorio, "regra_classificacao.csv", regras()));

        var jogadores = gerarJogadores();
        arquivos.add(escrever(diretorio, "jogador.csv", linhasDeJogador(jogadores)));
        arquivos.add(escrever(diretorio, "jogador_posicao.csv", linhasDePosicao(jogadores)));
        arquivos.add(escrever(diretorio, "jogador_atributo.csv", linhasDeAtributo(jogadores)));
        arquivos.add(escrever(diretorio, "jogador_atributo_oculto.csv", linhasDeOcultos(jogadores)));
        arquivos.add(escrever(diretorio, "jogador_caracteristica.csv", linhasDeCaracteristica(jogadores)));
        arquivos.add(escrever(diretorio, "jogador_vinculo.csv", linhasDeVinculo(jogadores)));

        escreverManifesto(diretorio, arquivos);
        System.out.println("Fixtures geradas em " + diretorio.toAbsolutePath());
    }
}
```

Os métodos que faltam seguem um padrão único — cada um devolve `List<String>` cujo primeiro elemento é o cabeçalho e os demais são as linhas. Regras de conteúdo, para que a implementação não fique aberta:

**`temporadas()`** — cabeçalho `label,ano_inicio,ano_fim`; duas linhas: `2025,2025,2025` e `2026,2026,2026`.

**`estadios()`** — cabeçalho `chave,nome,cidade,uf,capacidade,ano_inauguracao`; uma linha por clube, `chave` = slug do clube, capacidade = `20000 + reputacao * 400`, ano = `1950 + (reputacao % 30)`.

**`clubes()`** — uma linha por `ClubeFicticio`; `apelido` vazio; `ano_fundacao` = `1900 + (reputacao % 25)`; `iso_pais` = `BRA`; `estadio_chave` = slug; `uf_base` = `uf`.

**`aliases()`** — cabeçalho `clube_slug,alias,fonte`; duas linhas por clube: o `nomeCurto` e o `nomeOficial`, ambos com fonte `MANUAL`.

**`competicoes()`** — três linhas, conforme a seção "As fixtures": `serie-ouro` (`LIGA`, nível 1), `serie-prata` (`LIGA`, nível 2), `copa-nacional` (`COPA`, nível vazio). Todas com `iso_pais = BRA` e `genero = MASCULINO`.

**`edicoes()`** — `serie-ouro` em 2025 e 2026, `copa-nacional` em 2026. Três linhas. `serie-prata` não aparece.

**`fases()`** — uma fase `PONTOS_CORRIDOS` por edição de `serie-ouro` (ordem 1, `jogos_por_confronto = 2`, resto `false`); três fases `ELIMINATORIA` na `copa-nacional` de 2026: ordem 1 "Quartas de final" e ordem 2 "Semifinal" com `jogos_por_confronto = 2`, `tem_prorrogacao = true`, `tem_penaltis = true`; ordem 3 "Final" com `jogos_por_confronto = 1` e os mesmos dois `true`.

**`participantes()`** — os 8 clubes em cada uma das três edições, 24 linhas. `posicao_final` em `serie-ouro` segue a ordem de reputação (Serrano 1 … Fluvial 8) em 2025 e uma permutação fixa em 2026 (`2,1,4,3,6,5,8,7`), para que a tabela não seja idêntica nas duas temporadas. Na `copa-nacional`, `posicao_final` vazio.

**`regras()`** — por edição de `serie-ouro`: `1,4,LIBERTADORES_GRUPOS` (destino vazio), `5,6,SULAMERICANA` (destino vazio), `7,8,REBAIXAMENTO` com destino `serie-prata`. Seis linhas.

**`gerarJogadores()`** — para cada clube, 22 jogadores na ordem de `ELENCO`. Cada um recebe:

- nome: `PRENOMES.get(random.nextInt(...)) + " " + SOBRENOMES.get(...) + " " + SOBRENOMES.get(...)`, garantindo que os dois sobrenomes sejam distintos;
- slug: nome normalizado com hífens, mais um sufixo numérico se colidir;
- `data_nascimento`: idade sorteada entre 17 e 38, data `LocalDate.of(2026 - idade, 1 + random.nextInt(12), 1 + random.nextInt(28))`;
- `pe_preferido`: `ESQUERDO` para `LTE`, senão `DIREITO` em 80% e `ESQUERDO` em 20%;
- `origem`: `REAL` para todos — origem `GERADO` pertence ao gerador de base, não ao catálogo;
- altura entre 165 e 195 (goleiros entre 183 e 198), peso = `altura - 100 + random.nextInt(9) - 4`.

**Atributos** — base = `40 + clube.reputacao / 2`, ajustada por posição e idade:

- para cada skill, um peso por posição (goleiro tem `gol_*` em `base + 12` e `finalizacao`/`marcacao` em `base - 25`; zagueiro tem `marcacao`/`desarme`/`cabeceio` em `base + 10` e `gol_*` fixos em `10 + random.nextInt(10)`; e assim por diante, uma tabela por código de posição);
- ruído `random.nextInt(17) - 8`;
- fator de idade: `-6` abaixo de 20, `0` entre 20 e 30, `-4` acima de 33;
- clamp final em `[20, 94]` — nunca 99, para o teto continuar disponível ao modelo de progressão;
- `potencial_base` = `min(94, overallEstimado + max(0, 30 - idade))`, `potencial_variacao` entre 2 e 8;
- `fonte_atributo` = `IMPORTADO`, `coletado_em` = `2025-01-15T10:00:00Z` ou `2026-01-15T10:00:00Z` conforme a temporada.

Em 2026, cada skill recebe `+2` se o jogador tem menos de 25 anos, `-2` se tem mais de 32, e `0` no meio — a progressão determinística que prova o versionamento por temporada.

**Ocultos** — os oito valores em `35 + random.nextInt(55)`.

**Características** — de zero a duas por jogador, sorteadas entre os 13 códigos de `V9__cria_caracteristica_e_vinculo.sql`, coerentes com a posição (`ESPECIALISTA_PENALTI` só para não-goleiros, `CABECEIO_AEREO` preferindo ZAG e ATA).

**Vínculos** — todos os 176 jogadores com vínculo `CONTRATO` no seu clube em 2025 e 2026, `numero_camisa` de 1 a 22 dentro do elenco, `valor_mercado_eur` = `overall² × 800`. Exceções fixas, escolhidas por índice e não por sorteio: os jogadores de índice global 10, 45 e 120 têm o vínculo de 2026 no clube seguinte da lista (`(indiceDoClube + 1) % 8`); o de índice 77 tem tipo `EMPRESTIMO` em 2026.

**`escrever(...)`** grava o arquivo com `\n` e devolve o nome; **`escreverManifesto(...)`** monta o JSON com `ValidadorDeDataset.sha256(...)` de cada arquivo e a contagem de linhas de dados, na ordem em que foram escritos.

Campos que contenham vírgula precisam sair entre aspas — como nenhum nome fictício tem vírgula, um `assert` no gerador basta: se um campo contiver `,` ou `"`, lance `IllegalStateException` em vez de gravar CSV quebrado.

- [ ] **Step 3: Gerar as fixtures**

Run: `./gradlew gerarFixtures`
Expected: BUILD SUCCESSFUL e "Fixtures geradas em …/fixtures/v1"

- [ ] **Step 4: Inspecionar o que saiu**

```bash
wc -l fixtures/v1/*.csv
head -3 fixtures/v1/clube.csv fixtures/v1/jogador.csv fixtures/v1/jogador_atributo.csv
```

Confira à mão: 8 clubes, 176 jogadores, 352 linhas de atributo, 352 vínculos, cores em `#RRGGBB`, datas coerentes.

**Este passo exige leitura humana, não só contagem.** Percorra `jogador.csv` procurando combinação que coincida com jogador profissional conhecido. Se encontrar, ajuste o pool de sobrenomes e regenere — é a única verificação deste plano que uma máquina não faz sozinha.

- [ ] **Step 5: Escrever `FixturesIntegridadeTest`**

Crie `src/test/java/br/com/api/footfirma/importacao/internal/FixturesIntegridadeTest.java`:

```java
package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pega a fixture editada à mão sem regerar o manifesto — a falha real. Não regenera
 * o dataset nem compara com o gerador: acoplar o teste ao gerador faria os dois
 * concordarem enquanto ambos estivessem errados.
 */
class FixturesIntegridadeTest {

    private static final Path FIXTURES = Path.of(System.getProperty("footfirma.fixtures"));

    @Test
    void deveTerManifestoConsistenteComOsArquivosVersionados() {
        var manifesto = ValidadorDeDataset.validar(FIXTURES);

        assertThat(manifesto.datasetVersao()).isEqualTo("fixtures-v1");
        assertThat(manifesto.schemaVersao()).isEqualTo(Manifesto.SCHEMA_SUPORTADO);
    }

    @Test
    void deveConterOsQuinzeArquivosDoFormato() {
        var manifesto = ValidadorDeDataset.validar(FIXTURES);

        assertThat(manifesto.arquivos()).hasSize(15);
    }

    @Test
    void deveTerOitoClubesECentoESetentaESeisJogadores() throws IOException {
        var clubes = contarLinhas("clube.csv");
        var jogadores = contarLinhas("jogador.csv");

        assertThat(clubes).isEqualTo(8);
        assertThat(jogadores).isEqualTo(176);
    }

    @Test
    void deveTerAtributosEVinculosNasDuasTemporadas() throws IOException {
        var atributos = contarLinhas("jogador_atributo.csv");
        var vinculos = contarLinhas("jogador_vinculo.csv");

        assertThat(atributos).isEqualTo(352);
        assertThat(vinculos).isEqualTo(352);
    }

    private long contarLinhas(String arquivo) throws IOException {
        try (var linhas = Files.lines(FIXTURES.resolve(arquivo), StandardCharsets.UTF_8)) {
            return linhas.skip(1).filter(linha -> !linha.isBlank()).count();
        }
    }
}
```

- [ ] **Step 6: Rodar o teste para verificar que passa**

Run: `./gradlew test --tests '*FixturesIntegridadeTest'`
Expected: PASS — 4 testes verdes

- [ ] **Step 7: Escrever `fixtures/README.md`**

Crie `fixtures/README.md` seguindo o padrão de `docs/README.md`: `Status: verificado em 2026-08-03`, `## Fontes` com caminhos reais, e um comando de validação. O conteúdo é o contrato que um pipeline externo futuro precisa cumprir:

- a tabela dos 15 arquivos com **todas** as colunas de cada um (copie da seção "O formato do dataset" deste plano);
- o formato do `manifest.json` e as quatro regras de validação;
- o que é derivado (`chave_natural`, `semente`) e o que vem no arquivo;
- que `iso_pais`, `uf`, `posicao` e `caracteristica` resolvem contra seed versionado;
- como regerar: `./gradlew gerarFixtures`;
- comando de validação: `./gradlew test --tests '*FixturesIntegridadeTest'`.

- [ ] **Step 8: Commit**

```bash
git add build.gradle fixtures \
        src/test/java/br/com/api/footfirma/importacao/internal/GeradorDeFixtures.java \
        src/test/java/br/com/api/footfirma/importacao/internal/FixturesIntegridadeTest.java
git commit -m "feat(importacao): adiciona dataset de fixtures fictícias versionado"
```

---

## Task 10: `ImportacaoServiceImpl` — a orquestração

O centro do plano. Quinze etapas na ordem de dependência, mapas de resolução em memória, contagem por entidade, ocorrências, e `materializar()` no fim.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/importacao/ImportacaoService.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/dto/package-info.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/dto/RelatorioDeImportacao.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/dto/ContagemDeEntidade.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/dto/OcorrenciaRegistrada.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/ColecionadorDeOcorrencias.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/RegistroDeExecucao.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/ImportacaoServiceImpl.java`

**Interfaces:**
- Consumes: os 15 `sincronizar*` das Tasks 2–5; `GeografiaService.buscarPaisPorIso`, `buscarEstadoPorUf`; `JogadorService.listarPosicoes`, `buscarIdCaracteristicaPorCodigo`; `CompeticaoService.buscarIdPorSlug`; `AvaliacaoService.materializar(String)`; `ChaveNatural`, `LeitorDeCsv`, `ValidadorDeDataset` das Tasks 7–8; as três entidades e repositories da Task 6.
- Produces:
  - `ImportacaoService.importar(Path diretorio) → RelatorioDeImportacao`
  - `RelatorioDeImportacao(Long execucaoId, String datasetVersao, String status, List<ContagemDeEntidade> contagens, List<OcorrenciaRegistrada> ocorrencias, int overallsMaterializados, String motivoDaFalha)`
  - `ContagemDeEntidade(String entidade, int lidos, int criados, int atualizados, int recusados)`
  - `OcorrenciaRegistrada(String entidade, int linha, String chave, String severidade, String motivo)`

  A Task 11 chama `importar` e lê `status()`; a Task 12 verifica todos os campos.

- [ ] **Step 1: Criar os três DTOs e o `package-info`**

Crie `src/main/java/br/com/api/footfirma/importacao/dto/package-info.java`:

```java
// Records devolvidos por ImportacaoService. Hoje quem os consome é o
// ApplicationRunner do próprio módulo; sem @NamedInterface, um consumidor externo
// futuro seria reprovado pelo ModularidadeTest sem explicação óbvia.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.importacao.dto;
```

Crie `src/main/java/br/com/api/footfirma/importacao/dto/ContagemDeEntidade.java`:

```java
package br.com.api.footfirma.importacao.dto;

/** Uma linha por arquivo do dataset. {@code recusados} conta ocorrências, não erros de banco. */
public record ContagemDeEntidade(String entidade, int lidos, int criados,
                                 int atualizados, int recusados) {
}
```

Crie `src/main/java/br/com/api/footfirma/importacao/dto/OcorrenciaRegistrada.java`:

```java
package br.com.api.footfirma.importacao.dto;

/** Linha recusada antes de tocar o banco, com o lugar exato onde ela está. */
public record OcorrenciaRegistrada(String entidade, int linha, String chave,
                                   String severidade, String motivo) {
}
```

Crie `src/main/java/br/com/api/footfirma/importacao/dto/RelatorioDeImportacao.java`:

```java
package br.com.api.footfirma.importacao.dto;

import java.util.List;

/**
 * Resultado completo de uma carga. {@code motivoDaFalha} é nulo quando o status é
 * {@code CONCLUIDA} — uma carga pode concluir com ocorrências, e isso não é falha.
 */
public record RelatorioDeImportacao(Long execucaoId, String datasetVersao, String status,
                                    List<ContagemDeEntidade> contagens,
                                    List<OcorrenciaRegistrada> ocorrencias,
                                    int overallsMaterializados,
                                    String motivoDaFalha) {
}
```

- [ ] **Step 2: Criar a interface pública**

Crie `src/main/java/br/com/api/footfirma/importacao/ImportacaoService.java`:

```java
package br.com.api.footfirma.importacao;

import br.com.api.footfirma.importacao.dto.RelatorioDeImportacao;

import java.nio.file.Path;

public interface ImportacaoService {

    /**
     * Valida o dataset, importa na ordem de dependência e materializa o overall de
     * cada temporada carregada. Idempotente: duas execuções sobre o mesmo dataset
     * produzem estado idêntico.
     *
     * <p>Não é exposta por REST — seria escrita no catálogo, que é read-only por
     * decisão registrada em {@code docs/adr/2026-08-01-catalogo-read-only.md}.
     *
     * <p>Não lança em falha de carga: devolve o relatório com status
     * {@code FALHOU} e o motivo. Quem chama é um job, e job traduz resultado em
     * código de saída.
     */
    RelatorioDeImportacao importar(Path diretorio);
}
```

- [ ] **Step 3: Criar `ColecionadorDeOcorrencias`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/ColecionadorDeOcorrencias.java`:

```java
package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.importacao.dto.OcorrenciaRegistrada;

import java.util.ArrayList;
import java.util.List;

/**
 * Acumulador de linhas recusadas. Classe própria para que as etapas não precisem
 * carregar uma List mutável como parâmetro, e para que ocorrência e contagem de
 * recusados saiam sempre do mesmo lugar.
 */
final class ColecionadorDeOcorrencias {

    private final List<OcorrenciaRegistrada> ocorrencias = new ArrayList<>();

    void recusar(String entidade, int linha, String chave, String motivo) {
        ocorrencias.add(new OcorrenciaRegistrada(entidade, linha, chave, "ERRO", motivo));
    }

    void avisar(String entidade, int linha, String chave, String motivo) {
        ocorrencias.add(new OcorrenciaRegistrada(entidade, linha, chave, "AVISO", motivo));
    }

    List<OcorrenciaRegistrada> lista() {
        return List.copyOf(ocorrencias);
    }
}
```

- [ ] **Step 4: Criar `RegistroDeExecucao`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/RegistroDeExecucao.java`:

```java
package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.importacao.domain.ImportacaoContagem;
import br.com.api.footfirma.importacao.domain.ImportacaoExecucao;
import br.com.api.footfirma.importacao.domain.ImportacaoOcorrencia;
import br.com.api.footfirma.importacao.domain.SeveridadeOcorrencia;
import br.com.api.footfirma.importacao.domain.StatusImportacao;
import br.com.api.footfirma.importacao.dto.ContagemDeEntidade;
import br.com.api.footfirma.importacao.dto.OcorrenciaRegistrada;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Grava a auditoria em transação própria.
 *
 * <p>{@code REQUIRES_NEW} é o ponto: o registro precisa sobreviver ao rollback da
 * etapa que falhou. Sem isso, uma carga que quebra na metade não deixa rastro — o
 * pior comportamento possível para uma tabela de auditoria.
 */
@Component
@RequiredArgsConstructor
class RegistroDeExecucao {

    private final ImportacaoExecucaoRepository execucaoRepository;
    private final ImportacaoContagemRepository contagemRepository;
    private final ImportacaoOcorrenciaRepository ocorrenciaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Long abrir(String datasetVersao, String schemaVersao, String diretorio) {
        var execucao = execucaoRepository.save(
                new ImportacaoExecucao(datasetVersao, schemaVersao, diretorio));
        return execucao.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void concluir(Long execucaoId, List<ContagemDeEntidade> contagens,
                  List<OcorrenciaRegistrada> ocorrencias) {
        fechar(execucaoId, StatusImportacao.CONCLUIDA, null, contagens, ocorrencias);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void falhar(Long execucaoId, String motivo, List<ContagemDeEntidade> parciais,
                List<OcorrenciaRegistrada> ocorrencias) {
        fechar(execucaoId, StatusImportacao.FALHOU, truncar(motivo), parciais, ocorrencias);
    }

    private void fechar(Long execucaoId, StatusImportacao status, String motivo,
                        List<ContagemDeEntidade> contagens, List<OcorrenciaRegistrada> ocorrencias) {
        var execucao = execucaoRepository.findById(execucaoId).orElseThrow();
        execucao.setStatus(status);
        execucao.setMotivoDaFalha(motivo);
        execucao.setFinalizadaEm(OffsetDateTime.now());
        execucaoRepository.save(execucao);

        contagemRepository.saveAll(contagens.stream()
                .map(contagem -> new ImportacaoContagem(execucaoId, contagem.entidade(),
                        contagem.lidos(), contagem.criados(),
                        contagem.atualizados(), contagem.recusados()))
                .toList());

        ocorrenciaRepository.saveAll(ocorrencias.stream()
                .map(ocorrencia -> new ImportacaoOcorrencia(execucaoId, ocorrencia.entidade(),
                        ocorrencia.linha(), ocorrencia.chave(),
                        SeveridadeOcorrencia.valueOf(ocorrencia.severidade()),
                        truncar(ocorrencia.motivo())))
                .toList());
    }

    /** A coluna aceita 240 (motivo) e 400 (falha); truncar evita que auditoria vire erro. */
    private static String truncar(String texto) {
        return texto == null || texto.length() <= 240 ? texto : texto.substring(0, 240);
    }
}
```

Imports dos repositories: `br.com.api.footfirma.importacao.repository.{ImportacaoContagemRepository, ImportacaoExecucaoRepository, ImportacaoOcorrenciaRepository}`.

- [ ] **Step 5: Escrever o esqueleto de `ImportacaoServiceImpl`**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/ImportacaoServiceImpl.java`:

```java
package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.geografia.dto.EstadoResumo;
import br.com.api.footfirma.geografia.dto.PaisResumo;
import br.com.api.footfirma.importacao.ImportacaoService;
import br.com.api.footfirma.importacao.dto.ContagemDeEntidade;
import br.com.api.footfirma.importacao.dto.RelatorioDeImportacao;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.PosicaoCatalogo;
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
import br.com.api.footfirma.temporada.TemporadaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
@Slf4j
class ImportacaoServiceImpl implements ImportacaoService {

    // 8 dependências, acima do limite de 4~5 de .rules/java-core.md, e justificado:
    // este é o orquestrador, e cada uma é um módulo do catálogo. Dividi-lo por
    // módulo só moveria a lista para outro lugar.
    private final TemporadaService temporadaService;
    private final GeografiaService geografiaService;
    private final ClubeService clubeService;
    private final CompeticaoService competicaoService;
    private final JogadorService jogadorService;
    private final AvaliacaoService avaliacaoService;
    private final RegistroDeExecucao registroDeExecucao;
    private final TransactionTemplate transactionTemplate;

    /** Contador mutável de uma etapa. */
    private static final class Contador {
        private int lidos;
        private int criados;
        private int atualizados;
        private int recusados;

        void registrar(ResultadoDeSincronizacao resultado) {
            if (resultado.criado()) {
                criados++;
            } else {
                atualizados++;
            }
        }

        void recusar() {
            recusados++;
        }
    }

    /** Estado de uma execução. Instância nova por carga — nada sobrevive entre elas. */
    private static final class Contexto {
        private final ColecionadorDeOcorrencias ocorrencias = new ColecionadorDeOcorrencias();
        private final List<ContagemDeEntidade> contagens = new ArrayList<>();
        private final Map<String, Long> temporadaPorLabel = new HashMap<>();
        private final Map<String, Long> estadoPorUf = new HashMap<>();
        private final Map<String, Long> estadioPorChave = new HashMap<>();
        private final Map<String, Long> clubePorSlug = new HashMap<>();
        private final Map<String, Long> competicaoPorSlug = new HashMap<>();
        private final Map<String, Long> edicaoPorChave = new HashMap<>();
        private final Map<String, Long> jogadorPorSlug = new HashMap<>();
        private final Map<String, Long> posicaoPorCodigo = new HashMap<>();
        private final Map<String, Long> caracteristicaPorCodigo = new HashMap<>();
        private final LinkedHashSet<String> temporadasCarregadas = new LinkedHashSet<>();
        private Long paisPadraoId;

        static String chaveDeEdicao(String competicaoSlug, String temporadaLabel) {
            return competicaoSlug + "|" + temporadaLabel;
        }
    }

    @Override
    public RelatorioDeImportacao importar(Path diretorio) {
        // Validação antes de abrir execução: dataset inválido não produziu carga
        // nenhuma, e uma linha FALHOU sem dataset válido não informa nada.
        var manifesto = ValidadorDeDataset.validar(diretorio);
        var execucaoId = registroDeExecucao.abrir(
                manifesto.datasetVersao(), manifesto.schemaVersao(), diretorio.toString());
        var contexto = new Contexto();

        try {
            carregarCatalogoDeSeed(contexto);
            executarEtapas(diretorio, contexto);
            var overalls = materializarTemporadas(contexto);
            registroDeExecucao.concluir(execucaoId, contexto.contagens, contexto.ocorrencias.lista());
            return new RelatorioDeImportacao(execucaoId, manifesto.datasetVersao(), "CONCLUIDA",
                    contexto.contagens, contexto.ocorrencias.lista(), overalls, null);
        } catch (RuntimeException erro) {
            log.error("Importação {} falhou", execucaoId, erro);
            registroDeExecucao.falhar(execucaoId, erro.getMessage(),
                    contexto.contagens, contexto.ocorrencias.lista());
            return new RelatorioDeImportacao(execucaoId, manifesto.datasetVersao(), "FALHOU",
                    contexto.contagens, contexto.ocorrencias.lista(), 0, erro.getMessage());
        }
    }

    private void carregarCatalogoDeSeed(Contexto contexto) {
        contexto.paisPadraoId = geografiaService.buscarPaisPorIso("BRA")
                .map(PaisResumo::id)
                .orElseThrow(() -> new IllegalStateException("seed de geografia ausente: país BRA"));
        geografiaService.listarEstadosDoPais("BRA")
                .forEach(estado -> contexto.estadoPorUf.put(estado.uf(), estado.id()));
        jogadorService.listarPosicoes()
                .forEach(posicao -> contexto.posicaoPorCodigo.put(posicao.codigo(), posicao.id()));
    }

    /**
     * Uma transação por etapa. A carga inteira numa transação seguraria a conexão
     * por minutos; uma por linha multiplicaria o custo por ~1.500 sem ganho.
     */
    private ContagemDeEntidade etapa(Path diretorio, String arquivo, List<String> cabecalho,
                                     Contexto contexto, BiConsumer<LinhaDeCsv, Contador> acao) {
        var linhas = LeitorDeCsv.ler(diretorio.resolve(arquivo), cabecalho);
        var contador = new Contador();
        contador.lidos = linhas.size();
        transactionTemplate.executeWithoutResult(status ->
                linhas.forEach(linha -> acao.accept(linha, contador)));
        var contagem = new ContagemDeEntidade(
                arquivo.replace(".csv", ""),
                contador.lidos, contador.criados, contador.atualizados, contador.recusados);
        contexto.contagens.add(contagem);
        log.info("etapa {}: {} lidos, {} criados, {} atualizados, {} recusados",
                contagem.entidade(), contagem.lidos(), contagem.criados(),
                contagem.atualizados(), contagem.recusados());
        return contagem;
    }

    private int materializarTemporadas(Contexto contexto) {
        var linhas = 0;
        for (var label : contexto.temporadasCarregadas) {
            linhas += avaliacaoService.materializar(label).linhas();
        }
        return linhas;
    }
}
```

- [ ] **Step 6: Implementar as três etapas de referência**

Ainda em `ImportacaoServiceImpl`, o método `executarEtapas` chama as quinze na ordem de dependência. As três abaixo cobrem os três padrões que existem; as outras doze seguem a tabela do Step 7.

```java
    private void executarEtapas(Path diretorio, Contexto contexto) {
        etapaTemporada(diretorio, contexto);
        etapaEstadio(diretorio, contexto);
        etapaClube(diretorio, contexto);
        etapaAlias(diretorio, contexto);
        etapaCompeticao(diretorio, contexto);
        etapaEdicao(diretorio, contexto);
        etapaFase(diretorio, contexto);
        etapaParticipante(diretorio, contexto);
        etapaRegra(diretorio, contexto);
        etapaJogador(diretorio, contexto);
        etapaPosicaoSecundaria(diretorio, contexto);
        etapaAtributo(diretorio, contexto);
        etapaAtributoOculto(diretorio, contexto);
        etapaCaracteristica(diretorio, contexto);
        etapaVinculo(diretorio, contexto);
    }

    // Padrão 1 — sem referência a resolver: lê, sincroniza, guarda o id no mapa.
    private void etapaTemporada(Path diretorio, Contexto contexto) {
        etapa(diretorio, "temporada.csv", List.of("label", "ano_inicio", "ano_fim"), contexto,
                (linha, contador) -> {
                    var label = linha.textoObrigatorio("label");
                    var resultado = temporadaService.sincronizar(new DadosDeTemporada(
                            label, linha.inteiro("ano_inicio"), linha.inteiro("ano_fim")));
                    contexto.temporadaPorLabel.put(label, resultado.id());
                    contexto.temporadasCarregadas.add(label);
                    contador.registrar(resultado);
                });
    }

    // Padrão 2 — referência opcional a catálogo de seed: ausência vira AVISO, não recusa.
    private void etapaClube(Path diretorio, Contexto contexto) {
        etapa(diretorio, "clube.csv",
                List.of("slug", "nome_oficial", "nome_curto", "apelido", "ano_fundacao",
                        "iso_pais", "uf", "estadio_chave", "cor_primaria", "cor_secundaria",
                        "reputacao", "qualidade_base", "uf_base"),
                contexto, (linha, contador) -> {
                    var slug = linha.textoObrigatorio("slug");
                    var estadioChave = linha.texto("estadio_chave");
                    var estadioId = estadioChave == null
                            ? null : contexto.estadioPorChave.get(estadioChave);
                    if (estadioChave != null && estadioId == null) {
                        contexto.ocorrencias.avisar("clube", linha.numero(), slug,
                                "estádio '" + estadioChave + "' não existe no dataset");
                    }
                    var resultado = clubeService.sincronizarClube(new DadosDeClube(
                            slug,
                            linha.textoObrigatorio("nome_oficial"),
                            linha.textoObrigatorio("nome_curto"),
                            linha.texto("apelido"),
                            linha.inteiro("ano_fundacao"),
                            contexto.paisPadraoId,
                            contexto.estadoPorUf.get(linha.texto("uf")),
                            estadioId,
                            linha.texto("cor_primaria"),
                            linha.texto("cor_secundaria"),
                            linha.inteiro("reputacao"),
                            linha.inteiro("qualidade_base"),
                            contexto.estadoPorUf.get(linha.texto("uf_base"))));
                    contexto.clubePorSlug.put(slug, resultado.id());
                    contador.registrar(resultado);
                });
    }

    // Padrão 3 — referência obrigatória a entidade do dataset: ausência recusa a linha.
    private void etapaVinculo(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador_vinculo.csv",
                List.of("jogador_slug", "clube_slug", "temporada", "tipo", "numero_camisa",
                        "data_inicio", "data_fim", "valor_mercado_eur"),
                contexto, (linha, contador) -> {
                    var jogadorSlug = linha.textoObrigatorio("jogador_slug");
                    var clubeSlug = linha.textoObrigatorio("clube_slug");
                    var temporadaLabel = linha.textoObrigatorio("temporada");
                    var jogadorId = contexto.jogadorPorSlug.get(jogadorSlug);
                    var clubeId = contexto.clubePorSlug.get(clubeSlug);
                    var temporadaId = contexto.temporadaPorLabel.get(temporadaLabel);
                    if (jogadorId == null || clubeId == null || temporadaId == null) {
                        contexto.ocorrencias.recusar("jogador_vinculo", linha.numero(),
                                jogadorSlug + "@" + clubeSlug,
                                "jogador, clube ou temporada não encontrados no dataset");
                        contador.recusar();
                        return;
                    }
                    contador.registrar(jogadorService.sincronizarVinculo(new DadosDeVinculo(
                            jogadorId, clubeId, temporadaId,
                            linha.textoObrigatorio("tipo"),
                            linha.inteiro("numero_camisa"),
                            linha.data("data_inicio"),
                            linha.data("data_fim"),
                            linha.decimal("valor_mercado_eur"))));
                });
    }
```

**Por que referência ausente nunca chega ao banco:** o `if` acima resolve tudo em `HashMap`. Uma FK órfã que passasse daqui viraria `DataIntegrityViolationException` dentro do `TransactionTemplate`, marcaria a transação como rollback-only e derrubaria a etapa — comportamento correto, mas indesejável para dado previsivelmente ruim.

- [ ] **Step 7: Implementar as doze etapas restantes**

Cada uma segue um dos três padrões acima. A tabela dá cabeçalho, padrão e destino — não há decisão em aberto:

| Método | Arquivo | Padrão | Referências obrigatórias | Guarda no mapa |
|---|---|---|---|---|
| `etapaEstadio` | `estadio.csv` | 2 | — (`uf` ausente vira AVISO) | `estadioPorChave[chave] = id` |
| `etapaAlias` | `clube_alias.csv` | 3 | `clube_slug` → `clubePorSlug` | — |
| `etapaCompeticao` | `competicao.csv` | 1 | — | `competicaoPorSlug[slug] = id` |
| `etapaEdicao` | `edicao.csv` | 3 | `competicao_slug`, `temporada` | `edicaoPorChave[chaveDeEdicao(...)] = id` |
| `etapaFase` | `fase.csv` | 3 | `competicao_slug` + `temporada` → `edicaoPorChave` | — |
| `etapaParticipante` | `edicao_participante.csv` | 3 | edição + `clube_slug` | — |
| `etapaRegra` | `regra_classificacao.csv` | 3 | edição (`competicao_destino_slug` ausente vira AVISO) | — |
| `etapaJogador` | `jogador.csv` | 1 + derivação | — | `jogadorPorSlug[slug] = id` |
| `etapaPosicaoSecundaria` | `jogador_posicao.csv` | 3 | `jogador_slug`, `posicao` → `posicaoPorCodigo` | — |
| `etapaAtributo` | `jogador_atributo.csv` | 3 | `jogador_slug`, `temporada` | — |
| `etapaAtributoOculto` | `jogador_atributo_oculto.csv` | 3 | `jogador_slug` | — |
| `etapaCaracteristica` | `jogador_caracteristica.csv` | 3 | `jogador_slug`, `caracteristica` (resolvida sob demanda) | — |

`etapaJogador` é o único caso com derivação, e por isso vai escrito por extenso:

```java
    private void etapaJogador(Path diretorio, Contexto contexto) {
        etapa(diretorio, "jogador.csv",
                List.of("slug", "nome_completo", "nome_exibicao", "data_nascimento", "iso_pais",
                        "iso_segunda_nacionalidade", "altura_cm", "peso_kg", "pe_preferido",
                        "posicao_principal", "origem"),
                contexto, (linha, contador) -> {
                    var slug = linha.textoObrigatorio("slug");
                    var codigoDaPosicao = linha.textoObrigatorio("posicao_principal");
                    var posicaoId = contexto.posicaoPorCodigo.get(codigoDaPosicao);
                    if (posicaoId == null) {
                        contexto.ocorrencias.recusar("jogador", linha.numero(), slug,
                                "posição '" + codigoDaPosicao + "' não existe no catálogo");
                        contador.recusar();
                        return;
                    }
                    var nomeCompleto = linha.textoObrigatorio("nome_completo");
                    var nascimento = linha.data("data_nascimento");
                    var isoPais = linha.textoObrigatorio("iso_pais");
                    var chaveNatural = ChaveNatural.de(nomeCompleto, nascimento, isoPais);
                    var resultado = jogadorService.sincronizarJogador(new DadosDeJogador(
                            slug, chaveNatural, ChaveNatural.semente(chaveNatural),
                            nomeCompleto,
                            linha.textoObrigatorio("nome_exibicao"),
                            nascimento,
                            contexto.paisPadraoId,
                            null,
                            linha.inteiro("altura_cm"),
                            linha.inteiro("peso_kg"),
                            linha.textoObrigatorio("pe_preferido"),
                            posicaoId,
                            linha.textoObrigatorio("origem")));
                    contexto.jogadorPorSlug.put(slug, resultado.id());
                    contador.registrar(resultado);
                });
    }
```

`iso_segunda_nacionalidade` chega como `null` porque as fixtures só têm brasileiros; quando um dataset trouxer estrangeiro, este é o ponto a resolver contra `geografiaService.buscarPaisPorIso`.

`etapaCaracteristica` resolve o código sob demanda e memoriza:

```java
    private Long idDaCaracteristica(Contexto contexto, String codigo) {
        return contexto.caracteristicaPorCodigo.computeIfAbsent(codigo,
                chave -> jogadorService.buscarIdCaracteristicaPorCodigo(chave).orElse(null));
    }
```

`computeIfAbsent` com valor `null` não memoriza a ausência — o custo é uma consulta por linha para código inexistente, que só acontece em dataset defeituoso.

- [ ] **Step 8: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Rodar o teste de modularidade**

Run: `./gradlew test --tests '*ModularidadeTest'`
Expected: PASS — `importacao` depende de `avaliacao`, `clube`, `competicao`, `geografia`, `jogador`, `temporada` e `shared`, e de nenhum repository alheio

Se falhar, a causa mais provável é consumo de subpacote sem `@NamedInterface` — confira se as Tasks 1 e 10 criaram todos os `package-info.java` listados.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/br/com/api/footfirma/importacao
git commit -m "feat(importacao): orquestra a carga do dataset em quinze etapas"
```

---

## Task 11: `ExecutorDeImportacao` e o profile `importacao`

O gatilho. `ApplicationRunner` sob profile, para que subir a API normalmente nunca dispare carga.

**Files:**
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/PropriedadesDeImportacao.java`
- Create: `src/main/java/br/com/api/footfirma/importacao/internal/ExecutorDeImportacao.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: `ImportacaoService.importar(Path)` (Task 10).
- Produces: as propriedades `footfirma.importacao.diretorio` (default `fixtures/v1`) e `footfirma.importacao.encerrar-ao-final` (default `true`). Nenhum tipo novo é consumido por outra task.

- [ ] **Step 1: Criar as propriedades**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/PropriedadesDeImportacao.java`:

```java
package br.com.api.footfirma.importacao.internal;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "footfirma.importacao")
record PropriedadesDeImportacao(@NotBlank String diretorio, boolean encerrarAoFinal) {
}
```

- [ ] **Step 2: Criar o runner**

Crie `src/main/java/br/com/api/footfirma/importacao/internal/ExecutorDeImportacao.java`:

```java
package br.com.api.footfirma.importacao.internal;

import br.com.api.footfirma.importacao.ImportacaoService;
import br.com.api.footfirma.importacao.dto.RelatorioDeImportacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Dispara a carga sob o profile {@code importacao} e só sob ele: subir a API
 * normalmente nunca deve escrever no catálogo.
 *
 * <p>FootfirmaApplication não tem {@code @ConfigurationPropertiesScan}, então o
 * registro das propriedades é local. Anotar a classe de aplicação também
 * funcionaria, mas ligaria o scan do sistema inteiro para servir um runner que só
 * roda sob profile.
 */
@Component
@Profile("importacao")
@EnableConfigurationProperties(PropriedadesDeImportacao.class)
@RequiredArgsConstructor
@Slf4j
class ExecutorDeImportacao implements ApplicationRunner {

    private final ImportacaoService importacaoService;
    private final PropriedadesDeImportacao propriedades;
    private final ApplicationContext contexto;

    @Override
    public void run(ApplicationArguments argumentos) {
        var relatorio = importacaoService.importar(Path.of(propriedades.diretorio()));
        registrar(relatorio);

        if (propriedades.encerrarAoFinal()) {
            var codigo = "CONCLUIDA".equals(relatorio.status()) ? 0 : 1;
            System.exit(SpringApplication.exit(contexto, () -> codigo));
        }
    }

    private void registrar(RelatorioDeImportacao relatorio) {
        log.info("Importação {} — dataset {} — status {}",
                relatorio.execucaoId(), relatorio.datasetVersao(), relatorio.status());
        relatorio.contagens().forEach(contagem -> log.info(
                "  {}: {} lidos, {} criados, {} atualizados, {} recusados",
                contagem.entidade(), contagem.lidos(), contagem.criados(),
                contagem.atualizados(), contagem.recusados()));
        log.info("  overall: {} linhas materializadas", relatorio.overallsMaterializados());
        relatorio.ocorrencias().forEach(ocorrencia -> log.warn(
                "  [{}] {} linha {} ({}): {}",
                ocorrencia.severidade(), ocorrencia.entidade(), ocorrencia.linha(),
                ocorrencia.chave(), ocorrencia.motivo()));
        if (relatorio.motivoDaFalha() != null) {
            log.error("  motivo da falha: {}", relatorio.motivoDaFalha());
        }
    }
}
```

Encerrar com código de saída é o comportamento esperado de um job de carga: sem isso a aplicação fica de pé depois de importar, e um script não teria como saber se deu certo. `encerrarAoFinal=false` existe para inspecionar a API logo após a carga.

- [ ] **Step 3: Acrescentar o bloco em `application.properties`**

Em `src/main/resources/application.properties`, ao final:

```properties
# Importação de dataset — o ExecutorDeImportacao só existe sob o profile "importacao".
# Carga: ./gradlew bootRun --args='--spring.profiles.active=importacao'
footfirma.importacao.diretorio=fixtures/v1
footfirma.importacao.encerrar-ao-final=true
```

- [ ] **Step 4: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Verificar que o runner não sobe fora do profile**

Run: `./gradlew test --tests '*FootfirmaApplicationTests'`
Expected: PASS — o smoke test carrega o contexto sem o profile `importacao`, então nenhuma carga é disparada

Não rode `bootRun`: o guard bloqueia. A carga real é a Task 13.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/api/footfirma/importacao/internal/PropriedadesDeImportacao.java \
        src/main/java/br/com/api/footfirma/importacao/internal/ExecutorDeImportacao.java \
        src/main/resources/application.properties
git commit -m "feat(importacao): dispara a carga por ApplicationRunner sob profile"
```

---

## Task 12: Idempotência e ocorrência, com Testcontainers

Os dois testes que sustentam as afirmações centrais do plano.

**Files:**
- Create: `src/test/java/br/com/api/footfirma/importacao/CopiaDeDataset.java`
- Create: `src/test/java/br/com/api/footfirma/importacao/ImportacaoIdempotenciaTest.java`
- Create: `src/test/java/br/com/api/footfirma/importacao/ImportacaoOcorrenciaTest.java`

**Interfaces:**
- Consumes: `ImportacaoService.importar(Path)` e `RelatorioDeImportacao` (Task 10); `fixtures/v1` via `System.getProperty("footfirma.fixtures")` (Task 9).
- Produces: `CopiaDeDataset.copiarComLinhaExtra(Path origem, Path destino, String arquivo, String linha)` e `CopiaDeDataset.copiarSemRegerarManifesto(Path origem, Path destino, String arquivo, String linha)` — helpers reutilizáveis por testes futuros.

Os testes vivem em `importacao` (não em `importacao.internal`) porque consomem apenas a API pública do módulo.

- [ ] **Step 1: Escrever o helper `CopiaDeDataset`**

Crie `src/test/java/br/com/api/footfirma/importacao/CopiaDeDataset.java`:

```java
package br.com.api.footfirma.importacao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;
import java.util.stream.Stream;

/**
 * Monta variações do dataset em diretório temporário. O manifesto é regerado por
 * padrão: um teste de ocorrência que também quebrasse o checksum provaria a coisa
 * errada — abortaria na validação antes de chegar à ocorrência.
 */
final class CopiaDeDataset {

    private CopiaDeDataset() {
    }

    /** Copia tudo, acrescenta a linha ao arquivo indicado e regera o manifesto. */
    static void copiarComLinhaExtra(Path origem, Path destino, String arquivo, String linha)
            throws IOException {
        copiarArquivos(origem, destino);
        var alvo = destino.resolve(arquivo);
        Files.writeString(alvo, Files.readString(alvo, StandardCharsets.UTF_8) + linha + "\n",
                StandardCharsets.UTF_8);
        regerarManifesto(destino);
    }

    /** Copia tudo e acrescenta a linha SEM regerar o manifesto — o checksum passa a divergir. */
    static void copiarSemRegerarManifesto(Path origem, Path destino, String arquivo, String linha)
            throws IOException {
        copiarArquivos(origem, destino);
        var alvo = destino.resolve(arquivo);
        Files.writeString(alvo, Files.readString(alvo, StandardCharsets.UTF_8) + linha + "\n",
                StandardCharsets.UTF_8);
    }

    private static void copiarArquivos(Path origem, Path destino) throws IOException {
        Files.createDirectories(destino);
        try (Stream<Path> arquivos = Files.list(origem)) {
            for (var arquivo : arquivos.toList()) {
                Files.copy(arquivo, destino.resolve(arquivo.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void regerarManifesto(Path diretorio) throws IOException {
        var entradas = new StringJoiner(",\n    ", "[\n    ", "\n  ]");
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            for (var arquivo : arquivos.filter(caminho -> caminho.toString().endsWith(".csv"))
                    .sorted().toList()) {
                entradas.add("{ \\"nome\\": \\"%s\\", \\"linhas\\": %d, \\"sha256\\": \\"%s\\" }"
                        .formatted(arquivo.getFileName(), contarLinhas(arquivo), sha256(arquivo)));
            }
        }
        Files.writeString(diretorio.resolve("manifest.json"), """
                {
                  "schemaVersao": "1",
                  "datasetVersao": "fixtures-v1",
                  "geradoEm": "2026-08-03",
                  "arquivos": %s
                }
                """.formatted(entradas), StandardCharsets.UTF_8);
    }

    private static long contarLinhas(Path arquivo) throws IOException {
        try (Stream<String> linhas = Files.lines(arquivo, StandardCharsets.UTF_8)) {
            return linhas.skip(1).filter(linha -> !linha.isBlank()).count();
        }
    }

    private static String sha256(Path arquivo) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(arquivo)));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", erro);
        }
    }
}
```

- [ ] **Step 2: Escrever `ImportacaoIdempotenciaTest`**

Crie `src/test/java/br/com/api/footfirma/importacao/ImportacaoIdempotenciaTest.java`:

```java
package br.com.api.footfirma.importacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImportacaoIdempotenciaTest {

    private static final Path FIXTURES = Path.of(System.getProperty("footfirma.fixtures"));

    @Autowired
    ImportacaoService importacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void esvaziarCatalogo() {
        jdbcTemplate.update("delete from jogador_overall");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_atributo_oculto");
        jdbcTemplate.update("delete from jogador_caracteristica");
        jdbcTemplate.update("delete from jogador_posicao");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("delete from regra_classificacao");
        jdbcTemplate.update("delete from edicao_participante");
        jdbcTemplate.update("delete from fase");
        jdbcTemplate.update("delete from edicao");
        jdbcTemplate.update("delete from competicao");
        jdbcTemplate.update("delete from clube_alias");
        jdbcTemplate.update("delete from clube");
        jdbcTemplate.update("delete from estadio");
        jdbcTemplate.update("delete from importacao_ocorrencia");
        jdbcTemplate.update("delete from importacao_contagem");
        jdbcTemplate.update("delete from importacao_execucao");
    }

    @Test
    void deveConcluirSemOcorrenciasNaPrimeiraExecucao() {
        var relatorio = importacaoService.importar(FIXTURES);

        assertThat(relatorio.status()).isEqualTo("CONCLUIDA");
        assertThat(relatorio.ocorrencias()).isEmpty();
        assertThat(relatorio.motivoDaFalha()).isNull();
    }

    @Test
    void deveCarregarOitoClubesECentoESetentaESeisJogadores() {
        importacaoService.importar(FIXTURES);

        assertThat(contar("clube")).isEqualTo(8);
        assertThat(contar("jogador")).isEqualTo(176);
        assertThat(contar("jogador_atributo")).isEqualTo(352);
        assertThat(contar("jogador_vinculo")).isEqualTo(352);
    }

    @Test
    void deveCriarZeroRegistrosNaSegundaExecucao() {
        importacaoService.importar(FIXTURES);

        var segunda = importacaoService.importar(FIXTURES);

        assertThat(segunda.status()).isEqualTo("CONCLUIDA");
        assertThat(segunda.contagens()).allSatisfy(contagem ->
                assertThat(contagem.criados())
                        .describedAs("entidade %s criou registros na segunda execução", contagem.entidade())
                        .isZero());
    }

    @Test
    void deveManterAsMesmasContagensDeTabelaNaSegundaExecucao() {
        importacaoService.importar(FIXTURES);
        var jogadoresDepoisDaPrimeira = contar("jogador");
        var vinculosDepoisDaPrimeira = contar("jogador_vinculo");

        importacaoService.importar(FIXTURES);

        assertThat(contar("jogador")).isEqualTo(jogadoresDepoisDaPrimeira);
        assertThat(contar("jogador_vinculo")).isEqualTo(vinculosDepoisDaPrimeira);
    }

    @Test
    void deveMaterializarOverallNasNovePosicoesParaAsDuasTemporadas() {
        importacaoService.importar(FIXTURES);

        assertThat(contar("jogador_overall")).isEqualTo(176 * 9 * 2);
        var zerados = jdbcTemplate.queryForObject(
                "select count(*) from jogador_overall where overall = 0", Integer.class);
        assertThat(zerados).isZero();
    }

    @Test
    void deveRegistrarAExecucaoComODatasetEOStatus() {
        var relatorio = importacaoService.importar(FIXTURES);

        var status = jdbcTemplate.queryForObject(
                "select status from importacao_execucao where id = ?", String.class,
                relatorio.execucaoId());
        var dataset = jdbcTemplate.queryForObject(
                "select dataset_versao from importacao_execucao where id = ?", String.class,
                relatorio.execucaoId());
        var finalizada = jdbcTemplate.queryForObject(
                "select finalizada_em from importacao_execucao where id = ?",
                java.time.OffsetDateTime.class, relatorio.execucaoId());
        assertThat(status).isEqualTo("CONCLUIDA");
        assertThat(dataset).isEqualTo("fixtures-v1");
        assertThat(finalizada).isNotNull();
    }

    @Test
    void deveGravarUmaContagemPorArquivoDoDataset() {
        var relatorio = importacaoService.importar(FIXTURES);

        var contagens = jdbcTemplate.queryForObject(
                "select count(*) from importacao_contagem where execucao_id = ?", Integer.class,
                relatorio.execucaoId());
        assertThat(contagens).isEqualTo(15);
    }

    private Integer contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
```

`"select count(*) from " + tabela` é concatenação em query — permitida aqui porque o valor vem de literal do próprio teste, nunca de entrada externa. Em código de produção continua proibida.

- [ ] **Step 3: Rodar o teste de idempotência**

Run: `./gradlew test --tests '*ImportacaoIdempotenciaTest'`
Expected: PASS — 7 testes verdes

- [ ] **Step 4: Escrever `ImportacaoOcorrenciaTest`**

Crie `src/test/java/br/com/api/footfirma/importacao/ImportacaoOcorrenciaTest.java`:

```java
package br.com.api.footfirma.importacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImportacaoOcorrenciaTest {

    private static final Path FIXTURES = Path.of(System.getProperty("footfirma.fixtures"));
    private static final String VINCULO_ORFAO =
            "jogador-inexistente,clube-inexistente,2026,CONTRATO,7,2026-01-01,2026-12-31,1000000.00";

    @Autowired
    ImportacaoService importacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    Path diretorio;

    @BeforeEach
    void esvaziarCatalogo() {
        jdbcTemplate.update("delete from jogador_overall");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_atributo_oculto");
        jdbcTemplate.update("delete from jogador_caracteristica");
        jdbcTemplate.update("delete from jogador_posicao");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("delete from regra_classificacao");
        jdbcTemplate.update("delete from edicao_participante");
        jdbcTemplate.update("delete from fase");
        jdbcTemplate.update("delete from edicao");
        jdbcTemplate.update("delete from competicao");
        jdbcTemplate.update("delete from clube_alias");
        jdbcTemplate.update("delete from clube");
        jdbcTemplate.update("delete from estadio");
        jdbcTemplate.update("delete from importacao_ocorrencia");
        jdbcTemplate.update("delete from importacao_contagem");
        jdbcTemplate.update("delete from importacao_execucao");
    }

    @Test
    void deveConcluirACargaQuandoUmaLinhaApontaParaClubeInexistente() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        assertThat(relatorio.status()).isEqualTo("CONCLUIDA");
    }

    @Test
    void deveRegistrarExatamenteUmaOcorrenciaDeVinculo() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        assertThat(relatorio.ocorrencias()).hasSize(1);
        assertThat(relatorio.ocorrencias().getFirst().entidade()).isEqualTo("jogador_vinculo");
        assertThat(relatorio.ocorrencias().getFirst().severidade()).isEqualTo("ERRO");
    }

    @Test
    void deveGravarOsDemaisVinculosApesarDaLinhaRecusada() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        importacaoService.importar(diretorio);

        var vinculos = jdbcTemplate.queryForObject(
                "select count(*) from jogador_vinculo", Integer.class);
        assertThat(vinculos).isEqualTo(352);
    }

    @Test
    void deveContarALinhaRecusadaNaContagemDaEntidade() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        var contagem = relatorio.contagens().stream()
                .filter(candidata -> candidata.entidade().equals("jogador_vinculo"))
                .findFirst()
                .orElseThrow();
        assertThat(contagem.lidos()).isEqualTo(353);
        assertThat(contagem.recusados()).isEqualTo(1);
    }

    @Test
    void devePersistirAOcorrenciaComONumeroDaLinha() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        var linha = jdbcTemplate.queryForObject(
                "select linha from importacao_ocorrencia where execucao_id = ?", Integer.class,
                relatorio.execucaoId());
        assertThat(linha).isEqualTo(354);
    }

    @Test
    void deveAbortarSemTocarNoBancoQuandoChecksumDiverge() throws IOException {
        CopiaDeDataset.copiarSemRegerarManifesto(FIXTURES, diretorio, "clube.csv",
                "sinc-intruso,Intruso FC,Intruso,,1950,BRA,SP,,#000000,#FFFFFF,50,50,SP");

        assertThatThrownBy(() -> importacaoService.importar(diretorio))
                .hasMessageContaining("checksum");

        assertThat(jdbcTemplate.queryForObject("select count(*) from clube", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from importacao_execucao", Integer.class)).isZero();
    }
}
```

O último teste confirma a ordem que o plano promete: validação **antes** de escrita. Nenhuma linha em `importacao_execucao` porque a exception sobe de `ValidadorDeDataset`, chamado antes de `registroDeExecucao.abrir`.

- [ ] **Step 5: Rodar o teste de ocorrência**

Run: `./gradlew test --tests '*ImportacaoOcorrenciaTest'`
Expected: PASS — 6 testes verdes

- [ ] **Step 6: Rodar a suíte completa**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — os 74 testes anteriores mais os ~70 novos

- [ ] **Step 7: Commit**

```bash
git add src/test/java/br/com/api/footfirma/importacao
git commit -m "test(importacao): cobre idempotência, ocorrência e checksum divergente"
```

---

## Task 13: Documentação e carga real

**Files:**
- Create: `docs/adr/2026-08-03-ingestao-pelos-modulos.md`
- Create: `docs/runbooks/importacao.md`
- Modify: `backend/footfirma/AGENTS.md`
- Modify: `AGENTS.md` (raiz), seção "Estado atual"
- Modify: `docs/superpowers/specs/2026-08-01-catalogo-futebol-brasileiro-design.md` (raiz)

**Interfaces:**
- Consumes: tudo. Esta task não produz código.
- Produces: nada consumido por outra task.

- [ ] **Step 1: Escrever o ADR**

Crie `docs/adr/2026-08-03-ingestao-pelos-modulos.md`, no formato de `docs/adr/2026-08-01-catalogo-read-only.md`. Registra as decisões 1 e 3:

- **Decisão:** o módulo `importacao` orquestra e não escreve em tabela alheia; cada módulo de catálogo expõe `sincronizar*`. Ocorrência é linha recusada antes do banco; erro de banco aborta a etapa.
- **Por quê:** um módulo que escreve em todas as tabelas torna o `ModularidadeTest` decorativo. E capturar `DataIntegrityViolationException` para "continuar" produz transação *rollback-only* que reporta sucesso e grava nada.
- **Consequências:** ~15 métodos públicos novos; a validação de referência acontece em `HashMap`, não em FK; uma carga que quebra na metade deixa rastro porque `RegistroDeExecucao` usa `REQUIRES_NEW`.
- **Como validar:**

```bash
./gradlew test --tests '*ModularidadeTest' --tests '*ImportacaoIdempotenciaTest'

# Não deve imprimir nada: importacao não acessa repository de outro módulo
grep -rn "Repository" src/main/java/br/com/api/footfirma/importacao/internal/ \
    | grep -v "importacao.repository"
```

Cabeçalho: `Status: verificado em 2026-08-03`, `## Fontes` com os caminhos reais dos arquivos citados.

- [ ] **Step 2: Escrever o runbook**

Crie `docs/runbooks/importacao.md` com: como rodar a carga, como ler o relatório, o que fazer com ocorrência, o que fazer com status `FALHOU`, e as consultas de inspeção:

```sql
select id, dataset_versao, status, iniciada_em, finalizada_em, motivo_da_falha
  from importacao_execucao order by iniciada_em desc limit 5;

select entidade, lidos, criados, atualizados, recusados
  from importacao_contagem where execucao_id = :id order by entidade;

select entidade, linha, chave, severidade, motivo
  from importacao_ocorrencia where execucao_id = :id order by entidade, linha;
```

Mesmo cabeçalho obrigatório de `docs/README.md`.

- [ ] **Step 3: Atualizar o `AGENTS.md` do backend**

Em `backend/footfirma/AGENTS.md`:

- "Seis módulos de domínio existem" → sete, incluindo `importacao`;
- `V1…V13 aplicadas` → `V1…V14`;
- o parágrafo final da seção "Estrutura" afirma que o banco está vazio de clubes e jogadores. Substitua por: o banco continua vazio em base zerada, e a carga de `fixtures/v1` o popula com 8 clubes e 176 jogadores fictícios — ver `docs/runbooks/importacao.md`;
- acrescente à lista "O que nunca fazer": não escrever em tabela de outro módulo a partir de `importacao`.

- [ ] **Step 4: Atualizar o `AGENTS.md` da raiz**

Em `AGENTS.md` (raiz), seção "Estado atual": sete módulos, catorze migrations, dataset de fixtures versionado, e o Plano 3 marcado como **parcialmente executado** — importador e fixtures entregues, pipeline externo não. Atualize a data de verificação.

- [ ] **Step 5: Anotar o status no spec**

Em `docs/superpowers/specs/2026-08-01-catalogo-futebol-brasileiro-design.md` (raiz), acrescente ao topo da seção "Pipeline de dados" uma nota de status: os estágios 1–3 (extract, match, transform) não foram implementados; o estágio 4 (formato de emissão) e o importador foram, sobre fixtures fictícias. Aponte para este plano.

- [ ] **Step 6: Rodar a suíte completa**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit da documentação**

```bash
git add docs backend/footfirma/AGENTS.md AGENTS.md
git commit -m "docs(importacao): registra ADR da ingestão e runbook de carga"
```

- [ ] **Step 8: Pedir a carga real ao usuário**

O guard bloqueia `bootRun`. Peça ao usuário que dispare na própria sessão:

```
! cd backend/footfirma && ./gradlew bootRun --args='--spring.profiles.active=importacao'
```

Esperado no log: `Importação 1 — dataset fixtures-v1 — status CONCLUIDA`, quinze linhas de contagem, `overall: 3168 linhas materializadas`, e a JVM encerrando com código 0.

- [ ] **Step 9: Confirmar que a API deixou de responder vazio**

Com a API de pé (`footfirma.importacao.encerrar-ao-final=false` ou um `bootRun` normal depois da carga), peça ao usuário:

```
! curl -s localhost:8080/api/v1/clubes | head -40
! curl -s 'localhost:8080/api/v1/rankings?temporada=2026&posicao=ATA&size=5'
```

Esperado: 8 clubes na primeira, e cinco atacantes ordenados por overall na segunda — com o Serrano à frente do Fluvial, que é o que prova que os atributos das fixtures têm correlação com a reputação do clube em vez de ruído.

---

## Ordem de execução e paralelismo

Tasks 2–5 dependem só da Task 1 e são independentes entre si. Tasks 6–8 não dependem de nada. A Task 9 depende da 8; a 10, das 2–8; a 12, das 9 e 11.

```
1 ──┬── 2 ──┐
    ├── 3 ──┤
    ├── 4 ──┼──────── 10 ── 11 ──┬── 12 ── 13
    └── 5 ──┤                    │
6 ──────────┤                    │
7 ──────────┤                    │
8 ──────────┴── 9 ───────────────┘
```

## Self-review

Verificações feitas sobre o plano depois de escrito.

**Cobertura do spec (seções "Pipeline de dados" e "Importador"):**

| Requisito do spec | Onde |
|---|---|
| CSVs por entidade + `manifest.json` com versão, data, contagens e SHA-256 | "O formato do dataset"; Tasks 8 e 9 |
| Fixtures fictícias versionadas, consumidas pela suíte | Task 9; usadas nas Tasks 9 e 12 |
| `ApplicationRunner` sob profile `import` | Task 11 — o profile chama-se `importacao`, em português, coerente com o resto |
| Valida checksums e versão do schema antes de tocar no banco | Task 8; provado na Task 12, Step 4 |
| Upsert por chave natural | Tasks 2–5; provado na Task 12 |
| Transação por entidade | Task 10, método `etapa` |
| Ordem de dependência | Task 10, `executarEtapas` |
| Recálculo de `player_overall` ao final | Task 10, `materializarTemporadas` |
| `import_run` com contagens, status e versão do dataset | Task 6 (`importacao_execucao` + `importacao_contagem`), Task 10 |
| `import_issue` com entidade, chave e motivo | Task 6 (`importacao_ocorrencia`), Task 10 |
| Idempotente por construção | Task 12 |
| `dataimport` não escreve em tabela alheia | Tasks 2–5 expõem `sincronizar*`; verificado na Task 13, Step 1 |
| `raw/` e `dataset/` no `.gitignore` | **Não se aplica** — sem pipeline externo, esses diretórios não existem |
| Pipeline Python de quatro estágios, matching entre fontes | **Fora de escopo**, declarado na seção "Escopo" |

**Consistência de tipos:** `ResultadoDeSincronizacao` (Task 1) é o retorno declarado nas Tasks 2–5 e consumido em `Contador.registrar` na Task 10. `LinhaDeCsv` (Task 8) é o parâmetro dos lambdas da Task 10. `Manifesto.SCHEMA_SUPORTADO` (Task 8) é referenciado na Task 9, Step 5. `ContagemDeEntidade` e `OcorrenciaRegistrada` (Task 10) são consumidos por `RegistroDeExecucao` (Task 10) e verificados na Task 12.

**Correções aplicadas durante a revisão:**

- `ChaveNaturalTest`, `LeitorDeCsvTest`, `ValidadorDeDatasetTest`, `FixturesIntegridadeTest` e `GeradorDeFixtures` foram movidos para `importacao/internal/` — as classes que eles usam são package-private, e o pacote precisa bater. `ImportacaoSchemaTest`, `ImportacaoIdempotenciaTest`, `ImportacaoOcorrenciaTest` e `CopiaDeDataset` ficam em `importacao/`, porque só tocam API pública.
- A árvore da seção "Estrutura de arquivos" lista `ImportacaoContagemRepository`, criado na Task 6 e usado por `RegistroDeExecucao`.

**Ponto que continua em aberto, por natureza:** a verificação de que nenhum nome fictício coincide com jogador real (Task 9, Step 4) exige leitura humana. Está marcada como tal no passo, não escondida.

## Critérios de conclusão

- [ ] `./gradlew build` verde
- [ ] `ModularidadeTest` aprova `importacao` dependendo de seis módulos de domínio mais `shared`, sem acessar repository alheio
- [ ] Segunda importação do mesmo dataset cria zero registros em todas as 15 entidades
- [ ] `jogador_overall` com 3.168 linhas (176 × 9 × 2), nenhuma zerada
- [ ] Linha órfã vira ocorrência persistida e a carga conclui
- [ ] Checksum divergente aborta sem gravar nem a linha de execução
- [ ] `fixtures/v1` versionado, íntegro, e revisado à mão contra coincidência com nome real
- [ ] Nenhum endpoint de escrita foi adicionado ao catálogo
- [ ] ADR e runbook escritos; `AGENTS.md` dos dois níveis e o spec atualizados
- [ ] Carga real executada pelo usuário e `/api/v1/clubes` respondendo 8 clubes

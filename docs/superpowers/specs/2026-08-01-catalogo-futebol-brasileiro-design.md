# Catálogo de futebol brasileiro — design

Data: 2026-08-01
Status: aprovado, não implementado

## Contexto

O FootFirma será um jogo de gerenciamento de futebol no estilo Brasfoot: o usuário
comanda um clube e disputa competições reais. O sistema completo se decompõe em seis
subsistemas independentes:

1. **Catálogo + carga de dados reais** ← este spec
2. Motor de simulação de partida
3. Motor de temporada — calendário, tabela, acesso e rebaixamento
4. Mercado de transferências e IA dos clubes
5. UI de gerenciamento
6. Save, perfil e aplicação persistente da progressão

Cada um recebe seu próprio spec, plano e ciclo de implementação. Este documento cobre
apenas o primeiro.

O subsistema 6 merece uma nota, porque a fronteira com este spec é sutil: a v1 entrega
o **modelo** de progressão — tabelas de parametrização e calculadoras puras que, dado um
jogador, devolvem estágio de carreira, projeção de crescimento ou uma safra de base. O
subsistema 6 entrega a **aplicação** desse modelo: avançar temporada, persistir
evolução, acumular histórico.

O backend está em estágio de scaffold: existem `FootfirmaApplication` e
`config/OpenApiConfig`, nenhum módulo de domínio e nenhuma migration Flyway. O que
sair daqui será a primeira migration e o primeiro conjunto de módulos Modulith do
projeto.

## Objetivo

Entregar um catálogo read-only, populado com dados reais do futebol brasileiro, capaz
de sustentar os cinco subsistemas seguintes sem remodelagem.

Escopo de dados na v1: **Série A, Série B e Copa do Brasil**. O modelo suporta a
pirâmide inteira (C, D, estaduais, Libertadores, Sul-Americana), mas a carga se limita
ao que tem dado de qualidade — aproximadamente 60 clubes e 1.200 jogadores.

Fora de escopo: qualquer camada de save ou carreira, motor de simulação, frontend.

## Restrição legal

Nomes de jogadores, clubes e competições reais são propriedade licenciada. Para uso
pessoal e de estudo isso não constitui problema prático; para distribuição, constitui.

Duas consequências no desenho, ambas obrigatórias:

- A camada de nomes é substituível sem tocar nos dados: toda entidade nomeada tem
  `slug` estável e o nome de exibição é coluna comum. Trocar para nomes fictícios é um
  `UPDATE`, não uma remodelagem.
- Nenhum dado derivado de fonte licenciada entra no repositório git. Ver
  "Versionamento e git" na seção do pipeline.

As fontes escolhidas têm fragilidade jurídica conhecida e documentada em "Riscos".

---

## Arquitetura de módulos

Oito módulos Spring Modulith, cada um dono exclusivo das suas tabelas.

| Módulo | Dono de | Depende de |
|---|---|---|
| `season` | Temporada | — |
| `geography` | País, Estado/UF | — |
| `club` | Clube, Alias, Estádio | `geography` |
| `player` | Jogador, Atributos, Traits, Posição, Vínculo com clube | `geography`, `club`, `season` |
| `competition` | Competição, Edição, Fase, Participante, Regra de classificação | `geography`, `club`, `season` |
| `rating` | Perfis de peso, cálculo e materialização de overall | `player` |
| `progression` | Arquétipos, envelhecimento, estágios, projeção, geração de base, lesões, pools de nomes | `player`, `club`, `rating` |
| `dataimport` | Execução de importação e relatório de problemas | orquestra os sete acima |

`season` é um módulo pequeno e sem dependências por necessidade: tanto `player` quanto
`competition` precisam da temporada, e mantê-la dentro de `competition` obrigaria
`player` a depender de `competition` sem razão de domínio.

`rating` e `progression` são separados porque respondem perguntas diferentes: `rating`
diz quanto o jogador vale hoje; `progression` diz como ele chegou aqui e para onde vai.
Fundi-los faria o cálculo de overall — a função mais consultada do sistema — carregar
lesões e pools de nomes junto.

**Regra estrutural: `dataimport` não escreve em tabela alheia.** Cada módulo expõe um
serviço de ingestão na sua API pública (`ClubCatalogService.upsert(...)`,
`PlayerCatalogService.upsert(...)`) e o `dataimport` apenas orquestra. Sem isso o
importador se torna um god object que conhece o schema inteiro, e a verificação de
fronteiras do Modulith deixa de significar alguma coisa.

**Fronteira catálogo / save.** Todos esses módulos são read-only pela API REST; a
única escrita vem do importador. Quando o módulo `career` nascer, ele referencia o
catálogo e escreve somente no que é dele. `ModularityTest` reprova violações.

Estrutura de pacote, por módulo, conforme `.rules/java-core.md`:

```
br/com/api/footfirma/<modulo>/
├── web/         # controller + DTO de request/response
├── domain/      # entidade JPA e tipos de domínio
├── repository/
├── mapper/      # MapStruct
└── internal/    # detalhe não exposto a outros módulos
```

---

## Modelo de dados

### Decisões que carregam justificativa

**1. Atributos são versionados por temporada.** `player_attributes` tem chave
`(player_id, season_id)`. O mesmo jogador em 2025 e 2026 são linhas distintas. Sem
isso, recarregar a base no ano seguinte destrói o histórico — e o modelo de progressão
depende exatamente dessa série temporal para calibração.

**2. Regras de classificação são dados, não código.** `qualification_rule` mapeia
faixa de posição final → destino. Um campeonato com formato novo é um `INSERT`, não
uma classe nova.

**3. `competition_stage` existe desde a v1.** A Copa do Brasil obriga o modelo a
suportar fases, ida e volta, gol fora, prorrogação e pênaltis. É a razão de ela entrar
na carga inicial mesmo custando mais: os dois formatos (pontos corridos e mata-mata)
nascem provados, em vez de a limitação aparecer quando a Libertadores for adicionada.

**4. `external_ref` por módulo, com chave natural explícita.** Não existe ID global de
jogador. Cada módulo tem sua tabela `*_external_ref (entidade_id, fonte, id_externo)` e
uma chave natural definida. Para jogador: `normalizar(nome_completo) + data_nascimento
+ nacionalidade`. É o que torna a reimportação idempotente, e o único ponto do backend
que admite a existência de uma fonte externa.

**5. `player.seed` sustenta a individualidade.** `bigint` derivado de hash estável da
chave natural. Todo atributo oculto e todo ruído de crescimento derivam dele. Mesmo
jogador, qualquer máquina, mesma pessoa gerada.

### Tabelas

**season**

- `season` — `id`, `ano_inicio`, `ano_fim`, `label`

**geography**

- `country` — `id`, `iso_code`, `nome`, `confederacao`
- `state` — `id`, `country_id`, `uf`, `nome`

**club**

- `club` — `id`, `slug`, `nome_oficial`, `nome_curto`, `apelido`, `ano_fundacao`,
  `country_id`, `state_id`, `stadium_id`, `cor_primaria`, `cor_secundaria`,
  `reputacao` (0–99), `youth_rating` (0–99), `youth_region_bias` (`state_id`)
- `club_alias` — `club_id`, `alias`, `fonte` — resolve "Atlético-MG" / "Atlético Mineiro"
  / "Clube Atlético Mineiro" para o mesmo clube
- `stadium` — `id`, `nome`, `cidade`, `state_id`, `capacidade`, `ano_inauguracao`
- `club_external_ref`

**player**

- `player` — `id`, `slug`, `nome_completo`, `nome_exibicao`, `data_nascimento`,
  `country_id`, `segunda_nacionalidade_id`, `altura_cm`, `peso_kg`, `pe_preferido`,
  `posicao_principal_id`, `seed`, `origin` (`REAL` | `GERADO`)
- `player_position` — `player_id`, `position_id`, `ordem` — posição principal e
  secundárias
- `position` — `id`, `codigo` (GOL, ZAG, LTD, LTE, VOL, MEC, MEA, PTA, ATA), `nome`,
  `setor`
- `player_attributes` — `player_id`, `season_id`, as 18 skills, `potential_base`,
  `potential_variance`, `attribute_source` (`IMPORTADO` | `ESTIMADO`), `coletado_em`
- `player_hidden_attributes` — `player_id`, `professionalism`, `ambition`, `loyalty`,
  `temperament`, `leadership`, `consistency`, `injury_proneness`, `pressure_handling`
- `trait` — `id`, `codigo`, `nome`, `categoria`, `descricao`
- `player_trait` — `player_id`, `trait_id`
- `player_club_link` — `player_id`, `club_id`, `season_id`, `tipo`
  (`CONTRATO` | `EMPRESTIMO`), `numero_camisa`, `data_inicio`, `data_fim`,
  `valor_mercado_eur`
- `player_external_ref`

**competition**

- `competition` — `id`, `slug`, `nome`, `country_id` (nulo para continentais),
  `tipo` (`LIGA` | `COPA` | `MISTA`), `tier`, `genero`
- `competition_edition` — `id`, `competition_id`, `season_id`, `nome`, `data_inicio`,
  `data_fim`
- `competition_stage` — `id`, `edition_id`, `ordem`, `nome`,
  `tipo` (`PONTOS_CORRIDOS` | `GRUPOS` | `ELIMINATORIA`), `jogos_por_confronto`,
  `tem_gol_fora`, `tem_prorrogacao`, `tem_penaltis`
- `edition_participant` — `edition_id`, `club_id`, `posicao_final`
- `qualification_rule` — `edition_id`, `pos_inicio`, `pos_fim`, `tipo`
  (`ACESSO` | `REBAIXAMENTO` | `LIBERTADORES_GRUPOS` | `LIBERTADORES_PRE` |
  `SULAMERICANA`), `competition_destino_id`
- `competition_external_ref`

**rating**

- `rating_profile` — `id`, `position_id`, `versao`, `ativo`, `vigente_desde`
- `rating_weight` — `profile_id`, `attribute_code`, `peso`
- `player_overall` — `player_id`, `season_id`, `position_id`, `profile_version`,
  `overall` — materializado pelo importador

**progression**

- `growth_archetype` — `id`, `codigo`, `idade_fim_formacao`,
  `idade_fim_desenvolvimento`, `idade_fim_consolidacao`, `idade_inicio_auge`,
  `idade_fim_auge`, `idade_inicio_veterano`, `multiplicador_declinio`
- `player_growth_archetype` — `player_id`, `growth_archetype_id`
- `attribute_aging_profile` — `attribute_code`, `idade_pico`, `taxa_declinio_anual`,
  `sobe_apos_pico`
- `injury_type` — `id`, `nome`, `gravidade`, `dias_min`, `dias_max`,
  `atributos_afetados`
- `given_name_pool` / `surname_pool` / `nickname_pool` — `nome`, `frequencia`,
  `state_id`, `genero`

`multiplicador_declinio` (arquétipo) e `taxa_declinio_anual` (skill) são grandezas
distintas: a primeira escala o declínio do jogador como um todo, a segunda define quanto
cada skill perde por ano após seu pico. O declínio efetivo é o produto das duas.

---

## Modelo de overall

### As 18 skills

Escala 0–99.

| Família | Skills |
|---|---|
| Físico | Ritmo, Força, Fôlego, Salto, Agilidade |
| Técnica | Passe, Drible, Cruzamento, Frieza |
| Ataque | Finalização, Cabeceio, Falta, Pênalti |
| Defesa | Desarme, Marcação |
| Goleiro | Reflexos, Posicionamento, Manejo |

O mapeamento a partir dos ~35 atributos do EA FC vive **no ETL**, declarado em
`attribute_mapping.yaml`. Exemplos: `Ritmo = média(sprint_speed, acceleration)`,
`Finalização = ponderada(finishing, shot_power, volleys)`,
`Marcação = média(defensive_awareness, interceptions)`,
`Agilidade = média(agility, balance, reactions)`.

O domínio Java nunca vê um campo com nome da EA. Trocar de fonte altera apenas o YAML.

Skills de goleiro existem em todo jogador — o EA FC as fornece —, mas os perfis de
linha lhes atribuem peso 0. Sem `NULL`, sem caso especial no cálculo.

### Overall é função, não dado

```
overall(jogador, posição) = Σ (atributo_i × peso_i(posição))
```

Nunca uma coluna escrita à mão. Consequências: o mesmo zagueiro pontua diferente como
lateral; rebalancear o jogo inteiro não toca em nenhum registro de jogador; o cálculo é
testável isoladamente.

Nove perfis, um por posição, com pesos somando exatamente 1.0. Perfil de atacante como
referência:

| Skill | Peso |
|---|---|
| Finalização | 0.24 |
| Ritmo | 0.14 |
| Drible | 0.12 |
| Cabeceio | 0.10 |
| Frieza | 0.10 |
| Força | 0.08 |
| Passe | 0.07 |
| Agilidade | 0.07 |
| Salto | 0.05 |
| Fôlego | 0.03 |

**Rebalancear cria um perfil novo; nunca `UPDATE`.** `rating_profile` tem `versao` e
`ativo`. Isso permite comparar equilíbrio antes e depois e, quando existirem saves em
andamento, impede que uma carreira mude de regra no meio.

`OverallCalculator` é função pura: sem Spring, sem banco. O resultado é materializado
em `player_overall` pelo importador, para que consultas como "melhores atacantes da
Série A" usem índice em vez de full scan com aritmética.

---

## Modelo de progressão

Especificado aqui como contrato. A v1 implementa as tabelas e as calculadoras puras;
a aplicação persistente pertence ao spec de carreira.

### Estágios

Sete estágios, **derivados** de `(idade, growth_archetype)` — nunca coluna:

`FORMACAO` → `DESENVOLVIMENTO` → `CONSOLIDACAO` → `APROXIMACAO_AUGE` → `AUGE` →
`INICIO_DECLINIO` → `VETERANO`

### Arquétipos de crescimento

| Arquétipo | Perfil |
|---|---|
| `PRECOCE` | explode aos 19, auge 22–26, declina cedo |
| `NORMAL` | auge 26–30 |
| `TARDIO` | desenvolve devagar, auge 28–32 |
| `DURADOURO` | auge longo, declínio muito lento |
| `FRAGIL` | teto alto, auge curto, alta propensão a lesão |

### Fórmula

```
ganho_temporada = taxa_base(estágio, arquétipo)
                × (gap / 100)
                × fator_minutos
                × fator_moral
                × fator_profissionalismo
                × ruído(seed, temporada)

gap = potencial_dinâmico − overall_atual
```

O termo `(gap / 100)` produz a não-linearidade sem nenhuma regra explícita: conforme o
jogador se aproxima do potencial, o ganho tende a zero. Potencial é teto assintótico,
não destino — não se chega a ele.

Trajetória de referência, jogador de 17 anos com overall 60 e potencial 90:

| Idade | Estágio | Com minutos | Sem minutos |
|---|---|---|---|
| 17–18 | Formação | 60 → 64 | 60 → 61 |
| 19–21 | Desenvolvimento | 64 → 81 | 61 → 66 |
| 22–24 | Consolidação | 81 → 88 | 66 → 71 |
| 25–27 | Aproximação do auge | 88 → 89,4 | 71 → 72 |
| 28–31 | Auge | estável | estável |
| 32–34 | Início do declínio | físico cai primeiro | — |
| 35+ | Veterano | só técnica e mental resistem | — |

`fator_minutos` é o termo de maior amplitude do modelo, por decisão de design: tempo de
jogo é o principal motor de desenvolvimento.

### Envelhecimento por skill

`attribute_aging_profile` faz o declínio diferenciado emergir do modelo, sem
condicional no código:

| Skill | Idade de pico | Declínio |
|---|---|---|
| Ritmo, Agilidade | 24–25 | forte |
| Salto, Fôlego | 26 | moderado |
| Força | 28 | leve |
| Finalização, Passe, Cruzamento | 30 | muito leve |
| Marcação | 31 | nenhum |
| Frieza | 33 | nenhum — continua subindo |

### Declínio reversível

O envelhecimento reduz uma base permanente. Sobre ela incide um modificador de condição
recuperável: um veterano relegado ao banco cai mais rápido do que a idade justificaria e
recupera parte disso ao retomar minutos regulares e boa forma. O envelhecimento real
nunca é desfeito.

### Potencial base e dinâmico

O catálogo armazena `potential_base` e `potential_variance`. A carreira sorteia o
potencial real dentro dessa banda usando o seed do save, de modo que o mesmo jovem tenha
tetos diferentes em saves diferentes. O potencial dinâmico se move dentro da banda
conforme desempenho, minutos e moral.

### Atributos ocultos

`professionalism`, `ambition`, `loyalty`, `temperament`, `leadership`, `consistency`,
`injury_proneness`, `pressure_handling` — todos 0–99, estáveis, gerados a partir do
`seed`.

São gerados proceduralmente **por decisão deliberada**, não por limitação. O EA FC não
os fornece, e atribuir "profissionalismo 40" ou "temperamento explosivo" a uma pessoa
real e nomeada é emitir juízo sobre o caráter dela, não sobre como ela finaliza. Ficam
marcados como `ESTIMADO`.

---

## Jogadores de base gerados

As instâncias pertencem ao save. O material de geração é catálogo:

- **Pools de nomes do IBGE** — base pública, licença limpa, com frequência real e
  distribuição por estado. Os nomes soam brasileiros porque são brasileiros, e nenhum
  corresponde a uma pessoa real identificável.
- `club.youth_rating` e `club.youth_region_bias` — bases distintas produzem safras
  distintas, em qualidade e em origem regional.
- Distribuição de overall e potencial condicionada à reputação do clube e ao arquétipo
  sorteado.

Jogador gerado e jogador real compartilham a mesma estrutura, distinguidos por
`player.origin`. O gerador é serviço puro e determinístico: dado um seed, sempre a mesma
safra. Isso o torna testável na v1, antes de existir save onde persistir.

---

## Pipeline de dados

`data-pipeline/` na raiz do monorepo, em Python. Quatro estágios com artefato em disco
entre cada um — cada estágio roda, é inspecionado e re-executado isoladamente.

### 1. extract → `raw/<fonte>/<temporada>/`

Coleta crua, sem transformação, com data de coleta registrada.

Fontes: dataset EA FC (atributos, traits, potencial) e Transfermarkt (elencos, valor de
mercado, cobertura das divisões inferiores).

### 2. match → casamento EA FC ↔ Transfermarkt

Bloqueia por clube e nacionalidade; pontua por nome normalizado (rapidfuzz), data de
nascimento e posição. Três faixas: alta confiança aceita automaticamente, média envia
para `review/pending.csv`, baixa rejeita.

Decisões manuais ficam em `overrides.yaml` **versionado** — um caso resolvido
permanece resolvido entre execuções.

**Este é o estágio de maior risco do projeto** e o único com intervenção humana prevista
no desenho. Um jogador que não casa gera uma linha em `import_issue`; nunca uma falha
silenciosa nem um registro duplicado.

### 3. transform

Aplica `attribute_mapping.yaml` (35 → 18), normaliza nomes de clube via
`club_aliases.yaml`, resolve posições, gera `seed` e atributos ocultos, deriva
`growth_archetype`.

### 4. emit → `dataset/v<n>/`

CSVs por entidade mais `manifest.json` com versão do schema, data de geração, contagens
por entidade e checksum SHA-256 de cada arquivo.

### Versionamento e git

**Versionado:** o código do pipeline, `attribute_mapping.yaml`, `club_aliases.yaml`,
`overrides.yaml`, `manifest.json`.

**No `.gitignore`:** `raw/` e `dataset/` — dado derivado de fonte licenciada não entra
no repositório.

**`fixtures/`:** dataset pequeno e deliberadamente **fictício** (3 competições, 8
clubes, 60 jogadores inventados), versionado, consumido pela suíte de testes. Resolve
licença e determinismo de teste com a mesma decisão.

### Importador

`ApplicationRunner` sob profile `import`. Valida checksums e versão do schema do dataset
antes de tocar no banco. Upsert por chave natural, em transação por entidade, na ordem
de dependência:

```
season → geography → competition → stadium → club → player
→ attributes → traits → hidden_attributes → growth_archetype → club_links
→ editions → stages → participants → qualification_rules
→ recálculo de player_overall
```

Registra `import_run` (contagens, status, versão do dataset) e `import_issue` (entidade,
chave natural, motivo) por ocorrência.

Idempotente por construção: duas execuções sobre o mesmo dataset produzem estado
idêntico.

O guard do repositório bloqueia subir a aplicação. A carga é disparada pelo usuário na
própria sessão com `! <comando>`.

---

## API REST

Read-only, `/api/v1`, DTOs (nunca `@Entity`), paginação Spring, documentada no Swagger.

```
GET /api/v1/competitions?country=BR
GET /api/v1/competitions/{slug}/editions/{season}
GET /api/v1/clubs/{slug}
GET /api/v1/clubs/{slug}/squad?season=2025
GET /api/v1/players/{slug}?season=2025
GET /api/v1/players?position=ATA&minOverall=80&page=0&size=20
```

`GET /api/v1/players/{slug}` retorna dados pessoais, as 18 skills, traits, overall por
posição, estágio de carreira atual e projeção de crescimento — esta última calculada
pelas calculadoras puras, sem persistência.

---

## Testes

| Teste | Prova |
|---|---|
| `OverallCalculatorTest` | puro, sem Spring: pesos somam 1.0, overall em 0–99, mesmo zagueiro pontua diferente como lateral |
| `RatingProfileIntegrityTest` | todo perfil ativo soma exatamente 1.0 — pega peso quebrado no seed |
| `CareerStageCalculatorTest` | transições de estágio corretas por idade e arquétipo, incluindo as bordas |
| `GrowthProjectionTest` | reproduz a trajetória de referência 60→90; jogador sem minutos estaciona; potencial nunca é atingido |
| `YouthPlayerGeneratorTest` | determinismo por seed; distribuição coerente com `youth_rating`; nomes vindos das pools |
| `ImportIdempotencyTest` | Testcontainers + fixtures: importar duas vezes produz contagens idênticas e zero duplicado |
| `ImportIssueTest` | jogador ambíguo na fixture vira `import_issue` e a carga prossegue |
| `ModularityTest` | Spring Modulith reprova violação de fronteira entre módulos |
| pytest no matcher | homônimos, acentos, "Vitor"/"Victor", nomes compostos |

Persistência é testada com Testcontainers, conforme `.rules/java-testing.md`. Sem H2,
sem mock de repository.

---

## Riscos

**1. O matching entre fontes vai errar.** Não é hipótese: com ~1.200 jogadores é certeza
estatística. Mitigado por overrides versionados e por falha visível em vez de silenciosa.
Espere gastar tempo real revisando casos na primeira carga — o desenho torna esse
trabalho cumulativo, não recorrente.

**2. As fontes são frágeis juridicamente.** O Transfermarkt proíbe scraping em seus
termos de uso, e os datasets de EA FC disponíveis publicamente são reuploads não
oficiais. Para uso pessoal o problema é teórico; para publicação, não é. O desenho isola
isso inteiramente no ETL, de modo que trocar de fonte não toque no domínio.

**3. A cobertura de Série B no EA FC é parcial.** Uma fração dos jogadores virá sem
skills. Mitigação: `attribute_source = ESTIMADO`, com estimativa derivada de idade,
posição e valor de mercado. O dado inventado fica marcado como inventado em vez de se
disfarçar de real.

**4. O balanceamento da progressão não tem verdade de referência.** Os pesos e taxas
propostos são ponto de partida plausível, não valores calibrados. As calculadoras puras
existem na v1 exatamente para permitir rodar a curva de jogadores reais e ajustar antes
de o motor de temporada depender delas.

---

## Fora de escopo

Camada de save e carreira · motor de simulação de partida · calendário e tabela ·
mercado de transferências · IA de clubes · frontend · Séries C e D · campeonatos
estaduais · Libertadores e Sul-Americana · competições femininas.

O modelo suporta todos esses itens sem remodelagem; apenas não são carregados nem
implementados nesta entrega.

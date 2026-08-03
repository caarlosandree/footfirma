# Avaliação e overall — design

Data: 2026-08-02
Status: aprovado, não implementado

## Contexto

O spec `2026-08-01-catalogo-futebol-brasileiro-design.md` decompõe o FootFirma em seis
subsistemas e cobre o primeiro: catálogo e carga de dados reais. Esse subsistema foi
fatiado em três planos:

| | Entrega | Estado |
|---|---|---|
| Plano 1 | Schema e API read-only do catálogo | executado em 2026-08-02 |
| Plano 2 | Avaliação (overall) e progressão | este spec cobre a avaliação |
| Plano 3 | Pipeline Python e importador | depende do Plano 2 |

O Plano 1 entregou cinco módulos Modulith (`temporada`, `geografia`, `clube`,
`jogador`, `competicao`), onze migrations Flyway e três controllers read-only. O banco
tem o schema completo e os seeds de país, estado, posição e característica; está vazio
de clubes e de jogadores.

**O que o spec original chamava de "Plano 2" são dois subsistemas de tamanhos muito
diferentes.** A avaliação é fechada: nove perfis de peso, uma função pura, uma tabela
materializada. A progressão é grande e, por admissão do próprio spec (Risco 4), não tem
verdade de referência para calibração enquanto não houver dado real carregado. Tratá-los
como um ciclo só produziria um plano longo cuja segunda metade depende de números que
ninguém consegue validar ainda.

Este documento cobre **apenas a avaliação**. A progressão recebe spec próprio.

## Objetivo

Entregar o módulo `avaliacao`: perfis de peso versionados, cálculo de overall como função
pura e materialização em tabela indexada, com exposição read-only na API.

Fora de escopo: progressão, arquétipos de crescimento, envelhecimento por skill, geração
de jogadores de base, ETL e qualquer dado real no banco.

## Por que a avaliação vem antes do importador

A ordem de carga descrita no spec do catálogo termina com "recálculo de
`player_overall`". O importador do Plano 3 não fecha sem o calculador de overall
existir. Inverter a ordem significa entregar o importador e reabri-lo depois.

A consequência aceita é que, em banco real, os endpoints deste ciclo respondem lista
vazia e 404 até a carga acontecer. Os testes provam o comportamento com fixtures; a API
só fica útil depois do Plano 3. Isso é consequência da ordem escolhida, não descuido.

## Desvios reconhecidos do spec do catálogo

Dois, ambos deliberados:

**1. `avaliacao` depende de `jogador` e `temporada`; o spec original declarava apenas
`player`.** A dependência de `temporada` vem da chave estrangeira `temporada_id` em
`jogador_overall` e da resolução do label de temporada recebido pela API. Ela é real e
inevitável, não conveniência.

**2. A materialização muda de dono.** O spec original diz que o overall é "materializado
em `player_overall` pelo importador". Aqui a materialização é
`AvaliacaoService.materializar(...)`, e o importador do Plano 3 apenas a invoca. É a
aplicação correta da regra estrutural que o próprio spec estabelece — "`dataimport` não
escreve em tabela alheia" — mas é uma mudança de desenho, não uma continuidade.

---

## Arquitetura de módulos

Módulo novo: `br.com.api.footfirma.avaliacao`, com
`@ApplicationModule(displayName = "Avaliação")`. Depende de `jogador` e `temporada`.
Nada depende dele.

```
avaliacao/
├── package-info.java              # @ApplicationModule(displayName = "Avaliação")
├── AvaliacaoService.java          # única porta pública do módulo
├── dto/                           # AvaliacoesDoJogador, ItemDeRanking, ResultadoMaterializacao
├── domain/                        # PerfilAvaliacao, PerfilAvaliacaoPeso, JogadorOverall, AtributoAvaliavel
├── repository/
├── mapper/
├── web/                           # AvaliacaoController (package-private)
└── internal/                      # AvaliacaoServiceImpl, CalculadoraDeOverall
```

A porta pública do módulo, inteira:

```java
public interface AvaliacaoService {

    Optional<AvaliacoesDoJogador> buscarPorSlug(String slug, String labelTemporada);

    Page<ItemDeRanking> ranquear(String labelTemporada, String codigoPosicao,
                                 Integer overallMinimo, Pageable pageable);

    ResultadoMaterializacao materializar(String labelTemporada);
}
```

Os dois primeiros métodos servem os endpoints REST; o terceiro é chamado pelos testes
neste ciclo e pelo importador no Plano 3.

O DTO se chama `AvaliacoesDoJogador`, no plural, porque devolve as nove posições. O
singular induziria a esperar um número só.

### A fronteira com `jogador`

`avaliacao` precisa de quatro coisas que vivem no módulo `jogador`: os atributos para
calcular, os ids para gravar a chave estrangeira, os nomes para montar o ranking e as
posições para traduzir código em id. Nada disso pode ser lido por consulta direta —
`JogadorAtributoRepository` e `PosicaoRepository` vivem em `jogador/repository/`,
invisíveis fora do módulo, e entidade JPA não cruza fronteira.

A regra aplicada é a simétrica da que o spec do catálogo já impõe ao importador
("`dataimport` não escreve em tabela alheia"): **`avaliacao` não lê tabela alheia, lê
pelo serviço.** Um join JPQL contra `jogador` ou `posicao` está proibido pela mesma
regra que proíbe o importador de escrever nelas.

Isso custa quatro métodos novos em `JogadorService`. O custo é reconhecido e aceito: é o
preço da fronteira, e cada método tem função única.

```java
// Materialização: varredura paginada dos atributos de uma temporada.
Page<JogadorComAtributos> listarAtributosPorTemporada(String labelTemporada, Pageable pageable);

// Ranking: resolve uma página inteira de ids de uma vez. Evita o N+1 que
// chamar buscarPorSlug por item produziria.
List<JogadorResumo> listarResumosPorIds(Collection<Long> ids, String labelTemporada);

// Traduz codigo <-> id nos dois sentidos: "ATA" na query, e o código de volta
// na resposta. São nove linhas fixas de catálogo, carregadas de uma vez.
List<PosicaoCatalogo> listarPosicoes();

// Endpoint por slug: resolve o identificador público para a FK, sem carregar
// o JogadorDetalhe inteiro.
Optional<Long> buscarIdPorSlug(String slug);
```

Records novos em `jogador/dto/`:

- `JogadorComAtributos(Long jogadorId, AtributosJogador atributos)`
- `PosicaoCatalogo(Long id, String codigo, String nome, String setor)`

`JogadorResumo` **já existe e já expõe `id`** — o ranking o reaproveita sem alteração.
Isso também é a prova de que o id circulando entre módulos não é exceção aberta por este
spec: é a prática que o Plano 1 já estabeleceu. O identificador público em resposta REST
continua sendo o slug.

Uma observação sobre `JogadorResumo.posicao`: ele traz a posição **principal** do
jogador, que pode divergir da posição **avaliada** no ranking. Um volante ranqueado como
zagueiro aparece com `posicao = "VOL"` e overall de ZAG. `ItemDeRanking` carrega as duas
explicitamente para que isso seja legível em vez de confuso.

### Pré-requisito de compilação: `@NamedInterface` em `jogador/dto`

Hoje só `clube/dto` e `temporada/dto` têm `package-info.java` com `@NamedInterface`.
Sem criar `jogador/dto/package-info.java`, o Modulith trata o subpacote como interno e o
`ModularidadeTest` reprova `avaliacao` no primeiro build.

Isto não é detalhe de implementação: é a primeira coisa a fazer no módulo `jogador`, e
sua ausência quebra o build antes de qualquer linha de `avaliacao` ser escrita.

### Fronteira com o catálogo

O ADR `2026-08-01-catalogo-read-only.md` continua valendo sem alteração. `avaliacao`
escreve apenas na tabela que é dele, e o `grep` por `PostMapping|PutMapping|PatchMapping|
DeleteMapping` nos pacotes `web/` continua voltando vazio.

O `JogadorController` **não muda**. Se ele passasse a devolver overall, `jogador`
dependeria de `avaliacao` e a dependência declarada se inverteria — rebalancear pesos
passaria a mexer no contrato do catálogo. O overall é servido por controller próprio do
módulo `avaliacao`, que já pode ler `jogador`.

Esta decisão fecha o ciclo com um ADR próprio, como o ciclo do catálogo fez com o seu.

---

## Modelo de dados

Duas migrations: `V12__cria_avaliacao.sql` (estrutura) e
`V13__popula_perfil_avaliacao.sql` (seed dos nove perfis, versão 1).

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

-- Sustenta o ranking sem full scan com aritmética. jogador_id entra no índice
-- porque o ORDER BY precisa dele como desempate estável.
create index idx_jogador_overall_ranking
    on jogador_overall (temporada_id, posicao_id, overall desc, jogador_id);
```

Quatro decisões que carregam justificativa:

**1. A soma dos pesos igual a 1.0 não vira `check`.** Não é expressável como restrição de
linha. Fica em `PerfilAvaliacaoIntegridadeTest`, que reprova qualquer perfil ativo cuja
soma não seja exatamente 1.0000. Pega peso quebrado no seed antes de ele virar overall
errado.

**2. `perfil_versao` é gravada junto do overall, não inferida.** Sem ela não há como saber
com qual balanceamento uma linha foi calculada, e o versionamento de perfil perde a
função.

**3. Skills de goleiro recebem peso 0 nos perfis de linha, com linha explícita.** Todo
perfil tem exatamente 18 linhas de peso. Sem `NULL`, sem caso especial no calculador.

**4. `vigente_desde` é informativa e não participa de nenhuma regra.** A seleção do perfil
é por `ativo`. A coluna existe para auditar quando um rebalanceamento entrou em vigor, e
o comentário no schema diz isso — para que a próxima leitura não a promova a regra
implícita de vigência temporal.

### Abrangência da materialização

Nove linhas por jogador e temporada — uma por posição, inclusive as que ele não joga.
Cerca de dez mil linhas para os 1.200 jogadores previstos, volume que o plano do catálogo
já antecipou ao escolher a estratégia de id.

O motivo é o subsistema 4: mercado de transferências e IA de clubes perguntam "esse
volante serve como zagueiro?", e a resposta fica pronta em índice em vez de exigir
cálculo sob demanda. É também o que torna verificável a consequência que o spec do
catálogo destaca — o mesmo zagueiro pontuar diferente como lateral.

---

## Modelo de overall

```
overall(jogador, posição) = Σ (atributo_i × peso_i(posição))
```

Nunca uma coluna escrita à mão.

### Os nove perfis, versão 1

Cada coluna soma exatamente 1.0000. `—` significa peso 0, gravado explicitamente.

| Atributo | GOL | ZAG | LTD | LTE | VOL | MEC | MEA | PTA | ATA |
|---|---|---|---|---|---|---|---|---|---|
| Ritmo | — | 0.06 | 0.14 | 0.14 | 0.03 | 0.05 | 0.09 | 0.20 | 0.14 |
| Força | — | 0.14 | 0.04 | 0.04 | 0.11 | 0.03 | 0.03 | 0.03 | 0.08 |
| Fôlego | — | 0.01 | 0.13 | 0.13 | 0.12 | 0.09 | 0.05 | 0.05 | 0.03 |
| Salto | 0.02 | 0.09 | — | — | — | — | — | — | 0.05 |
| Agilidade | 0.08 | 0.02 | 0.07 | 0.07 | 0.04 | 0.11 | 0.12 | 0.14 | 0.07 |
| Passe | 0.03 | 0.07 | 0.10 | 0.10 | 0.17 | 0.26 | 0.19 | 0.08 | 0.07 |
| Drible | — | — | 0.06 | 0.06 | 0.02 | 0.15 | 0.18 | 0.20 | 0.12 |
| Cruzamento | — | — | 0.15 | 0.15 | — | 0.07 | 0.07 | 0.12 | — |
| Frieza | 0.05 | 0.04 | 0.02 | 0.02 | 0.07 | 0.13 | 0.14 | 0.07 | 0.10 |
| Finalização | — | — | — | — | — | 0.07 | 0.13 | 0.11 | 0.24 |
| Cabeceio | — | 0.13 | — | — | 0.05 | — | — | — | 0.10 |
| Falta | — | — | — | — | — | — | — | — | — |
| Pênalti | — | — | — | — | — | — | — | — | — |
| Desarme | — | 0.20 | 0.13 | 0.13 | 0.19 | 0.04 | — | — | — |
| Marcação | — | 0.24 | 0.16 | 0.16 | 0.20 | — | — | — | — |
| Gol/Reflexos | 0.30 | — | — | — | — | — | — | — | — |
| Gol/Posicionamento | 0.28 | — | — | — | — | — | — | — | — |
| Gol/Manejo | 0.24 | — | — | — | — | — | — | — | — |

O perfil de atacante é o do spec do catálogo, reproduzido sem alteração. Os outros oito
são desenhados aqui.

### Quatro decisões de balanceamento

**Falta e Pênalti pesam 0 nos nove perfis.** São habilidades de especialista, não medida
de qualidade. O motor de simulação vai usá-las para escolher quem cobra; deixá-las pesar
no overall faria um batedor de falta mediano parecer melhor jogador do que é. O dado
existe no catálogo e continua servindo — só não entra nesta conta.

**LTD e LTE têm pesos idênticos, deliberadamente.** A diferença entre lateral direito e
esquerdo é pé preferido, que já vive em `jogador.pe_preferido`, e não um conjunto
diferente de habilidades. O registro existe para que a próxima leitura da tabela não
interprete a repetição como descuido e a "corrija".

**O perfil de goleiro dá 82% do peso às três skills de goleiro.** Os 18% restantes
(agilidade, frieza, passe, salto) impedem que goleiros com o mesmo trio empatem, e fazem
o goleiro que joga com os pés valer mais.

**Sem curva de normalização.** O overall é a média ponderada crua, arredondada para
inteiro. Isso comprime a distribuição: um jogador de elite raramente é bom em tudo, então
tende a sair na casa dos 80 em vez de 90 e poucos. A compressão é aceita nesta versão por
três razões — o número fica auditável, o versionamento de perfil já é o mecanismo de
correção, e não existe dado real para calibrar uma curva contra coisa alguma. Quando o
Plano 3 carregar os 1.200 jogadores, a distribuição real é medida; se ficar chata, isso
vira `versao = 2`, sem tocar em nenhum registro de jogador.

### Rebalancear cria versão, nunca `UPDATE`

`perfil_avaliacao` tem `versao` e `ativo`. Alterar pesos de um perfil ativo mudaria o
equilíbrio de carreiras em curso quando o subsistema de save existir; e, antes disso,
impossibilitaria comparar o antes e o depois de um rebalanceamento.

### O calculador

`CalculadoraDeOverall` é função pura: sem Spring, sem banco, sem repositório. Recebe
`AtributosJogador` e o perfil de pesos; devolve `int`.

`AtributosJogador` já existe em `jogador/dto/` e tem **20 componentes**: as 18 skills mais
`potencialBase` e `fonteAtributo`. As skills são `Integer` (boxed), não primitivos. O
calculador usa apenas as 18; `potencialBase` pertence ao modelo de progressão e
`fonteAtributo` à procedência do dado.

Cruzar a fronteira com esse record é o propósito da API pública do módulo. Um DTO espelho
em `avaliacao`, só para traduzir, seria cerimônia sem ganho.

**Aritmética em `BigDecimal`, arredondamento `HALF_UP`.** Os pesos são `numeric(5,4)` e
chegam como `BigDecimal`; a soma ponderada permanece em `BigDecimal` e só vira `int` no
fim, com `setScale(0, RoundingMode.HALF_UP)`. Fazer a conta em `double` introduziria erro
de representação numa soma de dezoito parcelas — pequeno, mas suficiente para tornar o
resultado dependente da ordem das parcelas, e para fazer o teste de integridade da soma
igual a 1.0 falhar por motivo errado. Pelo mesmo motivo, comparações de `BigDecimal` nos
testes usam `compareTo`, não `equals`.

Um atributo `null` é erro de dado, não caso a tratar: `jogador_atributo` declara as 18
colunas como `not null`. O calculador falha alto se receber `null`, em vez de assumir
zero e produzir um overall silenciosamente errado.

`AtributoAvaliavel` carrega a própria extração, em vez de um `switch` de 18 casos:

```java
RITMO(AtributosJogador::ritmo),
FORCA(AtributosJogador::forca),
...
GOL_MANEJO(AtributosJogador::golManejo);
```

O ganho é que não existe caminho onde o código compile com um atributo declarado no enum
e ausente da conta. Adicionar uma skill continua custando quatro coisas — migration
alterando o `check`, nove linhas de seed por perfil ativo, a constante do enum e o campo
em `AtributosJogador` —; o que o desenho elimina é o esquecimento silencioso, não o
trabalho.

O teto de 99 não precisa de `clamp` defensivo: com atributos limitados a 99 e pesos
somando 1.0, ultrapassá-lo é matematicamente impossível. Isso vira teste, não `if`.

---

## Materialização

```java
ResultadoMaterializacao materializar(String labelTemporada);
```

Percorre os jogadores em páginas, com **transação por lote e não uma transação única de
dez mil linhas**, e faz upsert por `(jogador_id, temporada_id, posicao_id)`. Rodar duas
vezes sobre o mesmo estado produz resultado idêntico.

**A varredura é ordenada por `jogador.id` ascendente.** Sem ordenação total e estável, a
paginação entre commits de lote pode pular ou repetir uma página — e o resultado passaria
a depender do plano de execução do Postgres. O upsert torna a repetição inofensiva, mas
não a omissão.

**Não é exposta por REST.** Se virasse endpoint, seria um `POST` no catálogo e derrubaria
a verificação do ADR. Neste ciclo quem a chama são os testes; no Plano 3, o importador,
como último passo da ordem de carga.

---

## API REST

Read-only, `/api/v1`, DTOs, paginação Spring, documentada no Swagger.

```
GET /api/v1/jogadores/{slug}/overall?temporada=2025
GET /api/v1/rankings?temporada=2025&posicao=ATA&overallMinimo=80&page=0&size=20
```

O primeiro devolve o overall nas nove posições, ordenado do maior para o menor, com a
versão de perfil usada. O segundo devolve uma página de jogadores ordenada por overall
decrescente na posição pedida.

**O ranking é global por temporada, sem filtro de competição.** Responder "os melhores
atacantes da Série A" exigiria atravessar `competicao` (para saber quem disputou a
edição) e `jogador` (para saber em que clube o jogador estava), o que multiplica a
fronteira deste ciclo por dois. Fica de fora; o índice comporta o filtro quando ele for
necessário.

**A ordenação é `overall desc, jogador_id asc`.** Só por overall, dezenas de empates
tornariam a paginação não determinística — o mesmo jogador poderia aparecer em duas
páginas ou em nenhuma. O desempate é arbitrário de propósito; o que importa é ser total e
estável.

O ranking fica em `/api/v1/rankings`, **não** em `/api/v1/jogadores/ranking`. O segundo
colidiria com o `/api/v1/jogadores/{slug}` existente: funcionaria por acidente — o Spring
prioriza o literal sobre a variável de template — até alguém cadastrar um jogador com
slug `ranking`. Recurso próprio evita depender dessa precedência.

**Só `/api/v1/rankings` exige entrada nova em `SecurityConfig`.** O
`GET /api/v1/jogadores/**` já é `permitAll`, e o sub-recurso `/overall` está coberto por
ele.

---

## Testes

| Teste | Prova | Tipo |
|---|---|---|
| `CalculadoraDeOverallTest` | soma ponderada contra caso calculado à mão; o mesmo zagueiro pontua diferente como lateral; tudo 99 resulta em 99 e tudo 0 resulta em 0; `HALF_UP` nas bordas (`.5` sobe); atributo nulo falha alto | puro, sem Spring |
| `PerfilAvaliacaoIntegridadeTest` | todo perfil ativo soma exatamente 1.0000 por `compareTo`; exatamente um ativo por posição; todo perfil tem 18 linhas de peso | Testcontainers, contra o seed |
| `MaterializacaoOverallTest` | nove linhas por jogador e temporada; `perfil_versao` gravada; segunda execução não duplica e não altera nenhum overall | Testcontainers |
| `RankingPaginacaoTest` | com jogadores empatados no mesmo overall, duas páginas consecutivas não repetem nem omitem ninguém | Testcontainers |
| `AvaliacaoControllerTest` | contrato dos dois endpoints e 404 para slug inexistente | `@WebMvcTest` com `SecurityConfig` real e `@MockitoBean` |
| `ModularidadeTest` (existente) | `avaliacao` depende de `jogador` e `temporada`; nada depende de `avaliacao` | Spring Modulith |

Persistência é testada com Testcontainers, conforme `.rules/java-testing.md`. Sem H2, sem
mock de repository.

O `@WebMvcTest` importa o `SecurityConfig` real: sem ele a resposta é 401, e afrouxar a
segurança do teste mascararia o problema em vez de provar o contrato. É o mesmo
aprendizado registrado no ciclo do catálogo.

---

## Riscos

**1. A distribuição de overall pode sair comprimida demais.** Média ponderada crua sobre
atributos reais tende a concentrar os jogadores numa faixa estreita, o que empobrece a
percepção de qualidade no jogo. Só é mensurável depois do Plano 3. Mitigado pelo
versionamento de perfil: corrigir é criar `versao = 2`, não remodelar.

**2. Os pesos não têm verdade de referência.** São ponto de partida plausível, desenhado
por julgamento de domínio, não valores calibrados contra resultado de partida. O motor de
simulação (subsistema 2) é o que vai gerar evidência para ajustá-los.

**3. A materialização de dez mil linhas precisa caber em tempo aceitável.** O desenho em
lotes paginados existe por isso, mas o número real só aparece com dado real. Se o
importador ficar lento, o ponto de ataque é o tamanho do lote, não o modelo.

**4. A fronteira custa quatro métodos em `JogadorService`.** Cada um se justifica
isoladamente, mas o conjunto sinaliza que `posicao` — catálogo fixo de nove linhas,
consultado por dois módulos — pode acabar merecendo módulo próprio. Não neste ciclo: um
módulo para nove linhas imutáveis seria cerimônia. Se um terceiro módulo precisar de
`posicao`, a conta muda.

## Fora de escopo

Progressão, arquétipos de crescimento, envelhecimento por skill, estágios de carreira,
projeção, declínio reversível, lesões, pools de nomes e geração de jogadores de base —
todos com spec próprio. Pipeline de dados e importador — Plano 3. Camada de save,
motor de simulação e frontend — subsistemas seguintes.

Filtro de ranking por competição. Nenhum dado real no banco.

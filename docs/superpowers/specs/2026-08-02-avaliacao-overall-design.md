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

---

## Arquitetura de módulos

Módulo novo: `br.com.api.footfirma.avaliacao`, com
`@ApplicationModule(displayName = "Avaliação")`. Depende de `jogador` e `temporada`.
Nada depende dele.

```
avaliacao/
├── package-info.java              # @ApplicationModule(displayName = "Avaliação")
├── AvaliacaoService.java          # única porta pública do módulo
├── dto/                           # OverallDoJogador, ItemDeRanking, ResultadoMaterializacao
├── domain/                        # PerfilAvaliacao, PerfilAvaliacaoPeso, JogadorOverall, AtributoAvaliavel
├── repository/
├── mapper/
├── web/                           # AvaliacaoController (package-private)
└── internal/                      # AvaliacaoServiceImpl, CalculadoraDeOverall
```

A porta pública do módulo, inteira:

```java
public interface AvaliacaoService {

    Optional<OverallDoJogador> buscarPorSlug(String slug, String labelTemporada);

    Page<ItemDeRanking> ranquear(String labelTemporada, String codigoPosicao,
                                 Integer overallMinimo, Pageable pageable);

    ResultadoMaterializacao materializar(String labelTemporada);
}
```

Os dois primeiros métodos servem os endpoints REST; o terceiro é chamado pelos testes
neste ciclo e pelo importador no Plano 3.

### A fronteira que o spec original não previu

Para materializar aproximadamente dez mil linhas, `avaliacao` precisa varrer os
atributos de todos os jogadores de uma temporada. Mas `JogadorAtributoRepository` vive em
`jogador/repository/`, invisível fora do módulo, e entidade JPA não cruza fronteira.

A saída é a simétrica da regra que o spec do catálogo já aplica ao importador
("`dataimport` não escreve em tabela alheia"): **`avaliacao` não lê tabela alheia, lê
pelo serviço**. `JogadorService` ganha um método de leitura em lote:

```java
Page<JogadorComAtributos> listarAtributosPorTemporada(String labelTemporada, Pageable pageable);
```

`JogadorComAtributos` é um record novo em `jogador/dto/` que envolve o `AtributosJogador`
já existente, acrescentando identificação. Os dois nomes são próximos e designam coisas
distintas: `AtributosJogador` são as 18 skills e nada mais; `JogadorComAtributos` é
`(id, slug, atributos)`.

Ele carrega o **id numérico** porque a materialização precisa dele para a chave
estrangeira de `jogador_overall`. Isso não afrouxa a regra de que o identificador público
estável é o slug: o id circula entre módulos do backend e nunca aparece em resposta REST.

### Fronteira com o catálogo

O ADR `2026-08-01-catalogo-read-only.md` continua valendo sem alteração. `avaliacao`
escreve apenas na tabela que é dele, e o `grep` por `PostMapping|PutMapping|PatchMapping|
DeleteMapping` nos pacotes `web/` continua voltando vazio.

O `JogadorController` **não muda**. Se ele passasse a devolver overall, `jogador`
dependeria de `avaliacao` e a dependência declarada se inverteria — rebalancear pesos
passaria a mexer no contrato do catálogo. O overall é servido por controller próprio do
módulo `avaliacao`, que já pode ler `jogador`.

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

-- Sustenta "melhores atacantes da Série A" sem full scan com aritmética.
create index idx_jogador_overall_ranking
    on jogador_overall (temporada_id, posicao_id, overall desc);
```

Três decisões que carregam justificativa:

**1. A soma dos pesos igual a 1.0 não vira `check`.** Não é expressável como restrição de
linha. Fica em `PerfilAvaliacaoIntegridadeTest`, que reprova qualquer perfil ativo cuja
soma não seja exatamente 1.0000. Pega peso quebrado no seed antes de ele virar overall
errado.

**2. `perfil_versao` é gravada junto do overall, não inferida.** Sem ela não há como saber
com qual balanceamento uma linha foi calculada, e o versionamento de perfil perde a
função.

**3. Skills de goleiro recebem peso 0 nos perfis de linha, com linha explícita.** Todo
perfil tem exatamente 18 linhas de peso. Sem `NULL`, sem caso especial no calculador.

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
`AtributosJogador` — o record que `jogador` já expõe via `@NamedInterface` — e o perfil de
pesos; devolve `int`.

Cruzar a fronteira com um record imutável de 18 inteiros é o propósito da API pública do
módulo. Um DTO espelho em `avaliacao`, só para traduzir, seria cerimônia sem ganho.

`AtributoAvaliavel` carrega a própria extração, em vez de um `switch` de 18 casos:

```java
RITMO(AtributosJogador::ritmo),
FORCA(AtributosJogador::forca),
...
GOL_MANEJO(AtributosJogador::golManejo);
```

Adicionar uma skill vira uma linha, e não existe caminho onde o código compile com o
atributo declarado no `check` do banco e ausente da conta.

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

**Não é exposta por REST.** Se virasse endpoint, seria um `POST` no catálogo e derrubaria
a verificação do ADR. Neste ciclo quem a chama são os testes; no Plano 3, o importador, como
último passo da ordem de carga.

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

O ranking fica em `/api/v1/rankings`, **não** em `/api/v1/jogadores/ranking`. O segundo
colidiria com o `/api/v1/jogadores/{slug}` existente: funcionaria por acidente — o Spring
prioriza o literal sobre a variável de template — até alguém cadastrar um jogador com
slug `ranking`. Recurso próprio evita depender dessa precedência.

Ambas as rotas exigem entrada nova em `SecurityConfig`, que declara rotas públicas uma a
uma.

---

## Testes

| Teste | Prova | Tipo |
|---|---|---|
| `CalculadoraDeOverallTest` | soma ponderada contra caso calculado à mão; o mesmo zagueiro pontua diferente como lateral; tudo 99 resulta em 99 e tudo 0 resulta em 0; arredondamento nas bordas | puro, sem Spring |
| `PerfilAvaliacaoIntegridadeTest` | todo perfil ativo soma exatamente 1.0000; exatamente um ativo por posição; todo perfil tem 18 linhas de peso | Testcontainers, contra o seed |
| `MaterializacaoOverallTest` | nove linhas por jogador e temporada; `perfil_versao` gravada; segunda execução não duplica e não altera nenhum overall | Testcontainers |
| `AvaliacaoControllerTest` | contrato dos dois endpoints e 404 para slug inexistente | `@WebMvcTest` com `SecurityConfig` real e `@MockitoBean` |
| `ModularidadeTest` (existente) | `avaliacao` depende de `jogador`; nada depende de `avaliacao` | Spring Modulith |

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

## Fora de escopo

Progressão, arquétipos de crescimento, envelhecimento por skill, estágios de carreira,
projeção, declínio reversível, lesões, pools de nomes e geração de jogadores de base —
todos com spec próprio. Pipeline de dados e importador — Plano 3. Camada de save,
motor de simulação e frontend — subsistemas seguintes.

Nenhum dado real entra no banco neste ciclo.

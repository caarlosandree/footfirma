# Mundo fictício gerado — design

Data: 2026-08-03
Status: aprovado, não implementado

## Contexto

O subsistema de catálogo foi entregue em três planos. O Plano 3 terminou com um
importador de CSV (`ImportacaoService`), um dataset versionado em
`backend/footfirma/fixtures/v1` e uma carga que popula 20 clubes reais da Série A com
440 jogadores fictícios.

Esse desenho partia de uma premissa que não se sustentou: a de que o mundo do jogo
seria alimentado por dado externo, extraído de fonte pública por um pipeline Python
que nunca foi escrito. O importador existe para desconfiar de dado que vem de fora —
manifesto, SHA-256, contagem de linhas, ocorrência por referência ausente. Num mundo
inteiramente gerado não há fonte externa de que desconfiar: o dado nasce correto por
construção, e toda essa maquinaria vira cerimônia sobre um arquivo que o próprio
sistema acabou de escrever.

Some-se a isso o custo de misturar dado real com dado gerado. Clube real amarra o
balanceamento à expectativa do mundo real — ninguém aceita um Flamengo pobre — e a
Série B exigiria mais vinte clubes reais e mais decisões sobre qual temporada
espelhar.

Este documento substitui a ingestão por CSV por um gerador procedural, e o mundo real
por um mundo fictício balanceado.

## Objetivo

Remover o módulo `importacao` e entregar o módulo `mundo`: um gerador determinístico
que popula, a partir de uma semente, duas ligas nacionais fictícias com 40 clubes e
1.520 jogadores, balanceados de modo que cada clube tenha vantagens e desvantagens
distintas.

Fora de escopo: competição continental, copa, motor de simulação, transferências,
progressão entre temporadas, sistema financeiro com receita e folha salarial, e
qualquer dado real no banco além dos seeds de geografia.

## Decisões

### 1. Geração procedural em Java, não CSV

O mundo nasce de código e de uma semente. Sem arquivo intermediário, sem manifesto,
sem verificação de integridade de dataset.

A alternativa considerada foi manter o pipeline e trocar só o conteúdo das fixtures.
Ela preserva um diff revisável do mundo em git, mas cobra a manutenção de 25 classes
de ingestão cuja razão de existir — desconfiar da fonte — desapareceu. A
reprodutibilidade que o dataset versionado dava é obtida mais barato pelo
determinismo: mesma semente, mesmo mundo.

A alternativa de seed em SQL puro foi descartada porque migration versionada não pode
ser editada (o guard bloqueia), e rebalancear a liga exigiria uma migration nova a
cada ajuste de número.

### 2. Tudo fictício, geografia real

Clubes, estádios, apelidos, cores e jogadores são inventados. Cidade e UF saem dos
seeds reais de `V4__popula_geografia.sql`, que já existem e que `clube` e `jogador`
referenciam.

Nomes de clube são uma **lista curada de 40**, não combinação de pools. São poucos, e
um nome sorteado por combinatória tem chance real de coincidir com clube profissional
existente. Quarenta nomes revisados à mão é trabalho de uma sessão; 40 colisões
possíveis a cada regeração, não.

Nomes de jogador continuam saindo da combinatória de prenomes e sobrenomes
brasileiros. Ver Risco 3.

### 3. `mundo` orquestra, não escreve em tabela alheia

A regra que o ADR `2026-08-03-ingestao-pelos-modulos.md` estabeleceu para o importador
vale igual para o gerador: toda escrita passa pelos métodos `sincronizar*` da interface
pública de cada módulo de catálogo. Nenhum repository de outro módulo entra no
construtor do gerador.

Essa camada é o que sobrevive à remoção do importador. `ClubeService`,
`JogadorService`, `CompeticaoService`, `TemporadaService`, `GeografiaService` e
`ResultadoDeSincronizacao` ficam intactos; troca-se apenas quem os chama.

### 4. Riqueza é um eixo 0–99 no clube, não um sistema financeiro

`clube.forca_financeira` entra como irmão de `reputacao` e `qualidade_base`, que já
existem com a mesma escala e o mesmo papel.

Orçamento, receita e folha salarial em valores monetários foram descartados: são
números que nada leria e nada gastaria enquanto não existir motor de simulação e
mercado de transferências. Orçamento sem transferência é decoração.

### 5. Base é uma categoria no vínculo

`jogador_vinculo.categoria` in (`PROFISSIONAL`, `BASE`). Cada clube tem dois elencos
na mesma tabela, distinguíveis por consulta.

Uma tabela `elenco_base` separada duplicaria a modelagem de vínculo que já existe, e a
promoção de um garoto ao profissional viraria migração entre tabelas. Com a coluna,
promover é gravar o vínculo do ano seguinte como `PROFISSIONAL` — o vínculo já é
versionado por temporada.

Tratar base como convenção de idade dentro de um elenco único foi descartado porque
tornaria indistinguíveis um sub-19 titular e um garoto do sub-17.

### 6. Balanceamento por arquétipos declarados

Cada clube recebe um dos seis arquétipos, e seus três eixos saem sorteados dentro da
faixa dele:

| Arquétipo | Reputação | Finanças | Base | Divisão 1 | Divisão 2 |
|---|---|---|---|---|---|
| Potência | 82–90 | 85–95 | 55–70 | 3 | 0 |
| Gigante Endividado | 75–85 | 35–50 | 60–75 | 3 | 1 |
| Celeiro | 50–62 | 30–45 | 85–95 | 2 | 3 |
| Meio de Tabela | 55–68 | 55–70 | 45–60 | 6 | 4 |
| Recém-Promovido | 45–55 | 45–60 | 40–55 | 3 | 5 |
| Em Queda | 40–52 | 25–40 | 30–45 | 3 | 7 |

A tabela é dado explícito em código: lê-se, entende-se e ajusta-se sem rodar nada.
Uma distribuição contínua com correlação negativa entre dinheiro e base produziria um
mundo mais orgânico, mas sem garantia de que algum clube caia no canto "pobre com base
excelente" — e é exatamente esse canto que dá a dinâmica desejada.

## Arquitetura

### O que sai

| O quê | Onde |
|---|---|
| Módulo `importacao` | 25 classes em `src/main/java/br/com/api/footfirma/importacao/` |
| Testes de importação | 10 classes, incluindo `GeradorDeFixtures` |
| Dataset de fixtures | `fixtures/v1/` (15 CSVs + `manifest.json`) e `fixtures/README.md` |
| Task `gerarFixtures` | `build.gradle` |
| ADR de ingestão | `backend/footfirma/docs/adr/2026-08-03-ingestao-pelos-modulos.md` |
| Runbook de importação | `backend/footfirma/docs/runbooks/importacao.md` |

Some junto o vocabulário de ingestão: manifesto, checksum, ocorrência, severidade,
execução de importação e `chave_natural` no papel de chave de idempotência de arquivo.

`jogador.chave_natural` e `jogador.semente` **ficam**: a semente deriva da chave e é a
origem de todo atributo oculto do jogador.

### Schema — migrations `V15` e `V16`

`V14` está versionada e o guard bloqueia editá-la. São duas migrations porque são
duas mudanças lógicas: uma remove, a outra adiciona.

`V15__remove_importacao.sql`:

```sql
drop table if exists importacao_ocorrencia;
drop table if exists importacao_contagem;
drop table if exists importacao_execucao;
```

`V16__adiciona_forca_financeira_e_categoria.sql`:

```sql
alter table clube add column forca_financeira integer not null default 50
    check (forca_financeira between 0 and 99);

alter table jogador_vinculo add column categoria text not null default 'PROFISSIONAL'
    check (categoria in ('PROFISSIONAL', 'BASE'));

create index idx_jogador_vinculo_categoria on jogador_vinculo (clube_id, categoria);

alter table jogador_atributo drop constraint jogador_atributo_fonte_atributo_check;
alter table jogador_atributo add constraint jogador_atributo_fonte_atributo_check
    check (fonte_atributo in ('IMPORTADO', 'ESTIMADO', 'GERADO'));
```

O terceiro bloco não estava previsto e apareceu na escrita do plano: `fonte_atributo`
aceitava só `IMPORTADO` e `ESTIMADO`. Atributo gerado não é nenhum dos dois — não veio
de lugar nenhum nem foi inferido de observação. `IMPORTADO` permanece no conjunto
porque linhas antigas ainda o usam.

`clube_alias` e as três tabelas `*_referencia_externa` existem para casar registros com
fontes externas (`EA_FC`, `TRANSFERMARKT`). Num mundo fictício não há o que casar. Elas
**permanecem no schema e não são populadas**: removê-las implicaria mexer em
`ClubeService`, `JogadorService` e `CompeticaoService`, que não são alvo deste ciclo.
Ficam como dívida registrada, não como esquecimento.

### O módulo `mundo`

```
mundo/
  MundoService.java              gerar() → RelatorioDeMundo
  dto/
    RelatorioDeMundo.java        semente usada, temporada e contagens
    ContagemPorEntidade.java
  internal/
    MundoServiceImpl.java        orquestrador: ligas, clubes, elencos, materialização
    CatalogoDeArquetipos.java    os seis arquétipos e a distribuição por divisão
    Arquetipo.java               faixas de um perfil de clube
    FabricaDeClubes.java         arquétipo → eixos, cidade, estádio, cores
    FabricaDeElenco.java         cotas de papel → alvos de overall, idade e potencial
    FabricaDeAtributos.java      alvo de overall + posição → as 18 skills
    PapelNoElenco.java           enum das cotas, deltas e faixas de idade
    ClubeGerado.java             clube antes de virar linha
    JogadorGerado.java           jogador antes de virar linha
    AtributosGerados.java        as 18 skills
    ChaveDeJogador.java          chave natural e semente (herdado de `importacao`)
    GeradorDeNomes.java          lista curada de clubes; pools de nomes de jogador
    LimpezaDoCatalogo.java       apaga o catálogo na ordem inversa das FKs
    PropriedadesDeMundo.java     footfirma.mundo.semente / .recriar
    MundoRunner.java             ApplicationRunner sob profile `mundo`
```

As fábricas são **puras**: recebem um `SplittableRandom` e devolvem records, sem
Spring e sem banco. Isso mantém o orquestrador enxuto e dá teste unitário de
balanceamento que roda em milissegundos, sem Docker.

`LimpezaDoCatalogo` é a única exceção à regra de não tocar tabela alheia: apaga por
`JdbcTemplate`, porque esvaziar o catálogo inteiro é operação de infraestrutura sobre
o banco, não escrita de domínio. A alternativa seria expor um método destrutivo na
interface pública de cinco módulos — superfície pior que uma lista de tabelas.

### Fluxo

```
subir com --spring.profiles.active=mundo
  └─ MundoRunner
       ├─ [se footfirma.mundo.recriar=true] LimpezaDoCatalogo
       │     vínculo → característica → atributo oculto → atributo → posição do
       │     jogador → overall → jogador → participante → regra → fase → edição →
       │     clube → estádio → competição → temporada
       ├─ MundoServiceImpl.gerar()
       │     temporada → competições → clubes → elencos → atributos
       │     └─ avaliacaoService.materializar("2026")
       └─ RelatorioDeMundo no log
```

Geografia, posição, característica e perfil de avaliação **não** são apagados: são seed
de migration, não catálogo gerado.

O determinismo vem de `SplittableRandom` derivado da semente raiz, com um sub-gerador
por clube. Mexer no clube 7 não desloca o clube 8 — sem isso, qualquer ajuste
reescreveria o mundo inteiro e tornaria o diff de comportamento ilegível.

### A geração de atributos

O ponto mais delicado do módulo. O overall não é escrito: é derivado pelo módulo
`avaliacao`, que faz média ponderada das 18 skills com os pesos por posição da `V13`.

Como os pesos de cada perfil somam 1,0, a inversão é direta e não exige ler a tabela de
pesos — o que cruzaria fronteira de módulo. `FabricaDeAtributos` declara, por posição,
o conjunto de skills que importam; gera essas em torno do alvo com ruído, e as
irrelevantes num nível coerente com a posição (zagueiro com finalização baixa, goleiro
com ritmo indiferente). O overall materializado cai a até 2 pontos do alvo, e essa
tolerância é asserção de teste.

O conjunto de skills relevantes por posição é conhecimento duplicado entre `avaliacao`
e `mundo`. É duplicação consciente: a alternativa seria expor os pesos na interface
pública de `avaliacao` para que o gerador invertesse a fórmula, acoplando a geração à
versão do perfil de avaliação.

## O mundo gerado

### Competições

| Slug | Nome | Nível | Clubes |
|---|---|---|---|
| `primeira-divisao` | Primeira Divisão Nacional | 1 | 20 |
| `segunda-divisao` | Segunda Divisão Nacional | 2 | 20 |

Tipo `LIGA`, país BRA, edição 2026, fase única `PONTOS_CORRIDOS` com turno e returno.

Regras de classificação: `REBAIXAMENTO` para 17º–20º da primeira divisão e `ACESSO`
para 1º–4º da segunda. O enum de `regra_classificacao.tipo` também contém
`LIBERTADORES_GRUPOS`, `LIBERTADORES_PRE` e `SULAMERICANA`; esses valores ficam
dormentes até existir competição continental, que é ciclo próprio.

`edicao_participante.posicao_final` fica nulo: não houve temporada disputada.

### Qualidade do elenco

```
nivel_elenco       = 0,6 · reputacao + 0,4 · forca_financeira
qualidade_da_base  = qualidade_base        (independente do dinheiro)
```

É a fórmula que produz a dinâmica pedida:

- **Potência** (rep. 86, fin. 90, base 62) → elenco em 88, base mediana: compra
  pronto, não forma.
- **Gigante Endividado** (rep. 80, fin. 42, base 68) → elenco em 65, abaixo do que o
  nome sugere: vive de passado.
- **Celeiro** (rep. 56, fin. 38, base 91) → elenco em 49, joias de potencial 94 no
  sub-20: time ruim que revela.

### Composição por clube

Elenco profissional de 26:

| GOL | ZAG | LTD | LTE | VOL | MEC | MEA | PTA | ATA |
|---|---|---|---|---|---|---|---|---|
| 3 | 5 | 2 | 2 | 4 | 3 | 2 | 3 | 2 |

Elenco de base de 12: GOL 1 · ZAG 2 · LTD 1 · LTE 1 · VOL 2 · MEC 2 · MEA 1 · PTA 1 ·
ATA 1.

Camisas 1–26, únicas dentro do clube. Jogadores de base não recebem camisa.

### Curva de qualidade dentro do elenco

Overall relativo ao `nivel_elenco` do clube:

| Papel | Qtd | Overall | Idade |
|---|---|---|---|
| Estrela | 1 | +10 a +14 | 24–30 |
| Titulares | 9 | +2 a +6 | 21–33 |
| Rotativos | 8 | −2 a +2 | 21–33 |
| Reservas | 6 | −8 a −3 | 19–35 |
| Crias promovidas | 2 | −6 a 0 | 18–20 |

Elenco de base, relativo à `qualidade_base` do clube:

| Papel | Qtd | Overall | Potencial | Idade |
|---|---|---|---|---|
| Joias | 2 | −20 | 88–95 | 16–19 |
| Promessas | 4 | −15 | 75–85 | 15–19 |
| Garotos | 6 | −22 | 60–72 | 15–18 |

A cota de estrela é o que garante que todo clube, por pior que seja, tenha alguém para
se olhar. Uma distribuição gaussiana pura permitiria um elenco inteiro entre 55 e 61 —
tecnicamente correto e sem graça.

### Potencial e valor

`potencial_base` nunca fica abaixo do overall atual, e o espaço entre os dois encolhe
com a idade: aos 32 o potencial iguala o overall, aos 17 pode superá-lo em 25 pontos.
É o que faz "jovem promissor" significar alguma coisa.

`jogador_vinculo.valor_mercado_eur` deriva de overall, idade e potencial — não da
riqueza do clube. Valor de mercado é atributo do jogador; o eixo financeiro do clube já
está em `forca_financeira`.

Atributos ocultos (profissionalismo, ambição, lealdade, …) continuam derivando da
semente do jogador, como hoje.

## Testes

| Teste | Afirma |
|---|---|
| `FabricaDeClubesTest` (unitário) | 40 clubes, 20 por divisão, slugs e cidades únicos, faixas respeitadas, existe Celeiro pobre com base ≥ 85 e Gigante Endividado |
| `FabricaDeElencoTest` (unitário) | 26+12, nove posições cobertas, camisas únicas, estrela ≥ média + 8, joia de potencial ≥ 88 em clube pobre, potencial nunca abaixo do overall |
| `FabricaDeAtributosTest` (unitário) | skills na faixa 1–99, qualidade concentrada nas skills do perfil, skill de goleiro baixa em jogador de linha |
| `MundoLigaTest` | duas competições, duas edições, regras de acesso e rebaixamento |
| `MundoClubeTest` | 40 clubes com estádio próprio, 20 por divisão, estado resolvido |
| `MundoIntegridadeTest` | 1.520 jogadores, 26+12 por clube, nove posições em cada elenco, camisas únicas, 13.680 linhas de overall dentro da faixa |
| `MundoBalanceamentoTest` | divisão 1 com média acima da divisão 2; todo clube com destaque ≥ média + 8; joia em clube com `forca_financeira` < 50; overall médio a ≤ 8 pontos de `nivel_elenco` |
| `MundoDeterminismoTest` | mesma semente produz os mesmos slugs e overalls; regerar com `recriar=true` não duplica nem deixa órfão |
| `ModularidadeTest` (existente) | `mundo` não alcança tipo `internal` de outro módulo |

A tolerância entre alvo e overall materializado é verificada como **correlação por
clube** (`MundoBalanceamentoTest`), não jogador a jogador: o alvo individual não é
gravado em lugar nenhum, e reconstruí-lo no teste duplicaria a fábrica dentro da
asserção.

Os testes de balanceamento são o que impede o mundo de degradar em ruído a cada
ajuste de faixa. Sem eles, um erro de sinal numa fórmula produziria um mundo plausível
à primeira vista e sem nenhuma das propriedades pedidas.

## Riscos

**1. As faixas dos arquétipos não têm verdade de referência.** São julgamento de
domínio, não valores calibrados contra resultado de partida. O motor de simulação é o
que vai gerar evidência para ajustá-las. Mitigado por serem dado declarado num único
arquivo: rebalancear é editar uma tabela e rodar de novo.

**2. A inversão do overall pode não convergir dentro da tolerância em posições com
poucos atributos de peso alto.** Goleiro concentra 82% do peso em três skills; um
ruído infeliz nelas desloca o overall mais que nas demais posições.
`FabricaDeAtributosTest` e `MundoBalanceamentoTest` cobrem as nove posições
justamente para que isso apareça como falha, não como time de goleiros ruins.

**3. Nome de jogador pode coincidir com pessoa real.** Com 1.520 combinações de pools
de prenomes e sobrenomes brasileiros, a chance não é desprezível, e revisar à mão é
inviável nessa escala. Risco aceito e reduzido pelo contexto: nenhum clube deste mundo
é real, então uma coincidência de nome não sugere identificação de pessoa. O dataset
antigo, com clubes reais, tinha o problema oposto.

**4. `clube_alias` e as tabelas `*_referencia_externa` ficam sem uso.** São quatro
tabelas vazias e ~8 métodos públicos sem chamador. Dívida registrada. O momento de
cobrá-la é quando alguém precisar mexer nesses serviços por outro motivo.

**5. Uma temporada só remove o caso de teste de versionamento por temporada.** O
dataset antigo tinha 2025 e 2026, e com isso provava que atributo e vínculo são
versionados por ano. Com 2026 apenas, a estrutura continua no schema mas deixa de ser
exercitada. Volta a ser quando existir o spec de progressão, que é o dono legítimo
desse comportamento.

## Fora de escopo

Competição continental e copa — o mundo desta versão tem apenas as duas ligas
nacionais. Motor de simulação de partida, transferências, mercado, contratos e
salários. Progressão entre temporadas, envelhecimento e lesões. Sistema financeiro com
receita, folha e orçamento. Setor forte e setor fraco por clube — identidade tática que
só se sente com simulação de partida. Camada de save e frontend.

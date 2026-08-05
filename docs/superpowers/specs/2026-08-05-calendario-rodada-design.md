# Calendário e rodada — design

Data: 2026-08-05
Status: proposto

## Contexto

O spec do treinador fatiou o caminho até a partida em três camadas:

1. **Treinador** — quem controla um clube ✅ implementado
2. **Tática e escalação** — o que o treinador decide ✅ implementado
3. **Partida** — onde as decisões se resolvem

A camada 3 não é o próximo passo, e a razão é estrutural. Partida é o confronto entre
duas escalações numa data; hoje não existe data, e não existe confronto. `competicao`
vai até `Competicao → Edicao → Fase → EdicaoParticipante` e para ali: sabe quem disputa,
não sabe quem enfrenta quem, nem quando.

Este documento entrega o que falta entre a camada 2 e a 3.

`Fase` já declara `tipo` (`PONTOS_CORRIDOS`, `GRUPOS`, `ELIMINATORIA`),
`jogos_por_confronto`, `tem_gol_fora`, `tem_prorrogacao` e `tem_penaltis`. Cinco campos
escritos para um confronto que nunca existiu — este spec é quem finalmente os lê.

## Objetivo

Entregar o módulo `calendario`: rodadas, confrontos e jogos de uma edição, com data e
mando atribuídos por um gerador determinístico que respeita descanso mínimo entre jogos
de um mesmo clube; mais o registro de resultado, a resolução do confronto e a propagação
do chaveamento nas fases eliminatórias.

Fora de escopo: simulação da partida, tabela de classificação, competição continental,
copa no mundo gerado, transmissão, arbitragem, público e renda, adiamento por decisão
externa, autenticação.

## Decisões

### 1. Módulo próprio, não dentro de `competicao`

`competicao` é catálogo: escrito uma vez pelo gerador de mundo, lido pelo resto. Rodada,
confronto e jogo são a primeira coisa do sistema que **muda durante a temporada** — o
placar é reescrito a cada partida, e `confronto.vencedor_clube_id` só existe depois de
disputado.

Misturar catálogo estável com estado mutável é o que faz um módulo virar o dono de tudo.
`competicao` já tem cinco entidades e 28 arquivos; passaria a oito entidades e a receber
escrita de `partida`.

O precedente do repositório aponta para a separação: `tatica` é estado do clube e virou
módulo próprio em vez de entrar em `clube`, pelo mesmo motivo.

O custo aceito: `confronto.faseId`, `jogo.clubeMandanteId` e `jogo.estadioId` são colunas
`Long` cruas com FK na migration, e não `@ManyToOne`. É o padrão do repositório para
referência cross-module — `Jogador.paisId` e as três de `JogadorOverall` já fazem isso.

### 2. Confronto e jogo são entidades distintas

Em pontos corridos, um confronto é um jogo e a distinção parece burocracia. Em
eliminatória com `jogos_por_confronto = 2`, ela é a diferença entre modelar o domínio e
espalhá-lo: quem avança se decide pela **soma de dois jogos**, com gol fora, prorrogação
e pênaltis — regras que `Fase` declara e que não têm onde morar se só existir `jogo`.

Sem `confronto`, "quem venceu o mata-mata" seria um cálculo entre duas linhas de `jogo`
sem lugar para ser gravado, e o chaveamento não teria como apontar para o confronto que
o alimenta.

### 3. O chaveamento existe antes dos classificados

Confrontos de fases eliminatórias posteriores nascem no sorteio, vazios, com
`origem_lado_a` e `origem_lado_b` apontando para os confrontos que os alimentam. A
semifinal existe em banco antes de existirem semifinalistas.

A alternativa — criar os jogos de uma fase só quando a anterior termina — foi recusada
porque o calendário da temporada ficaria com buracos: não daria para exibir o chaveamento
nem dizer quando será a final antes das semifinais acabarem.

### 4. Rodada tem tipo, e o tipo decide o leque de dias

A verificação da tabela real da CBF (Série A, rodadas 23 a 27, 37 jogos) mostra 16 jogos
no domingo, 13 no sábado, 3 na quarta, 2 na segunda, 2 na quinta, 1 na terça e **zero na
sexta**. E mostra a estrutura: as rodadas 23, 24, 25 e 27 são de fim de semana; a 26
concentra quarta e quinta.

Não é um conjunto de dias por competição — é uma propriedade de cada rodada. Toda rodada
nasce `FIM_DE_SEMANA` ou `MEIO_DE_SEMANA`, e o tipo define em que dias seus jogos podem
cair.

Isso resolve sem remendo o problema que abriu a discussão: 20 clubes em turno e returno
dão 38 rodadas, e a janela do mundo gerado (4/4/2026 a 6/12/2026) tem 246 dias, ou 35
semanas. As rodadas que não cabem numa semana cada viram as de meio de semana.

### 5. Descanso mínimo é a restrição real, não a grade de dias

Não existe grade fixa de domingos. A regra que o calendário precisa garantir é que um
clube tenha um intervalo mínimo entre jogos consecutivos — em qualquer competição da
temporada, não só na que está sendo gerada.

É isso que torna a **agenda do clube** um conceito de primeira classe, exposto na porta
pública como `listarAgendaDoClube`, e não uma query escondida dentro do alocador. O
gerador consulta a mesma porta que a API.

O valor inicial é 3 dias, e ele não é arbitrário: domingo → quarta são exatamente 3 dias,
e quarta → domingo, 4. O descanso mínimo é o piso que o próprio padrão de fim de semana e
meio de semana produz — baixá-lo permitiria calendários que a realidade não produz, e
subi-lo tornaria a rodada de meio de semana impossível.

Ainda assim, como os coeficientes de `ConstantesDeTatica`, é julgamento de domínio: os
testes travam a regra, não o número.

### 6. O perfil de calendário é entrada de geração, não dado persistido

Os pesos de dia por tipo de rodada e o descanso mínimo entram como argumento de
`gerarParaEdicao`, ao lado da semente:

```java
record PerfilDeCalendario(
        Map<TipoDeRodada, Map<DayOfWeek, Integer>> pesos,
        int descansoMinimoEmDias) {}
```

Persistir exigiria migration em `competicao` — módulo alheio — ou uma tabela nova para um
dado que hoje tem dois valores. A semente já é entrada e não é persistida; o perfil segue
a mesma regra. Se um dia a API precisar responder "quando esse campeonato costuma jogar",
a resposta sai dos jogos gerados, que são o fato.

Os pesos são julgamento, não medição. Eles foram calibrados contra a distribuição real,
mas os testes travam a forma — nenhum jogo na sexta, meio de semana só em quarta e
quinta, descanso sempre respeitado — e não os percentuais.

### 7. Data de mata-mata é provisória até os clubes se definirem

Os jogos da semifinal são datados no sorteio, quando ninguém sabe quem os disputará. A
restrição de descanso foi verificada contra a agenda de clubes que ainda não estavam ali
— ou seja, não foi verificada.

Quando o confronto ganha seus dois clubes, seus jogos passam pela mesma regra de descanso
e deslizam se precisar. É o que acontece de fato: a CBF só detalha data e hora do
mata-mata depois de conhecidos os classificados.

A alternativa — data firme, descanso valendo só nas fases geradas com clubes conhecidos —
foi recusada porque produziria uma invariante que vale às vezes, e nenhum teste
conseguiria afirmar "todo clube sempre descansa N dias".

### 8. Avançar o chaveamento é deste módulo, não de `partida`

Sem isso, uma fase eliminatória é gerada e nunca preenche: `vencedor_clube_id` fica nulo
para sempre e a semifinal nunca conhece seus semifinalistas.

Somar dois jogos, aplicar gol fora e decidir nos pênaltis é mecânica de calendário — as
regras vivem em `Fase`, e afastá-las dali as espalharia. `partida` entrega dois números e
não precisa saber o que eles decidem.

### 9. Uma chamada por resultado, sem ping-pong

`buscarJogo` devolve, junto do jogo, as regras de desempate que valem para ele — lidas da
`Fase` e de se é o jogo decisivo do confronto. A partida simula de acordo e devolve tudo
de uma vez.

A alternativa seria a partida simular o tempo normal, perguntar se precisa de prorrogação,
simular de novo, perguntar sobre pênaltis. Três viagens onde cabe uma, e a regra de
desempate acabaria duplicada nos dois lados.

## Arquitetura

### Dependências

| Módulo | O que `calendario` usa | Por onde |
|---|---|---|
| `competicao` | fases da edição, tipo e regras de desempate, participantes | `CompeticaoService` |
| `clube` | estádio do mandante | `ClubeService` |

`calendario` não conhece `jogador`, `tatica` nem `avaliacao`. Quem cruza jogo com
escalação é `partida`, que ainda não existe.

`mundo` passa a depender de `calendario` — a terceira dependência que ele adquire, depois
de `avaliacao` e `tatica`.

### Ajustes fora do módulo

`CompeticaoService` precisa de um método que hoje não existe: os participantes por fase,
que `EdicaoDetalhe` não traz.

Os campos de desempate quase todos já existem. `FaseResumo` traz `tipo`,
`jogosPorConfronto`, `temProrrogacao` e `temPenaltis`; falta **apenas `temGolFora`**, que
está na entidade `Fase` desde a `V1` e é justamente o que o `ResolvedorDeConfronto`
precisa. O ajuste é acrescentar um campo ao record existente, não criar um DTO novo de
desempate.

`LimpezaDoCatalogo` ganha `jogo`, `confronto` e `rodada` **abrindo a lista**, antes de
tudo. As três referenciam `clube`, `estadio` e `fase`. É o mesmo defeito que
`plano_escalacao` e `plano_tatico` produziram em 2026-08-05 e que custou nove testes em
quatro classes: sem elas ali, `footfirma.mundo.recriar=true` falha por chave estrangeira.

### Schema — migration `V22`

```sql
create table rodada (
    id            bigint  generated always as identity primary key,
    fase_id       bigint  not null references fase (id),
    ordem         integer not null check (ordem > 0),
    tipo          text    not null check (tipo in ('FIM_DE_SEMANA', 'MEIO_DE_SEMANA')),
    data_alvo     date    not null,
    janela_inicio date    not null,
    janela_fim    date    not null
);

create unique index uq_rodada_ordem on rodada (fase_id, ordem);

comment on column rodada.tipo is
    'FIM_DE_SEMANA ou MEIO_DE_SEMANA. Decide o leque de dias em que os jogos podem cair';
comment on column rodada.data_alvo is
    'Âncora da rodada: domingo no fim de semana, quarta no meio de semana';

create table confronto (
    id                bigint generated always as identity primary key,
    fase_id           bigint  not null references fase (id),
    ordem             integer not null check (ordem > 0),
    chave             text,
    origem_lado_a     bigint  references confronto (id),
    origem_lado_b     bigint  references confronto (id),
    clube_a_id        bigint  references clube (id),
    clube_b_id        bigint  references clube (id),
    vencedor_clube_id bigint  references clube (id)
);

create unique index uq_confronto_ordem on confronto (fase_id, ordem);

-- A propagação do chaveamento busca confrontos por origem a cada confronto resolvido.
-- FK usada em filtro tem índice; o Postgres não o cria sozinho.
create index idx_confronto_origem_a on confronto (origem_lado_a) where origem_lado_a is not null;
create index idx_confronto_origem_b on confronto (origem_lado_b) where origem_lado_b is not null;

comment on column confronto.origem_lado_a is
    'Confronto que alimenta este lado. Nulo quando o clube veio do sorteio inicial';
comment on column confronto.clube_a_id is
    'Nulo até a fase anterior resolver. Em pontos corridos e grupos, nunca nulo';
comment on column confronto.vencedor_clube_id is
    'Só em fase ELIMINATORIA. Em pontos corridos e grupos fica nulo para sempre: quem avança ali é decidido por classificação, que não é deste módulo';

create table jogo (
    id                  bigint generated always as identity primary key,
    confronto_id        bigint  not null references confronto (id),
    rodada_id           bigint  not null references rodada (id),
    ordem_no_confronto  integer not null check (ordem_no_confronto between 1 and 2),
    mandante_id         bigint  references clube (id),
    visitante_id        bigint  references clube (id),
    estadio_id          bigint  references estadio (id),
    data_jogo           date,
    situacao            text    not null default 'AGENDADO'
                        check (situacao in ('AGENDADO', 'ENCERRADO')),
    gols_mandante             integer check (gols_mandante             >= 0),
    gols_visitante            integer check (gols_visitante            >= 0),
    gols_mandante_prorrogacao integer check (gols_mandante_prorrogacao >= 0),
    gols_visitante_prorrogacao integer check (gols_visitante_prorrogacao >= 0),
    penaltis_mandante         integer check (penaltis_mandante         >= 0),
    penaltis_visitante        integer check (penaltis_visitante        >= 0),

    constraint ck_jogo_clubes_distintos check (mandante_id <> visitante_id)
);

create unique index uq_jogo_ordem on jogo (confronto_id, ordem_no_confronto);
create index idx_jogo_rodada   on jogo (rodada_id);
create index idx_jogo_mandante on jogo (mandante_id, data_jogo);
create index idx_jogo_visitante on jogo (visitante_id, data_jogo);

comment on column jogo.estadio_id is
    'Gravado do mandante no momento em que o jogo ganha seus clubes, nunca inferido depois: o clube pode trocar de estádio, e o jogo passado aconteceu onde aconteceu';
comment on column jogo.data_jogo is
    'Provisória enquanto o confronto não tem os dois clubes. Realocada pela regra de descanso quando eles se definem (decisão 7)';
comment on column jogo.situacao is
    'AGENDADO ou ENCERRADO. Não há ADIADO: jogo fora da janela da rodada é apenas um jogo em outro dia, e ninguém o adia por decisão externa neste sistema';
comment on column jogo.ordem_no_confronto is
    '1 ou 2, conforme fase.jogos_por_confronto. A faixa é o domínio de hoje: confronto de três jogos exigiria migration, e é essa a intenção — que a mudança seja deliberada';
comment on constraint ck_jogo_clubes_distintos on jogo is
    'Bloqueia clube contra si mesmo. Passa de propósito quando um dos lados é nulo: em eliminatória o jogo nasce sem clubes, e no Postgres null <> null é null, que o check trata como aprovado. Não é falha da constraint — é o caso pré-classificação sendo aceito';
```

Os dois índices por clube e data existem para `listarAgendaDoClube`, que o gerador
consulta uma vez por jogo alocado — 760 vezes numa temporada de duas ligas.

**Por que `date` e não `timestamptz`.** A regra de banco pede `timestamptz` sempre, e é
explícita sobre o motivo: é a regra de **data e hora**. O gerador aloca por dia, e horário
de jogo não existe neste spec — nem como conceito, nem como coluna. `timestamptz` num
campo sem hora obrigaria a inventar uma meia-noite arbitrária e traria discussão de fuso
para um dado que não tem instante. Quando houver horário de jogo, ele entra como coluna
própria com `timestamptz`, e essa decisão se reabre com um caso concreto.

**A auto-FK de `confronto` é problema para a limpeza.** `origem_lado_a`/`origem_lado_b`
apontam para a própria tabela, e `LimpezaDoCatalogo` apaga com um `delete` linear por
tabela. Numa eliminatória, confrontos de fase posterior referenciam os de fase anterior:
um `delete from confronto` sem ordem viola a auto-FK. O plano precisa escolher entre
`on delete cascade` na auto-referência e apagar por fase em ordem decrescente. É o mesmo
tipo de defeito que `plano_tatico` produziu em 2026-08-05 — e desta vez está previsto
antes de morder.

### O módulo `calendario`

```
calendario/
├── package-info.java              @ApplicationModule(displayName = "Calendário")
├── CalendarioService.java         porta pública única
├── dto/                           @NamedInterface("dto")
│   ├── TipoDeRodada · SituacaoDoJogo
│   ├── PerfilDeCalendario · ResultadoDoJogo      entrada
│   └── JogoAgendado · RodadaDetalhe · ConfrontoDetalhe · RelatorioDeCalendario
├── domain/       Rodada · Confronto · Jogo
├── repository/   RodadaRepository · ConfrontoRepository · JogoRepository
├── web/          CalendarioController
└── internal/     ConstantesDeCalendario · TabelaDeBerger · SorteioDeChaveamento
                  TipadorDeRodadas · SorteadorDeDia · AlocadorDeDatas
                  ResolvedorDeConfronto · CalendarioServiceImpl
```

Os enums vivem em `dto/` pelo motivo que o spec de tática registrou: são o contrato, e
publicá-los a partir de `domain/` exporia as entidades JPA junto.

### Os motores puros

`TabelaDeBerger`, `TipadorDeRodadas`, `SorteadorDeDia` e `ResolvedorDeConfronto` recebem
records e `SplittableRandom`, e devolvem records. Sem Spring, sem JPA, sem relógio — como
as fábricas de `mundo`, é o que permite testar a regra sem Docker.

`AlocadorDeDatas` é a exceção: precisa da agenda do clube, que vem do banco. Ele recebe a
agenda já carregada como argumento, e não o repositório — assim continua testável puro.

## As regras

### Gerar

**Pontos corridos.** Round-robin pelo método de Berger. Com 20 participantes: 19 rodadas
no turno, returno espelhado com mando invertido, 38 rodadas e 380 jogos. O sorteio
determinístico define a posição inicial de cada clube na tabela de rotação.

**Grupos.** Participantes distribuídos entre os grupos por sorteio determinístico; dentro
de cada grupo, o mesmo Berger. Grupos de uma mesma fase compartilham as rodadas.

**Eliminatória.** Os confrontos da primeira fase saem do sorteio entre os participantes.
Os das fases seguintes nascem vazios, com as origens apontando para os confrontos que os
alimentam. `jogos_por_confronto` decide se são um ou dois jogos; no de dois, o mando
inverte.

### Datar

1. **Tipo de cada rodada.** Conta-se quantas semanas inteiras cabem na janela da edição
   (246 dias ÷ 7 = 35). Se há rodadas a mais que semanas, o excedente — 38 − 35 = 3 —
   vira `MEIO_DE_SEMANA`, distribuído uniformemente ao longo da sequência; as demais são
   `FIM_DE_SEMANA`. Quando as rodadas cabem em semanas, nenhuma é de meio de semana.
2. **Data-alvo.** As rodadas de fim de semana ocupam domingos consecutivos; cada rodada
   de meio de semana ocupa a quarta-feira da semana em que foi inserida, empurrando as
   seguintes uma semana para trás na contagem. A janela é o alvo ±2 dias.
3. **Dia de cada jogo.** Sorteio ponderado dentro do leque do tipo, com os pesos do
   perfil. Determinístico: consome a mesma semente da geração.
4. **Descanso.** Se o dia sorteado viola o descanso mínimo de qualquer um dos dois clubes
   contra a agenda inteira deles na temporada, tenta o próximo dia do leque em ordem de
   peso decrescente. Esgotado o leque, o jogo vai para o primeiro dia posterior que sirva
   — fora da janela, e a rodada **não** desliza.

Deslizar a rodada propagaria em cascata por todas as seguintes e tornaria o calendário
instável a cada competição acrescentada. Um jogo isolado fora da janela é o que acontece
numa temporada real.

**A ordem de geração importa.** A competição gerada primeiro ocupa os melhores dias; a
seguinte se acomoda no que sobrou. É o preço da geração incremental, e é o que permite
acrescentar uma copa sem refazer as ligas. Com as duas ligas atuais o efeito é nulo:
nenhum clube está nas duas.

O que **não** pode ficar implícito é que a ordem das chamadas em `mundo` determina o
calendário. Quando a copa entrar, ninguém vai lembrar disso lendo o gerador. O plano deve
tornar a precedência um argumento de `gerarParaEdicao` — divisão mais alta primeiro, copa
depois —, para que a dependência saia da ordem das linhas e vire contrato verificável.

### Registrar resultado

`registrarResultado(jogoId, ResultadoDoJogo)` grava o placar e marca `situacao =
'ENCERRADO'`. Recusa o que a fase não permite — pênaltis num jogo de pontos corridos é
`ResultadoInvalidoException`, não dado tolerado —, jogo já encerrado e jogo cujos clubes
ainda não se definiram.

### Resolver e propagar

Ao encerrar o último jogo de um confronto, e **só em fase eliminatória**, o módulo soma
os jogos e aplica na ordem: agregado, gol fora se `tem_gol_fora`, prorrogação se
`tem_prorrogacao`, pênaltis se `tem_penaltis`.

Definido o vencedor, os confrontos que o têm como origem recebem o clube. Quando os dois
lados chegam, os jogos do confronto ganham mandante, visitante e estádio — nulos desde a
geração, porque não havia clube de quem tirá-los — e passam pela realocação da decisão 7.

## Erros

Duas exceções em `shared/exception`, com handler 422 no `TratadorDeErros`, pelo motivo
que `EscalacaoInvalidaException` já registrou — quem traduz para HTTP não pode enxergar
tipo interno de módulo de domínio:

- `CalendarioInvalidoException` — fase sem participantes, número ímpar em eliminatória,
  janela curta demais para as rodadas exigidas.
- `ResultadoInvalidoException` — desempate que a fase não permite, jogo já encerrado,
  jogo sem clubes definidos.

## API

Dois endpoints de leitura, sob o ADR de catálogo read-only:

```
GET /api/v1/competicoes/{slug}/edicoes/{temporada}/rodadas
GET /api/v1/clubes/{slug}/jogos
```

O primeiro estende o path que `CompeticaoController` já expõe
(`/{slug}/edicoes/{temporada}`) em vez de abrir `/edicoes` como recurso raiz — rodada é
subrecurso da edição, e a edição só existe dentro de uma competição.

Sem escrita. `registrarResultado` é porta de módulo, não endpoint: quem a chama é
`partida`, e não há autenticação para autorizar um humano a gravar placar.

## Testes

### Motores puros — JUnit sem Spring, sem Docker

- `TabelaDeBergerTest` — todo participante enfrenta todos os outros exatamente uma vez
  por turno; nenhum joga duas vezes na mesma rodada; mando alternado entre turno e
  returno.
- `TipagemDeRodadasTest` — 38 rodadas em 246 dias produzem exatamente três de meio de
  semana; 20 rodadas na mesma janela não produzem nenhuma.
- `SorteioDeDiaTest` — nenhum dia fora do leque do tipo; mesma semente, mesma sequência.
- `AlocacaoDeDatasTest` — descanso respeitado; jogo empurrado para fora da janela quando
  o leque esgota; rodada não desliza.
- `ResolucaoDeConfrontoTest` — a ordem agregado → gol fora → prorrogação → pênaltis, e
  cada regra desligada quando a fase não a declara.

### Integridade — com Testcontainers

- Geração das duas ligas: 760 jogos, 76 rodadas, 760 confrontos; **nenhum jogo na
  sexta**; todo clube com descanso respeitado em toda a temporada.
- Chaveamento de uma eliminatória de 8 clubes: 7 confrontos, propagação até a final,
  todo confronto com vencedor ao fim.
- Realocação: um confronto resolvido cujos clubes vinham de jogo recente desloca a data.
- `registrarResultado` recusando os três casos inválidos.

### Determinismo

Gerar duas vezes com a mesma semente produz o mesmo calendário — mesmas datas, mesmos
mandos, mesma ordem de confrontos. É o teste que `MundoDeterminismoTest` já faz para
clubes e elencos, aplicado aqui.

### O que não é testado

A calibragem dos pesos. Que a Série A jogue 45% dos jogos no domingo é julgamento, e
travá-lo em teste transformaria rebalanceamento em quebra de suíte.

## Riscos

**1. Desenhado antes do consumidor.** Vale aqui o risco 2 do spec do treinador, na
íntegra. `registrarResultado` e as regras de desempate existem para uma `partida` que
ainda não foi escrita; quem se adapta é ela.

**2. Grupos e eliminatória nascem sem uso.** O mundo gerado tem duas ligas de pontos
corridos e nenhuma copa. As duas metades mais complexas deste spec — chaveamento,
propagação, realocação — nascem exercitadas só por teste. É a mesma aposta que `tatica`
fez, e ela se pagou: o escalador só encontrou a realidade quando o gerador passou a
chamá-lo.

Mitigação possível no plano: fatiar a entrega em pontos corridos primeiro, com grupos e
eliminatória em seguida, mantendo o schema desenhado inteiro desde a `V22`.

Se fatiar, o teste de integridade da eliminatória — 8 clubes, 7 confrontos, propagação até
a final — entra no **slice da eliminatória**, não no de pontos corridos. Ele é a única
coisa que prova que a metade complexa funciona; deixá-lo para trás faria a fatia nascer
sem a sua própria verificação.

**3. Guloso na alocação.** O alocador escolhe dia por dia, sem retroceder. Como o
escalador de `tatica`, ele erra onde uma busca completa acertaria. Com 38 rodadas e uma
competição por clube, o erro é raro; com três competições cruzadas, pode empurrar jogos
para bem fora da janela. O teste que verifica descanso pega a violação, não a feiura.

**4. `data_jogo` anulável.** A coluna aceita nulo por causa das fases eliminatórias
posteriores. Nada no banco impede um jogo de pontos corridos sem data — só o gerador. É
uma invariante de agregado, na mesma família das que `TaticaService` guarda no serviço
porque `check` não enxerga outras linhas.

## Fora de escopo

Simulação, classificação, competição continental, copa no mundo gerado, público e renda,
arbitragem, adiamento por decisão externa, autenticação. A tabela de classificação em
particular: `RegraClassificacao` existe em `competicao` desde a `V1`, e continuará sem
consumidor até que haja resultado para ordenar.

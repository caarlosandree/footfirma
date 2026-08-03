# Treinador — design

Data: 2026-08-03
Status: aprovado, não implementado

## Contexto

O mundo fictício está gerado: 40 clubes em duas divisões, 1.520 jogadores, 13.680
linhas de overall. O que existe é um catálogo — um mundo parado, sem ninguém dentro
dele tomando decisão.

O destino do FootFirma é um jogo online para 8 a 10 pessoas, cada uma dirigindo um
clube, com partidas em tempo real que os dois treinadores acompanham e nas quais
intervêm. Nada disso existe hoje, e três camadas separam o estado atual desse destino:

1. **Treinador** — quem controla um clube (este documento)
2. **Tática e escalação** — o que o treinador decide
3. **Partida** — onde as decisões se resolvem

A ordem não é arbitrária. Partida é o confronto entre duas escalações, não entre dois
clubes; escalação é a decisão de alguém. Sem o sujeito, as outras duas camadas não têm
onde se apoiar.

Este documento cobre apenas a primeira. Ele modela **estado e regra de transição** —
os gatilhos que alimentam essas transições chegam depois, quando a partida existir.

## Objetivo

Entregar o módulo `treinador`: uma entidade de domínio que dirige um clube, acumula
moral e reputação, pode ser demitida, recebe propostas, distribui pontos entre seis
skills e mantém uma relação individual com cada jogador do elenco.

O módulo é construído e testado **sem que a partida exista**. Os motores são funções
puras alimentadas por eventos de domínio; os testes publicam eventos sintéticos. Quando
`partida` for escrita, ela publica os mesmos eventos e o módulo passa a funcionar sem
alteração.

Fora de escopo: conta de usuário e autenticação, salário e multa rescisória, tática,
escalação, partida, evolução de `clube` ao longo das temporadas.

## Decisões

### 1. Treinador é entidade de domínio, não conta de usuário

`treinador.tipo` é `HUMANO` ou `IA`. Os ~30 clubes sem dono humano têm treinadores com
as mesmas skills, a mesma moral e os mesmos vínculos — simetria total, e o mercado de
propostas tem de onde tirar candidatos.

Não existe coluna apontando para conta. Quem liga pessoa a treinador é o spec de
autenticação, que é um subsistema próprio (registro, sessão, `SecurityConfig` real).
Criar aqui uma FK para tabela inexistente seria dívida disfarçada de preparo.

### 2. Moral é relativa à expectativa, não ao resultado bruto

Num mundo de 40 clubes com `reputacao` entre 35 e 88, julgar todo mundo pela mesma
régua premia quem pegou clube grande e demite quem foi bem com clube pequeno. Cada
vínculo nasce com uma `meta_posicao` derivada da força do clube, e cada resultado é
pesado pela força do adversário.

Vencer o líder vale mais que vencer o lanterna. Empatar com o lanterna, sendo gigante,
custa caro. É o que torna uma liga de clubes desiguais jogável entre amigos.

### 3. Toda relação tem duas camadas: memória e presente

| Nível | Memória (atravessa clubes) | Presente (morre com o vínculo) |
|---|---|---|
| Treinador | `treinador.reputacao` | `vinculo_treinador.moral` |
| Par treinador↔jogador | `treinador_jogador_afinidade.afinidade` | `treinador_jogador.moral` + `status_confianca` |

A memória **semeia** o presente a cada vínculo novo, com a mesma regra nos dois níveis.
Um treinador vitorioso chega ao clube novo com moral alta; um jogador que já foi seu
pupilo chega adiantado. Trocar de clube não é reset.

O efeito emergente — levar o pupilo junto para o clube seguinte — sai de graça das duas
tabelas, sem regra especial.

### 4. Seis skills, uma por momento do jogo

Skills que agem no mesmo instante criam escolha falsa: uma delas será estritamente
melhor e todo mundo montará o mesmo treinador. As seis atuam em momentos distintos —
durante a partida, no intervalo, no vestiário, entre temporadas, na execução tática e
no mercado.

Cada skill produz um **modificador nomeado** que outros módulos consultam, em vez de um
efeito cravado dentro do motor. Rebalancear é editar um arquivo de constantes.

### 5. Skills evoluem por temporada cumprida

A distribuição inicial é um começo, não uma sentença. Ao fim de cada temporada o
treinador ganha de 1 a 3 pontos conforme o desfecho, reaproveitando a `meta_posicao`
que a moral já usa. Skills ficam versionadas por temporada, no formato de
`jogador_atributo`, e o histórico de evolução sai de graça.

### 6. Vínculo jogador↔treinador tem dois eixos

`status_confianca` é **input**: o treinador declara o papel do jogador no elenco.
`moral` é **output**: o jogador reage à distância entre o que foi prometido e os
minutos que recebeu.

Um eixo só perderia a mecânica central — sem promessa declarada não existe promessa
quebrada, e é a quebra que gera história.

### 7. Eventos de domínio na borda, motor puro no núcleo

O núcleo são funções sem Spring, sem JPA e sem relógio: recebem estado e fato,
devolvem estado e consequências. A borda são `@ApplicationModuleListener` consumindo
eventos.

A tabela `event_publication` existe desde a `V1` e o `spring-modulith-starter-jpa` está
no `build.gradle`, mas nenhum `@ApplicationModuleListener`, `ApplicationEventPublisher`
ou `@EventListener` aparece em `src/`. A infraestrutura foi instalada no primeiro dia e
nunca usada; este módulo é o primeiro consumidor.

A alternativa — `PartidaService` chamando `TreinadorService` diretamente — inverteria a
dependência: `partida` passaria a conhecer `treinador`, depois `tatica`, depois
financeiro, virando o nó que todo módulo puxa. E deixaria este módulo inteiro sem
teste possível até a partida existir.

### 8. O evento carrega o estado congelado

O motor **nunca** lê `clube.reputacao`. As reputações vigentes viajam dentro do evento:

```java
record PartidaEncerrada(
    long edicaoId, long clubeMandanteId, long clubeVisitanteId,
    int golsMandante, int golsVisitante,
    int reputacaoMandante,      // congelada no instante do fato
    int reputacaoVisitante,
    Instant ocorridoEm
) {}
```

Sem isso, reprocessar uma campanha depois que a reputação do clube mudou produziria uma
moral diferente da que aconteceu, e o determinismo se perderia em silêncio. Hoje
`clube.reputacao` é estática e o problema não se manifesta — mas o spec de progressão
vai torná-la dinâmica, e o contrato precisa estar pronto antes.

A regra vale para **o que é recalculado**, não para o que é decidido uma vez. A moral de
assinatura e a `meta_posicao` consultam o clube no instante da contratação e gravam o
resultado em `vinculo_treinador`; nunca são recalculadas, então não têm o que congelar.
O que passa pelo evento é o que o motor toca a cada partida: as duas reputações.

## Arquitetura

### Schema — migration `V17`

```sql
create table treinador (
    id                 bigint  generated always as identity primary key,
    slug               text    not null unique check (length(slug) between 2 and 80),
    nome_completo      text    not null check (length(nome_completo) between 1 and 160),
    nome_exibicao      text    not null check (length(nome_exibicao) between 1 and 80),
    data_nascimento    date    not null,
    pais_id            bigint  not null references pais (id),
    tipo               text    not null check (tipo in ('HUMANO', 'IA')),
    reputacao          integer not null default 50 check (reputacao between 0 and 99),
    pontos_disponiveis integer not null default 0 check (pontos_disponiveis >= 0),
    semente            bigint  not null,
    criado_em          timestamptz not null default now(),
    atualizado_em      timestamptz
);

comment on column treinador.reputacao is
    'Memória de carreira (0-99). Decide quais clubes fazem proposta e semeia a moral inicial do vínculo';
comment on column treinador.pontos_disponiveis is
    'Pontos ganhos ao fim da temporada e ainda não distribuídos entre as skills';
```

```sql
-- Linha por skill, no formato de jogador_atributo. Versionar por temporada dá o
-- histórico de evolução sem tabela de auditoria.
create table treinador_skill (
    treinador_id bigint  not null references treinador (id),
    temporada_id bigint  not null references temporada (id),
    skill        text    not null check (skill in ('VISAO_DE_JOGO', 'PRELECAO', 'LIDERANCA',
                                                   'TREINAMENTO', 'TATICA', 'NEGOCIACAO')),
    valor        integer not null check (valor between 1 and 10),
    primary key (treinador_id, temporada_id, skill)
);
```

```sql
create table vinculo_treinador (
    id           bigint  generated always as identity primary key,
    treinador_id bigint  not null references treinador (id),
    clube_id     bigint  not null references clube (id),
    temporada_id bigint  not null references temporada (id),
    inicio       date    not null,
    fim          date,
    motivo_fim   text    check (motivo_fim in ('DEMISSAO', 'PEDIDO_DEMISSAO',
                                               'FIM_DE_CONTRATO', 'ACEITOU_PROPOSTA')),
    moral        numeric(4,2) not null check (moral between 0 and 99),
    meta_posicao integer      not null check (meta_posicao > 0),
    criado_em    timestamptz not null default now()
);

-- Mesmo padrão de uq_perfil_avaliacao_ativo (V12): a integridade é do banco, não da
-- aplicação. Sem estes dois índices, um bug de concorrência coloca dois treinadores
-- no mesmo clube e o mundo fica inconsistente sem ninguém perceber.
create unique index uq_vinculo_clube_ativo     on vinculo_treinador (clube_id)     where fim is null;
create unique index uq_vinculo_treinador_ativo on vinculo_treinador (treinador_id) where fim is null;

comment on column vinculo_treinador.moral is
    'Relação com ESTE clube. Semeada pela reputação do treinador na assinatura, nunca 50 fixo';
comment on column vinculo_treinador.meta_posicao is
    'Expectativa congelada na contratação. É contra ela que a moral e a evolução são medidas';
```

```sql
create table proposta (
    id            bigint  generated always as identity primary key,
    clube_id      bigint  not null references clube (id),
    treinador_id  bigint  not null references treinador (id),
    temporada_id  bigint  not null references temporada (id),
    meta_posicao  integer not null check (meta_posicao > 0),
    status        text    not null default 'ABERTA'
                  check (status in ('ABERTA', 'ACEITA', 'RECUSADA', 'EXPIRADA')),
    criada_em     timestamptz not null default now(),
    expira_em     timestamptz not null,
    respondida_em timestamptz
);

create index idx_proposta_treinador_aberta on proposta (treinador_id) where status = 'ABERTA';

comment on column proposta.meta_posicao is
    'O que o clube vai cobrar. Viaja na proposta para que se saiba o alvo antes de assinar';
```

```sql
-- Ancorada no vínculo: a relação morre quando a passagem acaba. O que sobrevive à
-- troca de clube é a afinidade, não o status.
create table treinador_jogador (
    id                   bigint  generated always as identity primary key,
    vinculo_treinador_id bigint  not null references vinculo_treinador (id),
    jogador_id           bigint  not null references jogador (id),
    status_confianca     text    not null default 'ROTACAO'
                         check (status_confianca in ('INDISCUTIVEL', 'IMPORTANTE', 'ROTACAO',
                                                     'PROMESSA', 'FORA_DOS_PLANOS')),
    moral                numeric(4,2) not null check (moral between 0 and 99),
    minutos_acumulados   integer      not null default 0 check (minutos_acumulados >= 0),
    atualizado_em        timestamptz,
    constraint uq_treinador_jogador unique (vinculo_treinador_id, jogador_id)
);

-- Ancorada no par, sem clube na chave: é a memória que atravessa carreiras.
create table treinador_jogador_afinidade (
    treinador_id  bigint  not null references treinador (id),
    jogador_id    bigint  not null references jogador (id),
    afinidade     numeric(4,2) not null default 50 check (afinidade between 0 and 99),
    jogos_juntos  integer      not null default 0 check (jogos_juntos >= 0),
    atualizado_em timestamptz,
    primary key (treinador_id, jogador_id)
);

comment on column treinador_jogador_afinidade.jogos_juntos is
    'Peso da consolidação: passagem de 3 jogos mexe pouco na afinidade, de 3 temporadas mexe muito';
```

**Moral e afinidade são `numeric(4,2)`, não `integer`.** Os deltas por partida são
fracionários (+5,9 · +0,8 · −3,2) e uma campanha tem 38 deles. Arredondar a cada
aplicação acumularia erro na ordem de dezenas de pontos ao longo da temporada, e a
diferença entre `+0,8` e `+1` decidiria demissões. Em Java os motores trabalham com
`double`; a coluna guarda duas casas.

**Uma invariante não cabe no banco.** A soma das seis skills de um treinador numa
temporada precisa bater com `20 + pontos ganhos`. É agregado, e `check` não enxerga
outras linhas. As alternativas seriam trigger — o projeto não tem nenhuma — ou coluna
redundante de total. A decisão é **validar no serviço e cravar em teste de
integridade**, registrada aqui para não parecer esquecimento.

### O módulo `treinador`

```
treinador/
├── TreinadorService.java        ← única porta pública
├── package-info.java
├── domain/      Treinador · VinculoTreinador · SkillTreinador · StatusConfianca
│                Proposta · MotivoFim · TipoTreinador
├── dto/         DadosDeTreinador · TreinadorResumo · DistribuicaoDeSkills
│                VinculoResumo · PropostaResumo · ElencoConfianca
├── repository/  TreinadorRepository · VinculoTreinadorRepository
│                PropostaRepository · TreinadorJogadorRepository · AfinidadeRepository
├── mapper/      TreinadorMapper (MapStruct)
├── web/         TreinadorController
└── internal/    MotorDeMoral · MotorDeConfianca · PoliticaDeDemissao
                 CalculadoraDeExpectativa · GeradorDePropostas
                 ConstantesDeTreinador · OuvinteDePartida · OuvinteDeTemporada
                 TreinadorServiceImpl
```

Os cinco primeiros tipos de `internal/` são funções puras: sem Spring, sem JPA, sem
relógio. Testáveis com JUnit puro, sem Docker.

### Bordas

| Direção | Evento | Existe hoje? |
|---|---|---|
| consome | `PartidaEncerrada` | não — contrato definido na decisão 8 |
| consome | `TemporadaEncerrada` | não |
| publica | `TreinadorDemitido` | novo |
| publica | `TreinadorSobPressao` | novo |
| publica | `PropostaEnviada` | novo |
| publica | `VinculoIniciado` · `VinculoEncerrado` | novos |
| publica | `JogadorInsatisfeito` | novo, sem consumidor |

## As regras

### A meta da temporada

`meta_posicao` é o **rank do clube por reputação entre os participantes da edição**:

```
meta_posicao = posição de clube.reputacao no ranking decrescente
               das reputações de edicao_participante
```

O clube mais reputado da divisão recebe meta 1º, o menos reputado meta 20º. Sem
coeficiente arbitrário, e se adapta sozinho a divisão de qualquer tamanho — a Série B
com 20 clubes e uma futura copa com 64 usam a mesma regra.

Congelada em `vinculo_treinador.meta_posicao` na assinatura: renegociar a expectativa no
meio da temporada seria mudar a régua com o jogo em andamento.

### Moral do treinador na assinatura

```
moral_inicial = clamp(50 + (treinador.reputacao − clube.reputacao) × 0,4 ; 25 ; 85)
```

| Situação | Moral |
|---|---|
| Vencedor (85) assume clube médio (40) | **68** — contratação de prestígio, lua de mel longa |
| Desconhecido (40) assume gigante (85) | **32** — desconfiança desde o primeiro dia |
| Reputações parelhas | **50** |

### Moral do treinador por partida

```
d     = (reputacao_adversario − reputacao_meu_clube) / 50
delta = base(resultado) × fator_meta
```

| Resultado | `base` |
|---|---|
| Vitória | `+3,0 × clamp(1 + d × 0,9 ; 0,25 ; 2,5)` |
| Empate | `clamp(−1,0 + d × 2,5 ; −3,5 ; +2,0)` |
| Derrota | `−3,0 × clamp(1 − d × 0,9 ; 0,25 ; 2,5)` |

| Cenário | Delta |
|---|---|
| Celeiro (35) vence Gigante (88) | **+5,9** |
| Gigante vence Celeiro | **+0,8** |
| Celeiro perde para Gigante | **−0,8** |
| Gigante perde para Celeiro | **−5,9** |
| Gigante empata com Celeiro | **−3,5** |
| Celeiro empata com Gigante | **+1,7** |

```
fator_meta = clamp(1 + (posicao_atual − meta_posicao) × 0,05 ; 0,7 ; 1,5)
```

Aplicado **somente quando `base` é negativa**. Estar em 12º com meta de 4º faz cada
derrota doer 40% mais; liderar dá crédito. Restringir ao negativo evita o absurdo de
uma campanha ruim também valorizar vitórias.

Classificação não existe até a partida existir — até lá `fator_meta` é fixo em 1,0. A
fórmula fica escrita e o teste a cobre com posição sintética.

### Demissão

```
limiar   = 15 + (reputacao_do_meu_clube_no_evento − 50) × 0,1
carência = 5 partidas desde o início do vínculo
```

A reputação vem do evento, não do banco — mesma regra da decisão 8. Um limiar lido do
estado atual faria o reprocessamento demitir (ou salvar) alguém que na campanha original
teve o desfecho oposto.

| Moral | Consequência |
|---|---|
| ≥ limiar + 10 | seguro |
| limiar … limiar + 10 | publica `TreinadorSobPressao` |
| < limiar | demissão: `TreinadorDemitido`, `vinculo.fim`, `motivo_fim = 'DEMISSAO'` |

O limiar variável faz o gigante (rep 88 → 18,8) demitir antes do clube pequeno
(rep 35 → 13,5). A carência vale igual para `HUMANO` e `IA` — sem simetria a IA vira
caricatura.

### Mercado

`GeradorDePropostas` acorda em `VinculoEncerrado` (vaga aberta) e `TemporadaEncerrada`
(janela de assédio).

**Clube sem treinador** busca quem está livre com
`|treinador.reputacao − clube.reputacao| ≤ 25`. Sem candidato, gera um treinador de IA:
nenhum clube pode ficar vago, ou o mundo trava na primeira rodada.

**Assédio a quem está empregado** exige as três condições:

```
clube_interessado.reputacao > clube_atual.reputacao + 10
vinculo.moral               ≥ 60
treinador.reputacao         ≥ clube_interessado.reputacao − 20
```

Proposta para `HUMANO` nasce `ABERTA` com `expira_em`. Para `IA`, a política decide na
hora. Aceitar encerra o vínculo atual com `motivo_fim = 'ACEITOU_PROPOSTA'` — e os
índices parciais únicos garantem que ninguém termine dirigindo dois clubes.

### Reputação do treinador

Atualizada ao encerrar cada vínculo: sobe se a `meta_posicao` foi batida, cai na
demissão. É o que torna a carreira acumulativa e o que faz o mercado reagir ao que
você construiu.

### Skills

| Skill | Modificador | Fórmula | Consumidor |
|---|---|---|---|
| `VISAO_DE_JOGO` | `bonus_substituicao` | `skill × 1,5%` | partida |
| `PRELECAO` | `bonus_moral_intervalo` | `skill × 0,8` | partida |
| `LIDERANCA` | `amortecimento_moral` | `1 − skill × 0,06` | **este módulo** |
| `TREINAMENTO` | `aceleracao_evolucao` | `skill × 0,3` | progressão |
| `TATICA` | `reducao_fora_de_posicao` | `skill × 4%` | tática |
| `NEGOCIACAO` | `atratividade_mercado` | `1 + skill × 0,08` | **este módulo** |

Criação: soma das seis = **20**, cada uma entre **1 e 10**.

```
Especialista   10 · 5 · 2 · 1 · 1 · 1
Generalista     4 · 4 · 3 · 3 · 3 · 3
```

Evolução ao fim da temporada:

| Desfecho | Pontos |
|---|---|
| Bateu a `meta_posicao` | +3 |
| Não bateu, sobreviveu | +2 |
| Demitido | +1 |

Demitido ainda ganha 1: fracasso ensina, e zerar quem já está mal só afunda mais. O
teto por skill permanece 10.

### Vínculo com o jogador

Cada status é uma promessa quantificada:

| Status | Escala | Minutos esperados/jogo |
|---|---|---|
| `INDISCUTIVEL` | 5 | 85 |
| `IMPORTANTE` | 4 | 65 |
| `ROTACAO` | 3 | 40 |
| `PROMESSA` | 2 | 20 |
| `FORA_DOS_PLANOS` | 1 | 0 |

```
diferença = minutos_recebidos − minutos_esperados(status)
delta     = clamp(diferença × 0,04 ; −4 ; +2)
se delta < 0:  delta × (1 − lideranca × 0,06)
```

| Caso | Delta |
|---|---|
| `INDISCUTIVEL` no banco, Liderança 1 | **−3,2** |
| `INDISCUTIVEL` no banco, Liderança 10 | **−1,4** |
| `PROMESSA` que jogou os 90 | **+2,0** |
| `FORA_DOS_PLANOS` que não jogou | **0** |

O clamp é assimétrico de propósito (−4 contra +2): decepção pesa mais que satisfação.
`FORA_DOS_PLANOS` chegando a zero sem punição torna a honestidade viável — dizer "você
não joga" custa menos que prometer e não cumprir.

Mudar de status tem preço imediato, senão bastaria promover todo mundo antes do jogo e
rebaixar depois:

```
delta_imediato = (escala_novo − escala_anterior) × 3
```

Rebaixar de `INDISCUTIVEL` para `FORA_DOS_PLANOS` custa **−12** na hora. Abaixo de
moral 25 o módulo publica `JogadorInsatisfeito`, gancho para o futuro pedido de
transferência.

### Moral do jogador na chegada e afinidade

```
moral_inicial = clamp(50 + (afinidade − 50) × 0,6 ; 20 ; 90)
```

| Histórico | Moral na chegada |
|---|---|
| Nunca trabalharam juntos (afinidade 50) | **50** |
| Ex-pupilo com afinidade 90 | **74** |
| Queimado no banco, afinidade 20 | **32** |

A afinidade consolida ao **encerrar** o vínculo — é memória de longo prazo, não humor:

```
peso      = clamp(jogos_juntos / 60 ; 0,10 ; 0,70)
afinidade ← afinidade_anterior × (1 − peso) + moral_final × peso
```

| Passagem | Peso | Efeito |
|---|---|---|
| 3 jogos | 0,10 | quase não move a afinidade |
| 1 temporada (38 jogos) | 0,63 | move bastante |
| 3 temporadas | 0,70 | domina a memória anterior |

O teto de 0,70 impede que uma passagem longa apague completamente o histórico — quem
trabalhou bem com você por seis anos não vira estranho por causa de um último ano ruim.

## Testes

### Motores puros — JUnit sem Spring, sem Docker

| Teste | Afirma |
|---|---|
| `MotorDeMoralTest` | Celeiro vence Gigante **+5,9**, Gigante vence Celeiro **+0,8**, Gigante empata **−3,5**, Celeiro empata **+1,7**; `fator_meta` só amplifica delta negativo |
| `MotorDeConfiancaTest` | `INDISCUTIVEL` no banco cai −3,2 com Liderança 1 e −1,4 com Liderança 10; `FORA_DOS_PLANOS` sem jogar não muda; clamp assimétrico; rebaixar status custa −12 |
| `PoliticaDeDemissaoTest` | limiar sobe com `clube.reputacao`; carência de 5 partidas segura início ruim; gigante demite antes do clube pequeno com a mesma moral |
| `CalculadoraDeExpectativaTest` | meta derivada da reputação; moral inicial do vínculo segue `clamp(50 + (rep_treinador − rep_clube) × 0,4)` |
| `GeradorDePropostasTest` | faixa ±25; assédio exige moral ≥ 60 e clube ≥ +10 de reputação; `NEGOCIACAO` aumenta a frequência; clube sem candidato recebe treinador de IA |

### O teste que carrega o spec

`CampanhaSimuladaTest` — 38 eventos `PartidaEncerrada` sintéticos, nenhuma partida real.

| Cenário | Afirma |
|---|---|
| Celeiro (rep 35, meta 16º), campanha mediana | sobrevive; moral fecha acima do limiar |
| Gigante (rep 88, meta 1º), **a mesma campanha** | demitido antes da rodada 38 |

Os dois cenários rodam sobre a mesma sequência de resultados. São a prova de que a
régua relativa funciona: se alguém trocar a fórmula por absoluta, este teste quebra e
diz exatamente por quê.

### Integridade — com Testcontainers

| Teste | Afirma |
|---|---|
| `VinculoIntegridadeTest` | segundo vínculo ativo no mesmo clube é rejeitado **pelo banco**; treinador com dois clubes ativos idem; encerrar o primeiro libera o segundo |
| `SkillIntegridadeTest` | soma ≠ 20 rejeitada pelo serviço; valor fora de 1–10 rejeitado pelo banco; distribuição de pontos ganhos respeita o teto |
| `AfinidadeTest` | encerrar vínculo consolida a afinidade; recontratar o pupilo semeia moral 74; jogador nunca dirigido começa em 50 |
| `ModularidadeTest` (existente) | `treinador` não alcança tipo `internal` de outro módulo |

### Determinismo

| Teste | Afirma |
|---|---|
| `ReprocessamentoTest` | reexecutar a mesma lista de eventos devolve a mesma moral final |
| `CongelamentoTest` | alterar `clube.reputacao` no banco e reprocessar devolve a moral **original** |

`CongelamentoTest` falha se alguém, por conveniência, fizer o motor consultar o clube
em vez de ler o evento — e é essa conveniência que quebraria tudo quando o spec de
progressão tornar a reputação dinâmica.

### O que não é testado

Os coeficientes (`0,9`, `2,5`, `0,04`, `0,06`, limiar 15, faixa ±25) são julgamento de
domínio sem verdade de referência, como as faixas dos arquétipos no spec do mundo. Os
testes travam a **forma** das fórmulas e a ordem relativa dos casos, não os valores
absolutos. Rebalancear é editar `ConstantesDeTreinador`; um teste que quebre nisso
estava medindo a coisa errada.

## Riscos

**1. Quatro das seis skills terminam sem consumidor.** `VISAO_DE_JOGO`, `PRELECAO`,
`TREINAMENTO` e `TATICA` ficam declaradas, testadas e ociosas — os módulos que as
consomem não existem. É intencional: o valor é que tática, partida e progressão nasçam
já sabendo o que consultar, em vez de inventarem o próprio conceito de habilidade de
treinador. Mas é preciso encarar que este módulo entrega quatro números corretos e sem
efeito visível.

**2. O contrato de `PartidaEncerrada` é desenhado antes do produtor existir.** A chance
de faltar um campo é real. Mitigado por ser um record pequeno e por `partida` ainda não
estar escrita — quem se adapta ao contrato é ela, não o contrário.

**3. `clube.reputacao` é estática.** O mundo é fotografia: o Celeiro pode ganhar cinco
campeonatos e continuará valendo 35 para todo efeito de cálculo, inclusive continuará
sendo um clube de quem se espera pouco. A decisão 8 protege este módulo do problema,
mas não o resolve — resolver exige versionar `clube` por temporada e evoluir
`reputacao`, `forca_financeira` e `qualidade_base` juntas. Dono legítimo: o spec de
progressão.

**4. A invariante da soma de skills vive na aplicação.** Um caminho de escrita que
ignore `TreinadorServiceImpl` produz um treinador com 40 pontos distribuídos e o banco
aceita. Mitigado por `SkillIntegridadeTest` e por a porta pública ser única.

**5. Demissão de humano numa liga de amigos.** A mecânica está desenhada para nunca
deixar alguém sem clube — há ~30 clubes de IA disponíveis e o mercado sempre gera
proposta. Ainda assim, cair de um gigante para um clube da segunda divisão é uma
punição social num grupo pequeno, e é possível que na prática o grupo ache duro demais.
Ajustável pelo limiar e pela carência, ambos em `ConstantesDeTreinador`.

**6. Nome de treinador pode coincidir com pessoa real.** Mesmo risco e mesma mitigação
do gerador de jogadores: nenhum clube deste mundo é real, então a coincidência não
sugere identificação.

## Fora de escopo

**Conta e autenticação.** `treinador.tipo` distingue `HUMANO` de `IA` e nada mais. O
vínculo entre pessoa e treinador é do spec de autenticação.

**Salário, multa rescisória e orçamento.** Proposta aqui é "o clube X te quer", sem
dinheiro. Valor exige sistema financeiro, e `forca_financeira` é um índice 0–99, não
caixa.

**Evolução de clube entre temporadas.** Ver risco 3.

**Tática, escalação e partida.** As duas camadas seguintes, cada uma com spec próprio.

**Efeito das skills em campo.** Os modificadores são declarados e expostos; quem os
aplica são os módulos futuros.

**Comissão técnica.** Auxiliar, preparador físico, olheiro. Um treinador por clube
nesta versão.

# Tática e escalação — design

Data: 2026-08-05
Status: aprovado, não implementado

## Contexto

O spec do treinador fatiou o caminho até a partida em três camadas:

1. **Treinador** — quem controla um clube ✅ implementado, mergeado em `staging`
2. **Tática e escalação** — o que o treinador decide (este documento)
3. **Partida** — onde as decisões se resolvem

A camada 1 entregou um sujeito que decide e não tem o que decidir. Quatro das seis
skills (`VISAO_DE_JOGO`, `PRELECAO`, `TREINAMENTO`, `TATICA`) estão testadas e ociosas;
`PartidaEncerrada` e `TemporadaEncerrada` existem em `shared/evento` e ninguém as
publica — só os testes, com eventos sintéticos.

Esta camada não resolve isso. Ela dá ao treinador a primeira decisão real e entrega à
partida um contrato pronto para consumir, e é honesto dizer de saída que boa parte do
que ela declara também nasce ociosa. O risco 1 do spec do treinador vale aqui na
íntegra.

## Objetivo

Entregar o módulo `tatica`: o plano tático vigente de cada clube na temporada —
formação, cinco instruções coletivas, onze titulares, banco e capitão — versionado a
cada alteração, com a aptidão posicional de cada escalado registrada, e um escalador
automático determinístico que atende os ~30 clubes de IA e cobre o humano que não
escalou.

Fora de escopo: partida, calendário, rodada, instruções durante o jogo, papéis
individuais dentro da formação, condição física, lesão, suspensão, API HTTP.

## Decisões

### 1. Módulo único, no formato do `treinador`

Catálogo de formações, plano versionado e escalador automático moram na mesma pasta,
com uma porta pública `TaticaService`.

A alternativa era separar `formacao` (catálogo) de `escalacao` (estado). Foi recusada
porque `formacao` nasceria com uma tabela e zero regra — um módulo Modulith inteiro
(`package-info`, porta pública, teste de modularidade) para hospedar um seed. O
codebase já decidiu contra isso duas vezes: `posicao` e `caracteristica` são catálogo
puro e moram dentro de `jogador`.

A alternativa de pôr o escalador dentro de `treinador` — já que é o treinador de IA
quem decide — foi recusada por inverter a dependência. Hoje `treinador` não conhece
elenco nem overall; passaria a conhecer formação, `jogador` e `avaliacao`. E o fallback
deixaria de ser "o módulo de escalação preenche o que faltou" para virar "o módulo de
treinador escala no seu lugar".

### 2. Plano é do clube, não do vínculo nem da partida

`partida` não existe, então não há FK para apontar. Mesma decisão 1 do spec do
treinador: criar coluna para tabela inexistente é dívida disfarçada de preparo.

O plano também não pertence ao vínculo do treinador. Trocar de treinador não deveria
apagar a escalação que estava lá — o clube continua tendo um time em campo enquanto o
novo não decide outra coisa.

O plano também não pertence à edição de competição. Um clube que dispute duas
competições usa o mesmo plano nas duas até a partida existir e o assunto ser reaberto.

### 3. Alterar é versionar, nunca sobrescrever

Cada alteração marca a versão vigente como `vigente = false` e insere a seguinte. Um
índice parcial garante **no banco** um único vigente por clube e temporada.

Isso compra a resposta a "que time você escalou naquele jogo?" sem tabela de auditoria,
pelo mesmo raciocínio que levou `treinador_skill` a ser versionada por temporada:
comparar dois momentos vira um select, não uma reconstrução.

### 4. Encaixe posicional já existe — esta camada lê, não recalcula

`jogador_overall` é materializado por posição: 1.520 jogadores × 9 posições = 13.680
linhas. O zagueiro escalado como ponta já tem um overall baixo naquela posição, porque
o perfil de pesos da ponta valoriza outra coisa.

Escalar fora de posição é permitido e a penalidade é automática. Nenhuma regra nova,
nenhum coeficiente novo — o módulo `avaliacao` já resolveu isso no Plano 2.

### 5. A aptidão é congelada, e a leitura traz também a atual

`plano_escalacao.aptidao_no_momento` guarda o overall lido na gravação. O DTO de
leitura traz, além dele, `aptidaoAtual`, recalculada no `join` com `jogador_overall`.

Congelar sozinho responde "o que o treinador sabia quando decidiu" — o valor de
auditoria que o versionamento comprou. Mas um plano vigente por vinte rodadas ficaria
com aptidão velha se o jogador evoluir, e a partida consumiria número obsoleto a
temporada inteira. Recalcular sozinho apagaria o histórico.

Dois campos, dois propósitos nomeados. É o espírito do `CongelamentoTest` do treinador
— o histórico não se reescreve — sem pagar o preço de a partida jogar com dado velho.

### 6. `TATICA` é repertório de formações, não multiplicador

O treinador de IA avalia as `1 + TATICA/2` primeiras formações do catálogo — de 1
formação com skill 1 até as 6 com skill 10.

Um multiplicador exigiria calibrar um coeficiente contra um motor de partida que não
existe. Repertório não exige calibragem nenhuma: treinador de `TATICA` baixa não joga
pior por um número mágico, ele simplesmente só conhece o 4-4-2 — e isso custa caro
quando o elenco tem três pontas.

É também o primeiro consumo real de uma das quatro skills ociosas.

### 7. As cinco instruções da IA são coerentes entre si por construção

`mentalidade` sai da `clube.reputacao` comparada à média de reputação de todos os
clubes. Os outros quatro eixos saem de uma tabela fixa indexada pela mentalidade.

A comparação é com a média global, não com a da divisão, porque saber a divisão exigiria
depender de `competicao` — e um clube pode disputar mais de uma competição, o que
tornaria "a divisão dele" ambíguo. O custo é que um gigante da segunda divisão joga
ofensivo contra todo mundo, inclusive contra pares da própria divisão. Aceito enquanto
não houver partida para mostrar se incomoda.

Cinco eixos sorteados independentemente produziriam um time ofensivo com linha recuada.
A tabela é o que impede a IA de se contradizer, e é um lugar só para rebalancear quando
a partida existir e mostrar o que funciona.

### 8. Sem controller nesta versão

`treinador` e `mundo` não têm `web/`; `jogador` e `clube` têm, porque são o catálogo
read-only. Módulo de decisão ficou sem HTTP porque não há autenticação para dizer quem
decide. `tatica` segue o precedente: a porta pública é Java, e o REST vem junto do spec
de autenticação.

## Arquitetura

### Dependências

| Módulo | Para quê | Como |
|---|---|---|
| `jogador` | quem está no elenco | `jogador_vinculo` por `(clube_id, temporada_id)` |
| `avaliacao` | encaixe posicional | `AvaliacaoService` — overall materializado por posição |
| `treinador` | estilo do escalador de IA | `TreinadorService` — as seis skills da temporada |
| `temporada`, `clube` | chaves | referências diretas |

Não depende de `competicao` nem de `mundo`. Ninguém depende de `tatica` hoje; `partida`
vai depender.

### Ajuste fora do módulo

`TreinadorService` hoje só sabe buscar por slug. O escalador precisa de "quem dirige o
clube X nesta temporada", então a porta pública do `treinador` ganha:

```java
Optional<TreinadorDetalhe> buscarPorClube(long clubeId, long temporadaId);
```

É acréscimo, não alteração — `vinculo_treinador` já tem os dados e o índice. Mas é
mexer num módulo recém-mergeado, e precisa estar explícito no plano.

### Schema — migrations `V20` e `V21`

Schema e seed separados, no padrão de `V6`/`V7`.

#### Catálogo

```sql
create table formacao (
    id     bigint  generated always as identity primary key,
    codigo text    not null unique check (length(codigo) between 3 and 12),
    nome   text    not null check (length(nome) between 1 and 60),
    ordem  integer not null unique check (ordem > 0)
);

create table formacao_slot (
    formacao_id bigint  not null references formacao (id),
    ordem       integer not null check (ordem between 1 and 11),
    posicao_id  bigint  not null references posicao (id),
    primary key (formacao_id, ordem)
);

create index idx_formacao_slot_posicao on formacao_slot (posicao_id);
```

Seed com seis formações: 4-4-2, 4-3-3, 4-2-3-1, 3-5-2, 5-3-2, 4-1-4-1. A `ordem` do
catálogo é a ordem em que o repertório cresce, então a primeira precisa ser a mais
genérica — 4-4-2.

"Exatamente 11 slots, exatamente um `GOL`" é invariante do seed, garantida por teste e
não por `check`. Mesma escolha que `posicao` fez.

#### Estado

```sql
create table plano_tatico (
    id              bigint  generated always as identity primary key,
    clube_id        bigint  not null references clube (id),
    temporada_id    bigint  not null references temporada (id),
    versao          integer not null check (versao > 0),
    vigente         boolean not null default true,
    formacao_id     bigint  not null references formacao (id),
    capitao_id      bigint  references jogador (id),
    origem          text    not null check (origem in ('MANUAL', 'AUTOMATICO')),
    mentalidade     text    not null check (mentalidade in ('MUITO_DEFENSIVA', 'DEFENSIVA',
                                                            'EQUILIBRADA', 'OFENSIVA',
                                                            'MUITO_OFENSIVA')),
    ritmo           text    not null check (ritmo in ('LENTO', 'EQUILIBRADO', 'INTENSO')),
    linha_defensiva text    not null check (linha_defensiva in ('RECUADA', 'MEDIA', 'ADIANTADA')),
    pressao         text    not null check (pressao in ('BAIXA', 'MEDIA', 'ALTA')),
    largura         text    not null check (largura in ('ESTREITA', 'MEDIA', 'ABERTA')),
    criado_em       timestamptz not null default now(),
    constraint uq_plano_versao unique (clube_id, temporada_id, versao)
);

create unique index uq_plano_vigente on plano_tatico (clube_id, temporada_id) where vigente;

comment on column plano_tatico.origem is
    'MANUAL quando o treinador escalou; AUTOMATICO quando o escalador preencheu';
```

```sql
create table plano_escalacao (
    plano_id           bigint  not null references plano_tatico (id),
    jogador_id         bigint  not null references jogador (id),
    papel              text    not null check (papel in ('TITULAR', 'RESERVA')),
    slot_ordem         integer check (slot_ordem  between 1 and 11),
    ordem_banco        integer check (ordem_banco between 1 and 12),
    aptidao_no_momento numeric(4,2) not null check (aptidao_no_momento between 0 and 99),
    primary key (plano_id, jogador_id),
    constraint ck_papel_coerente check (
        (papel = 'TITULAR' and slot_ordem is not null and ordem_banco is null) or
        (papel = 'RESERVA' and slot_ordem is null     and ordem_banco is not null)
    )
);

create unique index uq_escalacao_slot on plano_escalacao (plano_id, slot_ordem)
    where slot_ordem is not null;

create index idx_plano_escalacao_jogador on plano_escalacao (jogador_id);

comment on column plano_escalacao.aptidao_no_momento is
    'Overall do jogador na posição do slot, congelado na gravação. O valor atual vem do join com jogador_overall na leitura';
```

Os cinco eixos são `text` com `check`, o formato que `tipo`, `origem` e `pe_preferido`
já usam.

### O módulo `tatica`

Pastas `domain/`, `dto/`, `repository/`, `internal/`, `mapper/`, mais `package-info.java`
e `TaticaService`. Sem `web/` (decisão 8).

Os números — tamanho do repertório, limites do banco, faixas de mentalidade — vivem em
`ConstantesDeTatica`, espelhando `ConstantesDeTreinador`.

### Invariantes que ficam no serviço

Nenhuma cabe num `check` de linha, porque nenhuma enxerga as outras linhas do agregado:

- exatamente 11 titulares, e um slot para cada um
- exatamente um goleiro entre os titulares
- todo escalado tem `jogador_vinculo` naquele clube e temporada
- capitão, quando informado, é um dos 11 titulares — a coluna é anulável
- banco entre 5 e 12

`TaticaServiceImpl` é o único caminho de escrita, pelo mesmo motivo que
`TreinadorServiceImpl` é o único da soma das skills. `EscalacaoInvalidaTest` prova que
o banco sozinho aceita as cinco violações.

## As regras

### O treinador humano escala

`salvarPlano(clubeId, temporadaId, NovoPlano)` valida as cinco invariantes, marca a
versão vigente como `vigente = false`, insere a próxima versão com
`origem = 'MANUAL'` e grava as linhas com a aptidão lida de `jogador_overall` no
instante. Tudo numa transação; o índice parcial `uq_plano_vigente` garante que nem
concorrência produz dois vigentes.

Não existe update. Alterar é sempre versão nova.

### O escalador automático

`garantirPlanoVigente(clubeId, temporadaId)` é método explícito, não efeito colateral
de leitura. Se já há plano vigente, devolve; se não há, monta um com
`origem = 'AUTOMATICO'`. Quem chama: o gerador de `mundo`, para os 40 clubes de saída,
e no futuro a partida no apito inicial. `buscarPlanoVigente` continua puro e devolve
`Optional.empty()` quando não há plano.

**Passo 1 — escolher a formação.** O repertório é o das `1 + TATICA/2` primeiras
formações do catálogo, arredondando para baixo. Para cada formação do repertório, monta
o melhor onze possível pelo passo 2 e soma as aptidões; fica com a de maior soma,
desempatando pela `formacao.ordem`.

**Passo 2 — preencher os slots.** Guloso por escassez: a cada rodada, para cada slot
ainda vazio, calcular a diferença entre a melhor e a segunda melhor aptidão entre os
jogadores disponíveis; preencher primeiro o slot de maior diferença. Desempate por
`jogador.id`. Vínculos `categoria = 'BASE'` só entram se os profissionais não fecharem
o onze.

Não é ótimo — o guloso erra onde a atribuição húngara acertaria. A troca é aceita: com
~30 jogadores e 11 slots o erro é raro e pequeno, e a regra cabe em duas frases num
teste.

**Passo 3 — escolher as instruções.** `mentalidade` sai da `clube.reputacao` comparada
à média de reputação de todos os clubes (decisão 7); os outros quatro eixos saem da
tabela de coerência indexada pela mentalidade — ofensivo com linha adiantada, pressão
alta e campo aberto; defensivo com o oposto.

**Capitão:** o titular de maior `aptidao_no_momento`, desempatando por `jogador.id`.
Poderia sair de `LIDERANCA` ou da afinidade com o treinador, mas ambas são calibragem
contra um motor que não existe — e a braçadeira, hoje, não modifica nada.

**Banco:** os melhores restantes por aptidão na posição principal, sempre com ao menos
um goleiro.

### O contrato que a partida vai consumir

`buscarPlanoVigente(clubeId, temporadaId)` devolve um `PlanoVigente` com formação, os
cinco eixos, os 11 titulares — cada um com slot, posição, `aptidaoNoMomento`,
`aptidaoAtual` e a flag `irregular` —, o banco ordenado e o capitão.

Vale o aviso do risco 2 do spec do treinador: está sendo desenhado antes do consumidor
existir, e quem se adapta é a partida.

### O que esta camada não faz

Não reage a evento nenhum. `treinador` publica `VinculoIniciado`, e seria tentador
escalar automaticamente quando um treinador chega ao clube — mas plano é do clube, não
do vínculo (decisão 2). Sem listener nesta versão.

## Erros

Uma exceção nova em `shared/exception`, pelo motivo que `DistribuicaoInvalidaException`
já documenta — quem traduz para HTTP é o `TratadorDeErros` de `shared`, e ele não pode
enxergar tipo interno de módulo de domínio:

```java
public class EscalacaoInvalidaException extends RuntimeException  // → 422
```

Título "Escalação inválida". A mensagem nomeia a invariante quebrada — "10 titulares,
esperados 11", "dois goleiros entre os titulares", "jogador 412 não tem vínculo com o
clube 7 na temporada 2026" —, não uma genérica.

`RecursoNaoEncontradoException` cobre clube, temporada e formação inexistentes.

Três casos de borda que a validação de campo não resolve:

**Elenco insuficiente.** Menos de 11 jogadores com vínculo, ou nenhum goleiro.
`garantirPlanoVigente` lança `EscalacaoInvalidaException` em vez de devolver plano
incompleto. Falhar alto é melhor do que a partida descobrir isso em campo — e em base
zerada, sem o gerador de mundo, é exatamente o que acontece.

**Repertório vazio.** Treinador sem skills gravadas na temporada cai no repertório
mínimo, a primeira formação do catálogo, em vez de estourar. Um treinador recém-criado
ainda escala.

**Escalado que saiu do clube.** O plano vigente pode conter alguém cujo vínculo mudou.
Não há transferência no sistema hoje, mas `jogador_vinculo` já permite. `PlanoVigente`
marca esses titulares com a flag `irregular` e nada é regenerado automaticamente — é
informação suficiente para a partida decidir, sem inventar política de regeneração
antes de existir quem transfira.

## Testes

### Motores puros — JUnit sem Spring, sem Docker

| Teste | Afirma |
|---|---|
| `EscolhaDeFormacaoTest` | repertório cresce com `TATICA`; skill 1 avalia só a primeira formação; skill 10 avalia as seis; empate resolve por `formacao.ordem` |
| `PreenchimentoDeSlotsTest` | o guloso preenche antes o slot mais escasso; desempate por `jogador.id`; `BASE` só entra quando os profissionais não fecham o onze |
| `InstrucoesDaIaTest` | mentalidade sai da reputação relativa à média global; os outros quatro eixos são coerentes com ela — nunca ofensivo com linha recuada; capitão é o titular de maior aptidão |

### Integridade — com Testcontainers

| Teste | Afirma |
|---|---|
| `PlanoIntegridadeTest` | segundo plano vigente no mesmo clube e temporada é rejeitado **pelo banco**; titular sem slot e reserva com slot idem; dois titulares no mesmo slot idem |
| `EscalacaoInvalidaTest` | 10 titulares, dois goleiros, jogador de outro clube e capitão no banco são rejeitados **pelo serviço** — o banco sozinho aceita os quatro |
| `VersionamentoTest` | salvar duas vezes gera versões 1 e 2; só a 2 é vigente; a 1 preserva a aptidão congelada e as linhas de escalação |
| `CatalogoDeFormacaoTest` | cada uma das seis formações seedadas tem 11 slots e exatamente um `GOL` |
| `ModularidadeTest` (existente) | `tatica` não alcança tipo `internal` de outro módulo |

### Determinismo

| Teste | Afirma |
|---|---|
| `EscaladorDeterministicoTest` | mesmo elenco e mesma skill, duas execuções, mesmo onze e mesmas instruções |
| `AptidaoCongeladaTest` | alterar `jogador_overall` e reler o plano: `aptidaoNoMomento` inalterada, `aptidaoAtual` acompanha |

`AptidaoCongeladaTest` é o par do `CongelamentoTest` do treinador, e falha se alguém,
por simplificação, colapsar os dois campos em um.

### O que não é testado

Os limiares — `1 + TATICA/2`, as faixas de reputação que definem mentalidade, a tabela
de coerência entre eixos. São julgamento de domínio sem verdade de referência, como os
coeficientes do treinador e as faixas dos arquétipos do mundo. Os testes travam a
**forma** e a ordem relativa, não os valores. Rebalancear é editar
`ConstantesDeTatica`; um teste que quebre nisso estava medindo a coisa errada.

## Riscos

**1. Os cinco eixos e o capitão terminam sem consumidor.** Mentalidade, ritmo, linha
defensiva, pressão, largura e capitão ficam declarados, testados e ociosos — o motor
que os aplica não existe. É a repetição literal do risco 1 do spec do treinador, e é
intencional pelo mesmo motivo: a partida nasce sabendo o que consultar em vez de
inventar o próprio conceito de plano. Mas são seis campos corretos e sem efeito
visível, somados aos quatro do treinador.

**2. O contrato de `PlanoVigente` é desenhado antes do produtor de partidas existir.**
A chance de faltar um campo é real — condição física e disponibilidade são candidatos
óbvios, e ficaram fora. Mitigado por ser um DTO de leitura: acrescentar campo não quebra
quem já lê.

**3. O guloso por escassez pode montar um onze pior que o ótimo.** Aceito
explicitamente. O caso patológico é um elenco onde o mesmo jogador é a melhor opção em
três slots muito disputados. Se a partida mostrar que importa, trocar por atribuição
húngara é substituir uma função pura já isolada e coberta por teste.

**4. A tabela de coerência entre eixos engessa a IA.** Todos os treinadores de IA com a
mesma mentalidade jogam exatamente igual — 40 clubes, cinco comportamentos possíveis.
Numa liga de 8 a 10 amigos os ~30 clubes de IA vão parecer clones. Resolver exige
variação por semente ou por skill, e a semente do treinador já existe para isso; ficou
de fora por ser calibragem no vazio antes da partida existir.

**5. `clube.reputacao` é estática.** A mentalidade da IA sai dela, então o clube que
ganhar cinco campeonatos continuará jogando defensivo para sempre — e a média global,
por ser média de valores fixos, também nunca se move. É o risco 3 do spec do treinador
reaparecendo num segundo lugar, e o dono continua sendo o spec de progressão.

**6. Um plano vigente atravessa a temporada inteira sem revisão.** Um humano que escale
uma vez e não volte joga o ano com o mesmo onze, envelhecendo. É consequência direta de
plano ser do clube e não da partida (decisão 2), e só deixa de incomodar quando existir
calendário para pendurar a revisão.

## Fora de escopo

**Partida, calendário e rodada.** A camada 3, com spec próprio. `competicao` hoje tem
`Competicao → Edicao → Fase → EdicaoParticipante` e nenhuma tabela de jogos — construir
isso é parte daquele spec, não deste.

**Instruções durante o jogo.** O destino do FootFirma é partida em tempo real com
intervenção dos dois treinadores. Substituição, mudança de mentalidade no intervalo e
preleção são decisões tomadas dentro da partida, e não têm onde existir antes dela.

**Papéis individuais dentro da formação.** Ala que sobe, meia que recua, camisa 10
livre. Profundidade de Football Manager sem motor que valide se faz diferença.

**Condição física, lesão e suspensão.** Todo jogador com vínculo está disponível. As
três exigem calendário para acumular carga e cartão.

**API HTTP.** Decisão 8 — vem com o spec de autenticação.

**Efeito das skills em campo.** `TATICA` ganha consumo aqui, como repertório. As outras
três ociosas continuam ociosas.

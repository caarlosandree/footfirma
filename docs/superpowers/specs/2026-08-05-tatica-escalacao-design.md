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

### Ajustes fora do módulo

O escalador precisa de três coisas que nenhuma porta pública entrega hoje: quem dirige o
clube, o elenco com ids e categoria, e o overall de todo o elenco em todas as posições.
São três acréscimos, nenhum deles alterando assinatura existente.

**`treinador`** — a porta já expõe seis métodos (`buscarPorSlug`, `criar`,
`distribuirPontos`, `listarPropostasAbertas`, `aceitarProposta`, `recusarProposta`), e
nenhum parte do clube. Ganha um sétimo, mais um record no pacote raiz do módulo:

```java
// treinador/PerfilDeTreinador.java — pacote raiz, ao lado dos records de evento
public record PerfilDeTreinador(long treinadorId, int reputacao, int tatica) { }

// treinador/TreinadorService.java
Optional<PerfilDeTreinador> buscarPerfilDoClube(long clubeId, long temporadaId);
```

O retorno **não** é `TreinadorDetalhe`, e a razão é estrutural: `treinador/dto/` não tem
`package-info.java`, ao contrário de `jogador/dto/` e `avaliacao/dto/`, que declaram
`@NamedInterface("dto")`. Sem essa anotação o Modulith trata o subpacote como interno, e
`TreinadorDetalhe` — que ainda por cima expõe `Map<Skill, Integer>` de `treinador.domain`
— reprovaria em `ModularidadeTest` assim que `tatica` o referenciasse. Hoje isso não
aparece porque nenhum módulo consome `treinador`; `tatica` seria o primeiro.

A alternativa era anotar `treinador.dto` e `treinador.domain` como interfaces nomeadas,
o que publicaria seis DTOs e onze tipos de domínio para alimentar um consumidor que quer
dois inteiros. Um record de três campos no pacote raiz — onde os eventos já moram — custa
menos e vaza nada.

**`jogador`** — `listarElenco(slugClube, labelTemporada)` devolve `JogadorResumo`, que
traz a posição como texto e não traz a categoria. O escalador precisa de ids e de
`categoria` para aplicar a regra de `BASE`:

```java
// jogador/dto/JogadorDoElenco.java — o pacote já é @NamedInterface("dto")
public record JogadorDoElenco(Long jogadorId, Long posicaoPrincipalId,
                              String categoria, Integer numeroCamisa) { }

List<JogadorDoElenco> listarElencoParaEscalacao(long clubeId, long temporadaId);
```

`categoria` é `String` porque `CategoriaDeElenco` vive em `jogador.domain`, que é
interno. É a mesma travessia que `DadosDeVinculo` já faz na entrada.

**`avaliacao`** — `buscarPorSlug` devolve as nove posições de **um** jogador, por slug.
Montar um onze exige as nove de ~30 jogadores, e `jogador_overall` está em
`avaliacao.repository`, que o Modulith fecha:

```java
// avaliacao/dto/OverallDeJogador.java — o pacote já é @NamedInterface("dto")
public record OverallDeJogador(Long jogadorId, Long posicaoId, Integer overall) { }

List<OverallDeJogador> listarOveralls(Collection<Long> jogadorIds, long temporadaId);
```

Nenhum dos três é opcional, e `ModularidadeTest` roda `modulos.verify()` a cada build —
qualquer atalho por dentro de outro módulo quebra a suíte. `mundo` já consome
`AvaliacaoService` e `JogadorService` por esse caminho, então o padrão está estabelecido.

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
    posicao_id         bigint  not null references posicao (id),
    aptidao_no_momento integer not null check (aptidao_no_momento between 0 and 99),
    primary key (plano_id, jogador_id),
    constraint ck_papel_coerente check (
        (papel = 'TITULAR' and slot_ordem is not null and ordem_banco is null) or
        (papel = 'RESERVA' and slot_ordem is null     and ordem_banco is not null)
    )
);

create unique index uq_escalacao_slot on plano_escalacao (plano_id, slot_ordem)
    where slot_ordem is not null;

create unique index uq_escalacao_banco on plano_escalacao (plano_id, ordem_banco)
    where ordem_banco is not null;

create index idx_plano_escalacao_jogador on plano_escalacao (jogador_id);

comment on column plano_escalacao.posicao_id is
    'Em que posição o jogador foi escalado. Do slot da formação, se titular; de jogador.posicao_principal_id, se reserva. Gravada e nunca inferida: é a base do congelamento e do recálculo';
comment on column plano_escalacao.aptidao_no_momento is
    'Overall do jogador em posicao_id, congelado na gravação. O valor atual vem do join com jogador_overall na leitura';
```

`aptidao_no_momento` é `integer` porque a fonte é `integer`: `jogador_overall.overall`
tem `check (overall between 0 and 99)`. Congelar um inteiro num `numeric(4,2)`
prometeria casas decimais que ninguém produz.

`posicao_id` existe porque o congelamento precisa ser autodescritivo. Para o titular a
posição é derivável do `formacao_slot`; para o reserva, de `jogador.posicao_principal_id`
— mas derivar tem dois problemas. A formação do plano pode mudar na versão seguinte, e
`posicao_principal_id` é coluna mutável de `jogador`: em ambos os casos, a base do
número congelado se moveria depois de gravado, e `aptidaoAtual` passaria a ser calculada
sobre uma posição diferente da que gerou `aptidaoNoMomento`. Os dois campos da decisão 5
só são comparáveis se olharem a mesma posição, e é esta coluna que garante isso.

`uq_escalacao_banco` é o par de `uq_escalacao_slot`. Sem ele, dois reservas com
`ordem_banco = 3` passam, e o DTO de leitura entrega um banco cuja ordem não é
determinística. Pela decisão 3, invariante que o banco consegue segurar fica no banco.

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
- banco entre 5 e 12, com as ordens de 1 a N sem saltos

A unicidade da ordem do banco não está nesta lista: `uq_escalacao_banco` a segura no
banco. O que sobra aqui é a continuidade — que as ordens formem `1..N` sem buracos —,
e essa o índice não alcança.

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
o onze — e `PROFISSIONAL` é o default da coluna, então o filtro exclui a minoria, não a
maioria.

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

**Banco:** os melhores restantes pelo overall em `jogador.posicao_principal_id`, sempre
com ao menos um goleiro. É essa posição que vai para `plano_escalacao.posicao_id` das
linhas de reserva, e é sobre ela que a aptidão do reserva é congelada e recalculada.

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
| `EscolhaDeFormacaoTest` | o repertório nos seis limiares de `1 + TATICA/2` com divisão inteira — skills 1, 3, 5, 7, 9 e 10 dando 1, 2, 3, 4, 5 e 6 formações; empate resolve por `formacao.ordem` |
| `PreenchimentoDeSlotsTest` | o guloso preenche antes o slot mais escasso; desempate por `jogador.id`; `BASE` só entra quando os profissionais não fecham o onze |
| `InstrucoesDaIaTest` | mentalidade sai da reputação relativa à média global; os outros quatro eixos são coerentes com ela — nunca ofensivo com linha recuada; capitão é o titular de maior aptidão |

### Integridade — com Testcontainers

| Teste | Afirma |
|---|---|
| `PlanoIntegridadeTest` | segundo plano vigente no mesmo clube e temporada é rejeitado **pelo banco**; titular sem slot e reserva com slot idem; dois titulares no mesmo slot idem; dois reservas na mesma `ordem_banco` idem |
| `EscalacaoInvalidaTest` | 10 titulares, dois goleiros, jogador de outro clube, capitão no banco e banco com buraco na ordem são rejeitados **pelo serviço** — o banco sozinho aceita os cinco |
| `VersionamentoTest` | salvar duas vezes gera versões 1 e 2; só a 2 é vigente; a 1 preserva a aptidão congelada e as linhas de escalação |
| `CatalogoDeFormacaoTest` | cada uma das seis formações seedadas tem 11 slots e exatamente um `GOL` |
| `ModularidadeTest` (existente) | `tatica` não alcança tipo `internal` de outro módulo |

### Determinismo

| Teste | Afirma |
|---|---|
| `EscaladorDeterministicoTest` | mesmo elenco e mesma skill, duas execuções, mesmo onze e mesmas instruções |
| `AptidaoCongeladaTest` | alterar `jogador_overall` e reler o plano: `aptidaoNoMomento` inalterada, `aptidaoAtual` acompanha; alterar `jogador.posicao_principal_id` de um reserva não move nenhuma das duas |

`AptidaoCongeladaTest` é o par do `CongelamentoTest` do treinador, e falha em duas
simplificações: colapsar os dois campos em um, e derivar a posição do reserva na leitura
em vez de ler `plano_escalacao.posicao_id`.

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

**6. A camada abre três portas em três módulos antes de entregar valor visível.**
`treinador`, `jogador` e `avaliacao` ganham um método público cada para alimentar um
consumidor único. Se `tatica` for descartada ou redesenhada, os três acréscimos ficam
órfãos. Mitigado por serem pequenos e por nenhum deles alterar assinatura existente — mas
é dívida de superfície pública contraída antes de a partida provar que a camada serve.

**7. Um plano vigente atravessa a temporada inteira sem revisão.** Um humano que escale
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

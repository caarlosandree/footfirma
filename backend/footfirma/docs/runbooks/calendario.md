# Gerar o calendário

Status: verificado em 2026-08-05

## Fontes

- `../../src/main/java/br/com/api/footfirma/calendario/CalendarioService.java`
- `../../src/main/java/br/com/api/footfirma/calendario/internal/CalendarioServiceImpl.java`
- `../../src/main/java/br/com/api/footfirma/calendario/internal/TipadorDeRodadas.java`
- `../../src/main/java/br/com/api/footfirma/calendario/internal/AlocadorDeDatas.java`
- `../../src/main/java/br/com/api/footfirma/calendario/internal/ConstantesDeCalendario.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/PerfisDeLiga.java`
- `../../src/main/resources/db/migration/V22__cria_calendario.sql`
- `../superpowers/specs/2026-08-05-calendario-rodada-design.md`

## O que é

`calendario` guarda rodada, confronto e jogo — a primeira coisa do sistema que **muda
durante a temporada**. `competicao` continua sendo catálogo: ele sabe quem disputa, não
quem enfrenta quem nem quando.

O confronto é o slot; o jogo é a partida. Em pontos corridos a distinção parece
burocracia, mas em mata-mata de ida e volta quem avança se decide pela soma de dois jogos,
com gol fora e pênaltis — e isso não teria onde morar se só existisse jogo.

## Como gerar

Não há endpoint: a única escrita é `CalendarioService.gerarTemporada`, e quem a chama é o
gerador de mundo. Para gerar o mundo inteiro, incluindo calendário:

```bash
! ./gradlew bootRun --args='--spring.profiles.active=mundo'
```

Ver `mundo.md` para o resto do que o gerador produz.

## A precedência decide o calendário

`gerarTemporada` recebe **a lista de edições**, não uma edição por chamada, e ordena por
`EdicaoParaGerar.precedencia`. Quem gera primeiro ocupa os melhores dias; quem vem depois
se acomoda no que sobrou.

Isso é geração incremental de propósito: acrescentar uma copa não refaz as ligas. O preço
é que a ordem importa — e por isso ela é argumento, e não a sequência das linhas de quem
chama. No mundo gerado, a primeira divisão tem precedência 1 e a segunda, 2.

## Rodada tem tipo, e o tipo decide os dias

Toda rodada nasce `FIM_DE_SEMANA` ou `MEIO_DE_SEMANA`. A regra é aritmética de semanas:
cabem `dias / 7` semanas na janela da edição, e o que passar disso vira meio de semana,
espalhado uniformemente.

No mundo gerado são 38 rodadas em 246 dias — 35 semanas. Três rodadas viram de meio de
semana, e **ocupam a quarta da mesma semana de um domingo**, sem consumir a semana. É esse
detalhe que faz as 38 caberem, e é o que produz as semanas em que um clube joga duas
vezes.

## O descanso é a restrição real

Não há grade fixa de dias. O que o gerador garante é que um clube tenha um intervalo
mínimo entre jogos consecutivos, **em qualquer competição da temporada**. O valor inicial
é 3 dias, em `ConstantesDeCalendario` — e não é arbitrário: domingo → quarta são
exatamente 3 dias, e quarta → domingo, 4.

Cada jogo é alocado no primeiro dia da janela da rodada que respeite o descanso dos dois
clubes, seguindo a ordem de dias sorteada pelos pesos do perfil. Se nenhum dia da janela
serve, o jogo vai para o primeiro dia posterior que sirva — e **a rodada não desliza**,
porque deslizá-la propagaria em cascata por todas as seguintes.

Confira depois de gerar:

```sql
with agenda as (
  select mandante_id as clube_id, data_jogo from jogo where data_jogo is not null
  union all select visitante_id, data_jogo from jogo where data_jogo is not null
), consecutivos as (
  select clube_id, data_jogo,
         lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
  from agenda
)
select count(*) as violacoes from consecutivos
where anterior is not null and data_jogo - anterior < 3;
```

## O perfil não é persistido

`PerfilDeCalendario` — os pesos de cada dia por tipo de rodada, mais o descanso — é
**entrada de geração**, como a semente. Não há tabela para ele. Os dois perfis do mundo
vivem em `PerfisDeLiga`, ao lado de `CatalogoDeArquetipos`.

Uma consequência: a realocação de datas do mata-mata (abaixo) acontece muito depois da
geração, disparada por um resultado, e não tem o perfil da competição à mão. Ela usa um
perfil padrão declarado em `CalendarioServiceImpl`. Se um dia isso incomodar, é o sinal de
que o perfil precisa virar dado.

## O mata-mata nasce inteiro e vazio

A árvore de eliminatória existe em banco desde o sorteio: a primeira fase com clubes, as
seguintes com `origem_lado_a` e `origem_lado_b` apontando para os confrontos que as
alimentam. A semifinal existe antes dos semifinalistas.

Os jogos das fases posteriores nascem **datados**, com a data do alvo da rodada, mas sem
mandante, visitante e estádio. Quando o confronto ganha os dois clubes, esses três campos
são preenchidos e a data é **realocada** pela regra de descanso — ela foi marcada sem
saber quem jogaria, então o descanso nunca tinha sido verificado para aqueles clubes.

É o que acontece de fato: a CBF só detalha data e hora do mata-mata depois dos
classificados.

## O que este módulo não faz

- **Não simula.** O placar chega pronto por `registrarResultado`, e quem o produz será
  `partida`. Hoje o único produtor é teste.
- **Não classifica.** `RegraClassificacao` continua em `competicao` sem consumidor;
  ordenar clubes por pontos é outro spec. Por isso `confronto.vencedor_clube_id` fica nulo
  para sempre em pontos corridos e grupos.
- **Não adia.** `SituacaoDoJogo` tem `AGENDADO` e `ENCERRADO`, e mais nada: jogo fora da
  janela da rodada é apenas um jogo em outro dia.

## Estado atual

Pontos corridos é o único tipo exercitado em produção — o mundo gerado tem duas ligas e
nenhuma copa. Grupos e eliminatória funcionam e são provados por
`ChaveamentoTest` e `EliminatoriaIntegridadeTest`, mas nascem sem consumidor real.

```bash
./gradlew test --tests '*Calendario*' --tests '*Chaveamento*' \
               --tests '*Eliminatoria*' --tests '*Geracao*' \
               --tests '*TabelaDeBerger*' --tests '*Tipagem*' \
               --tests '*SorteioDe*' --tests '*Alocacao*' --tests '*Resolucao*'
```

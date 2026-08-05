# Gerar o mundo

Status: verificado em 2026-08-05

## Fontes

- `../../src/main/java/br/com/api/footfirma/mundo/internal/MundoServiceImpl.java`
- `../../src/main/java/br/com/api/footfirma/tatica/TaticaService.java`
- `../../src/main/java/br/com/api/footfirma/calendario/CalendarioService.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/PerfisDeLiga.java`
- `../../src/main/java/br/com/api/footfirma/tatica/internal/EscaladorAutomatico.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/CatalogoDeArquetipos.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/PapelNoElenco.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/LimpezaDoCatalogo.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/PropriedadesDeMundo.java`
- `../../src/main/resources/application.properties`
- `../adr/2026-08-03-mundo-gerado.md`

## O que é

Em base zerada o catálogo fica **vazio de clubes e jogadores** — os endpoints
respondem lista vazia e 404, e isso é esperado. O gerador popula duas ligas nacionais
fictícias a partir de uma semente.

Tudo é inventado: clubes, estádios, apelidos, cores e jogadores. Só a geografia é
real — cidade e UF resolvem contra o seed de `V4__popula_geografia.sql`.

## Como gerar

O guard bloqueia subir serviço. **Quem roda o comando é você**, na própria sessão:

```bash
! ./gradlew bootRun --args='--spring.profiles.active=mundo'
```

O `MundoRunner` só existe sob o profile `mundo`; subir a API normalmente nunca
escreve no catálogo. Com `footfirma.mundo.encerrar-ao-final=true` (o default), a
aplicação encerra ao terminar, como um job.

## Conteúdo esperado

| Entidade | Linhas |
|---|---|
| `competicao` / `edicao` / `fase` | 2 / 2 / 2 |
| `clube` / `estadio` | 40 / 40 |
| `edicao_participante` | 40 (20 por divisão) |
| `jogador` | 1.520 (26 profissionais + 12 de base por clube) |
| `jogador_atributo` / `jogador_vinculo` | 1.520 cada |
| `jogador_overall` | 13.680 (nove posições por jogador) |
| `plano_tatico` | 40 (um vigente por clube, `origem = 'AUTOMATICO'`) |
| `plano_escalacao` | 920 (11 titulares + 12 no banco por clube) |
| `rodada` | 76 (38 por divisão: turno e returno de 20 clubes) |
| `confronto` / `jogo` | 760 cada (380 por divisão, um jogo por confronto) |

Confira depois de gerar:

```sql
select
  (select count(*) from clube) as clubes,
  (select count(*) from jogador) as jogadores,
  (select count(*) from jogador_overall) as overalls,
  (select count(*) from plano_tatico where vigente) as planos,
  (select count(*) from jogo) as jogos;
```

## O calendário

Sai logo depois dos participantes, e antes dos elencos: o gerador de calendário precisa
saber quem disputa, e não precisa de overall nem de plano.

A primeira divisão é gerada antes da segunda, e isso é **argumento**, não a ordem das
linhas em `MundoServiceImpl` — quem gera primeiro ocupa os melhores dias. A precedência
vive em `EdicaoParaGerar`, e o módulo ordena por ela.

Cada divisão tem um perfil em `PerfisDeLiga`: os dias em que joga, com peso. Os dois
perfis compartilham o leque e se distinguem pelo peso — a primeira concentra no domingo,
a segunda vaza mais para segunda e terça. **Nenhum jogo cai na sexta**, como na tabela
real da CBF.

As 38 rodadas cabem nos 246 dias da edição porque três delas nascem de meio de semana e
ocupam a quarta da mesma semana de um domingo, em vez de consumir uma semana só para si.

Confira o descanso depois de gerar — nenhum clube joga com menos de três dias de folga:

```sql
with agenda as (
  select mandante_id as clube_id, data_jogo from jogo
  union all select visitante_id, data_jogo from jogo
), consecutivos as (
  select clube_id, data_jogo,
         lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
  from agenda
)
select count(*) as violacoes from consecutivos
where anterior is not null and data_jogo - anterior < 3;
```

## A escalação vem por último

O gerador chama `TaticaService.garantirPlanoVigente` para os 40 clubes **depois** de
materializar o overall, e a ordem não é arbitrária: o escalador lê `jogador_overall`
para decidir quem joga e para congelar `aptidao_no_momento`. Chamado antes, ele
escalaria onze jogadores com aptidão zero.

O plano sai com `versao = 1`, `origem = 'AUTOMATICO'` e capitão preenchido. Nenhum é
`MANUAL` — não há treinador humano em mundo gerado.

Se o elenco de um clube não fechar um time, `EscalacaoInvalidaException` sobe e a
geração para com parte dos clubes escalados. Como qualquer falha aqui, a saída é gerar
de novo com `recriar=true`.

## Como trocar o mundo

```bash
! ./gradlew bootRun --args='--spring.profiles.active=mundo \
    --footfirma.mundo.semente=42 --footfirma.mundo.recriar=true'
```

**Trocar a semente sem `recriar=true` deixa o mundo anterior órfão.** Os slugs novos
não colidem com os antigos, então o upsert cria tudo de novo em vez de atualizar, e o
banco fica com dois mundos sobrepostos.

Com a mesma semente e sem `recriar`, a geração é idempotente: atualiza no lugar e não
duplica.

## O que a limpeza apaga

`recriar=true` esvazia, na ordem inversa das chaves estrangeiras: `jogo`, `confronto`,
`rodada`, `plano_escalacao`,
`plano_tatico`, `jogador_overall`, `jogador_vinculo`, `jogador_atributo`,
`jogador_atributo_oculto`, `jogador_caracteristica`, `jogador_posicao`,
`jogador_referencia_externa`, `jogador`, `regra_classificacao`, `edicao_participante`,
`fase`, `edicao`, `competicao_referencia_externa`, `competicao`, `clube_alias`,
`clube_referencia_externa`, `clube`, `estadio`.

As cinco primeiras abrem a lista porque referenciam `jogador`, `clube`, `estadio` e
`fase` — sem elas, a limpeza falha por chave estrangeira assim que existe um plano ou um
jogo gravado.

Antes dos `delete`, a limpeza roda um `update confronto set origem_lado_a = null,
origem_lado_b = null`. `confronto` referencia a si mesma no chaveamento de mata-mata, e um
`delete` linear violaria essa FK. Um `update` resolve sem `on delete cascade` na
auto-referência — que faria apagar as quartas levar a semifinal junto — e sem quebrar a
lista acima em passos por fase.

**Não apaga** país, estado, posição, característica, perfil de avaliação e o catálogo
de formações (`formacao`, `formacao_slot`): são seed de migration, e o gerador depende
deles.

## Como rebalancear

Dois arquivos, nesta ordem de preferência:

- `CatalogoDeArquetipos` — as faixas de reputação, finanças e base de cada perfil, e
  quantos clubes de cada perfil vivem em cada divisão. As quotas precisam somar 20 por
  divisão.
- `PapelNoElenco` — as cotas de cada papel, seus deltas de overall e faixas de idade.
  As quantidades precisam somar 26 no profissional e 12 na base.

Depois de mexer, rode:

```bash
./gradlew test --tests '*FabricaDeClubesTest' \
               --tests '*FabricaDeElencoTest' \
               --tests '*MundoBalanceamentoTest'
```

Os dois primeiros são puros e rodam em segundos. O terceiro exige Docker e é o que
verifica as propriedades no banco: primeira divisão acima da segunda, um destaque por
clube, joia em clube pobre e overall médio seguindo o nível do elenco.

**O nível do clube é comprimido antes de virar overall** (`38 + 0,45 × índice`, em
`ClubeGerado`). Ao mexer nas faixas, lembre que reputação 90 não produz elenco de
overall 90 — produz elenco na casa dos 76 com estrela nos 90.

## Como validar

```bash
./gradlew test --tests '*Mundo*' --tests '*Fabrica*'
```

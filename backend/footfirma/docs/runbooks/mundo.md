# Gerar o mundo

Status: verificado em 2026-08-03

## Fontes

- `../../src/main/java/br/com/api/footfirma/mundo/internal/MundoServiceImpl.java`
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

Confira depois de gerar:

```sql
select
  (select count(*) from clube) as clubes,
  (select count(*) from jogador) as jogadores,
  (select count(*) from jogador_overall) as overalls;
```

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

`recriar=true` esvazia, na ordem inversa das chaves estrangeiras: `jogador_overall`,
`jogador_vinculo`, `jogador_atributo`, `jogador_atributo_oculto`,
`jogador_caracteristica`, `jogador_posicao`, `jogador_referencia_externa`, `jogador`,
`regra_classificacao`, `edicao_participante`, `fase`, `edicao`,
`competicao_referencia_externa`, `competicao`, `clube_alias`,
`clube_referencia_externa`, `clube`, `estadio`.

**Não apaga** país, estado, posição, característica e perfil de avaliação: são seed de
migration, e o gerador depende deles.

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

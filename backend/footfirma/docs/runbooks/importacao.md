# Runbook — carga do dataset

Status: verificado em 2026-08-03

## Fontes

- `../../src/main/java/br/com/api/footfirma/importacao/internal/ExecutorDeImportacao.java`
- `../../src/main/java/br/com/api/footfirma/importacao/internal/PropriedadesDeImportacao.java`
- `../../src/main/java/br/com/api/footfirma/importacao/internal/ValidadorDeDataset.java`
- `../../src/main/resources/application.properties`
- `../../src/main/resources/db/migration/V14__cria_importacao.sql`
- `../../fixtures/README.md`
- `../adr/2026-08-03-ingestao-pelos-modulos.md`

## Quando usar

O catálogo nasce vazio de clubes e jogadores. Rodar a carga popula `clube`, `jogador`,
`competicao` e tudo que depende deles a partir de `fixtures/v1`, e materializa o
overall ao final.

Sem a carga, `/api/v1/clubes` responde lista vazia e `/api/v1/jogadores/{slug}`
responde 404. Isso é esperado, não defeito.

## Como rodar

O `guard.mjs` bloqueia `bootRun` para agentes. **Quem dispara a carga é você**, na
própria sessão:

```bash
cd backend/footfirma
./gradlew bootRun --args='--spring.profiles.active=importacao'
```

O `ExecutorDeImportacao` só existe sob o profile `importacao` — subir a API sem ele
nunca escreve no catálogo.

Ao terminar, a JVM encerra com código `0` (status `CONCLUIDA`) ou `1` (`FALHOU`).
Para inspecionar a API logo depois da carga, sem o processo morrer:

```bash
./gradlew bootRun --args='--spring.profiles.active=importacao \
    --footfirma.importacao.encerrar-ao-final=false'
```

Para carregar outro diretório:

```bash
./gradlew bootRun --args='--spring.profiles.active=importacao \
    --footfirma.importacao.diretorio=/caminho/para/dataset'
```

## Como ler o relatório

O log traz uma linha por etapa e o total materializado:

```
Importação 1 — dataset fixtures-v1 — status CONCLUIDA
  temporada: 2 lidos, 2 criados, 0 atualizados, 0 recusados
  clube: 8 lidos, 8 criados, 0 atualizados, 0 recusados
  jogador: 176 lidos, 176 criados, 0 atualizados, 0 recusados
  …
  overall: 3168 linhas materializadas
```

Numa segunda execução sobre o mesmo dataset, **todo `criados` é 0** e todo
`atualizados` iguala o `lidos`. Se algum `criados` for diferente de zero, a
idempotência quebrou — investigue a chave de upsert daquela entidade.

## Consultas de inspeção

```sql
select id, dataset_versao, status, iniciada_em, finalizada_em, motivo_da_falha
  from importacao_execucao order by iniciada_em desc limit 5;

select entidade, lidos, criados, atualizados, recusados
  from importacao_contagem where execucao_id = :id order by entidade;

select entidade, linha, chave, severidade, motivo
  from importacao_ocorrencia where execucao_id = :id order by entidade, linha;
```

## O que fazer quando aparece ocorrência

Ocorrência **não interrompe a carga**. Ela registra uma linha que o importador
recusou antes de tocar no banco.

| Severidade | Significa | O que fazer |
|---|---|---|
| `ERRO` | A linha não entrou. Referência obrigatória ausente. | Corrija o dataset e recarregue — a carga é idempotente, reimportar não duplica. |
| `AVISO` | A linha entrou, mas um campo opcional ficou nulo. | Avalie se importa. UF fora do seed de geografia, por exemplo, deixa `estado_id` nulo. |

`motivo` e `chave` apontam o registro; `linha` aponta a posição no CSV, contando o
cabeçalho como linha 1.

## O que fazer quando o status é `FALHOU`

Falha significa que uma etapa abortou — quase sempre violação de constraint. Diferente
de ocorrência: aqui a transação daquela etapa foi desfeita inteira.

1. Leia `motivo_da_falha` em `importacao_execucao`.
2. Veja em `importacao_contagem` quais etapas chegaram a completar — a que falhou não
   tem linha lá.
3. Corrija o dataset e recarregue. As etapas que já haviam completado serão
   reprocessadas como atualização, sem duplicar.

## Quando a carga aborta sem deixar registro

Se `importacao_execucao` não ganhou nenhuma linha, o dataset foi rejeitado na
validação, **antes** de a execução ser aberta. A mensagem no log diz qual das quatro
regras falhou:

- `schema versão 'X' não suportada` — o dataset foi gerado para outro contrato.
- `checksum de X.csv não confere` — alguém editou o CSV sem regerar o manifesto.
  Rode `./gradlew gerarFixtures` ou recalcule o manifesto.
- `X.csv tem N linhas de dados; manifesto declara M` — mesma causa.
- `X.csv existe no diretório mas não está no manifesto` — arquivo esquecido.

Isso é por construção: dataset inválido não produziu carga nenhuma, e uma linha
`FALHOU` sem dataset válido não informaria nada.

## Como validar

```bash
./gradlew test --tests '*ImportacaoIdempotenciaTest' --tests '*ImportacaoOcorrenciaTest'
```

Depois da carga, com a API de pé:

```bash
curl -s localhost:8080/api/v1/clubes | head -40
curl -s 'localhost:8080/api/v1/rankings?temporada=2026&posicao=ATA&size=5'
```

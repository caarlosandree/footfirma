# A ingestão passa pelos módulos, e ocorrência é falha pré-banco

Status: verificado em 2026-08-03

## Fontes

- `../../src/main/java/br/com/api/footfirma/importacao/internal/ImportacaoServiceImpl.java`
- `../../src/main/java/br/com/api/footfirma/importacao/internal/RegistroDeExecucao.java`
- `../../src/main/java/br/com/api/footfirma/shared/dto/ResultadoDeSincronizacao.java`
- `../../src/main/java/br/com/api/footfirma/clube/ClubeService.java`
- `../../src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- `../../src/main/resources/db/migration/V14__cria_importacao.sql`
- `../../src/test/java/br/com/api/footfirma/importacao/ImportacaoIdempotenciaTest.java`
- `../../src/test/java/br/com/api/footfirma/importacao/ImportacaoOcorrenciaTest.java`
- `../../../../docs/superpowers/specs/2026-08-01-catalogo-futebol-brasileiro-design.md`

## Decisão

Duas decisões ligadas, ambas do módulo `importacao`.

**1. O importador orquestra e não escreve em tabela alheia.** Cada módulo de catálogo
expõe métodos `sincronizar*` na sua interface pública, todos com a mesma assinatura:

```java
ResultadoDeSincronizacao sincronizarX(DadosDeX dados);
```

`ImportacaoServiceImpl` lê o CSV, resolve slug → id nos seus próprios mapas de memória
e chama esses métodos na ordem de dependência. Nenhum repository de outro módulo entra
no seu construtor.

**2. Ocorrência é linha recusada ANTES do banco; erro de banco aborta a etapa.** Uma
referência ausente é detectável em `HashMap`, antes de qualquer `INSERT` — vira
`importacao_ocorrencia` com severidade `ERRO` e a carga prossegue. Uma violação de
constraint, não: derruba a etapa inteira e a execução termina com status `FALHOU`.

## Por quê

**Sobre a primeira.** Um importador com acesso direto aos repositories economizaria
~15 métodos públicos e um record por entidade. O preço seria o significado do
`ModularidadeTest`: um módulo que escreve em todas as tabelas torna a verificação de
fronteira decorativa. A superfície é o custo de a fronteira continuar valendo.

**Sobre a segunda.** Capturar `DataIntegrityViolationException` para "continuar" não
funciona: o JPA marca a transação como *rollback-only*, e o que segue é uma transação
zumbi cujo commit falha no fim — gravando nada e reportando sucesso. Falha silenciosa
é o pior resultado possível para uma carga de dados.

Com a divisão atual, dado ruim previsível vira relatório e dado ruim imprevisto vira
falha ruidosa. O que não existe é o caso do meio.

## Consequências

- `ResultadoDeSincronizacao` devolve `criado`, não só o id. É o que permite provar que
  a segunda importação do mesmo dataset não criou nada.
- Três `sincronizar*` de `jogador` devolvem o `jogadorId` no lugar de um id próprio:
  `jogador_posicao`, `jogador_atributo_oculto` e `jogador_caracteristica` têm chave
  composta ou compartilhada e não possuem coluna `id`.
- Enums de domínio (`PeParaChute`, `TipoVinculo`, `FonteExterna`, …) não cruzam
  fronteira: os records `DadosDe*` os transportam como `String` e o módulo dono
  converte.
- A validação de referência acontece em memória, não em FK. A FK continua no banco
  como rede de segurança, não como mecanismo de detecção.
- `RegistroDeExecucao` usa `REQUIRES_NEW` nos três métodos. Sem isso, uma carga que
  quebra na metade não deixaria rastro — o pior comportamento para auditoria.
- `ImportacaoServiceImpl` tem 8 dependências, acima do limite de 4~5 de
  `.rules/java-core.md`. É o orquestrador, e cada dependência é um módulo do catálogo;
  dividi-lo só moveria a lista de lugar.
- Uma transação por etapa (arquivo), via `TransactionTemplate`. A carga inteira numa
  transação seguraria a conexão por minutos; uma por linha multiplicaria o custo por
  ~1.500 sem ganho.

## Como validar

```bash
./gradlew test --tests '*ModularidadeTest' \
               --tests '*ImportacaoIdempotenciaTest' \
               --tests '*ImportacaoOcorrenciaTest'
```

```bash
# Não deve imprimir nada: importacao só importa repository do próprio módulo.
# Filtrar por "Repository" no corpo não serve — os repositories de auditoria são
# do próprio importacao e apareceriam como falso positivo.
grep -rn "^import br\.com\.api\.footfirma\..*\.repository\." \
    src/main/java/br/com/api/footfirma/importacao/ \
    | grep -v "footfirma\.importacao\.repository"
```

```bash
# Não deve imprimir nada: o catálogo continua sem endpoint de escrita
grep -rn "PostMapping\|PutMapping\|PatchMapping\|DeleteMapping" \
    src/main/java/br/com/api/footfirma/*/web/
```

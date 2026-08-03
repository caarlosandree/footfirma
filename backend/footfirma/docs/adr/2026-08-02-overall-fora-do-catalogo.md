# Overall é servido por avaliacao, não pelo catálogo

Status: verificado em 2026-08-03

## Fontes

- `../../../../docs/superpowers/specs/2026-08-02-avaliacao-overall-design.md`
- `src/main/java/br/com/api/footfirma/avaliacao/web/AvaliacaoController.java`
- `src/main/java/br/com/api/footfirma/jogador/JogadorService.java`
- `src/main/java/br/com/api/footfirma/avaliacao/internal/CalculadoraDeOverall.java`
- `src/main/java/br/com/api/footfirma/avaliacao/internal/AvaliacaoServiceImpl.java`
- `src/main/resources/db/migration/V12__cria_avaliacao.sql`
- `src/main/resources/db/migration/V13__popula_perfil_avaliacao.sql`
- `src/test/java/br/com/api/footfirma/ModularidadeTest.java`

## Decisão

O overall é calculado e servido pelo módulo `avaliacao`, que depende de `jogador` e
`temporada`. O `JogadorController` não devolve overall, e `avaliacao` lê o catálogo
apenas pela interface `JogadorService` — nunca por consulta a tabela alheia.

## Por quê

Embutir overall em `JogadorDetalhe` inverteria a dependência: rebalancear pesos
passaria a alterar o contrato do catálogo. A regra de leitura é a simétrica da que o
spec do catálogo já impõe ao importador ("não escreve em tabela alheia"); sem ela, um
join contra `jogador` tornaria a fronteira decorativa.

## Consequências

- A tela de jogador faz duas chamadas: `/api/v1/jogadores/{slug}` e
  `/api/v1/jogadores/{slug}/overall`.
- `JogadorService` ganhou quatro métodos de leitura em lote — `listarPosicoes`,
  `listarAtributosPorTemporada`, `listarResumosPorIds` e `buscarIdPorSlug`. É o custo
  da fronteira, e `listarResumosPorIds` existe especificamente para que o ranking não
  resolva uma página de vinte jogadores um a um.
- Referências a outros módulos são coluna `Long`, não associação JPA: a integridade
  fica nas chaves estrangeiras da `V12`, seguindo o que `Jogador.paisId` já fazia.
- Rebalancear pesos cria `versao = 2`; o `UPDATE` em perfil ativo está fora de questão.
  Um índice parcial (`uq_perfil_avaliacao_ativo`) garante um único ativo por posição.
- A materialização não é exposta por REST. Seria um `POST` no catálogo, e derrubaria a
  verificação do ADR anterior.
- O ranking é global por temporada. Filtrar por competição atravessaria `competicao` e
  `jogador`, e ficou fora deste ciclo.
- `jogador_overall` fica vazia até o importador do Plano 3 rodar: em banco real, os dois
  endpoints respondem lista vazia e 404 até lá.

## Como validar

```bash
./gradlew test --tests '*ModularidadeTest'
./gradlew test --tests '*PerfilAvaliacaoIntegridadeTest'

# Nenhuma associação JPA cruzando fronteira de módulo
grep -rn "ManyToOne\|OneToOne\|ManyToMany" src/main/java/br/com/api/footfirma/avaliacao/

# Catálogo segue sem endpoint de escrita
grep -rn "PostMapping\|PutMapping\|PatchMapping\|DeleteMapping" \
    src/main/java/br/com/api/footfirma/*/web/
```

Os dois `grep` devem sair sem nenhuma linha.

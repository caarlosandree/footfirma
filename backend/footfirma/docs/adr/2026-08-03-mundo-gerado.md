# O mundo é gerado, e o gerador orquestra sem escrever em tabela alheia

Status: verificado em 2026-08-03

## Fontes

- `../../src/main/java/br/com/api/footfirma/mundo/internal/MundoServiceImpl.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/CatalogoDeArquetipos.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/FabricaDeElenco.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/FabricaDeAtributos.java`
- `../../src/main/java/br/com/api/footfirma/mundo/internal/LimpezaDoCatalogo.java`
- `../../src/main/java/br/com/api/footfirma/avaliacao/internal/AvaliacaoServiceImpl.java`
- `../../src/main/resources/db/migration/V13__popula_perfil_avaliacao.sql`
- `../../../../docs/superpowers/specs/2026-08-03-mundo-ficticio-design.md`

## Decisão

Cinco decisões do módulo `mundo`, que substitui o `importacao`.

**1. O mundo é gerado a partir de uma semente, não importado de um dataset.** Sem
fonte externa não há do que desconfiar: manifesto, checksum e ocorrência por linha
recusada viravam cerimônia sobre um arquivo que o próprio sistema acabara de
escrever. A reprodutibilidade que o dataset versionado dava vem agora do
determinismo — mesma semente, mesmo mundo.

**2. As fábricas de conteúdo são puras.** `FabricaDeClubes`, `FabricaDeElenco`,
`FabricaDeAtributos` e `GeradorDeNomes` recebem um `SplittableRandom` e devolvem
records. Não conhecem Spring nem banco.

**3. `LimpezaDoCatalogo` toca tabela alheia por `JdbcTemplate`.** É a única exceção
à regra que o módulo segue no resto.

**4. `MundoServiceImpl` tem oito dependências**, acima do limite de 4~5 de
`.rules/java-core.md`.

**5. O conjunto de skills relevantes por posição é declarado no gerador**, duplicando
o que a `V13` já declara em pesos.

E uma que os testes impuseram:

**6. O índice do clube (0–99) é comprimido antes de virar alvo de overall**, por
`38 + 0,45 × índice`.

## Por quê

**Sobre a segunda.** Fábrica pura dá teste de balanceamento que roda em
milissegundos, sem Docker: `FabricaDeClubesTest`, `FabricaDeElencoTest` e
`FabricaDeAtributosTest` somam 17 casos e não sobem contexto Spring. Balanceamento é
o que mais precisa de iteração rápida — cada ajuste de faixa pede uma rodada — e uma
suíte que exige Testcontainers para cada tentativa faz o ajuste não acontecer.

**Sobre a terceira.** Apagar o catálogo inteiro é operação de infraestrutura sobre o
banco, não escrita de domínio. A alternativa seria expor um método destrutivo na
interface pública de cinco módulos, numa API que é read-only por decisão registrada
em `2026-08-01-catalogo-read-only.md`. Uma lista de tabelas numa classe é superfície
menor que cinco métodos públicos que ninguém mais deveria chamar.

**Sobre a quarta.** É o orquestrador, e cada dependência é um módulo do catálogo mais
as propriedades e o `JdbcTemplate` da limpeza. Dividi-lo só moveria a lista de lugar.
Mesmo precedente do importador que ele substitui.

**Sobre a quinta.** A alternativa seria expor os pesos na interface pública de
`avaliacao` para que o gerador invertesse a fórmula — acoplando a geração à versão do
perfil de avaliação. Como os pesos de cada perfil somam 1,0, gerar em torno do alvo
apenas as skills que pesam já faz o overall cair perto dele, sem inverter nada.

**Sobre a sexta.** Índice de clube não é overall de jogador. Sem a compressão, um
clube de reputação 92 recebia alvo de elenco 92, e a estrela em +14 batia no teto de
95 junto com os titulares: o elenco inteiro se achatava contra o limite e ninguém se
destacava — exatamente a propriedade que o balanceamento existe para garantir.
`MundoBalanceamentoTest.deveDarUmDestaqueACadaClube` é quem cobra isso.

## Consequências

- **A geração não abre transação envolvente.** `AvaliacaoService.materializar` é
  `@Transactional(propagation = NOT_SUPPORTED)` e suspende a transação corrente:
  dentro de um commit único ela não enxergaria nenhum dos 1.520 atributos recém
  gravados e produziria zero linha de overall. O preço é que uma falha no meio deixa
  mundo parcial, e é o que `footfirma.mundo.recriar` resolve.
- Um sub-gerador por clube (`semente + slug.hashCode()`): mexer no clube 7 não
  desloca o 8. Sem isso, qualquer ajuste reescreveria o mundo inteiro e o diff de
  comportamento ficaria ilegível.
- `FabricaDeElenco` mantém um `Set` de chaves naturais já emitidas e re-sorteia o
  nome em caso de colisão. Chave duplicada viola a unicidade de `jogador` e
  derrubaria a carga inteira.
- Atributo oculto deriva da semente do jogador, não do sorteio do clube:
  personalidade não muda de time.
- `clube_alias` e as três tabelas `*_referencia_externa` ficam sem uso. Existiam para
  casar registros com `EA_FC` e `TRANSFERMARKT`; num mundo fictício não há o que
  casar. Dívida registrada, não esquecimento.
- `FonteAtributo` ganhou `GERADO`. Não é sinônimo de `ESTIMADO`, que infere a partir
  de observação: gerado não observa nada.
- O enum de `regra_classificacao.tipo` mantém `LIBERTADORES_GRUPOS`,
  `LIBERTADORES_PRE` e `SULAMERICANA` dormentes. Só `ACESSO` e `REBAIXAMENTO` são
  usados — regra que aponta para competição inexistente é dado falso.

## Como validar

```bash
./gradlew test --tests '*ModularidadeTest' \
               --tests '*MundoBalanceamentoTest' \
               --tests '*MundoDeterminismoTest'
```

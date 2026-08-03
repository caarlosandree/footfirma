# Dataset de fixtures — formato e conteúdo

Status: verificado em 2026-08-03

## Fontes

- `src/test/java/br/com/api/footfirma/importacao/internal/GeradorDeFixtures.java`
- `src/main/java/br/com/api/footfirma/importacao/internal/ValidadorDeDataset.java`
- `src/main/java/br/com/api/footfirma/importacao/internal/LeitorDeCsv.java`
- `src/main/java/br/com/api/footfirma/importacao/internal/Manifesto.java`
- `src/test/java/br/com/api/footfirma/importacao/internal/FixturesIntegridadeTest.java`
- `../../../docs/superpowers/specs/2026-08-01-catalogo-futebol-brasileiro-design.md`

## O que é isto

`v1/` é um dataset **fictício**, versionado no repositório, consumido pelo importador
(`ImportacaoService`) e pela suíte de testes.

Nenhum clube, jogador ou competição aqui corresponde a entidade real. Isso não é
detalhe: dado derivado de fonte licenciada não entra no repositório, e um dataset
inventado resolve licença e determinismo de teste com a mesma decisão.

Este diretório também é o **contrato de formato**. Um pipeline externo que um dia
substitua o gerador precisa emitir exatamente isto — e então o importador não muda.

## Conteúdo

2 temporadas (2025, 2026) · 3 competições · 8 clubes · 8 estádios · 176 jogadores
(22 por clube) · 352 linhas de atributo · 352 vínculos.

Os atributos são correlacionados com `clube.reputacao` e com a posição, para que o
ranking de overall produza ordem com significado em vez de ruído. Entre 2025 e 2026
os jogadores com menos de 25 anos ganham 2 pontos por skill e os com mais de 32
perdem 2 — a progressão determinística que prova o versionamento por temporada.

Três jogadores trocam de clube em 2026 e um tem vínculo `EMPRESTIMO`. Os índices são
fixos no gerador, não sorteados: caso de teste escolhido por sorteio muda de lugar a
cada ajuste.

## Formato

UTF-8, `LF`, separador `,`, aspas duplas quando o campo contém vírgula ou aspas
(escapadas por duplicação). Cabeçalho obrigatório e validado contra o esperado.
Campo vazio significa `null`.

**Quebra de linha dentro de aspas não é suportada.** O parser lê linha a linha, e
nenhuma coluna do formato é texto livre multilinha.

Referências entre arquivos são **sempre por chave de negócio** — slug, label, código
— nunca por id. O dataset não conhece os ids do banco, e é isso que o torna
reimportável em base zerada.

| # | Arquivo | Colunas | Chave de upsert |
|---|---|---|---|
| 1 | `temporada.csv` | `label,ano_inicio,ano_fim` | `label` |
| 2 | `estadio.csv` | `chave,nome,cidade,uf,capacidade,ano_inauguracao` | `(nome, cidade)` |
| 3 | `clube.csv` | `slug,nome_oficial,nome_curto,apelido,ano_fundacao,iso_pais,uf,estadio_chave,cor_primaria,cor_secundaria,reputacao,qualidade_base,uf_base` | `slug` |
| 4 | `clube_alias.csv` | `clube_slug,alias,fonte` | `(alias, fonte)` |
| 5 | `competicao.csv` | `slug,nome,iso_pais,tipo,nivel,genero` | `slug` |
| 6 | `edicao.csv` | `competicao_slug,temporada,nome,data_inicio,data_fim` | `(competicao, temporada)` |
| 7 | `fase.csv` | `competicao_slug,temporada,ordem,nome,tipo,jogos_por_confronto,tem_gol_fora,tem_prorrogacao,tem_penaltis` | `(edicao, ordem)` |
| 8 | `edicao_participante.csv` | `competicao_slug,temporada,clube_slug,posicao_final` | `(edicao, clube)` |
| 9 | `regra_classificacao.csv` | `competicao_slug,temporada,posicao_inicio,posicao_fim,tipo,competicao_destino_slug` | `(edicao, faixa, tipo)` |
| 10 | `jogador.csv` | `slug,nome_completo,nome_exibicao,data_nascimento,iso_pais,iso_segunda_nacionalidade,altura_cm,peso_kg,pe_preferido,posicao_principal,origem` | `chave_natural` (derivada) |
| 11 | `jogador_posicao.csv` | `jogador_slug,posicao,ordem` | `(jogador, posicao)` |
| 12 | `jogador_atributo.csv` | `jogador_slug,temporada,` + 18 skills + `,potencial_base,potencial_variacao,fonte_atributo,coletado_em` | `(jogador, temporada)` |
| 13 | `jogador_atributo_oculto.csv` | `jogador_slug,profissionalismo,ambicao,lealdade,temperamento,lideranca,regularidade,propensao_lesao,resistencia_pressao` | `jogador` |
| 14 | `jogador_caracteristica.csv` | `jogador_slug,caracteristica` | `(jogador, caracteristica)` |
| 15 | `jogador_vinculo.csv` | `jogador_slug,clube_slug,temporada,tipo,numero_camisa,data_inicio,data_fim,valor_mercado_eur` | `(jogador, temporada, clube)` |

As 18 skills, nesta ordem exata:

```
ritmo,forca,folego,salto,agilidade,passe,drible,cruzamento,frieza,
finalizacao,cabeceio,falta,penalti,desarme,marcacao,
gol_reflexo,gol_posicionamento,gol_manejo
```

`estadio.chave` existe só dentro do dataset, para `clube.estadio_chave` apontar. Não
vira coluna no banco.

## O que é derivado, não declarado

`jogador.csv` **não** tem colunas para `chave_natural` nem `semente`. O importador as
calcula (`ChaveNatural`):

```
chave_natural = normalizar(nome_completo) | data_nascimento | iso_pais
semente       = primeiros 8 bytes do SHA-256(chave_natural), sem sinal
```

São invariantes do sistema, não dados da fonte. Se viessem no arquivo, um dataset mal
gerado daria sementes diferentes ao mesmo jogador entre execuções — e a semente é a
origem de todo atributo oculto e de todo ruído de crescimento.

`slug`, ao contrário, **vem** no arquivo: é identificador de API, escolha editorial, e
precisa ser legível.

## Referências a catálogo de seed

`iso_pais` e `uf` resolvem contra `V4__popula_geografia.sql`; `posicao_principal` e
`jogador_posicao.posicao` contra `V7__popula_posicao.sql`; `caracteristica` contra
`V9__cria_caracteristica_e_vinculo.sql`.

Valor ausente nesses três catálogos vira **ocorrência**, não erro: o dataset é que
está fora do catálogo, e o catálogo é seed versionado.

## `manifest.json`

```json
{
  "schemaVersao": "1",
  "datasetVersao": "fixtures-v1",
  "geradoEm": "2026-08-03",
  "arquivos": [
    { "nome": "temporada.csv", "linhas": 2, "sha256": "…" }
  ]
}
```

Quatro regras, verificadas **antes** de qualquer escrita no banco, nesta ordem:

1. `schemaVersao` é exatamente `"1"` — sem schema conhecido, as demais checagens não
   significam nada.
2. Todo arquivo listado existe e seu SHA-256 confere.
3. Toda contagem de linhas confere (sem contar o cabeçalho).
4. Todo `.csv` presente no diretório está listado no manifesto — sem essa regra, um
   arquivo esquecido pelo gerador seria silenciosamente ignorado.

Violação de qualquer uma aborta a carga sem gravar uma linha sequer, nem em
`importacao_execucao`.

## Como regerar

```bash
./gradlew gerarFixtures
```

O gerador é determinístico: mesma semente (`20260803`), mesmos arquivos. Ele não roda
na suíte — o resultado é versionado, e regerar a cada build produziria diff em todo
commit.

**Ao regerar, revise `jogador.csv` à mão.** Os nomes saem da combinação de pools de
prenomes e sobrenomes; nada impede que uma combinação nova coincida com o nome de um
jogador profissional real. É a única verificação deste dataset que uma máquina não faz.

## Como validar

```bash
./gradlew test --tests '*FixturesIntegridadeTest'
```

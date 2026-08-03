# Documentação — Backend FootFirma

Esta pasta guarda o conhecimento sobre **o que o sistema é**. As regras sobre **como
trabalhar nele** ficam em `.rules/` — não misture os dois.

| Pasta | Conteúdo |
|---|---|
| `docs/runbooks/` | procedimentos operacionais (validação, diagnóstico) |
| `docs/adr/` | decisões arquiteturais e o motivo delas |
| `docs/domain/` | uma nota por domínio de negócio, criada quando o módulo existir |

`docs/domain/` nasce junto com a primeira nota de domínio que tenha lastro no
código — documentar antes seria ficção.

## Padrão obrigatório de nota técnica

Toda nota em `docs/` começa assim:

```md
# Título

Status: verificado em AAAA-MM-DD

## Fontes

- `caminho/real/no/repo/Arquivo.java`
- `src/main/resources/db/migration/V3__cria_partida.sql`

## Conteúdo

...

## Como validar

```bash
./gradlew compileJava
```
```

As quatro partes existem por um motivo:

- **Status com data** — deixa explícito que a nota é uma fotografia. Sem data, uma
  afirmação errada parece atual para sempre.
- **Fontes com caminho real** — se o arquivo citado não existe, a nota está podre e
  isso é verificável. Nota sem fonte é palpite formatado.
- **Como validar** — o comando que confirma o que a nota afirma.

## Regras de escrita

- **Lastro no código.** Documente o que está no repositório, não o que foi combinado
  numa conversa. Regra de negócio vinda de conversa entra marcada como
  `não verificado no código` até ser confirmada — nunca como fato.
- **Separe fato de decisão.** "O endpoint devolve 409 em conflito" (fato, com fonte)
  é diferente de "decidimos que vai devolver 409" (decisão, ainda não implementada).
- **Notas pequenas por domínio** valem mais que um documento gigante. Ninguém lê o
  gigante, nem humano nem agente.
- **Atualize a data** ao revisar. Se conferiu e continua certo, mude a data assim mesmo
  — isso é a informação mais útil da nota.
- **Não documente segredo**, senha, token ou credencial, nem "só de exemplo".
- **Ao mudar código que uma nota descreve, atualize a nota no mesmo trabalho.** Nota
  defasada é pior que nota ausente: ela é confiada.

## Quando o código e a documentação divergem

O código vence, sempre. A nota é que está errada — corrija a nota e registre a data.
Se não der para confirmar no código naquele momento, marque a parte duvidosa
explicitamente em vez de deixar a afirmação de pé.

## Relacionado

- `.rules/` — regras de desenvolvimento (fonte de verdade sobre como codar aqui)
- `AGENTS.md` — índice do repositório
- `docs/runbooks/validation.md` — qual validação rodar para cada tipo de mudança

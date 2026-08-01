# Documentação — Frontend FootFirma

Esta pasta guarda o conhecimento sobre **o que o sistema é**. As regras sobre **como
trabalhar nele** ficam em `.rules/` — não misture os dois.

| Pasta | Conteúdo |
|---|---|
| `docs/runbooks/` | procedimentos operacionais (validação, diagnóstico) |
| `docs/features/` | uma nota por feature, criada quando a feature existir |

Hoje só existe o runbook de validação. `docs/features/` nasce junto com a primeira
feature real — documentar antes seria ficção.

## Padrão obrigatório de nota técnica

Toda nota em `docs/` começa assim:

```md
# Título

Status: verificado em AAAA-MM-DD

## Fontes

- `apps/web/app/partidas/page.tsx`
- `packages/ui/src/components/button.tsx`

## Conteúdo

...

## Como validar

```bash
pnpm typecheck
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
  numa conversa. Comportamento vindo de conversa entra marcado como
  `não verificado no código` até ser confirmado — nunca como fato.
- **Separe fato de decisão.** "A listagem pagina de 20 em 20" (fato, com fonte) é
  diferente de "vamos paginar de 20 em 20" (decisão, ainda não implementada).
- **Não repita a documentação do Next.** Ela já está versionada em
  `node_modules/next/dist/docs/` e é mais confiável que qualquer resumo nosso.
  Documente **as decisões deste projeto**, não o framework.
- **Notas pequenas por feature** valem mais que um documento gigante.
- **Atualize a data** ao revisar, mesmo quando nada mudou.
- **Não documente segredo**, token ou chave, nem "só de exemplo".
- **Ao mudar código que uma nota descreve, atualize a nota no mesmo trabalho.**

## Quando o código e a documentação divergem

O código vence, sempre. A nota é que está errada — corrija a nota e registre a data.
Se não der para confirmar naquele momento, marque a parte duvidosa explicitamente em
vez de deixar a afirmação de pé.

## Relacionado

- `.rules/` — regras de desenvolvimento (fonte de verdade sobre como codar aqui)
- `AGENTS.md` — índice do repositório
- `docs/runbooks/validation.md` — qual validação rodar para cada tipo de mudança

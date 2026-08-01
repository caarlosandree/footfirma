---
trigger: model_decision
globs: "**/*"
description: Padrão de mensagens de commit do repositório backend FootFirma.
---

# Commits — Backend FootFirma

## Regra absoluta

**Nunca** adicione rodapé `Co-authored-by:` (nem variações) às mensagens de commit
deste repositório. Vale para qualquer agente.

## Formato

```
<tipo>(<escopo>): <descrição curta>

<corpo opcional>
```

Mensagens em **português brasileiro**.

### Tipos

`feat` · `fix` · `refactor` · `perf` · `test` · `docs` · `style` · `chore` · `build` · `ci`

### Escopo

O escopo é o **módulo de domínio** afetado (`br.com.api.footfirma.<escopo>`) ou uma
área transversal:

- módulos de domínio: `partida`, `usuario`, `quadra`, … conforme forem criados
- transversais: `config`, `shared`, `security`, `db`, `gradle`, `openapi`

Commit que atravessa vários módulos pode omitir o escopo — mas antes verifique se
não deveriam ser dois commits.

### Descrição curta

- Máximo 72 caracteres
- Imperativo: "adiciona", "corrige", "extrai" — não "adicionado"/"adicionando"
- Primeira letra minúscula, sem ponto final

## Exemplos

```
feat(partida): adiciona endpoint de listagem paginada
```

```
fix(usuario): corrige vazamento de email em resposta de erro

O tratador de DataIntegrityViolationException devolvia a mensagem crua do
Postgres, expondo o valor do índice único uq_usuario_email.
```

```
feat(inscricao): publica evento InscricaoConfirmada

- Cria o record InscricaoConfirmada na raiz do módulo
- Publica dentro da transação de confirmação
- Adiciona listener em notificacao com @ApplicationModuleListener
- Migration V4 cria a tabela event_publication do Spring Modulith
```

```
chore(gradle): atualiza Spring Modulith para a versão mais recente da major
```

## Boas práticas

1. Um commit = uma mudança lógica. Refatoração e feature não andam juntas.
2. Mudança de `@Entity` e a migration correspondente vão **no mesmo commit**.
3. Mencione arquivos ou classes principais no corpo quando o diff for grande.
4. Breaking change de contrato de API é declarado no corpo, com destaque.
5. Não commite `build/`, `.idea/`, `*.iml` ou configuração local.

## Fluxo

- Trabalhe em branch `feat/...` ou `fix/...` e abra PR para `staging`, que é a
  branch padrão do repositório. `main` é produção e só recebe PR vindo de
  `staging`.
- **Não execute `git push` nem force-push sem pedido explícito.** `git merge` local
  é livre — o que exige pedido é publicar no remote.
- Antes de commitar, percorra `.rules/java-checklist.md`.

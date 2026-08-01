---
trigger: model_decision
globs: "**/*"
description: Padrão de mensagens de commit do monorepo frontend FootFirma.
---

# Commits — Frontend FootFirma

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

O escopo é o pacote do monorepo ou a feature dentro de `apps/web/app/`:

- pacotes: `web`, `ui`, `eslint-config`, `typescript-config`
- features: `partidas`, `auth`, … conforme forem criadas (`web/partidas` quando
  ajudar a desambiguar)
- transversais: `turbo`, `deps`, `tema`

Commit que atravessa pacotes pode omitir o escopo — mas verifique antes se não
deveriam ser dois commits.

### Descrição curta

- Máximo 72 caracteres
- Imperativo: "adiciona", "corrige", "extrai"
- Primeira letra minúscula, sem ponto final

## Exemplos

```
feat(partidas): adiciona listagem com filtro por data
```

```
fix(ui): restaura foco visível no Button variante ghost

O override de className removia focus-visible:ring, quebrando navegação
por teclado nos botões do header.
```

```
feat(web): integra criação de partida com a API v1

- Adiciona Server Action criarPartida com validação Zod
- Cria cliente HTTP em lib/api com timeout e parse de erro
- Trata estados de carregando, erro e vazio na listagem
```

```
chore(deps): atualiza next e react para as versões mais recentes da major
```

## Boas práticas

1. Um commit = uma mudança lógica. Formatação em massa vai em commit `style` próprio.
2. Componente novo do design system e seu primeiro uso podem ir juntos; refatoração
   ampla, não.
3. Mudança em `pnpm-lock.yaml` só acompanha commit que realmente mexeu em dependência.
4. Breaking change de contrato com o backend é declarado no corpo.
5. Não commite `.next/`, `.turbo/`, `node_modules/` ou `.env`.

## Fluxo

- Trabalhe em branch `feat/...` ou `fix/...`; a branch atual é `main`.
- **Não execute `git push`, `git merge` ou force-push sem pedido explícito.**
- Antes de commitar, percorra `.rules/nextjs-checklist.md`.

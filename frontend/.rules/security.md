---
trigger: always_on
globs: "**/*.{ts,tsx}"
description: Segurança do frontend FootFirma — fronteira servidor/cliente, Server Actions, segredos, XSS, autenticação e integração com a API v1.
---

# FootFirma Frontend — Segurança

A regra que organiza todas as outras: **o frontend não é fonte de autorização**.
Ele reflete as regras do backend. Toda decisão de acesso é revalidada no servidor.

## Fronteira servidor / cliente

Este é o risco número um de um app App Router: código que você pensou ser de servidor
acabar no bundle do browser.

- Arquivo com `'use client'` — e tudo que ele importa — **vai para o browser**.
  Nunca importe ali módulo que leia segredo, chave de API ou URL interna.
- Segredo é lido em Server Component, Server Action ou Route Handler. Ponto.
- Dado passado como prop de Server para Client Component é serializado e **visível no
  HTML**. Filtre antes: nunca mande o objeto inteiro do usuário para renderizar o nome.
- Use `import "server-only"` no topo de módulos que jamais podem ir ao cliente — o
  build quebra se alguém importar do lado errado.

```ts
// lib/api/servidor.ts
import "server-only"

const token = process.env.API_SERVICE_TOKEN   // sem NEXT_PUBLIC_
```

## Variáveis de ambiente

- `NEXT_PUBLIC_*` é **público**: aparece no bundle, no HTML e no DevTools de qualquer
  visitante. Só URL pública da API e flags inofensivas.
- Segredo nunca leva o prefixo. Se você precisou de `NEXT_PUBLIC_` para um segredo,
  o desenho está errado — mova a chamada para o servidor.
- Valide o ambiente com Zod em `lib/env.ts`, separando o schema do servidor do schema
  público, e falhe no boot se faltar variável.
- O `.gitignore` ignora `.env*` — inclusive `.env.example`. Se for versionar o
  exemplo, adicione `!.env.example`. Nunca commite `.env` real.

## Server Actions

Uma Server Action é um **endpoint HTTP público**. Qualquer pessoa pode invocá-la
diretamente, com qualquer payload, ignorando completamente a interface.

Em toda action, nesta ordem:

1. Verificar sessão — a checagem feita na página **não** protege a action.
2. Verificar permissão sobre o recurso específico (o id veio do cliente).
3. Validar a entrada com Zod.
4. Só então executar.

```ts
"use server"

export async function cancelarPartida(id: string) {
  const sessao = await obterSessao()
  if (!sessao) throw new Error("Não autenticado")

  const { data } = esquemaId.parse({ id })
  // o backend revalida a posse; o frontend não é a última linha de defesa
  await api.delete(`/api/v1/partidas/${data}`)
  refresh()
}
```

- Nunca aceite `usuarioId` vindo do formulário para decidir permissão — use a sessão.
- Erro devolvido ao cliente é genérico; detalhe fica no log do servidor.

## Autenticação

- `proxy.ts` serve para checagem **otimista** e redirecionamento — não é
  gerenciamento de sessão nem autorização. Veja
  `docs/01-app/02-guides/authentication.md`.
- A verificação que vale acontece perto do dado: no Server Component que busca,
  na Server Action que muda, no Route Handler.
- Token de sessão em cookie `httpOnly`, `Secure`, `SameSite=Lax` ou `Strict`.
  **Nunca** em `localStorage` — é leitura trivial para qualquer XSS.
- Não renderize elemento de UI restrito confiando só em flag do cliente; o dado
  sensível não deve nem chegar ao browser.

## XSS

- JSX escapa por padrão. O risco mora nas exceções.
- `dangerouslySetInnerHTML` exige HTML sanitizado no servidor (DOMPurify ou
  equivalente) e um comentário justificando. Conteúdo de usuário sem sanitização,
  nunca.
- Nunca monte `href` a partir de entrada do usuário sem validar o protocolo —
  `javascript:` é um vetor real.
- Não injete conteúdo dinâmico dentro de `<script>` nem de `<style>`.
- Considere Content Security Policy quando a app for para produção
  (`docs/01-app/02-guides/content-security-policy.md`).

## Chamadas à API do backend

- Toda resposta é `unknown` até passar por Zod. API não é fonte de tipos confiável em
  runtime, mesmo que o contrato exista.
- Erro da API nunca é repassado cru para a tela — traduza para mensagem de usuário.
- Timeout explícito em toda chamada; trate falha de rede como estado previsto.
- Não faça o browser chamar endpoint interno do backend com credencial de serviço:
  proxie por Server Action ou Route Handler.

## Dados pessoais

- Não logue dado pessoal no console — logs de client vazam em ferramentas de
  monitoramento e no DevTools do usuário.
- Não guarde dado sensível em `localStorage`, `sessionStorage` ou query string.
- Formulário com dado sensível: `autoComplete="off"` onde fizer sentido e
  `type="password"` para segredo.

## Dependências

- `pnpm audit` antes de subir versão relevante.
- Componente de terceiros que injeta HTML ou script entra só com revisão explícita.
- Não adicione dependência para resolver o que o framework já resolve — cada pacote é
  superfície de ataque e peso no bundle.

## Checklist rápido

- [ ] Nenhum segredo em arquivo alcançável pelo cliente
- [ ] Nenhuma variável sensível com prefixo `NEXT_PUBLIC_`
- [ ] Server Action nova valida sessão, permissão e entrada
- [ ] Props que cruzam para o cliente não carregam dado a mais
- [ ] Sem `dangerouslySetInnerHTML` não sanitizado
- [ ] Resposta da API validada com Zod
- [ ] Erro exibido não vaza detalhe interno
- [ ] Nada sensível em `localStorage` ou em log

## Módulos relacionados

- `.rules/nextjs-core.md` — Server Components, Server Actions, ambiente
- `.rules/nextjs-checklist.md` — checklist final
- Backend: `../backend/footfirma/.rules/security.md`

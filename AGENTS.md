# FootFirma — Índice do sistema

Este diretório **não é um repositório git**: é o workspace local que agrupa dois
repositórios independentes, cada um com seu próprio conjunto de regras.

| Repositório | Caminho | Stack | Regras |
|---|---|---|---|
| Frontend | `frontend/` | Turborepo + pnpm · Next.js 16 (App Router) · React 19 · Tailwind v4 · shadcn/ui sobre Base UI · Zod 4 | `frontend/AGENTS.md` → `frontend/.rules/` |
| Backend | `backend/footfirma/` | Spring Boot 4 · Java 25 · Spring Modulith · PostgreSQL · Redis · Flyway · Gradle | `backend/footfirma/AGENTS.md` → `backend/footfirma/.rules/` |

As regras citam apenas a **major** de cada dependência. As versões exatas vivem nos
manifestos (`package.json` / `pnpm-lock.yaml` no frontend, `build.gradle` no backend)
— consulte-os quando a versão precisa importar, em vez de confiar no que está escrito
nas regras.

## Antes de qualquer coisa

Abra o `AGENTS.md` do repositório em que você vai trabalhar e siga o arquivo de
`.rules/` correspondente à tarefa. Este índice existe só para orientar; ele **não**
substitui as regras específicas.

Se a tarefa atravessa os dois lados (mudança de contrato de API, por exemplo), leia
os dois `AGENTS.md` antes de começar.

## Contrato entre os dois

- O backend expõe a API em `/api/v1/`. Swagger em
  `http://localhost:8080/swagger-ui.html`, OpenAPI em `/v3/api-docs`.
- O frontend consome essa API e roda em `http://localhost:3000`.
- Mudança incompatível de contrato vira `/api/v2` — não se altera a v1 em uso.
- Alterou o contrato no backend? Diga explicitamente o que o frontend precisa ajustar;
  são repositórios separados, com commits e histórico independentes.

## Comandos por lado

```bash
# frontend/
pnpm lint && pnpm typecheck && pnpm build

# backend/footfirma/
./gradlew compileJava     # rápido
./gradlew build           # compila + testa (exige Docker)
```

## Documentação vs. regras

Cada repositório separa as duas coisas, e a distinção importa:

| Pasta | Responde |
|---|---|
| `.rules/` | **como trabalhar** aqui — arquitetura, padrões, checklists |
| `docs/` | **o que o sistema é** — runbooks e notas de domínio |

Toda nota em `docs/` segue o padrão obrigatório do `docs/README.md` do repositório:
`Status: verificado em AAAA-MM-DD`, `## Fontes` com caminhos reais e um comando de
validação. Documentação sem lastro no código não entra — o código sempre vence.

## Guardrails automáticos

Os três diretórios (raiz, `frontend/`, `backend/footfirma/`) têm
`.claude/settings.json` com um hook `PreToolUse` apontando para
`.claude/hooks/guard.mjs`. O guard **bloqueia**, não avisa:

- subir serviços (`pnpm dev`, `./gradlew bootRun`, `docker compose up`, …)
- `git push`, `reset --hard`, `clean -fd`, `rebase -i`, `commit --amend`, `branch -D`
- editar ou commitar migration Flyway já versionada
- commitar segredo (PAT, chave de API, private key, URL de banco com senha, JWT)

Os três `guard.mjs` são **cópias byte-idênticas**. Ao alterar um, copie por cima nos
outros dois — os repositórios são independentes e cada um carrega o próprio guard.

Bloqueio não é convite a contornar por outro caminho. Se o usuário realmente quer o
comando, ele executa na própria sessão com `! <comando>`.

## Vale para os dois repositórios

- Responda e escreva commits em **português brasileiro**.
- **Não suba serviços** sem pedido explícito (o guard bloqueia).
- **Não faça `git push`, `merge` ou force-push** sem pedido explícito. O remote é
  `origin` → `git@github.com:caarlosandree/footfirma.git`.
- **Fluxo de branches**: `staging` é a branch padrão do repositório; `main` é
  produção. Nada vai para `main` sem passar por `staging` antes — branch de
  feature → PR para `staging` → depois de validado, PR de `staging` para `main`.
  As duas branches são protegidas no GitHub (PR obrigatório, 1 aprovação, sem
  force-push nem delete); só o dono do repositório aprova.
- **Nunca** adicione `Co-authored-by:` em mensagem de commit.
- Nenhum segredo em código, log ou commit.
- Antes de encerrar uma tarefa, percorra o checklist do repositório:
  `frontend/.rules/nextjs-checklist.md` ou
  `backend/footfirma/.rules/java-checklist.md`.
- **Feche toda tarefa com o registro**: arquivos alterados, validações executadas,
  validações não executadas e por quê, e alterações preexistentes no worktree.
  Nunca diga "pronto" sem ter rodado o comando que sustenta a frase.

## Releases (release-please)

Os dois lados são versionados **de forma independente**, a partir dos commits
convencionais. Nada é publicado em registry: o release-please só gera versão,
CHANGELOG, tag e GitHub Release.

| Branch | Workflow | Config / manifesto | Produz |
|---|---|---|---|
| `staging` | `.github/workflows/release-please-staging.yml` | `.github/release-please/config.staging.json` + `manifest.staging.json` | pré-releases `frontend-v0.1.0-rc.1`, `backend-v0.1.0-rc.1` |
| `main` | `.github/workflows/release-please.yml` | `release-please-config.json` + `.release-please-manifest.json` | releases estáveis `frontend-v0.1.0`, `backend-v0.1.0` |

Os dois fluxos são **disjuntos por construção** — nenhum arquivo é escrito pelos
dois, então o PR `staging → main` nunca conflita por causa de release:

- `staging` roda com `skip-changelog` e sem arquivo de versão, então seu PR de
  release altera **só** `manifest.staging.json`.
- `main` escreve `frontend/package.json`, `frontend/CHANGELOG.md`,
  `backend/footfirma/CHANGELOG.md` e a versão do `build.gradle`.

Consequência prática: **não crie `frontend/version.txt` nem
`backend/footfirma/version.txt`**. O release type `simple` só atualiza esse arquivo
se ele já existir; criá-lo faria as duas branches escreverem no mesmo lugar e traria
o conflito de volta.

A versão do backend vive no `build.gradle`, na linha marcada com
`// x-release-please-version` — é ela que o release-please reescreve. Não remova a
marcação nem coloque outro número semver na mesma linha.

Duas condições para o fluxo funcionar:

- O PR `staging → main` precisa ser mergeado com **merge commit**, não squash. O
  release-please lê os commits convencionais individuais para calcular o bump e
  montar o CHANGELOG; um squash colapsa tudo em um commit só.
- Em *Settings → Actions → General*, habilite **"Allow GitHub Actions to create and
  approve pull requests"**, senão o workflow não consegue abrir o PR de release.
  Opcionalmente, defina o secret `RELEASE_PLEASE_TOKEN` (PAT com `contents` e
  `pull_requests`) para que o PR de release dispare os demais workflows — o
  `GITHUB_TOKEN` padrão não dispara.

## Estado atual

Os dois lados estão em estágio de scaffold: o backend tem apenas
`FootfirmaApplication` e `OpenApiConfig`, sem módulo de domínio nem migration; o
frontend tem o layout raiz, a página inicial e o `Button` do design system. As regras
foram escritas para **guiar a construção**, não para descrever o que já existe —
espere criar estrutura nova seguindo os padrões, em vez de encontrar exemplos prontos
no código.

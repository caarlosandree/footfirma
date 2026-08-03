# FootFirma — Índice do sistema

Este diretório é a raiz de um **monorepo git único** que abriga os dois lados do
sistema, cada um com seu próprio conjunto de regras. Um commit pode tocar os dois, e
o histórico é compartilhado — mas as regras não: cada lado tem as suas.

| Lado | Caminho | Stack | Regras |
|---|---|---|---|
| Frontend | `frontend/` | Turborepo + pnpm · Next.js 16 (App Router) · React 19 · Tailwind v4 · shadcn/ui sobre Base UI · Zod 4 | `frontend/AGENTS.md` → `frontend/.rules/` |
| Backend | `backend/footfirma/` | Spring Boot 4 · Java 25 · Spring Modulith · PostgreSQL · Redis · Flyway · Gradle | `backend/footfirma/AGENTS.md` → `backend/footfirma/.rules/` |

As regras citam apenas a **major** de cada dependência. As versões exatas vivem nos
manifestos (`package.json` / `pnpm-lock.yaml` no frontend, `build.gradle` no backend)
— consulte-os quando a versão precisa importar, em vez de confiar no que está escrito
nas regras.

## Antes de qualquer coisa

Abra o `AGENTS.md` do lado em que você vai trabalhar e siga o arquivo de `.rules/`
correspondente à tarefa. Este índice existe só para orientar; ele **não** substitui as
regras específicas.

Se a tarefa atravessa os dois lados (mudança de contrato de API, por exemplo), leia
os dois `AGENTS.md` antes de começar.

## Contrato entre os dois

- O backend expõe a API em `/api/v1/`. Swagger em
  `http://localhost:8080/swagger-ui.html`, OpenAPI em `/v3/api-docs`.
- O frontend consome essa API e roda em `http://localhost:3000`.
- Mudança incompatível de contrato vira `/api/v2` — não se altera a v1 em uso.
- Alterou o contrato no backend? Diga explicitamente o que o frontend precisa ajustar.
  Estando os dois no mesmo repositório, nada obriga o ajuste a vir no mesmo commit — e
  um contrato alterado de um lado só quebra silenciosamente o outro.

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
- `git push` e force-push — **só o que publica no remote**
- editar ou commitar migration Flyway já versionada
- commitar segredo (PAT, chave de API, private key, URL de banco com senha, JWT)

Os três `guard.mjs` são **cópias byte-idênticas**. Ao alterar um, copie por cima nos
outros dois: o `settings.json` tem escopo de diretório, então cada um precisa apontar
para um guard que exista ao seu lado. Verifique com
`md5 -q .claude/hooks/guard.mjs frontend/.claude/hooks/guard.mjs backend/footfirma/.claude/hooks/guard.mjs | sort -u | wc -l`
— o resultado precisa ser `1`.

Bloqueio não é convite a contornar por outro caminho. Se o usuário realmente quer o
comando, ele executa na própria sessão com `! <comando>`.

## Vale para os dois lados

- Responda e escreva commits em **português brasileiro**.
- **Não suba serviços** sem pedido explícito (o guard bloqueia).
- **Não faça `git push` nem force-push** sem pedido explícito. O remote é
  `origin` → `git@github.com:caarlosandree/footfirma.git`.
- Operação **local é livre** — `merge`, `rebase`, `commit --amend`, `reset`,
  `clean`, `branch -D`. Nada disso sai da máquina, e desfazer é problema local. A
  fronteira que o guard defende é o remote, não o histórico local.
- **Fluxo de branches**: `staging` é a branch padrão do repositório; `main` é
  produção. Nada vai para `main` sem passar por `staging` antes — branch de
  feature → PR para `staging` → depois de validado, PR de `staging` para `main`.
  As duas branches são protegidas no GitHub (PR obrigatório, 1 aprovação, sem
  force-push nem delete); só o dono do repositório aprova. A proteção vale **no
  remote e não vale para administradores** (`enforce_admins: false`): o dono
  consegue push direto. Ela não alcança o repositório local — merge, rebase e
  reset aqui não passam por ela.
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

| Branch | Workflow | Produz |
|---|---|---|
| `staging` | `.github/workflows/release-please-staging.yml` | pré-releases `frontend-v0.1.0-rc.1`, `backend-v0.1.0-rc.1` |
| `main` | `.github/workflows/release-please.yml` | releases estáveis `frontend-v0.1.0`, `backend-v0.1.0` |

### O que gera release

Só `feat`, `fix`, `perf`, `revert` e breaking change. `docs`, `refactor`, `chore`,
`test`, `build`, `ci` e `style` entram no histórico mas **não versionam nada** —
seguem para o próximo release junto de um commit que conte.

No release-please as duas coisas são a mesma decisão: ele pula o release quando as
release notes saem vazias, então um tipo oculto no changelog é também um tipo que
não dispara release. Não existe "aparece no CHANGELOG mas não versiona".

Por isso as `changelog-sections` são **idênticas nos quatro configs**. Se
divergirem, `staging` e `main` passam a discordar sobre o que merece release — e
sai versão estável em produção sem o release candidate correspondente. Ao mexer
nelas, mexa nas quatro.

### Nada é compartilhado entre componentes

Cada combinação branch × componente tem **config, manifesto e par de labels
próprios** — quatro conjuntos independentes em `.github/release-please/`:

```
config.<componente>.<branch>.json
manifest.<componente>.<branch>.json
```

Isso não é organização, é correção. Com um manifesto único para os dois
componentes, os dois PRs de release editam o mesmo arquivo: ao mergear um, o
outro fica desatualizado e, se for mergeado antes do workflow recriá-lo,
**sobrescreve a versão que o primeiro acabou de gravar**. As labels também
precisam ser distintas, porque o release-please varre todo PR mergeado que tenha
a label de release e o cruza contra os pacotes do seu config.

Consequências ao mexer nisso:

- `include-component-in-tag: true` é **obrigatório** em cada config. Como cada um
  declara um pacote só, sem essa opção as tags sairiam como `v0.1.0`, sem o
  prefixo do componente — e os dois componentes colidiriam na mesma tag.
- Ao adicionar um terceiro componente, crie o par de arquivos e as labels dele e
  acrescente o nome à `matrix.component` dos dois workflows.

### Por que o PR staging → main não conflita

Nenhum arquivo é escrito pelos dois fluxos:

- `staging` roda com `skip-changelog` e sem arquivo de versão, então seu PR de
  release altera **só** o próprio `manifest.<componente>.staging.json`.
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

Status: verificado em 2026-08-03.

**Os dois lados estão em estágios muito diferentes.**

O **backend** saiu do scaffold. Tem sete módulos de domínio (`temporada`, `geografia`,
`clube`, `jogador`, `competicao`, `avaliacao`, `importacao`), mais `config` e `shared`
como módulos abertos, catorze migrations Flyway e uma suíte de 157 testes. A API é
read-only em `/api/v1/clubes`, `/jogadores`, `/competicoes`,
`/jogadores/{slug}/overall` e `/rankings`. Aqui já existem exemplos prontos: ao criar
um módulo novo, copie o formato de um existente em vez de partir do zero.

O banco tem o schema completo e os seeds de país, estado, posição, característica e
perfis de peso. Em base zerada continua **vazio de clubes e jogadores** — os endpoints
respondem lista vazia e 404, e isso é esperado. Rodar a carga de
`backend/footfirma/fixtures/v1` popula 8 clubes fictícios, 176 jogadores e 3.168
linhas de overall. Ver `backend/footfirma/docs/runbooks/importacao.md`.

O **frontend** continua em scaffold: layout raiz, página inicial, `theme-provider` e o
`Button` do design system. Ali as regras ainda guiam a construção em vez de descrever
o que existe.

Roadmap do backend, para situar uma tarefa nova: o subsistema de catálogo foi fatiado
em três planos. Plano 1 (schema e API read-only) e Plano 2 (avaliação e overall) estão
executados. O Plano 3 está **parcialmente** executado: o formato do dataset, as
fixtures fictícias e o importador Java foram entregues; o pipeline de dados em Python
— extração de fonte externa e matching entre fontes — **não**, e é o próximo passo se
o projeto quiser dados reais. A progressão de jogadores tem spec próprio ainda não
escrito. Ver `docs/superpowers/specs/` e `docs/superpowers/plans/`.

#!/usr/bin/env node
/**
 * guard.mjs — PreToolUse guard do workspace FootFirma.
 *
 * Transforma em bloqueio real três regras que, escritas só em texto, dependem da
 * boa vontade do agente:
 *   1. não subir serviços sem pedido explícito;
 *   2. não publicar no remote (push / force-push) sem pedido explícito;
 *   3. não commitar migration Flyway já aplicada, nem segredo.
 *
 * O corte é entre local e remoto, não entre seguro e perigoso: merge, rebase, amend,
 * reset e clean ficam livres porque não saem da máquina do usuário.
 *
 * Também barra a edição direta de uma migration já versionada, antes mesmo do commit.
 *
 * Este arquivo é uma CÓPIA IDÊNTICA em três lugares:
 *   .claude/hooks/guard.mjs                     (raiz do workspace)
 *   frontend/.claude/hooks/guard.mjs
 *   backend/footfirma/.claude/hooks/guard.mjs
 * Ao alterar, copie por cima nos três — os repositórios são independentes e cada um
 * precisa carregar o próprio guard.
 *
 * Contrato: recebe o evento PreToolUse em JSON no stdin e responde em JSON no stdout.
 * Em qualquer erro interno, libera (fail-open) — um guard quebrado não pode travar o
 * trabalho. O custo disso é que ele protege, mas não é uma fronteira de segurança.
 */

import { execFileSync } from "node:child_process"
import { readFileSync, statSync } from "node:fs"

const LIMITE_LEITURA = 512 * 1024 // 512 KiB por arquivo escaneado

const PADROES_DE_SEGREDO = [
  { nome: "GitHub PAT clássico", re: /\bghp_[A-Za-z0-9]{20,}\b/ },
  { nome: "GitHub PAT fine-grained", re: /\bgithub_pat_[A-Za-z0-9_]{20,}\b/ },
  { nome: "chave estilo OpenAI", re: /\bsk-[A-Za-z0-9_-]{20,}\b/ },
  { nome: "Google API key", re: /\bAIza[0-9A-Za-z_-]{20,}\b/ },
  { nome: "AWS access key id", re: /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/ },
  { nome: "chave privada PEM", re: /-----BEGIN (?:RSA |EC |OPENSSH |PGP |)PRIVATE KEY-----/ },
  { nome: "URL de banco com senha", re: /\b(?:postgres|postgresql|redis|mysql|mongodb):\/\/[^:\s/]+:[^@\s]+@/ },
  { nome: "JWT", re: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/ },
]

const EXTENSOES_ESCANEAVEIS = /\.(java|kt|ts|tsx|js|jsx|mjs|cjs|json|ya?ml|properties|env|sql|sh|gradle|md|txt|xml)$/i
const MIGRATION = /(?:^|\/)db\/migration\/V\d+__[^/]+\.sql$/i

// ---------------------------------------------------------------- infraestrutura

const evento = await lerEvento()
if (!evento) liberar()

const ferramenta = evento.tool_name ?? ""
const entrada = evento.tool_input ?? {}
const cwd = evento.cwd || process.cwd()

try {
  if (ferramenta === "Bash") avaliarComando(String(entrada.command ?? ""))
  else if (ferramenta === "Edit" || ferramenta === "Write") avaliarEscrita(String(entrada.file_path ?? ""))
} catch {
  // fail-open: guard nunca derruba a sessão
}
liberar()

async function lerEvento() {
  try {
    let bruto = ""
    process.stdin.setEncoding("utf8")
    for await (const pedaco of process.stdin) bruto += pedaco
    return JSON.parse(bruto || "{}")
  } catch {
    return null
  }
}

function negar(motivo) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "deny",
        permissionDecisionReason: motivo,
      },
    })
  )
  process.exit(0)
}

function liberar() {
  process.exit(0)
}

function git(args) {
  // stderr silenciado: `ls-files --error-unmatch` falha de propósito no caminho feliz
  // (arquivo não versionado) e o ruído vazaria para o terminal do usuário.
  return execFileSync("git", args, {
    cwd,
    encoding: "utf8",
    maxBuffer: 32 * 1024 * 1024,
    stdio: ["ignore", "pipe", "ignore"],
  })
}

// ------------------------------------------------------------------ regras Bash

function avaliarComando(comando) {
  if (!comando) return

  bloquearSubidaDeServico(comando)
  bloquearGitPerigoso(comando)

  if (/\bgit\s+(add|commit)\b/.test(comando)) {
    const alterados = coletarAlteracoes()
    bloquearMigrationAlterada(alterados)
    bloquearSegredoNoStage(alterados)
  }
}

function bloquearSubidaDeServico(comando) {
  const servicos = [
    [/\b(?:pnpm|npm run|yarn|bun run)\s+dev\b/, "servidor de desenvolvimento do frontend"],
    [/\bnext\s+dev\b/, "servidor de desenvolvimento do Next.js"],
    [/\bturbo\s+(?:run\s+)?dev\b/, "turbo dev"],
    [/\bgradlew\s+(?:\S+\s+)*boot(?:Test)?Run\b/, "aplicação Spring Boot"],
    [/\bdocker[-\s]compose\s+up\b/, "containers do docker compose"],
    [/\bdocker\s+run\b/, "container docker"],
    [/\bbrew\s+services\s+start\b/, "serviço do Homebrew"],
  ]

  for (const [re, alvo] of servicos) {
    if (re.test(comando)) {
      negar(
        `Bloqueado pelo guard do FootFirma: este comando sobe ${alvo}, e o projeto não permite ` +
          `que o agente inicie serviços por conta própria (regra em AGENTS.md).\n\n` +
          `Se for isso mesmo que você quer, peça ao usuário para rodar na própria sessão:\n` +
          `  ! ${comando.trim()}\n\n` +
          `Para validar sem subir nada, use: pnpm lint / pnpm typecheck / pnpm build no frontend, ` +
          `./gradlew compileJava no backend.`
      )
    }
  }
}

/**
 * Bloqueia apenas o que publica no remote. Operações locais — merge, rebase, amend,
 * reset, clean, branch -D — são do usuário e não passam por aqui: elas não saem da
 * máquina dele e desfazer é problema local. O que não pode acontecer sem pedido
 * explícito é o agente empurrar qualquer coisa para o `origin`.
 */
function bloquearGitPerigoso(comando) {
  const perigosos = [
    [/\bgit\s+push\b.*--force\b/, "`git push --force`"],
    [/\bgit\s+push\b.*--force-with-lease\b/, "`git push --force-with-lease`"],
    [/\bgit\s+push\b/, "`git push`"],
  ]

  for (const [re, rotulo] of perigosos) {
    if (re.test(comando)) {
      negar(
        `Bloqueado pelo guard do FootFirma: ${rotulo} publica no remote e exige ` +
          `pedido explícito do usuário (regra em AGENTS.md).\n\n` +
          `Se o usuário já pediu, ele mesmo executa na sessão:\n  ! ${comando.trim()}`
      )
    }
  }
}

/** Une o que está no stage com o que está modificado no worktree. */
function coletarAlteracoes() {
  const linhas = []
  for (const args of [
    ["diff", "--cached", "--name-status"],
    ["diff", "--name-status"],
  ]) {
    let saida = ""
    try {
      saida = git(args)
    } catch {
      continue
    }
    for (const linha of saida.split("\n")) {
      const limpa = linha.trim()
      if (!limpa) continue
      const [status, ...resto] = limpa.split(/\s+/)
      const arquivo = resto.join(" ").trim()
      if (arquivo) linhas.push({ status: status ?? "", arquivo })
    }
  }
  return linhas
}

function bloquearMigrationAlterada(alterados) {
  const tocadas = alterados
    .filter((x) => x.status.startsWith("M") && MIGRATION.test(x.arquivo))
    .map((x) => x.arquivo)

  const unicas = [...new Set(tocadas)]
  if (unicas.length === 0) return

  negar(
    `Bloqueado pelo guard do FootFirma: migration Flyway já versionada foi modificada:\n` +
      unicas.map((f) => `  • ${f}`).join("\n") +
      `\n\nMigration aplicada é imutável. Reverta a alteração ` +
      `(\`git checkout -- <arquivo>\`) e crie uma nova migration \`V{n+1}__descricao.sql\` ` +
      `com a correção. Ver backend/footfirma/.rules/database.md.`
  )
}

function bloquearSegredoNoStage(alterados) {
  const achados = []

  let diff = ""
  try {
    diff = git(["diff", "--cached"])
  } catch {
    diff = ""
  }
  for (const { nome, re } of PADROES_DE_SEGREDO) {
    if (re.test(diff)) achados.push(`${nome} (no diff staged)`)
  }

  const arquivos = [...new Set(alterados.filter((x) => !x.status.startsWith("D")).map((x) => x.arquivo))]
  for (const arquivo of arquivos) {
    if (!EXTENSOES_ESCANEAVEIS.test(arquivo)) continue
    let conteudo = ""
    try {
      if (statSync(arquivo).size > LIMITE_LEITURA) continue
      conteudo = readFileSync(arquivo, "utf8")
    } catch {
      continue
    }
    for (const { nome, re } of PADROES_DE_SEGREDO) {
      if (re.test(conteudo)) achados.push(`${nome} em ${arquivo}`)
    }
  }

  if (achados.length === 0) return

  negar(
    `Bloqueado pelo guard do FootFirma: possível segredo no que seria commitado:\n` +
      [...new Set(achados)].map((a) => `  • ${a}`).join("\n") +
      `\n\nRemova o valor e leia-o de variável de ambiente. Se o segredo já foi exposto ` +
      `em algum momento, rotacione-o — remover do commit não basta.\n` +
      `Falso positivo (exemplo em documentação)? Peça ao usuário para commitar na própria sessão.`
  )
}

// -------------------------------------------------------------- regras de escrita

function avaliarEscrita(caminho) {
  if (!caminho || !MIGRATION.test(caminho)) return

  // Arquivo ainda não versionado é migration nova — pode escrever à vontade.
  let versionada = false
  try {
    git(["ls-files", "--error-unmatch", caminho])
    versionada = true
  } catch {
    versionada = false
  }
  if (!versionada) return

  negar(
    `Bloqueado pelo guard do FootFirma: ${caminho} é uma migration Flyway já versionada.\n\n` +
      `Migration aplicada é imutável — editar quebra o checksum do Flyway em qualquer ambiente ` +
      `onde ela já rodou. Crie uma nova migration \`V{n+1}__descricao.sql\` com a correção.\n` +
      `Ver backend/footfirma/.rules/database.md.`
  )
}

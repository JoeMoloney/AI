import { realpath } from "node:fs/promises"
import { isAbsolute, relative, sep } from "node:path"
import type { Plugin } from "@opencode/plugin"
import type { SessionRequest } from "@opencode/plugin/promise/session"
import { BUDGET, classify, chooseMode, isLocalChatURL, isTarget, rewriteBody } from "./policy.ts"
import type { Kind, Mode, Override } from "./policy.ts"

type State = { override: Override; intent: Mode }

export async function install(ctx: Plugin.Context): Promise<void> {
  const root = await realpath(ctx.location.directory)
  // Primary calls are serialized per session. Carry their decision into a
  // request header before raw model-body defaults are applied.
  const primaryModes = new Map<string, Mode>()

  async function inScope(sessionID: Parameters<typeof ctx.session.get>[0]["sessionID"]): Promise<boolean> {
    const session = await ctx.session.get({ sessionID })
    const directory = await realpath(session.location.directory)
    const path = relative(root, directory)
    return path === "" || (!path.startsWith(`..${sep}`) && path !== ".." && !isAbsolute(path))
  }

  async function state(sessionID: string): Promise<State> {
    const value = await ctx.storage.get(`mode:${sessionID}`)
    if (value && typeof value === "object" && !Array.isArray(value)) {
      const record = value as Record<string, unknown>
      const override = record.override
      const intent = record.intent
      return {
        override: override === "brief" || override === "analysis" ? override : "auto",
        intent: intent === "analysis" ? "analysis" : "brief",
      }
    }
    return { override: "auto", intent: "brief" }
  }

  await ctx.session.hook("prompt", async event => {
    const session = await ctx.session.get({ sessionID: event.sessionID })
    if (!session.model || !isTarget(session.model) || !await inScope(event.sessionID)) return
    const current = await state(event.sessionID)
    await ctx.storage.set(`mode:${event.sessionID}`, {
      ...current, intent: classify(event.prompt.text, Boolean(event.prompt.files?.length)),
    })
  })

  async function route(event: SessionRequest, kind: Kind = "primary"): Promise<void> {
    if (!isTarget(event.model) || !await inScope(event.sessionID)) return
    const auxiliary = kind !== "primary"
    const current = await state(event.sessionID)
    const coordinator = "agent" in event && event.agent === "re-coordinator"
    const mode = auxiliary || coordinator ? "brief" : chooseMode(current.override, current.intent, event.messages)
    if (!auxiliary) primaryModes.set(event.sessionID, mode)
    event.options.maxTokens = kind === "compaction" ? BUDGET.compaction : auxiliary ? BUDGET.auxiliary : BUDGET[mode]
    event.options.reasoningEffort = mode === "analysis" ? "medium" : "none"
    event.system.push({ type: "text", text: auxiliary
      ? "Produce a compact factual summary, aiming for 500-800 words rather than filling the output budget. Preserve objective, evidence references, unresolved questions and next action. Reference artifacts instead of copying code. Do not add new analysis."
      : `CURRENT REQUEST MODE: ${mode}. Earlier mode labels in history belong to earlier requests. Start this answer with [${mode === "brief" ? "Brief" : "Analysis"}]. ${mode === "brief"
          ? "Answer conceptual questions directly and concisely; do not start a file/code investigation unless verification was requested."
          : "Investigate one small item at a time. Separate evidence from hypotheses; keep explanations concise."}`,
    })
  }

  const scope = { providerID: "ollama" }
  await ctx.session.hook("context", event => route(event), scope)
  await ctx.session.hook("generate", event => route(event, "generate"), scope)
  await ctx.session.hook("compaction", event => route(event, "compaction"), scope)
  await ctx.session.hook("title", event => route(event, "title"), scope)

  await ctx.session.hook("model.request", async event => {
    if (!isTarget(event.model) || !await inScope(event.sessionID)) return
    event.headers["x-opencode-re-mode"] = event.kind === "primary"
      ? primaryModes.get(event.sessionID) ?? "brief" : "brief"
  }, scope)

  await ctx.session.hook("http.request", async event => {
    if (!isTarget(event.model) || !isLocalChatURL(event.request.url) || !await inScope(event.sessionID)) return
    if (event.request.method !== "POST") return
    const body = await event.request.clone().json() as Record<string, unknown>
    const hint = event.request.headers.get("x-opencode-re-mode")
    const result = rewriteBody(body, event.kind, hint === "brief" || hint === "analysis" ? hint : undefined)
    const headers = new Headers(event.request.headers)
    headers.delete("content-length")
    headers.set("x-opencode-re-mode", result.mode)
    event.request = new Request(event.request, { body: JSON.stringify(result.body), headers })
    await ctx.storage.set(`last-route:${event.sessionID}`, {
      mode: result.mode, kind: event.kind, maxTokens: result.maxTokens,
    })
    // Never log prompts, history, pseudocode, credentials, or responses.
    console.info(`[re-mode] ${event.kind} ${result.mode} max_tokens=${result.maxTokens}`)
  }, scope)

  await ctx.command.transform(editor => {
    for (const [name, mode] of [["re-brief", "brief"], ["re-analyse", "analysis"], ["re-auto", "auto"]] as const) {
      editor.add({
        name,
        description: `Set local Qwen RE routing to ${mode}; optionally supply a task`,
        async execute({ sessionID, prompt, delivery }) {
          const session = await ctx.session.get({ sessionID })
          if (!session.model || !isTarget(session.model) || !await inScope(sessionID)) {
            await ctx.session.synthetic({ sessionID, text: "RE routing only applies to ollama/qwen3.8:27b in this workspace. No model was changed." })
            return
          }
          const current = await state(sessionID)
          await ctx.storage.set(`mode:${sessionID}`, { ...current, override: mode })
          const previous = await ctx.storage.get(`last-route:${sessionID}`)
          const route = previous && typeof previous === "object" && !Array.isArray(previous)
            ? previous as Record<string, unknown> : undefined
          const detail = route && (route.mode === "brief" || route.mode === "analysis") && typeof route.maxTokens === "number"
            ? ` Last routed request: ${route.mode}, cap ${route.maxTokens} tokens.` : ""
          await ctx.session.synthetic({ sessionID, text: `Local Qwen RE routing: ${mode}.${detail}` })
          if (prompt.text.trim() || prompt.files?.length) {
            await ctx.session.prompt({ ...prompt, sessionID, delivery })
          }
        },
      })
    }
  })
}

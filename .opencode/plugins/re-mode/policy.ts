import type { Message } from "@opencode/ai"

export type Mode = "brief" | "analysis"
export type Override = Mode | "auto"
export type Kind = "primary" | "compaction" | "title" | "generate"

export const TARGET = { providerID: "ollama", id: "qwen3.8:27b" } as const
export const BUDGET = { brief: 1024, analysis: 4096, compaction: 4096, auxiliary: 1536 } as const

export function isTarget(model: { providerID: string; id: string }): boolean {
  return model.providerID === TARGET.providerID && model.id === TARGET.id
}

export function classify(text: string, attachments = false): Mode {
  const goal = text.slice(0, 4000).toLowerCase()
  // Conceptual explanations mentioning "decompile" should not launch an investigation.
  if (/\b(what is (?:decompilation|pseudocode|lua|an? function)|was .*coded|written in lua|only .*xex|file (?:types?|formats?)|(?:identify|classify).{0,40}header|how (?:would|do) we)\b/.test(goal)
      && !/\b0x[\da-f]{6,}\b|```/.test(goal)) return "brief"
  if (/^(?:please\s+)?(?:list|show|count)\b/.test(goal)
      && !/\b(explain|interpret|trace|compare|investigate|decompile)\b/.test(goal)) return "brief"
  if (attachments || /\b(decompil\w*|pseudocode|disassembl\w*|functions?|routines?|callers?|callees?|vtable|control.flow|trace|investigate|reverse.engineer\w*|data.structures?)\b|\b0x[\da-f]{6,}\b|```/.test(goal)) return "analysis"
  return "brief"
}

export function chooseMode(override: Override, intent: Mode, messages: readonly Message[]): Mode {
  if (override !== "auto") return override
  // Only inspect tool names after the latest user message; never classify old tool results.
  for (const message of [...messages].reverse()) {
    if (message.role === "user") {
      const text = message.content.filter(p => p.type === "text").map(p => p.text).join("\n")
      if (text.startsWith("Local Qwen RE routing:") || text.startsWith("RE routing only applies to")) continue
      return text ? classify(text, message.content.some(p => p.type === "media")) : intent
    }
    if (message.content.some(p => p.type === "tool-result"
        && /^ghidra_(decompile|disassemble|get_function_calls)/.test(p.name))) return "analysis"
  }
  // A compaction may remove the original user turn; preserve its enum, not its text.
  return intent
}

export function isLocalChatURL(value: string): boolean {
  const url = new URL(value)
  return url.protocol === "http:" && ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)
    && url.port === "11434" && url.pathname.endsWith("/v1/chat/completions")
}

export function rewriteBody(body: Record<string, unknown>, kind: Kind, chosen?: Mode): {
  body: Record<string, unknown>; mode: Mode; maxTokens: number
} {
  const auxiliary = kind === "compaction" || kind === "title" || kind === "generate"
  // A primary request's header carries its decision past safe model-body
  // defaults. The token field is a fallback for callers without that header.
  const requested = body.max_tokens ?? body.max_completion_tokens
  const mode: Mode = auxiliary ? "brief" : chosen ?? (requested === BUDGET.analysis && body.reasoning_effort !== "none" ? "analysis" : "brief")
  const budget = kind === "compaction" ? BUDGET.compaction : auxiliary ? BUDGET.auxiliary : BUDGET[mode]
  const maxTokens = chosen || auxiliary ? budget
    : typeof requested === "number" && requested > 0 ? Math.min(requested, budget) : budget
  const result: Record<string, unknown> = { ...body, max_tokens: maxTokens, reasoning_effort: mode === "analysis" ? "medium" : "none" }
  delete result.max_completion_tokens
  if (body.reasoning && typeof body.reasoning === "object" && !Array.isArray(body.reasoning)) {
    result.reasoning = { ...body.reasoning, effort: result.reasoning_effort }
  }
  return { body: result, mode, maxTokens }
}

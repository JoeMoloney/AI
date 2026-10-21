import assert from "node:assert/strict"
import { test } from "node:test"
import { BUDGET, classify, chooseMode, isLocalChatURL, isTarget, rewriteBody } from "../.opencode/plugins/re-mode/policy.ts"
import { install } from "../.opencode/plugins/re-mode/router.ts"
import type { Plugin } from "../.opencode/node_modules/@opencode/plugin/dist/promise/index.d.ts"
import type { Message } from "../.opencode/node_modules/@opencode/ai/dist/schema/messages.d.ts"

test("conceptual/file-format questions stay brief; code interpretation escalates", () => {
  for (const prompt of ["Was the game written in Lua?", "Is it only the XEX we need to decompile?", "What is decompilation?", "Explain file formats", "Identify a file from its header", "Report progress"]) {
    assert.equal(classify(prompt), "brief", prompt)
  }
  for (const prompt of ["Decompile one function", "Trace callers of 0x82170000", "Interpret this pseudocode", "Investigate the control flow", "Compare data structures"]) {
    assert.equal(classify(prompt), "analysis", prompt)
  }
  assert.equal(classify("Inspect the attached code", true), "analysis")
  assert.equal(classify("Identify the file from its header", true), "brief")
  assert.equal(classify("What does this pseudocode do?", true), "analysis")
  assert.equal(classify("Explain this function"), "analysis")
  assert.equal(classify("List five functions at 0x401000"), "brief")
})

test("manual overrides and compacted-goal fallback are deterministic", () => {
  assert.equal(chooseMode("brief", "analysis", []), "brief")
  assert.equal(chooseMode("analysis", "brief", []), "analysis")
  assert.equal(chooseMode("auto", "analysis", []), "analysis")
  const messages = [
    { role: "tool", content: [{ type: "tool-result", name: "ghidra_decompile_function" }] },
    { role: "user", content: [{ type: "text", text: "What is Lua?" }] },
  ] as unknown as Message[]
  assert.equal(chooseMode("auto", "analysis", messages), "brief")
})

test("only the exact Ollama model and local chat endpoint match", () => {
  assert.ok(isTarget({ providerID: "ollama", id: "qwen3.8:27b" }))
  assert.ok(!isTarget({ providerID: "openai", id: "qwen3.8:27b" }))
  assert.ok(!isTarget({ providerID: "ollama", id: "another-model" }))
  assert.ok(isLocalChatURL("http://127.0.0.1:11434/v1/chat/completions"))
  assert.ok(!isLocalChatURL("https://cloud.example/v1/chat/completions"))
  assert.ok(!isLocalChatURL("http://127.0.0.1:9999/v1/chat/completions"))
})

test("wire budgets use max_tokens and auxiliary calls stay brief", () => {
  const original = { model: "test", messages: [{ role: "user", content: "synthetic" }], max_completion_tokens: BUDGET.analysis, reasoning: { effort: "high" } }
  const analysis = rewriteBody(original, "primary")
  assert.equal(analysis.mode, "analysis")
  assert.equal(analysis.body.max_tokens, 4096)
  assert.equal(analysis.body.reasoning_effort, "medium")
  assert.ok(!("max_completion_tokens" in analysis.body))
  assert.equal(original.reasoning.effort, "high")
  assert.deepEqual(analysis.body.messages, original.messages)
  for (const kind of ["title", "compaction"] as const) {
    const result = rewriteBody(original, kind)
    assert.equal(result.mode, "brief")
    assert.equal(result.body.reasoning_effort, "none")
    assert.equal(result.body.max_tokens, kind === "compaction" ? 4096 : 1536)
  }
  assert.equal(rewriteBody({ max_tokens: 8 }, "primary").maxTokens, 8)
  const fallback = { max_tokens: 1536, reasoning_effort: "none" }
  assert.equal(rewriteBody(fallback, "primary", "analysis").maxTokens, 4096)
  assert.equal(rewriteBody(fallback, "primary", "brief").maxTokens, 1024)
  assert.equal(rewriteBody(fallback, "generate", "analysis").maxTokens, 1536)
  const safeFallback = { max_tokens: 4096, reasoning_effort: "none" }
  assert.equal(rewriteBody(safeFallback, "primary").mode, "brief")
  assert.equal(rewriteBody(safeFallback, "primary").maxTokens, 1024)
  assert.equal(rewriteBody(safeFallback, "compaction", "analysis").maxTokens, 4096)
  assert.equal(rewriteBody(safeFallback, "compaction", "analysis").body.reasoning_effort, "none")
})

function fixture() {
  const hooks = new Map<string, Function>()
  const commands = new Map<string, any>()
  const storage = new Map<string, any>()
  const messages: any[] = []
  const models: Record<string, any> = {
    local: { model: { providerID: "ollama", id: "qwen3.8:27b" }, location: { directory: process.cwd() } },
    cloud: { model: { providerID: "openai", id: "gpt-6.1-sol" }, location: { directory: process.cwd() } },
    outside: { model: { providerID: "ollama", id: "qwen3.8:27b" }, location: { directory: "/tmp/opencode" } },
  }
  const ctx = {
    location: { directory: process.cwd() },
    storage: { get: async (key: string) => storage.get(key), set: async (key: string, value: any) => { storage.set(key, value) } },
    session: {
      hook: async (name: string, callback: Function) => { hooks.set(name, callback) },
      get: async ({ sessionID }: any) => models[sessionID],
      synthetic: async (value: any) => { messages.push(value) },
      prompt: async (value: any) => { messages.push(value) },
    },
    command: { transform: async (callback: Function) => callback({ add: (command: any) => commands.set(command.name, command) }) },
  } as unknown as Plugin.Context
  return { ctx, hooks, commands, storage, messages, models }
}

test("hooks are no-ops for cloud, other models, and sessions outside the workspace", async () => {
  const f = fixture()
  await install(f.ctx)
  for (const sessionID of ["cloud", "outside"]) {
    const event = { sessionID, model: f.models[sessionID].model, messages: [], system: [], options: {} }
    await f.hooks.get("context")!(event)
    assert.deepEqual(event.options, {})
    assert.deepEqual(event.system, [])
  }
})

test("commands persist only mode enums, preserve attachments, and do not switch models", async () => {
  const f = fixture()
  await install(f.ctx)
  const prompt = { text: "synthetic task", files: [{ uri: "file:///synthetic" }] }
  await f.commands.get("re-analyse").execute({ sessionID: "local", prompt, delivery: "queue" })
  assert.deepEqual(f.storage.get("mode:local"), { override: "analysis", intent: "brief" })
  assert.deepEqual(f.messages[1], { ...prompt, sessionID: "local", delivery: "queue" })
  await f.commands.get("re-brief").execute({ sessionID: "cloud", prompt: { text: "" } })
  assert.ok(!f.storage.has("mode:cloud"))
})

test("the native HTTP hook enforces caps without dropping headers or prompt data", async () => {
  const f = fixture()
  await install(f.ctx)
  const event = {
    sessionID: "local", model: f.models.local.model, kind: "primary",
    request: new Request("http://localhost:11434/v1/chat/completions", {
      method: "POST", headers: { "content-type": "application/json", "x-test": "preserved" },
      body: JSON.stringify({ max_completion_tokens: 1024, messages: [{ role: "user", content: "synthetic" }] }),
    }),
  }
  await f.hooks.get("http.request")!(event)
  const body = await event.request.json()
  assert.equal(body.max_tokens, 1024)
  assert.equal(body.reasoning_effort, "none")
  assert.equal(event.request.headers.get("x-test"), "preserved")
  assert.equal(event.request.headers.get("x-opencode-re-mode"), "brief")
  assert.equal(body.messages[0].content, "synthetic")
  assert.deepEqual(f.storage.get("last-route:local"), { mode: "brief", kind: "primary", maxTokens: 1024 })
})

test("forced analysis does not turn summaries or titles into deep reasoning", async () => {
  const f = fixture()
  f.storage.set("mode:local", { override: "analysis", intent: "analysis" })
  await install(f.ctx)
  for (const hook of ["compaction", "title"]) {
    const event = { sessionID: "local", model: f.models.local.model, messages: [], system: [], options: {} as Record<string, unknown> }
    await f.hooks.get(hook)!(event)
    assert.equal(event.options.maxTokens, hook === "compaction" ? 4096 : 1536)
    assert.equal(event.options.reasoningEffort, "none")
  }
})

test("a mode notice is not mistaken for the task after compaction", () => {
  const messages = [{ role: "user", content: [{ type: "text", text: "Local Qwen RE routing: auto." }] }] as unknown as Message[]
  assert.equal(chooseMode("auto", "analysis", messages), "analysis")
})

test("cloud HTTP requests are left byte-for-byte unchanged", async () => {
  const f = fixture()
  await install(f.ctx)
  const request = new Request("https://cloud.example/v1/chat/completions", { method: "POST", body: '{"synthetic":true}' })
  const event = { sessionID: "cloud", model: f.models.cloud.model, kind: "primary", request }
  await f.hooks.get("http.request")!(event)
  assert.equal(event.request, request)
  assert.equal(await event.request.text(), '{"synthetic":true}')
  assert.ok(!f.storage.has("last-route:cloud"))
})

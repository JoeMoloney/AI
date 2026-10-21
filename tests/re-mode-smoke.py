"""Opt-in live router checks: fresh synthetic session, local Qwen, all tools denied.

Creates a test session in OpenCode. Does not read/reuse existing sessions, game
files, or game memory. Leaves the synthetic session available for inspection.
"""
import json
from pathlib import Path
import subprocess
import urllib.parse


ROOT = Path(__file__).resolve().parents[1]


def api(method, path, body=None):
    args = ["opencode", "api", method, path]
    if body is not None:
        args.extend(["--data", json.dumps(body)])
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=180)
    if result.returncode:
        raise RuntimeError("Synthetic test API request failed: " + result.stderr[:500])
    return json.loads(result.stdout).get("data") if result.stdout.strip() else None


def latest(session_id, kind):
    query = urllib.parse.urlencode({"type": kind, "limit": "1", "order": "desc"})
    return api("get", f"/api/session/{session_id}/message?{query}")[0]


def command(session_id, name, text=""):
    api("post", f"/api/session/{session_id}/command", {"name": name, "text": text})


def prompt(session_id, text):
    api("post", f"/api/session/{session_id}/prompt", {"text": text})
    api("post", f"/api/experimental/session/{session_id}/wait")
    return latest(session_id, "assistant")


def assert_route(session_id, mode, cap):
    command(session_id, "re-auto")
    status = latest(session_id, "synthetic")["text"]
    assert f"Last routed request: {mode}, cap {cap} tokens." in status, status


def main():
    session = api("post", "/api/session", {
        "title": "RE router synthetic smoke test",
        "agent": "build",
        "model": {"providerID": "ollama", "id": "qwen3.8:27b"},
        "location": {"directory": str(ROOT)},
        "permissions": [{"action": "*", "resource": "*", "effect": "deny"}],
    })
    sid = session["id"]
    print("Synthetic test session:", sid, flush=True)
    for mode, cap, text in [
        ("brief", 1024, "Give a short readiness acknowledgement. Synthetic test only; do not use tools or inspect files."),
        ("analysis", 4096, "Interpret synthetic pseudocode at synthetic address 0x401000: int increment(int x) { return x + 1; }. Explain in two sentences. No tools or real files."),
    ]:
        reply = prompt(sid, text)
        assert reply["finish"] == "stop"
        assert reply.get("tokens", {}).get("output", 0) <= cap
        assert_route(sid, mode, cap)
        print(f"PASS: auto {mode}, real wire cap {cap}", flush=True)

    command(sid, "re-brief", "Explain synthetic pseudocode int identity(int x) { return x; } in one sentence. Do not use tools or inspect files.")
    api("post", f"/api/experimental/session/{sid}/wait")
    reply = latest(sid, "assistant")
    assert not any(part.get("type") == "reasoning" and part.get("text") for part in reply["content"])
    assert_route(sid, "brief", 1024)
    print("PASS: forced brief on an analysis task, no reasoning trace", flush=True)

    # Purely fabricated history; enough to exercise real compaction, never game data.
    for block in range(8):
        text = "Synthetic checkpoint for router verification only.\n" + "\n".join(
            f"fabricated item {block}-{i}: complete; expected synthetic result {i * 3}; no real files involved."
            for i in range(80)
        )
        api("post", f"/api/session/{sid}/synthetic", {"text": text})
    api("post", f"/api/session/{sid}/compact", {})
    api("post", f"/api/experimental/session/{sid}/wait")
    compact = latest(sid, "compaction")
    assert compact["status"] == "completed"
    assert compact.get("tokens", {}).get("output", 0) <= 4096
    print("PASS: real compaction completed within the 4096-token fallback cap", flush=True)
    print("No existing sessions, game files, or game memory were read or modified.")


if __name__ == "__main__":
    main()

"""Readiness means a program is analyzed and its Java backend responds."""
import json
from pathlib import Path
import sys
import urllib.request


def main():
    try:
        url = Path("/tmp/ghidra-backend-url").read_text().strip()
        if not url.startswith("http://127.0.0.1:"):
            return 1
        with urllib.request.urlopen(url + "health", timeout=3) as response:
            payload = json.load(response)
        return 0 if payload.get("data", {}).get("mode") == "headless" else 1
    except (OSError, ValueError):
        return 1


if __name__ == "__main__":
    sys.exit(main())

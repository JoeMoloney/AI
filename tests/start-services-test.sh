#!/usr/bin/env bash
# Non-destructive launcher regressions: all Docker operations are mocked.
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
source ./start-services.sh

draw() { :; }
load_env() { GHIDRA_BINARY=/data/mock-binary; }

# The network helper is a child Bash process, so export its Docker mock.
docker() {
  [[ $* == 'network inspect selfhost_network' ]]
}
export -f docker

mock_compose() {
  printf 'COMPOSE'
  printf ' %s' "$@"
  printf '\n'
  [[ ${COMPOSE_FAIL:-} != "$1" ]]
}
COMPOSE=( mock_compose )

check_menu() {
  local label=$1 input=$2 expected=$3 output
  output=$(printf '%s' "$input" | menu)
  if [[ $output != *"COMPOSE up -d --build --force-recreate $expected"$'\n'* ]]; then
    printf 'FAIL: %s\n%s\n' "$label" "$output" >&2
    exit 1
  fi
  printf 'PASS: %s\n' "$label"
}

check_menu 'Enter starts only the always-on trio' $'\n' \
  'ollama memory-db memory-service'
check_menu 'invalid line does not retain chat selection' $'chat invalid-token\nrole\n' \
  'ollama memory-db memory-service sillytavern'
check_menu 'invalid line followed by Enter clears all selections' $'studio invalid-token\n\n' \
  'ollama memory-db memory-service'
check_menu 'multiple invalid lines do not accumulate selections' $'chat bad\nart bad\nrole\n' \
  'ollama memory-db memory-service sillytavern'
check_menu 'combined presets and numbers work' $'WEB,3\n' \
  'ollama memory-db memory-service open-webui searxng comfyui'
check_menu 'all excludes unfinished Ghidra' $'all\n' \
  'ollama memory-db memory-service open-webui searxng comfyui sillytavern'
check_menu 'Ghidra is selected only explicitly' $'re\n' \
  'ollama memory-db memory-service ghidra-mcp'

output=$(printf 're\n' | menu)
[[ $output == *$'COMPOSE pull ollama memory-db\n'* ]]
printf 'PASS: locally built services are not pulled\n'

output=$(
  load_env() { unset GHIDRA_BINARY; }
  rc=0
  printf 're\n' | menu 2>&1 || rc=$?
  [[ $rc == 11 ]]
)
[[ $output != *COMPOSE* && $output == *'set GHIDRA_BINARY'* ]]
printf 'PASS: missing Ghidra target is rejected before Compose operations\n'

for input in $'q\n' ''; do
  rc=0
  output=$(printf '%s' "$input" | menu) || rc=$?
  if [[ $input == $'q\n' ]]; then expected_rc=10; else expected_rc=1; fi
  [[ $rc == "$expected_rc" && $output != *COMPOSE* ]]
done
printf 'PASS: quit and EOF perform no Compose operations\n'

for action in stop logs; do
  output=$(printf '%s\n' "$action" | menu)
  [[ $output != *'COMPOSE up'* && $output != *'COMPOSE pull'* ]]
  if [[ $action == stop ]]; then
    [[ $output == *$'COMPOSE down\n'* ]]
  else
    [[ $output == *'COMPOSE logs -f'* ]]
  fi
done
printf 'PASS: stop and logs do not start services\n'

for phase in pull up; do
  COMPOSE_FAIL=$phase
  rc=0
  output=$(printf '\n' | menu 2>&1) || rc=$?
  [[ $rc == 11 && $output != *'  running:'* ]]
  if [[ $phase == pull ]]; then [[ $output != *'COMPOSE up'* ]]; fi
done
unset COMPOSE_FAIL
printf 'PASS: pull and build/start failures are reported\n'

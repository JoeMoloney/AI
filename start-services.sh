#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# start-services.sh — pick which self-hosted AI services to start
#
#   ollama is always started (it's the AI engine the rest plug into)
#
# services:
#   1) open-webui   chat UI + search & image tools   :3000
#   2) searxng      search backend for open-webui    :8080
#   3) comfyui      image generation (GPU)           :8188
#   4) sillytavern  character / roleplay chat        :8000
#   5) ghidra-mcp   Ghidra RE bridge (127.0.0.1)     :18081
#
# presets:
#   chat     open-webui
#   web      open-webui + searxng
#   art      open-webui + comfyui
#   studio   open-webui + searxng + comfyui
#   role     sillytavern
#   re       ghidra-mcp
#   all      everything except ghidra-mcp
#
# input: numbers and/or presets, space- or comma-separated
#   Enter  start ollama only
#   q      quit, start nothing
#   stop   stop all services in this project (docker compose down)
#   logs   follow live logs of every service (docker compose logs -f)
#   unknown token → the menu is shown again
#
# exit codes: 0 started/stopped · 1 quit / aborted / compose failure
#
# needs: bash, docker, docker compose (v2 plugin or legacy binary)
# ---------------------------------------------------------------------------

set -u

# --- color (off when not a terminal, TERM=dumb, or NO_COLOR is set) -------
if [[ -t 1 && -z ${NO_COLOR:-} && ${TERM:-x} != dumb ]]; then
  B=$'\e[1m'  # bold
  G=$'\e[32m' # green
  Y=$'\e[33m' # yellow
  C=$'\e[36m' # teal
  D=$'\e[2m'  # dim
  E=$'\e[0m'  # reset
else
  B='' G='' Y='' C='' D='' E=''
fi

# --- static data ------------------------------------------------------------
# menu order; ghidra-mcp last — it's a niche dev bridge, not a daily service
readonly SERVICES=( open-webui searxng comfyui sillytavern ghidra-mcp )
declare -A PORT=( [ollama]=11434 [open-webui]=3000 [searxng]=8080 \
                  [comfyui]=8188 [sillytavern]=8000 [ghidra-mcp]=18081 )
readonly -a ENV_FILES=( open-webui/.env open-webui/.apiKey searxng/.env \
                        comfyui/.env sillytavern/.env )

# selection state — set by pick(), read in start_selected()
declare -A ON=( [open-webui]=0 [searxng]=0 [comfyui]=0 \
                [sillytavern]=0 [ghidra-mcp]=0 )

# --- helpers ----------------------------------------------------------------
fail() {
  printf '  ! %s\n' "$*" >&2
  exit 1
}

# source the .env / .apiKey files so compose variables (volume paths, secret key)
# are expanded. Needed for BOTH up and down — `docker compose down` parses the
# same ${VAR}:/path volume specs, and an empty one yields
#   invalid spec: :/root/.ollama: empty section between colons
load_env() {
  set -a
  local f
  for f in "${ENV_FILES[@]}"; do
    [[ -f $f ]] || { set +a; fail "missing env file: ${f}"; }
    # shellcheck disable=SC1090
    source "$f"
  done
  set +a
}

# --- menu --------------------------------------------------------------------
draw() {
  local -a L=() line
  L+=("${B}  AI services — pick what to start${E}  ${D}· ollama always on · http://localhost:${PORT[ollama]}${E}")
  L+=("")
  L+=("  ${D}    1)${E}  ${B}open-webui${E}   ${C}http://localhost:3000${E}  ${D}chat UI + search & image tools${E}")
  L+=("  ${D}    2)${E}  ${B}searxng${E}      ${C}http://localhost:8080${E}  ${D}search backend for open-webui${E}")
  L+=("  ${D}    3)${E}  ${B}comfyui${E}      ${C}http://localhost:8188${E}  ${D}image generation (GPU)${E}")
  L+=("  ${D}    4)${E}  ${B}sillytavern${E}  ${C}http://localhost:8000${E}  ${D}character / roleplay chat${E}")
  L+=("  ${D}    5)${E}  ${B}ghidra-mcp${E}   ${C}http://127.0.0.1:18081${E} ${D}Ghidra RE/SSE-MCP bridge${E}")
  L+=("")
  L+=("  ${B}presets:${E}")
  L+=("    ${D}chat${E}     open-webui")
  L+=("    ${D}web${E}      open-webui + searxng")
  L+=("    ${D}art${E}      open-webui + comfyui")
  L+=("    ${D}studio${E}   open-webui + searxng + comfyui")
  L+=("    ${D}role${E}     sillytavern")
  L+=("    ${D}re${E}       ghidra-mcp")
  L+=("    ${D}all${E}      everything except ghidra-mcp")
  for line in "${L[@]}"; do
    printf '%s\n' "$line"
  done
  printf '  %stype numbers and/or presets (space or comma) · Enter = ollama only · stop = stop all · logs = tail all · q = quit%s\n' "$D" "$E"
  printf '  > '
}

pick() {
  local s
  for s in "$@"; do ON[$s]=1; done
}

# menu() exit codes: 0 started · 10 quit · 11 compose failed · 1 aborted(EOF)
menu() {
  local line bad tok
  local -a toks=()
  while :; do
    draw
    IFS= read -r line || return 1          # EOF (Ctrl-D)
    line=${line//,/ }
    read -ra toks <<< "$line"
    bad=''
    for tok in "${toks[@]}"; do
      tok=${tok,,}
      case $tok in
        q)      return 10 ;;
        stop)   stop_all; return $? ;;
        logs)   show_logs; return $? ;;
        chat)   pick open-webui ;;
        web)    pick open-webui searxng ;;
        art)    pick open-webui comfyui ;;
        studio) pick open-webui searxng comfyui ;;
        role)   pick sillytavern ;;
        re)     pick ghidra-mcp ;;
        all)    pick open-webui searxng comfyui sillytavern ;;
        1)      pick open-webui ;;
        2)      pick searxng ;;
        3)      pick comfyui ;;
        4)      pick sillytavern ;;
        5)      pick ghidra-mcp ;;
        *)      bad+=" $tok" ;;
      esac
    done
    if [[ -n $bad ]]; then
      printf '\n  ! unknown token(s):%s\n     valid: 1-5 · chat web art studio role re all · stop · logs · q to quit\n' "$bad"
      continue                        # show the menu again
    fi
    start_selected
    return $?
  done
}

# --- preflight ----------------------------------------------------------------
COMPOSE=( docker compose )

preflight() {
  command -v docker >/dev/null 2>&1 \
    || fail "docker not found — install it first:" \
         "  Arch:    sudo pacman -S docker" \
         "  then enable: sudo systemctl enable --now docker" \
         "  (and add yourself to the docker group: sudo usermod -aG docker \$USER, then re-login)"
  if ! "${COMPOSE[@]}" version >/dev/null 2>&1; then
    fail "docker compose not usable — for the docker-ce build install the plugin:" \
         "  sudo pacman -S docker  docker-compose (usually bundled as 'compose' plugin)" \
         "  or: sudo pacman -S docker-compose && sudo systemctl restart docker"
  fi
  if ! docker info >/dev/null 2>&1; then
    fail "docker daemon unreachable — is it running?  sudo systemctl start docker"
  fi
}

# --- start -------------------------------------------------------------------
start_selected() {
  local -a targets=( ollama ) pull_targets=( ollama )
  local svc

  for svc in "${SERVICES[@]}"; do
    if [[ ${ON[$svc]} -eq 1 ]]; then
      targets+=( "$svc" )
      # ghidra-mcp is build-based (no image) → nothing to pull
      [[ $svc == ghidra-mcp ]] || pull_targets+=( "$svc" )
    fi
  done

  printf '\nstarting: %s\n\n' "${targets[*]}"

  load_env

  if ! "${COMPOSE[@]}" pull "${pull_targets[@]}"; then
    printf '  ! failed: %s pull\n' "${COMPOSE[*]}" >&2
    return 11
  fi
  if ! "${COMPOSE[@]}" up -d --force-recreate "${targets[@]}"; then
    printf '  ! failed: %s up -d --force-recreate\n' "${COMPOSE[*]}" >&2
    return 11
  fi

  printf '\n'
  printf '  running:\n'
  for svc in "${targets[@]}"; do
    if [[ $svc == ghidra-mcp ]]; then
      printf '    %-12s http://127.0.0.1:%s   %sSSE/MCP endpoint — not a web page%s\n' \
             "$svc" "${PORT[$svc]}" "$D" "$E"
      printf '  %s    note: needs the Ghidra server running on port 18080 (host)%s\n' "$D" "$E"
    else
      printf '    %-12s http://localhost:%s\n' "$svc" "${PORT[$svc]}"
    fi
  done
  printf '\n'
  printf '  logs:   %s logs -f <service>\n' "${COMPOSE[*]}"
  printf '  status: %s ps\n'                "${COMPOSE[*]}"
  printf '  stop:   %s down [service...]\n' "${COMPOSE[*]}"
  return 0
}

# --- stop --------------------------------------------------------------------
stop_all() {
  # down parses the same compose file as up, so it needs the same env vars for
  # the ${VAR}:/path volume specs to expand
  load_env
  printf '\nstopping: all services in this project (ollama open-webui searxng comfyui sillytavern ghidra-mcp)\n\n'
  if ! "${COMPOSE[@]}" down; then
    printf '  ! failed: %s down\n' "${COMPOSE[*]}" >&2
    return 11
  fi
  printf '  done.\n'
  printf '  status: %s ps\n'        "${COMPOSE[*]}"
  printf '  start:  %s\n'           "./start-services.sh"
  return 0
}

# --- logs --------------------------------------------------------------------
show_logs() {
  # stream every service's logs live — the attached, foreground log view that
  # `docker compose up` (without -d) gives us. compose still parses the
  # ${VAR}:/path volume specs, so source env first (same reason stop does).
  load_env
  printf '\nlogs: all services (ollama open-webui searxng comfyui sillytavern ghidra-mcp)\n'
  printf '  (Ctrl-C to stop following)\n\n'
  "${COMPOSE[@]}" logs -f
  return $?
}

# --- main ---------------------------------------------------------------------
main() {
  cd "$(dirname -- "$0")" || fail "cannot cd to the script directory"
  [[ -f docker-compose.yml ]] || fail "no docker-compose.yml here — is this the AI repo?"
  preflight

  if [[ ! -t 0 || ! -t 1 ]]; then
    fail "needs an interactive terminal (no tty on stdin/stdout)"
  fi

  local rc=0
  menu || rc=$?

  case $rc in
    0)  : ;;
    10) printf '\n  nothing started.\n' ;;
    11) printf '\n  ! compose step failed — see the error above.\n' ;;
    *)  printf '\n  aborted — nothing started.\n' ;;
  esac
  [[ $rc -eq 0 ]] || exit 1
}

trap 'exit 130' INT
trap 'exit 143' TERM

main "$@"

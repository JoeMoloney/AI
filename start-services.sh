#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# start-services.sh — interactive picker for the self-hosted AI services
#
#   ollama     always started (it's the AI engine the rest plug into)
#   open-webui, searxng, comfyui, sillytavern  opt-in, default OFF
#
# keys:
#   w s c t    toggle open-webui / searxng / comfyui / sillytavern
#   a          select all optional services
#   n          deselect all optional services
#   Enter      start what's selected (detached)
#   q          quit without starting
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
SERVICES=( open-webui searxng comfyui sillytavern )
declare -A KEY=( [open-webui]=w [searxng]=s [comfyui]=c [sillytavern]=t )
declare -A PORT=( [ollama]=11434 [open-webui]=3000 [searxng]=8080 \
                  [comfyui]=8188 [sillytavern]=8000 )
ENV_FILES=( open-webui/.env open-webui/.apiKey searxng/.env \
            comfyui/.env sillytavern/.env )

declare -A ON=( [open-webui]=0 [searxng]=0 [comfyui]=0 [sillytavern]=0 )

# --- helpers ----------------------------------------------------------------
fail() {
  printf '  ! %s\n' "$*" >&2
  exit 1
}

# --- menu state ---------------------------------------------------------------
MENULINES=0
CURSOR_HIDDEN=0

cleanup() {
  if [[ $CURSOR_HIDDEN -eq 1 ]]; then
    printf '\e[?25h\n'
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

draw() {
  local -a L=()
  local svc k
  L+=("${B}  AI services — pick what to start${E}")
  L+=("")
  L+=("  ${C}●${E}${B} ollama ${E}${D}always on · http://localhost:${PORT[ollama]}${E}")
  for svc in "${SERVICES[@]}"; do
    k=${KEY[$svc]}
    if [[ ${ON[$svc]} -eq 1 ]]; then
      L+=("  ${G}[x]${E}${B} ${svc}${E}  ${D}(${k}) · http://localhost:${PORT[$svc]}${E}")
    else
      L+=("  ${D}[ ]  ${svc}  (${k}) · http://localhost:${PORT[$svc]}${D}${E}")
    fi
  done
  L+=("")
  L+=("  ${B}w s c t${E} toggle   ${B}a${E} all   ${B}n${E} none   ${B}Enter${E} start   ${B}q${E} quit")

  local n=${#L[@]}
  if [[ $MENULINES -gt 0 ]]; then
    printf "\e[${MENULINES}A"
  fi
  local line
  for line in "${L[@]}"; do
    printf '%s\n' "$line"
  done
  printf '\e[J'
  MENULINES=$n
}

toggle_key() {
  local k=$1 svc
  for svc in "${SERVICES[@]}"; do
    if [[ ${KEY[$svc]} == "$k" ]]; then
      ON[$svc]=$(( 1 - ON[$svc] ))
      return 0
    fi
  done
  return 1
}

# menu exit codes: 0 started · 10 quit(q) · 11 compose failure · 1 aborted
menu() {
  local key
  while :; do
    draw
    IFS= read -rsn1 key || return 1     # EOF (Ctrl-D)
    case $key in
      '')   start_selected; return $? ;;
      q|Q)  return 10 ;;
      a|A)  local s; for s in "${SERVICES[@]}"; do ON[$s]=1; done ;;
      n|N)  local s; for s in "${SERVICES[@]}"; do ON[$s]=0; done ;;
      *)    toggle_key "$key" || : ;;
    esac
  done
}

# --- actions -----------------------------------------------------------------
COMPOSE=( docker compose )
preflight() {
  command -v docker >/dev/null 2>&1 || {
    printf '\n  missing dependency: docker\n\n'
    cat <<'EOF'
    Arch/CachyOS : sudo pacman -S docker
                   sudo systemctl enable --now docker
    macOS        : brew install colima docker && colima start
    Windows      : wsl --install  (then inside WSL: sudo apt install docker.io)
EOF
    fail "install docker first"
  }

  if ! docker compose version >/dev/null 2>&1; then
    if command -v docker-compose >/dev/null 2>&1; then
      COMPOSE=( docker-compose )
      printf '  note: using legacy "docker-compose"\n'
    else
      printf '\n  missing dependency: docker compose\n'
      echo '    install: https://docs.docker.com/compose/install/'
      fail "install docker compose first"
    fi
  fi

  docker info >/dev/null 2>&1 || {
    printf '\n  docker daemon not reachable\n'
    echo '    Arch/WSL : sudo systemctl start docker'
    echo '    macOS    : start Docker Desktop, or: colima start'
    fail "start the docker daemon first"
  }
}

start_selected() {
  local -a targets=( ollama )
  local svc f

  for svc in "${SERVICES[@]}"; do
    [[ ${ON[$svc]} -eq 1 ]] && targets+=( "$svc" )
  done

  printf '\nstarting: %s\n\n' "${targets[*]}"

  set -a
  for f in "${ENV_FILES[@]}"; do
    [[ -f $f ]] || { set +a; fail "missing env file: ${f}"; }
    # shellcheck disable=SC1090
    source "$f"
  done
  set +a

  local step
  for step in "pull" "up -d --force-recreate"; do
    if ! "${COMPOSE[@]}" $step "${targets[@]}"; then
      printf '  ! failed: %s %s\n' "${COMPOSE[*]}" "$step" >&2
      return 11
    fi
  done

  printf '\n'
  printf '  running:\n'
  for svc in "${targets[@]}"; do
    printf '    %-12s http://localhost:%s\n' "$svc" "${PORT[$svc]}"
  done
  printf '\n'
  printf '  logs:   %s logs -f <service>\n' "${COMPOSE[*]}"
  printf '  status: %s ps\n'                "${COMPOSE[*]}"
  printf '  stop:   %s down [service...]\n' "${COMPOSE[*]}"
  return 0
}

# --- main ---------------------------------------------------------------------
main() {
  cd "$(dirname -- "$0")" || fail "cannot cd to the script directory"
  [[ -f docker-compose.yml ]] || fail "no docker-compose.yml here — is this the AI repo?"
  preflight

  if [[ ! -t 0 || ! -t 1 ]]; then
    fail "needs an interactive terminal (no tty on stdin/stdout)"
  fi

  CURSOR_HIDDEN=1
  printf '\e[?25l'

  local rc=0
  menu || rc=$?
  CURSOR_HIDDEN=0

  if [[ $MENULINES -gt 0 ]]; then
    printf "\e[${MENULINES}A\e[J"
  fi
  case $rc in
    0)  : ;;
    10) printf '\n  nothing started.\n' ;;
    11) printf '\n  ! compose step failed — see the error above.\n' ;;
    *)  printf '\n  aborted — nothing started.\n' ;;
  esac
  [[ $rc -eq 0 ]] || exit 1
}

main "$@"

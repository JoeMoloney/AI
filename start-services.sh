set -a
source ./open-webui/.env
source ./searxng/.env
source ./comfyui/.env
source ./sillytavern/.env
set +a
docker compose pull
docker compose up --force-recreate
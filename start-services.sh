set -a
source ./open-webui/.env
source ./searxng/.env
source ./comfui/.env
source ./sillytavern/.env
set +a
docker compose pull
docker compose up
set -a
source ./open-webui/.env
source ./searxng/.env
set +a
docker compose pull
docker compose up
set -a
source ./.env
set +a
docker compose pull
docker compose up
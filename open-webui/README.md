# Open WebUI @ Home
The Open WebUI at home:

## Access

- This computer: http://localhost:3000
- Trusted LAN devices: http://192.168.1.50:3000

WebUI authentication remains enabled. Only Open WebUI is published on the LAN;
the backend ports stay bound to host loopback. The explicit
LAN binding in `docker-compose.yml` must be updated if the host IP changes
(a router DHCP reservation is recommended). Do not forward this port from the
internet. Plain HTTP is not encrypted; use a trusted network or add HTTPS/VPN
before using it on an untrusted network.

## Ollama runtime settings

The Compose configuration requests a 131072-token default context and one
parallel inference slot (`OLLAMA_NUM_PARALLEL=1`). Large contexts can use
substantial VRAM; lower `OLLAMA_CONTEXT_LENGTH` if a model exceeds your GPU's
capacity. These defaults do not override a client's explicit context settings;
OpenCode's local Qwen profile separately advertises a 32768-token context.

## Pre-requisites
Standard Docker installation on Linux Mint
+ installed & enabled nvidia container toolkit
+ + allows use to utilise our GPU hardware for faster processing
+ added `$USER` to docker usergroup
+ + I **don't** want to `sudo docker compose` everytime

## Usage
+ Depending where you want to store the containers data on your system
+ + update the `.env` file accordingly`
+ + + I've stored mine on a NVME M.2 that is symlinked to my `/home/` directory for ease of access
+ Create a gitignored `.apiKey` beside `.env` containing `WEBUI_SECRET_KEY=<secret>`.
+ + Use a persistent random signing secret (generate one with `openssl rand -hex 32`), not an Open WebUI API token. Keep your existing value on established installations.
+ + Restrict the file with `chmod 600 .apiKey`; do not regenerate it on each startup.
+ From the open-webui folder, export both files before starting:

```bash
../create-docker-network.sh
set -a
source ./.env
source ./.apiKey
set +a
docker compose up
```

+ Run `docker compose down` to gracefully stop the containers when you're finished.

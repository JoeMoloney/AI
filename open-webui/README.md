# Open WebUI @ Home
The Open WebUI at home:

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
+ From the open-webui folder run: `docker compose up` to start up the containers
+ + `docker compose down` to gracefully shutdown the containers when you're finished
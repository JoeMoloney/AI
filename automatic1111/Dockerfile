FROM debian:stable-slim

WORKDIR /

RUN apt-get update \
    && apt-get -y install wget git python3 python3-venv libgl1 libglib2.0-0

RUN git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui

RUN chmod +x stable-diffusion-webui/webui.sh

WORKDIR /stable-diffusion-webui

EXPOSE 7860

RUN pwd && ls -a

ENTRYPOINT ["/bin/bash", "webui.sh"]
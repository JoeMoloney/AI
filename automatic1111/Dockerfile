FROM debian:stable-slim

# RUN useradd -ms --disabled-password /bin/bash containerUser
# USER root

WORKDIR /

RUN apt-get update \
    && apt-get -y install wget git python3 python3-venv libgl1 libglib2.0-0

# RUN wget -q https://raw.githubusercontent.com/AUTOMATIC1111/stable-diffusion-webui/master/webui.sh
RUN git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui

RUN chmod +x stable-diffusion-webui/webui.sh

#COMMANDLINE_ARGS=--skip-torch-cuda-test

EXPOSE 7860

RUN pwd && ls -a

ENTRYPOINT ["/bin/bash", "/stable-diffusion-webui/webui.sh"]
# CMD ["-f"]
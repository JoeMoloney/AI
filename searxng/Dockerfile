FROM debian:stable-slim

#See For Installation Guide: https://docs.searxng.org/admin/installation-searxng.html

WORKDIR /

#Install packages
RUN su - && apt-get update && apt-get install -y \
    sudo pip python3-dev python3-babel \
    python3-venv python-is-python3 \
    uwsgi uwsgi-plugin-python3 \
    git build-essential libxslt-dev \
    zlib1g-dev libffi-dev libssl-dev

#Create user
RUN sudo useradd --shell /bin/bash --system \
    --home-dir "/usr/local/searxng" \
    --comment 'Privacy-respecting metasearch engine' \
    searxng
RUN sudo mkdir "/usr/local/searxng"
RUN sudo chown -R "searxng:searxng" "/usr/local/searxng"

#Clone SearXNG & create the virtualenv
USER searxng
RUN git clone "https://github.com/searxng/searxng" "/usr/local/searxng/searxng-src" && \
    python3 -m venv "/usr/local/searxng/searx-pyenv" && \
    echo ". /usr/local/searxng/searx-pyenv/bin/activate" \
                   >>  "/usr/local/searxng/.profile"

#Check virtualenv is sourced
RUN cat ~/.profile

# RUN command -v python && \ 
#     python --version && \
#     pip install --break-system-packages pip setuptools wheel pyyaml msgspec

# RUN cd "/usr/local/searxng/searxng-src" && \
#     command -v python && \ 
#     python --version && \
#     pip install --break-system-packages --use-pep517 --no-build-isolation -e .
WORKDIR /usr/local/searxng/searxng-src
RUN command -v python && \
    python --version && \
    pip install --break-system-packages pip setuptools wheel pyyaml msgspec && \
    pip install --break-system-packages --use-pep517 --no-build-isolation -e .
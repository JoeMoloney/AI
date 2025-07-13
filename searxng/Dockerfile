FROM debian:stable-slim

#See For Installation Guide: https://docs.searxng.org/admin/installation-searxng.html

WORKDIR /

#Install Packages
RUN apt-get update \
    && apt-get -y install sudo python3-dev python3-babel python3-venv python-is-python3 uwsgi uwsgi-plugin-python3 git build-essential libxslt-dev zlib1g-dev libffi-dev libssl-dev python3-pip

#Create User
RUN sudo -H useradd --shell /bin/bash --system \
    --home-dir "/usr/local/searxng" \
    --comment 'Privacy-respecting metasearch engine' \
    searxng \
    && sudo mkdir "/usr/local/searxng" \
    && sudo chown -R "searxng:searxng" "/usr/local/searxng"

#Install SearXNG, Dependencies & VirtualEnv
RUN sudo -H -u searxng -i \
    && git clone "https://github.com/searxng/searxng" "/usr/local/searxng/searxng-src" \
    && python3 -m venv "/usr/local/searxng/searx-pyenv" \
    && echo ". /usr/local/searxng/searx-pyenv/bin/activate" >> "/usr/local/searxng/.profile"

ENV PATH="/usr/local/searxng/searx-pyenv/bin:$PATH"

#Check VirtualEnv is sourced from login (~/.profile)
RUN sudo -H -u searxng -i \ 
    && command -v python && python --version

#Update pip boilerplate
RUN pip install pip \
    && pip install setuptools \
    && pip install wheel \
    && pip install pyyaml

#Install SearXNG into virtualenv
RUN sudo -H -u searxng -i \
    && cd "/usr/local/searxng/searxng-src" \
    && pip install --use-pep517 --no-build-isolation -e .
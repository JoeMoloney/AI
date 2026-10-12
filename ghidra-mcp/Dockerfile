FROM eclipse-temurin:25-jdk-noble AS ghidra

ARG GHIDRA_VERSION=12.1.4
ARG GHIDRA_BUILD_DATE=20260921
ARG GHIDRA_SHA256=ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db
ARG XENON_VERSION=0.7.0
ARG XENON_SHA256=d3cc32fcd1af154a6d18f5a53ec779e0e60fbd32b9cade5f5225b7a48fefd6bc

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       unzip \
        wget \
        ca-certificates \
        procps \
        libxrender1 \
        libxtst6 \
        libxi6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt

RUN wget -O /tmp/ghidra.zip \
      "https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_${GHIDRA_VERSION}_build/ghidra_${GHIDRA_VERSION}_PUBLIC_${GHIDRA_BUILD_DATE}.zip" \
    && echo "${GHIDRA_SHA256}  /tmp/ghidra.zip" | sha256sum -c - \
    && unzip -q /tmp/ghidra.zip \
    && rm /tmp/ghidra.zip \
    && mv "ghidra_${GHIDRA_VERSION}_PUBLIC" ghidra

RUN wget -O /tmp/xenon.zip \
      "https://github.com/EveryoneEverybody/Xenon360/releases/download/v${XENON_VERSION}/ghidra_${GHIDRA_VERSION}_XenonVMX128_${XENON_VERSION}.zip" \
    && echo "${XENON_SHA256}  /tmp/xenon.zip" | sha256sum -c - \
    && unzip -q /tmp/xenon.zip -d /opt/ghidra/Ghidra/Extensions \
    && rm /tmp/xenon.zip

ENV GHIDRA_HOME=/opt/ghidra
ENV GHIDRA_INSTALL_DIR=/opt/ghidra
ENV GHIDRA_MAXMEM=4G
ENV HOME=/home/ghidra

RUN if ! getent group 1000 >/dev/null; then groupadd --gid 1000 ghidra; fi \
    && if getent passwd 1000 >/dev/null; then \
         usermod --login ghidra --home /home/ghidra --shell /usr/sbin/nologin \
           "$(getent passwd 1000 | cut -d: -f1)"; \
       else \
         useradd --uid 1000 --gid 1000 --create-home \
           --home-dir /home/ghidra --shell /usr/sbin/nologin ghidra; \
       fi \
    && mkdir -p /data /projects /exports /scripts /logs /home/ghidra \
    && chown 1000:1000 /data /projects /exports /scripts /logs /home/ghidra

WORKDIR /opt/ghidra
USER ghidra

CMD ["sleep", "infinity"]

FROM ghidra AS mcp
USER root

ARG GHIDRAMCP_REF=1e980252087b2479a21b9ca6d82d5829538651a7

RUN apt-get update \
    && apt-get install -y --no-install-recommends git python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*

RUN git init /opt/ghidramcp \
    && git -C /opt/ghidramcp remote add origin https://github.com/LaurieWired/GhidraMCP.git \
    && git -C /opt/ghidramcp fetch --depth 1 origin "${GHIDRAMCP_REF}" \
    && git -C /opt/ghidramcp checkout --detach FETCH_HEAD

RUN /opt/ghidra/support/gradle/gradlew --no-daemon \
      -p /opt/ghidramcp/extension buildExtension \
    && unzip -q /opt/ghidramcp/extension/dist/*.zip -d /opt/ghidra/Ghidra/Extensions

RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir \
      '/opt/ghidramcp[headless]' 'pyghidra>=3.1,<4'

COPY run_headless.py healthcheck.py /opt/ghidra-runner/

ENV PATH="/opt/venv/bin:${PATH}"
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

USER ghidra
EXPOSE 18081
HEALTHCHECK --interval=30s --timeout=5s --start-period=10m --retries=3 \
    CMD python /opt/ghidra-runner/healthcheck.py
ENTRYPOINT ["python", "/opt/ghidra-runner/run_headless.py"]
CMD []

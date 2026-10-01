FROM python:3.12-slim

ARG GHIDRAMCP_REPO=https://github.com/LaurieWired/GhidraMCP.git
ARG GHIDRAMCP_REF=27f316f80139e2d5dec882519a1bdf4aa46ac04c

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        git \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt

RUN git clone "${GHIDRAMCP_REPO}" ghidramcp \
    && cd ghidramcp \
    && git checkout --detach "${GHIDRAMCP_REF}" \
    && test "$(git rev-parse HEAD)" = "${GHIDRAMCP_REF}"

WORKDIR /opt/ghidramcp

RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

ENTRYPOINT ["python", "/opt/ghidramcp/bridge_mcp_ghidra.py"]
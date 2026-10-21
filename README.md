[![Typing SVG](https://readme-typing-svg.demolab.com?font=Fira+Code&weight=900&size=42&pause=1000&color=7500F7&width=500&height=70&lines=Support+Real+Artists)](https://git.io/typing-svg)
# AI Services Repository

I decided I wanted to experiment with AI considering it's everywhere including electric toothbrushes (for some reason): https://www.oralb.co.uk/en-gb/product-collections/genius-x)

This repository contains configurations to run various AI-based services through Docker Container Engine. The goal is to make setting up these services as smooth as possible, especially if you want to distro-hop, and to retain privacy and control over your data by using your own hardware.

## Overview

This setup allows you to run multiple AI services locally on your hardware, giving you complete control over data and processing. All services are containerized for easy deployment and management.

**Check through the subfolders for each individual service**


**Some AI Was Used In The Creation Of This Project**

## Current Services

### Open-WebUI
A chat interface for interacting with large language models. Accessible through a web UI.

### Ollama  
An application for running LLMs locally on your machine.

### SearXNG
A privacy-respecting metasearch engine that searches the web without tracking users.

### ComfyUI
A web UI for generating images from text.

### Memory DB
Database for storing LLM-backed sematic memory for long term projects and usage

### Memory Service
REST API and MCP to allow local LLMs to read/write to Memory DB service

### Ghidra MCP
Allows local LLMs to use ghidra tools and services for decompilation projects and research like abandoned Xbox 360 games.

### Automatic1111 (Deprecating because of ComfyUI - might remove later)
A web UI for generating images from text.

### SillyTavern (WIP)
A web UI for interacting with LLMs for generating images, personas, characters, stories etc.

## Getting Started

### Prerequisites
- Docker and Docker Compose installed
- At least 16GB RAM recommended for running AI services

### Setup
1. Clone this repository

2. Configure environment variables (if needed) via the .env files

3. Start selected services using `./start-services.sh`. The `studio` preset starts
   Open WebUI, SearXNG, and ComfyUI alongside the always-on services.

4. Access the services:
   - Open-WebUI: http://localhost:3000
   - SearXNG: http://localhost:8080
   - ComfyUI: http://localhost:8188
   - Automatic1111 Stable Diffusion: http://localhost:7860

Published backend ports bind to `127.0.0.1` for local access only. Open WebUI
additionally binds to `192.168.1.50:3000` for authenticated access from trusted
LAN devices; see [WebUI access notes](open-webui/README.md#access). Containers
still communicate over the shared Docker network. Existing containers must be
recreated to apply port-binding changes.

All Compose files use the external Docker network `selfhost_network`. The
launcher creates it if needed. Before running Compose manually (from the root
or an individual service folder), run `./create-docker-network.sh` from the
repository root; it is safe to run repeatedly. Existing containers must be
recreated to apply network changes. Manage each service through one Compose
project, rather than starting the same service from both the root and its folder.

### Configuration checks

Local Qwen's brief/analysis router and overrides are documented in
[the router guide](.opencode/plugins/re-mode/README.md). It is scoped to this
workspace and does not change cloud-model behaviour or run game analysis.

[Bounded local RE batches](.opencode/workflows/README.md) use a small coordinator,
fresh read-only workers, project-3 memories, and an ignored progress ledger.

```bash
bash tests/start-services-test.sh
python tests/test_memory_credentials.py
python tests/test_ghidra_runner.py
```

These check launcher behavior, memory-script credentials, and Ghidra target
isolation using mocks/temporary fixtures. No
containers are started or stopped, and no live database changes are made.

## Git workflow

See [the Git workflow guide](docs/git-workflow.md) for feature branches,
focused commits, partial staging, and pushing to the configured remotes.

## WIP (Work In Progress)
- Refine ComfyUI integration with Open-WebUI
- Hook up Automatic1111 to Open-WebUI

## Hopes & Dreams
Multiple smaller AI models that will communicate together and research the users prompt using SearXNG private web access. They shall be known as:
> **_The Council_**

## My Hardware & OS Specs:

+ **OS:** CachyOS Arch Linux
+ **Kernel:** 7.2.2-1-cachyos
+ **Packages:** 1842 (pacman)
+ **Shell:** zsh 5.9.2
+ **DE:** KDE Plasma 6.7.4 (Wayland)
+ **CPU:** Intel i9-10900K (20) @ 5.3GHz
+ **GPU 0:** NVIDIA GeForce RTX 3090 (24GB)
+ **GPU 1:** Intel CometLake-S GT2 (UHD Graphics 630)
+ **RAM *(Acquired before the price gouging)*:** 2x16GB (31921 MiB) @2133MHz

## Inspiration / Sources / Guides: 
- Open-WebUI Service: https://github.com/open-webui/open-webui
- Automatic1111 Stable Diffusion Service: https://github.com/AUTOMATIC1111/stable-diffusion-webui
- SearXNG Service: https://docs.searxng.org/admin/installation-searxng.html
- PewDiePie: https://github.com/pewdiepie-archdaemon
- OpenCode: https://opencode.ai/docs/
- - A codex-like CLI interface for conversing and working alongside an LLM. Works nicely with the local ollama docker container.

[![Typing SVG](https://readme-typing-svg.demolab.com?font=Fira+Code&weight=900&size=42&pause=1000&color=7500F7&width=500&height=70&lines=Support+Real+Artists)](https://git.io/typing-svg)
# AI Services Repository

I decided I wanted to experiment with AI considering it's everywhere including electric toothbrushes (for some reason): https://www.oralb.co.uk/en-gb/product-collections/genius-x)

This repository contains configurations to run various AI-based services through Docker Container Engine. The goal is to make setting up these services as smooth as possible, especially if you want to distro-hop, and to retain privacy and control over your data by using your own hardware. I think it is also important to understand how tech works so to have a better understanding of the ins and outs of it.

## Overview

This setup allows you to run multiple AI services locally on your hardware, giving you complete control over data, processing and customisation. All services are containerized for easy deployment and management. I am trying to keep it simple and straight forward as it grows and more ideas come to mind.

**Check through the subfolders for each individual service**

**I am a qualified software engineer, I refuse to accept vibecoding as the way forward for this industry, however some AI was used in the advancements of this project in order to save on time and scratch the excitement of POC'ing new features. **

## Current Services

### Open-WebUI
A chat interface for interacting with large language models. Accessible through the browser.

### Ollama  
The main engine for running LLMs on your machine.

### SearXNG
A privacy-respecting metasearch engine that searches the web without tracking users. Can be hooked up to LLMs via MCP tooling and such.

### ComfyUI
A web UI for generating images from text.

### Memory DB
Database for storing LLM-backed sematic memory for long term projects and usage.

### Memory Service
REST API and MCP to allow local LLMs to read/write to Memory DB service. I will eventually re-write this in Rust as part of learning Rust in my spare time.

### Ghidra MCP
Allows local LLMs to use ghidra tools and services for decompilation projects and research like abandoned Xbox 360 games.

### Automatic1111 (Deprecating because of ComfyUI - might remove later)
A web UI for generating images from text.

### SillyTavern (WIP - Unsure if will be retaining for the future)
A web UI for interacting with LLMs for generating images, personas, characters, stories etc.

## Getting Started

### Prerequisites
- Docker and Docker Compose installed
- At least 16GB RAM recommended for running AI services. (Good luck.. Looking at you Jensen.)

### Setup
1. Clone this repository

2. Configure environment variables (if needed) via the .env files

3. Start selected services using `./start-services.sh`. 
   Always-on services are Ollama, Memory-Service, Memory-DB
   Notable options are:
   `open-webui` - Includes the open-webui frontend for general chatting.
   `studio` - Includes open-webui, comfyui and searxng for general chatting with image generation and web browsing.
   `re` - Includes ghidra-mcp for reverse-engineering decomp recomp projects.

4. Accessing the services:
   - Open-WebUI: http://localhost:3000
   - SearXNG: http://localhost:8080
   - ComfyUI: http://localhost:8188

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

### Configuration checks (These need refinement as of `The Great Cleanening/Alignment`)

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

I decided I wanted gitlab to be my main source of truth whilst still pushing changes to github as my secondary/backup.
See [the Git workflow guide](docs/git-workflow.md) for feature branches,
focused commits, partial staging, and pushing to the configured remotes.

## TODO
- Refine ComfyUI integration with Open-WebUI
- Hook up Automatic1111 to Open-WebUI - or remove it entirely
- Better scripted ways to set up opencode to work with the parameters set in these services (context limits, parallel models etc)
- Making these self-hosted solutions as close to effective as frontier solutions 

## Hopes & Dreams
Multiple smaller AI models that will communicate together and research the users prompt using SearXNG private web access. They shall be known as:
> **_The Council_**

Albeit VRAM limitations and context limitations and model-weight/effectiveness is the tricky part here for me.

## My Hardware & OS Specs (Kept relatively up to date but never always bleeding edge):

+ **OS:** CachyOS Arch Linux
+ **Kernel:** 7.x.x-cachyos
+ **Shell:** zsh 5.9.2
+ **DE:** KDE Plasma (Wayland)
+ **CPU:** Intel i9-10900K (20) @ 5.3GHz
+ **GPU 0:** NVIDIA GeForce RTX 3090 (24GB)
+ **GPU 1:** Intel CometLake-S GT2 (UHD Graphics 630)
+ **RAM *(Acquired before the price gouging)*:** 2x16GB (31921 MiB) @2133MHz 
+ + Yes the MHz don't match the cpu baseline, I was in a rush right before covid and the great chip shortage of 2020 kicked in

## Inspiration / Sources / Guides: 
- Open-WebUI Service: https://github.com/open-webui/open-webui
- Automatic1111 Stable Diffusion Service: https://github.com/AUTOMATIC1111/stable-diffusion-webui
- SearXNG Service: https://docs.searxng.org/admin/installation-searxng.html
- PewDiePie: https://github.com/pewdiepie-archdaemon
- OpenCode: https://opencode.ai/docs/
- - A codex-like CLI interface for conversing and working alongside an LLM. Works nicely with the local ollama docker container.

## Some notable repos I highly suggest you check out:
- GPU-Unlock (Unlock FP/BF16 instructions issue rate on consumer 30 series cards): https://github.com/Zagitta/gpu-unlock
- RexGlue-SDK (Converts Xbox360 PowerPC code into portable C++): https://github.com/rexglue/rexglue-sdk
- GhidraMCP (Allow LLMs to access reverse engineering tools): https://github.com/LaurieWired/GhidraMCP
- Ghidra (Allows YOU to access reverse engineering tools): https://github.com/NationalSecurityAgency/ghidra
- VGPU-Unlock (Unlock virtual gpu functionality for consumer GPUs - albeit I havent had much success.. yet): https://github.com/DualCoder/vgpu_unlock
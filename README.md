# AI Services Repository

I decided I wanted to experiment with AI considering it's everywhere including electric toothbrushes (for some reason): https://www.oralb.co.uk/en-gb/product-collections/genius-x)

This repository contains configurations to run various AI-based services through Docker Container Engine. The goal is to make setting up these services as smooth as possible, especially if you want to distro-hop, and to retain privacy and control over your data by using your own hardware.

## Overview

This setup allows you to run multiple AI services locally on your hardware, giving you complete control over data privacy and processing. All services are containerized for easy deployment and management.

**Check through the subfolders for each individual service**

## Current Services

### Open-WebUI
A chat interface for interacting with large language models. Accessible through a web UI.

### Ollama  
An application for running LLMs locally on your machine.

### SearXNG
A privacy-respecting metasearch engine that searches the web without tracking users.

### ComfyUI
A web UI for generating images from text.

### Automatic1111
A web UI for generating images from text.

### SillyTavern
A web UI for interacting with LLMs for generating images, personas, characters, stories etc.

## Getting Started

### Prerequisites
- Docker and Docker Compose installed
- At least 16GB RAM recommended for running AI services

### Setup
1. Clone this repository

2. Configure environment variables (if needed) via the .env files

3. Start all services using .start-services.sh

4. Access the services:
   - Open-WebUI: http://localhost:3000
   - SearXNG: http://localhost:8080
   - ComfyUI: http://localhost:8188  
   - Automatic1111 Stable Diffusion: http://localhost:7860

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
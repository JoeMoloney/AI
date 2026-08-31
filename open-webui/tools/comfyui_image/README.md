# ComfyUI Model-Routed Image Generator

Modular refactor of the original Open WebUI ComfyUI image-generation
and image-editing tool, version 5.3.1.

## Structure

```text
comfyui_image_generator/
├── __init__.py
├── main.py
├── config.py
├── routing.py
├── workflows.py
├── openwebui.py
├── comfyui.py
├── diagnostics.py
└── README.md
```

## Responsibilities

### `main.py`

Open WebUI-facing `Tools` class.

Responsible for:

* tool entry points
* orchestration
* parameter validation
* status events
* coordinating the other modules

### `config.py`

Central configuration:

* ComfyUI URL
* workflow directory
* polling interval
* timeout
* node IDs
* edit denoise limits
* model/workflow routing

### `routing.py`

Handles:

* Open WebUI model detection
* model-to-workflow routing
* generation/edit workflow selection

### `workflows.py`

Handles:

* workflow file validation
* safe workflow path resolution
* JSON loading
* `%prompt%` replacement
* latent node configuration
* sampler configuration
* seed handling
* edit image injection
* edit denoise handling

### `openwebui.py`

Handles:

* Open WebUI chat lookup
* message inspection
* attachment discovery
* previous-image selection
* Open WebUI storage
* native File creation
* message file attachment

### `comfyui.py`

Handles:

* ComfyUI connection
* workflow queueing
* execution polling
* image extraction
* image downloading

### `diagnostics.py`

Handles:

* workflow directory diagnostics
* previous-image diagnostics

## Installation

The directory containing this package must be importable by the Python
environment running Open WebUI.

For example:

```text
/app/backend/data/functions/
└── comfyui_image_generator/
    ├── __init__.py
    ├── main.py
    ├── config.py
    ├── routing.py
    ├── workflows.py
    ├── openwebui.py
    ├── comfyui.py
    └── diagnostics.py
```

The tool entry point is:

```python
from comfyui_image_generator.main import Tools
```

If your Open WebUI installation automatically loads Python files from a
specific functions/tools directory, make sure the entire package directory
is placed somewhere on that environment's Python path.

## Configuration

Edit `config.py` to change:

```python
COMFYUI_URL = "http://comfyui:8188"
WORKFLOW_DIR = "/comfyui_workflows"
```

Workflow files remain external JSON files and are not embedded into Python.

## Existing routing preserved

The following model routes remain unchanged:

* `gemma4:12b-ImagePrompter_Editing`
* `Qwen3-VL-8B-Instruct-Unc-GGUF:Q8_0`
* `Qwen3-VL-8B-Instruct-Unc-Ani-GGUF:Q8_0`
* `Qwen3-VL-8B-Instruct-Unc-GGUF:Q8_0_V2`
* `Qwen3-VL-8B-Instruct-Unc-Ani-GGUF:Q8_0_V2`

## Existing behavior preserved

The refactor preserves:

* `%prompt%` replacement
* Flux latent/sampler selection
* non-Flux latent/sampler selection
* Flux Kontext editing
* `LoadImageFromBase64`
* current-message image preference
* previous-message image fallback
* Open WebUI storage
* native Open WebUI file records
* native chat file attachments
* ComfyUI `/prompt`
* ComfyUI `/history`
* ComfyUI `/view`
* seed generation
* width/height limits
* steps limits
* edit denoise limits
* workflow diagnostics
* previous-image diagnostics

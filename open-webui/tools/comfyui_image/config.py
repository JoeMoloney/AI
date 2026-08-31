COMFYUI_URL = "http://comfyui:8188"
WORKFLOW_DIR = "/comfyui_workflows"

POLL_INTERVAL = 1.0
TIMEOUT_SECONDS = 1800

# ------------------------------------------------------------
# IMAGE EDITING
# ------------------------------------------------------------

DEFAULT_EDIT_DENOISE = 1.0
MIN_EDIT_DENOISE = 0.0
MAX_EDIT_DENOISE = 1.0

# Flux Kontext Dev editing workflow node IDs.

EDIT_IMAGE_NODE = "196"
EDIT_SAMPLER_NODE = "192:31"

# ------------------------------------------------------------
# GENERATION WORKFLOW NODE IDs
# ------------------------------------------------------------

FLUX_LATENT_NODE = "56:50"
FLUX_SAMPLER_NODE = "56:52"

OTHER_LATENT_NODE = "48:31"
OTHER_SAMPLER_NODE = "48:33"

# ------------------------------------------------------------
# MODEL -> WORKFLOW ROUTING
# ------------------------------------------------------------

MODEL_WORKFLOWS = {
"gemma4:12b-ImagePrompter_Editing": {
"name": "Flux Dev",
"generate": "flux_dev_checkpoint.json",
"edit": "flux_kontext_dev_basic.json",
},
"Qwen3-VL-8B-Instruct-Unc-GGUF": {
"name": "Flux Dev UNC",
"generate": "flux_dev_checkpoint_unc.json",
"edit": None,
},
"Qwen3-VL-8B-Instruct-Unc-Ani-GGUF": {
"name": "NetaYume Lumina T2I UNC",
"generate": "image_netayume_lumina_t2i_unc.json",
"edit": None,
},
"Qwen3-VL-8B-Instruct-Unc-GGUF_V2": {
"name": "Flux Dev UNC V2",
"generate": "flux_dev_checkpoint_unc_V2.json",
"edit": None,
},
"Qwen3-VL-8B-Instruct-Unc-Ani-GGUF_V2": {
"name": "NetaYume Lumina T2I UNC V2",
"generate": "image_netayume_lumina_t2i_unc_V2.json",
"edit": None,
},
}
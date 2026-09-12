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

# FLUX.2 Klein image-edit workflow node IDs.
#
# Keep these explicit so Python never depends on JSON object ordering when
# deciding which base64 image goes into which workflow input.
SINGLE_EDIT_IMAGE_NODE = "131"
MULTI_EDIT_PRIMARY_IMAGE_NODE = "132"
MULTI_EDIT_REFERENCE_IMAGE_NODE = "133"

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
"qwen3-vl:30b-a3b-instruct": {
"name": "qwen3-vl:30b-a3b-instruct",
"generate": "image_flux2_text_to_image_9b.json",
"edit": "image_flux2_klein_image_edit_9b_base.json",
"reference_edit": "image_flux2_klein_image_edit_9b_base_multi.json",
},
"qwen3-vl:30b-a3b-instruct_Chroma": {
"name": "qwen3-vl:30b-a3b-instruct_Chroma",
"generate": "image_chroma_text_to_image.json"
},
"qwen3-vl:30b-a3b-instruct_ChromaRadiance": {
"name": "qwen3-vl:30b-a3b-instruct_ChromaRadiance",
"generate": "image_chroma1_radiance_text_to_image.json"
},
"qwen3-vl:30b-a3b-instruct_Netayume": {
"name": "qwen3-vl:30b-a3b-instruct_Netayume",
"generate": "image_netayume_lumina_t2i.json"
},
}

import importlib.util
import os
import sys
from typing import Optional


# ============================================================
# TOOL DIRECTORY
# ============================================================

# Use the directory where main.py is located instead of hardcoded paths
# This makes it work correctly whether run directly or via copy-paste in Open WebUI
TOOL_DIR = os.getenv(
    "COMFYUI_IMAGE_TOOL_DIR",
    os.path.dirname(os.path.abspath(__file__)),
)

# Debug: Print current working directory and paths for troubleshooting
print(f"[COMFYUI_IMAGE] TOOL_DIR set to: {TOOL_DIR}", flush=True)
print(f"[COMFYUI_IMAGE] Current working directory: {os.getcwd()}", flush=True)
print(f"[COMFYUI_IMAGE] Python path: {sys.path}", flush=True)

# Ensure the tool directory is in Python's path for dynamic imports
if TOOL_DIR not in sys.path:
    sys.path.insert(0, TOOL_DIR)
    print(f"[COMFYUI_IMAGE] Added TOOL_DIR to sys.path: {TOOL_DIR}", flush=True)


if not os.path.isdir(TOOL_DIR):
    raise RuntimeError(
        "ComfyUI image tool directory does not exist: "
        f"{TOOL_DIR}"
    )

print("[COMFYUI_IMAGE] main.py loaded", flush=True)

# ============================================================
# LOAD LOCAL MODULES
# ============================================================

def _load_local_module(module_name: str):

    module_path = os.path.join(
        TOOL_DIR,
        f"{module_name}.py",
    )

    if not os.path.isfile(module_path):
        raise RuntimeError(
            "Required tool module was not found.\n"
            f"Module: `{module_name}`\n"
            f"Path: `{module_path}`"
        )

    unique_name = (
        f"_comfyui_image_tool_{module_name}"
    )

    existing = sys.modules.get(unique_name)

    if existing is not None:
        return existing

    spec = importlib.util.spec_from_file_location(
        unique_name,
        module_path,
    )

    if spec is None or spec.loader is None:
        raise RuntimeError(
            "Could not create an import specification for "
            f"`{module_path}`."
        )

    module = importlib.util.module_from_spec(spec)

    sys.modules[unique_name] = module

    try:
        spec.loader.exec_module(module)

    except Exception:
        sys.modules.pop(unique_name, None)
        raise

    return module


# ============================================================
# LOAD DEPENDENCIES
# ============================================================

_config = _load_local_module("config")

# Make the local config available to the other modules under
# the exact name they expect.
sys.modules["comfyui_image_local_config"] = _config


# ============================================================
# IMPORTANT
# ============================================================
#
# The supporting modules currently use relative imports such as:
#
#     from .config import ...
#
# Those cannot work when loaded this way.
#
# Therefore we load the dependency modules through a temporary
# package namespace below.
# ============================================================


PACKAGE_NAME = "_comfyui_image_tool_package"


if PACKAGE_NAME not in sys.modules:

    import types

    package = types.ModuleType(
        PACKAGE_NAME
    )

    package.__path__ = [
        TOOL_DIR
    ]

    package.__file__ = os.path.join(
        TOOL_DIR,
        "__init__.py",
    )

    sys.modules[PACKAGE_NAME] = package


def _load_package_module(module_name: str):
    """
    Load a local module as part of a private package namespace.

    This allows supporting files containing:

        from .config import ...

    to work correctly without exposing generic module names
    like `config` to Open WebUI's Python environment.
    """

    full_name = (
        f"{PACKAGE_NAME}.{module_name}"
    )

    existing = sys.modules.get(full_name)

    if existing is not None:
        return existing

    module_path = os.path.join(
        TOOL_DIR,
        f"{module_name}.py",
    )

    if not os.path.isfile(module_path):
        raise RuntimeError(
            "Required tool module was not found.\n"
            f"Module: `{module_name}`\n"
            f"Path: `{module_path}`"
        )

    spec = importlib.util.spec_from_file_location(
        full_name,
        module_path,
    )

    if spec is None or spec.loader is None:
        raise RuntimeError(
            "Could not create import specification for "
            f"`{module_path}`."
        )

    module = importlib.util.module_from_spec(
        spec
    )

    sys.modules[full_name] = module

    try:
        spec.loader.exec_module(module)

    except Exception:
        sys.modules.pop(full_name, None)
        raise

    return module


# ============================================================
# LOAD TOOL MODULES
# ============================================================

_comfyui = _load_package_module(
    "comfyui"
)

_diagnostics = _load_package_module(
    "diagnostics"
)

_openwebui = _load_package_module(
    "openwebui"
)

_routing = _load_package_module(
    "routing"
)

_workflows = _load_package_module(
    "workflows"
)


# ============================================================
# EXPOSE CLASSES / CONFIGURATION
# ============================================================

ComfyUIClient = _comfyui.ComfyUIClient

Diagnostics = _diagnostics.Diagnostics

OpenWebUIManager = _openwebui.OpenWebUIManager

ModelRouter = _routing.ModelRouter

WorkflowManager = _workflows.WorkflowManager

MODEL_WORKFLOWS = _config.MODEL_WORKFLOWS

WORKFLOW_DIR = _config.WORKFLOW_DIR


# ============================================================
# OPEN WEBUI TOOL
# ============================================================

class Tools:

    def __init__(self):
        print("[COMFYUI_IMAGE] Tools.__init__()", flush=True)

        self.router = ModelRouter()
        print("[COMFYUI_IMAGE] ModelRouter created", flush=True)

        self.workflow_manager = WorkflowManager()
        print("[COMFYUI_IMAGE] WorkflowManager created", flush=True)

        self.openwebui = OpenWebUIManager()
        print("[COMFYUI_IMAGE] OpenWebUIManager created", flush=True)

        self.comfyui = ComfyUIClient()
        print("[COMFYUI_IMAGE] ComfyUIClient created", flush=True)

        self.diagnostics = Diagnostics(
            self.workflow_manager,
            self.openwebui,
        )

    # ========================================================
    # STATUS
    # ========================================================

    async def _status(
        self,
        emitter,
        description,
        done=False,
    ):
        if emitter:
            await emitter(
                {
                    "type": "status",
                    "data": {
                        "description": description,
                        "done": done,
                        "hidden": False,
                    },
                }
            )

    # ========================================================
    # PARAMETER VALIDATION
    # ========================================================

    def _validate_dimensions(
        self,
        width,
        height,
    ):
        try:
            width = int(width)
            height = int(height)

        except (TypeError, ValueError) as e:
            raise ValueError(
                "Width and height must be valid integers."
            ) from e

        width = max(
            64,
            min(width, 4096),
        )

        height = max(
            64,
            min(height, 4096),
        )

        return width, height

    def _validate_steps(
        self,
        steps,
    ):
        if steps is None:
            return None

        try:
            steps = int(steps)

        except (TypeError, ValueError) as e:
            raise ValueError(
                "Steps must be a valid integer."
            ) from e

        return max(
            1,
            min(steps, 200),
        )

    # ========================================================
    # DIAGNOSTICS
    # ========================================================

    async def test_workflow_directory(
        self,
    ) -> str:
        return self.diagnostics.test_workflow_directory()

    async def diagnose_previous_image(
        self,
        __chat_id__: Optional[str] = None,
        __message_id__: Optional[str] = None,
    ) -> str:
        return await self.diagnostics.diagnose_previous_image(
            __chat_id__,
            __message_id__,
        )

    # ========================================================
    # MAIN TOOL
    # ========================================================

    async def generate_image(
        self,
        prompt: str,
        width: int = 1024,
        height: int = 1024,
        steps: Optional[int] = None,
        seed: int = -1,
        edit_previous: bool = False,
        denoise: Optional[float] = None,
        __model__: Optional[dict] = None,
        __event_emitter__=None,
        __user__: Optional[dict] = None,
        __chat_id__: Optional[str] = None,
        __message_id__: Optional[str] = None,
    ) -> str:

        print(
            "[COMFYUI_IMAGE] generate_image() START "
            f"prompt={prompt!r} "
            f"width={width} "
            f"height={height} "
            f"steps={steps} "
            f"seed={seed} "
            f"edit_previous={edit_previous}",
            flush=True,
        )

        # ====================================================
        # IDENTIFY MODEL
        # ====================================================

        model_name = self.router.get_model_name(
            __model__
        )

        print(
            "[COMFYUI_IMAGE] MODEL IDENTIFIED: "
            f"{model_name!r}",
            flush=True,
        )

        if not model_name:
            return (
                "❌ I couldn't determine the configured "
                "Open WebUI model.\n\n"
                "Available routed models:\n"
                + "\n".join(
                    f"- `{name}`"
                    for name in MODEL_WORKFLOWS
                )
            )

        route = self.router.get_route(
            model_name
        )

        print(
            "[COMFYUI_IMAGE] ROUTE: "
            f"{route!r}",
            flush=True,
        )

        workflow_name = route["name"]

        # ====================================================
        # DETERMINE WORKFLOW
        # ====================================================

        try:
            workflow_filename = (
                self.router.get_workflow_filename(
                    model_name,
                    edit_previous,
                )
            )
            print(
                "[COMFYUI_IMAGE] WORKFLOW SELECTED: "
                f"{workflow_filename!r}",
                flush=True,
)

        except ValueError:

            if edit_previous:
                return (
                    "❌ Image editing is not configured "
                    f"for `{workflow_name}` yet.\n\n"
                    f"Model: `{model_name}`\n\n"
                    "This model currently has a generation "
                    "workflow but no edit workflow."
                )

            return (
                "❌ No generation workflow is configured "
                f"for `{workflow_name}`."
            )

        # ====================================================
        # VALIDATE DIMENSIONS
        # ====================================================

        try:
            width, height = (
                self._validate_dimensions(
                    width,
                    height,
                )
            )

        except ValueError as e:
            return (
                "❌ Width and height must be valid integers.\n\n"
                f"Error: `{type(e).__name__}: {e}`"
            )

        # ====================================================
        # VALIDATE STEPS
        # ====================================================

        try:
            steps = self._validate_steps(
                steps
            )

        except ValueError as e:
            return (
                "❌ Steps must be a valid integer.\n\n"
                f"Error: `{type(e).__name__}: {e}`"
            )

        # ====================================================
        # RESOLVE EDIT DENOISE
        # ====================================================

        effective_denoise = None

        if edit_previous:
            try:
                effective_denoise = (
                    self.workflow_manager
                    .resolve_edit_denoise(
                        denoise
                    )
                )

            except ValueError as e:
                return (
                    "❌ Invalid edit denoise value.\n\n"
                    f"{e}"
                )

        # ====================================================
        # VALIDATE USER
        # ====================================================

        if (
            not __user__
            or not __user__.get("id")
        ):
            return (
                "❌ Open WebUI did not provide "
                "a user ID."
            )

        user_id = str(
            __user__["id"]
        )

        # ====================================================
        # GET PREVIOUS IMAGE
        # ====================================================

        image_base64 = None
        image_diagnostics = None

        if edit_previous:

            if not __chat_id__:
                return (
                    "❌ Image editing was requested, "
                    "but Open WebUI did not provide "
                    "`__chat_id__`."
                )

            await self._status(
                __event_emitter__,
                "Preparing image edit...",
            )

            try:

                (
                    image_base64,
                    image_diagnostics,
                ) = await (
                    self.openwebui
                    .get_previous_image_base64(
                        __chat_id__,
                        __message_id__,
                        lambda description: (
                            self._status(
                                __event_emitter__,
                                description,
                            )
                        ),
                    )
                )

            except Exception as e:

                return (
                    "❌ Could not retrieve the previous "
                    "image for editing.\n\n"
                    f"Chat ID: `{__chat_id__}`\n"
                    f"Current message ID: "
                    f"`{__message_id__}`\n\n"
                    "Error:\n"
                    f"`{type(e).__name__}: {e}`\n\n"
                    "Use `diagnose_previous_image` "
                    "for additional diagnostics."
                )

        # ====================================================
        # PREPARE WORKFLOW
        # ====================================================

        mode_name = (
            "editing"
            if edit_previous
            else "generating"
        )

        await self._status(
            __event_emitter__,
            (
                f"Loading {workflow_name} "
                f"{mode_name} workflow..."
            ),
        )

        try:

            (
                workflow,
                actual_seed,
            ) = self.workflow_manager.prepare(
                workflow_filename,
                prompt,
                width,
                height,
                steps,
                seed,
                edit_previous=edit_previous,
                image_base64=image_base64,
                denoise=effective_denoise,
            )

        except Exception as e:

            return (
                "❌ Could not load or prepare "
                "the ComfyUI workflow.\n\n"
                f"Mode: `{mode_name}`\n"
                f"Workflow: `{workflow_filename}`\n"
                f"Directory: `{WORKFLOW_DIR}`\n\n"
                "Error:\n"
                f"`{type(e).__name__}: {e}`"
            )

        # ====================================================
        # COMFYUI
        # ====================================================

        async with (
            self.comfyui.create_http_client()
            as client
        ):

            try:

                await self.comfyui.check_connection(
                    client
                )

            except Exception as e:

                return (
                    "❌ Could not connect to ComfyUI.\n\n"
                    f"`{self.comfyui.base_url}`\n\n"
                    "Error:\n"
                    f"`{type(e).__name__}: {e}`"
                )

            await self._status(
                __event_emitter__,
                (
                    f"Sending {mode_name} "
                    "workflow to ComfyUI..."
                ),
            )

            try:

                prompt_id = (
                    await self.comfyui.queue_prompt(
                        client,
                        workflow,
                    )
                )

            except Exception as e:

                return (
                    "❌ ComfyUI rejected the workflow.\n\n"
                    f"Mode: `{mode_name}`\n"
                    f"Workflow: `{workflow_name}`\n"
                    "Error:\n"
                    f"`{type(e).__name__}: {e}`"
                )

            await self._status(
                __event_emitter__,
                (
                    f"{'Editing' if edit_previous else 'Generating'} "
                    f"with {workflow_name}..."
                ),
            )

            try:

                history = (
                    await self.comfyui.wait_for_result(
                        client,
                        prompt_id,
                        lambda description: (
                            self._status(
                                __event_emitter__,
                                description,
                            )
                        ),
                    )
                )

            except Exception as e:

                return (
                    "❌ ComfyUI failed.\n\n"
                    f"Mode: `{mode_name}`\n"
                    f"Workflow: `{workflow_name}`\n"
                    f"Prompt ID: `{prompt_id}`\n"
                    "Error:\n"
                    f"`{type(e).__name__}: {e}`"
                )

            # ====================================================
            # EXTRACT IMAGES
            # ====================================================

            images = self.comfyui.extract_images(
                history
            )

            if not images:

                await self._status(
                    __event_emitter__,
                    "ComfyUI finished but returned no images.",
                    done=True,
                )

                return (
                    "❌ ComfyUI completed the workflow, "
                    "but no image output was found.\n\n"
                    f"Prompt ID: `{prompt_id}`\n"
                    f"Workflow: `{workflow_name}`"
                )

            # ====================================================
            # IMPORT IMAGES
            # ====================================================

            attached_files = []

            for image in images:

                filename = image["filename"]

                await self._status(
                    __event_emitter__,
                    (
                        f"Importing {filename} "
                        "into Open WebUI..."
                    ),
                )

                try:

                    image_bytes = (
                        await self.comfyui.download_image(
                            client,
                            image,
                        )
                    )

                    if not image_bytes:
                        raise RuntimeError(
                            "ComfyUI returned an empty image."
                        )

                    file_item = (
                        await self.openwebui.create_file(
                            image_bytes,
                            filename,
                            user_id,
                        )
                    )

                    file_id = str(
                        file_item.id
                    )

                    file_url = (
                        f"/api/v1/files/"
                        f"{file_id}/content"
                    )

                    attached_files.append(
                        {
                            "id": file_id,
                            "type": "image",
                            "name": filename,
                            "url": file_url,
                            "collection_name": "local",
                        }
                    )

                except Exception as e:

                    await self._status(
                        __event_emitter__,
                        (
                            f"Failed to import "
                            f"{filename}: {e}"
                        ),
                        done=False,
                    )

                    return (
                        "❌ The image was generated by "
                        "ComfyUI, but Open WebUI could "
                        "not store the generated image.\n\n"
                        f"Filename: `{filename}`\n"
                        "Error:\n"
                        f"`{type(e).__name__}: {e}`"
                    )

            # ====================================================
            # ATTACH
            # ====================================================

            if (
                __chat_id__
                and __message_id__
                and attached_files
            ):

                try:

                    await (
                        self.openwebui
                        .attach_files_to_message(
                            __chat_id__,
                            __message_id__,
                            attached_files,
                        )
                    )

                except Exception as e:

                    return (
                        "❌ The image was stored in "
                        "Open WebUI, but could not be "
                        "attached to the current message.\n\n"
                        "Error:\n"
                        f"`{type(e).__name__}: {e}`"
                    )

            # ====================================================
            # EMIT FILE EVENT
            # ====================================================

            if (
                __event_emitter__
                and attached_files
            ):

                await __event_emitter__(
                    {
                        "type": "chat:message:files",
                        "data": {
                            "files": attached_files,
                        },
                    }
                )

            # ====================================================
            # COMPLETE
            # ====================================================

            await self._status(
                __event_emitter__,
                (
                    "Finished "
                    f"{'editing' if edit_previous else 'generating'} "
                    f"with {workflow_name}."
                ),
                done=True,
            )

            # ====================================================
            # RESULT
            # ====================================================

            if edit_previous:

                source_info = ""

                if image_diagnostics:

                    source_info = (
                        "\n\n"
                        "Edited source:\n"
                        "- File: "
                        f"`{image_diagnostics['filename']}`\n"
                        "- File ID: "
                        f"`{image_diagnostics['file_id']}`\n"
                        "- Source message: "
                        f"`{image_diagnostics['source_message_id']}`\n"
                        "- Source size: "
                        f"`{image_diagnostics['byte_count']} bytes`\n"
                    )

                return (
                    "Image edited successfully with "
                    f"{workflow_name}.\n\n"
                    f"Seed: `{actual_seed}`\n"
                    f"Denoise: "
                    f"`{effective_denoise:.2f}`"
                    f"{source_info}"
                )

            return (
                "Image generated successfully with "
                f"{workflow_name}.\n\n"
                f"Seed: `{actual_seed}`"
            )
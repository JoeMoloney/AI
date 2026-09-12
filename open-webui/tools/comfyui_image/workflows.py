import copy
import json
import math
import os
import random

from .config import (
DEFAULT_EDIT_DENOISE,
SINGLE_EDIT_IMAGE_NODE,
MULTI_EDIT_PRIMARY_IMAGE_NODE,
MULTI_EDIT_REFERENCE_IMAGE_NODE,
FLUX_LATENT_NODE,
FLUX_SAMPLER_NODE,
MAX_EDIT_DENOISE,
MIN_EDIT_DENOISE,
OTHER_LATENT_NODE,
OTHER_SAMPLER_NODE,
WORKFLOW_DIR,
)

print("[COMFYUI_IMAGE] workflows.py loaded", flush=True)

class WorkflowManager:

    def __init__(self, workflow_dir=WORKFLOW_DIR):
        print(
            "[COMFYUI_IMAGE] WorkflowManager.__init__ "
            f"workflow_dir={workflow_dir!r}",
            flush=True,
        )
        self.workflow_dir = workflow_dir
    # ============================================================
    # WORKFLOW DIRECTORY DIAGNOSTICS
    # ============================================================

    def test_directory(self):
        try:
            configured_directory = self.workflow_dir
            resolved_directory = os.path.realpath(
                configured_directory
            )

            if not os.path.isdir(resolved_directory):
                return (
                    "❌ WORKFLOW DIRECTORY DOES NOT EXIST\n\n"
                    f"Configured path:\n`{configured_directory}`\n\n"
                    f"Resolved path:\n`{resolved_directory}`\n\n"
                    f"Current working directory:\n`{os.getcwd()}`\n\n"
                    "Directory exists: `False`\n"
                    f"Directory readable: "
                    f"`{os.access(resolved_directory, os.R_OK)}`"
                )

            try:
                directory_contents = sorted(
                    os.listdir(resolved_directory)
                )

            except Exception as e:
                return (
                    "❌ WORKFLOW DIRECTORY EXISTS "
                    "BUT COULD NOT BE LISTED\n\n"
                    f"Path:\n`{resolved_directory}`\n\n"
                    f"Error:\n`{type(e).__name__}: {e}`"
                )

            workflow_files = [
                filename
                for filename in directory_contents
                if filename.lower().endswith(".json")
            ]

            return (
                "✅ WORKFLOW DIRECTORY IS ACCESSIBLE\n\n"
                f"Configured path:\n`{configured_directory}`\n\n"
                f"Resolved path:\n`{resolved_directory}`\n\n"
                f"Current working directory:\n`{os.getcwd()}`\n\n"
                "Directory exists: `True`\n"
                f"Directory readable: "
                f"`{os.access(resolved_directory, os.R_OK)}`\n\n"
                "All directory contents:\n"
                + (
                    "\n".join(
                        f"- `{filename}`"
                        for filename in directory_contents
                    )
                    if directory_contents
                    else "- *(directory is empty)*"
                )
                + "\n\n"
                "JSON workflow files:\n"
                + (
                    "\n".join(
                        f"- `{filename}`"
                        for filename in workflow_files
                    )
                    if workflow_files
                    else "- *(no JSON workflow files found)*"
                )
            )

        except Exception as e:
            return (
                "❌ FAILED TO TEST WORKFLOW DIRECTORY\n\n"
                f"Configured path:\n`{self.workflow_dir}`\n\n"
                f"Current working directory:\n`{os.getcwd()}`\n\n"
                f"Error:\n`{type(e).__name__}: {e}`"
            )

    # ============================================================
    # LOAD WORKFLOW
    # ============================================================

    def load(self, workflow_filename):
        print(
            "[COMFYUI_IMAGE] WorkflowManager.load() "
            f"filename={workflow_filename!r}",
            flush=True,
        )
        if not workflow_filename:
            raise ValueError(
                "No workflow filename was specified."
            )

        filename = os.path.basename(workflow_filename)

        if filename != workflow_filename:
            raise ValueError(
                "Invalid workflow filename. "
                "Only filenames are permitted.\n"
                f"Received: `{workflow_filename}`"
            )

        resolved_directory = os.path.realpath(
            self.workflow_dir
        )

        workflow_path = os.path.realpath(
            os.path.join(
                resolved_directory,
                filename,
            )
        )

        print(
            "[COMFYUI_IMAGE] WORKFLOW PATH: "
            f"{workflow_path!r}",
            flush=True,
        )

        if not workflow_path.startswith(
            resolved_directory + os.sep
        ):
            raise ValueError(
                "Workflow path is outside the configured "
                "workflow directory.\n\n"
                f"Workflow path: `{workflow_path}`\n"
                f"Workflow directory: "
                f"`{resolved_directory}`"
            )

        diagnostic = (
            "\n\n"
            "Workflow diagnostics:\n"
            f"- Configured directory: "
            f"`{self.workflow_dir}`\n"
            f"- Resolved directory: "
            f"`{resolved_directory}`\n"
            f"- Workflow filename: `{filename}`\n"
            f"- Resolved workflow path: "
            f"`{workflow_path}`\n"
            f"- Current working directory: "
            f"`{os.getcwd()}`\n"
            f"- Directory exists: "
            f"`{os.path.isdir(resolved_directory)}`\n"
            f"- Directory readable: "
            f"`{os.access(resolved_directory, os.R_OK)}`\n"
            f"- File exists: "
            f"`{os.path.isfile(workflow_path)}`\n"
            f"- File readable: "
            f"`{os.access(workflow_path, os.R_OK)}`"
        )

        if not os.path.isdir(resolved_directory):
            raise FileNotFoundError(
                "The configured workflow directory "
                "does not exist."
                + diagnostic
            )

        if not os.path.isfile(workflow_path):

            try:
                directory_contents = sorted(
                    os.listdir(resolved_directory)
                )

            except Exception as e:
                directory_contents = [
                    f"<Could not list directory: {e}>"
                ]

            raise FileNotFoundError(
                "The requested workflow file could not be found."
                + diagnostic
                + "\n"
                f"- Directory contents: "
                f"`{directory_contents}`"
            )

        try:

            with open(
                workflow_path,
                "r",
                encoding="utf-8",
            ) as file:
                workflow = json.load(file)

        except json.JSONDecodeError as e:
            raise ValueError(
                "The workflow file contains invalid JSON."
                + diagnostic
                + f"\n- JSON error: `{e}`"
            ) from e

        except PermissionError as e:
            raise PermissionError(
                "Permission denied while reading "
                "the workflow file."
                + diagnostic
                + f"\n- Permission error: `{e}`"
            ) from e

        except Exception as e:
            raise RuntimeError(
                "Could not read the workflow file."
                + diagnostic
                + f"\n- Read error: "
                f"`{type(e).__name__}: {e}`"
            ) from e

        if not isinstance(workflow, dict):
            raise ValueError(
                "The workflow JSON must contain a JSON "
                "object at its root."
                + diagnostic
            )

        if not workflow:
            raise ValueError(
                "The workflow JSON is empty."
                + diagnostic
            )

        print(
            "[COMFYUI_IMAGE] WORKFLOW LOADED: "
            f"{workflow_filename!r} "
            f"nodes={len(workflow)}",
            flush=True,
        )
        return workflow

    # ============================================================
    # EDIT DENOISE
    # ============================================================

    def resolve_edit_denoise(self, denoise):
        if denoise is None:
            value = DEFAULT_EDIT_DENOISE

        else:
            try:
                value = float(denoise)

            except (TypeError, ValueError) as e:
                raise ValueError(
                    f"Invalid edit denoise value: `{denoise}`."
                ) from e

        if not math.isfinite(value):
            raise ValueError(
                "Edit denoise must be a finite number. "
                f"Received: `{value}`."
            )

        return max(
            MIN_EDIT_DENOISE,
            min(
                value,
                MAX_EDIT_DENOISE,
            ),
        )

    # ============================================================
    # PREPARE WORKFLOW
    # ============================================================

    def prepare(
        self,
        workflow_filename,
        prompt,
        width,
        height,
        steps,
        seed,
        edit_previous=False,
        image_base64=None,
        reference_image_base64=None,
        denoise=None,
        negative_prompt=None,
    ):
        print(
            "[COMFYUI_IMAGE] WorkflowManager.prepare() START "
            f"workflow={workflow_filename!r} "
            f"edit_previous={edit_previous} "
            f"reference_edit={bool(reference_image_base64)} "
            f"width={width} height={height} steps={steps} seed={seed}",
            flush=True,
        )

        workflow = copy.deepcopy(self.load(workflow_filename))

        # Replace prompt placeholders wherever they occur.
        for node in workflow.values():
            if not isinstance(node, dict):
                continue
            inputs = node.get("inputs")
            if not isinstance(inputs, dict):
                continue
            for key, value in list(inputs.items()):
                if value in ("%prompt%", "%positive_prompt%"):
                    inputs[key] = prompt
                elif value == "%negative_prompt%":
                    inputs[key] = negative_prompt or ""

        # --------------------------------------------------------
        # Image inputs for edit workflows
        # --------------------------------------------------------
        if edit_previous:
            if not image_base64:
                raise ValueError(
                    "Editing was requested, but no image base64 data "
                    "was supplied."
                )

            if reference_image_base64:
                expected_nodes = {
                    MULTI_EDIT_PRIMARY_IMAGE_NODE: image_base64,
                    MULTI_EDIT_REFERENCE_IMAGE_NODE: reference_image_base64,
                }
                expected_count = 2
            else:
                expected_nodes = {
                    SINGLE_EDIT_IMAGE_NODE: image_base64,
                }
                expected_count = 1

            actual_image_nodes = [
                node_id
                for node_id, node in workflow.items()
                if isinstance(node, dict)
                and node.get("class_type") == "LoadImageFromBase64"
            ]

            if len(actual_image_nodes) != expected_count:
                raise ValueError(
                    f"Workflow `{workflow_filename}` contains "
                    f"{len(actual_image_nodes)} LoadImageFromBase64 node(s), "
                    f"but this edit mode expects {expected_count}."
                )

            for node_id, encoded_image in expected_nodes.items():
                node = workflow.get(node_id)
                if not isinstance(node, dict):
                    raise ValueError(
                        f"Workflow `{workflow_filename}` does not contain "
                        f"the expected image node `{node_id}`."
                    )

                if node.get("class_type") != "LoadImageFromBase64":
                    raise ValueError(
                        f"Workflow node `{node_id}` is not a "
                        "LoadImageFromBase64 node."
                    )

                inputs = node.get("inputs")
                if not isinstance(inputs, dict):
                    inputs = {}
                    node["inputs"] = inputs

                inputs["data"] = encoded_image

                role = (
                    "primary"
                    if node_id == MULTI_EDIT_PRIMARY_IMAGE_NODE
                    else (
                        "reference"
                        if node_id == MULTI_EDIT_REFERENCE_IMAGE_NODE
                        else "single-edit"
                    )
                )
                print(
                    "[COMFYUI_IMAGE] Injected "
                    f"{role} image into LoadImageFromBase64 node {node_id}",
                    flush=True,
                )

        # --------------------------------------------------------
        # Generation dimensions
        # --------------------------------------------------------
        else:
            width_set = False
            height_set = False

            # The supplied Flux.2 generation workflow exposes Width and
            # Height as PrimitiveInt nodes. Prefer those when present.
            for node in workflow.values():
                if not isinstance(node, dict):
                    continue
                inputs = node.get("inputs")
                meta = node.get("_meta", {})
                if not isinstance(inputs, dict):
                    continue

                title = str(meta.get("title", "")).strip().lower()
                if node.get("class_type") == "PrimitiveInt":
                    if title == "width":
                        inputs["value"] = int(width)
                        width_set = True
                    elif title == "height":
                        inputs["value"] = int(height)
                        height_set = True

            # Fallback for workflows with scalar width/height directly on
            # a latent node. Do not overwrite linked list inputs.
            if not (width_set and height_set):
                for node in workflow.values():
                    if not isinstance(node, dict):
                        continue
                    class_type = str(node.get("class_type", ""))
                    inputs = node.get("inputs")
                    if "Latent" not in class_type or not isinstance(inputs, dict):
                        continue
                    if not width_set and not isinstance(inputs.get("width"), list):
                        inputs["width"] = int(width)
                        width_set = True
                    if not height_set and not isinstance(inputs.get("height"), list):
                        inputs["height"] = int(height)
                        height_set = True

        # --------------------------------------------------------
        # Resolve seed
        # --------------------------------------------------------
        if seed is None:
            seed = random.randint(0, 2**63 - 1)
        else:
            try:
                seed = int(seed)
            except (TypeError, ValueError) as e:
                raise ValueError(f"Invalid seed value: `{seed}`.") from e
            if seed < 0:
                seed = random.randint(0, 2**63 - 1)

        # --------------------------------------------------------
        # Steps / seed / denoise controls
        # --------------------------------------------------------
        resolved_denoise = (
            self.resolve_edit_denoise(denoise)
            if edit_previous
            else None
        )

        for node in workflow.values():
            if not isinstance(node, dict):
                continue
            inputs = node.get("inputs")
            if not isinstance(inputs, dict):
                continue

            class_type = node.get("class_type", "")

            # Flux.2 advanced workflows keep steps in Flux2Scheduler.
            if steps is not None and (
                class_type == "Flux2Scheduler"
                or "steps" in inputs
            ):
                inputs["steps"] = int(steps)

            # SamplerCustomAdvanced takes a RandomNoise node, so its seed
            # belongs in RandomNoise.noise_seed. Standard KSampler uses seed.
            if class_type == "RandomNoise" and "noise_seed" in inputs:
                inputs["noise_seed"] = int(seed)
            elif "seed" in inputs:
                inputs["seed"] = int(seed)

            # Only set denoise on workflows/nodes that actually expose it.
            if (
                edit_previous
                and resolved_denoise is not None
                and "denoise" in inputs
            ):
                inputs["denoise"] = resolved_denoise

        print(
            "[COMFYUI_IMAGE] WorkflowManager.prepare() COMPLETE "
            f"seed={seed}",
            flush=True,
        )
        return workflow, seed

    def _find_nodes_by_class_type(self, workflow):
        """Find latent and sampler nodes by their class types instead of hardcoded IDs."""

        # Define expected node classes for different workflow types
        # For Chroma/standard workflows that don't use Flux pattern
        latent_classes = [
            "EmptySD3LatentImage",
            "EmptyChromaRadianceLatentImage",
            "EmptyLatentImage"
        ]

        sampler_classes = [
            "SamplerCustomAdvanced",
            "KSampler",
            "Sampler"
        ]

        found_latent_node = None
        found_sampler_node = None

        # Look through all nodes to find the right types
        for node_id, node_data in workflow.items():
            if not isinstance(node_data, dict):
                continue

            class_type = node_data.get("class_type")

            if class_type in latent_classes and not found_latent_node:
                found_latent_node = node_id
            elif class_type in sampler_classes and not found_sampler_node:
                found_sampler_node = node_id

            # If we found both, break early
            if found_latent_node and found_sampler_node:
                break

        if not found_latent_node:
            raise ValueError("Could not find latent node by class type in workflow")

        if not found_sampler_node:
            raise ValueError("Could not find sampler node by class type in workflow")

        return found_latent_node, found_sampler_node

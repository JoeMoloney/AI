import copy
import json
import math
import os
import random

from .config import (
DEFAULT_EDIT_DENOISE,
EDIT_IMAGE_NODE,
EDIT_SAMPLER_NODE,
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
        denoise=None,
    ):
        print(
            "[COMFYUI_IMAGE] WorkflowManager.prepare() START "
            f"workflow={workflow_filename!r} "
            f"edit_previous={edit_previous} "
            f"width={width} "
            f"height={height} "
            f"steps={steps} "
            f"seed={seed}",
            flush=True,
        )
        workflow = copy.deepcopy(
            self.load(workflow_filename)
        )
        print(
            "[COMFYUI_IMAGE] WorkflowManager.prepare() "
            "workflow loaded successfully",
            flush=True,
        )
        # --------------------------------------------------------
        # Replace %prompt%
        # --------------------------------------------------------

        for node in workflow.values():

            if not isinstance(node, dict):
                continue

            inputs = node.get("inputs")

            if not isinstance(inputs, dict):
                continue

            for key, value in list(inputs.items()):

                if value == "%prompt%":
                    inputs[key] = prompt

        # --------------------------------------------------------
        # Select nodes
        # --------------------------------------------------------

        latent_node = None

        if edit_previous:

            sampler_node = EDIT_SAMPLER_NODE

        elif workflow_filename.startswith("flux_"):

            latent_node = FLUX_LATENT_NODE
            sampler_node = FLUX_SAMPLER_NODE
            
        else:
            # For non-flux workflows, try to find existing nodes first
            # If they have the expected IDs, use those (existing behavior)
            if OTHER_LATENT_NODE in workflow:
                latent_node = OTHER_LATENT_NODE
                sampler_node = OTHER_SAMPLER_NODE
            else:
                # For other workflow formats like yours which use different node IDs,
                # try to detect nodes by class type but be more precise about node selection
                # This allows workflows like Chroma/Radiance to work without modification
                
                # Check if we can find latent and sampler nodes
                latent_nodes = []
                sampler_nodes = []
                
                for node_id, node_data in workflow.items():
                    if not isinstance(node_data, dict):
                        continue
                        
                    class_type = node_data.get("class_type", "")
                    
                    # Look for nodes that are likely to be the latent/sampler
                    # This handles different formats without requiring node ID changes
                    if "latent" in class_type.lower() and "image" in class_type.lower():
                        latent_nodes.append(node_id)
                    elif class_type in ("SamplerCustomAdvanced", "KSampler", "KSamplerSelect"):
                        sampler_nodes.append(node_id)
                
                # Use first found matching nodes (most common case)
                if latent_nodes:
                    latent_node = latent_nodes[0]
                else:
                    # If no node found, use fallback
                    latent_node = OTHER_LATENT_NODE
                    
                if sampler_nodes:
                    sampler_node = sampler_nodes[0]
                else:
                    # If no node found, use fallback  
                    sampler_node = OTHER_SAMPLER_NODE

        # --------------------------------------------------------
        # Editing
        # --------------------------------------------------------

        if edit_previous:

            if not image_base64:
                raise ValueError(
                    "Editing was requested, but no previous "
                    "image base64 data was supplied."
                )

            if EDIT_IMAGE_NODE not in workflow:
                raise ValueError(
                    "Edit workflow does not contain image "
                    "input node "
                    f"`{EDIT_IMAGE_NODE}`."
                )

            image_node = workflow[EDIT_IMAGE_NODE]

            if not isinstance(image_node, dict):
                raise ValueError(
                    f"Edit image node `{EDIT_IMAGE_NODE}` "
                    "is not a valid workflow node."
                )

            image_inputs = image_node.get("inputs")

            if not isinstance(image_inputs, dict):
                raise ValueError(
                    "Image input node "
                    f"`{EDIT_IMAGE_NODE}` does not contain "
                    "valid inputs."
                )

            image_inputs["data"] = image_base64

        # --------------------------------------------------------
        # Generation dimensions
        # --------------------------------------------------------

        else:

            if not latent_node:
                raise ValueError(
                    "No latent node was selected."
                )

            # Check that the node actually exists in workflow (needed because we may have detected it dynamically)
            # Allow dynamic nodes to pass through, but ensure they exist in the workflow at least
            if latent_node not in workflow and not (OTHER_LATENT_NODE in workflow): 
                raise ValueError(
                    f"Workflow `{workflow_filename}` "
                    "does not contain expected latent node "
                    f"`{latent_node}`."
                )

            latent_node_data = workflow[latent_node]

            if not isinstance(
                latent_node_data,
                dict,
            ):
                raise ValueError(
                    f"Workflow latent node `{latent_node}` "
                    "is invalid."
                )

            latent_inputs = latent_node_data.get(
                "inputs"
            )

            if not isinstance(
                latent_inputs,
                dict,
            ):
                raise ValueError(
                    f"Workflow `{workflow_filename}` latent "
                    f"node `{latent_node}` does not contain "
                    "valid inputs."
                )

            latent_inputs["width"] = int(width)
            latent_inputs["height"] = int(height)

        # --------------------------------------------------------
        # Validate sampler
        # --------------------------------------------------------

        if sampler_node not in workflow:
            # If flexible detection was used and the node was found, 
            # just try to use the node ID directly - might be a valid case
            pass  # Let it proceed since we'll validate with inputs

        sampler_node_data = workflow.get(sampler_node)

        if not isinstance(
            sampler_node_data,
            dict,
        ):
            raise ValueError(
                f"Sampler node `{sampler_node}` is invalid."
            )

        sampler_inputs = sampler_node_data.get(
            "inputs"
        )

        if not isinstance(
            sampler_inputs,
            dict,
        ):
            raise ValueError(
                f"Workflow `{workflow_filename}` sampler "
                f"node `{sampler_node}` does not contain "
                "valid inputs."
            )

        # --------------------------------------------------------
        # Steps
        # --------------------------------------------------------

        if steps is not None:
            sampler_inputs["steps"] = int(steps)

        # --------------------------------------------------------
        # Seed
        # --------------------------------------------------------

        if seed is None:

            seed = random.randint(
                0,
                2**63 - 1,
            )

        else:

            try:
                seed = int(seed)

            except (TypeError, ValueError) as e:
                raise ValueError(
                    f"Invalid seed value: `{seed}`."
                ) from e

            if seed < 0:
                seed = random.randint(
                    0,
                    2**63 - 1,
                )

        sampler_inputs["seed"] = int(seed)

        # --------------------------------------------------------
        # Edit denoise
        # --------------------------------------------------------

        if edit_previous:

            resolved_denoise = self.resolve_edit_denoise(
                denoise
            )

            sampler_inputs["denoise"] = resolved_denoise

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
"""
title: ComfyUI Model-Routed Image Generator
author: Custom
description: Routes Open WebUI models to ComfyUI workflows loaded from external JSON files and returns generated images as native Open WebUI attachments.
version: 4.1.0
"""

import asyncio
import copy
import hashlib
import io
import json
import os
import random
import time
import uuid
from typing import Optional

import httpx


class Tools:

    # ================================================================
    # CONFIGURATION
    # ================================================================

    # Docker-to-Docker address for ComfyUI.
    COMFYUI_URL = "http://comfyui:8188"

    # Directory containing ComfyUI API-format workflow JSON files.
    #
    # IMPORTANT:
    # This directory must exist INSIDE the Open WebUI container.
    #
    # Example Docker Compose:
    #
    #   volumes:
    #     - ./comfyui_workflows:/comfyui_workflows
    #
    WORKFLOW_DIR = "/comfyui_workflows"

    POLL_INTERVAL = 1.0
    TIMEOUT_SECONDS = 1800

    # ================================================================
    # MODEL -> WORKFLOW ROUTING
    # ================================================================

    MODEL_WORKFLOWS = {
        "MODEL_1_NAME": {
            "name": "Flux Dev",
            "file": "comfyui_workflow_1.json",
        },
        "MODEL_2_NAME": {
            "name": "Flux Dev UNC",
            "file": "comfyui_workflow_2.json",
        },
        "MODEL_3_NAME": {
            "name": "NetaYume Lumina T2I UNC",
            "file": "comfyui_workflow_3.json",
        },
    }

    # ================================================================
    # MODEL DETECTION
    # ================================================================

    def _get_model_name(self, __model__):

        if not __model__:
            return None

        candidates = [
            __model__.get("id"),
            __model__.get("name"),
            __model__.get("model"),
        ]

        for candidate in candidates:

            if candidate in self.MODEL_WORKFLOWS:
                return candidate

        return None

    # ================================================================
    # DIAGNOSTIC TOOL
    # ================================================================

    def test_workflow_directory(self) -> str:
        """
        Test whether Open WebUI can access the external ComfyUI
        workflow directory and list the available workflow files.

        Use this tool when troubleshooting workflow loading problems.
        """

        try:

            configured_directory = self.WORKFLOW_DIR

            resolved_directory = os.path.realpath(configured_directory)

            directory_exists = os.path.isdir(resolved_directory)

            if not directory_exists:

                return (
                    "❌ WORKFLOW DIRECTORY DOES NOT EXIST\n\n"
                    f"Configured path:\n"
                    f"`{configured_directory}`\n\n"
                    f"Resolved path:\n"
                    f"`{resolved_directory}`\n\n"
                    f"Current working directory:\n"
                    f"`{os.getcwd()}`\n\n"
                    f"Directory exists: `False`\n"
                    f"Directory is readable: "
                    f"`{os.access(resolved_directory, os.R_OK)}`"
                )

            try:

                directory_contents = sorted(os.listdir(resolved_directory))

            except Exception as e:

                return (
                    "❌ WORKFLOW DIRECTORY EXISTS "
                    "BUT COULD NOT BE LISTED\n\n"
                    f"Path:\n"
                    f"`{resolved_directory}`\n\n"
                    f"Error:\n"
                    f"`{e}`"
                )

            workflow_files = [
                filename
                for filename in directory_contents
                if filename.lower().endswith(".json")
            ]

            return (
                "✅ WORKFLOW DIRECTORY IS ACCESSIBLE\n\n"
                f"Configured path:\n"
                f"`{configured_directory}`\n\n"
                f"Resolved path:\n"
                f"`{resolved_directory}`\n\n"
                f"Current working directory:\n"
                f"`{os.getcwd()}`\n\n"
                f"Directory exists: `True`\n"
                f"Directory readable: "
                f"`{os.access(resolved_directory, os.R_OK)}`\n\n"
                f"All directory contents:\n"
                + (
                    "\n".join(f"- `{filename}`" for filename in directory_contents)
                    if directory_contents
                    else "- *(directory is empty)*"
                )
                + "\n\n"
                f"JSON workflow files:\n"
                + (
                    "\n".join(f"- `{filename}`" for filename in workflow_files)
                    if workflow_files
                    else "- *(no JSON workflow files found)*"
                )
            )

        except Exception as e:

            return (
                "❌ FAILED TO TEST WORKFLOW DIRECTORY\n\n"
                f"Configured path:\n"
                f"`{self.WORKFLOW_DIR}`\n\n"
                f"Current working directory:\n"
                f"`{os.getcwd()}`\n\n"
                f"Error:\n"
                f"`{type(e).__name__}: {e}`"
            )

    # ================================================================
    # LOAD WORKFLOW FROM FILE
    # ================================================================

    def _load_workflow(
        self,
        workflow_filename,
    ):
        """
        Load a ComfyUI API-format workflow JSON file from
        WORKFLOW_DIR.

        Includes detailed diagnostics to make filesystem problems
        visible when the tool is being executed from Open WebUI.
        """

        if not workflow_filename:

            raise ValueError("No workflow filename was specified.")

        # ------------------------------------------------------------
        # Only allow a filename.
        #
        # This prevents things such as:
        #
        # ../../some-other-file.json
        #
        # from being used as a workflow path.
        # ------------------------------------------------------------

        filename = os.path.basename(workflow_filename)

        if filename != workflow_filename:

            raise ValueError(
                "Invalid workflow filename. "
                "Only filenames are permitted.\n"
                f"Received: `{workflow_filename}`"
            )

        # ------------------------------------------------------------
        # Resolve directory and workflow path.
        # ------------------------------------------------------------

        configured_directory = self.WORKFLOW_DIR

        resolved_directory = os.path.realpath(configured_directory)

        workflow_path = os.path.realpath(
            os.path.join(
                resolved_directory,
                filename,
            )
        )

        # ------------------------------------------------------------
        # Prevent path traversal.
        # ------------------------------------------------------------

        if not workflow_path.startswith(resolved_directory + os.sep):

            raise ValueError(
                "Workflow path is outside the configured "
                "workflow directory.\n\n"
                f"Workflow path: `{workflow_path}`\n"
                f"Workflow directory: `{resolved_directory}`"
            )

        # ------------------------------------------------------------
        # Diagnostics.
        # ------------------------------------------------------------

        diagnostic = (
            "\n\n"
            "Workflow diagnostics:\n"
            f"- Configured directory: `{configured_directory}`\n"
            f"- Resolved directory: `{resolved_directory}`\n"
            f"- Workflow filename: `{filename}`\n"
            f"- Resolved workflow path: `{workflow_path}`\n"
            f"- Current working directory: `{os.getcwd()}`\n"
            f"- Directory exists: "
            f"`{os.path.isdir(resolved_directory)}`\n"
            f"- Directory readable: "
            f"`{os.access(resolved_directory, os.R_OK)}`\n"
            f"- File exists: "
            f"`{os.path.isfile(workflow_path)}`\n"
            f"- File readable: "
            f"`{os.access(workflow_path, os.R_OK)}`"
        )

        # ------------------------------------------------------------
        # Check directory.
        # ------------------------------------------------------------

        if not os.path.isdir(resolved_directory):

            raise FileNotFoundError(
                "The configured workflow directory " "does not exist." + diagnostic
            )

        # ------------------------------------------------------------
        # Check file.
        # ------------------------------------------------------------

        if not os.path.isfile(workflow_path):

            try:

                directory_contents = sorted(os.listdir(resolved_directory))

            except Exception as e:

                directory_contents = [f"<Could not list directory: {e}>"]

            raise FileNotFoundError(
                "The requested workflow file "
                "could not be found." + diagnostic + "\n"
                f"- Directory contents: "
                f"`{directory_contents}`"
            )

        # ------------------------------------------------------------
        # Read JSON.
        # ------------------------------------------------------------

        try:

            with open(
                workflow_path,
                "r",
                encoding="utf-8",
            ) as file:

                workflow = json.load(file)

        except json.JSONDecodeError as e:

            raise ValueError(
                "The workflow file contains invalid JSON." + diagnostic + "\n"
                f"- JSON error: `{e}`"
            ) from e

        except PermissionError as e:

            raise PermissionError(
                "Permission denied while reading "
                "the workflow file." + diagnostic + "\n"
                f"- Permission error: `{e}`"
            ) from e

        except Exception as e:

            raise RuntimeError(
                "Could not read the workflow file." + diagnostic + "\n"
                f"- Read error: "
                f"`{type(e).__name__}: {e}`"
            ) from e

        # ------------------------------------------------------------
        # Validate JSON structure.
        # ------------------------------------------------------------

        if not isinstance(
            workflow,
            dict,
        ):

            raise ValueError(
                "The workflow JSON must contain "
                "a JSON object at its root." + diagnostic
            )

        if not workflow:

            raise ValueError("The workflow JSON is empty." + diagnostic)

        return workflow

    # ================================================================
    # STATUS
    # ================================================================

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

    # ================================================================
    # WORKFLOW PREPARATION
    # ================================================================

    def _prepare_workflow(
        self,
        workflow_filename,
        prompt,
        width,
        height,
        steps,
        seed,
    ):
        """
        Load and prepare a workflow from the external workflow
        directory.

        The JSON files must be exported from ComfyUI in API format.
        """

        # ------------------------------------------------------------
        # Load workflow.
        # ------------------------------------------------------------

        workflow = self._load_workflow(workflow_filename)

        workflow = copy.deepcopy(workflow)

        # ------------------------------------------------------------
        # Replace %prompt%
        # ------------------------------------------------------------

        for node_id, node in workflow.items():

            if not isinstance(
                node,
                dict,
            ):
                continue

            inputs = node.get(
                "inputs",
                {},
            )

            if not isinstance(
                inputs,
                dict,
            ):
                continue

            for key, value in list(inputs.items()):

                if value == "%prompt%":

                    inputs[key] = prompt

        # ------------------------------------------------------------
        # Identify workflow nodes.
        #
        # This preserves your existing routing logic.
        # ------------------------------------------------------------

        if workflow_filename.startswith("flux_"):

            latent_node = "56:50"
            sampler_node = "56:52"

        else:

            latent_node = "48:31"
            sampler_node = "48:33"

        # ------------------------------------------------------------
        # Validate latent node.
        # ------------------------------------------------------------

        if latent_node not in workflow:

            raise ValueError(
                f"Workflow `{workflow_filename}` does not contain "
                f"the expected latent node `{latent_node}`."
            )

        # ------------------------------------------------------------
        # Validate sampler node.
        # ------------------------------------------------------------

        if sampler_node not in workflow:

            raise ValueError(
                f"Workflow `{workflow_filename}` does not contain "
                f"the expected sampler node `{sampler_node}`."
            )

        # ------------------------------------------------------------
        # Validate inputs.
        # ------------------------------------------------------------

        if not isinstance(
            workflow[latent_node].get("inputs"),
            dict,
        ):

            raise ValueError(
                f"Workflow `{workflow_filename}` latent node "
                f"`{latent_node}` does not contain valid inputs."
            )

        if not isinstance(
            workflow[sampler_node].get("inputs"),
            dict,
        ):

            raise ValueError(
                f"Workflow `{workflow_filename}` sampler node "
                f"`{sampler_node}` does not contain valid inputs."
            )

        # ------------------------------------------------------------
        # Dimensions.
        # ------------------------------------------------------------

        workflow[latent_node]["inputs"]["width"] = int(width)

        workflow[latent_node]["inputs"]["height"] = int(height)

        # ------------------------------------------------------------
        # Steps.
        # ------------------------------------------------------------

        if steps is not None:

            workflow[sampler_node]["inputs"]["steps"] = int(steps)

        # ------------------------------------------------------------
        # Seed.
        # ------------------------------------------------------------

        if seed is None or int(seed) < 0:

            seed = random.randint(
                0,
                2**63 - 1,
            )

        workflow[sampler_node]["inputs"]["seed"] = int(seed)

        return (
            workflow,
            seed,
        )

    # ================================================================
    # QUEUE COMFYUI
    # ================================================================

    async def _queue_prompt(
        self,
        client,
        workflow,
    ):

        response = await client.post(
            f"{self.COMFYUI_URL}/prompt",
            json={
                "prompt": workflow,
            },
        )

        response.raise_for_status()

        data = response.json()

        if "error" in data:

            raise RuntimeError(f"ComfyUI rejected workflow: {data}")

        prompt_id = data.get("prompt_id")

        if not prompt_id:

            raise RuntimeError("No prompt_id returned by ComfyUI: " f"{data}")

        return prompt_id

    # ================================================================
    # WAIT FOR COMFYUI
    # ================================================================

    async def _wait_for_result(
        self,
        client,
        prompt_id,
        emitter,
    ):

        elapsed = 0

        while elapsed < self.TIMEOUT_SECONDS:

            await asyncio.sleep(self.POLL_INTERVAL)

            elapsed += self.POLL_INTERVAL

            response = await client.get(f"{self.COMFYUI_URL}/history/" f"{prompt_id}")

            response.raise_for_status()

            history = response.json()

            if prompt_id not in history:

                await self._status(
                    emitter,
                    f"Generating... {int(elapsed)}s",
                )

                continue

            result = history[prompt_id]

            status = result.get(
                "status",
                {},
            )

            if status.get("status_str") == "error":

                raise RuntimeError(
                    "ComfyUI execution error: "
                    + str(
                        status.get(
                            "messages",
                            [],
                        )
                    )
                )

            if status.get("completed") is True:

                return result

        raise TimeoutError("ComfyUI generation timed out.")

    # ================================================================
    # EXTRACT IMAGES
    # ================================================================

    def _extract_images(
        self,
        history,
    ):

        images = []

        outputs = history.get(
            "outputs",
            {},
        )

        for (
            node_id,
            output,
        ) in outputs.items():

            for image in output.get(
                "images",
                [],
            ):

                if image.get("filename"):

                    images.append(
                        {
                            "filename": image["filename"],
                            "subfolder": image.get(
                                "subfolder",
                                "",
                            ),
                            "type": image.get(
                                "type",
                                "output",
                            ),
                            "node_id": node_id,
                        }
                    )

        return images

    # ================================================================
    # DOWNLOAD IMAGE FROM COMFYUI
    # ================================================================

    async def _download_image(
        self,
        client,
        image,
    ):

        params = {
            "filename": image["filename"],
            "type": image.get(
                "type",
                "output",
            ),
        }

        if image.get("subfolder"):

            params["subfolder"] = image["subfolder"]

        response = await client.get(
            f"{self.COMFYUI_URL}/view",
            params=params,
        )

        response.raise_for_status()

        return response.content

    # ================================================================
    # CREATE NATIVE OPEN WEBUI FILE
    # ================================================================

    async def _create_openwebui_file(
        self,
        image_bytes,
        filename,
        user_id,
    ):
        """
        Store the PNG using Open WebUI's native Storage provider
        and create the corresponding FileModel.
        """

        from open_webui.models.files import (
            FileForm,
            Files,
        )

        from open_webui.storage.provider import (
            Storage,
        )

        file_id = str(uuid.uuid4())

        safe_filename = filename

        storage_filename = f"{file_id}_{safe_filename}"

        tags = {
            "OpenWebUI-User-Id": str(user_id),
            "OpenWebUI-File-Id": str(file_id),
        }

        file_object = io.BytesIO(image_bytes)

        contents, file_path = await asyncio.to_thread(
            Storage.upload_file,
            file_object,
            storage_filename,
            tags,
        )

        file_hash = hashlib.sha256(contents).hexdigest()

        file_item = await Files.insert_new_file(
            user_id,
            FileForm(
                id=file_id,
                filename=safe_filename,
                path=file_path,
                data={},
                meta={
                    "name": safe_filename,
                    "content_type": "image/png",
                    "size": len(contents),
                    "file_hash": file_hash,
                    "data": {},
                },
            ),
        )

        if not file_item:

            raise RuntimeError("Open WebUI failed to create " "the File record.")

        return file_item

    # ================================================================
    # MAIN TOOL
    # ================================================================

    async def generate_image(
        self,
        prompt: str,
        width: int = 1024,
        height: int = 1024,
        steps: Optional[int] = None,
        seed: int = -1,
        __model__: Optional[dict] = None,
        __event_emitter__=None,
        __user__: Optional[dict] = None,
        __chat_id__: Optional[str] = None,
        __message_id__: Optional[str] = None,
    ) -> str:
        """
        Generate an image through the ComfyUI workflow assigned
        to the selected Open WebUI model.
        """

        # ============================================================
        # IDENTIFY MODEL
        # ============================================================

        model_name = self._get_model_name(__model__)

        if not model_name:

            return "❌ I couldn't determine the configured " "Open WebUI model."

        route = self.MODEL_WORKFLOWS[model_name]

        workflow_filename = route["file"]

        workflow_name = route["name"]

        # ============================================================
        # VALIDATE PARAMETERS
        # ============================================================

        width = max(
            64,
            min(
                int(width),
                4096,
            ),
        )

        height = max(
            64,
            min(
                int(height),
                4096,
            ),
        )

        if steps is not None:

            steps = max(
                1,
                min(
                    int(steps),
                    200,
                ),
            )

        if not __user__ or not __user__.get("id"):

            return "❌ Open WebUI did not provide " "a user ID."

        user_id = str(__user__["id"])

        # ============================================================
        # PREPARE WORKFLOW
        # ============================================================

        await self._status(
            __event_emitter__,
            f"Loading {workflow_name}...",
        )

        try:

            workflow, actual_seed = self._prepare_workflow(
                workflow_filename,
                prompt,
                width,
                height,
                steps,
                seed,
            )

        except Exception as e:

            return (
                "❌ Could not load or prepare "
                "the ComfyUI workflow.\n\n"
                f"Workflow: `{workflow_filename}`\n"
                f"Directory: `{self.WORKFLOW_DIR}`\n\n"
                f"Error:\n"
                f"`{type(e).__name__}: {e}`"
            )

        timeout = httpx.Timeout(
            connect=15,
            read=60,
            write=60,
            pool=15,
        )

        async with httpx.AsyncClient(timeout=timeout) as client:

            # ========================================================
            # TEST COMFYUI
            # ========================================================

            try:

                response = await client.get(f"{self.COMFYUI_URL}/system_stats")

                response.raise_for_status()

            except Exception as e:

                return (
                    "❌ Could not connect to ComfyUI.\n\n"
                    f"`{self.COMFYUI_URL}`\n\n"
                    f"Error: `{e}`"
                )

            # ========================================================
            # QUEUE
            # ========================================================

            await self._status(
                __event_emitter__,
                "Sending workflow to ComfyUI...",
            )

            try:

                prompt_id = await self._queue_prompt(
                    client,
                    workflow,
                )

            except Exception as e:

                return (
                    "❌ ComfyUI rejected the workflow.\n\n"
                    f"Workflow: `{workflow_name}`\n"
                    f"Error: `{e}`"
                )

            # ========================================================
            # WAIT
            # ========================================================

            await self._status(
                __event_emitter__,
                f"Generating with {workflow_name}...",
            )

            try:

                history = await self._wait_for_result(
                    client,
                    prompt_id,
                    __event_emitter__,
                )

            except Exception as e:

                return (
                    "❌ ComfyUI failed.\n\n"
                    f"Workflow: `{workflow_name}`\n"
                    f"Prompt ID: `{prompt_id}`\n"
                    f"Error: `{e}`"
                )

            # ========================================================
            # GET IMAGE OUTPUTS
            # ========================================================

            images = self._extract_images(history)

            if not images:

                await self._status(
                    __event_emitter__,
                    "ComfyUI finished but returned no images.",
                    done=True,
                )

                return (
                    "❌ ComfyUI completed the workflow, "
                    "but no image output was found."
                )

            # ========================================================
            # CREATE OPEN WEBUI FILES
            # ========================================================

            attached_files = []

            for image in images:

                filename = image["filename"]

                await self._status(
                    __event_emitter__,
                    f"Importing {filename} into Open WebUI...",
                )

                try:

                    image_bytes = await self._download_image(
                        client,
                        image,
                    )

                    file_item = await self._create_openwebui_file(
                        image_bytes,
                        filename,
                        user_id,
                    )

                    file_id = str(file_item.id)

                    file_url = f"/api/v1/files/" f"{file_id}/content"

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
                        f"Failed to import {filename}: {e}",
                        done=False,
                    )

                    return (
                        "❌ The image was generated by "
                        "ComfyUI, but Open WebUI could not "
                        "store the generated image.\n\n"
                        f"Filename: `{filename}`\n"
                        f"Error: `{e}`"
                    )

            # ========================================================
            # ATTACH FILES TO CHAT MESSAGE
            # ========================================================

            if __chat_id__ and __message_id__ and attached_files:

                from open_webui.models.chats import (
                    Chats,
                )

                try:

                    await Chats.add_message_files_by_id_and_message_id(
                        __chat_id__,
                        __message_id__,
                        attached_files,
                    )

                except Exception as e:

                    return (
                        "❌ The image was stored in "
                        "Open WebUI, but could not be attached "
                        "to the current message.\n\n"
                        f"Error: `{e}`"
                    )

            # ========================================================
            # EMIT IMAGE EVENT
            # ========================================================

            if __event_emitter__ and attached_files:

                await __event_emitter__(
                    {
                        "type": "chat:message:files",
                        "data": {
                            "files": attached_files,
                        },
                    }
                )

            # ========================================================
            # COMPLETE
            # ========================================================

            await self._status(
                __event_emitter__,
                f"Finished with {workflow_name}.",
                done=True,
            )

            return (
                f"Image generated successfully with "
                f"{workflow_name}.\n\n"
                f"Seed: `{actual_seed}`"
            )

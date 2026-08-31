import asyncio

import httpx

from .config import (
COMFYUI_URL,
POLL_INTERVAL,
TIMEOUT_SECONDS,
)

print("[COMFYUI_IMAGE] comfyui.py loaded", flush=True)

class ComfyUIClient:

    def __init__(
        self,
        base_url=COMFYUI_URL,
        poll_interval=POLL_INTERVAL,
        timeout_seconds=TIMEOUT_SECONDS,
    ):
        self.base_url = base_url.rstrip("/")
        self.poll_interval = poll_interval
        self.timeout_seconds = timeout_seconds
        print(
            "[COMFYUI_IMAGE] ComfyUIClient initialized "
            f"base_url={self.base_url!r}",
            flush=True,
        )

    # ============================================================
    # HTTP CLIENT
    # ============================================================

    def create_http_client(self):
        timeout = httpx.Timeout(
            connect=15,
            read=60,
            write=60,
            pool=15,
        )

        return httpx.AsyncClient(
            timeout=timeout
        )

    # ============================================================
    # CONNECTION TEST
    # ============================================================

    async def check_connection(
        self,
        client,
    ):
        print(
            "[COMFYUI_IMAGE] ComfyUI connection test: "
            f"GET {self.base_url}/system_stats",
            flush=True,
        )
        response = await client.get(
            f"{self.base_url}/system_stats"
        )

        response.raise_for_status()
        print(
            "[COMFYUI_IMAGE] ComfyUI connection OK",
            flush=True,
        )
    # ============================================================
    # QUEUE PROMPT
    # ============================================================

    async def queue_prompt(
        self,
        client,
        workflow,
    ):
        print(
            "[COMFYUI_IMAGE] QUEUEING WORKFLOW TO COMFYUI",
            flush=True,
        )
        response = await client.post(
            f"{self.base_url}/prompt",
            json={
                "prompt": workflow,
            },
        )

        response.raise_for_status()

        try:
            data = response.json()
            print(
                "[COMFYUI_IMAGE] COMFYUI /prompt RESPONSE: "
                f"{data!r}",
                flush=True,
            )

        except Exception as e:
            raise RuntimeError(
                "ComfyUI returned a response that was "
                "not valid JSON.\n"
                f"Response: `{response.text[:1000]}`"
            ) from e

        if "error" in data:
            raise RuntimeError(
                f"ComfyUI rejected workflow: {data}"
            )

        prompt_id = data.get("prompt_id")
        print(
            "[COMFYUI_IMAGE] COMFYUI PROMPT ID: "
            f"{prompt_id}",
            flush=True,
        )

        if not prompt_id:
            raise RuntimeError(
                "No prompt_id returned by ComfyUI: "
                f"{data}"
            )

        return prompt_id

    # ============================================================
    # WAIT FOR RESULT
    # ============================================================

    async def wait_for_result(
        self,
        client,
        prompt_id,
        status_callback=None,
    ):
        print(
            "[COMFYUI_IMAGE] WAITING FOR COMFYUI RESULT "
            f"prompt_id={prompt_id}",
            flush=True,
        )
        elapsed = 0.0

        while elapsed < self.timeout_seconds:

            await asyncio.sleep(
                self.poll_interval
            )

            elapsed += self.poll_interval

            response = await client.get(
                f"{self.base_url}/history/{prompt_id}"
            )

            response.raise_for_status()

            history = response.json()

            if prompt_id not in history:

                if status_callback:
                    await status_callback(
                        f"Generating... {int(elapsed)}s"
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
                print(
                    "[COMFYUI_IMAGE] COMFYUI GENERATION COMPLETE "
                    f"prompt_id={prompt_id}",
                    flush=True,
                )
                return result

        raise TimeoutError(
            "ComfyUI generation timed out."
        )

    # ============================================================
    # EXTRACT IMAGES
    # ============================================================

    def extract_images(
        self,
        history,
    ):
        images = []
        
        outputs = history.get(
            "outputs",
            {},
        )

        if not isinstance(
            outputs,
            dict,
        ):
            return images

        for node_id, output in outputs.items():

            if not isinstance(
                output,
                dict,
            ):
                continue

            node_images = output.get(
                "images",
                [],
            )

            if not isinstance(
                node_images,
                list,
            ):
                continue

            for image in node_images:

                if not isinstance(
                    image,
                    dict,
                ):
                    continue

                filename = image.get(
                    "filename"
                )

                if filename:
                    images.append(
                        {
                            "filename": filename,
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
        print(
            "[COMFYUI_IMAGE] EXTRACTED IMAGES: "
            f"{images!r}",
            flush=True,
        )
        return images

    # ============================================================
    # DOWNLOAD IMAGE
    # ============================================================

    async def download_image(
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
            f"{self.base_url}/view",
            params=params,
        )

        response.raise_for_status()

        return response.content
from typing import Optional

print("[COMFYUI_IMAGE] test_workflow_directory()", flush=True)

class Diagnostics:

    def __init__(
        self,
        workflow_manager,
        openwebui_manager,
    ):
        self.workflow_manager = workflow_manager
        self.openwebui_manager = openwebui_manager

    # ============================================================
    # WORKFLOW DIRECTORY
    # ============================================================

    def test_workflow_directory(self):
        return self.workflow_manager.test_directory()

    # ============================================================
    # PREVIOUS IMAGE
    # ============================================================

    async def diagnose_previous_image(
        self,
        chat_id: Optional[str] = None,
        message_id: Optional[str] = None,
    ):
        if not chat_id:

            return (
                "❌ IMAGE EDIT DIAGNOSTICS FAILED\n\n"
                "Open WebUI did not provide `__chat_id__`."
            )

        try:

            (
                _base64_data,
                diagnostics,
            ) = (
                await self.openwebui_manager
                .get_previous_image_base64(
                    chat_id,
                    message_id,
                )
            )

            return (
                "✅ PREVIOUS IMAGE FOUND AND READ\n\n"
                f"Chat ID:\n"
                f"`{diagnostics['chat_id']}`\n\n"
                f"Current message ID:\n"
                f"`{diagnostics['current_message_id']}`\n\n"
                f"Source message ID:\n"
                f"`{diagnostics['source_message_id']}`\n\n"
                f"File ID:\n"
                f"`{diagnostics['file_id']}`\n\n"
                f"Filename:\n"
                f"`{diagnostics['filename']}`\n\n"
                f"Content type:\n"
                f"`{diagnostics['content_type']}`\n\n"
                f"Image bytes:\n"
                f"`{diagnostics['byte_count']}`\n\n"
                f"Base64 characters:\n"
                f"`{diagnostics['base64_character_count']}`\n\n"
                "The image can be supplied to "
                "`LoadImageFromBase64`."
            )

        except Exception as e:

            return (
                "❌ PREVIOUS IMAGE DIAGNOSTICS FAILED\n\n"
                f"Chat ID:\n`{chat_id}`\n\n"
                f"Current message ID:\n"
                f"`{message_id}`\n\n"
                f"Error:\n"
                f"`{type(e).__name__}: {e}`"
            )
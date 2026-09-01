import asyncio
import base64
import hashlib
import io
import json
import os
import uuid

print("[COMFYUI_IMAGE] openwebui.py loaded", flush=True)

class OpenWebUIManager:

    # ============================================================
    # CHAT
    # ============================================================

    async def get_chat_data(
        self,
        chat_id,
    ):
        if not chat_id:
            return None

        from open_webui.models.chats import Chats

        method = getattr(
            Chats,
            "get_chat_by_id",
            None,
        )

        if method is None:
            raise RuntimeError(
                "Open WebUI's Chats model does not expose "
                "`get_chat_by_id`. The installed Open WebUI "
                "version may have changed its internal API."
            )

        try:

            result = method(chat_id)

            if asyncio.iscoroutine(result):
                result = await result

            return result

        except Exception as e:

            raise RuntimeError(
                "Open WebUI failed while retrieving "
                f"chat `{chat_id}`.\n"
                f"{type(e).__name__}: {e}"
            ) from e

    # ============================================================
    # MESSAGE MAP
    # ============================================================

    def extract_message_map(
        self,
        chat,
    ):
        if chat is None:
            return {}

        chat_data = getattr(
            chat,
            "chat",
            None,
        )

        if isinstance(
            chat_data,
            str,
        ):

            try:
                chat_data = json.loads(
                    chat_data
                )

            except Exception:
                return {}

        if not isinstance(
            chat_data,
            dict,
        ):
            return {}

        history = chat_data.get(
            "history",
            {},
        )

        if isinstance(
            history,
            dict,
        ):

            messages = history.get(
                "messages",
                {},
            )

            if isinstance(
                messages,
                dict,
            ):
                return messages

        messages = chat_data.get(
            "messages",
            {},
        )

        if isinstance(
            messages,
            dict,
        ):
            return messages

        return {}

    # ============================================================
    # FILE IDS
    # ============================================================

    def extract_file_ids_from_message(
        self,
        message,
    ):
        if not isinstance(
            message,
            dict,
        ):
            return []

        files = message.get(
            "files"
        )

        if not isinstance(
            files,
            list,
        ):
            return []

        file_ids = []

        for file_item in files:

            if isinstance(
                file_item,
                str,
            ):
                file_ids.append(
                    file_item
                )
                continue

            if not isinstance(
                file_item,
                dict,
            ):
                continue

            for candidate in (
                file_item.get("id"),
                file_item.get("file_id"),
                file_item.get("fileId"),
            ):

                if candidate:
                    file_ids.append(
                        str(candidate)
                    )
                    break

        return file_ids

    # ============================================================
    # FILE RECORD
    # ============================================================

    async def get_file_record(
        self,
        file_id,
    ):
        from open_webui.models.files import Files

        method = None

        for method_name in (
            "get_file_by_id",
            "get_file",
        ):

            possible = getattr(
                Files,
                method_name,
                None,
            )

            if possible:
                method = possible
                break

        if method is None:
            raise RuntimeError(
                "Could not find a supported Open WebUI "
                "Files lookup method. Tried: "
                "`get_file_by_id`, `get_file`."
            )

        try:

            result = method(file_id)

            if asyncio.iscoroutine(result):
                result = await result

            return result

        except Exception as e:

            raise RuntimeError(
                "Open WebUI failed while retrieving "
                f"file `{file_id}`.\n"
                f"{type(e).__name__}: {e}"
            ) from e

    # ============================================================
    # FILE METADATA
    # ============================================================

    def file_filename(
        self,
        file_item,
    ):
        filename = getattr(
            file_item,
            "filename",
            None,
        )

        if filename:
            return str(filename)

        for attribute in (
            "data",
            "meta",
        ):

            value = getattr(
                file_item,
                attribute,
                None,
            )

            if isinstance(
                value,
                dict,
            ):

                filename = value.get(
                    "name"
                )

                if filename:
                    return str(filename)

        return "previous_image.png"

    def file_content_type(
        self,
        file_item,
    ):
        for attribute in (
            "meta",
            "data",
        ):

            value = getattr(
                file_item,
                attribute,
                None,
            )

            if isinstance(
                value,
                dict,
            ):

                content_type = value.get(
                    "content_type"
                )

                if content_type:
                    return str(
                        content_type
                    )

        return "application/octet-stream"

    def file_path(
        self,
        file_item,
    ):
        path = getattr(
            file_item,
            "path",
            None,
        )

        if path:
            return str(path)

        data = getattr(
            file_item,
            "data",
            None,
        )

        if isinstance(
            data,
            dict,
        ):

            path = data.get(
                "path"
            )

            if path:
                return str(path)

        return None

    # ============================================================
    # READ FILE BYTES
    # ============================================================

    async def read_file_bytes(
        self,
        file_item,
    ):
        from open_webui.storage.provider import Storage

        file_path = self.file_path(
            file_item
        )

        if not file_path:
            raise RuntimeError(
                "The Open WebUI File record does not "
                "contain a storage path."
            )

        errors = []

        # --------------------------------------------------------
        # Storage.get_file
        # --------------------------------------------------------

        get_file = getattr(
            Storage,
            "get_file",
            None,
        )

        if get_file:

            try:

                result = get_file(
                    file_path
                )

                if asyncio.iscoroutine(result):
                    result = await result

                if hasattr(
                    result,
                    "read",
                ):

                    contents = result.read()

                    if asyncio.iscoroutine(
                        contents
                    ):
                        contents = await contents

                    if contents:
                        return contents

                if isinstance(
                    result,
                    bytes,
                ):
                    return result

                if isinstance(
                    result,
                    bytearray,
                ):
                    return bytes(result)

            except Exception as e:

                errors.append(
                    "Storage.get_file: "
                    f"{type(e).__name__}: {e}"
                )

        # --------------------------------------------------------
        # Storage.get_file_content
        # --------------------------------------------------------

        get_file_content = getattr(
            Storage,
            "get_file_content",
            None,
        )

        if get_file_content:

            try:

                result = get_file_content(
                    file_path
                )

                if asyncio.iscoroutine(result):
                    result = await result

                if isinstance(
                    result,
                    bytes,
                ):
                    return result

                if isinstance(
                    result,
                    bytearray,
                ):
                    return bytes(result)

                if hasattr(
                    result,
                    "read",
                ):

                    contents = result.read()

                    if asyncio.iscoroutine(
                        contents
                    ):
                        contents = await contents

                    if contents:
                        return contents

            except Exception as e:

                errors.append(
                    "Storage.get_file_content: "
                    f"{type(e).__name__}: {e}"
                )

        # --------------------------------------------------------
        # Direct filesystem fallback
        # --------------------------------------------------------

        if os.path.isfile(file_path):

            try:

                with open(
                    file_path,
                    "rb",
                ) as file:
                    contents = file.read()

                if contents:
                    return contents

            except Exception as e:

                errors.append(
                    f"Filesystem `{file_path}`: "
                    f"{type(e).__name__}: {e}"
                )

        raise RuntimeError(
            "Could not read the image bytes from "
            "Open WebUI storage.\n\n"
            f"File path: `{file_path}`\n\n"
            "Attempts:\n"
            + "\n".join(
                f"- {error}"
                for error in errors
            )
        )

    # ============================================================
    # FIND LATEST IMAGE
    # ============================================================

    async def find_latest_image_file(
        self,
        chat_id,
        current_message_id=None,
    ):
        chat = await self.get_chat_data(
            chat_id
        )

        if chat is None:
            raise RuntimeError(
                f"Open WebUI returned no chat for `{chat_id}`."
            )

        messages = self.extract_message_map(
            chat
        )

        if not messages:
            raise RuntimeError(
                "The current chat contains no accessible "
                "message map."
            )

        candidates = []

        for message_id, message in messages.items():

            if not isinstance(
                message,
                dict,
            ):
                continue

            file_ids = (
                self.extract_file_ids_from_message(
                    message
                )
            )

            if not file_ids:
                continue

            timestamp = (
                message.get("timestamp")
                or message.get("created_at")
                or 0
            )

            candidates.append(
                {
                    "message_id": str(
                        message_id
                    ),
                    "message": message,
                    "file_ids": file_ids,
                    "timestamp": timestamp,
                    "role": message.get(
                        "role"
                    ),
                }
            )

        if not candidates:
            raise RuntimeError(
                "No message containing file attachments "
                "was found in the current chat."
            )

        try:

            candidates.sort(
                key=lambda item: float(
                    item["timestamp"] or 0
                ),
                reverse=True,
            )

        except Exception:

            candidates.reverse()

        # Prefer the current message first.
        ordered_candidates = []

        if current_message_id:

            current_id = str(
                current_message_id
            )

            ordered_candidates.extend(
                candidate
                for candidate in candidates
                if candidate["message_id"]
                == current_id
            )

            ordered_candidates.extend(
                candidate
                for candidate in candidates
                if candidate["message_id"]
                != current_id
            )

        else:

            ordered_candidates = candidates

        image_extensions = (
            ".png",
            ".jpg",
            ".jpeg",
            ".webp",
            ".gif",
            ".bmp",
        )

        for candidate in ordered_candidates:

            for file_id in candidate[
                "file_ids"
            ]:

                file_item = (
                    await self.get_file_record(
                        file_id
                    )
                )

                if not file_item:
                    continue

                content_type = (
                    self.file_content_type(
                        file_item
                    )
                )

                filename = (
                    self.file_filename(
                        file_item
                    )
                )

                if (
                    content_type.startswith(
                        "image/"
                    )
                    or filename.lower().endswith(
                        image_extensions
                    )
                ):

                    return {
                        "file": file_item,
                        "file_id": file_id,
                        "message_id": candidate[
                            "message_id"
                        ],
                        "filename": filename,
                        "content_type": content_type,
                        "timestamp": candidate[
                            "timestamp"
                        ],
                    }

        raise RuntimeError(
            "The current chat contains attachments, "
            "but no image attachment could be identified."
        )

    # ============================================================
    # PREVIOUS IMAGE BASE64
    # ============================================================

    async def get_previous_image_base64(
        self,
        chat_id,
        current_message_id=None,
        status_callback=None,
    ):
        print(
            "[COMFYUI_IMAGE] Looking for previous image "
            f"chat_id={chat_id!r} "
            f"current_message_id={current_message_id!r}",
            flush=True,
        )
        if status_callback:
            await status_callback(
                "Looking for the latest generated image..."
            )

        selected = (
            await self.find_latest_image_file(
                chat_id,
                current_message_id,
            )
        )

        if status_callback:
            await status_callback(
                "Found previous image "
                f"`{selected['filename']}`. "
                "Retrieving image data..."
            )

        image_bytes = (
            await self.read_file_bytes(
                selected["file"]
            )
        )

        if not image_bytes:
            raise RuntimeError(
                "The previous image file was found, "
                "but it contains no data."
            )

        encoded = base64.b64encode(
            image_bytes
        ).decode("ascii")

        diagnostics = {
            "chat_id": str(chat_id),
            "current_message_id": (
                str(current_message_id)
                if current_message_id
                else None
            ),
            "source_message_id": selected[
                "message_id"
            ],
            "file_id": selected[
                "file_id"
            ],
            "filename": selected[
                "filename"
            ],
            "content_type": selected[
                "content_type"
            ],
            "byte_count": len(
                image_bytes
            ),
            "base64_character_count": len(
                encoded
            ),
        }

        print(
            "[COMFYUI_IMAGE] PREVIOUS IMAGE FOUND "
            f"file_id={selected['file_id']!r} "
            f"filename={selected['filename']!r}",
            flush=True,
        )
        return encoded, diagnostics

    # ============================================================
    # CREATE OPEN WEBUI FILE
    # ============================================================

    async def create_file(
        self,
        image_bytes,
        filename,
        user_id,
    ):
        print(
            "[COMFYUI_IMAGE] create_file() "
            f"filename={filename!r} "
            f"bytes={len(image_bytes)} "
            f"user_id={user_id!r}",
            flush=True,
        )
        from open_webui.models.files import (
            FileForm,
            Files,
        )

        from open_webui.storage.provider import (
            Storage,
        )

        file_id = str(
            uuid.uuid4()
        )

        storage_filename = (
            f"{file_id}_{filename}"
        )

        tags = {
            "OpenWebUI-User-Id": str(
                user_id
            ),
            "OpenWebUI-File-Id": str(
                file_id
            ),
        }

        file_object = io.BytesIO(
            image_bytes
        )

        upload_result = (
            await asyncio.to_thread(
                Storage.upload_file,
                file_object,
                storage_filename,
                tags,
            )
        )
        if (
            not isinstance(
                upload_result,
                tuple,
            )
            or len(upload_result) != 2
        ):
            raise RuntimeError(
                "Open WebUI Storage.upload_file "
                "returned an unexpected result."
            )

        contents, file_path = upload_result

        print(
            "[COMFYUI_IMAGE] Storage.upload_file() COMPLETE "
            f"path={file_path!r}",
            flush=True,
        )

        if (
            not isinstance(
                upload_result,
                tuple,
            )
            or len(upload_result) != 2
        ):
            raise RuntimeError(
                "Open WebUI Storage.upload_file "
                "returned an unexpected result."
            )

        contents, file_path = upload_result

        if not contents:
            raise RuntimeError(
                "Open WebUI storage returned "
                "empty file contents."
            )

        file_hash = hashlib.sha256(
            contents
        ).hexdigest()

        file_item = (
            await Files.insert_new_file(
                user_id,
                FileForm(
                    id=file_id,
                    filename=filename,
                    path=file_path,
                    data={},
                    meta={
                        "name": filename,
                        "content_type": "image/png",
                        "size": len(contents),
                        "file_hash": file_hash,
                        "data": {},
                    },
                ),
            )
        )
        print(
            "[COMFYUI_IMAGE] Open WebUI File record CREATED "
            f"id={file_id}",
            flush=True,
        )

        if not file_item:
            raise RuntimeError(
                "Open WebUI failed to create "
                "the File record."
            )

        return file_item

    # ============================================================
    # ATTACH FILES
    # ============================================================

    async def attach_files_to_message(
        self,
        chat_id,
        message_id,
        attached_files,
    ):
        if (
            not chat_id
            or not message_id
            or not attached_files
        ):
            return

        from open_webui.models.chats import (
            Chats,
        )

        method = getattr(
            Chats,
            "add_message_files_by_id_and_message_id",
            None,
        )

        if method is None:
            raise RuntimeError(
                "Open WebUI does not expose "
                "`add_message_files_by_id_and_message_id`."
            )

        result = method(
            chat_id,
            message_id,
            attached_files,
        )

        if asyncio.iscoroutine(result):
            await result
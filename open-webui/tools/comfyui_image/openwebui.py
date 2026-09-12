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
    # REQUEST MESSAGE / IMAGE SELECTION HELPERS
    # ============================================================

    def _message_parent_id(self, message):
        if not isinstance(message, dict):
            return None

        for key in ("parentId", "parent_id", "parentMessageId"):
            value = message.get(key)
            if value:
                return str(value)

        return None

    def _message_timestamp(self, message):
        if not isinstance(message, dict):
            return 0.0

        value = (
            message.get("timestamp")
            or message.get("created_at")
            or 0
        )

        try:
            return float(value or 0)
        except (TypeError, ValueError):
            return 0.0

    def _resolve_request_message_id(
        self,
        messages,
        current_message_id=None,
    ):
        """Resolve the user message that caused the current tool call.

        Open WebUI may provide either the user message ID or the assistant
        message ID as ``__message_id__``. When it is an assistant message,
        walk its parent chain back to the nearest user message. If the
        current ID is not yet present in stored chat data, fall back to the
        newest user message in the active chat data.
        """
        if not isinstance(messages, dict) or not messages:
            return None

        if current_message_id:
            cursor = str(current_message_id)
            visited = set()

            while cursor and cursor not in visited:
                visited.add(cursor)
                message = messages.get(cursor)

                if not isinstance(message, dict):
                    break

                if str(message.get("role", "")).lower() == "user":
                    return cursor

                cursor = self._message_parent_id(message)

        user_candidates = []
        for message_id, message in messages.items():
            if not isinstance(message, dict):
                continue
            if str(message.get("role", "")).lower() != "user":
                continue

            user_candidates.append(
                (
                    self._message_timestamp(message),
                    str(message_id),
                )
            )

        if not user_candidates:
            return None

        user_candidates.sort(reverse=True)
        return user_candidates[0][1]

    async def _image_records_from_message(
        self,
        message_id,
        message,
    ):
        """Return image attachments from one message in stored file order."""
        if not isinstance(message, dict):
            return []

        image_extensions = (
            ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"
        )
        records = []

        for attachment_index, file_id in enumerate(
            self.extract_file_ids_from_message(message),
            start=1,
        ):
            file_id = str(file_id)
            file_item = await self.get_file_record(file_id)
            if not file_item:
                continue

            content_type = self.file_content_type(file_item)
            filename = self.file_filename(file_item)

            if not (
                content_type.startswith("image/")
                or filename.lower().endswith(image_extensions)
            ):
                continue

            records.append(
                {
                    "file": file_item,
                    "file_id": file_id,
                    "message_id": str(message_id),
                    "filename": filename,
                    "content_type": content_type,
                    "timestamp": self._message_timestamp(message),
                    "attachment_index": attachment_index,
                    "role": message.get("role"),
                }
            )

        return records

    async def _find_previous_thread_image(
        self,
        messages,
        request_message_id,
        current_message_id=None,
    ):
        """Find the nearest earlier image on the active message branch."""
        request_message = messages.get(request_message_id)
        cursor = self._message_parent_id(request_message)
        visited = set()

        # Prefer the direct ancestor chain so alternate chat branches cannot
        # accidentally become the edit source.
        while cursor and cursor not in visited:
            visited.add(cursor)
            message = messages.get(cursor)
            if not isinstance(message, dict):
                break

            records = await self._image_records_from_message(
                cursor,
                message,
            )
            if records:
                return records[0]

            cursor = self._message_parent_id(message)

        # Fallback for Open WebUI versions/chat records that do not preserve
        # parent links. Only consider messages no newer than the request.
        request_timestamp = self._message_timestamp(request_message)
        excluded = {str(request_message_id)}
        if current_message_id:
            excluded.add(str(current_message_id))

        candidates = []
        for message_id, message in messages.items():
            message_id = str(message_id)
            if message_id in excluded or not isinstance(message, dict):
                continue

            timestamp = self._message_timestamp(message)
            if request_timestamp and timestamp > request_timestamp:
                continue

            if not self.extract_file_ids_from_message(message):
                continue

            candidates.append((timestamp, message_id, message))

        candidates.sort(key=lambda item: item[0], reverse=True)

        for _timestamp, message_id, message in candidates:
            records = await self._image_records_from_message(
                message_id,
                message,
            )
            if records:
                return records[0]

        return None

    # ============================================================
    # FIND LATEST IMAGES
    # ============================================================

    async def find_latest_image_files(
        self,
        chat_id,
        current_message_id=None,
        limit=2,
    ):
        """Return up to ``limit`` image attachments, newest/current first.

        Attachments on the current message are preferred and retain the
        order in which Open WebUI stored them. If more images are needed,
        older messages are searched newest-first.
        """
        try:
            limit = int(limit)
        except (TypeError, ValueError):
            limit = 2

        if limit < 1:
            raise ValueError("Image limit must be at least 1.")

        chat = await self.get_chat_data(chat_id)
        if chat is None:
            raise RuntimeError(
                f"Open WebUI returned no chat for `{chat_id}`."
            )

        messages = self.extract_message_map(chat)
        if not messages:
            raise RuntimeError(
                "The current chat contains no accessible message map."
            )

        candidates = []
        for message_id, message in messages.items():
            if not isinstance(message, dict):
                continue

            file_ids = self.extract_file_ids_from_message(message)
            if not file_ids:
                continue

            timestamp = (
                message.get("timestamp")
                or message.get("created_at")
                or 0
            )
            candidates.append(
                {
                    "message_id": str(message_id),
                    "file_ids": file_ids,
                    "timestamp": timestamp,
                }
            )

        if not candidates:
            raise RuntimeError(
                "No message containing file attachments "
                "was found in the current chat."
            )

        try:
            candidates.sort(
                key=lambda item: float(item["timestamp"] or 0),
                reverse=True,
            )
        except Exception:
            candidates.reverse()

        if current_message_id:
            current_id = str(current_message_id)
            ordered_candidates = [
                c for c in candidates
                if c["message_id"] == current_id
            ] + [
                c for c in candidates
                if c["message_id"] != current_id
            ]
        else:
            ordered_candidates = candidates

        image_extensions = (
            ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"
        )
        selected = []
        seen_file_ids = set()

        for candidate in ordered_candidates:
            for file_id in candidate["file_ids"]:
                file_id = str(file_id)
                if file_id in seen_file_ids:
                    continue

                file_item = await self.get_file_record(file_id)
                if not file_item:
                    continue

                content_type = self.file_content_type(file_item)
                filename = self.file_filename(file_item)

                if (
                    content_type.startswith("image/")
                    or filename.lower().endswith(image_extensions)
                ):
                    seen_file_ids.add(file_id)
                    selected.append(
                        {
                            "file": file_item,
                            "file_id": file_id,
                            "message_id": candidate["message_id"],
                            "filename": filename,
                            "content_type": content_type,
                            "timestamp": candidate["timestamp"],
                        }
                    )
                    if len(selected) >= limit:
                        return selected

        if not selected:
            raise RuntimeError(
                "The current chat contains attachments, "
                "but no image attachment could be identified."
            )

        return selected

    # ============================================================
    # PREVIOUS IMAGES BASE64
    # ============================================================

    async def get_previous_images_base64(
        self,
        chat_id,
        current_message_id=None,
        count=2,
        status_callback=None,
    ):
        """Read and base64-encode ``count`` image attachments."""
        if status_callback:
            await status_callback(
                f"Looking for {count} image"
                f"{'s' if count != 1 else ''} for editing..."
            )

        selected_images = await self.find_latest_image_files(
            chat_id,
            current_message_id,
            limit=count,
        )

        if len(selected_images) < count:
            raise RuntimeError(
                f"Reference editing requires {count} images, "
                f"but only {len(selected_images)} image"
                f"{'s were' if len(selected_images) != 1 else ' was'} found."
            )

        encoded_images = []
        diagnostics = []

        for index, selected in enumerate(selected_images[:count], start=1):
            if status_callback:
                await status_callback(
                    f"Retrieving image {index}/{count}: "
                    f"`{selected['filename']}`..."
                )

            image_bytes = await self.read_file_bytes(selected["file"])
            if not image_bytes:
                raise RuntimeError(
                    f"Image `{selected['filename']}` was found, "
                    "but it contains no data."
                )

            encoded = base64.b64encode(image_bytes).decode("ascii")
            encoded_images.append(encoded)
            diagnostics.append(
                {
                    "chat_id": str(chat_id),
                    "current_message_id": (
                        str(current_message_id)
                        if current_message_id
                        else None
                    ),
                    "source_message_id": selected["message_id"],
                    "file_id": selected["file_id"],
                    "filename": selected["filename"],
                    "content_type": selected["content_type"],
                    "byte_count": len(image_bytes),
                    "base64_character_count": len(encoded),
                }
            )

        print(
            "[COMFYUI_IMAGE] PREVIOUS IMAGES FOUND "
            + ", ".join(
                f"{item['filename']!r}" for item in diagnostics
            ),
            flush=True,
        )
        return encoded_images, diagnostics

    # ============================================================
    # REFERENCE EDIT IMAGES BASE64
    # ============================================================

    async def get_reference_edit_images_base64(
        self,
        chat_id,
        current_message_id=None,
        primary_image_index=None,
        status_callback=None,
    ):
        """Select and encode the MAIN and REFERENCE images for Klein.

        Default selection rules:

        * Two images on the current user request: image 1 is MAIN and
          image 2 is REFERENCE.
        * One image on the current user request: the nearest previous image
          in the active chat thread is MAIN and the current upload is
          REFERENCE.

        ``primary_image_index`` may be 1 or 2 to override which of those
        two selected candidates becomes MAIN. This is intended for semantic
        cases where the vision model can tell that the second image is the
        object/scene that should be edited.
        """
        if primary_image_index is not None:
            try:
                primary_image_index = int(primary_image_index)
            except (TypeError, ValueError) as e:
                raise ValueError(
                    "primary_image_index must be 1, 2, or omitted."
                ) from e

            if primary_image_index not in (1, 2):
                raise ValueError(
                    "primary_image_index must be 1, 2, or omitted."
                )

        if status_callback:
            await status_callback(
                "Determining the main and reference images..."
            )

        chat = await self.get_chat_data(chat_id)
        if chat is None:
            raise RuntimeError(
                f"Open WebUI returned no chat for `{chat_id}`."
            )

        messages = self.extract_message_map(chat)
        if not messages:
            raise RuntimeError(
                "The current chat contains no accessible message map."
            )

        request_message_id = self._resolve_request_message_id(
            messages,
            current_message_id,
        )
        if not request_message_id:
            raise RuntimeError(
                "Could not determine the current user request message."
            )

        request_message = messages.get(request_message_id)
        request_images = await self._image_records_from_message(
            request_message_id,
            request_message,
        )

        if len(request_images) > 2:
            raise RuntimeError(
                "Two-image reference editing currently supports at most "
                "two images attached to the current user message. "
                f"Found {len(request_images)}."
            )

        if len(request_images) == 2:
            candidates = [request_images[0], request_images[1]]
            selection_mode = "two_current_uploads"

        elif len(request_images) == 1:
            previous_image = await self._find_previous_thread_image(
                messages,
                request_message_id,
                current_message_id=current_message_id,
            )
            if not previous_image:
                raise RuntimeError(
                    "A reference image was attached to the current request, "
                    "but no earlier image could be found in the active chat "
                    "thread to use as the main image."
                )

            # Candidate 1 = existing chat image (MAIN by default).
            # Candidate 2 = newly uploaded reference image.
            candidates = [previous_image, request_images[0]]
            selection_mode = "previous_main_plus_current_reference"

        else:
            raise RuntimeError(
                "Two-image reference editing needs one or two images attached "
                "to the current user request. With one upload, the tool uses "
                "the latest earlier image in the active chat thread as the "
                "main image. With two uploads, the first is main and the "
                "second is reference by default."
            )

        # Candidate order defines the natural/default MAIN/REFERENCE roles.
        # The model can explicitly choose candidate 2 as MAIN for semantic
        # requests such as 'put the hat from image 1 on the cat in image 2'.
        candidate_order = [candidates[0], candidates[1]]
        selected_primary_index = primary_image_index or 1
        if selected_primary_index == 2:
            candidates = [candidate_order[1], candidate_order[0]]

        encoded_images = []
        diagnostics = []
        roles = ("main", "reference")

        for role, selected in zip(roles, candidates):
            if status_callback:
                await status_callback(
                    f"Retrieving {role} image: "
                    f"`{selected['filename']}`..."
                )

            image_bytes = await self.read_file_bytes(selected["file"])
            if not image_bytes:
                raise RuntimeError(
                    f"Image `{selected['filename']}` was found, "
                    "but it contains no data."
                )

            encoded = base64.b64encode(image_bytes).decode("ascii")
            encoded_images.append(encoded)

            original_candidate_index = (
                1 if selected is candidate_order[0] else 2
            )
            origin = (
                "current_request"
                if selected["message_id"] == str(request_message_id)
                else "chat_history"
            )

            diagnostics.append(
                {
                    "chat_id": str(chat_id),
                    "current_message_id": (
                        str(current_message_id)
                        if current_message_id
                        else None
                    ),
                    "request_message_id": str(request_message_id),
                    "source_message_id": selected["message_id"],
                    "file_id": selected["file_id"],
                    "filename": selected["filename"],
                    "content_type": selected["content_type"],
                    "byte_count": len(image_bytes),
                    "base64_character_count": len(encoded),
                    "image_role": role,
                    "origin": origin,
                    "candidate_index": original_candidate_index,
                    "selection_mode": selection_mode,
                    "primary_image_index": selected_primary_index,
                }
            )

        print(
            "[COMFYUI_IMAGE] REFERENCE EDIT IMAGE SELECTION "
            f"mode={selection_mode!r} "
            f"primary_index={selected_primary_index} "
            f"main={diagnostics[0]['filename']!r} "
            f"reference={diagnostics[1]['filename']!r}",
            flush=True,
        )

        return encoded_images, diagnostics

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
            result = await result

        # Return Open WebUI's canonical attachment objects so the caller
        # can emit the same representation to the live UI.
        return result

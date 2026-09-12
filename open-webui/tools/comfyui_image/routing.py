from .config import MODEL_WORKFLOWS

print("[COMFYUI_IMAGE] routing.py loaded", flush=True)

class ModelRouter:
    def __init__(self, routes=None):
        self.routes = routes if routes is not None else MODEL_WORKFLOWS

    def get_model_name(self, model):

        print(
            "[COMFYUI_IMAGE] get_model_name() received: "
            f"{model!r}",
            flush=True,
        )

        if not model:
            return None

        if not isinstance(model, dict):
            return None

        for candidate in (
            model.get("id"),
            model.get("name"),
            model.get("model"),
        ):
            if candidate in self.routes:
                print(
                    "[COMFYUI_IMAGE] MODEL ROUTING MATCH: "
                    f"{candidate!r}",
                    flush=True,
                )
                return candidate
        print(
            "[COMFYUI_IMAGE] MODEL ROUTING FAILED",
            flush=True,
        )
        return None

    def get_route(self, model_name):
        """Return routing information for a configured model."""

        if model_name not in self.routes:
            raise KeyError(f"Unknown model: {model_name}")

        return self.routes[model_name]

    def get_workflow_filename(
        self,
        model_name,
        edit_previous=False,
        reference_edit=False,
    ):
        """Return the generation, single-image edit, or reference-edit workflow."""

        route = self.get_route(model_name)

        if not edit_previous:
            workflow_key = "generate"
        elif reference_edit:
            workflow_key = "reference_edit"
        else:
            workflow_key = "edit"

        workflow_filename = route.get(workflow_key)

        if not workflow_filename:
            if workflow_key == "reference_edit":
                raise ValueError(
                    f"Two-image reference editing is not configured "
                    f"for `{route['name']}` yet."
                )
            if workflow_key == "edit":
                raise ValueError(
                    f"Image editing is not configured "
                    f"for `{route['name']}` yet."
                )
            raise ValueError(
                f"No generation workflow is configured "
                f"for `{route['name']}`."
            )

        return workflow_filename

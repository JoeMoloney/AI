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
    ):
        """Return the generation or editing workflow filename."""

        route = self.get_route(model_name)

        workflow_key = "edit" if edit_previous else "generate"

        workflow_filename = route.get(workflow_key)

        if edit_previous and not workflow_filename:
            raise ValueError(
                f"Image editing is not configured "
                f"for `{route['name']}` yet."
            )

        if not edit_previous and not workflow_filename:
            raise ValueError(
                f"No generation workflow is configured "
                f"for `{route['name']}`."
            )

        return workflow_filename
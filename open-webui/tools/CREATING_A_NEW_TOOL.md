Open WebUI Custom Tool: Creation & Registration Guide

This guide documents the workflow for creating a new local Open WebUI custom tool, registering it with Open WebUI, and deploying its source files into the mounted custom-tools directory.

Important: Open WebUI tool registration and the tool's supporting source files are two separate things. Registration tells Open WebUI that a tool exists and stores its main.py content. The mounted directory provides the supporting Python modules/files that main.py imports at runtime.

1. Create the tool in the repository

A useful repository layout is:

tools/
└── my_new_tool/
    ├── main.py
    ├── config.py
    ├── helpers.py
    └── ...

The important file is:

main.py

It should contain an Open WebUI-compatible Tools class.

For example:

"""
title: My New Tool
author: Local
version: 1.0.0
"""

class Tools:
    def __init__(self):
        print("[MY_NEW_TOOL] Tools initialized", flush=True)

    def hello(self, name: str = "world") -> str:
        return f"Hello, {name}!"

Open WebUI discovers callable methods on the Tools class and exposes them as tool functions.

2. Keep the tool directory self-contained

If main.py imports local files, keep those files inside the tool's directory.

For example:

my_new_tool/
├── main.py
├── config.py
├── helpers.py
└── workflows/
    └── example.json

Avoid relying on arbitrary files elsewhere on the host filesystem unless there is a deliberate reason to do so.

For the ComfyUI Image Tools project, this is why the tool has its own directory:

/custom_tools/comfyui_image/

and supporting modules such as:

comfyui.py
config.py
diagnostics.py
openwebui.py
routing.py
workflows.py
3. Decide the tool ID and display name

Choose a stable ID.

Example:

ID: comfyui_image_tools
Name: ComfyUI Image Tools

The ID is the important identifier used by Open WebUI's API.

The display name is what you normally see in the UI.

A good convention is:

lowercase_with_underscores

for the ID.

Do not casually change the ID after registration. Treat it as the tool's stable identifier.

4. Register the tool with Open WebUI

Registration is separate from copying the supporting files.

The registration request creates the Open WebUI database entry.

Conceptually, the request looks like:

curl -X POST "http://localhost:3000/api/tools/create" \
  -H "Authorization: Bearer $OPENWEBUI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "my_new_tool",
    "name": "My New Tool",
    "content": "...contents of main.py...",
    "meta": {
      "description": "My New Tool",
      "manifest": {}
    },
    "access_grants": []
  }'
Important

The content field is the actual contents of main.py.

Do not put the path:

/custom_tools/my_new_tool/main.py

in content.

Open WebUI stores the registered tool's main source code separately.

5. Verify that registration succeeded

Query the tools API:

curl -s \
  -H "Authorization: Bearer $OPENWEBUI_API_KEY" \
  "http://localhost:3000/api/v1/tools/" \
  | python -m json.tool

You should see an entry resembling:

{
    "id": "my_new_tool",
    "name": "My New Tool",
    ...
}

You can also query the specific tool:

curl -s \
  -H "Authorization: Bearer $OPENWEBUI_API_KEY" \
  "http://localhost:3000/api/v1/tools/id/my_new_tool" \
  | python -m json.tool

Check that:

the ID is correct
the name is correct
the tool exists
the expected functions appear under specs

For example:

"specs": [
    {
        "name": "hello",
        ...
    }
]
6. Copy the supporting files into the mounted directory

Your Docker Compose configuration mounts:

volumes:
  - ${OPENWEBUI_CUSTOMTOOLS}:/custom_tools

If your environment contains:

OPENWEBUI_CUSTOMTOOLS=~/M2/docker_data/open-webui/custom_tools

then, from the container's point of view, the host directory:

~/M2/docker_data/open-webui/custom_tools

appears as:

/custom_tools

inside Open WebUI.

Therefore a repository tool:

tools/my_new_tool/

should be copied to the host directory:

~/M2/docker_data/open-webui/custom_tools/my_new_tool/

which Open WebUI sees as:

/custom_tools/my_new_tool/
7. The relationship between registration and the mounted files

This is the part that is easiest to confuse.

There are two separate pieces.

Open WebUI registration

Open WebUI knows:

ID: my_new_tool
Name: My New Tool
main.py: registered source code

This is what makes the tool appear in the Open WebUI tools list and allows it to be attached to a model.

Mounted filesystem

The container has:

/custom_tools/my_new_tool/
├── main.py
├── config.py
├── helpers.py
└── ...

This is where the runtime can load supporting modules and files.

For a simple one-file tool, the registered main.py may be all that is required.

For a multi-file tool, the supporting files must also be available in the mounted directory.

8. Restart Open WebUI after changing tool source files

For this setup, the safe development workflow is:

Edit the source files in the repository.
Copy them into the mounted custom-tools directory.
Restart/recreate the Open WebUI container.
Test the tool.

For example:

docker compose up -d --force-recreate open-webui

or:

docker restart open-webui

A restart is the safest assumption when developing custom tools because Python modules may already have been imported into the running Open WebUI process.

Do not assume that changing a .py file on the mounted filesystem automatically reloads an already-running Python module.

9. Verify the files from inside the container

After copying the files, check:

docker exec open-webui sh -c \
  'ls -la /custom_tools/my_new_tool'

For a multi-file tool, confirm all expected files are present.

You can inspect main.py with:

docker exec open-webui sh -c \
  'sed -n "1,200p" /custom_tools/my_new_tool/main.py'
10. Test loading the tool directly

A useful diagnostic is to ask Open WebUI's own Python environment to load the registered tool:

docker exec open-webui sh -c \
  'python - <<'"'"'PY'"'"'
import asyncio
from open_webui.utils.plugin import load_tool_module_by_id

async def main():
    tool, frontmatter = await load_tool_module_by_id("my_new_tool")

    print("LOAD OK")
    print("Tool class:", type(tool))
    print("Frontmatter:", frontmatter)

asyncio.run(main())
PY'

A successful result should look roughly like:

LOAD OK
Tool class: <class 'tool_my_new_tool.Tools'>
Frontmatter: {}

Any diagnostic print() statements in your modules may also appear before LOAD OK.

This is an excellent test because it confirms that Open WebUI can actually import the registered tool.

11. Test the tool through Open WebUI

Once the direct loading test works:

Open Open WebUI.
Confirm the tool appears in the tools list.
Attach it to the intended model.
Ask the model to use the tool.
Check the Open WebUI container logs if something fails.

For example:

docker logs --since 5m open-webui

For a tool with explicit diagnostic logging, messages such as:

[MY_NEW_TOOL] Tools initialized

are useful confirmation that the tool was instantiated.

Development Workflow

Once everything is set up, the normal development cycle can be very simple.

┌─────────────────────────┐
│ Edit tool in VS Code    │
│ repository              │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ Run copy/deploy script  │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ Files copied to         │
│ $OPENWEBUI_CUSTOMTOOLS  │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ Restart Open WebUI      │
│ container               │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ Test tool loading       │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ Test from Open WebUI    │
└─────────────────────────┘
Creating a Completely New Tool

For a brand-new tool, the complete process is:

Step 1 — Create the repository folder
tools/my_new_tool/
Step 2 — Create main.py

Make sure it contains:

class Tools:
    ...

and valid Open WebUI tool methods.

Step 3 — Add supporting Python files

For example:

tools/my_new_tool/
├── main.py
├── config.py
└── helpers.py
Step 4 — Copy the folder to the custom-tools directory

Host:

~/M2/docker_data/open-webui/custom_tools/my_new_tool/

Container:

/custom_tools/my_new_tool/
Step 5 — Register the tool with Open WebUI

Use the tools API and provide:

id
name
main.py content
meta
access_grants
Step 6 — Verify registration
curl -s \
  -H "Authorization: Bearer $OPENWEBUI_API_KEY" \
  "http://localhost:3000/api/v1/tools/" \
  | python -m json.tool
Step 7 — Restart Open WebUI
docker restart open-webui
Step 8 — Test direct loading

Use:

load_tool_module_by_id("my_new_tool")
Step 9 — Test from the UI

Attach the tool to a model and call one of its functions.

Updating an Existing Tool

For normal code changes, you generally do not need to create a second tool.

If the tool already exists:

ID: my_new_tool

then:

Edit the source in the repository.
Copy the updated files into /custom_tools/my_new_tool/.
Restart Open WebUI.
Test the tool.

However, there is an important distinction.

If you change only supporting files

For example:

config.py
helpers.py
workflows.py

you only need to deploy those updated files and restart Open WebUI.

If you change main.py

Remember that Open WebUI also has a registered copy of main.py.

Therefore, depending on how the tool is being loaded and managed, you should keep the registered tool content and your repository copy synchronized.

For the safest workflow, treat the repository's main.py as the source of truth and update the Open WebUI registration when the registered main.py needs to change.

Avoid Copying __pycache__

Do not deploy Python cache files such as:

__pycache__/
*.pyc

into the custom-tools directory.

A copy command such as:

cp -r source/* destination/

can accidentally copy __pycache__.

Prefer a deployment method that excludes it.

For example, using rsync:

rsync -av \
  --exclude '__pycache__' \
  --exclude '*.pyc' \
  source/ \
  destination/

This also makes repeated deployments much cleaner because only changed files need to be copied.

Docker Mounting: What the Variables Mean

Given:

volumes:
  - ${OPENWEBUI_CUSTOMTOOLS}:/custom_tools

and:

OPENWEBUI_CUSTOMTOOLS=~/M2/docker_data/open-webui/custom_tools

the mapping is:

HOST
~/M2/docker_data/open-webui/custom_tools
          │
          │ Docker bind mount
          ▼
CONTAINER
/custom_tools

Therefore:

Host:
~/M2/docker_data/open-webui/custom_tools/comfyui_image/

is visible inside the container as:

/custom_tools/comfyui_image/
Environment Variables vs Container Paths

An environment variable such as:

COMFYUI_IMAGE_TOOL_DIR: /custom_tools/comfyui_image

is a container path.

It tells the Python code where the tool's files are located inside the container.

It should normally remain:

/custom_tools/comfyui_image

Do not replace it with the host path:

~/M2/docker_data/open-webui/custom_tools/comfyui_image

because the Python code is running inside the container and sees the mounted path, not the host filesystem path.

A useful mental model is:

Host path
    │
    │ Docker mount
    ▼
Container path
    │
    │ Python application uses this
    ▼
/custom_tools/comfyui_image
Recommended Repository Structure

A practical long-term layout might be:

AI/
├── docker-compose.yml
├── start.sh
├── open-webui/
│   ├── .env
│   └── .apiKey
└── tools/
    ├── comfyui_image/
    │   ├── main.py
    │   ├── config.py
    │   ├── comfyui.py
    │   ├── diagnostics.py
    │   ├── openwebui.py
    │   ├── routing.py
    │   ├── workflows.py
    │   └── ...
    │
    └── my_new_tool/
        ├── main.py
        ├── config.py
        └── ...

The repository becomes the place where you develop and version-control the source.

The mounted directory becomes the deployed copy that Open WebUI can access:

~/M2/docker_data/open-webui/custom_tools/
├── comfyui_image/
└── my_new_tool/
Key Things to Remember
1. Registration is not the same as mounting

Registering:

my_new_tool

with Open WebUI creates the tool entry.

Mounting:

/custom_tools/my_new_tool/

makes the filesystem files available inside the container.

Both can matter for a multi-file tool.

2. The tool ID is important

For example:

comfyui_image_tools

is the stable ID used by Open WebUI.

3. main.py contains the Open WebUI-facing Tools class

For example:

class Tools:
    def generate_image(...):
        ...
4. Supporting modules live beside main.py

For example:

main.py
config.py
routing.py
workflows.py
5. Use container paths inside Python

Use:

/custom_tools/my_new_tool

not the host path.

6. Restart after source changes

For this setup, restarting Open WebUI after deploying Python changes is the safest workflow.

7. Don't deploy __pycache__

Keep:

__pycache__/
*.pyc

out of the deployed tool directory.

8. Test registration separately from runtime loading

First verify:

/api/v1/tools/

Then verify:

load_tool_module_by_id(...)

Then test the tool through the UI.

ComfyUI Image Tools: Current Example

The existing tool demonstrates the complete architecture.

Open WebUI registration:

ID:
comfyui_image_tools

Name:
ComfyUI Image Tools

Container directory:

/custom_tools/comfyui_image/

Configured through:

COMFYUI_IMAGE_TOOL_DIR: /custom_tools/comfyui_image

Supporting modules are loaded from that directory.

A successful direct load currently looks like:

[COMFYUI_IMAGE] main.py loaded
[COMFYUI_IMAGE] comfyui.py loaded
[COMFYUI_IMAGE] openwebui.py loaded
[COMFYUI_IMAGE] routing.py loaded
[COMFYUI_IMAGE] workflows.py loaded
[COMFYUI_IMAGE] Tools.__init__()
...
LOAD OK

That confirms the registered tool can be loaded together with its supporting modules.

Future Automation

Once the manual process is understood, it is reasonable to automate it.

A future deployment script could:

Read OPENWEBUI_CUSTOMTOOLS from .env.
Copy a selected tool directory.
Exclude __pycache__ and .pyc files.
Read main.py.
Register the tool through the Open WebUI API if it does not already exist.
Update the registration if main.py changed.
Restart Open WebUI.
Run a load test.
Report success/failure.

It is worth keeping the manual process working first, however. Automation should reproduce a known-good workflow rather than hide what Open WebUI is doing.

Quick Checklist

When creating a new tool:

 Create a unique tool ID.
 Create the tool directory in the repository.
 Create main.py.
 Add a Tools class.
 Add supporting modules/files.
 Copy the directory to OPENWEBUI_CUSTOMTOOLS.
 Exclude __pycache__ and .pyc.
 Register the tool with Open WebUI.
 Verify it appears in /api/v1/tools/.
 Restart Open WebUI.
 Test load_tool_module_by_id().
 Attach the tool to a model.
 Test the tool from the Open WebUI UI.
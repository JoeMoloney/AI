# Open WebUI Custom Tools — Creation & Deployment Runbook

This runbook documents the local workflow for creating, registering, deploying, and testing Open WebUI Workspace Tools while keeping the source code in a version-controlled repository.

The important concept is that Open WebUI tool registration and the tool's supporting files are separate things.

- Open WebUI registration stores the actual `main.py` source and tells Open WebUI that the tool exists.
- The mounted `/custom_tools` directory provides supporting Python modules and other files that `main.py` imports or uses at runtime.

This runbook is designed to remain useful even without internet access.


## 1. Architecture

The recommended layout is:

Git repository
└── tools/
    └── my_new_tool/
        ├── main.py
        ├── config.py
        ├── helpers.py
        └── ...

The deployed copy is placed in the host directory configured by:

volumes:
  - ${OPENWEBUI_CUSTOMTOOLS}:/custom_tools

For example:

Host:
~/M2/docker_data/open-webui/custom_tools/my_new_tool/

Container:
/custom_tools/my_new_tool/

Open WebUI's database contains the registered tool:

Tool ID:
my_new_tool

Name:
My New Tool

Content:
<actual contents of main.py>

The relationship is:

                 Git repository
                      |
          +-----------+-----------+
          |                       |
       main.py              supporting files
          |                       |
          v                       v
 Open WebUI database       /custom_tools/my_new_tool/
          |                       |
          +-----------+-----------+
                      |
                      v
              Open WebUI loads
              registered main.py
                      |
                      v
          main.py imports/uses
          supporting files

IMPORTANT:

Copying a folder into `/custom_tools` does NOT automatically register a new Workspace Tool.

The tool must first exist in Open WebUI's tool registry/database.


## 2. Create the Tool in the Repository

Create a directory:

tools/my_new_tool/

At minimum:

tools/my_new_tool/
└── main.py

A simple `main.py`:

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

Open WebUI uses the `Tools` class and its callable methods as the tool's functions.


## 3. Keep Supporting Files With the Tool

For a multi-file tool:

tools/my_new_tool/
├── main.py
├── config.py
├── helpers.py
└── workflows/
    └── example.json

`main.py` can import the supporting modules.

For example:

from helpers import do_something
from config import SOME_SETTING

Keep the tool self-contained where practical.

Avoid relying on arbitrary files elsewhere on the host filesystem unless there is a deliberate reason to do so.


## 4. Choose a Stable Tool ID

Choose a unique, stable ID.

Example:

ID: comfyui_image_tools
Name: ComfyUI Image Tools

Recommended ID style:

lowercase_with_underscores

Treat the ID as permanent. Avoid casually changing it after registration.


## 5. Register the Tool With Open WebUI

This is a required step for this deployment method.

DO NOT register a filesystem path to `main.py`.

The registration contains the actual contents of `main.py`.

Conceptually:

ID:
my_new_tool

Name:
My New Tool

Content:
<contents of tools/my_new_tool/main.py>

The registration creates the Open WebUI database entry.

Example API request:

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

IMPORTANT:

The `content` field is the actual Python source of `main.py`.
[To update, paste the contents of main.py into the tools web IDE interface]

## 6. Deploy the Tool Files

After registering the tool, copy the repository directory to the mounted custom-tools directory.

Given:

volumes:
  - ${OPENWEBUI_CUSTOMTOOLS}:/custom_tools

and:

OPENWEBUI_CUSTOMTOOLS=~/M2/docker_data/open-webui/custom_tools

the mapping is:

Host:
~/M2/docker_data/open-webui/custom_tools/

        |
        | Docker bind mount
        v

Container:
/custom_tools/

Therefore:

Repository:
tools/my_new_tool/

        |
        | deploy/copy
        v

Host:
~/M2/docker_data/open-webui/custom_tools/my_new_tool/

        |
        | Docker mount
        v

Container:
/custom_tools/my_new_tool/


## 7. Why Both Registration and Filesystem Deployment Are Needed

For a multi-file tool there are two separate pieces.

### Open WebUI registration

Open WebUI knows:

ID: my_new_tool
Name: My New Tool
main.py: registered Python source

This is what makes the tool known to Open WebUI.

### Mounted filesystem

The container has:

/custom_tools/my_new_tool/
├── config.py
├── helpers.py
├── workflows/
└── ...

These files are available to the registered `main.py` at runtime.

### Critical distinction

Open WebUI does NOT automatically find and register:

/custom_tools/my_new_tool/main.py

just because the file exists.

The normal sequence is:

1. Register main.py with Open WebUI.
2. Deploy supporting files to /custom_tools/.
3. Restart Open WebUI.
4. Test the tool.

For a simple one-file tool, the registered `main.py` may be sufficient.

For a multi-file tool, the supporting files must also be available inside the container.


## 8. Keep Repository main.py and Registered main.py Synchronized

There are effectively two copies of `main.py`:

Git repository:
tools/my_new_tool/main.py

Open WebUI database:
registered main.py

Treat the Git repository as the source of truth.

If `main.py` changes:

1. Edit the repository version.
2. Update/re-register the Open WebUI tool so its registered content matches.
2.1 This can be done simply by updating the code in the open-webui frontend IDE interface with the new main.py source code
3. Deploy the supporting files.
4. Restart Open WebUI.
5. Test the tool.

Avoid accidentally testing an old registered copy while believing you are testing the new Git version.


## 9. Deploy Supporting Files

Use a deployment method that excludes Python cache files.

Recommended:

rsync -av \
  --exclude '__pycache__/' \
  --exclude '*.pyc' \
  tools/my_new_tool/ \
  "$OPENWEBUI_CUSTOMTOOLS/my_new_tool/"

Do not deploy:

__pycache__/
*.pyc

These are generated Python cache files and should not normally be version-controlled or deployed.


## 10. Restart Open WebUI

After changing Python source files, restart Open WebUI.

For example:

docker restart open-webui

or:

docker compose up -d --force-recreate open-webui

Restarting is the safest development workflow because Python modules may already have been imported and cached by the running Open WebUI process.

Do not assume that changing a `.py` file on the mounted filesystem automatically reloads an already-running module.


## 11. Verify the Files Inside the Container

Check that the deployed files exist:

docker exec open-webui sh -c \
  'ls -la /custom_tools/my_new_tool'

For example:

main.py
config.py
helpers.py
workflows/

Inspect `main.py` if necessary:

docker exec open-webui sh -c \
  'sed -n "1,200p" /custom_tools/my_new_tool/main.py'

Remember that this filesystem copy is primarily useful for supporting files. The Open WebUI tool loader executes the registered `main.py` source.


## 12. Verify Tool Registration

List registered tools:

curl -s \
  -H "Authorization: Bearer $OPENWEBUI_API_KEY" \
  "http://localhost:3000/api/v1/tools/" \
  | python -m json.tool

Look for:

my_new_tool

You can also query the individual tool:

curl -s \
  -H "Authorization: Bearer $OPENWEBUI_API_KEY" \
  "http://localhost:3000/api/v1/tools/id/my_new_tool" \
  | python -m json.tool

Check that:

- The ID is correct.
- The name is correct.
- The tool exists.
- The expected functions appear in the tool specification.

For example:

"specs": [
    {
        "name": "hello"
    }
]


## 13. Test Loading the Tool

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

Diagnostic `print()` statements from the tool may appear before `LOAD OK`.

This test is useful because it confirms that Open WebUI can actually load the registered tool and its imports.


## 14. Test Through Open WebUI

Once direct loading works:

1. Open Open WebUI.
2. Confirm the tool appears in the Tools list.
3. Attach it to the intended model.
4. Ask the model to use the tool.
5. Check the logs if something fails.

Useful command:

docker logs --since 5m open-webui

Diagnostic messages such as:

[MY_NEW_TOOL] Tools initialized

can confirm that the tool was instantiated.


## 15. Dependencies

The Open WebUI container has its own Python environment.

A package installed on the host is NOT automatically available inside the container.

For example:

import requests

requires `requests` to exist in the Open WebUI container's Python environment.

For every third-party dependency:

- Document it in the repository.
- Make sure it is installed in the Open WebUI environment.
- Do not assume host Python packages are available inside Docker.

Prefer the Python standard library where practical.


## 16. Environment Variables and Secrets

Python code runs inside the Open WebUI container.

Use container-visible environment variables:

import os

comfyui_url = os.environ["COMFYUI_URL"]

Docker Compose might provide:

environment:
  COMFYUI_URL: ${COMFYUI_URL}

Use container paths in Python.

For example:

/custom_tools/comfyui_image

NOT:

~/M2/docker_data/open-webui/custom_tools/comfyui_image

The latter is a host path and is not normally visible from inside the container.

### Never put secrets directly into main.py

Avoid:

API_KEY = "actual-secret"

Prefer environment variables or another appropriate secret mechanism.


## 17. Security

Open WebUI Workspace Tools execute Python inside the Open WebUI server environment.

Treat tool code as trusted code.

A tool may potentially access:

- The filesystem
- Environment variables
- Network resources
- Installed Python packages
- The Open WebUI Python environment

Do not install or register untrusted tool code.

For a local single-user installation this may be acceptable, but it becomes particularly important if Open WebUI is accessible to other users.


## 18. Updating an Existing Tool

If the tool already exists:

ID: my_new_tool

do not create another tool just because the code changed.

### Supporting-file-only change

If you change:

config.py
helpers.py
workflows.py

then:

1. Deploy the changed files.
2. Restart Open WebUI.
3. Test the tool.

### main.py change

If you change:

main.py

then:

1. Edit the repository version.
2. Update the registered Open WebUI source.
3. Deploy supporting files.
4. Restart Open WebUI.
5. Test the tool.

The repository version should remain the source of truth.


## 19. Tool vs Function

This runbook describes an Open WebUI Workspace Tool using:

class Tools:
    ...

Do not confuse this with Open WebUI Functions such as:

Pipe
Filter
Action
Event

They are different extension mechanisms.


## 20. Docker Path Mental Model

Given:

volumes:
  - ${OPENWEBUI_CUSTOMTOOLS}:/custom_tools

think:

HOST
~/M2/docker_data/open-webui/custom_tools/
                |
                | Docker bind mount
                v
CONTAINER
/custom_tools/
                |
                v
PYTHON CODE
/custom_tools/my_new_tool/

Environment variables such as:

COMFYUI_IMAGE_TOOL_DIR: /custom_tools/comfyui_image

are therefore container paths.


## 21. Recommended Repository Structure

A practical long-term structure:

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

The repository is where you develop and version-control the source.

The mounted directory is the deployed runtime copy:

~/M2/docker_data/open-webui/custom_tools/

├── comfyui_image/
└── my_new_tool/


## 22. Complete New Tool Procedure

When creating a completely new tool:

### Step 1 — Create the repository folder

tools/my_new_tool/

### Step 2 — Create main.py

Include:

class Tools:
    ...

### Step 3 — Add supporting files

For example:

main.py
config.py
helpers.py

### Step 4 — Register the tool with Open WebUI

Register:

ID
Name
actual contents of main.py

Do NOT register a filesystem path.

### Step 5 — Deploy the tool directory

Copy:

tools/my_new_tool/

to:

$OPENWEBUI_CUSTOMTOOLS/my_new_tool/

Exclude:

__pycache__/
*.pyc

### Step 6 — Restart Open WebUI

docker restart open-webui

### Step 7 — Verify registration

curl -s \
  -H "Authorization: Bearer $OPENWEBUI_API_KEY" \
  "http://localhost:3000/api/v1/tools/" \
  | python -m json.tool

### Step 8 — Test direct loading

Use:

load_tool_module_by_id("my_new_tool")

### Step 9 — Test from Open WebUI

Attach the tool to the intended model and call one of its functions.


## 23. Quick Troubleshooting

### Tool does not appear in Open WebUI

Check:

/api/v1/tools/

If it is missing, the registration step has failed or the ID is wrong.

### Tool appears but fails to load

Check:

docker logs --since 5m open-webui

Then run the direct loading test.

### Import error

Example:

ModuleNotFoundError: No module named 'helpers'

Check that:

/custom_tools/my_new_tool/helpers.py

exists inside the container.

### Changes are not appearing

Check that:

1. The repository was deployed.
2. The registered `main.py` was updated if `main.py` changed.
3. Open WebUI was restarted.

### Works on the host but not in Docker

Check:

- Python package availability inside the container.
- Environment variables.
- Container filesystem paths.
- Network connectivity from the container.


## 24. Future Automation

Once the manual workflow is understood, it can be automated.

A deployment script could:

1. Read `OPENWEBUI_CUSTOMTOOLS`.
2. Select a tool from the repository.
3. Copy its files.
4. Exclude `__pycache__` and `.pyc`.
5. Read `main.py`.
6. Register the tool if it does not exist.
7. Update the registration when `main.py` changes.
8. Restart Open WebUI.
9. Run a load test.
10. Report success or failure.

The repository should remain the source of truth.

Automation should reproduce the known-good manual workflow rather than hide what Open WebUI is doing.


## 25. Final Checklist

When creating a new tool:

- [ ] Create a unique tool ID.
- [ ] Create the tool directory in the Git repository.
- [ ] Create `main.py`.
- [ ] Add the `Tools` class.
- [ ] Add supporting modules/files.
- [ ] Register the tool with Open WebUI.
- [ ] Put the actual `main.py` source into the registration.
- [ ] Do NOT register a path to `main.py`.
- [ ] Copy the tool directory to `$OPENWEBUI_CUSTOMTOOLS`.
- [ ] Exclude `__pycache__` and `.pyc`.
- [ ] Verify the tool appears in `/api/v1/tools/`.
- [ ] Restart Open WebUI.
- [ ] Test `load_tool_module_by_id()`.
- [ ] Confirm supporting imports work.
- [ ] Attach the tool to the intended model.
- [ ] Test the tool from the Open WebUI UI.


## The Short Version

For future reference, the essential workflow is:

1. Create tool in Git repo.
2. Register tool with Open WebUI.
   - Register the actual `main.py` contents.
   - Do not register a path to `main.py`.
3. Copy the tool folder to `$OPENWEBUI_CUSTOMTOOLS`.
4. Restart Open WebUI.
5. Open WebUI loads the registered `main.py`.
6. `main.py` imports supporting files from `/custom_tools/my_new_tool/`.
7. Test the tool.

Remember:

The database registration tells Open WebUI **what tool to execute**.

The `/custom_tools` mount provides **the additional files that the registered tool needs at runtime**.

The Git repository is the source of truth.

The Open WebUI registration and mounted directory are the deployed runtime state.
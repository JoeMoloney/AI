# Git workflow

Use a feature branch for each piece of work, then merge it into `master`.

## 1. Create a branch

```bash
git switch master
git pull --ff-only
git switch -c feature/project-api
```

## 2. Commit your work

Check and stage only the files you intend to commit:

```bash
git status
git diff
git add <files>
git add -p <file>  # Stage selected hunks when one file contains several changes.
git diff --staged
git commit -m "(chat jippity) feat(memory-service): add project API"
```

Use one commit per understandable change, including its tests and documentation.
Avoid `git add .` when unrelated work is present. Review `git diff --staged` for
credentials and machine-specific settings before committing. The
`(chat jippity)` prefix identifies commits made with this assistant; keep the
rest of the message specific to the change.

## 3. Push the feature branch

```bash
git push -u origin feature/project-api
```

Later pushes from the same branch only need `git push`.

## 4. Merge into `master`

```bash
git switch master
git pull --ff-only
git merge --no-ff -m "(chat jippity) Merge feature/project-api" feature/project-api
git push
```

Supplying `-m` prevents Git from opening an editor for the merge message.
If Git says a merge is still in progress, finish it with:

```bash
git commit -m "(chat jippity) Merge feature/project-api"
git push
```

## 5. Optional cleanup

After confirming that `master` was pushed successfully:

```bash
git branch -d feature/project-api
git push origin --delete feature/project-api
```

Replace `feature/project-api` and the commit messages with names appropriate to
the work. This repository pushes `origin` to both GitLab and GitHub, so two sets
of push output are expected.

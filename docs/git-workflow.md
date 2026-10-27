# Git Workflow (Simplified)

## 1. Create new branch

```bash
git checkout master
git pull --ff-only
git checkout -b feature/this-gon-git
```

## 2. Commit work

Yes.

## 3. Push changes

Yes.

## 4. Do the merge - without having to faff around with GUI merges in Github + Gitlab

```bash
git checkout master
git pull --ff-only
git merge --no-ff -m "something something something chat jippity something something"
git push
```

Pray you have been paying close attention to your git flow, there ain't nothing more enjoyable than dealing with conflicts and divergence..

## 5. Optional cleanup

After confirming that `master` has the feature merged into it and pushed to remote/origin successfully:

```bash
git branch -d feature/project-api
git push origin --delete feature/project-api
```

## 6. Setting up Gitlab Primary + Github Secondary

I have those notes around somewhere and will include them.
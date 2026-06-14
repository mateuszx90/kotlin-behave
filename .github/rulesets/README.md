# Branch protection rulesets

GitHub branch protection rules for this repository, stored as importable JSON.

## How to apply

1. GitHub → Settings → Rules → Rulesets → **New ruleset** → **Import a ruleset**
2. Upload `main-protection.json`
3. Click **Create**

The ruleset is targeted at `~DEFAULT_BRANCH`, so it follows whatever branch is set as default (currently `main`).

## What it enforces on `main`

| Rule | Effect |
|---|---|
| `deletion` | The branch cannot be deleted. |
| `non_fast_forward` | No force-pushes — nobody, not even Admins. |
| `required_linear_history` | Only squash or rebase merges, no merge commits. |
| `pull_request` | All changes go through a PR. Stale reviews dismissed on new commits. Unresolved review threads block merge. |
| `required_status_checks` | Required CI checks must pass. The list is empty; populate after adding GitHub Actions workflows. |

## Bypass

Only **Repository Admin** (role id `5` — i.e. you, the owner) can bypass the PR requirement, and only via the "pull_request" bypass mode. This means:

- Direct pushes to `main` are blocked for everyone, including you.
- To merge a PR, you can self-approve and squash/rebase merge.
- Emergencies: as Admin you can still merge a PR without the normal status-check guard.
- Force-push is **not** bypassable — re-writing `main` history is impossible.

## Adding required status checks later

Once a CI workflow exists (e.g. `.github/workflows/ci.yml` with a job named `build`), update the ruleset:

```json
"required_status_checks": [
  { "context": "build" }
]
```

Re-import or edit the ruleset in the GitHub UI.

#!/data/data/com.termux/files/usr/bin/sh
# Installs the GitHub Actions workflow. The automated session cannot push to .github/workflows,
# so run this once from the repository root in Termux (or any shell):
#
#   git pull
#   sh ci/apply-ci.sh
#
# Your push credentials need the "workflow" scope (classic PAT) or "Workflows: write" (fine-grained).
set -eu

cd "$(git rev-parse --show-toplevel)"
mkdir -p .github/workflows
cp ci/build.yml .github/workflows/build.yml
git add .github/workflows/build.yml

if git diff --cached --quiet; then
  echo "Workflow already installed, nothing to commit."
  exit 0
fi

git commit -m "ci: add Android build and unit test workflow"
git push
echo "Done. Check the Actions tab for the first run."

#!/usr/bin/env bash
# Pose le tag annote contract-vX.Y.Z sur le contrat OpenAPI courant.
# Usage : tools/tag-contract.sh [--dry-run] <X.Y.Z>
#   --dry-run : fait toutes les verifications puis s'arrete avant `git tag`.
# Ne pousse jamais : le push du tag reste une action manuelle.
set -euo pipefail

usage() {
  echo "Usage : tools/tag-contract.sh [--dry-run] <X.Y.Z>" >&2
  exit 1
}

dry_run=false
if [ "${1:-}" = "--dry-run" ]; then
  dry_run=true
  shift
fi

version="${1:-}"
[ -n "$version" ] || usage
if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version invalide : '$version' (format attendu X.Y.Z)." >&2
  usage
fi

cd "$(git rev-parse --show-toplevel)"
tag="contract-v$version"

if [ -n "$(git status --porcelain)" ]; then
  echo "L'arbre de travail n'est pas propre : commite ou range tes changements avant de tagger." >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  echo "Le tag $tag existe déjà." >&2
  exit 1
fi

file="contract/openapi.yaml"
if [ ! -f "$file" ]; then
  echo "$file est introuvable : lance 'make contract' et commite le résultat." >&2
  exit 1
fi

file_version="$(awk '/^info:/{i=1;next} i&&/^[^ ]/{i=0} i&&/^  version:/{gsub(/["'"'"' \r]/,"",$2);print $2;exit}' "$file")"
if [ "$file_version" != "$version" ]; then
  echo "info.version de $file vaut '${file_version:-?}', pas '$version' : bump app.contract.version et regénère (make contract)." >&2
  exit 1
fi

if $dry_run; then
  echo "[dry-run] Vérifications OK : le tag $tag serait créé sur $(git rev-parse --short HEAD)."
  exit 0
fi

git tag -a "$tag" -m "Contrat OpenAPI v$version"
git show --no-patch --format='%D %H' "$tag"
echo "Tag créé localement. Pour le publier : git push origin $tag"

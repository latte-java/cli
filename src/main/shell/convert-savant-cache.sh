#!/bin/bash
#
# Converts a Savant cache to a Latte cache.
#
# Steps:
#   1. Delete the Latte cache directory if it exists
#   2. Copy all files from the Savant cache to the Latte cache
#   3. Remove all .md5 checksum files
#   4. Convert Savant XML .amd files to Latte JSON format
#   5. Generate .sha256 checksum files for every artifact file
#
# Usage: convert-savant-cache.sh [savant-dir] [latte-dir]
#   savant-dir  Path to the Savant cache (default: ~/.cache/savant)
#   latte-dir   Path to the Latte cache  (default: ~/.cache/latte)
#

set -euo pipefail

SAVANT_DIR="${1:-$HOME/.cache/savant}"
LATTE_DIR="${2:-$HOME/.cache/latte}"

if [ ! -d "$SAVANT_DIR" ]; then
  echo "Savant cache not found: $SAVANT_DIR" >&2
  exit 1
fi

# Resolve the real savant path in case of symlinks
SAVANT_REAL=$(cd "$SAVANT_DIR" && pwd -P)

# --- Step 1: Delete the Latte cache if it exists -----------------------------------
if [ -L "$LATTE_DIR" ]; then
  echo "Removing symlink $LATTE_DIR"
  rm "$LATTE_DIR"
elif [ -d "$LATTE_DIR" ]; then
  echo "Removing existing Latte cache at $LATTE_DIR"
  rm -rf "$LATTE_DIR"
fi

# --- Step 2: Copy the Savant cache -------------------------------------------------
echo "Copying $SAVANT_REAL -> $LATTE_DIR"
cp -a "$SAVANT_REAL" "$LATTE_DIR"

# --- Step 3: Remove .md5 files -----------------------------------------------------
md5_count=$(find "$LATTE_DIR" -type f -name '*.md5' | wc -l | tr -d ' ')
find "$LATTE_DIR" -type f -name '*.md5' -delete
echo "Removed $md5_count .md5 files"

# --- Step 4: Convert XML .amd files to JSON ----------------------------------------
amd_count=0
fail_count=0
while IFS= read -r -d '' amd_file; do
  if python3 -c "
import sys, xml.etree.ElementTree as ET, json

tree = ET.parse(sys.argv[1])
root = tree.getroot()

licenses = []
for lic in root.findall('license'):
    entry = {'type': lic.get('type')}
    if lic.text and lic.text.strip():
        entry['text'] = lic.text.strip()
    licenses.append(entry)

dep_groups = {}
deps_el = root.find('dependencies')
if deps_el is not None:
    for group in deps_el.findall('dependency-group'):
        group_name = group.get('name')
        group_deps = []
        for dep in group.findall('dependency'):
            dep_id = ':'.join([
                dep.get('group'),
                dep.get('project'),
                dep.get('name'),
                dep.get('version'),
                dep.get('type', 'jar')
            ])
            entry = {'id': dep_id}
            exclusions = []
            for exc in dep.findall('exclusion'):
                exc_id = ':'.join(filter(None, [
                    exc.get('group'),
                    exc.get('project'),
                    exc.get('name'),
                    exc.get('type')
                ]))
                exclusions.append(exc_id)
            if exclusions:
                entry['exclusions'] = exclusions
            group_deps.append(entry)
        if group_deps:
            dep_groups[group_name] = group_deps

result = {'licenses': licenses}
if dep_groups:
    result['dependencyGroups'] = dep_groups

with open(sys.argv[1], 'w') as f:
    json.dump(result, f, indent=2)
    f.write('\n')
" "$amd_file" 2>/dev/null; then
    amd_count=$((amd_count + 1))
  else
    echo "  WARNING: failed to convert $amd_file" >&2
    fail_count=$((fail_count + 1))
  fi
done < <(find "$LATTE_DIR" -type f -name '*.amd' -print0)

echo "Converted $amd_count .amd files to JSON"
if [ "$fail_count" -gt 0 ]; then
  echo "  ($fail_count files failed conversion)"
fi

# --- Step 5: Generate .sha256 files ------------------------------------------------
sha_count=0
while IFS= read -r -d '' file; do
  shasum -a 256 "$file" | cut -d ' ' -f 1 > "${file}.sha256"
  sha_count=$((sha_count + 1))
done < <(find "$LATTE_DIR" -type f \
  ! -name '*.sha256' \
  ! -name '*.neg' \
  -print0)
echo "Generated $sha_count .sha256 files"

echo "Done. Latte cache is at $LATTE_DIR"

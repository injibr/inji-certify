#!/bin/bash
set -euo pipefail

# único diretório de trabalho
WORK_DIR=/tmp/work
mkdir -p "$WORK_DIR"

download_and_extract() {
  local url="$1"
  local dest_dir="$2"
  local temp_zip="$WORK_DIR/temp_plugin.zip"

  mkdir -p "$dest_dir"

  wget -q "$url" -O "$temp_zip"

  echo "Installation of plugins started"

  local files
  files=$(unzip -l "$temp_zip" | awk 'NR>3 {print $4}' | sed '$d')

  unzip -o -j "$temp_zip" -d "$dest_dir"

  for file in $files; do
    echo "Extracted file $file"
  done

  echo "Installation of plugins completed"

  rm -f "$temp_zip"
}

# plugin continua funcionando
if [ "${enable_certify_artifactory:-false}" = "true" ]; then
  download_and_extract \
    "${artifactory_url_env}/artifactory/libs-release-local/certify/certify-plugin.zip" \
    "${loader_path_env}"
fi

exec "$@"
#!/usr/bin/env bash
set -euo pipefail

OUT_DIR=".workflow/hubble-v2-release/feishu-downloads"
PROFILE="apache-hg"

fetch_content() {
  local token="$1"
  local format="$2"
  local detail="$3"
  local output="$4"
  local tmp

  tmp="$(mktemp)"
  lark-cli docs +fetch \
    --api-version v2 \
    --profile "${PROFILE}" \
    --as user \
    --doc "${token}" \
    --detail "${detail}" \
    --doc-format "${format}" \
    --jq '.data.document.content' > "${tmp}"
  mv "${tmp}" "${output}"
}

fetch_raw() {
  local token="$1"
  local format="$2"
  local detail="$3"
  local output="$4"
  local tmp

  tmp="$(mktemp)"
  lark-cli docs +fetch \
    --api-version v2 \
    --profile "${PROFILE}" \
    --as user \
    --doc "${token}" \
    --detail "${detail}" \
    --doc-format "${format}" > "${tmp}"
  mv "${tmp}" "${output}"
}

refresh_doc() {
  local token="$1"
  local name="$2"

  fetch_content "${token}" xml full "${OUT_DIR}/xml/${name}.xml"
  fetch_content "${token}" markdown simple "${OUT_DIR}/markdown/${name}.md"
  fetch_raw "${token}" xml full "${OUT_DIR}/raw/${name}.xml.json"
  fetch_raw "${token}" markdown simple "${OUT_DIR}/raw/${name}.md.json"
}

refresh_doc "BF9jde9W9oUNWwxh4NFcEbfnnoC" "02_subtask_i18n"
refresh_doc "BI3qdZD0boWOf6xEL2wcU0tgnAc" "03_subtask_oltp_olap"
refresh_doc "WBWNdepPFoetx2xTerjcrnDgnEd" "04_subtask_binary_compliance"
refresh_doc "Rc0bdmBNSojnRpxptdZcUNfFnwr" "05_subtask_server_api_gap"
refresh_doc "Qfbzd4AsKopO2ixngE1cVROgnff" "00_parent_task_split"

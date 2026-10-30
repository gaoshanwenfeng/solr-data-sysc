#!/usr/bin/env bash
set -euo pipefail

# 初始化 Solr core 的字段模型（幂等，可重复执行）。
# 业务字段定义与 solr/hotel/conf/managed-schema 保持一致。
#
# 用法:
#   ./scripts/init-solr.sh
#   SOLR_URL=http://localhost:8983/solr SOLR_CORE=hotel_rps_core ./scripts/init-solr.sh
#
# 说明:
# - core 不存在时使用 _default configset 创建；
# - 只通过 Schema API 补齐字段，不修改 solrconfig.xml；
#   fq_rp_prices / fq_rp_sts 仍需按 README 第 3.4 节注册；
# - 不会删除旧版脚本遗留的 city / longitude / latitude / price 字段，这些字段不再使用。

SOLR_URL="${SOLR_URL:-http://localhost:8983/solr}"
SOLR_URL="${SOLR_URL%/}"
CORE="${SOLR_CORE:-hotel_rp}"
SCHEMA_URL="${SOLR_URL}/${CORE}/schema"

echo "Solr URL: ${SOLR_URL}"
echo "Core    : ${CORE}"

# 1. 创建 core（不存在时）
if curl -fsS "${SOLR_URL}/admin/cores?action=STATUS&core=${CORE}" 2>/dev/null | grep -q "\"name\":\"${CORE}\""; then
  echo "core ${CORE} 已存在"
else
  echo "创建 core ${CORE} ..."
  curl -fsS "${SOLR_URL}/admin/cores?action=CREATE&name=${CORE}&configSet=_default" >/dev/null
  echo "core ${CORE} 创建完成"
fi

post_schema() {
  local payload="$1"
  local response
  local http_code
  response=$(curl -sS -w $'\n%{http_code}' -X POST \
    -H 'Content-Type: application/json' \
    --data-binary "$payload" \
    "${SCHEMA_URL}")
  http_code="${response##*$'\n'}"
  if [ "$http_code" != "200" ]; then
    echo "Schema 更新失败 (HTTP ${http_code}):" >&2
    echo "${response%$'\n'*}" >&2
    if echo "$response" | grep -qi "IKTokenizerFactory"; then
      echo "提示: 未加载 IK 分词器，请先按 README 第二节安装 ik-analyzer 并重启 Solr" >&2
    fi
    exit 1
  fi
}

field_exists() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' "${SCHEMA_URL}/fields/$1")" = "200" ]
}

field_type_exists() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' "${SCHEMA_URL}/fieldtypes/$1")" = "200" ]
}

# 2. 添加字段类型
if field_type_exists "text_ik"; then
  echo "字段类型 text_ik 已存在"
else
  echo "创建字段类型 text_ik ..."
  post_schema '{"add-field-type":{"name":"text_ik","class":"solr.TextField","positionIncrementGap":"100","indexAnalyzer":{"tokenizer":{"class":"org.wltea.analyzer.lucene.IKTokenizerFactory","useSmart":"false"},"filters":[{"class":"solr.LowerCaseFilterFactory"}]},"queryAnalyzer":{"tokenizer":{"class":"org.wltea.analyzer.lucene.IKTokenizerFactory","useSmart":"true"},"filters":[{"class":"solr.LowerCaseFilterFactory"}]}}}'
fi

if field_type_exists "location"; then
  echo "字段类型 location 已存在"
else
  echo "创建字段类型 location ..."
  post_schema '{"add-field-type":{"name":"location","class":"solr.LatLonPointSpatialField","docValues":true}}'
fi

# 3. 添加业务字段（id、_version_、_root_、_text_、_nest_path_ 由 _default configset 自带）
add_field() {
  local name="$1"
  local definition="$2"
  if field_exists "$name"; then
    echo "字段 ${name} 已存在"
  else
    echo "创建字段 ${name} ..."
    post_schema "{\"add-field\":${definition}}"
  fi
}

add_field rate_plan_id '{"name":"rate_plan_id","type":"string","indexed":true,"stored":true,"required":true,"multiValued":false}'
add_field hotel_id '{"name":"hotel_id","type":"string","indexed":true,"stored":true,"required":true,"multiValued":false}'
add_field name '{"name":"name","type":"text_ik","indexed":true,"stored":true,"multiValued":false}'
add_field address '{"name":"address","type":"text_ik","indexed":true,"stored":true,"multiValued":false}'
add_field city_id '{"name":"city_id","type":"string","indexed":true,"stored":true,"multiValued":false,"docValues":true}'
add_field city_name '{"name":"city_name","type":"string","indexed":true,"stored":true,"multiValued":false,"docValues":true}'
add_field location '{"name":"location","type":"location","indexed":true,"stored":true}'
add_field star '{"name":"star","type":"pint","indexed":true,"stored":true,"multiValued":false,"docValues":true}'
add_field tags '{"name":"tags","type":"string","indexed":true,"stored":true,"multiValued":true}'
add_field rate_plan_prices '{"name":"rate_plan_prices","type":"string","indexed":false,"stored":true,"multiValued":false,"docValues":true}'
add_field rate_plan_room_sts '{"name":"rate_plan_room_sts","type":"string","indexed":false,"stored":true,"multiValued":false,"docValues":true}'
add_field create_time '{"name":"create_time","type":"pdate","indexed":true,"stored":true,"multiValued":false,"docValues":true}'
add_field update_time '{"name":"update_time","type":"pdate","indexed":true,"stored":true,"multiValued":false,"docValues":true}'

echo "初始化完成。可访问 ${SCHEMA_URL}/fields 查看字段，${SCHEMA_URL}/fieldtypes/text_ik 验证 IK 字段类型。"

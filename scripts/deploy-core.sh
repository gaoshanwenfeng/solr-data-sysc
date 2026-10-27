#!/usr/bin/env bash
set -euo pipefail

# 将项目中的 solr/<core> 部署到 Solr 安装目录的 server/solr/<core>。
# 用法:
#   SOLR_INSTALL_DIR=/path/to/solr-8.0.0 ./scripts/deploy-core.sh
#   SOLR_INSTALL_DIR=/path/to/solr-8.0.0 SOLR_CORE=hotel ./scripts/deploy-core.sh
#
# 说明:
# - 只覆盖 core 的 conf 与 core.properties，不会删除已有 data 目录；
# - 目标 conf 与项目配置不同时，会先备份为 conf.bak.<timestamp>；
# - 部署后需重启 Solr 使配置生效；
# - 请先执行 scripts/install-ik.sh，否则 core 可能因找不到 IKTokenizerFactory 而加载失败。

SOLR_INSTALL_DIR="${SOLR_INSTALL_DIR:-}"
CORE="${SOLR_CORE:-hotel}"

if [ -z "$SOLR_INSTALL_DIR" ]; then
  echo "请设置 SOLR_INSTALL_DIR，例如: export SOLR_INSTALL_DIR=/opt/solr-8.0.0" >&2
  exit 1
fi
if [ ! -x "$SOLR_INSTALL_DIR/bin/solr" ]; then
  echo "未找到 $SOLR_INSTALL_DIR/bin/solr，请检查 SOLR_INSTALL_DIR" >&2
  exit 1
fi

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$PROJECT_DIR/solr/$CORE"
DEST="$SOLR_INSTALL_DIR/server/solr/$CORE"

if [ ! -f "$SRC/conf/managed-schema" ] || [ ! -f "$SRC/conf/solrconfig.xml" ]; then
  echo "未找到 $SRC/conf/managed-schema 或 solrconfig.xml" >&2
  exit 1
fi

mkdir -p "$DEST/conf"

# 已有配置且与项目不同则备份，避免覆盖用户自定义配置后无法恢复。
if [ -f "$DEST/conf/managed-schema" ] || [ -f "$DEST/conf/solrconfig.xml" ]; then
  if cmp -s "$SRC/conf/managed-schema" "$DEST/conf/managed-schema" 2>/dev/null \
     && cmp -s "$SRC/conf/solrconfig.xml" "$DEST/conf/solrconfig.xml" 2>/dev/null; then
    echo "目标 core 配置已是最新，无需覆盖"
  else
    BACKUP="$DEST/conf.bak.$(date +%Y%m%d%H%M%S)"
    cp -R "$DEST/conf" "$BACKUP"
    echo "已备份原配置到 $BACKUP"
  fi
fi

cp -R "$SRC/conf/." "$DEST/conf/"
if [ ! -f "$DEST/core.properties" ]; then
  cp "$SRC/core.properties" "$DEST/core.properties"
else
  echo "保留已有 $DEST/core.properties"
fi

IK_JAR_DIR="${IK_JAR_DIR:-$SOLR_INSTALL_DIR/server/solr-webapp/webapp/WEB-INF/lib}"
if ! ls "$IK_JAR_DIR"/ik-analyzer-*.jar >/dev/null 2>&1; then
  echo "警告: 未在 $IK_JAR_DIR 找到 ik-analyzer-*.jar" >&2
  echo "      请先执行 SOLR_INSTALL_DIR=$SOLR_INSTALL_DIR ./scripts/install-ik.sh 并重启 Solr" >&2
fi

echo "配置已部署到 $DEST"
echo "下一步:"
echo "  1. 重启 Solr（如使用自定义 SOLR_HOME/端口，请自行追加 -s/-p 参数）:"
echo "     \"$SOLR_INSTALL_DIR/bin/solr\" restart"
echo "  2. 确认 core 加载成功:"
echo "     curl 'http://localhost:8983/solr/admin/cores?action=STATUS&core=$CORE&wt=json'"
echo "  3. 如修改了字段或分词方式，按需清空旧索引并重建。"

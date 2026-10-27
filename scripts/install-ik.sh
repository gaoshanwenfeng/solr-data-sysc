#!/usr/bin/env bash
set -euo pipefail

# 安装 IK 分词器到 Solr 8.0.0（standalone 模式）。
# 用法:
#   SOLR_INSTALL_DIR=/opt/solr-8.0.0 ./scripts/install-ik.sh
#   IK_VERSION=8.0.0 SOLR_INSTALL_DIR=/opt/solr-8.0.0 ./scripts/install-ik.sh
#   （兼容旧用法: SOLR_HOME=/opt/solr-8.0.0 ./scripts/install-ik.sh）
# 可选:
#   IK_JAR_DIR 自定义 jar 目标目录
#              （默认 $SOLR_INSTALL_DIR/server/solr-webapp/webapp/WEB-INF/lib）

VERSION="${IK_VERSION:-8.0.0}"
ARTIFACT="ik-analyzer-${VERSION}.jar"
RELATIVE_PATH="com/github/magese/ik-analyzer/${VERSION}/${ARTIFACT}"

SOLR_INSTALL_DIR="${SOLR_INSTALL_DIR:-${SOLR_HOME:-}}"

if [ -z "$SOLR_INSTALL_DIR" ]; then
  echo "请先设置 SOLR_INSTALL_DIR，例如: export SOLR_INSTALL_DIR=/opt/solr-8.0.0" >&2
  exit 1
fi

DEST_DIR="${IK_JAR_DIR:-${SOLR_INSTALL_DIR}/server/solr-webapp/webapp/WEB-INF/lib}"
mkdir -p "$DEST_DIR"

if [ -f "${DEST_DIR}/${ARTIFACT}" ]; then
  echo "IK 分词器已存在: ${DEST_DIR}/${ARTIFACT}"
  exit 0
fi

MIRRORS=(
  "https://maven.aliyun.com/repository/public/${RELATIVE_PATH}"
  "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/${RELATIVE_PATH}"
  "https://repo1.maven.org/maven2/${RELATIVE_PATH}"
)

for url in "${MIRRORS[@]}"; do
  echo "尝试下载: ${url}"
  if curl -fSL --connect-timeout 10 -o "${DEST_DIR}/${ARTIFACT}.tmp" "$url"; then
    mv "${DEST_DIR}/${ARTIFACT}.tmp" "${DEST_DIR}/${ARTIFACT}"
    echo "已安装: ${DEST_DIR}/${ARTIFACT}"
    echo "请重启 Solr 使 IK 分词器生效，例如: \"$SOLR_INSTALL_DIR/bin/solr\" restart"
    exit 0
  fi
  rm -f "${DEST_DIR}/${ARTIFACT}.tmp"
done

echo "下载失败，请手动下载 ${ARTIFACT} 并放入 ${DEST_DIR}" >&2
exit 1

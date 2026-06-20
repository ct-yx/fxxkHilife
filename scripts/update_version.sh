#!/bin/bash
# ============================================
# fxxkHilife 版本号统一升级脚本
# 用法: ./scripts/update_version.sh <versionCode> <versionName>
# 例如: ./scripts/update_version.sh 6 "v1.6.0"
# ============================================
# 注意: 执行后需手动追加 DEVELOPMENT_LOG.md 记录
# ============================================

set -e

VERSION_CODE=$1
VERSION_NAME=$2

if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
    echo "❌ 用法: $0 <versionCode> <versionName>"
    echo "   例如: $0 6 \"v1.6.0\""
    exit 1
fi

echo "🔧 正在更新版本号至 $VERSION_NAME (versionCode=$VERSION_CODE)..."

# 1. build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" app/build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"$VERSION_NAME\"/" app/build.gradle.kts
echo "  ✅ app/build.gradle.kts"

# 2. strings.xml (EN)
sed -i "s|<string name=\"version_name\">.*</string>|<string name=\"version_name\">$VERSION_NAME</string>|" \
    app/src/main/res/values/strings.xml
echo "  ✅ values/strings.xml"

# 3. strings.xml (ZH)
sed -i "s|<string name=\"version_name\">.*</string>|<string name=\"version_name\">$VERSION_NAME</string>|" \
    app/src/main/res/values-zh-rCN/strings.xml
echo "  ✅ values-zh-rCN/strings.xml"

# 4. README.md (中文)
sed -i "s/当前版本：\*\*v[0-9.]*\*\*/当前版本：\*\*$VERSION_NAME\*\*/" README.md
echo "  ✅ README.md"

# 5. README_EN.md (英文)
sed -i "s/Current version: \*\*v[0-9.]*\*\*/Current version: \*\*$VERSION_NAME\*\*/" README_EN.md
echo "  ✅ README_EN.md"

echo ""
echo "🎉 所有版本号已更新至 $VERSION_NAME (versionCode=$VERSION_CODE)"
echo "⚠️  请手动追加 DEVELOPMENT_LOG.md 变更记录！"
echo ""
echo "建议记录模板："
echo "---"
echo "## $(date +%Y-%m-%d)"
echo ""
echo "**${VERSION_NAME} Release — <变更简述>**"
echo ""
echo "- <变更说明>"
echo "- Version bump: versionCode **<旧>→${VERSION_CODE}**, versionName **\"<旧>\"→\"${VERSION_NAME}\"**"
echo "✅ completed"

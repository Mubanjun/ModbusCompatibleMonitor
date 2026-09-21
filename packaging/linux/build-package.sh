#!/usr/bin/env bash
# ============================================================================
# JDRK 水质监控平台 · Linux 打包脚本（生成 Debian .deb / RPM .rpm）
#
# 用法：
#   ./packaging/linux/build-package.sh                     # 构建后端+前端，生成 deb 与 rpm
#   ./packaging/linux/build-package.sh --formats deb       # 只生成 .deb
#   ./packaging/linux/build-package.sh --no-frontend       # 只打包后端（产物无 Qt 依赖）
#   ./packaging/linux/build-package.sh --skip-build        # 复用已有构建产物，仅重新打包
#   ./packaging/linux/build-package.sh --stage-only        # 只生成 staging 目录（排错用）
#   ./packaging/linux/build-package.sh --version 0.2.0 --release 2 [--deb-depends "包名列表"]
#
# 构建依赖（Debian / Ubuntu）：
#   sudo apt install build-essential pkg-config dpkg-dev rpm \
#                    qt6-base-dev qt6-declarative-dev qml6-module-qtquick-controls
#   （Rust：https://rustup.rs；Qt 仅打包前端时需要，可用 --no-frontend 跳过）
#
# 构建依赖（Fedora / RHEL / openSUSE）：
#   sudo dnf install gcc-c++ make pkgconf rpm-build qt6-qtbase-devel \
#                    qt6-qtdeclarative-devel qt6-qtquickcontrols2
#
# 产物：
#   packaging/dist/jdrk-monitor_<version>_<arch>.deb
#   packaging/dist/jdrk-monitor-<version>-<release>.<dist>.rpm
# ============================================================================
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

PKG_NAME="jdrk-monitor"
PKG_SUMMARY="JDRK 水质监控平台（RS-QXZ-M / RS-XJZ Modbus 采集服务）"
PKG_DESC="JDRK 水质监控平台：RS-QXZ-M / RS-XJZ 环境监控主机的 Modbus 采集服务与 Qt6 上位机；后端负责轮询采集、SQLite 本地缓冲、MySQL 回传与 REST/WebSocket 接口，前端为可选的 Qt6 QML 图形界面（可连接本机或远端后端）。"
PKG_URL="https://github.com/Mubanjun/ModbusCompatibleMonitor"
PKG_LICENSE="MIT"
MAINTAINER="${JDRK_MAINTAINER:-JDRK <jdrk@example.com>}"

# Qt6 运行时依赖（仅在有前端时加入）
DEB_QT_DEPENDS="libqt6core6, libqt6gui6, libqt6qml6, libqt6quick6, libqt6network6, libqt6quickcontrols2-6, qml6-module-qtquick, qml6-module-qtquick-controls, qml6-module-qtquick-layouts, qml6-module-qtquick-templates, qml6-module-qtqml-workerscript"
RPM_QT_REQUIRES="qt6-qtbase, qt6-qtdeclarative, qt6-qtquickcontrols2"

FORMATS="deb,rpm"
PREFIX="/usr"
JOBS="$( (nproc 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4) )"
VERSION=""
RELEASE="1"
WITH_FRONTEND=1
DEB_DEPENDS_OVERRIDE=""
SKIP_BUILD=0
STAGE_ONLY=0
OUTDIR=""
BUILD_DIR=""
STAGE=""

log()  { printf '\033[1;34m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"; }
warn() { printf '\033[1;33m[warn] \033[0m%s\n' "$*" >&2; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }
trap 'die "脚本在第 $LINENO 行失败"' ERR

usage() { sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

# ---------------------------------------------------------------- 参数解析
while [ $# -gt 0 ]; do
  case "$1" in
    -f|--formats)   FORMATS="$2"; shift 2 ;;
    --version)      VERSION="$2"; shift 2 ;;
    --release)      RELEASE="$2"; shift 2 ;;
    --prefix)       PREFIX="$2"; shift 2 ;;
    --outdir)       OUTDIR="$2"; shift 2 ;;
    --jobs|-j)      JOBS="$2"; shift 2 ;;
    --deb-depends)  DEB_DEPENDS_OVERRIDE="$2"; shift 2 ;;
    --no-frontend)  WITH_FRONTEND=0; shift ;;
    --skip-build)   SKIP_BUILD=1; shift ;;
    --stage-only)   STAGE_ONLY=1; shift ;;
    -h|--help)      usage ;;
    *) die "未知参数：$1（用 --help 查看用法）" ;;
  esac
done

OUTDIR="${OUTDIR:-$REPO_ROOT/packaging/dist}"
BUILD_DIR="$REPO_ROOT/packaging/build"
STAGE="$BUILD_DIR/stage"
mkdir -p "$OUTDIR" "$BUILD_DIR"

[ "$(id -u)" -eq 0 ] && warn "以 root 运行打包脚本不是必须的（除非需要安装依赖）"

# ---------------------------------------------------------------- 工具函数
read_version() {
  sed -n 's/^version[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$REPO_ROOT/backend/Cargo.toml" | head -n1
}

detect_deb_arch() {
  if command -v dpkg >/dev/null 2>&1; then
    dpkg --print-architecture
  else
    case "$(uname -m)" in
      x86_64|amd64) echo amd64 ;;
      aarch64|arm64) echo arm64 ;;
      armv7l) echo armhf ;;
      *) echo "$(uname -m)" ;;
    esac
  fi
}

detect_rpm_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo x86_64 ;;
    aarch64|arm64) echo aarch64 ;;
    *) uname -m ;;
  esac
}

find_qmake() {
  local c
  for c in qmake6 qmake-qt6 qmake; do
    if command -v "$c" >/dev/null 2>&1; then command -v "$c"; return 0; fi
  done
  return 1
}

has_format() {
  case ",$FORMATS," in *",$1,"*) return 0 ;; *) return 1 ;; esac
}

# ---------------------------------------------------------------- 版本
if [ -z "$VERSION" ]; then
  VERSION="$(read_version)"
fi
[ -n "$VERSION" ] || die "无法从 backend/Cargo.toml 读取版本号，请用 --version 指定"
DEB_ARCH="$(detect_deb_arch)"
RPM_ARCH="$(detect_rpm_arch)"

log "包名 $PKG_NAME  版本 $VERSION-$RELEASE  架构 deb=$DEB_ARCH rpm=$RPM_ARCH"
log "仓库 $REPO_ROOT"
log "格式 $FORMATS  前端 $([ "$WITH_FRONTEND" = 1 ] && echo 打包 || echo 跳过)  jobs=$JOBS"

# ---------------------------------------------------------------- 编译
BACKEND_BIN="$REPO_ROOT/backend/target/release/jdrk-monitor"
UI_BIN="$BUILD_DIR/frontend/jdrk-monitor-ui"

if [ "$SKIP_BUILD" = 0 ]; then
  command -v cargo >/dev/null 2>&1 || die "未找到 cargo，请先安装 Rust（https://rustup.rs）"
  log "编译后端（release，首次编译较慢）…"
  cargo build --release --manifest-path "$REPO_ROOT/backend/Cargo.toml"
else
  log "跳过编译，复用已有产物"
fi
[ -x "$BACKEND_BIN" ] || die "后端产物不存在：$BACKEND_BIN（先去掉 --skip-build）"

if [ "$WITH_FRONTEND" = 1 ]; then
  if [ "$SKIP_BUILD" = 0 ]; then
    QMAKE="$(find_qmake)" || die "未找到 qmake6/qmake，请安装 Qt6 开发包，或用 --no-frontend 只打包后端"
    log "编译前端（qmake=$QMAKE）…"
    mkdir -p "$BUILD_DIR/frontend"
    (
      cd "$BUILD_DIR/frontend"
      "$QMAKE" "$REPO_ROOT/frontend/jdrk-frontend.pro"
      make -j"$JOBS"
    )
  fi
  [ -x "$UI_BIN" ] || die "前端产物不存在：$UI_BIN（先去掉 --skip-build）"
fi

# ---------------------------------------------------------------- 生成配置
# 从 backend/config/default.toml 派生安装用配置（单一数据源，避免两份配置漂移）
gen_config() {
  local target="$1" bind="$2" sqlite="$3" port1="$4" port2="$5"
  {
    cat <<EOF
# ============================================================
# JDRK 水质监控平台 · 安装配置（conffile，升级时不会被覆盖）
#
# 本文件由 packaging/linux/build-package.sh 依据 backend/config/default.toml 生成。
# 修改后执行：sudo systemctl restart jdrk-monitor
# 说明文档：/usr/share/doc/$PKG_NAME/后端API与WebSocket协议.md
# ============================================================
EOF
    sed -e "s|^bind = .*|bind = \"$bind\"|" \
        -e "s|^sqlite_path = .*|sqlite_path = \"$sqlite\"|" \
        -e "s|^port = \"COM3\"|port = \"$port1\"|" \
        -e "s|^port = \"COM4\"|port = \"$port2\"|" \
        "$REPO_ROOT/backend/config/default.toml"
  } > "$target"
}

# ---------------------------------------------------------------- staging
log "生成 staging 目录 $STAGE"
rm -rf "$STAGE"
mkdir -p \
  "$STAGE$PREFIX/bin" \
  "$STAGE$PREFIX/lib/systemd/system" \
  "$STAGE$PREFIX/share/applications" \
  "$STAGE$PREFIX/share/doc/$PKG_NAME" \
  "$STAGE$PREFIX/share/$PKG_NAME/examples" \
  "$STAGE/etc/$PKG_NAME"

install -m 0755 "$BACKEND_BIN" "$STAGE$PREFIX/bin/jdrk-monitor"
if [ "$WITH_FRONTEND" = 1 ]; then
  install -m 0755 "$UI_BIN" "$STAGE$PREFIX/bin/jdrk-monitor-ui"
fi

# 服务默认监听 0.0.0.0，便于上位机远程连接；仅本机使用请改回 127.0.0.1:8790
gen_config "$STAGE/etc/$PKG_NAME/config.toml" \
           "0.0.0.0:8790" \
           "/var/lib/$PKG_NAME/monitor.db" \
           "/dev/ttyUSB0" \
           "/dev/ttyUSB1"

install -m 0644 "$SCRIPT_DIR/jdrk-monitor.service" "$STAGE$PREFIX/lib/systemd/system/jdrk-monitor.service"
if [ "$WITH_FRONTEND" = 1 ]; then
  install -m 0644 "$SCRIPT_DIR/jdrk-monitor.desktop" "$STAGE$PREFIX/share/applications/jdrk-monitor.desktop"
fi

# 文档与示例配置
for f in "$REPO_ROOT/README.md" "$REPO_ROOT"/docs/*.md; do
  [ -f "$f" ] && install -m 0644 "$f" "$STAGE$PREFIX/share/doc/$PKG_NAME/"
done
install -m 0644 "$SCRIPT_DIR/../README.md" "$STAGE$PREFIX/share/doc/$PKG_NAME/部署说明.md"
[ -f "$REPO_ROOT/LICENSE" ] && install -m 0644 "$REPO_ROOT/LICENSE" "$STAGE$PREFIX/share/doc/$PKG_NAME/copyright"
install -m 0644 "$REPO_ROOT"/backend/config/*.toml "$STAGE$PREFIX/share/$PKG_NAME/examples/"

if [ "$STAGE_ONLY" = 1 ]; then
  log "仅生成 staging：$STAGE"
  find "$STAGE" -type f | sed "s|$STAGE||" | sort
  exit 0
fi

# ---------------------------------------------------------------- .deb
build_deb() {
  command -v dpkg-deb >/dev/null 2>&1 || die "未找到 dpkg-deb（Debian 系安装 dpkg-dev / dpkg）"
  local debdir="$BUILD_DIR/deb/${PKG_NAME}_${VERSION}_${DEB_ARCH}"
  local depends="libc6, libgcc-s1, libstdc++6"
  if [ "$WITH_FRONTEND" = 1 ]; then depends="$depends, $DEB_QT_DEPENDS"; fi
  if [ -n "$DEB_DEPENDS_OVERRIDE" ]; then
    warn "使用 --deb-depends 覆盖依赖列表"
    depends="$DEB_DEPENDS_OVERRIDE"
  fi

  # 依赖名自检：各发行版的 Qt6 包名不完全一致，装不上时能第一时间看到提示
  if command -v apt-cache >/dev/null 2>&1; then
    local p _pkgs
    IFS=',' read -r -a _pkgs <<< "$depends"
    for p in "${_pkgs[@]}"; do
      p="$(printf '%s' "$p" | tr -d ' ')"
      [ -n "$p" ] || continue
      if ! apt-cache show "$p" >/dev/null 2>&1; then
        warn "依赖包名在当前 apt 源中不存在：$p（可用 --deb-depends 覆盖整份依赖列表）"
      fi
    done
  fi

  log "构建 .deb（$DEB_ARCH）…"
  rm -rf "$debdir"
  mkdir -p "$debdir/DEBIAN"
  cp -a "$STAGE/." "$debdir/"

  sed -e "s|@VERSION@|$VERSION|g" \
      -e "s|@ARCH@|$DEB_ARCH|g" \
      -e "s|@MAINTAINER@|$MAINTAINER|g" \
      -e "s|@DEPENDS@|$depends|g" \
      -e "s|@SUMMARY@|$PKG_SUMMARY|g" \
      -e "s|@DESC@|$PKG_DESC|g" \
      -e "s|@URL@|$PKG_URL|g" \
      -e "s|@LICENSE@|$PKG_LICENSE|g" \
      "$SCRIPT_DIR/deb/control.in" > "$debdir/DEBIAN/control"

  install -m 0755 "$SCRIPT_DIR/deb/postinst.sh" "$debdir/DEBIAN/postinst"
  install -m 0755 "$SCRIPT_DIR/deb/prerm.sh"    "$debdir/DEBIAN/prerm"
  install -m 0755 "$SCRIPT_DIR/deb/postrm.sh"   "$debdir/DEBIAN/postrm"
  printf '/etc/%s/config.toml\n' "$PKG_NAME" > "$debdir/DEBIAN/conffiles"
  if command -v md5sum >/dev/null 2>&1; then
    ( cd "$debdir" && find . -path ./DEBIAN -prune -o -type f -print0 \
        | xargs -0 md5sum > DEBIAN/md5sums 2>/dev/null || rm -f DEBIAN/md5sums )
  fi

  local out="$OUTDIR/${PKG_NAME}_${VERSION}_${DEB_ARCH}.deb"
  dpkg-deb --root-owner-group --build "$debdir" "$out" >/dev/null
  log "生成 $out（$(du -h "$out" | cut -f1)）"
}

# ---------------------------------------------------------------- .rpm
build_rpm() {
  command -v rpmbuild >/dev/null 2>&1 || die "未找到 rpmbuild（RedHat 系安装 rpm-build，Debian 系安装 rpm）"
  local rtop="$BUILD_DIR/rpm"
  local spec="$rtop/SPECS/$PKG_NAME.spec"
  local qt_requires=""
  if [ "$WITH_FRONTEND" = 1 ]; then
    qt_requires="Requires: $RPM_QT_REQUIRES"
    cat > "$rtop/ui_files.inc" <<'EOS'
%{_bindir}/jdrk-monitor-ui
%{_datadir}/applications/jdrk-monitor.desktop
EOS
  else
    : > "$rtop/ui_files.inc"
  fi

  log "构建 .rpm（$RPM_ARCH）…"
  rm -rf "$rtop"
  mkdir -p "$rtop/BUILD" "$rtop/SPECS" "$rtop/BUILDROOT" "$rtop/RPMS" "$rtop/SRPMS"

  sed -e "s|@VERSION@|$VERSION|g" \
      -e "s|@RELEASE@|$RELEASE|g" \
      -e "s|@SUMMARY@|$PKG_SUMMARY|g" \
      -e "s|@DESC@|$PKG_DESC|g" \
      -e "s|@URL@|$PKG_URL|g" \
      -e "s|@LICENSE@|$PKG_LICENSE|g" \
      -e "s|@PREFIX@|$PREFIX|g" \
      -e "s|@QT_REQUIRES@|$qt_requires|g" \
      "$SCRIPT_DIR/rpm/$PKG_NAME.spec.in" > "$spec"

  # 多行替换：UI 文件清单（从 include 文件读入，避免 awk -v 传多行）
  awk -v inc="$rtop/ui_files.inc" \
    '{ if ($0 == "@UI_FILES@") { while ((getline line < inc) > 0) print line } else print }' \
    "$spec" > "$spec.tmp"
  mv "$spec.tmp" "$spec"

  rpmbuild -bb "$spec" \
    --define "_topdir $rtop" \
    --define "_jdrk_stage $STAGE" \
    --define "debug_package %{nil}" \
    --target "$RPM_ARCH" 2>&1 | tail -n 25

  local rpmfile
  rpmfile="$(find "$rtop/RPMS" -name '*.rpm' | head -n1)"
  [ -n "$rpmfile" ] || die "rpm 构建失败，未找到产物"
  cp -f "$rpmfile" "$OUTDIR/"
  log "生成 $OUTDIR/$(basename "$rpmfile")（$(du -h "$OUTDIR/$(basename "$rpmfile")" | cut -f1)）"
}

if has_format deb; then build_deb; else log "跳过 .deb"; fi
if has_format rpm; then build_rpm; else log "跳过 .rpm"; fi

log "全部完成，产物在 $OUTDIR"
ls -1 "$OUTDIR" | sed 's/^/  /'

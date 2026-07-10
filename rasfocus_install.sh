#!/data/data/com.termux/files/usr/bin/bash
# ─────────────────────────────────────────────
# RasFocus Latest APK Downloader & Installer
# GitHub: raseledutools/RasFocus-final
# শুধু full-armeabi-v7a APK
# ─────────────────────────────────────────────

REPO="raseledutools/RasFocus-final"
API="https://api.github.com/repos/$REPO/releases/latest"
APK_PATH="$HOME/storage/downloads/RasFocus-latest.apk"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}"
echo "╔══════════════════════════════════════╗"
echo "║     RasFocus APK Installer           ║"
echo "╚══════════════════════════════════════╝"
echo -e "${NC}"

if ! command -v curl &>/dev/null; then
    echo -e "${YELLOW}[*] curl install হচ্ছে...${NC}"
    pkg install -y curl
fi

if [ ! -d "$HOME/storage/downloads" ]; then
    echo -e "${YELLOW}[*] Storage permission দরকার...${NC}"
    termux-setup-storage
    sleep 3
fi

echo -e "${YELLOW}[*] GitHub থেকে latest release খুঁজছি...${NC}"

RELEASE_JSON=$(curl -s "$API")

if echo "$RELEASE_JSON" | grep -q "rate limit"; then
    echo -e "${RED}[!] GitHub API rate limit — একটু পরে আবার চেষ্টা করো${NC}"
    exit 1
fi

TAG=$(echo "$RELEASE_JSON" | python3 -c "import json,sys; r=json.load(sys.stdin); print(r['tag_name'])" 2>/dev/null)

if [ -z "$TAG" ]; then
    echo -e "${RED}[!] Release পাওয়া যায়নি${NC}"
    exit 1
fi

echo -e "${GREEN}[✓] Latest release: ${TAG}${NC}"

APK_URL=$(echo "$RELEASE_JSON" | python3 -c "
import json, sys
r = json.load(sys.stdin)
for a in r.get('assets', []):
    if 'armeabi-v7a' in a['name'] and 'full' in a['name'].lower():
        print(a['browser_download_url'])
        sys.exit(0)
sys.exit(1)
" 2>/dev/null)

if [ -z "$APK_URL" ]; then
    echo -e "${RED}[!] full-armeabi-v7a APK পাওয়া যায়নি${NC}"
    exit 1
fi

if [ -f "$APK_PATH" ]; then
    rm -f "$APK_PATH"
    echo -e "${YELLOW}[*] আগের APK মুছে ফেলা হয়েছে${NC}"
fi

echo -e "${YELLOW}[*] Download হচ্ছে → Downloads/RasFocus-latest.apk${NC}"
echo ""

curl -L --progress-bar -o "$APK_PATH" "$APK_URL"

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}[!] Download failed${NC}"
    exit 1
fi

SIZE=$(du -h "$APK_PATH" | cut -f1)
echo -e "\n${GREEN}[✓] Download সম্পন্ন: ${SIZE}${NC}"
echo -e "${YELLOW}[*] Install করা হচ্ছে...${NC}"

if ! command -v termux-open &>/dev/null; then
    pkg install -y termux-tools
fi

termux-open "$APK_PATH"

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  RasFocus ${TAG} — Install চাপো!${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# ── Alias setup (প্রথমবার চালালে auto-add হবে) ──────────────────────
BASHRC="$HOME/.bashrc"
ALIAS_LINE="alias d='bash ~/rasfocus_install.sh'"

if ! grep -qF "alias d=" "$BASHRC" 2>/dev/null; then
    echo "" >> "$BASHRC"
    echo "# RasFocus quick install" >> "$BASHRC"
    echo "$ALIAS_LINE" >> "$BASHRC"
    echo -e "${GREEN}[✓] Alias 'd' সেট হয়েছে — পরের বার শুধু 'd' লিখলেই হবে${NC}"
fi

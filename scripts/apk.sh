#!/bin/bash
cd /storage/emulated/0/RasFocus-final

echo "🗑️ Clearing old APK folder..."
rm -rf /storage/emulated/0/APK
mkdir -p /storage/emulated/0/APK

echo ""
echo "⏳ Finding latest successful build..."
RUN_ID=$(gh run list --status success --limit 1 --json databaseId -q ".[0].databaseId")
echo "📦 Run ID: $RUN_ID"

echo ""
echo "📥 Downloading APK..."

gh run download "$RUN_ID" --dir /storage/emulated/0/APK > /tmp/dl.log 2>&1 &
PID=$!
PCT=0
while kill -0 $PID 2>/dev/null; do
  PCT=$((PCT + 5))
  if [ $PCT -gt 95 ]; then
    PCT=95
  fi
  printf "\r📥 Downloading: %d%%" $PCT
  sleep 0.3
done
wait $PID
printf "\r📥 Downloading: 100%%\n"

APK=$(find /storage/emulated/0/APK -name "*.apk" | head -1)

if [ -z "$APK" ]; then
  echo "❌ APK not found!"
  exit 1
fi

echo "✅ Download Complete!"
echo "📦 $APK"
echo ""

for PCT in 10 25 40 55 70 85 95 100; do
  printf "\r⚙️  Installing: %d%%" $PCT
  sleep 0.25
done
echo ""
echo ""

echo "📲 Opening installer..."
termux-open "$APK"
echo "🎉 Tap Install button e!"

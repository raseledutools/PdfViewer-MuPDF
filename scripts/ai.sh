#!/bin/bash
cd /storage/emulated/0/RasFocus-final

HISTORY_FILE="/tmp/ai_history.json"
echo "[]" > "$HISTORY_FILE"

clear
echo "╔════════════════════════════════════╗"
echo "║       🤖 AI Assistant Ready          ║"
echo "║   Bangla/English likho, 'exit' likhe ║"
echo "║         beriye jao                   ║"
echo "╚════════════════════════════════════╝"
echo ""

while true; do
  echo -n "👤 You: "
  read -r MSG

  if [ "$MSG" = "exit" ] || [ "$MSG" = "quit" ]; then
    echo "👋 Bye!"
    break
  fi

  if [ -z "$MSG" ]; then
    continue
  fi

  # Add user message to history
  python3 -c "
import json
with open('$HISTORY_FILE') as f:
    h = json.load(f)
h.append({'role': 'user', 'content': '''$MSG'''})
with open('$HISTORY_FILE', 'w') as f:
    json.dump(h, f)
"

  echo "⏳ Thinking..."

  MESSAGES=$(python3 -c "
import json
with open('$HISTORY_FILE') as f:
    h = json.load(f)
system = {'role': 'system', 'content': 'You are a friendly Android developer assistant. You understand both Bangla and English fluently. Always reply in the same language the user writes in. Be warm, helpful, and concise.'}
print(json.dumps([system] + h))
")

  RESPONSE=$(curl -s https://models.inference.ai.azure.com/chat/completions \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"model\":\"gpt-4o-mini\",\"messages\":$MESSAGES}" \
    | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d['choices'][0]['message']['content'])
except Exception as e:
    print('ERROR: ' + str(e))
")

  echo -e "\r🤖 AI: $RESPONSE"
  echo ""

  # Add AI response to history
  python3 -c "
import json
with open('$HISTORY_FILE') as f:
    h = json.load(f)
h.append({'role': 'assistant', 'content': '''$RESPONSE'''})
with open('$HISTORY_FILE', 'w') as f:
    json.dump(h, f)
"

  # Save code blocks if any
  CODE=$(echo "$RESPONSE" | sed -n '/^```/,/^```$/p' | grep -v '^```')
  if [ -n "$CODE" ]; then
    echo "$CODE" > ai_output.txt
    echo "💾 Code saved to: ai_output.txt"
    echo ""
  fi
done

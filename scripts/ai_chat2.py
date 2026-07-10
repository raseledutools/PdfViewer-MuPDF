#!/usr/bin/env python3
import sys, os, json, urllib.request, urllib.error

API_KEY = os.environ.get("GROQ_API_KEY")
MODEL_NAME = "meta-llama/llama-4-scout-17b-16e-instruct"
API_URL = "https://api.groq.com/openai/v1/chat/completions"

def ask_groq(question: str) -> str:
    if not API_KEY:
        return "❌ GROQ_API_KEY পাওয়া যায়নাই।"
    payload = {"model": MODEL_NAME, "messages": [{"role": "user", "content": question}]}
    req = urllib.request.Request(API_URL, data=json.dumps(payload).encode("utf-8"), headers={"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))["choices"][0]["message"]["content"].strip()
    except urllib.error.HTTPError as e:
        return f"❌ Groq API error ({e.code}): {e.read().decode('utf-8', errors='ignore')}"
    except Exception as e:
        return f"❌ Error: {e}"

def main():
    if len(sys.argv) < 2:
        print("Usage: help \"your question here\"")
        sys.exit(1)
    print("⏳ Groq thinking...")
    print("\n🤖 Groq AI:\n" + ask_groq(" ".join(sys.argv[1:])))

if __name__ == "__main__":
    main()

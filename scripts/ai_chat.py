import json
import os
import re
import subprocess
import urllib.request

HISTORY_FILE = os.path.expanduser("~/ai_history.json")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
CODE_DIR = "/storage/emulated/0/AI-Code"

# Maps language hints (from ```lang blocks) to a canonical runner key
LANG_MAP = {
    "python": "python", "py": "python",
    "bash": "bash", "sh": "bash", "shell": "bash",
    "javascript": "javascript", "js": "javascript", "node": "javascript",
    "java": "java",
    "kotlin": "kotlin", "kt": "kotlin",
    "c++": "cpp", "cpp": "cpp", "cc": "cpp",
    "c": "c",
    "go": "go", "golang": "go",
    "rust": "rust", "rs": "rust",
    "ruby": "ruby", "rb": "ruby",
    "php": "php",
    "html": "html_open",
}

EXT_MAP = {
    "python": "py", "bash": "sh", "javascript": "js", "java": "java",
    "kotlin": "kt", "cpp": "cpp", "c": "c", "go": "go", "rust": "rs",
    "ruby": "rb", "php": "php",
    "html_open": "html",
}

# Saved with correct extension but no run/open support
EXT_ONLY = {"json": "json", "xml": "xml", "css": "css"}

# pkg packages needed in Termux for each runner, shown if the tool is missing
INSTALL_HINT = {
    "cpp": "pkg install clang", "c": "pkg install clang",
    "java": "pkg install openjdk-17", "kotlin": "pkg install kotlin",
    "go": "pkg install golang", "rust": "pkg install rust",
    "ruby": "pkg install ruby", "php": "pkg install php",
    "javascript": "pkg install nodejs",
}


def load_history():
    if os.path.exists(HISTORY_FILE):
        with open(HISTORY_FILE) as f:
            return json.load(f)
    return []


def save_history(h):
    with open(HISTORY_FILE, "w") as f:
        json.dump(h, f)


def ask_ai(history):
    system = {"role": "system", "content": "You are a friendly Android developer assistant. You understand both Bangla and English fluently. Always reply in the same language the user writes in. Be warm, helpful, and concise."}
    messages = [system] + history

    data = json.dumps({"model": "gpt-4o-mini", "messages": messages}).encode("utf-8")
    req = urllib.request.Request(
        "https://models.inference.ai.azure.com/chat/completions",
        data=data,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json"
        }
    )
    try:
        with urllib.request.urlopen(req) as res:
            result = json.loads(res.read().decode("utf-8"))
            return result["choices"][0]["message"]["content"]
    except Exception as e:
        try:
            err_body = e.read().decode("utf-8")
            return f"ERROR: {err_body}"
        except Exception:
            return f"ERROR: {str(e)}"


def detect_lang(lang_hint):
    hint = (lang_hint or "").lower().strip()
    if hint in LANG_MAP:
        key = LANG_MAP[hint]
        return EXT_MAP[key], key
    if hint in EXT_ONLY:
        return EXT_ONLY[hint], None
    return "txt", None


def java_class_name(code, default="Main"):
    m = re.search(r"public\s+class\s+(\w+)", code)
    return m.group(1) if m else default


def copy_to_clipboard(text):
    """Copy text straight to the Android clipboard, no manual select/copy needed."""
    try:
        subprocess.run(["termux-clipboard-set"], input=text.encode(), check=True, timeout=3)
        return True
    except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return False


def run_code(lang, filepath, code):
    """Compile/open/run code, confirmed with a single keypress."""
    if lang == "html_open":
        choice = input("   👉 Browser e open korbo? (Enter = open, n = skip): ").strip().lower()
        if choice == "n":
            return
        try:
            subprocess.run(["termux-open", filepath])
        except FileNotFoundError:
            print("⚠️  termux-open pawa jay nai. Install koro: pkg install termux-tools")
        print()
        return

    choice = input("   👉 Run now? (Enter = run, n = skip): ").strip().lower()
    if choice == "n":
        return

    folder = os.path.dirname(filepath)
    base = os.path.splitext(os.path.basename(filepath))[0]

    try:
        if lang == "python":
            cmd = ["python3", filepath]
        elif lang == "bash":
            cmd = ["bash", filepath]
        elif lang == "javascript":
            cmd = ["node", filepath]
        elif lang == "ruby":
            cmd = ["ruby", filepath]
        elif lang == "php":
            cmd = ["php", filepath]
        elif lang == "go":
            cmd = ["go", "run", filepath]
        elif lang == "c":
            binpath = os.path.join(folder, base)
            print(f"\n$ gcc {filepath} -o {binpath}")
            subprocess.run(["gcc", filepath, "-o", binpath], check=True)
            cmd = [binpath]
        elif lang == "cpp":
            binpath = os.path.join(folder, base)
            print(f"\n$ g++ {filepath} -o {binpath}")
            subprocess.run(["g++", filepath, "-o", binpath], check=True)
            cmd = [binpath]
        elif lang == "java":
            classname = java_class_name(code)
            javafile = os.path.join(folder, f"{classname}.java")
            if javafile != filepath:
                os.rename(filepath, javafile)
                filepath = javafile
            print(f"\n$ javac {filepath}")
            subprocess.run(["javac", filepath], check=True)
            cmd = ["java", "-cp", folder, classname]
        elif lang == "kotlin":
            jarpath = os.path.join(folder, f"{base}.jar")
            print(f"\n$ kotlinc {filepath} -include-runtime -d {jarpath}")
            subprocess.run(["kotlinc", filepath, "-include-runtime", "-d", jarpath], check=True)
            cmd = ["java", "-jar", jarpath]
        elif lang == "rust":
            binpath = os.path.join(folder, base)
            print(f"\n$ rustc {filepath} -o {binpath}")
            subprocess.run(["rustc", filepath, "-o", binpath], check=True)
            cmd = [binpath]
        else:
            print(f"⚠️  '{lang}' er jonno run support nei.")
            return

        print(f"\n$ {' '.join(cmd)}\n")
        subprocess.run(cmd)

    except FileNotFoundError:
        hint = INSTALL_HINT.get(lang, "")
        print(f"⚠️  '{lang}' er compiler/runner pawa jay nai.")
        if hint:
            print(f"   Install koro: {hint}")
    except subprocess.CalledProcessError:
        print("⚠️  Compile/run e error hoyeche — code ta check koro.")
    except Exception as e:
        print(f"⚠️  Run failed: {e}")
    print()


def main():
    os.makedirs(CODE_DIR, exist_ok=True)
    history = []
    save_history(history)
    counter = 1

    print("╔════════════════════════════════════╗")
    print("║       🤖 AI Assistant Ready          ║")
    print("║   Bangla/English likho, 'exit' likhe ║")
    print("║         beriye jao                   ║")
    print("╚════════════════════════════════════╝")
    print()

    while True:
        try:
            msg = input("👤 You: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 Bye!")
            break

        if msg.lower() in ("exit", "quit"):
            print("👋 Bye!")
            break
        if not msg:
            continue

        history.append({"role": "user", "content": msg})
        print("⏳ Thinking...")

        reply = ask_ai(history)
        print(f"\n🤖 AI: {reply}\n")

        history.append({"role": "assistant", "content": reply})
        save_history(history)

        # Find code blocks with language hints
        code_blocks = re.findall(r"```(\w*)\n(.*?)```", reply, re.DOTALL)
        if code_blocks:
            for lang_hint, code in code_blocks:
                code = code.strip()
                ext, lang = detect_lang(lang_hint)
                filename = f"code_{counter}.{ext}"
                filepath = os.path.join(CODE_DIR, filename)
                with open(filepath, "w") as f:
                    f.write(code)

                print(f"💾 Saved: {filepath}")
                if copy_to_clipboard(code):
                    print("📋 Clipboard e copy hoye geche — shudhu paste koro!")
                else:
                    print(f"📋 Copy command: cat {filepath}")
                    print("   (Auto-copy korte: pkg install termux-api, + Termux:API app install koro)")

                if lang:
                    run_code(lang, filepath, code)

                print()
                counter += 1


if __name__ == "__main__":
    main()

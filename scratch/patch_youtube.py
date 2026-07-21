import os
import re

file_paths = [
    r"D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\selfcontrol\familybrowser\youtubeactivity.kt",
    r"D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\combo\selfcontrol\familybrowser\youtubeactivity.kt"
]

ad_servers_replacement = """        private val AD_SERVERS = setOf(
            "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", 
            "pubads.g.doubleclick.net", "youtube.com/api/stats/ads", "youtube.com/pagead",
            "googleadservices.com", "adservice.google.com",
            "/ptracking", "youtube.com/ptracking",
            "youtubei/v1/player/ad_break", "googlevideo.com/videoplayback?*&oad=",
            "doubleclick.net", "ad.doubleclick.net", "ads.youtube.com",
            "/ad_stat", "/aclk"
        )
"""

for file_path in file_paths:
    if not os.path.exists(file_path):
        continue
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Replace AD_SERVERS
    content = re.sub(r'private val AD_SERVERS = setOf\([^)]+\)', ad_servers_replacement.strip(), content, flags=re.MULTILINE|re.DOTALL)

    # Enhance shouldInterceptRequest for more robust ad blocking (similar to ublock)
    intercept_target = """                    if (AD_SERVERS.any { url.contains(it) }) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }"""
                    
    intercept_replacement = """                    // Check against known ad domains and paths (Network-level Ad Block)
                    if (AD_SERVERS.any { url.contains(it) }) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    
                    // Advanced YouTube specific network ad filtering
                    if (url.contains("youtube.com/api/stats/qoe") && url.contains("adformat=")) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (url.contains("googlevideo.com/videoplayback") && (url.contains("&oad=") || url.contains("ctier=A"))) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }"""
    
    if "youtube.com/api/stats/qoe" not in content:
        content = content.replace(intercept_target, intercept_replacement)

    # Remove JS skip logic completely
    # The JS logic is injected in override fun onPageFinished inside view.evaluateJavascript
    # I will replace the big JS block with a simple one that just hides some banners just in case, but no skip button logic.
    js_block_regex = r'view\.evaluateJavascript\("""\n\s*\(function\(\) \{\n.*?setInterval\(function\(\).*?\}\)\(\);\n\s*"""\.trimIndent\(\)\) \{\}'
    
    clean_js = """view.evaluateJavascript(\"\"\"
                    (function() {
                        // JS Ad skipping logic has been removed as requested.
                        // Relying purely on network-level WebResourceRequest interception (uBlock style).
                        
                        // Hide static banner elements just for cosmetic cleanup
                        const style = document.createElement('style');
                        style.innerHTML = '.ytp-ad-overlay-container, .ytp-ad-text-overlay, ytd-promoted-sparkles-web-renderer, ytd-compact-promoted-video-renderer, ytd-display-ad-renderer, .ad-showing, .ad-interrupting { display: none !important; }';
                        document.head.appendChild(style);
                    })();
                \"\"\".trimIndent()) {}"""

    content = re.sub(js_block_regex, clean_js, content, flags=re.DOTALL)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

print("youtubeactivity patched")

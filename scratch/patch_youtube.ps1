$paths = @(
    "D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\selfcontrol\familybrowser\youtubeactivity.kt",
    "D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\combo\selfcontrol\familybrowser\youtubeactivity.kt"
)

$adServersRegex = '(?s)private val AD_SERVERS = setOf\(.*?\)'
$adServersReplace = 'private val AD_SERVERS = setOf(
            "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", 
            "pubads.g.doubleclick.net", "youtube.com/api/stats/ads", "youtube.com/pagead",
            "googleadservices.com", "adservice.google.com",
            "/ptracking", "youtube.com/ptracking",
            "youtubei/v1/player/ad_break", "googlevideo.com/videoplayback?*&oad=",
            "doubleclick.net", "ad.doubleclick.net", "ads.youtube.com",
            "/ad_stat", "/aclk"
        )'

$interceptRegex = '(?s)if \(AD_SERVERS\.any \{ url\.contains\(it\) \}\) \{\s+return WebResourceResponse\("text/plain", "UTF-8", ByteArrayInputStream\(ByteArray\(0\)\)\)\s+\}'
$interceptReplace = 'if (AD_SERVERS.any { url.contains(it) }) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (url.contains("youtube.com/api/stats/qoe") && url.contains("adformat=")) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (url.contains("googlevideo.com/videoplayback") && (url.contains("&oad=") || url.contains("ctier=A"))) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }'

$jsRegex = '(?s)view\.evaluateJavascript\("""\s*\(function\(\) \{\s+window.__rasAdBlockerActive__ = true;.*?(?=""\.trimIndent\(\))'
$jsReplace = 'view.evaluateJavascript("""
            (function() {
                if (window.__rasAdBlockerActive__) return;
                window.__rasAdBlockerActive__ = true;
                const style = document.createElement("style");
                style.innerHTML = ".ytp-ad-overlay-container, .ytp-ad-text-overlay, ytd-promoted-sparkles-web-renderer, ytd-compact-promoted-video-renderer, ytd-display-ad-renderer, .ad-showing, .ad-interrupting { display: none !important; }";
                document.head.appendChild(style);
            })();
        '

foreach ($p in $paths) {
    if (Test-Path $p) {
        $content = Get-Content -Path $p -Raw -Encoding UTF8
        $content = $content -replace $adServersRegex, $adServersReplace
        if ($content -notmatch "youtube.com/api/stats/qoe") {
            $content = $content -replace $interceptRegex, $interceptReplace
        }
        $content = $content -replace $jsRegex, $jsReplace
        Set-Content -Path $p -Value $content -Encoding UTF8
        Write-Host "Patched $p"
    }
}

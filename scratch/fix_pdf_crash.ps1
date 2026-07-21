$paths = @(
    "D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\selfcontrol\study_tools\PdfToolsScreen.kt",
    "D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\combo\selfcontrol\study_tools\PdfToolsScreen.kt"
)

$target = '                    val openUri = if \(item\.path\.isNotBlank\(\)\) \{
                        android\.net\.Uri\.fromFile\(java\.io\.File\(item\.path\)\)
                    \} else \{
                        uri
                    \}'

$replace = '                    val openUri = if (item.path.isNotBlank()) {
                        try {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(item.path)
                            )
                        } catch (e: Exception) {
                            android.net.Uri.fromFile(java.io.File(item.path))
                        }
                    } else {
                        uri
                    }'

foreach ($p in $paths) {
    if (Test-Path $p) {
        $content = Get-Content -Path $p -Raw -Encoding UTF8
        $content = [regex]::Replace($content, $target, $replace)
        Set-Content -Path $p -Value $content -Encoding UTF8
        Write-Host "Patched $p"
    }
}

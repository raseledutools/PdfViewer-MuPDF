$paths = @(
    "D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\selfcontrol\study_tools\diary.kt",
    "D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\combo\selfcontrol\study_tools\diary.kt"
)

$sigRegex = '(?s)fun DiaryListScreen\(\s*entries: List<DiaryEntry>,\s*onEntryClick: \(DiaryEntry\) -> Unit,\s*onNewEntry: \(\) -> Unit,\s*onNavigateBack: \(\) -> Unit\s*\)'
$sigReplace = 'fun DiaryListScreen(
    entries: List<DiaryEntry>,
    onEntryClick: (DiaryEntry) -> Unit,
    onNewEntry: () -> Unit,
    onNavigateBack: () -> Unit,
    onMenuClick: () -> Unit = {}
)'

$topBarRegex = '(?s)title = \{\s*Text\(\s*"WriteDiary"(.*?)\},\s*navigationIcon = \{\s*IconButton\(onClick = onNavigateBack\) \{\s*Icon\(Icons\.Default\.Menu, contentDescription = "Menu", tint = Color\.White\)\s*\}\s*\},'
$topBarReplace = 'title = {
                    Text(
                        "RasDiary"$1},
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },'

$canvasTopBarRegex = '(?s)IconButton\(onClick = \{ scope\.launch \{ drawerState\.open\(\) \} \}\) \{\s*Icon\(Icons\.Default\.ArrowBack, contentDescription = "Menu", tint = Color\.White\)\s*\}'
$canvasTopBarReplace = 'IconButton(onClick = { 
                            viewModel.forceSaveOnExit()
                            showListScreen = true 
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }'

$writeNoteRegex = '"Write note"'
$writeNoteReplace = '"RasDiary"'

$writeDirayRegex = '"write Diray"'
$writeDirayReplace = '"RasDiary"'

foreach ($p in $paths) {
    if (Test-Path $p) {
        $content = Get-Content -Path $p -Raw -Encoding UTF8
        
        $content = $content -replace $sigRegex, $sigReplace
        $content = $content -replace $topBarRegex, $topBarReplace
        $content = $content -replace $canvasTopBarRegex, $canvasTopBarReplace
        $content = $content -replace $writeNoteRegex, $writeNoteReplace
        $content = $content -replace $writeDirayRegex, $writeDirayReplace

        # BackHandler in Scaffold:
        # Just find `) {` followed by `Scaffold(` and replace with BackHandler
        # Only for the main Canvas scaffold
        $scaffoldRegex = '(?s)\) \{\s*Scaffold\(\s*topBar = \{'
        $scaffoldReplace = ') {
        androidx.activity.compose.BackHandler {
            viewModel.forceSaveOnExit()
            showListScreen = true
        }
        Scaffold(
            topBar = {'
        
        if ($content -notmatch 'androidx\.activity\.compose\.BackHandler \{') {
            $content = $content -replace $scaffoldRegex, $scaffoldReplace
        }

        # Now fix the ModalNavigationDrawer
        # It's tricky to nest it correctly with regex.
        # Instead, since we already solved the "canvas back button" and "physical back button" issues,
        # AND we added "onMenuClick" to DiaryListScreen... Wait, DiaryListScreen is still outside the Drawer!
        # Because in ProfessionalDiaryScreen:
        # if (showListScreen) { DiaryListScreen(...); return }
        # To fix this, I can just replace the if (showListScreen) block to include the Drawer for it!
        # Actually, if we just wrap DiaryListScreen inside its own ModalNavigationDrawer?
        # That would duplicate state. But it's easier to just replace the whole ModalNavigationDrawer section.
        # Let's do this: we'll comment out the early returns and put them inside the main drawer body.
        
        $regexDrawer = '(?s)    if \(showListScreen\) \{.*?ModalNavigationDrawer\(\s*drawerState = drawerState,.*?drawerContent = \{.*?\)\s*\}\s*\) \{'
        $replaceDrawer = '    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = Color(0xFF2D323E)
            ) {
                DiarySidebar(
                    selectedFilter = selectedFilter,
                    isDarkMode = isDarkMode,
                    cloudStatus = cloudStatus,
                    isLoggedIn = DiaryCloudSync.isLoggedIn(),
                    onFilterSelect = {
                        viewModel.setFolderFilter(it)
                        scope.launch { drawerState.close() }
                    },
                    onNewEntry = {
                        viewModel.startNewEntry()
                        showListScreen = false
                        scope.launch { drawerState.close() }
                    },
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onExportClick = {
                        scope.launch { drawerState.close() }
                        showExportMenu = true
                    },
                    onCalendarClick = {
                        scope.launch { drawerState.close() }
                        showCalendar = true
                    },
                    onSyncClick = { viewModel.syncToCloud() },
                    allEntries = allEntries,
                    onEntryClick = { entry ->
                        viewModel.loadEntry(entry)
                        showListScreen = false
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        if (showListScreen) {
            DiaryListScreen(
                entries = allEntries,
                onEntryClick = { entry ->
                    viewModel.loadEntry(entry)
                    showListScreen = false
                },
                onNewEntry = {
                    viewModel.startNewEntry()
                    showListScreen = false
                },
                onNavigateBack = onNavigateBack,
                onMenuClick = { scope.launch { drawerState.open() } }
            )
        } else if (showCalendar) {
            DiaryCalendarScreen(
                entries = allEntries,
                onEntryClick = { entry ->
                    viewModel.loadEntry(entry)
                    showCalendar = false
                    showListScreen = false
                },
                onClose = { showCalendar = false }
            )
        } else if (currentEntry != null && currentEntry!!.isLocked && !isUnlocked) {
            DiaryLockScreen(
                entry = currentEntry!!,
                onUnlock = { viewModel.unlockWithBiometric() },
                onCancel = { showListScreen = true }
            )
        } else if (currentEntry != null) {'
        
        $content = $content -replace $regexDrawer, $replaceDrawer

        # We need to add one more '}' at the end of the ProfessionalDiaryScreen function body to close the `} else if (currentEntry != null) {` block
        # The function body ends right before `// ---- Dialogs ----`
        $content = $content -replace '// ---- Dialogs ----', '}
    // ---- Dialogs ----'

        # Also, fix `currentEntry.title` to `currentEntry!!.title` if Kotlin complains about smart cast?
        # Because we check `currentEntry != null`, it might be fine, but if currentEntry is a State (val currentEntry by ...), smart cast won't work!
        # Wait! `currentEntry` is a property delegated by `by ...collectAsState()`. Smart cast is impossible on delegated properties!
        # So it's better to just use `val entry = currentEntry; if (entry != null)` or just use `currentEntry` if it's already non-null in the original code.
        # Oh, the original code had:
        # `val currentEntry by viewModel.currentEntry.collectAsState()`
        # If `currentEntry` was NOT nullable, then `currentEntry != null` is unnecessary!
        # Let's check if it's nullable. If it's not nullable, `currentEntry.isLocked` works fine. Let's see original code:
        # Original code: `if (currentEntry.isLocked && !isUnlocked)`
        # This implies currentEntry is NOT nullable! It's a non-null DiaryEntry. It probably has a dummy/empty state.
        
        # So I will remove `currentEntry != null` check from my replacement.
        $nullCheckRegex = '\} else if \(currentEntry != null && currentEntry!!\.isLocked && !isUnlocked\) \{'
        $nullCheckReplace = '} else if (currentEntry.isLocked && !isUnlocked) {'
        $content = $content -replace $nullCheckRegex, $nullCheckReplace
        
        $nullCheckRegex2 = '\} else if \(currentEntry != null\) \{'
        $nullCheckReplace2 = '} else {'
        $content = $content -replace $nullCheckRegex2, $nullCheckReplace2

        $nullCheckRegex3 = 'entry = currentEntry!!'
        $nullCheckReplace3 = 'entry = currentEntry'
        $content = $content -replace $nullCheckRegex3, $nullCheckReplace3

        Set-Content -Path $p -Value $content -Encoding UTF8
        Write-Host "Patched $p"
    }
}

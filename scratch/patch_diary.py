import os
import re

file_paths = [
    r"D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\selfcontrol\study_tools\diary.kt",
    r"D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\combo\selfcontrol\study_tools\diary.kt"
]

for file_path in file_paths:
    if not os.path.exists(file_path):
        continue
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Update DiaryListScreen signature and TopAppBar
    # Add onMenuClick: () -> Unit to DiaryListScreen
    sig_target = """fun DiaryListScreen(
    entries: List<DiaryEntry>,
    onEntryClick: (DiaryEntry) -> Unit,
    onNewEntry: () -> Unit,
    onNavigateBack: () -> Unit
)"""
    sig_replace = """fun DiaryListScreen(
    entries: List<DiaryEntry>,
    onEntryClick: (DiaryEntry) -> Unit,
    onNewEntry: () -> Unit,
    onNavigateBack: () -> Unit,
    onMenuClick: () -> Unit = {}
)"""
    content = content.replace(sig_target, sig_replace)

    # Change "WriteDiary" to "RasDiary" and fix Menu onClick
    list_topbar_target = """                title = {
                    Text(
                        "WriteDiary",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },"""
    list_topbar_replace = """                title = {
                    Text(
                        "RasDiary",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },"""
    content = content.replace(list_topbar_target, list_topbar_replace)
    # Just in case "write Diray" is somewhere else
    content = content.replace('"write Diray"', '"RasDiary"')
    content = content.replace('"Write note"', '"RasDiary"')

    # 2. Fix ProfessionalDiaryScreen to wrap everything in ModalNavigationDrawer
    # Remove early returns for showListScreen and lock screen
    # We will replace the bottom part of ProfessionalDiaryScreen where ModalNavigationDrawer is called.
    
    # We need to find the showListScreen if block and remove the 'return' so we can wrap it.
    # Actually, it's easier to find the `ModalNavigationDrawer(` and move it up, OR wrap the components.
    
    # Wait, the easiest way is to use regex to capture the blocks and rebuild ProfessionalDiaryScreen body.
    # But since it's kotlin, indentation and braces matter. Let's do a targeted replace for ProfessionalDiaryScreen body.
    
    # Let's replace the whole ProfessionalDiaryScreen function body up to the Scaffold.
    # But it's risky with regex. Let's use targeted replaces.

    # Fix the ArrowBack in Canvas Scaffold to go back to list
    canvas_topbar_target = """                    navigationIcon = {
                        // Screenshot-? "+?" back-arrow ݅ ؅-_" < 1 ؅", ?-_"  ?Y_
                        // sidebar drawer - <  (Calendar, Folder filter, Cloud
                        // Sync, PDF Export, entry list ?" ?- ? < 1_ݨ_ݬ  __"_ݬ_
                        // __ݪ  "_)  +, save & exit checkmark (o") ݪ_Y" ؅ 
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Menu", tint = Color.White)
                        }
                    },"""
    canvas_topbar_replace = """                    navigationIcon = {
                        IconButton(onClick = { 
                            viewModel.forceSaveOnExit()
                            showListScreen = true 
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },"""
    content = content.replace(canvas_topbar_target, canvas_topbar_replace)
    
    # Add BackHandler to Canvas Scaffold
    scaffold_start_target = """    ) {
        Scaffold("""
    scaffold_start_replace = """    ) {
        BackHandler {
            viewModel.forceSaveOnExit()
            showListScreen = true
        }
        Scaffold("""
    if "BackHandler {" not in content:
        content = content.replace(scaffold_start_target, scaffold_start_replace)

    # Now, how to make the drawer wrap the list? 
    # Currently:
    # if (showListScreen) { DiaryListScreen(...); return }
    # if (currentEntry.isLocked) { DiaryLockScreen(...); return }
    # ModalNavigationDrawer { Scaffold { ... } }
    
    # We can change it to:
    # ModalNavigationDrawer {
    #     if (showListScreen) { DiaryListScreen(..., onMenuClick = { scope.launch { drawerState.open() } }); return@ModalNavigationDrawer } // Wait, can't return from Composable lambda easily without breaking layout if it's the only child
    # Better:
    # ModalNavigationDrawer {
    #     if (showListScreen) { DiaryListScreen(..., onMenuClick = { scope.launch { drawerState.open() } }) }
    #     else if (currentEntry.isLocked && !isUnlocked) { DiaryLockScreen(...) }
    #     else { Scaffold(...) }
    # }
    
    # Let's find the exact chunk:
    chunk_to_replace = """    if (showListScreen) {
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
            onNavigateBack = onNavigateBack
        )
        return
    }

    // Show calendar - calendar icon click ???? DatePickerDialog ???? ????
    // user ?????? date choose ??? ???? entry ????? ????
    if (showCalendar) {
        DiaryCalendarScreen(
            entries = allEntries,
            onEntryClick = { entry ->
                viewModel.loadEntry(entry)
                showCalendar = false
            },
            onClose = { showCalendar = false }
        )
        return
    }

    // Show lock screen if entry is locked and not yet unlocked
    if (currentEntry.isLocked && !isUnlocked) {
        DiaryLockScreen(
            entry = currentEntry,
            onUnlock = { viewModel.unlockWithBiometric() },
            onCancel = { showListScreen = true }
        )
        return
    }

    ModalNavigationDrawer(
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
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {"""

    # Because of unicode comments, it's safer to use regex to capture this block
    regex_block = r"    if \(showListScreen\) \{.*?ModalNavigationDrawer\(\s*drawerState = drawerState,.*?drawerContent = \{.*?\)\s*\}\s*\}\s*\) \{"
    
    new_block = """    ModalNavigationDrawer(
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
        } else {"""
        
    # Let's write a python regex replacement
    import re
    # We replace from 'if (showListScreen) {' all the way to 'ModalNavigationDrawer(...) {'
    content = re.sub(r'    if \(showListScreen\) \{.*?ModalNavigationDrawer\([^)]*\)\s*\{', new_block, content, flags=re.DOTALL)
    
    # We need to add a closing brace '}' at the very end of ProfessionalDiaryScreen to close the 'else {'
    # Let's find the end of ProfessionalDiaryScreen.
    # It ends with:
    #         if (showSetPinDialog) {
    #             ...
    #         }
    #     }
    # }
    
    # Actually, we can just replace the last '}' of the Scaffold's ModalNavigationDrawer closure with '} }'
    # Wait, the easiest way is to add `}` before the end of the file or function. 
    # But wait, does this file contain anything else? Yes, DiaryListScreen, DiaryCalendarScreen etc. are above it!
    # ProfessionalDiaryScreen is at the bottom.
    
    # Let's find `} // End of Scaffold` or something similar, or just replace the last closing brace of ProfessionalDiaryScreen.
    # Let's check what's at the end of the file.
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

print("diary.kt patched part 1")

$filePath = "app\src\main\java\com\rasel\RasFocus\selfcontrol\SelfControlModule.kt"
$content = Get-Content $filePath -Raw

$content = $content.Replace(
    "import androidx.compose.material3.*",
    "import androidx.compose.material3.*`r`nimport kotlinx.coroutines.launch"
)

$oldSetup = @"
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
"@
$newSetup = @"
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
"@
$content = $content.Replace($oldSetup, $newSetup)

$oldMaterial = @"
    MaterialTheme {
        if (isComboMode) {
"@
$newMaterial = @"
    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    onNavigate = { route -> 
                        if (route == `"statistics`") {
                            navController.navigate(`"statistics_new`")
                        } else {
                            navController.navigate(route)
                        }
                    },
                    closeDrawer = { coroutineScope.launch { drawerState.close() } }
                )
            }
        ) {
        if (isComboMode) {
"@
$content = $content.Replace($oldMaterial, $newMaterial)

$oldEnd = @"
                    SelfControlBottomNav(selectedTab) { selectedTab = it }
                }
            }
        }
    }
}
"@
$newEnd = @"
                    SelfControlBottomNav(selectedTab) { selectedTab = it }
                }
            }
        }
        }
    }
}
"@
$content = $content.Replace($oldEnd, $newEnd)

$content = $content.Replace(
    "TopHeader(navController)",
    "TopHeader(navController, onMenuClick = { coroutineScope.launch { drawerState.open() } })"
)

$content = $content.Replace(
    "TopHeader()",
    "TopHeader(navController = null, onMenuClick = { coroutineScope.launch { drawerState.open() } })"
)

$content = $content.Replace(
    "fun TopHeader(navController: NavController? = null) {",
    "fun TopHeader(navController: NavController? = null, onMenuClick: () -> Unit = {}) {"
)

$oldIcon = @"
Box(Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu
"@
$newIcon = @"
Box(Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape).clickable { onMenuClick() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu
"@
$content = $content.Replace($oldIcon, $newIcon)

Set-Content -Path $filePath -Value $content -NoNewline

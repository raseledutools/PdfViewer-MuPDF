import sys

with open('app/src/main/java/com/rasel/RasFocus/selfcontrol/SelfControlModule.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Imports
content = content.replace("import androidx.compose.material3.*", "import androidx.compose.material3.*\nimport kotlinx.coroutines.launch")

# 2. StayFocusedApp setup
old_setup = """    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current"""

new_setup = """    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()"""

content = content.replace(old_setup, new_setup)

# 3. Wrapping MaterialTheme
old_material = """    MaterialTheme {
        if (isComboMode) {"""

new_material = """    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    onNavigate = { route -> 
                        if (route == "statistics") {
                            navController.navigate("statistics_new")
                        } else {
                            navController.navigate(route)
                        }
                    },
                    closeDrawer = { coroutineScope.launch { drawerState.close() } }
                )
            }
        ) {
        if (isComboMode) {"""

content = content.replace(old_material, new_material)

# Close the ModalNavigationDrawer bracket at the end of StayFocusedApp
old_end = """                    SelfControlBottomNav(selectedTab) { selectedTab = it }
                }
            }
        }
    }
}"""

new_end = """                    SelfControlBottomNav(selectedTab) { selectedTab = it }
                }
            }
        }
        }
    }
}"""
content = content.replace(old_end, new_end)

# 4. TopHeader calls
content = content.replace("TopHeader(navController)", "TopHeader(navController, onMenuClick = { coroutineScope.launch { drawerState.open() } })")
content = content.replace("TopHeader()", "TopHeader(navController = null, onMenuClick = { coroutineScope.launch { drawerState.open() } })")

# 5. TopHeader definition
old_def = "fun TopHeader(navController: NavController? = null) {"
new_def = "fun TopHeader(navController: NavController? = null, onMenuClick: () -> Unit = {}) {"
content = content.replace(old_def, new_def)

# 6. TopHeader button click
old_icon = """Box(Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu"""
new_icon = """Box(Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape).clickable { onMenuClick() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu"""
content = content.replace(old_icon, new_icon)

with open('app/src/main/java/com/rasel/RasFocus/selfcontrol/SelfControlModule.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Patched successfully")

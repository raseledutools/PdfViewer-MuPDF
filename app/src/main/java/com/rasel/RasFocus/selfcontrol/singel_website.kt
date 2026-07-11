package com.rasel.RasFocus.selfcontrol

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle

// ==========================================
// 1. COLORS (BlockingPlan এর মতোই)
// ==========================================
private val SwPrimaryTeal = Color(0xFF0096B4)
private val SwDarkText      = Color(0xFF1A1A1A)
private val SwGrayText      = Color(0xFF6B7280)
private val SwCardBg        = Color(0xFFFFFFFF)
private val SwRedIcon       = Color(0xFFEF4444)
private val SwBackground    = Color(0xFFF3F4F6)

// ==========================================
// 2. DATA CLASS
// ==========================================
data class IndividualSiteConfig(
    val domain: String,
    val isActive: Boolean
)

// ==========================================
// 3. MANAGER — SharedPreferences store
//    BlockingManager.getActiveBlockedSites()
//    এই key থেকে পড়বে, তাই same prefs name রাখা হয়েছে
// ==========================================
object IndividualSiteManager {

    private const val PREFS_NAME = "individual_site_locks"
    private const val KEY_CONFIGS = "site_configs_json"
    private val gson = Gson()

    fun saveConfigs(context: Context, configs: Map<String, IndividualSiteConfig>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIGS, gson.toJson(configs))
            .apply()
    }

    fun loadConfigs(context: Context): Map<String, IndividualSiteConfig> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONFIGS, null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, IndividualSiteConfig>>() {}.type)
                ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // BlockingManager.getActiveBlockedSites() এর সাথে merge করার জন্য
    // active blocked domains এর set return করে
    fun getActiveBlockedDomains(context: Context): Set<String> {
        return loadConfigs(context)
            .values
            .filter { it.isActive }
            .map { WebsiteBlockingAccessibilityService.cleanDomain(it.domain) }
            .filter { it.isNotBlank() }
            .toSet()
    }
}

// ==========================================
// 4. MAIN SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndividualWebsiteLockScreen(navController: NavController) {
    val context = LocalContext.current

    var configs by remember { mutableStateOf(IndividualSiteManager.loadConfigs(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    // Accessibility permission state
    var hasAccessibilityPerm by remember {
        mutableStateOf(BlockingManager.isWebsiteBlockingServiceEnabled(context))
    }
    // Initial load এ একবার recheck করো (দুইটা service ই check হবে)
    LaunchedEffect(Unit) {
        hasAccessibilityPerm = BlockingManager.isWebsiteBlockingServiceEnabled(context)
    }
    // Settings থেকে ফিরলে recheck
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAccessibilityPerm = BlockingManager.isWebsiteBlockingServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Add dialog
    if (showAddDialog) {
        AddWebsiteDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { domain ->
                // Permission check
                if (!hasAccessibilityPerm) {
                    Toast.makeText(
                        context,
                        "Website block করতে Accessibility Permission দিন",
                        Toast.LENGTH_LONG
                    ).show()
                    BlockingManager.openAccessibilitySettings(context)
                    showAddDialog = false
                    return@AddWebsiteDialog
                }
                val clean = WebsiteBlockingAccessibilityService.cleanDomain(domain)
                if (clean.isNotBlank() && !configs.containsKey(clean)) {
                    val newMap = configs.toMutableMap()
                    newMap[clean] = IndividualSiteConfig(domain = clean, isActive = true)
                    configs = newMap
                    IndividualSiteManager.saveConfigs(context, newMap)
                    AppBlockerService.start(context)
                    Toast.makeText(context, "$clean blocked!", Toast.LENGTH_SHORT).show()
                } else if (configs.containsKey(clean)) {
                    Toast.makeText(context, "Already in list!", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
            }
        )
    }

    // Active blocked sites list — blocked আগে, তারপর alphabetically
    val sortedConfigs = configs.values
        .filter { it.domain.contains(searchQuery.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<IndividualSiteConfig> { it.isActive }.thenBy { it.domain })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Website Blocker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = SwDarkText
                        )
                        Text(
                            "প্রতিটি সাইট আলাদাভাবে ব্লক করো",
                            fontSize = 12.sp,
                            color = SwGrayText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .background(SwPrimaryTeal, RoundedCornerShape(10.dp))
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add website",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SwCardBg)
            )
        },
        containerColor = SwBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Accessibility permission banner
            if (!hasAccessibilityPerm) {
                SwPermissionBanner(
                    title = "Accessibility Permission দাও",
                    message = "Website block করতে Accessibility Service enable করতে হবে।"
                ) {
                    BlockingManager.openAccessibilitySettings(context)
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Website search করো...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SwPrimaryTeal,
                    unfocusedBorderColor = Color(0xFFE5E7EB)
                )
            )

            Spacer(Modifier.height(12.dp))

            if (sortedConfigs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = SwGrayText.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "কোনো website block করা নেই",
                            fontSize = 15.sp,
                            color = SwGrayText
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "+ বাটন চেপে যোগ করো",
                            fontSize = 13.sp,
                            color = SwGrayText.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortedConfigs, key = { it.domain }) { siteConfig ->
                        SiteRowItem(
                            config = siteConfig,
                            onToggle = {
                                // Enable করার সময় permission check, disable করার সময় না
                                val isEnabling = !siteConfig.isActive
                                if (isEnabling && !hasAccessibilityPerm) {
                                    Toast.makeText(
                                        context,
                                        "Website block করতে Accessibility Permission দিন",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    BlockingManager.openAccessibilitySettings(context)
                                    return@SiteRowItem
                                }
                                val newMap = configs.toMutableMap()
                                newMap[siteConfig.domain] =
                                    siteConfig.copy(isActive = !siteConfig.isActive)
                                configs = newMap
                                IndividualSiteManager.saveConfigs(context, newMap)
                                if (newMap.values.any { it.isActive }) {
                                    AppBlockerService.start(context)
                                } else {
                                    // Individual sites নেই, কিন্তু কোনো active profile আছে কিনা চেক করো
                                    val anyProfileActive = BlockingManager.loadProfiles(context).any { it.isActive }
                                    if (!anyProfileActive) AppBlockerService.stop(context)
                                }
                            },
                            onDelete = {
                                val newMap = configs.toMutableMap()
                                newMap.remove(siteConfig.domain)
                                configs = newMap
                                IndividualSiteManager.saveConfigs(context, newMap)
                                if (newMap.values.none { it.isActive }) {
                                    val anyProfileActive = BlockingManager.loadProfiles(context).any { it.isActive }
                                    if (!anyProfileActive) AppBlockerService.stop(context)
                                }
                                Toast.makeText(
                                    context,
                                    "${siteConfig.domain} removed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

// ==========================================
// 5. SITE ROW ITEM
// ==========================================
@Composable
private fun SiteRowItem(
    config: IndividualSiteConfig,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isActive) SwPrimaryTeal.copy(alpha = 0.08f)
            else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (config.isActive) SwPrimaryTeal.copy(alpha = 0.5f) else Color(0xFFE5E7EB)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favicon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFFF9FAFB), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                FaviconImage(
                    domain = config.domain,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.domain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SwDarkText
                )
                Text(
                    if (config.isActive) "Blocked" else "Inactive",
                    fontSize = 11.sp,
                    color = if (config.isActive) SwPrimaryTeal else SwGrayText,
                    fontWeight = if (config.isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = SwRedIcon,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Toggle switch
            Switch(
                checked = config.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SwPrimaryTeal,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFD1D5DB)
                )
            )
        }
    }
}

// ==========================================
// 6. ADD WEBSITE DIALOG
// ==========================================
@Composable
private fun AddWebsiteDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val allWebsites = remember {
        listOf(
            "facebook.com", "instagram.com", "twitter.com", "x.com", "tiktok.com",
            "snapchat.com", "pinterest.com", "reddit.com", "linkedin.com", "threads.net",
            "youtube.com", "netflix.com", "primevideo.com", "hotstar.com", "chorki.com",
            "prothomalo.com", "bdnews24.com", "thedailystar.net", "samakal.com",
            "daraz.com.bd", "shajgoj.com", "chaldal.com", "amazon.com",
            "roblox.com", "steam.com", "epicgames.com",
            "web.whatsapp.com", "web.telegram.org", "messenger.com", "discord.com",
            "9gag.com", "quora.com", "wikipedia.org", "medium.com"
        )
    }
    val suggestions = remember(input) {
        if (input.trim().length < 2) emptyList()
        else allWebsites.filter {
            it.contains(input.trim(), ignoreCase = true)
        }.take(5)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = SwCardBg
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Website যোগ করো",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SwDarkText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "domain লিখো (যেমন: facebook.com)",
                    fontSize = 12.sp,
                    color = SwGrayText
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("example.com") },
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null)
                    },
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(onClick = { input = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SwPrimaryTeal
                    )
                )

                // Suggestions
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    suggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { input = suggestion }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FaviconImage(
                                domain = suggestion,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(suggestion, fontSize = 14.sp, color = SwDarkText)
                        }
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { if (input.trim().isNotBlank()) onAdd(input.trim()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = input.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = SwPrimaryTeal)
                    ) {
                        Text("Block করো", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// SwPermissionBanner — singel_website screen এর জন্য
// ==========================================
@Composable
private fun SwPermissionBanner(
    title: String,
    message: String,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF8B5CF6).copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Accessibility,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("দাও", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}

// ==========================================
// 7. BlockingManager এ merge করার জন্য extension
//    BlockingPlan.kt এর getActiveBlockedSites() কে
//    এই file এর sites ও include করাতে হবে।
//
//    BlockingPlan.kt এ getActiveBlockedSites() এ
//    নিচের লাইনটা যোগ করো:
//
//    + IndividualSiteManager.getActiveBlockedDomains(context)
//
//    মানে পুরো function টা হবে:
//
//    fun getActiveBlockedSites(context: Context): Set<String> {
//        val profileSites = loadProfiles(context)
//            .filter { it.isActive }
//            .flatMap { it.blockedSites }
//            .map { WebsiteBlockingAccessibilityService.cleanDomain(it) }
//            .filter { it.isNotBlank() }
//            .toSet()
//        val individualSites = IndividualSiteManager.getActiveBlockedDomains(context)
//        return profileSites + individualSites
//    }
// ==========================================
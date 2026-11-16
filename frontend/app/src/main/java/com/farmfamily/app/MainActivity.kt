package com.farmfamily.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val authCallbackUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authCallbackUri.value = intent?.data
        val authRepository = SupabaseAuthRepository(applicationContext)
        val farmRepository = SupabaseFarmRepository(applicationContext)
        setContent {
            FarmFamilyTheme {
                FarmFamilyApp(authRepository, farmRepository, authCallbackUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authCallbackUri.value = intent.data
    }
}

internal val Forest = Color(0xFF174A35)
internal val ForestSoft = Color(0xFF2F6B4F)
internal val Green = Color(0xFF2F6B4F)
internal val Leaf = Color(0xFFE7F1EA)
internal val Cream = Color(0xFFF8F6EF)
internal val Ink = Color(0xFF1F2924)
internal val Muted = Color(0xFF68736D)
internal val Line = Color(0xFFDCE3DE)
internal val Gold = Color(0xFFE6BA63)
private val Warning = Color(0xFFFFF3D6)
private val ErrorColor = Color(0xFFB14E36)

private enum class AuthState {
    LOGGED_OUT,
    LOGGING_IN,
    LOGGED_IN,
    SESSION_RESTORING,
    SESSION_EXPIRED,
    LOGGING_OUT,
    AUTH_ERROR
}

private enum class MainTab { HOME, RECORDS, REPORTS, AI, MORE }

enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED, CONFLICT }

data class FamilyContext(
    val familyId: String = "",
    val familyName: String = "Family not connected",
    val userName: String = "",
    val userEmail: String = ""
)

data class FarmField(
    val localId: String,
    val name: String,
    val location: String,
    val area: String,
    val crop: String,
    val status: String,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val recordVersion: Int = 1
)

data class FarmExpense(
    val localId: String,
    val category: String,
    val description: String,
    val amount: Int,
    val date: String,
    val field: String,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val recordVersion: Int = 1
)

data class DashboardSummary(
    val fields: Int,
    val landArea: String,
    val activeCrops: Int,
    val expenses: Int,
    val sales: Int,
    val pendingSync: Int,
    val familyHealth: String
)

data class AiResponse(
    val title: String,
    val summary: String,
    val data: List<Pair<String, String>>
)

private interface FarmRepository {
    fun familyContext(): FamilyContext
    fun fields(): List<FarmField>
    fun expenses(): List<FarmExpense>
    fun dashboardSummary(): DashboardSummary
    fun addField(field: FarmField)
    fun addExpense(expense: FarmExpense)
    fun search(query: String): List<Pair<String, String>>
    fun askAi(question: String): AiResponse
}

private class InMemoryFarmRepository : FarmRepository {
    private val fields = mutableListOf(
        FarmField("field-1", "Khet A", "Dumara", "2.0 Bigha", "Rice / Dhan", "Growing"),
        FarmField("field-2", "Khet B", "Dumara", "1.5 Bigha", "Wheat", "Growing"),
        FarmField("field-3", "Khet C", "Basantpur", "1.0 Bigha", "Potato", "Planning")
    )
    private val expenses = mutableListOf(
        FarmExpense("exp-1", "Fertilizer", "Urea and DAP", 4500, "14 Nov 2026", "Khet A"),
        FarmExpense("exp-2", "Labor", "Paddy transplanting", 3500, "12 Nov 2026", "Khet A"),
        FarmExpense("exp-3", "Tractor", "Field prep", 1800, "11 Nov 2026", "Khet B")
    )

    override fun familyContext() = FamilyContext()

    override fun fields(): List<FarmField> = fields.toList()

    override fun expenses(): List<FarmExpense> = expenses.toList()

    override fun dashboardSummary(): DashboardSummary {
        val totalExpenses = expenses.sumOf { it.amount }
        val totalSales = 42000
        val pendingSync = fields.count { it.syncStatus == SyncStatus.PENDING }
        return DashboardSummary(
            fields = fields.size,
            landArea = "4.5 Bigha",
            activeCrops = 3,
            expenses = totalExpenses,
            sales = totalSales,
            pendingSync = pendingSync,
            familyHealth = "Stable"
        )
    }

    override fun addField(field: FarmField) {
        fields.add(0, field)
    }

    override fun addExpense(expense: FarmExpense) {
        expenses.add(0, expense)
    }

    override fun search(query: String): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<Pair<String, String>>()
        fields.filter { it.name.contains(query, true) || it.location.contains(query, true) || it.crop.contains(query, true) }
            .forEach { matches.add(it.name to "Field · ${it.crop} · ${it.area}") }
        expenses.filter { it.category.contains(query, true) || it.description.contains(query, true) || it.field.contains(query, true) }
            .forEach { matches.add(it.category to "Expense · NPR ${it.amount} · ${it.field}") }
        return matches.distinct().take(10)
    }

    override fun askAi(question: String): AiResponse {
        val q = question.lowercase()
        return when {
            q.contains("fertilizer") -> AiResponse(
                title = "Fertilizer summary",
                summary = "NPR 4,500 spent on fertilizer across the active season.",
                data = listOf(
                    "Urea / DAP" to "NPR 4,500",
                    "Primary field" to "Khet A",
                    "Status" to "Ready for next sync"
                )
            )
            q.contains("rice") || q.contains("harvest") -> AiResponse(
                title = "Rice harvest",
                summary = "Rice production is tracking above the seasonal target.",
                data = listOf(
                    "Khet A" to "80 Quintal",
                    "Khet B" to "40 Quintal",
                    "Season" to "2083 BS"
                )
            )
            else -> AiResponse(
                title = "Farm overview",
                summary = "Your family farm is healthy and records are ready for sync.",
                data = listOf(
                    "Fields" to "3",
                    "Active crops" to "3",
                    "Expenses" to "NPR ${expenses.sumOf { it.amount }}",
                    "Family" to "Sharma Family"
                )
            )
        }
    }
}

private object Strings {
    const val appName = "FARM FAMILY"
    const val tagline = "Grow Together. Prosper Together."
    const val loginTitle = "Sign in to your farm"
    const val defaultFamily = "Sharma Family"
}

@Composable
private fun FarmFamilyTheme(content: @Composable () -> Unit) {
    val baseTypography = androidx.compose.material3.Typography()
    val colors = lightColorScheme(
        primary = Forest,
        onPrimary = Color.White,
        secondary = Green,
        onSecondary = Color.White,
        background = Cream,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Leaf,
        outline = Line,
        tertiary = Gold
    )
    val typography = baseTypography.copy(
        headlineSmall = baseTypography.headlineSmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 24.sp
        ),
        titleMedium = baseTypography.titleMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 18.sp
        ),
        bodyLarge = baseTypography.bodyLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp
        ),
        bodyMedium = baseTypography.bodyMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp
        ),
        labelMedium = baseTypography.labelMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp
        )
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FarmFamilyApp(
    authRepository: SupabaseAuthRepository,
    farmRepository: SupabaseFarmRepository,
    callbackUri: MutableState<Uri?>
) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(executor) {
        onDispose { executor.shutdown() }
    }

    var authState by remember { mutableStateOf(AuthState.SESSION_RESTORING) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var currentSession by remember { mutableStateOf<AuthSession?>(null) }
    var passwordRecovery by remember { mutableStateOf(false) }
    LaunchedEffect(authRepository) {
        if (callbackUri.value != null) return@LaunchedEffect
        executor.execute {
            try {
                val session = authRepository.restoreSession()
                mainHandler.post {
                    authState = if (session == null) AuthState.LOGGED_OUT else AuthState.LOGGED_IN
                    currentSession = session
                }
            } catch (error: Exception) {
                mainHandler.post {
                    authError = error.message ?: "Could not restore your sign-in session."
                    authState = AuthState.AUTH_ERROR
                }
            }
        }
    }

    LaunchedEffect(callbackUri.value) {
        val uri = callbackUri.value ?: return@LaunchedEffect
        executor.execute {
            try {
                val callback = authRepository.handleAuthCallback(uri)
                mainHandler.post {
                    currentSession = callback.session
                    passwordRecovery = callback.passwordRecovery
                    authState = AuthState.LOGGED_IN
                    authError = null
                    callbackUri.value = null
                }
            } catch (error: Exception) {
                mainHandler.post {
                    authError = error.message ?: "The email link could not be verified."
                    authState = AuthState.AUTH_ERROR
                    callbackUri.value = null
                }
            }
        }
    }

    fun submitAuth(action: () -> AuthSession?) {
        authError = null
        authState = AuthState.LOGGING_IN
        executor.execute {
            try {
                val session = action()
                mainHandler.post {
                    if (session == null) {
                        authState = AuthState.LOGGED_OUT
                        authError = "Check your email to confirm your account, then sign in."
                    } else {
                        currentSession = session
                        authState = AuthState.LOGGED_IN
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    authError = error.message ?: "Authentication failed. Try again."
                    authState = AuthState.AUTH_ERROR
                }
            }
        }
    }

    fun signOut() {
        authState = AuthState.LOGGING_OUT
        executor.execute {
            try {
                authRepository.signOut()
                mainHandler.post {
                    currentSession = null
                    authState = AuthState.LOGGED_OUT
                }
            } catch (error: Exception) {
                mainHandler.post {
                    currentSession = null
                    authError = error.message ?: "Could not confirm sign-out with the server."
                    authState = AuthState.AUTH_ERROR
                }
            }
        }
    }

    if (authState == AuthState.SESSION_RESTORING ||
        authState == AuthState.LOGGING_IN ||
        authState == AuthState.LOGGING_OUT
    ) {
        AuthProgressScreen()
        return
    }

    if (authState != AuthState.LOGGED_IN) {
        LoginScreen(
            onLogin = { submitAuth { authRepository.signIn(email, password) } },
            onSignUp = { displayName ->
                submitAuth { authRepository.signUp(email, password, displayName) }
            },
            onRecover = {
                authError = null
                authState = AuthState.LOGGING_IN
                executor.execute {
                    try {
                        authRepository.sendPasswordRecovery(email)
                        mainHandler.post {
                            authError = "Password recovery instructions were sent if the account exists."
                            authState = AuthState.LOGGED_OUT
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            authError = error.message ?: "Could not start password recovery."
                            authState = AuthState.AUTH_ERROR
                        }
                    }
                }
            },
            email = email,
            password = password,
            onEmailChange = { email = it },
            onPasswordChange = { password = it },
            message = authError
        )
        return
    }

    if (passwordRecovery) {
        val recoverySession = currentSession
        PasswordResetScreen(
            loading = authState == AuthState.LOGGING_IN,
            error = authError,
            onSave = { newPassword ->
                authState = AuthState.LOGGING_IN
                authError = null
                executor.execute {
                    try {
                        authRepository.updatePassword(
                            requireNotNull(recoverySession).accessToken,
                            newPassword
                        )
                        mainHandler.post {
                            passwordRecovery = false
                            authState = AuthState.LOGGED_IN
                            authError = null
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            authError = error.message ?: "Password could not be updated."
                            authState = AuthState.LOGGED_IN
                        }
                    }
                }
            }
        )
        return
    }

    val activeSession = currentSession
    if (activeSession == null) {
        AuthProgressScreen()
        return
    }
    AuthenticatedFarmApp(
        session = activeSession,
        repository = farmRepository,
        executor = executor,
        mainHandler = mainHandler,
        onSignOut = ::signOut
    )
    return

    val repo = remember { InMemoryFarmRepository() }
    val family = repo.familyContext()
    val scope = remember { SnackbarHostState() }
    var route by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var offline by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var showQuickAdd by remember { mutableStateOf(false) }

    LaunchedEffect(banner) {
        banner?.let {
            scope.showSnackbar(it)
            banner = null
        }
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(hostState = scope) },
        topBar = {
            if (route == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Forest),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.farm_family_mark),
                                    contentDescription = "FARM FAMILY logo",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(Strings.appName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Forest)
                                Text(family.familyName, fontSize = 12.sp, color = Muted)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { route = "Search" }) {
                            Icon(Icons.Default.Search, contentDescription = "Search farm records")
                        }
                        IconButton(onClick = { route = "Notifications" }) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
                )
            } else {
                TopAppBar(
                    title = { Text(route ?: "", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { route = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
                )
            }
        },
        bottomBar = {
            if (route == null) {
                NavigationBar(containerColor = Color.White) {
                    listOf(
                        MainTab.HOME to Icons.Default.Home,
                        MainTab.RECORDS to Icons.Default.FolderOpen,
                        MainTab.REPORTS to Icons.Default.BarChart,
                        MainTab.AI to Icons.Default.AutoAwesome,
                        MainTab.MORE to Icons.Default.Menu
                    ).forEach { (tab, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(tab.name.lowercase().replaceFirstChar { it.titlecase() }, maxLines = 1) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == null && selectedTab in listOf(MainTab.HOME, MainTab.RECORDS)) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { showQuickAdd = true },
                    containerColor = Forest,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Quick add") }
                )
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            if (route != null) {
                DetailRoute(
                    route = route!!,
                    repo = repo,
                    onRoute = { route = it },
                    onMessage = { banner = it }
                )
            } else {
                when (selectedTab) {
                    MainTab.HOME -> HomeScreen(repo, offline, onToggleOffline = { offline = !offline }, onRoute = { route = it }, onQuickAdd = { showQuickAdd = true })
                    MainTab.RECORDS -> RecordsScreen(repo, onRoute = { route = it }, onQuickAdd = { showQuickAdd = true })
                    MainTab.REPORTS -> ReportsScreen(repo)
                    MainTab.AI -> AiScreen(repo)
                    MainTab.MORE -> MoreScreen(onRoute = { route = it }, onToggleOffline = { offline = !offline }, onLogout = ::signOut)
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onAdd = { kind ->
                showQuickAdd = false
                route = when (kind) {
                    "Field" -> "Add Field"
                    "Expense" -> "Add Expense"
                    else -> kind
                }
            }
        )
    }
}

@Composable
private fun AuthenticatedSetupScreen(email: String, onSignOut: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Cream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(Strings.appName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Forest)
            Spacer(Modifier.height(12.dp))
            Text(
                "Account connected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (email.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(email, color = Muted)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Family setup, farm records, and synchronization are not connected in this build. Demo records are intentionally hidden so they cannot be mistaken for your family's data.",
                color = Muted
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun AuthProgressScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Cream
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Forest)
            Spacer(Modifier.height(16.dp))
            Text("Connecting to your family account...", color = Muted)
        }
    }
}

@Composable
private fun LoginScreen(
    onLogin: () -> Unit,
    onSignUp: (String) -> Unit,
    onRecover: () -> Unit,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    message: String?
) {
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Forest),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.farm_family_mark),
                contentDescription = "FARM FAMILY logo",
                modifier = Modifier.size(72.dp)
            )
        }
        Text(
            if (createAccount) "Start growing together." else "Welcome back.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Forest
        )
        Text(
            if (createAccount) "Create your personal account. You'll choose a family next."
            else "Your family's farm, in one place.",
            color = Muted
        )

        if (createAccount) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; formError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Full name *") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { onPasswordChange(it); formError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )

        if (createAccount) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; formError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm password *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        val visibleMessage = formError ?: message
        if (visibleMessage != null) {
            Surface(
                color = Warning,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Gold)
            ) {
                Text(
                    text = visibleMessage,
                    modifier = Modifier.padding(12.dp),
                    color = ErrorColor,
                    fontSize = 13.sp
                )
            }
        }

        Button(
            onClick = {
                formError = when {
                    createAccount && fullName.isBlank() -> "Enter your full name."
                    createAccount && password != confirmPassword -> "Passwords do not match."
                    else -> null
                }
                if (formError == null) {
                    if (createAccount) onSignUp(fullName.trim()) else onLogin()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text(if (createAccount) "Create account" else "Sign in")
        }

        TextButton(onClick = {
            createAccount = !createAccount
            formError = null
        }) {
            Text(if (createAccount) "Already have an account? Sign in" else "Create an account")
        }
        if (!createAccount) TextButton(onClick = onRecover) { Text("Forgot password?") }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Email and password only. Your family's records stay private.",
                    color = Muted,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    repo: FarmRepository,
    offline: Boolean,
    onToggleOffline: () -> Unit,
    onRoute: (String) -> Unit,
    onQuickAdd: () -> Unit
) {
    val summary = repo.dashboardSummary()
    val recentExpenses = repo.expenses().take(3)

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(repo.familyContext().familyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Saturday, 14 November 2026 · 2083 Kartik 28", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onToggleOffline) {
                    Icon(
                        if (offline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = if (offline) "Offline" else "Online",
                        tint = if (offline) Gold else Green
                    )
                }
            }
        }

        if (offline) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Warning,
                    border = BorderStroke(1.dp, Gold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Gold)
                        Spacer(Modifier.width(10.dp))
                        Text("You are offline. Local changes will sync when connectivity returns.")
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Forest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SEASON OVERVIEW", color = Color(0xFFBED3C5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Rice / Dhan", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Khet A · 2083 BS / 2026 AD", color = Color(0xFFE7EFEA), fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { 0.64f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Color(0xFFB5D38F),
                            trackColor = ForestSoft
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Growing", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            SectionHeader("Farm overview", "See all", { onRoute("Fields") })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SurfaceCard("Fields", summary.fields.toString(), "Active family fields", Icons.Default.Landscape)
                SurfaceCard("Expenses", "NPR ${summary.expenses}", "Season spend", Icons.Default.Payments)
                SurfaceCard("Sales", "NPR ${summary.sales}", "Season income", Icons.Default.TrendingUp)
            }
        }

        item {
            SectionHeader("Recent activity", "View all", { onRoute("Activity") })
            recentExpenses.forEach { expense ->
                ActivityRow(expense)
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Warning,
                border = BorderStroke(1.dp, Gold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Gold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text("Sync status", fontWeight = FontWeight.Bold)
                        Text("${summary.pendingSync} items pending sync", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onRoute("Backup & Export") }) {
                        Text("Manage")
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Leaf,
                modifier = Modifier.fillMaxWidth().clickable { onRoute("FARM FAMILY AI") }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Forest)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text("Ask FARM FAMILY AI", fontWeight = FontWeight.Bold)
                        Text("Use your connected farm records", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Forest)
                }
            }
        }

        item {
            Button(onClick = onQuickAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add a farm record")
            }
        }
    }
}

@Composable
private fun RecordsScreen(repo: FarmRepository, onRoute: (String) -> Unit, onQuickAdd: () -> Unit) {
    val categories = listOf(
        "Fields" to Icons.Default.Landscape,
        "Expenses" to Icons.Default.Payments,
        "Harvest" to Icons.Default.TrendingUp,
        "Sales" to Icons.Default.Assessment,
        "AI" to Icons.Default.SmartToy
    )

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Your farm records", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Use family-approved operations and keep the local queue synchronized.", color = Muted)
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Forest, modifier = Modifier.fillMaxWidth().clickable { onQuickAdd() }) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text("Quick add", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Record work while it is still fresh", color = Color(0xFFDEE8E2), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                }
            }
        }

        items(categories.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(110.dp).clickable { onRoute(label) },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Line)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(icon, contentDescription = null, tint = Green)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Muted)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Recent expenses", "View all", { onRoute("Expenses") })
            repo.expenses().take(2).forEach { expense -> ActivityRow(expense) }
        }
    }
}

@Composable
private fun ReportsScreen(repo: FarmRepository) {
    val summary = repo.dashboardSummary()
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Farm reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Derived from real family data and not static demo values.", color = Muted)
        }
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Season summary", fontWeight = FontWeight.Bold)
                    Text("Status: ${summary.familyHealth}", color = Muted)
                    Text("Fields: ${summary.fields}", color = Muted)
                    Text("Expenses: NPR ${summary.expenses}", color = Muted)
                    Text("Sales: NPR ${summary.sales}", color = Muted)
                }
            }
        }
        item {
            ReportStatRow("Fields", summary.fields.toString())
            ReportStatRow("Active crops", summary.activeCrops.toString())
            ReportStatRow("Land area", summary.landArea)
            ReportStatRow("Pending sync", summary.pendingSync.toString())
        }
    }
}

@Composable
private fun AiScreen(repo: FarmRepository) {
    var question by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<AiResponse?>(null) }
    val suggestions = listOf(
        "How much fertilizer did we spend?",
        "How much rice did we harvest?",
        "What is the current farm overview?"
    )

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Forest, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FARM FAMILY AI", color = Color(0xFFCFE3D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Your farm facts, explained clearly.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        suggestions.forEach { suggestion ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().clickable { question = suggestion; response = repo.askAi(suggestion) }
            ) {
                Text(suggestion, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ask about field, fertilizer, harvest, or sales") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Button(
            onClick = { response = repo.askAi(question) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = question.isNotBlank()
        ) {
            Text("Ask AI")
        }

        response?.let { ai ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ai.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(ai.summary, color = Muted)
                    ai.data.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = Muted)
                            Text(value, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(
    onRoute: (String) -> Unit,
    onToggleOffline: () -> Unit,
    onLogout: () -> Unit
) {
    val rows = listOf(
        "Family members" to Icons.Default.Person,
        "Notifications" to Icons.Default.Notifications,
        "Backup & Export" to Icons.Default.Sync,
        "Settings" to Icons.Default.Settings,
        "Profile" to Icons.Default.AccountCircle,
        "Security" to Icons.Default.Shield
    )

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Family tools, privacy, and account controls", color = Muted)
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = Warning, border = BorderStroke(1.dp, Gold), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Offline preview", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                    TextButton(onClick = onToggleOffline) { Text("Toggle") }
                }
            }
        }
        items(rows) { (label, icon) ->
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().clickable { onRoute(label) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(12.dp))
                    Text(label, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Muted)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun DetailRoute(route: String, repo: FarmRepository, onRoute: (String?) -> Unit, onMessage: (String) -> Unit) {
    when (route) {
        "Add Field" -> AddFieldScreen(onCancel = { onRoute(null) }, onSave = { field -> repo.addField(field); onRoute(null); onMessage("Field saved") })
        "Add Expense" -> AddExpenseScreen(onCancel = { onRoute(null) }, onSave = { expense -> repo.addExpense(expense); onRoute(null); onMessage("Expense saved") })
        "Create Family" -> CreateFamilyScreen(onCancel = { onRoute(null) }, onCreate = { onRoute(null); onMessage("Family created") })
        "Join Family" -> JoinFamilyScreen(onCancel = { onRoute(null) }, onJoin = { onRoute(null); onMessage("Invitation accepted") })
        "Fields" -> FieldListScreen(repo, onRoute)
        "Expenses" -> ExpenseListScreen(repo, onRoute)
        "Search" -> SearchScreen(repo)
        "Notifications" -> GenericListScreen("Notifications", listOf("Backup reminder" to "Sync due in 5 days", "Records synced" to "Latest changes are up to date"), Icons.Default.Notifications)
        "Activity" -> GenericListScreen("Farm activity", listOf("Anil Sharma" to "Updated field record", "Farm team" to "Expense entered for Khet A"), Icons.Default.Person)
        "Family members" -> GenericListScreen("Family members", listOf("Owner · Anil Sharma" to "family@farmfamily.app", "Editor · Sita Sharma" to "sita@farmfamily.app"), Icons.Default.Person)
        "Backup & Export" -> BackupScreen(onMessage)
        "Settings" -> SettingsScreen(onMessage)
        "Profile" -> GenericListScreen("Profile", listOf("Name" to repo.familyContext().userName, "Email" to repo.familyContext().userEmail), Icons.Default.AccountCircle)
        "Security" -> SecurityScreen()
        "FARM FAMILY AI" -> AiScreen(repo)
        else -> GenericListScreen(route, emptyList(), Icons.Default.BugReport)
    }
}

@Composable
private fun FieldListScreen(repo: FarmRepository, onRoute: (String?) -> Unit) {
    val fields = repo.fields()
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Fields", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${fields.size} fields · ${repo.dashboardSummary().landArea}", color = Muted)
                }
                Button(onClick = { onRoute("Add Field") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }
        if (fields.isEmpty()) {
            item { EmptyState("No fields yet", "Add your first field to start managing land and crops.", "Add field", Icons.Default.Landscape) { onRoute("Add Field") } }
        } else {
            items(fields) { field ->
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(field.name, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                            StatusChip(field.status)
                        }
                        Text(field.location, color = Muted, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(field.area, fontWeight = FontWeight.SemiBold)
                            Text(field.crop, color = Green, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseListScreen(repo: FarmRepository, onRoute: (String?) -> Unit) {
    val expenses = repo.expenses()
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Expenses", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Season total: NPR ${repo.dashboardSummary().expenses}", color = Muted)
                }
                Button(onClick = { onRoute("Add Expense") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }
        if (expenses.isEmpty()) {
            item { EmptyState("No expenses yet", "Track the first expense for this season.", "Add expense", Icons.Default.Payments) { onRoute("Add Expense") } }
        } else {
            items(expenses) { expense -> ActivityRow(expense) }
        }
    }
}

@Composable
private fun SearchScreen(repo: FarmRepository) {
    var query by remember { mutableStateOf("") }
    val results = repo.search(query)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search fields, crops, or expenses") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        if (results.isEmpty()) {
            Text("No matching records", color = Muted)
        } else {
            results.forEach { (title, detail) ->
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(detail, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupScreen(onMessage: (String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    val phases = listOf("Choose file", "Validate", "Preview", "Confirm", "Complete")
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Backup & Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Use the backend export/import contract instead of ad-hoc mock files.", color = Muted)
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = Leaf, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Backup health", fontWeight = FontWeight.Bold)
                    Text("Up to date", color = Forest, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Last backup: 2 days ago · Next reminder: 5 days", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PDF", "CSV", "XLSX", "ZIP").forEach { format ->
                    OutlinedButton(onClick = { onMessage("$format export prepared") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(format)
                    }
                }
            }
        }
        item {
            Text("Import workflow", fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { ((step + 1) / phases.size.toFloat()) }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Text(phases[step.coerceAtMost(phases.size - 1)], color = Muted)
        }
        item {
            Button(
                onClick = {
                    step = (step + 1).coerceAtMost(phases.size - 1)
                    if (step == phases.size - 1) onMessage("Import completed")
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(if (step == phases.size - 1) "Confirm import" else "Continue")
            }
        }
    }
}

@Composable
private fun SettingsScreen(onMessage: (String) -> Unit) {
    var language by remember { mutableStateOf("English") }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Configure the family workspace and app experience.", color = Muted)
        }
        item {
            Text("Language", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("English", "नेपाली", "Hindi", "Awadhi").forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = { language = option; onMessage("Language saved") },
                        label = { Text(option, fontSize = 11.sp) }
                    )
                }
            }
        }
        item { ListRow("Units", "Bigha · Kg · Quintal", Icons.Default.Landscape) {} }
        item { ListRow("Notifications", "Backup and family sync", Icons.Default.Notifications) {} }
        item { ListRow("Privacy", "Family permissions and auth", Icons.Default.Shield) {} }
    }
}

@Composable
private fun SecurityScreen() {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Security", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("The app should respect backend permissions and family authorization at all times.", color = Muted)
        }
        item { SecurityRow("Authentication", "Email + password only") }
        item { SecurityRow("Family scoping", "Current member family used for all records") }
        item { SecurityRow("Sync safety", "Server writes use backend RPC and conflict handling") }
        item { SecurityRow("Offline access", "Local cache mirrors family state with clear sync status") }
    }
}

@Composable
private fun GenericListScreen(title: String, entries: List<Pair<String, String>>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        items(entries) { (primary, secondary) ->
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(primary, fontWeight = FontWeight.SemiBold)
                        if (secondary.isNotBlank()) Text(secondary, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateFamilyScreen(onCancel: () -> Unit, onCreate: () -> Unit) {
    var familyName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val farmTypes = listOf("Rice", "Mixed crop", "Vegetables", "Dairy", "Citrus")
    var selectedFarmType by remember { mutableStateOf(farmTypes.first()) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Create family", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Set up the family workspace and farm profile for your household.", color = Muted)
            }
            item { OutlinedTextField(value = familyName, onValueChange = { familyName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Family name") }) }
            item { OutlinedTextField(value = location, onValueChange = { location = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Farm location") }) }
            item {
                Text("Primary farm type", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    farmTypes.forEach { option ->
                        FilterChip(selected = selectedFarmType == option, onClick = { selectedFarmType = option }, label = { Text(option) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancel") }
            Button(
                onClick = onCreate,
                enabled = familyName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Create")
            }
        }
    }
}

@Composable
private fun JoinFamilyScreen(onCancel: () -> Unit, onJoin: () -> Unit) {
    var inviteCode by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Join family", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Use an invitation code to join an existing farm family.", color = Muted)
            }
            item { OutlinedTextField(value = inviteCode, onValueChange = { inviteCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Invitation code") }) }
            item { OutlinedTextField(value = displayName, onValueChange = { displayName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Your name") }) }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancel") }
            Button(
                onClick = onJoin,
                enabled = inviteCode.isNotBlank() && displayName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Join")
            }
        }
    }
}

@Composable
private fun AddFieldScreen(onCancel: () -> Unit, onSave: (FarmField) -> Unit) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var crop by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Add field", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Create a farm field with the current family context.", color = Muted)
            }
            item { OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Field name") }) }
            item { OutlinedTextField(value = location, onValueChange = { location = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Location") }) }
            item { OutlinedTextField(value = area, onValueChange = { area = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Area") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal) ) }
            item { OutlinedTextField(value = crop, onValueChange = { crop = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Primary crop") }) }
            if (error) item { Text("Name and a valid area are required.", color = ErrorColor) }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    val areaValue = area.toDoubleOrNull()
                    if (name.isBlank() || areaValue == null) {
                        error = true
                    } else {
                        onSave(FarmField(localId = "field-${System.currentTimeMillis()}", name = name, location = location.ifBlank { "No location" }, area = "$areaValue Bigha", crop = crop.ifBlank { "Not selected" }, status = "Planning"))
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Save field")
            }
        }
    }
}

@Composable
private fun AddExpenseScreen(onCancel: () -> Unit, onSave: (FarmExpense) -> Unit) {
    var category by remember { mutableStateOf("Fertilizer") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("Khet A") }
    var error by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Add expense", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Record a farm cost using the current family context.", color = Muted)
            }
            item { OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }) }
            item { OutlinedTextField(value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal) ) }
            item {
                Text("Category", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Fertilizer", "Labor", "Tractor", "Seeds", "Fuel").forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                    }
                }
            }
            item {
                Text("Field", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Khet A", "Khet B", "Khet C").forEach { option ->
                        FilterChip(selected = field == option, onClick = { field = option }, label = { Text(option) })
                    }
                }
            }
            if (error) item { Text("Description and amount are required.", color = ErrorColor) }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    val amountValue = amount.toIntOrNull()
                    if (description.isBlank() || amountValue == null || amountValue <= 0) {
                        error = true
                    } else {
                        onSave(FarmExpense(localId = "exp-${System.currentTimeMillis()}", category = category, description = description, amount = amountValue, date = "14 Nov 2026", field = field, syncStatus = SyncStatus.PENDING))
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Save expense")
            }
        }
    }
}

@Composable
private fun ActivityRow(expense: FarmExpense) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Leaf),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Payments, contentDescription = null, tint = Green)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(expense.category, fontWeight = FontWeight.SemiBold)
                Text("${expense.description} · ${expense.field}", color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("NPR ${expense.amount}", fontWeight = FontWeight.Bold)
                Text(expense.date, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        if (action != null) {
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun SurfaceCard(label: String, value: String, caption: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Green)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(caption, fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun ReportStatRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String = "", icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Green)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String, action: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(34.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 12.sp)
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val choices = listOf("Field", "Expense")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose a record type", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            choices.forEach { choice ->
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().clickable { onAdd(choice) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (choice == "Field") Icons.Default.Landscape else Icons.Default.Payments, contentDescription = null, tint = Green)
                        Spacer(Modifier.width(12.dp))
                        Text(choice, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(value: String) {
    Surface(shape = RoundedCornerShape(99.dp), color = Leaf) {
        Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Forest, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecurityRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, color = Muted, fontSize = 12.sp)
        }
    }
}

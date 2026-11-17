package com.farmfamily.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

@Composable
internal fun FarmFamilyApp(
    viewModel: FarmFamilyViewModel,
    initialCallback: Uri?,
    onCallbackHandled: () -> Unit
) {
    val state = viewModel.state.value
    LaunchedEffect(initialCallback) {
        viewModel.start(initialCallback)
        if (initialCallback != null) onCallbackHandled()
    }

    when {
        state.authLoading -> FarmSplashScreen()
        state.passwordRecovery && state.authSession != null -> FarmPasswordResetScreen(
            loading = state.authLoading,
            message = state.authMessage,
            error = state.authMessageIsError,
            onSave = viewModel::setNewPassword,
            onBack = viewModel::clearAuthMessage
        )
        state.authSession == null -> {
            if (state.passwordRecovery) {
                FarmPasswordResetScreen(
                    loading = state.authLoading,
                    message = state.authMessage,
                    error = state.authMessageIsError,
                    onSave = viewModel::setNewPassword,
                    onBack = { viewModel.clearAuthMessage() }
                )
            } else {
                FarmAuthenticationScreen(
                    loading = state.authLoading,
                    message = state.authMessage,
                    messageIsError = state.authMessageIsError,
                    onSignIn = viewModel::signIn,
                    onSignUp = viewModel::signUp,
                    onRecover = viewModel::recoverPassword
                )
            }
        }
        state.familyLoading && state.memberships == null -> FarmSplashScreen("Loading your family")
        state.memberships == null && state.familyError != null -> FarmErrorScreen(
            title = "We couldn’t load your family",
            message = state.familyError,
            onRetry = viewModel::loadFamilies
        )
        state.memberships?.isEmpty() == true -> FarmFamilySetupScreen(
            loading = state.familyLoading,
            error = state.familyError,
            onCreate = viewModel::createFamily,
            onJoin = viewModel::joinFamily
        )
        state.family == null && !state.memberships.isNullOrEmpty() -> FarmFamilyChooserScreen(
            families = state.memberships.orEmpty(),
            onSelect = viewModel::selectFamily
        )
        state.family == null -> FarmSplashScreen("Preparing your farm")
        else -> FarmWorkspaceScreen(viewModel, state)
    }
}

@Composable
internal fun FarmSplashScreen(message: String = "Growing together") {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.farm_family_mark),
            contentDescription = "FARM FAMILY",
            modifier = Modifier.size(88.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        Text("FARM FAMILY", style = MaterialTheme.typography.headlineMedium, color = FarmForest)
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        CircularProgressIndicator(color = FarmGreen, strokeWidth = 2.dp)
    }
}

@Composable
internal fun FarmErrorScreen(title: String, message: String, onRetry: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = FarmForest)
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        androidx.compose.material3.Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
internal fun FarmWorkspaceScreen(viewModel: FarmFamilyViewModel, state: FarmUiState) {
    var selectedTab by rememberSaveable { mutableStateOf(FarmTab.HOME.name) }
    val routeStack = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { values -> mutableStateListOf<String>().apply { addAll(values) } }
        )
    ) { mutableStateListOf<String>() }
    var signOutConfirm by rememberSaveable { mutableStateOf(false) }
    val family = state.family ?: return
    LaunchedEffect(family.id) { viewModel.loadHomeSummary() }
    BackHandler(routeStack.isNotEmpty()) { routeStack.removeAt(routeStack.lastIndex) }

    fun navigate(route: String) { routeStack.add(route) }
    fun back() { if (routeStack.isNotEmpty()) routeStack.removeAt(routeStack.lastIndex) }

    FarmWorkspaceScaffold(
        family = family,
        selectedTab = selectedTab,
        awadhi = viewModel.awadhiLanguage.value,
        nestedTitle = routeStack.lastOrNull()?.let(::farmRouteTitle),
        onSelectTab = { tab -> selectedTab = tab.name; routeStack.clear() },
        onBack = ::back,
        onSearch = { navigate("search") },
        onNotifications = { navigate("notifications") },
        onQuickAdd = { navigate("quick_add") }
    ) { contentModifier ->
        val route = routeStack.lastOrNull()
        if (route != null) {
            FarmNestedRoute(
                route = route,
                modifier = contentModifier,
                viewModel = viewModel,
                state = state,
                onNavigate = ::navigate,
                onBack = ::back,
                onSignOut = { signOutConfirm = true }
            )
        } else {
            when (FarmTab.valueOf(selectedTab)) {
                FarmTab.HOME -> FarmHomeScreen(
                    modifier = contentModifier,
                    family = family,
                    userName = state.authSession?.displayName.orEmpty().ifBlank { state.authSession?.email?.substringBefore('@').orEmpty() },
                    fields = state.records["fields"].orEmpty(),
                    seasons = state.records["crop_seasons"].orEmpty(),
                    report = state.yearReport,
                    weather = state.weather,
                    loading = state.loading,
                    errors = state.errors,
                    onRetry = viewModel::loadHomeSummary,
                    onLoadWeather = viewModel::loadWeather,
                    onNavigate = ::navigate
                )
                FarmTab.RECORDS -> FarmRecordsHubScreen(
                    modifier = contentModifier,
                    family = family,
                    records = state.records,
                    onNavigate = ::navigate
                )
                FarmTab.REPORTS -> FarmReportsScreen(
                    modifier = contentModifier,
                    family = family,
                    report = state.yearReport,
                    loading = "home_report" in state.loading,
                    error = state.errors["home_report"],
                    onRetry = { viewModel.loadYearReport() },
                    onSelectYear = viewModel::loadYearReport,
                    onNavigate = ::navigate
                )
                FarmTab.AI -> FarmAiScreen(
                    modifier = contentModifier,
                    answer = state.aiAnswer,
                    loading = "ai" in state.loading,
                    error = state.errors["ai"],
                    onAsk = viewModel::askAi
                )
                FarmTab.MORE -> FarmMoreScreen(
                    modifier = contentModifier,
                    family = family,
                    session = state.authSession,
                    awadhi = viewModel.awadhiLanguage.value,
                    onNavigate = ::navigate
                )
            }
        }
    }

    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text("Sign out of FARM FAMILY?") },
            text = { Text("Your saved family records will stay in your account.") },
            confirmButton = { TextButton(onClick = { signOutConfirm = false; viewModel.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { signOutConfirm = false }) { Text("Cancel") } }
        )
    }
}

internal enum class FarmTab(val title: String) {
    HOME("Home"), RECORDS("Records"), REPORTS("Reports"), AI("AI"), MORE("More")
}

internal fun farmRouteTitle(route: String): String {
    val parts = route.split(':')
    return when (parts.firstOrNull()) {
        "module" -> FarmModules.byTable(parts.getOrNull(1).orEmpty())?.title ?: "Records"
        "form" -> if (parts.getOrNull(2) == "new") "Add ${FarmModules.byTable(parts.getOrNull(1).orEmpty())?.title?.lowercase().orEmpty().trimEnd('s')}" else "Edit record"
        "detail" -> "Record details"
        "family" -> "Family"
        "members" -> "Family members"
        "invitations" -> "Invitations"
        "notifications" -> "Notifications"
        "search" -> "Search records"
        "backup" -> "Backup & restore"
        "recycle_bin" -> "Recycle bin"
        "profile" -> "Profile"
        "settings" -> "Settings"
        "family_settings" -> "Family settings"
        "quick_add" -> "Add a farm record"
        else -> parts.firstOrNull()?.replaceFirstChar { it.uppercase() }.orEmpty()
    }
}

package com.farmfamily.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class AuthMode { SIGN_IN, CREATE_ACCOUNT, RECOVER }

@Composable
internal fun FarmAuthenticationScreen(
    loading: Boolean,
    message: String?,
    messageIsError: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onRecover: (String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val title = when (mode) {
        AuthMode.SIGN_IN -> "Welcome back"
        AuthMode.CREATE_ACCOUNT -> "Create your account"
        AuthMode.RECOVER -> "Reset your password"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(FarmCream).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = FarmForest, modifier = Modifier.size(52.dp)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.farm_family_mark),
                contentDescription = "FARM FAMILY mark",
                modifier = Modifier.padding(5.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("FARM FAMILY", style = MaterialTheme.typography.labelLarge, color = FarmGreen, letterSpacing = 1.4.sp)
            Text("Grow together. Prosper together.", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = FarmForest)
            Text(
                when (mode) {
                    AuthMode.SIGN_IN -> "Sign in to see your family’s farm records."
                    AuthMode.CREATE_ACCOUNT -> "Start a shared place for your family’s farm work."
                    AuthMode.RECOVER -> "We’ll send a secure recovery link if an account matches."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = FarmMuted
            )
        }

        if (mode == AuthMode.CREATE_ACCOUNT) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; validation = null },
                label = { Text("Your name") },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim(); validation = null },
            label = { Text("Email address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        )
        if (mode != AuthMode.RECOVER) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; validation = null },
                label = { Text("Password") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "Hide password" else "Show password")
                    }
                },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (mode == AuthMode.CREATE_ACCOUNT) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; validation = null },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
        }
        (validation ?: message)?.let { text ->
            FarmNotice(text, isError = validation != null || messageIsError)
        }
        Button(
            onClick = {
                validation = when {
                    !email.contains(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) -> "Enter a valid email address."
                    mode == AuthMode.CREATE_ACCOUNT && displayName.trim().isBlank() -> "Enter your name."
                    mode != AuthMode.RECOVER && password.isBlank() -> "Enter your password."
                    mode == AuthMode.CREATE_ACCOUNT && password.length < 8 -> "Use at least 8 characters for your password."
                    mode == AuthMode.CREATE_ACCOUNT && password != confirmPassword -> "The passwords do not match."
                    else -> null
                }
                if (validation == null) when (mode) {
                    AuthMode.SIGN_IN -> onSignIn(email.trim(), password)
                    AuthMode.CREATE_ACCOUNT -> onSignUp(email.trim(), password, displayName.trim())
                    AuthMode.RECOVER -> onRecover(email.trim())
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FarmWhite)
            else Text(when (mode) {
                AuthMode.SIGN_IN -> "Sign in"
                AuthMode.CREATE_ACCOUNT -> "Create account"
                AuthMode.RECOVER -> "Send recovery link"
            })
        }

        when (mode) {
            AuthMode.SIGN_IN -> {
                TextButton(onClick = { mode = AuthMode.RECOVER; validation = null }) { Text("Forgot password?") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New to FARM FAMILY?", color = FarmMuted, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { mode = AuthMode.CREATE_ACCOUNT; validation = null }) { Text("Create account") }
                }
            }
            AuthMode.CREATE_ACCOUNT -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", color = FarmMuted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { mode = AuthMode.SIGN_IN; validation = null }) { Text("Sign in") }
            }
            AuthMode.RECOVER -> TextButton(onClick = { mode = AuthMode.SIGN_IN; validation = null }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Text("Back to sign in")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Your family’s information stays private to your authorized family members.", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
    }
}

@Composable
internal fun FarmPasswordResetScreen(
    loading: Boolean,
    message: String?,
    error: Boolean,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(FarmCream).imePadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Choose a new password", style = MaterialTheme.typography.headlineMedium, color = FarmForest)
        Spacer(Modifier.height(8.dp))
        Text("Your email recovery link is verified. Set a new password to continue.", color = FarmMuted)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(password, { password = it; validation = null }, label = { Text("New password") }, visualTransformation = PasswordVisualTransformation(), enabled = !loading, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(confirm, { confirm = it; validation = null }, label = { Text("Confirm new password") }, visualTransformation = PasswordVisualTransformation(), enabled = !loading, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        (validation ?: message)?.let { FarmNotice(it, isError = validation != null || error) }
        Button(onClick = {
            validation = when {
                password.length < 8 -> "Use at least 8 characters."
                password != confirm -> "The passwords do not match."
                else -> null
            }
            if (validation == null) onSave(password)
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FarmWhite) else Text("Save new password")
        }
        TextButton(onClick = onBack) { Text("Cancel") }
    }
}

@Composable
internal fun FarmFamilySetupScreen(
    loading: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit
) {
    var joining by rememberSaveable { mutableStateOf(false) }
    var familyName by rememberSaveable { mutableStateOf("") }
    var invitation by rememberSaveable { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(FarmCream).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("A shared place for your farm", style = MaterialTheme.typography.headlineMedium, color = FarmForest)
        Text("Create a family workspace or join one with an invitation code.", style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { joining = false; validation = null }) { Text("Create family") }
            TextButton(onClick = { joining = true; validation = null }) { Text("Join a family") }
        }
        if (joining) {
            OutlinedTextField(invitation, { invitation = it; validation = null }, label = { Text("Invitation code") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(familyName, { familyName = it; validation = null }, label = { Text("Family name") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        }
        (validation ?: error)?.let { FarmNotice(it, isError = true) }
        Button(onClick = {
            validation = when {
                joining && invitation.trim().isBlank() -> "Enter the invitation code you received."
                !joining && familyName.trim().isBlank() -> "Enter a name for your family."
                familyName.trim().length > 160 -> "Family name must be 160 characters or fewer."
                else -> null
            }
            if (validation == null) if (joining) onJoin(invitation.trim()) else onCreate(familyName.trim())
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FarmWhite)
            else Text(if (joining) "Join family" else "Create family")
        }
        Text("Family records are shared only with members you invite.", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
    }
}

@Composable
internal fun FarmFamilyChooserScreen(families: List<FamilyMembership>, onSelect: (FamilyMembership) -> Unit) {
    Column(Modifier.fillMaxSize().background(FarmCream).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose your family", style = MaterialTheme.typography.headlineMedium, color = FarmForest)
        Text("Select the farm workspace you want to open.", style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
        families.forEach { family ->
            Card(
                onClick = { onSelect(family) },
                colors = CardDefaults.cardColors(containerColor = FarmWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, FarmLine),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(family.name, style = MaterialTheme.typography.titleMedium, color = FarmForest)
                    Text("${family.role} · ${family.currency}", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
                }
            }
        }
    }
}

@Composable
internal fun FarmNotice(message: String, isError: Boolean = false) {
    Surface(
        color = if (isError) FarmErrorSurface else FarmLeaf,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) FarmError else FarmForest
        )
    }
}

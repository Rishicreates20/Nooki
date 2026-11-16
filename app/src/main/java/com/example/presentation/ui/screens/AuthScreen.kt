package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.auth.AccountEntity
import com.example.auth.AuthRepository
import com.example.auth.AuthResult
import kotlinx.coroutines.launch

/**
 * Login / sign-up screen. Accounts live only on this phone, and each one gets its own database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onSignedIn: (AccountEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<AccountEntity>>(emptyList()) }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accounts = authRepository.accountsOnThisPhone()
        // First time on this phone: go straight to sign-up.
        if (accounts.isEmpty()) isSignUp = true
    }

    val submit: () -> Unit = submit@{
        if (busy) return@submit
        busy = true
        error = null
        scope.launch {
            val result = if (isSignUp) {
                authRepository.signUp(name, email, password, confirm)
            } else {
                authRepository.logIn(email, password)
            }
            busy = false
            when (result) {
                is AuthResult.Success -> onSignedIn(result.account)
                is AuthResult.Error -> error = result.message
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = CircleShape, color = scheme.primary.copy(alpha = 0.18f)) {
                Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    Text("🦊", style = MaterialTheme.typography.displaySmall)
                }
            }
            Text(
                text = if (isSignUp) "Create your Nooki account" else "Welcome back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your reminders, tasks and health logs stay on this phone, in a space only your account can open.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onBackground.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isSignUp,
                    onClick = { isSignUp = false; error = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("auth_tab_login")
                ) { Text("Log in") }
                SegmentedButton(
                    selected = isSignUp,
                    onClick = { isSignUp = true; error = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("auth_tab_signup")
                ) { Text("Sign up") }
            }

            if (!isSignUp && accounts.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Accounts on this phone",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onBackground.copy(alpha = 0.7f)
                    )
                    accounts.forEach { account ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (email.trim().equals(account.email, ignoreCase = true)) {
                                scheme.primary.copy(alpha = 0.18f)
                            } else {
                                scheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { email = account.email; error = null }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = CircleShape, color = scheme.primary) {
                                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = account.displayName.take(1).uppercase(),
                                            color = scheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(account.displayName, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        account.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSignUp) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("auth_name")
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("auth_email")
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().testTag("auth_password")
            )
            if (isSignUp) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().testTag("auth_confirm_password")
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().testTag("auth_error")
                )
            }

            Button(
                onClick = submit,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("auth_submit")
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = scheme.onPrimary
                    )
                } else {
                    Text(if (isSignUp) "Create account" else "Log in")
                }
            }

            Text(
                text = if (isSignUp) "Already have an account? Log in" else "New here? Create an account",
                color = scheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { isSignUp = !isSignUp; error = null }
                    .padding(8.dp)
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Forgot your password? Accounts are offline, so there is no reset email. Make a new account, or ask whoever set up this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onBackground.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}

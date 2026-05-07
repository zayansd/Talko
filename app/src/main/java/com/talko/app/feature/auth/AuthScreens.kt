package com.talko.app.feature.auth

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TalkoPrimary     = Color(0xFF4C4FDE)
private val TalkoSurface     = Color(0xFFF7F7FF)
private val TalkoError       = Color(0xFFD32F2F)
private val TalkoGrey        = Color(0xFF8B8C9B)
private val TalkoTextPrimary = Color(0xFF1A1A2E)

@Composable
fun AuthLoginScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onToggleMode: () -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(state.resetEmailSent) {
        if (state.resetEmailSent) Toast.makeText(context, "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(Color(0xFF1A1A3E), Color(0xFF2D2D6B), TalkoSurface), startY = 0f, endY = 900f)
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Box(modifier = Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text("💬", fontSize = 36.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Talko", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Effortless communication, redefined.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))

            // Mode tabs
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(4.dp)) {
                    listOf("Sign In" to !state.isRegisterMode, "Create Account" to state.isRegisterMode).forEach { (label, active) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) Color.White else Color.Transparent,
                            onClick = { if (!active) onToggleMode() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(label, modifier = Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (active) TalkoPrimary else Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnimatedContent(targetState = state.isRegisterMode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") { isReg ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (isReg) "Create Account" else "Welcome Back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TalkoTextPrimary)
                            Text(if (isReg) "Choose an email and a password to get started." else "Enter your email and password to sign in.", color = TalkoGrey, fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(value = state.email, onValueChange = onEmailChange, label = { Text("Email address") },
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = TalkoPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)

                    OutlinedTextField(value = state.password, onValueChange = onPasswordChange, label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = TalkoPrimary) },
                        trailingIcon = { IconButton(onClick = onTogglePasswordVisibility) { Icon(if (state.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TalkoGrey) } },
                        visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (state.isRegisterMode) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }, onDone = { onSignIn() }),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true,
                        supportingText = if (state.isRegisterMode) { { Text("At least 6 characters", fontSize = 11.sp, color = TalkoGrey) } } else null)

                    AnimatedVisibility(visible = state.isRegisterMode, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        OutlinedTextField(value = state.confirmPassword, onValueChange = onConfirmPasswordChange, label = { Text("Confirm password") },
                            leadingIcon = { Icon(Icons.Default.LockOpen, null, tint = TalkoPrimary) },
                            trailingIcon = { IconButton(onClick = onToggleConfirmPasswordVisibility) { Icon(if (state.confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TalkoGrey) } },
                            visualTransformation = if (state.confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSignIn() }),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true,
                            isError = state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword,
                            supportingText = if (state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword) {
                                { Text("Passwords do not match", color = TalkoError, fontSize = 11.sp) }
                            } else null)
                    }

                    AnimatedVisibility(visible = !state.isRegisterMode) {
                        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End), enabled = !state.isLoading) {
                            Text("Forgot password?", color = TalkoPrimary, fontSize = 13.sp)
                        }
                    }

                    Button(onClick = onSignIn, enabled = !state.isLoading, shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TalkoPrimary),
                        modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        else {
                            Text(if (state.isRegisterMode) "Create Account" else "Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, null)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.isRegisterMode) "Already have an account?" else "Don't have an account?", color = TalkoGrey, fontSize = 13.sp)
                        TextButton(onClick = onToggleMode, enabled = !state.isLoading) {
                            Text(if (state.isRegisterMode) "Sign In" else "Create one", color = TalkoPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("© 2025 TALKO INC. • VERSION 2.0.4", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Profile Setup Screen ──────────────────────────────────────────────────────

@Composable
fun ProfileSetupScreen(
    state: AuthUiState,
    onSave: (String, String) -> Unit,
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var bio      by rememberSaveable { mutableStateOf("") }
    val context  = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(TalkoSurface).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Talko", color = TalkoPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Person, contentDescription = null, tint = TalkoGrey)
        }
        Text("Complete your profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TalkoTextPrimary)
        Text("Tell the community who you are", color = TalkoGrey)

        Box(
            modifier = Modifier.align(Alignment.CenterHorizontally).size(120.dp).clip(CircleShape).background(TalkoPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = TalkoPrimary.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
        }

        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") },
            placeholder = { Text("How do you want to be called?") },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = TalkoPrimary) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))

        OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Status / Bio") },
            placeholder = { Text("I'm feeling...") },
            leadingIcon = { Icon(Icons.Default.Edit, null, tint = TalkoPrimary) },
            modifier = Modifier.fillMaxWidth().height(110.dp), shape = RoundedCornerShape(14.dp))

        Button(
            onClick = { onSave(fullName, bio) },
            enabled = !state.isLoading && fullName.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TalkoPrimary),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            else {
                Text("Complete Setup", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, null)
            }
        }

        TextButton(onClick = { onSave("__skip__", "") }, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading) {
            Text("Skip for now", color = TalkoGrey, fontSize = 14.sp)
        }

        Text("You can always update this later in Settings.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = TalkoGrey, fontSize = 12.sp)
    }
}

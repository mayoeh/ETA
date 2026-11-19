package edu.virginia.cs4720.eta.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import edu.virginia.cs4720.eta.data.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var signedIn by mutableStateOf(false)
        private set

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) { message = "Enter your email and password."; return }
        viewModelScope.launch {
            busy = true
            message = null
            try {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
                signedIn = true
            } catch (e: Exception) {
                message = e.localizedMessage ?: "Sign-in failed."
            } finally {
                busy = false
            }
        }
    }

    fun createAccount(name: String, email: String, password: String) {
        if (name.isBlank()) { message = "Enter a display name."; return }
        if (email.isBlank() || password.length < 6) { message = "Enter an email and a password of at least 6 characters."; return }
        viewModelScope.launch {
            busy = true
            message = null
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val uid = result.user?.uid ?: error("No user returned")
                UserRepository.createProfile(uid, name.trim().take(50), email.trim())
                signedIn = true
            } catch (e: Exception) {
                message = e.localizedMessage ?: "Could not create the account."
            } finally {
                busy = false
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) { message = "Enter your email first, then tap Forgot password."; return }
        viewModelScope.launch {
            message = try {
                auth.sendPasswordResetEmail(email.trim()).await()
                "Password reset email sent to ${email.trim()}."
            } catch (e: Exception) {
                e.localizedMessage ?: "Could not send the reset email."
            }
        }
    }
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit, vm: LoginViewModel = viewModel()) {
    // rememberSaveable keeps typed text through rotation and process death.
    var mode by rememberSaveable { mutableStateOf("in") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(vm.signedIn) { if (vm.signedIn) onSignedIn() }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo placeholder box, like the wireframe.
            Spacer(Modifier.size(72.dp).border(1.dp, Line))
            Spacer(Modifier.height(12.dp))
            Text("ETA", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Know exactly when to leave.", fontSize = 15.sp, color = Quiet)
        }
        Spacer(Modifier.height(16.dp))

        ChoiceRow(
            options = listOf("in" to "Sign in", "up" to "Create account"),
            selected = mode,
            onSelect = { mode = it },
        )

        if (mode == "up") {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Display name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        vm.message?.let { NoticeBox(it) }

        PrimaryButton(
            text = when {
                vm.busy -> "Please wait…"
                mode == "up" -> "Create account"
                else -> "Sign in"
            },
            onClick = {
                if (mode == "up") vm.createAccount(name, email, password) else vm.signIn(email, password)
            },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode == "in") {
            TextButton(onClick = { vm.resetPassword(email) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Forgot password?", color = Ink, textDecoration = TextDecoration.Underline)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

package com.example.dormia

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dormia.data.FirebaseRepository
import com.example.dormia.data.LocationHelper
import com.example.dormia.data.SessionStore
import com.example.dormia.data.SleepDiagnosticsStore
import com.example.dormia.sleep.SleepService
import com.example.dormia.sleep.SleepSubscriptionManager
import com.example.dormia.ui.theme.DormiaTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DormiaTheme(darkTheme = true, dynamicColor = false) {
                DormiaApp()
            }
        }
    }
}

@Composable
fun DormiaApp(viewModel: DormiaViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(uiState.route) {
        val targetRoute = uiState.route
        if (navController.currentDestination?.route != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "loading",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("loading") {
            FullScreenMessage("Loading DORMIA...")
        }
        composable("signin") {
            SignInScreen(onSignIn = { viewModel.startGoogleSignIn(signInLauncher) }, uiState.errorMessage)
        }
        composable("onboarding") {
            OnboardingScreen(
                uiState = uiState,
                onDetectLocation = { viewModel.detectAndSaveLocation() },
                onSave = { target, continent, country, city ->
                    viewModel.finishOnboarding(target, continent, country, city)
                }
            )
        }
        composable("main") {
            MainTrackingScreen(
                uiState = uiState,
                onFallAsleep = { viewModel.fallAsleep() },
                onWakeUp = { viewModel.wakeUp() }
            )
            LaunchedEffect(Unit) {
                while (isActive) {
                    viewModel.refreshDiagnostics()
                    delay(3000)
                }
            }
            RequestRuntimePermissions(
                onPermissionsReady = {
                    viewModel.ensureSleepTrackingStarted(context)
                    viewModel.detectAndSaveLocation()
                }
            )
        }
    }
}

@Composable
private fun SignInScreen(onSignIn: () -> Unit, errorMessage: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DORMIA",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6C63FF)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Sleep is now a sport.",
            color = Color.White
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text("Sign in with Google")
        }
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = errorMessage, color = Color(0xFFFF6B6B))
        }
    }
}

@Composable
private fun OnboardingScreen(
    uiState: DormiaUiState,
    onDetectLocation: () -> Unit,
    onSave: (Int, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            onDetectLocation()
        }
    }
    var target by remember { mutableStateOf(uiState.sleepTargetHours.toFloat()) }
    var continent by remember { mutableStateOf(uiState.continent.ifBlank { "Unknown" }) }
    var country by remember { mutableStateOf(uiState.country.ifBlank { "Unknown" }) }
    var city by remember { mutableStateOf(uiState.city.ifBlank { "Unknown" }) }
    LaunchedEffect(uiState.continent) {
        val latest = uiState.continent.ifBlank { "Unknown" }
        if (latest != continent) {
            continent = latest
        }
    }
    LaunchedEffect(uiState.country) {
        val latest = uiState.country.ifBlank { "Unknown" }
        if (latest != country) {
            country = latest
        }
    }
    LaunchedEffect(uiState.city) {
        val latest = uiState.city.ifBlank { "Unknown" }
        if (latest != city) {
            city = latest
        }
    }
    val continentOptions = listOf(
        "Unknown",
        "North America",
        "South America",
        "Europe",
        "Africa",
        "Asia",
        "Oceania"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome to DORMIA", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text("Set your sleep target and continent.", color = Color.White)
        Text("Sleep Target: ${target.toInt()} hours", color = Color.White)
        Slider(
            value = target,
            onValueChange = { target = it },
            valueRange = 4f..12f,
            steps = 7
        )
        Button(
            onClick = {
                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    onDetectLocation()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Auto-detect from GPS")
        }
        Text("Country: $country", color = Color.White)
        Text("City: $city", color = Color.White)
        Text("Continent", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            continentOptions.forEach { option ->
                Button(
                    onClick = { continent = option },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (option == continent) Color(0xFF6C63FF) else Color(0xFF333340)
                    )
                ) { Text(option) }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onSave(target.toInt(), continent, country, city) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
        if (!uiState.errorMessage.isNullOrBlank()) {
            Text(uiState.errorMessage, color = Color(0xFFFF6B6B))
        }
    }
}

@Composable
private fun MainTrackingScreen(
    uiState: DormiaUiState,
    onFallAsleep: () -> Unit,
    onWakeUp: () -> Unit
) {
    val status = if (uiState.isAsleep) "You are asleep" else "Sleep tracking active"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(status, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text("Current streak: ${uiState.currentStreak}", color = Color.White)
        Text("Target: ${uiState.sleepTargetHours} hours", color = Color.White)
        Text("Continent: ${uiState.continent.ifBlank { "Unknown" }}", color = Color.White)
        Text("Country: ${uiState.country.ifBlank { "Unknown" }}", color = Color.White)
        Text("City: ${uiState.city.ifBlank { "Unknown" }}", color = Color.White)
        if (uiState.lastActionText.isNotBlank()) {
            Text("Last action: ${uiState.lastActionText}", color = Color(0xFFB0B0C5))
        }
        Text("Sleep API diagnostics", color = Color(0xFFB0B0C5), style = MaterialTheme.typography.titleMedium)
        Text("Notifications enabled: ${if (uiState.notificationsEnabled) "Yes" else "No"}", color = Color.White)
        Text("Service running: ${if (uiState.serviceRunning) "Yes" else "No"}", color = Color.White)
        Text("Subscription active: ${if (uiState.subscriptionActive) "Yes" else "No"}", color = Color.White)
        if (uiState.lastSleepApiEventText.isNotBlank()) {
            Text("Last Sleep API event: ${uiState.lastSleepApiEventText}", color = Color(0xFFB0B0C5))
        }
        if (uiState.lastSleepApiError.isNotBlank()) {
            Text("Sleep API error: ${uiState.lastSleepApiError}", color = Color(0xFFFF6B6B))
        }
        Button(
            onClick = onFallAsleep,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA78BFA)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text("Fall Asleep")
        }
        Button(
            onClick = onWakeUp,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text("Wake Up")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("Open dormia.vercel.app/live to see the map", color = Color(0xFFB0B0C5))
        if (!uiState.errorMessage.isNullOrBlank()) {
            Text(uiState.errorMessage, color = Color(0xFFFF6B6B))
        }
    }
}

@Composable
private fun FullScreenMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, color = Color.White)
    }
}

@Composable
private fun RequestRuntimePermissions(onPermissionsReady: () -> Unit) {
    val context = LocalContext.current
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) onPermissionsReady()
    }

    LaunchedEffect(Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onPermissionsReady()
        } else {
            launcher.launch(missing.toTypedArray())
        }
    }
}

data class DormiaUiState(
    val route: String = "loading",
    val uid: String = "",
    val isAsleep: Boolean = false,
    val sleepTargetHours: Int = 7,
    val currentStreak: Int = 0,
    val continent: String = "",
    val country: String = "",
    val city: String = "",
    val lastActionText: String = "",
    val notificationsEnabled: Boolean = true,
    val serviceRunning: Boolean = false,
    val subscriptionActive: Boolean = false,
    val lastSleepApiEventText: String = "",
    val lastSleepApiError: String = "",
    val errorMessage: String? = null
)

class DormiaViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirebaseRepository()
    private val locationHelper = LocationHelper(application.applicationContext)
    private val appContext = application.applicationContext
    private var playerListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _uiState = MutableStateFlow(DormiaUiState())
    val uiState: StateFlow<DormiaUiState> = _uiState.asStateFlow()

    private val googleClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(appContext.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(appContext, options)
    }

    fun initialize() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(route = "signin")
            return
        }
        viewModelScope.launch {
            try {
                SessionStore.setUid(appContext, currentUser.uid)
                repository.ensurePlayerProfile(
                    uid = currentUser.uid,
                    displayName = currentUser.displayName ?: "Dormia Player",
                    email = currentUser.email ?: ""
                )
                repository.markAndroidApkPresent(currentUser.uid)
                syncLocationBestEffort(currentUser.uid)
                loadPlayerAndRoute(currentUser.uid)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    route = "signin",
                    errorMessage = e.message ?: "Unable to load profile."
                )
            }
        }
    }

    fun startGoogleSignIn(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        launcher.launch(googleClient.signInIntent)
    }

    fun handleGoogleSignInResult(data: Intent?) {
        if (data == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Google sign-in canceled.")
            return
        }
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.result
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: error("No Firebase user")
                SessionStore.setUid(appContext, user.uid)
                repository.ensurePlayerProfile(
                    uid = user.uid,
                    displayName = user.displayName ?: "Dormia Player",
                    email = user.email ?: ""
                )
                repository.markAndroidApkPresent(user.uid)
                syncLocationBestEffort(user.uid)
                loadPlayerAndRoute(user.uid)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    route = "signin",
                    errorMessage = e.message ?: "Unable to sign in."
                )
            }
        }
    }

    fun detectAndSaveLocation() {
        val uid = _uiState.value.uid
        if (uid.isBlank()) return
        viewModelScope.launch {
            try {
                val detected = locationHelper.detectBestLocation()
                if (detected == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Unable to detect location right now. Check location/internet and retry."
                    )
                    return@launch
                }
                val details = detected.details
                repository.updateLocation(
                    uid = uid,
                    continent = details.continent,
                    country = details.country,
                    city = details.city,
                    latitude = detected.latitude,
                    longitude = detected.longitude
                )
                _uiState.value = _uiState.value.copy(
                    continent = details.continent,
                    country = details.country,
                    city = details.city,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun finishOnboarding(targetHours: Int, continent: String, country: String, city: String) {
        val uid = _uiState.value.uid
        if (uid.isBlank()) return
        viewModelScope.launch {
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("players")
                    .document(uid)
                    .update(
                        mapOf(
                            "sleepTargetHours" to targetHours,
                            "continent" to continent,
                            "country" to country,
                            "city" to city
                        )
                    ).await()
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .reference.child("dormia/players").child(uid)
                    .updateChildren(
                        mapOf(
                            "continent" to continent,
                            "country" to country,
                            "city" to city
                        )
                    ).await()
                _uiState.value = _uiState.value.copy(route = "main")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun fallAsleep() {
        val uid = _uiState.value.uid
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.updateSleepState(uid, isAsleep = true, updateStart = true)
        }
    }

    fun wakeUp() {
        val uid = _uiState.value.uid
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.updateSleepState(uid, isAsleep = false, updateEnd = true)
        }
    }

    fun ensureSleepTrackingStarted(context: Context) {
        val uid = _uiState.value.uid
        if (uid.isBlank()) return
        try {
            startForegroundService(context, Intent(context, SleepService::class.java))
            SleepDiagnosticsStore.setServiceRunning(context, true)
            SleepSubscriptionManager.subscribe(context)
            SleepDiagnosticsStore.setSubscriptionActive(context, true)
            SleepDiagnosticsStore.clearError(context)
            refreshDiagnostics()
        } catch (_: SecurityException) {
            SleepDiagnosticsStore.setSubscriptionActive(context, false)
            SleepDiagnosticsStore.setLastError(context, "Sleep permission required.")
            refreshDiagnostics()
            _uiState.value = _uiState.value.copy(errorMessage = "Sleep permission required.")
        } catch (e: Exception) {
            SleepDiagnosticsStore.setSubscriptionActive(context, false)
            SleepDiagnosticsStore.setLastError(context, e.message ?: "Unable to start sleep tracking.")
            refreshDiagnostics()
        }
    }

    private suspend fun syncLocationBestEffort(uid: String) {
        runCatching {
            val detected = locationHelper.detectBestLocation() ?: return
            repository.updateLocation(
                uid = uid,
                continent = detected.details.continent,
                country = detected.details.country,
                city = detected.details.city,
                latitude = detected.latitude,
                longitude = detected.longitude
            )
            _uiState.value = _uiState.value.copy(
                continent = detected.details.continent,
                country = detected.details.country,
                city = detected.details.city
            )
        }
    }

    private fun loadPlayerAndRoute(uid: String) {
        playerListener?.remove()
        _uiState.value = _uiState.value.copy(uid = uid, errorMessage = null)
        playerListener = repository.observePlayer(uid) { profile ->
            _uiState.value = _uiState.value.copy(
                uid = uid,
                isAsleep = profile.isAsleep,
                sleepTargetHours = profile.sleepTargetHours,
                currentStreak = profile.currentStreak,
                continent = profile.continent,
                country = profile.country,
                city = profile.city,
                lastActionText = buildLastActionText(profile),
                route = if (profile.continent.isBlank()) "onboarding" else "main"
            )
        }
    }

    fun refreshDiagnostics() {
        val diagnostics = SleepDiagnosticsStore.snapshot(appContext)
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        _uiState.value = _uiState.value.copy(
            notificationsEnabled = notificationsEnabled,
            serviceRunning = diagnostics.serviceRunning,
            subscriptionActive = diagnostics.subscriptionActive,
            lastSleepApiEventText = formatSleepEvent(diagnostics.lastEventType, diagnostics.lastEventMs, diagnostics.lastConfidence),
            lastSleepApiError = diagnostics.lastError
        )
    }

    private fun formatSleepEvent(type: String, eventMs: Long?, confidence: Int?): String {
        if (type.isBlank() || eventMs == null) return ""
        val time = formatTime(eventMs)
        return when (type) {
            "classify" -> {
                if (confidence != null) "Classify at $time (confidence $confidence)"
                else "Classify at $time"
            }

            "segment" -> "Segment at $time"
            else -> "$type at $time"
        }
    }

    private fun buildLastActionText(profile: com.example.dormia.data.PlayerProfile): String {
        val lastStart = profile.lastSleepStartMs
        val lastEnd = profile.lastSleepEndMs
        return when {
            lastStart == null && lastEnd == null -> ""
            lastEnd == null || (lastStart != null && lastStart >= lastEnd) -> {
                "Fell asleep at ${formatTime(lastStart ?: return "")}"
            }

            else -> "Woke up at ${formatTime(lastEnd)}"
        }
    }

    private fun formatTime(epochMs: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(epochMs))
    }

    override fun onCleared() {
        playerListener?.remove()
        super.onCleared()
    }
}
package com.qweet.rider.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qweet.rider.data.*
import kotlinx.coroutines.launch

private enum class ProfileSubScreen { MAIN, PERSONAL_INFO, KYC, BANK, PRIVACY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit, onOpenSupport: () -> Unit) {
    var sub by remember { mutableStateOf(ProfileSubScreen.MAIN) }

    when (sub) {
        ProfileSubScreen.KYC -> KycScreen(onBack = { sub = ProfileSubScreen.MAIN })
        ProfileSubScreen.BANK -> BankScreen(onBack = { sub = ProfileSubScreen.MAIN })
        ProfileSubScreen.PRIVACY -> PrivacyPolicyScreen(onBack = { sub = ProfileSubScreen.MAIN })
        ProfileSubScreen.PERSONAL_INFO -> PersonalInfoScreen(onBack = { sub = ProfileSubScreen.MAIN })
        ProfileSubScreen.MAIN -> ProfileMainScreen(
            onOpenSupport = onOpenSupport,
            onLogout = onLogout,
            onOpenPersonalInfo = { sub = ProfileSubScreen.PERSONAL_INFO },
            onOpenKyc = { sub = ProfileSubScreen.KYC },
            onOpenBank = { sub = ProfileSubScreen.BANK },
            onOpenPrivacy = { sub = ProfileSubScreen.PRIVACY }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileMainScreen(
    onOpenSupport: () -> Unit,
    onLogout: () -> Unit,
    onOpenPersonalInfo: () -> Unit,
    onOpenKyc: () -> Unit,
    onOpenBank: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }
    var uploadingAvatar by remember { mutableStateOf(false) }

    var account by remember { mutableStateOf<AccountDto?>(null) }
    var rider by remember { mutableStateOf<RiderProfileDto?>(null) }

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val part = context.uriToMultipartPart(uri, "avatar")
        if (part == null) {
            errorText = "Couldn't read the selected photo. Try again."
            return@rememberLauncherForActivityResult
        }
        uploadingAvatar = true
        scope.launch {
            val result = runCatching { ApiClient.service.updateAvatar(part) }
            val resp = result.getOrNull()?.body()
            if (resp?.success == true) {
                errorText = null
                retryTick++
            } else {
                errorText = resp?.errors?.joinToString(" ") ?: resp?.error ?: describeFailure(result)
            }
            uploadingAvatar = false
        }
    }

    LaunchedEffect(retryTick) {
        loading = true
        val result = runCatching { ApiClient.service.me() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            errorText = null
            account = body.data.user
            rider = body.data.rider
        } else {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorText?.let { msg -> ErrorBanner(message = msg, onRetry = { retryTick++ }) }
            successText?.let { SuccessBanner(it) }

            if (loading && account == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            rider?.let { r ->
                ProfileHeaderCard(
                    name = account?.name ?: "Rider",
                    rider = r,
                    uploadingAvatar = uploadingAvatar,
                    onChangePhoto = { avatarLauncher.launch("image/*") }
                )

                PerformanceStatsRow(rider = r)

                VehicleCard(rider = r, onClick = onOpenKyc)

                DocumentsCard(kycStatus = r.kyc_status, onClick = onOpenKyc)

                SettingsList(
                    onOpenPersonalInfo = onOpenPersonalInfo,
                    onOpenKyc = onOpenKyc,
                    onOpenBank = onOpenBank,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenSupport = onOpenSupport
                )
            }

            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Log out", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Avatar + name + partner-since + verified/KYC badge, matching the provided design. */
@Composable
private fun ProfileHeaderCard(
    name: String,
    rider: RiderProfileDto,
    uploadingAvatar: Boolean,
    onChangePhoto: () -> Unit
) {
    val partnerSince = rider.partner_since?.let { formatPartnerSince(it) }

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!rider.avatar_url.isNullOrBlank()) {
                        AsyncImage(
                            model = rider.avatar_url,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.first().uppercase() }.ifBlank { "R" }
                        Text(initials, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (uploadingAvatar) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White)
                        }
                    }
                }
                IconButton(
                    onClick = onChangePhoto,
                    modifier = Modifier
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (partnerSince != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Partner since $partnerSince",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rider.kyc_status == "approved") {
                    StatusBadge("Verified Partner", BadgeTone.SUCCESS)
                } else {
                    StatusBadge("KYC ${rider.kyc_status.replaceFirstChar { it.uppercase() }.replace('_', ' ')}", BadgeTone.WARNING)
                }
            }
        }
    }
}

@Composable
private fun PerformanceStatsRow(rider: RiderProfileDto) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(icon = Icons.Default.Star, value = "%.1f".format(rider.rating_avg), label = "Rating", modifier = Modifier.weight(1f))
        StatTile(icon = Icons.Default.LocalShipping, value = rider.completed_deliveries.toString(), label = "Deliveries", modifier = Modifier.weight(1f))
        StatTile(icon = Icons.Default.CheckCircle, value = rider.status.replaceFirstChar { it.uppercase() }, label = "Status", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.height(96.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VehicleCard(rider: RiderProfileDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Active Vehicle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClick) { Text(if (rider.kyc_locked) "View" else "Add") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rider.vehicle_model?.ifBlank { null } ?: "Not added yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!rider.vehicle_number.isNullOrBlank()) {
                        Text(
                            rider.vehicle_number!!.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (rider.kyc_locked) {
                    StatusBadge(
                        when (rider.kyc_status) {
                            "approved" -> "Approved"
                            "pending" -> "Pending"
                            "rejected" -> "Rejected"
                            else -> rider.kyc_status
                        },
                        when (rider.kyc_status) {
                            "approved" -> BadgeTone.SUCCESS
                            "pending" -> BadgeTone.WARNING
                            "rejected" -> BadgeTone.ERROR
                            else -> BadgeTone.NEUTRAL
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentsCard(kycStatus: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderShared, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("Documents", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusBadge(
                when (kycStatus) {
                    "approved" -> "Verified"
                    "pending" -> "Pending Review"
                    "rejected" -> "Rejected"
                    else -> "Not Submitted"
                },
                when (kycStatus) {
                    "approved" -> BadgeTone.SUCCESS
                    "pending" -> BadgeTone.WARNING
                    "rejected" -> BadgeTone.ERROR
                    else -> BadgeTone.NEUTRAL
                }
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsList(
    onOpenPersonalInfo: () -> Unit,
    onOpenKyc: () -> Unit,
    onOpenBank: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSupport: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            SettingsRow(Icons.Default.Person, "Personal Information", onOpenPersonalInfo)
            HorizontalDivider()
            SettingsRow(Icons.Default.Badge, "KYC & Vehicle Details", onOpenKyc)
            HorizontalDivider()
            SettingsRow(Icons.Default.AccountBalance, "Bank Details", onOpenBank)
            HorizontalDivider()
            SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Help & Support", onOpenSupport)
            HorizontalDivider()
            SettingsRow(Icons.Default.Policy, "Privacy Policy", onOpenPrivacy)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

private fun formatPartnerSince(raw: String): String? {
    val datePart = raw.take(10) // "YYYY-MM-DD"
    val parts = datePart.split("-")
    if (parts.size != 3) return null
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return null
    if (monthIndex !in months.indices) return null
    return "${months[monthIndex]} ${parts[0]}"
}

/** Simple account-details editor (email/phone/username) — split out of the main hub. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalInfoScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }

    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    LaunchedEffect(retryTick) {
        loading = true
        val result = runCatching { ApiClient.service.me() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            errorText = null
            email = body.data.user.email.orEmpty()
            phone = body.data.user.phone.orEmpty()
            username = body.data.user.username.orEmpty()
        } else {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Information", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorText?.let { msg -> ErrorBanner(message = msg, onRetry = { retryTick++ }) }
            successText?.let { SuccessBanner(it) }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            SectionCard(title = "Account details", icon = Icons.Default.Person) {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Button(
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = runCatching { ApiClient.service.updateAccount(UpdateAccountRequest(email, phone, username)) }
                            val resp = result.getOrNull()?.body()
                            if (resp?.success == true) {
                                errorText = null
                                successText = resp.message ?: "Account details updated."
                            } else {
                                errorText = resp?.errors?.joinToString(" ") ?: resp?.error ?: describeFailure(result)
                            }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (saving) "Saving…" else "Save account details") }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

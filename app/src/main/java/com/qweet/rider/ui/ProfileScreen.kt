package com.qweet.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweet.rider.data.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    var account by remember { mutableStateOf<AccountDto?>(null) }
    var rider by remember { mutableStateOf<RiderProfileDto?>(null) }

    // Editable fields
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var bankAccountName by remember { mutableStateOf("") }
    var bankAccountNumber by remember { mutableStateOf("") }
    var bankIfsc by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }

    LaunchedEffect(retryTick) {
        loading = true
        val result = runCatching { ApiClient.service.me() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            errorText = null
            account = body.data.user
            rider = body.data.rider
            email = body.data.user.email.orEmpty()
            phone = body.data.user.phone.orEmpty()
            username = body.data.user.username.orEmpty()
            vehicleType = body.data.rider.vehicle_type.orEmpty()
            vehicleNumber = body.data.rider.vehicle_number.orEmpty()
            licenseNumber = body.data.rider.license_number.orEmpty()
            bankAccountName = body.data.rider.bank_account_name.orEmpty()
            bankAccountNumber = body.data.rider.bank_account_number.orEmpty()
            bankIfsc = body.data.rider.bank_ifsc.orEmpty()
            upiId = body.data.rider.upi_id.orEmpty()
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
            successText?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (loading && account == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            rider?.let { r ->
                ProfileHeroCard(name = account?.name ?: "Rider", email = account?.email, rider = r)
            }

            SectionCard(title = "Account details", icon = Icons.Default.Person) {
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Button(
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = runCatching {
                                ApiClient.service.updateAccount(UpdateAccountRequest(email, phone, username))
                            }
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

            SectionCard(title = "Vehicle details", icon = Icons.Default.DirectionsBike) {
                OutlinedTextField(value = vehicleType, onValueChange = { vehicleType = it }, label = { Text("Vehicle type (e.g. Bike, Scooter)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = vehicleNumber, onValueChange = { vehicleNumber = it }, label = { Text("Vehicle number") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, label = { Text("License number") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Button(
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        saving = true
                        scope.launch {
                            val plainText = "text/plain".toMediaTypeOrNull()
                            val result = runCatching {
                                ApiClient.service.updateVehicle(
                                    vehicleType.toRequestBody(plainText),
                                    vehicleNumber.toRequestBody(plainText),
                                    licenseNumber.toRequestBody(plainText)
                                )
                            }
                            val resp = result.getOrNull()?.body()
                            if (resp?.success == true) {
                                errorText = null
                                successText = resp.message ?: "Vehicle details updated."
                            } else {
                                errorText = resp?.errors?.joinToString(" ") ?: resp?.error ?: describeFailure(result)
                            }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (saving) "Saving…" else "Save vehicle details") }
            }

            SectionCard(title = "Payout details", icon = Icons.Default.AccountBalance) {
                OutlinedTextField(value = bankAccountName, onValueChange = { bankAccountName = it }, label = { Text("Account holder name") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = bankAccountNumber, onValueChange = { bankAccountNumber = it }, label = { Text("Account number") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = bankIfsc, onValueChange = { bankIfsc = it }, label = { Text("IFSC code") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = upiId, onValueChange = { upiId = it }, label = { Text("UPI ID") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Button(
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = runCatching {
                                ApiClient.service.updateBank(
                                    UpdateBankRequest(bankAccountName, bankAccountNumber, bankIfsc, upiId)
                                )
                            }
                            val resp = result.getOrNull()?.body()
                            if (resp?.success == true) {
                                errorText = null
                                successText = resp.message ?: "Payout details updated."
                            } else {
                                errorText = resp?.error ?: describeFailure(result)
                            }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (saving) "Saving…" else "Save payout details") }
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
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

/** Attractive gradient hero card: avatar initials, name, email, rating and status chips. */
@Composable
private fun ProfileHeroCard(name: String, email: String?, rider: RiderProfileDto) {
    val gradient = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "R" }

    Card(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initials,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!email.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(email, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${"%.1f".format(rider.rating_avg)} rating",
                            color = Color.White.copy(alpha = 0.95f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStatusChip(
                    label = rider.status.replaceFirstChar { it.uppercase() },
                    icon = Icons.Default.Badge,
                    tint = statusColorFor(rider.status)
                )
                HeroStatusChip(
                    label = "KYC ${rider.kyc_status.replaceFirstChar { it.uppercase() }}",
                    icon = Icons.Default.VerifiedUser,
                    tint = statusColorFor(rider.kyc_status)
                )
            }

            if (rider.kyc_status == "rejected" && !rider.kyc_rejection_reason.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(12.dp)
                ) {
                    Text(
                        "KYC rejected: ${rider.kyc_rejection_reason}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun statusColorFor(status: String): Color = when (status.lowercase()) {
    "active", "approved", "verified" -> Color(0xFF66BB6A)
    "pending", "under_review" -> Color(0xFFFFB74D)
    "rejected", "suspended", "blocked", "inactive" -> Color(0xFFEF5350)
    else -> Color.White
}

@Composable
private fun HeroStatusChip(label: String, icon: ImageVector, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.20f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
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

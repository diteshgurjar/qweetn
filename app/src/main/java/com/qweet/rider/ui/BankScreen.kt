package com.qweet.rider.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweet.rider.data.ApiClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }

    var status by remember { mutableStateOf("not_submitted") }
    var verified by remember { mutableStateOf<com.qweet.rider.data.VerifiedBankDto?>(null) }
    var pending by remember { mutableStateOf<Map<String, String>?>(null) }

    var accountHolderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(retryTick) {
        loading = true
        val result = runCatching { ApiClient.service.getBank() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            errorText = null
            status = body.data.status
            verified = body.data.verified
            pending = body.data.pending
        } else {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bank Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
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

            if (loading && status == "not_submitted" && verified == null && pending == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            Text(
                "This is where Admin sends your earnings settlement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (status) {
                "pending" -> {
                    InfoBanner(
                        "Pending Admin verification. It will be added to your account after verification is complete.",
                        isError = false
                    )
                    pending?.let { p ->
                        SectionCard(title = "Submitted Details", icon = androidx.compose.material.icons.Icons.Default.AccountBalance) {
                            ReadOnlyRowPublic("Account Holder", p["bank_account_holder_name"])
                            ReadOnlyRowPublic("Account Number", p["bank_account_number"])
                            ReadOnlyRowPublic("Bank", p["bank_name"])
                            ReadOnlyRowPublic("IFSC", p["bank_ifsc"])
                            ReadOnlyRowPublic("Branch", p["bank_branch"])
                        }
                    }
                }
                "verified" -> {
                    verified?.let { v ->
                        SectionCard(title = "Payout Account", icon = androidx.compose.material.icons.Icons.Default.AccountBalance) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge("Verified", BadgeTone.SUCCESS)
                            }
                            Spacer(Modifier.height(10.dp))
                            ReadOnlyRowPublic("Account Holder", v.bank_account_holder_name)
                            ReadOnlyRowPublic("Account Number", v.bank_account_number)
                            ReadOnlyRowPublic("Bank", v.bank_name)
                            ReadOnlyRowPublic("IFSC", v.bank_ifsc)
                            ReadOnlyRowPublic("Branch", v.bank_branch)
                        }
                    }
                }
                else -> {
                    InfoBanner("No bank details on file yet.", isError = false)
                }
            }

            if (status != "pending") {
                SectionCard(
                    title = if (status == "verified") "Change Bank Details" else "Add Bank Details",
                    icon = androidx.compose.material.icons.Icons.Default.AccountBalance
                ) {
                    OutlinedTextField(accountHolderName, { accountHolderName = it }, label = { Text("Account Holder Name") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(bankName, { bankName = it }, label = { Text("Bank Name") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(accountNumber, { accountNumber = it }, label = { Text("Account Number") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(ifsc, { ifsc = it.uppercase() }, label = { Text("IFSC Code") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    DocumentPickerRow("Account Holder Photo", false, photoUri?.lastPathSegment, acceptPdf = false) { photoUri = it }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        enabled = !saving,
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            if (accountHolderName.isBlank() || accountNumber.isBlank() || bankName.isBlank() || ifsc.isBlank() || branch.isBlank() || photoUri == null) {
                                errorText = "Please fill all fields and upload the account holder photo."
                                return@Button
                            }
                            saving = true
                            scope.launch {
                                val plain = "text/plain".toMediaTypeOrNull()
                                val fields = mapOf(
                                    "bank_account_holder_name" to accountHolderName,
                                    "bank_account_number" to accountNumber,
                                    "bank_name" to bankName,
                                    "bank_ifsc" to ifsc,
                                    "bank_branch" to branch
                                ).mapValues { it.value.toRequestBody(plain) }
                                val photoPart = context.uriToMultipartPart(photoUri!!, "bank_account_holder_photo")
                                if (photoPart == null) {
                                    errorText = "Couldn't read the selected photo. Try again."
                                    saving = false
                                    return@launch
                                }
                                val result = runCatching { ApiClient.service.submitBank(fields, photoPart) }
                                val resp = result.getOrNull()?.body()
                                if (resp?.success == true) {
                                    errorText = null
                                    successText = resp.message ?: "Bank details submitted."
                                    retryTick++
                                } else {
                                    errorText = resp?.errors?.joinToString(" ") ?: resp?.error ?: describeFailure(result)
                                }
                                saving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(if (saving) "Submitting…" else "Submit for Verification") }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReadOnlyRowPublic(label: String, value: String?) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.ifBlank { "—" } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

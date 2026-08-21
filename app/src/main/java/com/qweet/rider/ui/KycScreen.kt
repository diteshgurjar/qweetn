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
import com.qweet.rider.data.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private val vehicleTypes = listOf("bike" to "Bike", "scooter" to "Scooter", "bicycle" to "Bicycle", "car" to "Car")
private val idDocTypes = listOf("aadhar" to "Aadhar Card", "pan" to "PAN Card", "dl" to "Driving Licence", "voter_id" to "Voter ID")

private val editableFieldLabels = linkedMapOf(
    "vehicle_type" to "Vehicle Type",
    "vehicle_number" to "Vehicle Number",
    "vehicle_model" to "Vehicle Model",
    "vehicle_chassis_number" to "Chassis Number",
    "vehicle_engine_number" to "Engine Number",
    "vehicle_insurance_number" to "Insurance Number",
    "vehicle_insurance_expiry" to "Insurance Due Date (YYYY-MM-DD)",
    "kyc_id_type" to "ID Document Type",
    "kyc_id_number" to "ID Document Number",
    "kyc_pan_number" to "PAN Number"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    var kyc by remember { mutableStateOf<KycData?>(null) }

    // First-time submission form state
    var vehicleType by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }
    var vehicleModel by remember { mutableStateOf("") }
    var chassisNumber by remember { mutableStateOf("") }
    var engineNumber by remember { mutableStateOf("") }
    var insuranceNumber by remember { mutableStateOf("") }
    var insuranceExpiry by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var panNumber by remember { mutableStateOf("") }
    var idImageUri by remember { mutableStateOf<Uri?>(null) }
    var panImageUri by remember { mutableStateOf<Uri?>(null) }
    var rcImageUri by remember { mutableStateOf<Uri?>(null) }
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
    var passbookUri by remember { mutableStateOf<Uri?>(null) }

    // Change-request state
    var showChangeSheet by remember { mutableStateOf(false) }
    var selectedFields by remember { mutableStateOf(setOf<String>()) }
    var newValues by remember { mutableStateOf(mapOf<String, String>()) }
    var replacementFiles by remember { mutableStateOf(mapOf<String, Uri>()) }
    var changeReason by remember { mutableStateOf("") }

    LaunchedEffect(retryTick) {
        loading = true
        val result = runCatching { ApiClient.service.getKyc() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            errorText = null
            kyc = body.data
        } else {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KYC & Documents", fontWeight = FontWeight.Bold) },
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

            if (loading && kyc == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            val data = kyc ?: return@Column

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status:", style = MaterialTheme.typography.bodyMedium)
                StatusBadge(
                    label = data.kyc_status.replaceFirstChar { it.uppercase() }.replace('_', ' '),
                    tone = when (data.kyc_status) {
                        "approved" -> BadgeTone.SUCCESS
                        "pending" -> BadgeTone.WARNING
                        "rejected" -> BadgeTone.ERROR
                        else -> BadgeTone.NEUTRAL
                    }
                )
            }

            if (data.kyc_status == "rejected" && !data.kyc_rejection_reason.isNullOrBlank()) {
                InfoBanner("Reason for rejection: ${data.kyc_rejection_reason}", isError = true)
            }

            if (!data.locked) {
                // ---- First-time combined submission ----
                Text(
                    "Submit this once. After it's submitted, these details are locked — any future change needs Admin approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionCard(title = "Vehicle Details", icon = androidx.compose.material.icons.Icons.Default.DirectionsBike) {
                    DropdownField("Vehicle Type", vehicleTypes, vehicleType) { vehicleType = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Vehicle Number", vehicleNumber) { vehicleNumber = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Vehicle Model (e.g. Honda Activa 6G)", vehicleModel) { vehicleModel = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Chassis Number", chassisNumber) { chassisNumber = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Engine Number", engineNumber) { engineNumber = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Insurance Number", insuranceNumber) { insuranceNumber = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Insurance Due Date (YYYY-MM-DD)", insuranceExpiry) { insuranceExpiry = it }
                }

                SectionCard(title = "Identity Documents", icon = androidx.compose.material.icons.Icons.Default.Badge) {
                    DropdownField("ID Document Type", idDocTypes, idType) { idType = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("ID Document Number", idNumber) { idNumber = it }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("PAN Number", panNumber) { panNumber = it.uppercase() }
                    Spacer(Modifier.height(14.dp))
                    DocumentPickerRow("ID Document Photo", false, idImageUri?.lastPathSegment) { idImageUri = it }
                    Spacer(Modifier.height(8.dp))
                    DocumentPickerRow("PAN Card Photo", false, panImageUri?.lastPathSegment) { panImageUri = it }
                    Spacer(Modifier.height(8.dp))
                    DocumentPickerRow("Vehicle RC / Registration", false, rcImageUri?.lastPathSegment) { rcImageUri = it }
                    Spacer(Modifier.height(8.dp))
                    DocumentPickerRow("Selfie", false, selfieUri?.lastPathSegment, acceptPdf = false) { selfieUri = it }
                    Spacer(Modifier.height(8.dp))
                    DocumentPickerRow("Bank Passbook / Cancelled Cheque", false, passbookUri?.lastPathSegment) { passbookUri = it }
                }

                Button(
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        val uris = listOf(idImageUri, panImageUri, rcImageUri, selfieUri, passbookUri)
                        if (uris.any { it == null }) {
                            errorText = "Please upload all 5 documents."
                            return@Button
                        }
                        saving = true
                        scope.launch {
                            val plain = "text/plain".toMediaTypeOrNull()
                            val fields = mapOf(
                                "vehicle_type" to vehicleType, "vehicle_number" to vehicleNumber,
                                "vehicle_model" to vehicleModel, "vehicle_chassis_number" to chassisNumber,
                                "vehicle_engine_number" to engineNumber, "vehicle_insurance_number" to insuranceNumber,
                                "vehicle_insurance_expiry" to insuranceExpiry, "kyc_id_type" to idType,
                                "kyc_id_number" to idNumber, "kyc_pan_number" to panNumber
                            ).mapValues { it.value.toRequestBody(plain) }

                            val result = runCatching {
                                ApiClient.service.submitKyc(
                                    fields,
                                    context.uriToMultipartPart(idImageUri!!, "kyc_id_image")!!,
                                    context.uriToMultipartPart(panImageUri!!, "kyc_pan_image")!!,
                                    context.uriToMultipartPart(rcImageUri!!, "kyc_vehicle_rc_image")!!,
                                    context.uriToMultipartPart(selfieUri!!, "kyc_selfie_image")!!,
                                    context.uriToMultipartPart(passbookUri!!, "kyc_bank_passbook_image")!!
                                )
                            }
                            val resp = result.getOrNull()?.body()
                            if (resp?.success == true) {
                                errorText = null
                                successText = resp.message ?: "KYC submitted."
                                retryTick++
                            } else {
                                errorText = resp?.errors?.joinToString(" ") ?: resp?.error ?: describeFailure(result)
                            }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (saving) "Submitting…" else "Submit for Verification") }

            } else {
                // ---- Locked: read-only view + Request a Change ----
                SectionCard(title = "Your Submitted Details", icon = androidx.compose.material.icons.Icons.Default.Lock) {
                    ReadOnlyRow("Vehicle Type", data.vehicle_type)
                    ReadOnlyRow("Vehicle Number", data.vehicle_number)
                    ReadOnlyRow("Vehicle Model", data.vehicle_model)
                    ReadOnlyRow("Chassis Number", data.vehicle_chassis_number)
                    ReadOnlyRow("Engine Number", data.vehicle_engine_number)
                    ReadOnlyRow("Insurance Number", data.vehicle_insurance_number)
                    ReadOnlyRow("Insurance Due Date", data.vehicle_insurance_expiry)
                    ReadOnlyRow("ID Document Type", idDocTypes.toMap()[data.kyc_id_type] ?: data.kyc_id_type)
                    ReadOnlyRow("ID Document Number", data.kyc_id_number)
                    ReadOnlyRow("PAN Number", data.kyc_pan_number)
                }

                SectionCard(title = "Documents", icon = androidx.compose.material.icons.Icons.Default.FolderShared) {
                    DocThumb("ID Document", data.kyc_id_image_url)
                    DocThumb("PAN Card", data.kyc_pan_image_url)
                    DocThumb("Vehicle RC", data.kyc_vehicle_rc_url)
                    DocThumb("Selfie", data.kyc_selfie_url)
                    DocThumb("Bank Passbook", data.kyc_bank_passbook_url)
                }

                val pending = data.pending_change_request
                if (pending != null) {
                    InfoBanner("You have a change request pending Admin review (submitted ${pending.submitted_at ?: ""}).", isError = false)
                } else {
                    OutlinedButton(
                        onClick = { showChangeSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Request a Change") }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showChangeSheet) {
        ModalBottomSheet(onDismissRequest = { showChangeSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Request a Change", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Pick what needs to change, tell Admin why, and submit. Nothing changes until Admin approves it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                editableFieldLabels.forEach { (field, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = field in selectedFields,
                            onCheckedChange = { checked ->
                                selectedFields = if (checked) selectedFields + field else selectedFields - field
                            }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (field in selectedFields) {
                        OutlinedTextField(
                            value = newValues[field].orEmpty(),
                            onValueChange = { newValues = newValues + (field to it) },
                            label = { Text("New value") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 6.dp)
                        )
                    }
                }

                HorizontalDivider()
                Text("Replace a document instead (optional):", style = MaterialTheme.typography.labelMedium)
                mapOf(
                    "kyc_id_image" to "ID Document", "kyc_pan_image" to "PAN Card",
                    "kyc_vehicle_rc_image" to "Vehicle RC", "kyc_selfie_image" to "Selfie",
                    "kyc_bank_passbook_image" to "Bank Passbook"
                ).forEach { (field, label) ->
                    DocumentPickerRow(label, false, replacementFiles[field]?.lastPathSegment) { uri ->
                        replacementFiles = replacementFiles + (field to uri)
                    }
                }

                OutlinedTextField(
                    value = changeReason,
                    onValueChange = { changeReason = it },
                    label = { Text("Reason for this change") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        if (changeReason.isBlank() || (selectedFields.isEmpty() && replacementFiles.isEmpty())) {
                            errorText = "Pick at least one field or document to change, and tell Admin why."
                            return@Button
                        }
                        saving = true
                        scope.launch {
                            val plain = "text/plain".toMediaTypeOrNull()
                            val fieldsJson = org.json.JSONObject(
                                selectedFields.associateWith { newValues[it].orEmpty() }.filterValues { it.isNotBlank() }
                            ).toString()
                            val parts = mapOf(
                                "reason" to changeReason.toRequestBody(plain),
                                "fields" to fieldsJson.toRequestBody(plain)
                            )
                            val files = replacementFiles.mapNotNull { (field, uri) ->
                                context.uriToMultipartPart(uri, field)
                            }
                            val result = runCatching { ApiClient.service.submitKycChangeRequest(parts, files) }
                            val resp = result.getOrNull()?.body()
                            if (resp?.success == true) {
                                errorText = null
                                successText = resp.message ?: "Change request submitted."
                                showChangeSheet = false
                                selectedFields = emptySet(); newValues = emptyMap(); replacementFiles = emptyMap(); changeReason = ""
                                retryTick++
                            } else {
                                errorText = resp?.errors?.joinToString(" ") ?: resp?.error ?: describeFailure(result)
                            }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (saving) "Submitting…" else "Submit Change Request") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.toMap()[selected] ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String?) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.ifBlank { "—" } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DocThumb(label: String, url: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (!url.isNullOrBlank()) {
            StatusBadge("Uploaded", BadgeTone.SUCCESS)
        } else {
            StatusBadge("Missing", BadgeTone.NEUTRAL)
        }
    }
}

@Composable
fun SuccessBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun InfoBanner(message: String, isError: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

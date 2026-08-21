package com.qweet.rider.data

// ---- Auth ----

data class LoginRequest(
    val identity: String,
    val password: String,
    val device_name: String? = null
)

data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val token_type: String? = null,
    val user: UserDto? = null,
    val rider: RiderDto? = null,
    val error: String? = null,
    val errors: List<String>? = null
)

data class UserDto(
    val id: Int,
    val name: String,
    val email: String?,
    val phone: String?,
    val username: String?,
    val status: String
)

data class RiderDto(
    val id: Int,
    val status: String,
    val kyc_status: String,
    val is_online: Boolean,
    val rating_avg: Double
)

// ---- Dashboard ----

data class DashboardResponse(
    val success: Boolean,
    val data: DashboardData? = null,
    val error: String? = null
)

data class DashboardData(
    val name: String,
    val is_online: Boolean,
    val account_status: String,
    val kyc_status: String,
    val rating_avg: Double,
    val active_deliveries: Int,
    val completed_deliveries: Int,
    val today_earnings: Double,
    val pending_payout: Double,
    val currency_symbol: String
)

// ---- Online toggle ----

data class ToggleOnlineRequest(val online: Boolean)
data class ToggleOnlineResponse(val success: Boolean, val is_online: Boolean? = null, val error: String? = null)

// ---- Location ----

data class UpdateLocationRequest(val lat: Double, val lng: Double)
data class SimpleResponse(val success: Boolean, val error: String? = null)
data class DeviceTokenRequest(val fcm_token: String, val platform: String = "android")

// ---- Support Chat ----

data class SupportTicketDto(
    val id: Int,
    val ticket_no: String,
    val subject: String,
    val category: String,
    val status: String,
    val order_number: String?,
    val unread_count: Int,
    val updated_at: String
)
data class SupportTicketsResponse(val success: Boolean, val tickets: List<SupportTicketDto>? = null, val error: String? = null)

data class SupportMessageDto(
    val id: Int,
    val sender_type: String, // "rider" | "admin" | "system"
    val message: String,
    val time: String,
    val created_at: String,
    val attachment_url: String?,
    val attachment_type: String?,
    val attachment_name: String?,
    val attachment_size: String?
)
data class SupportMessagesResponse(
    val success: Boolean,
    val messages: List<SupportMessageDto>? = null,
    val status: String? = null,
    val resolution_reason: String? = null,
    val error: String? = null
)
data class SupportSendResponse(val success: Boolean, val message: SupportMessageDto? = null, val status: String? = null, val error: String? = null)

data class CreateTicketRequest(
    val subject: String,
    val message: String,
    val category: String = "general",
    val order_id: Int? = null
)
data class CreateTicketResponse(val success: Boolean, val ticket_id: Int? = null, val ticket_no: String? = null, val error: String? = null)

// ---- Orders ----

data class OrdersResponse(
    val success: Boolean,
    val data: List<DeliveryDto>? = null,
    val error: String? = null
)

data class DeliveryDto(
    val delivery_id: Int,
    val order_id: Int,
    val order_number: String,
    val status: String, // assigned | picked_up
    val payment_method: String?,
    val total_amount: Double,
    val currency_symbol: String,
    val est_earning: Double,
    val delivery_instructions: String?,
    val assigned_at: String?,
    val picked_up_at: String?,
    val items: List<OrderItemDto> = emptyList(),
    val pickup: PickupDto,
    val dropoff: DropoffDto,
    val next_actions: List<String>
)

data class OrderItemDto(
    val item_name: String,
    val quantity: Int,
    val price: Double,
    val subtotal: Double
)

data class PickupDto(
    val name: String?,
    val address: String?,
    val phone: String?,
    val lat: Double?,
    val lng: Double?
)

data class DropoffDto(
    val customer_name: String?,
    val label: String?,
    val address_line1: String?,
    val address_line2: String?,
    val city: String?,
    val phone: String?,
    val lat: Double?,
    val lng: Double?
)

// ---- Order offer (global new-order popup, polled from anywhere in the app) ----

data class OrderOfferResponse(
    val success: Boolean,
    val offer: OrderOfferDto? = null,
    val error: String? = null
)

data class OrderOfferDto(
    val delivery_id: Int,
    val order_number: String,
    val seconds_left: Int,
    val window_seconds: Int,
    val pickup: OfferPickupDto,
    val dropoff: OfferDropoffDto,
    val customer_rating: Double,
    val distance_to_pickup_km: Double?,
    val distance_pickup_to_drop_km: Double?,
    val total_distance_km: Double?,
    val est_delivery_minutes: Int?,
    val total_amount: Double,
    val delivery_fee: Double,
    val est_earning: Double,
    val currency_symbol: String
)

data class OfferPickupDto(
    val name: String?,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val rating: Double?
)

data class OfferDropoffDto(
    val customer_name: String?,
    val label: String?,
    val address_line1: String?,
    val address_line2: String?,
    val city: String?,
    val lat: Double?,
    val lng: Double?
)

// ---- Order action ----

data class OrderActionRequest(
    val delivery_id: Int,
    val action: String, // "advance" | "decline"
    val new_status: String? = null, // "picked_up" | "delivered"
    val reason: String? = null
)

data class OrderActionResponse(
    val success: Boolean,
    val message: String? = null,
    val status: String? = null,
    val error: String? = null
)

// ---- Profile (me.php) ----

data class MeResponse(
    val success: Boolean,
    val data: MeData? = null,
    val error: String? = null
)

data class MeData(
    val user: AccountDto,
    val rider: RiderProfileDto
)

data class AccountDto(
    val id: Int,
    val name: String,
    val email: String?,
    val phone: String?,
    val username: String?,
    val status: String
)

data class RiderProfileDto(
    val id: Int,
    val vehicle_type: String?,
    val vehicle_number: String?,
    val vehicle_model: String?,
    val vehicle_chassis_number: String?,
    val vehicle_engine_number: String?,
    val vehicle_insurance_number: String?,
    val vehicle_insurance_expiry: String?,
    val license_number: String?,
    val avatar_url: String?,
    val is_online: Boolean,
    val status: String,
    val rating_avg: Double,
    val kyc_status: String,
    val kyc_locked: Boolean = false,
    val kyc_rejection_reason: String?,
    val partner_since: String? = null,
    val completed_deliveries: Int = 0,
    val bank_status: String? = null,
    val bank_account_name: String?,
    val bank_account_number: String?,
    val bank_name: String? = null,
    val bank_ifsc: String?,
    val bank_branch: String? = null,
    val upi_id: String?,
    val earning_type: String?,
    val earning_value: Double?
)

data class UpdateAccountRequest(
    val email: String,
    val phone: String,
    val username: String
)

data class UpdateAccountResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val errors: List<String>? = null
)

// ---- Avatar (avatar.php — photo only, not locked by KYC) ----

data class UpdateAvatarResponse(
    val success: Boolean,
    val message: String? = null,
    val avatar_url: String? = null,
    val error: String? = null,
    val errors: List<String>? = null
)

// ---- KYC + Vehicle (kyc.php — submitted together once, then locked) ----

data class KycResponse(
    val success: Boolean,
    val data: KycData? = null,
    val error: String? = null,
    val errors: List<String>? = null,
    val locked: Boolean? = null
)

data class KycData(
    val locked: Boolean,
    val kyc_status: String,
    val kyc_id_type: String?,
    val kyc_id_number: String?,
    val kyc_id_image_url: String?,
    val kyc_pan_number: String?,
    val kyc_pan_image_url: String?,
    val kyc_vehicle_rc_url: String?,
    val kyc_selfie_url: String?,
    val kyc_bank_passbook_url: String?,
    val kyc_rejection_reason: String?,
    val kyc_submitted_at: String?,
    val kyc_reviewed_at: String?,
    val vehicle_type: String?,
    val vehicle_number: String?,
    val vehicle_model: String?,
    val vehicle_chassis_number: String?,
    val vehicle_engine_number: String?,
    val vehicle_insurance_number: String?,
    val vehicle_insurance_expiry: String?,
    val pending_change_request: PendingKycRequestDto? = null
)

data class PendingKycRequestDto(
    val id: Int,
    val requested_changes: Map<String, String>?,
    val submitted_at: String?
)

data class SubmitKycResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val errors: List<String>? = null,
    val locked: Boolean? = null
)

// ---- KYC change requests (kyc-change-request.php) ----

data class KycChangeRequestDto(
    val id: Int,
    val requested_changes: Map<String, String>?,
    val previous_values: Map<String, String>?,
    val status: String, // pending | approved | rejected
    val admin_note: String?,
    val reviewed_at: String?,
    val created_at: String?
)

data class KycChangeRequestListResponse(
    val success: Boolean,
    val data: List<KycChangeRequestDto>? = null,
    val error: String? = null
)

data class SubmitKycChangeRequestResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val errors: List<String>? = null
)

// ---- Bank / payout (bank.php — every submission is pending until Admin verifies) ----

data class BankResponse(
    val success: Boolean,
    val data: BankData? = null,
    val error: String? = null
)

data class BankData(
    val status: String, // not_submitted | pending | verified
    val verified: VerifiedBankDto? = null,
    val pending: Map<String, String>? = null
)

data class VerifiedBankDto(
    val bank_account_holder_name: String?,
    val bank_account_number: String?,
    val bank_name: String?,
    val bank_ifsc: String?,
    val bank_branch: String?,
    val bank_account_holder_photo_url: String?,
    val upi_id: String?
)

data class SubmitBankResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val errors: List<String>? = null
)

// ---- Privacy Policy (privacy-policy.php — content managed by Admin) ----

data class PrivacyPolicyResponse(
    val success: Boolean,
    val data: PrivacyPolicyData? = null,
    val error: String? = null
)

data class PrivacyPolicyData(
    val content: String,
    val updated_at: String?
)

// ---- Order history (history.php) — past delivered/cancelled deliveries, for the Orders tab ----

data class HistoryResponse(
    val success: Boolean,
    val data: List<HistoryItemDto>? = null,
    val pagination: HistoryPagination? = null,
    val error: String? = null
)

data class HistoryItemDto(
    val delivery_id: Int,
    val order_number: String,
    val payment_method: String?,
    val total_amount: Double,
    val earning_amount: Double?,
    val status: String, // delivered | cancelled
    val cancel_reason: String?,
    val date: String?,
    val currency_symbol: String
)

data class HistoryPagination(
    val page: Int,
    val per_page: Int,
    val total: Int,
    val total_pages: Int
)

// ---- Wallet / earnings (earnings.php) ----

data class EarningsResponse(
    val success: Boolean,
    val data: EarningsData? = null,
    val error: String? = null
)

data class EarningsData(
    val currency_symbol: String,
    val rate: EarningsRate,
    val totals: EarningsTotals,
    val daily_series: List<DailyEarningsPoint> = emptyList(),
    val last_payout: LastPayoutDto? = null,
    val wallet_history: List<WalletHistoryItem> = emptyList(),
    // Deprecated: kept for older code paths. Prefer wallet_history.
    val breakdown: List<EarningsBreakdownItem> = emptyList()
)

data class EarningsRate(
    val type: String, // fixed | percentage
    val value: Double,
    val is_custom: Boolean
)

data class EarningsTotals(
    val total_earned: Double,
    val delivery_total: Double = 0.0,
    val bonus_total: Double = 0.0,
    val tips_total: Double = 0.0,
    val settled_total: Double,
    val pending_total: Double,
    val this_month: Double,
    val this_week: Double = 0.0,
    val today_total: Double = 0.0,
    val pending_withdrawal: Double = 0.0,
    val available_balance: Double = 0.0,
    val total_deliveries: Int = 0,
    val month_deliveries: Int = 0,
    val week_deliveries: Int = 0,
    val today_deliveries: Int = 0
)

/** One day's bucket in the last-7-days earnings chart. */
data class DailyEarningsPoint(
    val date: String, // YYYY-MM-DD
    val amount: Double,
    val delivery_count: Int = 0
)

data class LastPayoutDto(
    val amount: Double,
    val date: String?
)

/** One row in the wallet screen's unified history feed. */
data class WalletHistoryItem(
    val id: String,
    val kind: String, // delivery | bonus | tip | withdrawal
    val title: String,
    val order_number: String?,
    val amount: Double, // positive = credit, negative = debit (withdrawal)
    val status: String, // pending | settled | processing | completed | rejected
    val date: String?
)

data class EarningsBreakdownItem(
    val order_number: String,
    val delivered_at: String?,
    val amount: Double,
    val status: String // pending | settled
)

// ---- Withdrawals (withdrawals.php) ----

data class WithdrawalsResponse(
    val success: Boolean,
    val data: List<WithdrawalDto>? = null,
    val error: String? = null
)

data class WithdrawalDto(
    val id: Int,
    val amount: Double,
    val status: String, // pending | processing | completed | rejected
    val payout_method: String, // bank | upi
    val admin_note: String?,
    val requested_at: String?,
    val processed_at: String?
)

data class CreateWithdrawalRequest(val amount: Double)

data class CreateWithdrawalResponse(
    val success: Boolean,
    val message: String? = null,
    val id: Int? = null,
    val error: String? = null
)

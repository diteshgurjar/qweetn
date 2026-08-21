package com.qweet.rider.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Query

/**
 * Mirrors QWEET's RIDER_API.md. Only the endpoints this minimal app needs:
 * login, dashboard, toggle-online, update-location, orders, order-action,
 * me (profile), vehicle, bank (payout), earnings (wallet).
 */
interface RiderApiService {

    @POST("login.php")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("dashboard.php")
    suspend fun dashboard(): Response<DashboardResponse>

    @POST("toggle-online.php")
    suspend fun toggleOnline(@Body body: ToggleOnlineRequest): Response<ToggleOnlineResponse>

    @POST("update-location.php")
    suspend fun updateLocation(@Body body: UpdateLocationRequest): Response<SimpleResponse>

    @GET("orders.php")
    suspend fun orders(): Response<OrdersResponse>

    // Polled globally (from anywhere in the app) to detect a brand-new
    // auto-assigned delivery still within its accept/decline window.
    @GET("order-offer.php")
    suspend fun orderOffer(): Response<OrderOfferResponse>

    @POST("order-action.php")
    suspend fun orderAction(@Body body: OrderActionRequest): Response<OrderActionResponse>

    // Past delivered/cancelled deliveries for the Orders tab.
    @GET("history.php")
    suspend fun history(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<HistoryResponse>

    @GET("me.php")
    suspend fun me(): Response<MeResponse>

    @PUT("me.php")
    suspend fun updateAccount(@Body body: UpdateAccountRequest): Response<UpdateAccountResponse>

    // Profile photo — the only profile field NOT locked by KYC.
    @Multipart
    @POST("avatar.php")
    suspend fun updateAvatar(
        @Part avatar: okhttp3.MultipartBody.Part
    ): Response<UpdateAvatarResponse>

    // ---- KYC + Vehicle (submitted together once, then locked) ----

    @GET("kyc.php")
    suspend fun getKyc(): Response<KycResponse>

    @Multipart
    @POST("kyc.php")
    suspend fun submitKyc(
        @PartMap fields: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>,
        @Part kycIdImage: okhttp3.MultipartBody.Part,
        @Part kycPanImage: okhttp3.MultipartBody.Part,
        @Part kycVehicleRcImage: okhttp3.MultipartBody.Part,
        @Part kycSelfieImage: okhttp3.MultipartBody.Part,
        @Part kycBankPassbookImage: okhttp3.MultipartBody.Part
    ): Response<SubmitKycResponse>

    @GET("kyc-change-request.php")
    suspend fun listKycChangeRequests(): Response<KycChangeRequestListResponse>

    // Only "reason" and "fields" (a JSON string) are required — any file @Part is optional,
    // only send the ones being replaced. Retrofit @Multipart requires at least one @Part in
    // the signature to be non-null at call time, so callers omit unused file parts by not
    // adding them to the parts list built at the call site (see ApiClient helper usage).
    @Multipart
    @POST("kyc-change-request.php")
    suspend fun submitKycChangeRequest(
        @PartMap parts: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>,
        @Part files: List<okhttp3.MultipartBody.Part>
    ): Response<SubmitKycChangeRequestResponse>

    // ---- Bank / payout (every submission is pending until Admin verifies) ----

    @GET("bank.php")
    suspend fun getBank(): Response<BankResponse>

    @Multipart
    @POST("bank.php")
    suspend fun submitBank(
        @PartMap fields: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>,
        @Part bankAccountHolderPhoto: okhttp3.MultipartBody.Part
    ): Response<SubmitBankResponse>

    // ---- Privacy Policy (content managed by Admin) ----

    @GET("privacy-policy.php")
    suspend fun privacyPolicy(): Response<PrivacyPolicyResponse>

    @GET("earnings.php")
    suspend fun earnings(): Response<EarningsResponse>

    @GET("withdrawals.php")
    suspend fun withdrawals(): Response<WithdrawalsResponse>

    // Called from WithdrawFundsSheet after the rider enters their withdrawal PIN.
    @POST("withdrawals.php")
    suspend fun createWithdrawal(@Body body: CreateWithdrawalRequest): Response<CreateWithdrawalResponse>

    // Registers/refreshes this device's FCM token so the server can push to it.
    // Called right after login and again whenever Firebase rotates the token.
    @POST("device-token.php")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): Response<SimpleResponse>

    // Called on logout so a signed-out device stops receiving this rider's pushes.
    @HTTP(method = "DELETE", path = "device-token.php", hasBody = true)
    suspend fun unregisterDeviceToken(@Body body: DeviceTokenRequest): Response<SimpleResponse>

    // ---- Support chat ----

    @GET("support-tickets.php")
    suspend fun supportTickets(): Response<SupportTicketsResponse>

    @POST("support-tickets.php")
    suspend fun createSupportTicket(@Body body: CreateTicketRequest): Response<CreateTicketResponse>

    @GET("support-messages.php")
    suspend fun supportMessages(
        @Query("ticket_id") ticketId: Int,
        @Query("after_id") afterId: Int = 0
    ): Response<SupportMessagesResponse>

    // Text-only reply — no attachment (multipart variant below covers that case).
    @FormUrlEncoded
    @POST("support-messages.php")
    suspend fun sendSupportMessage(
        @Field("ticket_id") ticketId: Int,
        @Field("message") message: String
    ): Response<SupportSendResponse>

    @Multipart
    @POST("support-messages.php")
    suspend fun sendSupportMessageWithAttachment(
        @Part("ticket_id") ticketId: okhttp3.RequestBody,
        @Part("message") message: okhttp3.RequestBody,
        @Part attachment: okhttp3.MultipartBody.Part
    ): Response<SupportSendResponse>
}

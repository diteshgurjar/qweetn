package com.qweet.rider.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Part
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

    // avatar upload isn't wired up yet — text fields only, matches what ProfileScreen sends.
    @Multipart
    @POST("vehicle.php")
    suspend fun updateVehicle(
        @Part("vehicle_type") vehicleType: okhttp3.RequestBody,
        @Part("vehicle_number") vehicleNumber: okhttp3.RequestBody,
        @Part("license_number") licenseNumber: okhttp3.RequestBody
    ): Response<UpdateVehicleResponse>

    @POST("bank.php")
    suspend fun updateBank(@Body body: UpdateBankRequest): Response<UpdateBankResponse>

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
}

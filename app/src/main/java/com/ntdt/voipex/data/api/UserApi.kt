package com.ntdt.voipex.data.api

import com.ntdt.voipex.data.models.User
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Headers
import retrofit2.Response
import retrofit2.http.Body

interface UserApi {
    @GET("users")
    suspend fun getUsers(): List<User>

    @POST("profile/contacts")
    suspend fun addContact(@Body request: AddContactRequest): Response<Unit>

    @GET("profile/contacts")
    suspend fun getContacts(): List<User>

    @GET("profile/search")
    suspend fun searchUser(@Query("query") query: String): SearchUserResponse

    @POST("guests/online")
    suspend fun registerGuestOnline(@Body guest: GuestOnlineRequest): Response<Unit>

    @GET("guests/online")
    suspend fun getOnlineGuests(): GuestListResponse

    @retrofit2.http.DELETE("guests/online/{id}")
    suspend fun unregisterGuestOnline(@retrofit2.http.Path("id") id: String): Response<Unit>

    @POST("call/invite")
    suspend fun inviteGuest(@Body invite: GuestInviteRequest): Response<Unit>

    @GET("call/invites/{guestId}")
    suspend fun getInvites(@retrofit2.http.Path("guestId") guestId: String): GuestInviteListResponse

    @POST("call/room")
    suspend fun createOrJoinRoom(@Body req: RoomRequest): RoomResponse
}

data class AddContactRequest(val contactId: String)

data class SearchUserResponse(
    val status: String,
    val data: UsersData
)

data class UsersData(
    val users: List<User>
)

data class GuestOnlineRequest(
    val id: String,
    val name: String
)

data class GuestListResponse(
    val status: String,
    val data: GuestListData
)

data class GuestListData(
    val users: List<GuestInfo>  // 서버에서 'users'로 반환
)

data class GuestInfo(
    val id: String,
    val name: String
)

data class GuestInviteRequest(
    val fromId: String,
    val fromName: String,
    val toId: String,
    val type: String // "audio" or "video"
)

data class GuestInviteListResponse(
    val status: String,
    val data: GuestInviteListData
)

data class GuestInviteListData(
    val invites: List<GuestInvite>
)

data class GuestInvite(
    val fromId: String,
    val fromName: String,
    val type: String,
    val timestamp: Long
)

data class RoomRequest(
    val guestA: String,
    val guestB: String
)

data class RoomResponse(
    val status: String,
    val data: RoomData
)

data class RoomData(
    val roomId: String
) 
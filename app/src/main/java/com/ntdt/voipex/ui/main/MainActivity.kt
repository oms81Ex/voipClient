package com.ntdt.voipex.ui.main

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ntdt.voipex.R
import com.ntdt.voipex.databinding.ActivityMainBinding
import com.ntdt.voipex.ui.auth.LoginActivity
import com.ntdt.voipex.ui.call.CallActivity
import com.ntdt.voipex.ui.contacts.ContactsAdapter
import com.ntdt.voipex.utils.ErrorUtils
import com.ntdt.voipex.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ntdt.voipex.data.api.AddContactRequest
import com.ntdt.voipex.data.api.UserApi
import com.ntdt.voipex.data.models.User
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Response
import com.ntdt.voipex.data.repository.CallRepository
import javax.inject.Inject
import timber.log.Timber
import com.ntdt.voipex.data.api.GuestOnlineRequest
import com.ntdt.voipex.data.api.GuestInfo
import com.ntdt.voipex.data.api.GuestListResponse
import com.ntdt.voipex.data.api.GuestInviteRequest
import com.ntdt.voipex.data.api.GuestInvite
import com.ntdt.voipex.data.api.GuestInviteListResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.ntdt.voipex.data.api.RoomRequest
import com.ntdt.voipex.ui.guest.GuestUsersActivity
import com.ntdt.voipex.data.signaling.GuestSignalingClient
import com.ntdt.voipex.data.signaling.SignalingEvent
import com.ntdt.voipex.domain.model.GuestUser
import com.ntdt.voipex.ui.guest.GuestUsersAdapter
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import android.os.CountDownTimer
import java.util.UUID
import com.ntdt.voipex.ui.auth.RegistrationActivity
import android.widget.LinearLayout
import android.widget.TextView

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var contactsAdapter: ContactsAdapter
    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var guestSignalingClient: GuestSignalingClient
    
    private lateinit var guestAdapter: GuestUsersAdapter
    private val guestUsers = mutableListOf<GuestUser>()
    private var callTimeLimitTimer: CountDownTimer? = null
    private var currentCall: Any? = null
    
    // 게스트 모드 UI 요소들 (lazy initialization)
    private val guestModeBanner by lazy { binding.root.findViewById<LinearLayout?>(R.id.guestModeBanner) }
    private val callTimeLimitLayout by lazy { binding.root.findViewById<LinearLayout?>(R.id.callTimeLimitLayout) }
    private val callTimeLimitTextView by lazy { binding.root.findViewById<TextView?>(R.id.callTimeLimitTextView) }
    private val callTimeRemainingTextView by lazy { binding.root.findViewById<TextView?>(R.id.callTimeRemainingTextView) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showContent()
        } else {
            showPermissionError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupLogoutButton()
        observeViewModel()
        if (PermissionUtils.hasRequiredPermissions(this)) {
            showContent()
        } else {
            requestPermissions()
        }
        
        // 토큰이 없으면 로그인 화면으로
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)
        if (token == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        val isGuest = prefs.getBoolean("is_guest", false)
        
        if (isGuest) {
            Timber.d("[GuestMode] User is guest, setting up guest mode")
            // 게스트 사용자인 경우
            setupSimpleGuestMode()
        } else {
            Timber.d("[GuestMode] User is not guest, setting up normal mode")
            binding.fabAddCall.visibility = View.VISIBLE
            binding.fabAddCall.setOnClickListener {
                showAddContactDialog()
            }
        }
        // 예시: 게스트 목록 조회 버튼 추가 (실제 UI에 맞게 배치)
        binding.fabAddCall.setOnLongClickListener {
            if (!isGuest) {
                showOnlineGuestList()
            }
            true
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.app_name)
        }
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter { user ->
            startCall(user.id)
        }
        binding.contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactsAdapter
        }
    }

    private fun setupLogoutButton() {
        binding.logoutFab.setOnClickListener {
            viewModel.logout()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // 게스트 모드일 때는 ViewModel 상태를 무시
                if (getSharedPreferences("VoipExPrefs", MODE_PRIVATE).getBoolean("is_guest", false)) {
                    return@collectLatest
                }
                
                when (state) {
                    is MainUiState.Loading -> showLoading()
                    is MainUiState.Success -> {
                        hideLoading()
                        if (state.contacts.isEmpty()) {
                            showEmptyState()
                        } else {
                            hideEmptyState()
                            contactsAdapter.submitList(state.contacts)
                        }
                    }
                    is MainUiState.Error -> {
                        hideLoading()
                        showError(state.message)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is MainEvent.NavigateToLogin -> {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        finish()
                    }
                    is MainEvent.ShowError -> {
                        showError(event.message)
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        ErrorUtils.showError(
            view = binding.root,
            message = message,
            actionText = "Retry",
            action = {
                viewModel.refreshUsers()
            }
        )
    }

    private fun showContent() {
        binding.permissionErrorText.isVisible = false
        binding.contentLayout.isVisible = true
        
        // TODO: Set up RecyclerView and observe ViewModel
    }

    private fun showPermissionError() {
        binding.permissionErrorText.isVisible = true
        binding.contentLayout.isVisible = false
    }

    private fun requestPermissions() {
        permissionLauncher.launch(PermissionUtils.getRequiredPermissions())
    }

    private fun startCall(userId: String) {
        lifecycleScope.launch {
            try {
                val response = callRepository.createCall(userId)
                val callId = response.callId
                val intent = Intent(this@MainActivity, CallActivity::class.java).apply {
                    putExtra(CallActivity.EXTRA_USER_ID, userId)
                    putExtra(CallActivity.EXTRA_IS_INCOMING, false)
                    putExtra(CallActivity.EXTRA_CALL_ID, callId)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "통화 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contactsRecyclerView.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.contactsRecyclerView.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.contactsRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.tvEmptyState.visibility = View.GONE
        binding.contactsRecyclerView.visibility = View.VISIBLE
    }

    private fun showAddContactDialog() {
        val editText = EditText(this)
        editText.hint = "이름 또는 이메일로 검색"

        val dialog = AlertDialog.Builder(this)
            .setTitle("연락처 추가")
            .setView(editText)
            .setPositiveButton("검색", null)
            .setNegativeButton("취소", null)
            .create()

        dialog.setOnShowListener {
            val searchButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            searchButton.setOnClickListener {
                val query = editText.text.toString().trim()
                Timber.d("[ContactSearch] 검색 시도: query=$query")
                if (query.isEmpty()) {
                    Toast.makeText(this, "검색어를 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Retrofit 인스턴스 생성 (실제 앱에서는 DI/싱글톤 사용 권장)
                val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
                val tokenProvider = { prefs.getString("auth_token", null) }
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(object : Interceptor {
                        override fun intercept(chain: Interceptor.Chain): Response {
                            val token = tokenProvider()
                            Timber.d("[ContactSearch] Authorization 헤더: $token")
                            val request = if (token != null) {
                                chain.request().newBuilder()
                                    .addHeader("Authorization", "Bearer $token")
                                    .build()
                            } else {
                                chain.request()
                            }
                            Timber.d("[ContactSearch] 요청 URL: ${request.url}")
                            return chain.proceed(request)
                        }
                    })
                    .build()
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://10.0.2.2:3000/api/") // api-gateway 포트로 변경
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val userApi = retrofit.create(UserApi::class.java)
                lifecycleScope.launch {
                    try {
                        Timber.d("[ContactSearch] userApi.searchUser 호출: query=$query")
                        val response = withContext(Dispatchers.IO) { userApi.searchUser(query) }
                        val users = response.data.users
                        Timber.d("[ContactSearch] 검색 결과: $users")
                        if (users.isEmpty()) {
                            Toast.makeText(this@MainActivity, "검색 결과가 없습니다", Toast.LENGTH_SHORT).show()
                        } else {
                            showSearchResultsDialog(users, userApi)
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[ContactSearch] 검색 실패")
                        Toast.makeText(this@MainActivity, "검색 실패: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showSearchResultsDialog(users: List<User>, userApi: UserApi) {
        val items = users.map { "${it.name} (${it.email ?: "-"})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("검색 결과")
            .setItems(items) { _, which ->
                val user = users[which]
                lifecycleScope.launch {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            userApi.addContact(AddContactRequest(user.id))
                        }
                        if (response.isSuccessful) {
                            Toast.makeText(this@MainActivity, "연락처 추가 완료", Toast.LENGTH_SHORT).show()
                            viewModel.refreshUsers()
                        } else {
                            Toast.makeText(this@MainActivity, "추가 실패", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "추가 실패: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun registerGuestOnline() {
        Timber.d("[GuestMode] registerGuestOnline called")
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        // PreferencesManager에서 사용하는 키로 통일
        val guestId = prefs.getString("user_id", null)
        val guestName = prefs.getString("user_name", null)
        val token = prefs.getString("auth_token", null)
        
        Timber.d("[GuestMode] registerGuestOnline - guestId: $guestId, guestName: $guestName, token: $token")
        
        if (guestId != null && guestName != null && token != null) {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(request)
                }
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("http://10.0.2.2:3000/api/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val userApi = retrofit.create(UserApi::class.java)
            lifecycleScope.launch {
                try {
                    Timber.d("[GuestMode] Registering guest online...")
                    userApi.registerGuestOnline(GuestOnlineRequest(guestId, guestName))
                    Timber.d("[GuestMode] Guest registered online successfully")
                } catch (e: Exception) {
                    Timber.e(e, "[GuestMode] Failed to register guest online")
                }
            }
        }
    }

    private fun showOnlineGuestList() {
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)
        if (token == null) return
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3002/")  // user-service의 올바른 포트
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val userApi = retrofit.create(UserApi::class.java)
        lifecycleScope.launch {
            try {
                val response = userApi.getOnlineGuests()
                val guests = response.data.users  // 'guests' -> 'users'로 변경
                showGuestListDialog(guests)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "게스트 목록 조회 실패: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showGuestListDialog(guests: List<GuestInfo>) {
        val items = guests.map { "${'$'}{it.name} (${'$'}{it.id})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("현재 접속 중인 게스트")
            .setItems(items) { _, which ->
                val selectedGuest = guests[which]
                inviteGuest(selectedGuest)
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun inviteGuest(selectedGuest: GuestInfo, type: String = "audio") {
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val myId = prefs.getString("user_id", null) ?: return
        val myName = prefs.getString("user_name", null) ?: return
        val token = prefs.getString("auth_token", null) ?: return
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3000/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val userApi = retrofit.create(UserApi::class.java)
        lifecycleScope.launch {
            try {
                userApi.inviteGuest(
                    GuestInviteRequest(
                        fromId = myId,
                        fromName = myName,
                        toId = selectedGuest.id,
                        type = type
                    )
                )
                Toast.makeText(this@MainActivity, "초대 전송 완료", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "초대 실패: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 주기적으로 내 초대 목록을 polling (예: onResume에서 시작, onPause에서 중단)
    private var invitePollingJob: Job? = null
    private var guestListPollingJob: Job? = null
    override fun onResume() {
        super.onResume()
        startInvitePolling()
        
        // 게스트 모드인 경우 온라인 게스트 목록 업데이트
        if (getSharedPreferences("VoipExPrefs", MODE_PRIVATE).getBoolean("is_guest", false)) {
            startGuestListPolling()
        }
    }
    override fun onPause() {
        super.onPause()
        invitePollingJob?.cancel()
        guestListPollingJob?.cancel()
    }
    private fun startInvitePolling() {
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val myId = prefs.getString("user_id", null) ?: return
        val token = prefs.getString("auth_token", null) ?: return
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3000/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val userApi = retrofit.create(UserApi::class.java)
        invitePollingJob = lifecycleScope.launch {
            while (coroutineContext.isActive) {
                try {
                    val response = userApi.getInvites(myId)
                    val invites = response.data.invites
                    if (invites.isNotEmpty()) {
                        showInviteDialog(invites.first())
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }
    private fun showInviteDialog(invite: GuestInvite) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("통화 초대")
                .setMessage("${invite.fromName}님이 통화를 요청했습니다.")
                .setPositiveButton("수락") { _, _ ->
                    acceptInvite(invite)
                }
                .setNegativeButton("거절", null)
                .show()
        }
    }

    private fun acceptInvite(invite: GuestInvite) {
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val myId = prefs.getString("user_id", null) ?: return
        val myName = prefs.getString("user_name", null) ?: return
        val token = prefs.getString("auth_token", null) ?: return
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3000/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val userApi = retrofit.create(UserApi::class.java)
        lifecycleScope.launch {
            try {
                val response = userApi.createOrJoinRoom(RoomRequest(myId, invite.fromId))
                val roomId = response.data.roomId
                // CallActivity로 이동, roomId와 상대 guestId 전달
                val intent = Intent(this@MainActivity, com.ntdt.voipex.ui.call.CallActivity::class.java).apply {
                    putExtra("room_id", roomId)
                    putExtra("peer_id", invite.fromId)
                    putExtra("peer_name", invite.fromName)
                    putExtra("is_incoming", true)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "방 입장 실패: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_call_history -> {
                // TODO: Navigate to call history
                true
            }
            R.id.action_settings -> {
                // TODO: Navigate to settings
                true
            }
            R.id.action_logout -> {
                viewModel.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupGuestMode() {
        Timber.d("[GuestMode] setupGuestMode called")
        // 게스트 모드 UI 설정
        binding.apply {
            // 로그인 관련 UI 숨기기
            logoutFab.visibility = View.GONE
            fabAddCall.visibility = View.GONE // FAB 숨기기
            
            // 게스트 모드 표시
            toolbar.title = "VoIP (게스트)"
            // 게스트 모드 색상 (색상이 없으면 기본 파란색 사용)
            try {
                toolbar.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.guest_mode_color))
            } catch (e: Exception) {
                toolbar.setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_light))
            }
            
            // 게스트 모드 제한 안내
            showGuestModeInfo()
        }
        
        // 게스트 사용자 정보 생성
        val guestUser = User(
            id = UUID.randomUUID().toString(),
            name = "Guest_${System.currentTimeMillis().toString().takeLast(6)}",
            email = "",
            isGuest = true
        )
        
        // SharedPreferences에 게스트 정보 저장
        getSharedPreferences("VoipExPrefs", MODE_PRIVATE).edit().apply {
            putString("guest_user_id", guestUser.id)
            putString("guest_user_name", guestUser.name)
            putBoolean("is_guest_mode", true)
            apply()
        }
        
        // 게스트 모드 권한 체크
        checkGuestPermissions()
        
        // 게스트 모드 전용 기능 설정
        setupGuestFeatures()
        
        // WebSocket 연결 (게스트 모드)
        connectAsGuest(guestUser)
        
        // 게스트 온라인 등록
        registerGuestOnline()
        
        // 짧은 딜레이 후 게스트 목록 로드 (서버가 등록을 처리할 시간 주기)
        lifecycleScope.launch {
            delay(1000) // 1초 대기
            // 게스트 사용자 목록 표시를 위한 어댑터 설정
            setupGuestUsersList()
            
            // 게스트 시그널링 클라이언트 연결 및 이벤트 관찰
            connectGuestSignaling(GuestUser(guestUser.id, guestUser.name ?: "Guest"))
        }
    }
    
    private fun showGuestModeInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("게스트 모드")
            .setMessage("""
                게스트 모드로 사용 중입니다.
                
                사용 가능한 기능:
                • 1:1 음성/영상 통화
                • 화면 공유
                • 채팅
                
                제한된 기능:
                • 통화 녹음
                • 연락처 저장
                • 통화 기록 저장
                • 30분 이상 연속 통화
            """.trimIndent())
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("회원가입") { _, _ ->
                showRegistrationDialog()
            }
            .show()
    }
    
    private fun checkGuestPermissions() {
        // 게스트 모드에 필요한 최소 권한만 요청
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                GUEST_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun setupGuestFeatures() {
        // 게스트 모드 전용 UI 설정
        // 통화 시간 제한 표시
        callTimeLimitLayout?.visibility = View.VISIBLE
        callTimeLimitTextView?.text = "게스트 모드: 최대 30분"
        
        // 녹음 버튼 비활성화
        // binding.recordButton?.isEnabled = false
        // binding.recordButton?.alpha = 0.5f
        
        // 연락처 버튼 숨기기
        // binding.contactsButton?.visibility = View.GONE
        
        // 게스트 모드 배너 표시
        guestModeBanner?.visibility = View.VISIBLE
        guestModeBanner?.setOnClickListener {
            showUpgradeDialog()
        }
        
        // 30분 타이머 설정
        setupCallTimeLimit()
    }
    
    private fun connectAsGuest(guestUser: User) {
        // 게스트용 토큰 생성 (서버에서 임시 토큰 발급)
        viewModel.connectAsGuest(guestUser) { success ->
            if (success) {
                runOnUiThread {
                    Toast.makeText(this, "게스트로 연결되었습니다", Toast.LENGTH_SHORT).show()
                    updateUIForGuestMode()
                }
            } else {
                runOnUiThread {
                    showGuestConnectionError()
                }
            }
        }
    }
    
    private fun setupCallTimeLimit() {
        // 30분 통화 제한 타이머
        callTimeLimitTimer = object : CountDownTimer(30 * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                
                runOnUiThread {
                    callTimeRemainingTextView?.text = 
                        String.format("남은 시간: %02d:%02d", minutes, seconds)
                    
                    // 5분 남았을 때 경고
                    if (minutes == 5L && seconds == 0L) {
                        showTimeWarning(5)
                    }
                    // 1분 남았을 때 경고
                    else if (minutes == 1L && seconds == 0L) {
                        showTimeWarning(1)
                    }
                }
            }
            
            override fun onFinish() {
                // 통화 자동 종료
                endCallDueToTimeLimit()
            }
        }
    }
    
    private fun showTimeWarning(minutesLeft: Int) {
        runOnUiThread {
            val message = if (minutesLeft == 5) {
                "게스트 모드 통화 시간이 5분 남았습니다."
            } else {
                "게스트 모드 통화 시간이 1분 남았습니다.\n회원가입하시면 무제한 통화가 가능합니다."
            }
            
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction("회원가입") {
                    showRegistrationDialog()
                }
                .show()
        }
    }
    
    private fun endCallDueToTimeLimit() {
        // 통화 종료
        currentCall?.let { call ->
            // call.hangup()
            currentCall = null
        }
        
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle("통화 시간 초과")
                .setMessage("게스트 모드 30분 제한 시간이 초과되어 통화가 종료되었습니다.")
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton("회원가입") { _, _ ->
                    showRegistrationDialog()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    private fun showUpgradeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("프리미엄으로 업그레이드")
            .setMessage("""
                회원가입하시면 다음 기능을 사용할 수 있습니다:
                
                ✓ 무제한 통화 시간
                ✓ 통화 녹음 기능
                ✓ 연락처 저장 및 관리
                ✓ 통화 기록 저장
                ✓ 그룹 통화 (최대 4명)
                ✓ 고화질 영상 통화
                ✓ 클라우드 백업
            """.trimIndent())
            .setPositiveButton("회원가입") { _, _ ->
                showRegistrationDialog()
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showRegistrationDialog() {
        val intent = Intent(this, RegistrationActivity::class.java)
        intent.putExtra("from_guest_mode", true)
        intent.putExtra("guest_user_id", getGuestUserId())
        startActivityForResult(intent, REGISTRATION_REQUEST_CODE)
    }
    
    private fun updateUIForGuestMode() {
        binding.apply {
            // 메인 화면 게스트 모드 UI 업데이트
            toolbar.title = "VoIP (게스트)"
            toolbar.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.guest_mode_color))
            
            // 하단 네비게이션 메뉴 조정
            // bottomNavigation.menu.findItem(R.id.nav_contacts).isVisible = false
            // bottomNavigation.menu.findItem(R.id.nav_history).isVisible = false
        }
    }
    
    private fun showGuestConnectionError() {
        MaterialAlertDialogBuilder(this)
            .setTitle("연결 실패")
            .setMessage("게스트 모드로 연결할 수 없습니다. 네트워크 연결을 확인해주세요.")
            .setPositiveButton("다시 시도") { _, _ ->
                setupGuestMode()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }
    
    private fun getGuestUserId(): String {
        return getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
            .getString("guest_user_id", "") ?: ""
    }
    
    // 액티비티 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REGISTRATION_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    // 회원가입 성공 - 게스트 모드 종료하고 정식 사용자로 전환
                    clearGuestMode()
                    setupNormalMode()
                }
            }
        }
    }
    
    private fun clearGuestMode() {
        // 게스트 모드 정보 삭제
        getSharedPreferences("VoipExPrefs", MODE_PRIVATE).edit().apply {
            remove("guest_user_id")
            remove("guest_user_name")
            remove("is_guest_mode")
            apply()
        }
        
        // 타이머 취소
        callTimeLimitTimer?.cancel()
    }
    
    private fun setupNormalMode() {
        // 정상 모드로 UI 전환
        binding.apply {
            logoutFab.visibility = View.VISIBLE
            toolbar.title = "VoIP"
            toolbar.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
        }
        
        guestModeBanner?.visibility = View.GONE
        callTimeLimitLayout?.visibility = View.GONE
        
        // recordButton?.isEnabled = true
        // recordButton?.alpha = 1.0f
        // contactsButton?.visibility = View.VISIBLE
        
        // 하단 네비게이션 모든 메뉴 표시
        // bottomNavigation?.menu?.findItem(R.id.nav_contacts)?.isVisible = true
        // bottomNavigation?.menu?.findItem(R.id.nav_history)?.isVisible = true
    }
    
    private fun setupGuestUsersList() {
        Timber.d("[GuestMode] setupGuestUsersList called")
        // 게스트 사용자 어댑터 초기화
        guestAdapter = GuestUsersAdapter { guestUser ->
            // 게스트 사용자 클릭 시 통화 시작
            startGuestCall(guestUser)
        }
        
        // RecyclerView에 게스트 어댑터 설정
        binding.contactsRecyclerView.apply {
            visibility = View.VISIBLE
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = guestAdapter
        }
        
        // Empty state 텍스트 변경
        binding.tvEmptyState.text = "게스트 사용자를 찾는 중..."
        
        // 온라인 게스트 목록 로드
        loadOnlineGuests()
        
        // 주기적으로 게스트 목록 업데이트
        startGuestListPolling()
    }
    
    private fun connectGuestSignaling(guestUser: GuestUser) {
        Timber.d("[GuestMode] connectGuestSignaling called with user: ${guestUser.name}")
        // 게스트 시그널링 클라이언트 연결
        guestSignalingClient.connect(guestUser)
        
        // 시그널링 이벤트 관찰
        lifecycleScope.launch {
            guestSignalingClient.signalingEvents.collect { event ->
                when (event) {
                    is SignalingEvent.GuestJoined -> {
                        // 새 게스트 추가
                        val newGuest = GuestUser(event.userId, event.name)
                        if (!guestUsers.any { it.id == newGuest.id }) {
                            guestUsers.add(newGuest)
                            runOnUiThread {
                                guestAdapter.submitList(guestUsers.toList())
                                hideEmptyState()
                            }
                        }
                    }
                    is SignalingEvent.GuestLeft -> {
                        // 게스트 제거
                        guestUsers.removeAll { it.id == event.userId }
                        runOnUiThread {
                            guestAdapter.submitList(guestUsers.toList())
                            if (guestUsers.isEmpty()) {
                                showEmptyState()
                            }
                        }
                    }
                    is SignalingEvent.IncomingCall -> {
                        // 통화 수신 처리
                        runOnUiThread {
                            showIncomingCallDialog(event)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun loadOnlineGuests() {
        Timber.d("[GuestMode] loadOnlineGuests called")
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)
        val myGuestId = prefs.getString("user_id", null)
        
        Timber.d("[GuestMode] token: $token, myGuestId: $myGuestId")
        
        if (token == null || myGuestId == null) {
            Timber.e("[GuestMode] token or myGuestId is null")
            return
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()
            
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3002/")  // user-service의 올바른 포트
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        val userApi = retrofit.create(UserApi::class.java)
        
        lifecycleScope.launch {
            try {
                Timber.d("[GuestMode] Calling getOnlineGuests API...")
                val response = userApi.getOnlineGuests()
                val guests = response.data.users  // 'guests' -> 'users'로 변경
                
                Timber.d("[GuestMode] Received ${guests.size} guests from API")
                guests.forEach { guest ->
                    Timber.d("[GuestMode] Guest: id=${guest.id}, name=${guest.name}")
                }
                
                // 자신을 제외한 게스트 목록 표시
                guestUsers.clear()
                guests.filter { it.id != myGuestId }.forEach { guestInfo ->
                    guestUsers.add(GuestUser(guestInfo.id, guestInfo.name))
                }
                
                Timber.d("[GuestMode] Filtered guests count: ${guestUsers.size}")
                
                runOnUiThread {
                    Timber.d("[GuestMode] Updating UI with ${guestUsers.size} guests")
                    guestAdapter.submitList(guestUsers.toList())
                    if (guestUsers.isEmpty()) {
                        showEmptyState()
                        binding.tvEmptyState.text = "현재 접속 중인 다른 게스트가 없습니다"
                    } else {
                        hideEmptyState()
                        binding.contactsRecyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[GuestMode] Failed to load online guests")
                runOnUiThread {
                    showEmptyState()
                    binding.tvEmptyState.text = "게스트 목록을 불러올 수 없습니다\n${e.message}"
                }
            }
        }
    }
    
    private fun startGuestCall(guestUser: GuestUser) {
        Timber.d("[GuestCall] Starting call with guest: ${guestUser.name} (${guestUser.id})")
        
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val myId = prefs.getString("user_id", null)
        val myName = prefs.getString("user_name", null)
        
        if (myId == null || myName == null) {
            Timber.e("[GuestCall] Missing user info")
            Toast.makeText(this, "사용자 정보가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                Timber.d("[GuestCall] Creating guest call - callerId: $myId, calleeId: ${guestUser.id}")
                
                // 게스트 통화 생성
                val response = callRepository.createGuestCall(
                    callerId = myId,
                    calleeId = guestUser.id,
                    callerName = myName,
                    calleeName = guestUser.name,
                    callType = "audio"
                )
                
                Timber.d("[GuestCall] Call created successfully - response: $response")
                
                val callData = response.data
                
                // 시그널링 서버로 통화 요청 (roomId와 callId 전달)
                guestSignalingClient.initiateCall(
                    targetUserId = guestUser.id,
                    callType = "audio",
                    roomId = callData.roomId,
                    callId = callData.callId
                )
                
                // CallActivity로 이동
                val intent = Intent(this@MainActivity, CallActivity::class.java).apply {
                    putExtra(CallActivity.EXTRA_CALL_ID, callData.callId)
                    putExtra(CallActivity.EXTRA_ROOM_ID, callData.roomId)
                    putExtra(CallActivity.EXTRA_USER_ID, guestUser.id)
                    putExtra(CallActivity.EXTRA_USER_NAME, guestUser.name)
                    putExtra(CallActivity.EXTRA_IS_INCOMING, false)
                    putExtra(CallActivity.EXTRA_IS_GUEST_CALL, true)
                    putExtra(CallActivity.EXTRA_CALL_TYPE, "audio")
                }
                
                Timber.d("[GuestCall] Starting CallActivity with extras: " +
                    "callId=${callData.callId}, roomId=${callData.roomId}, userId=${guestUser.id}")
                
                startActivity(intent)
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] Failed to create guest call")
                Toast.makeText(
                    this@MainActivity,
                    "통화 생성 실패: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showIncomingCallDialog(event: SignalingEvent.IncomingCall) {
        MaterialAlertDialogBuilder(this)
            .setTitle("통화 수신")
            .setMessage("${event.callerName}님이 통화를 요청했습니다.")
            .setPositiveButton("수락") { _, _ ->
                // 통화 수락
                guestSignalingClient.acceptCall(event.roomId)
                
                // CallActivity로 이동
                val intent = Intent(this, CallActivity::class.java).apply {
                    putExtra(CallActivity.EXTRA_CALL_ID, event.callId)
                    putExtra(CallActivity.EXTRA_ROOM_ID, event.roomId)
                    putExtra(CallActivity.EXTRA_USER_ID, event.callerId)
                    putExtra(CallActivity.EXTRA_USER_NAME, event.callerName)
                    putExtra(CallActivity.EXTRA_IS_INCOMING, true)
                    putExtra(CallActivity.EXTRA_IS_GUEST_CALL, true)
                    putExtra(CallActivity.EXTRA_CALL_TYPE, event.callType)
                }
                
                Timber.d("[GuestCall] Starting incoming CallActivity with extras: " +
                    "callId=${event.callId}, roomId=${event.roomId}, userId=${event.callerId}")
                
                startActivity(intent)
            }
            .setNegativeButton("거절") { _, _ ->
                guestSignalingClient.rejectCall(event.roomId)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startGuestListPolling() {
        guestListPollingJob?.cancel()
        guestListPollingJob = lifecycleScope.launch {
            while (coroutineContext.isActive) {
                loadOnlineGuests()
                delay(5000) // 5초마다 업데이트
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 게스트 시그널링 연결 해제
        if (getSharedPreferences("VoipExPrefs", MODE_PRIVATE).getBoolean("is_guest", false)) {
            guestSignalingClient.disconnect()
        }
    }
    
    private fun setupSimpleGuestMode() {
        Timber.d("[GuestMode] setupSimpleGuestMode called")
        
        // UI 설정
        binding.apply {
            // 로그아웃 버튼 숨기기
            logoutFab.visibility = View.GONE
            fabAddCall.visibility = View.GONE
            
            // 타이틀 변경
            toolbar.title = "VoIP (게스트)"
            toolbar.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.guest_mode_color))
        }
        
        // 게스트 사용자 어댑터 설정
        guestAdapter = GuestUsersAdapter { guestUser ->
            startGuestCall(guestUser)
        }
        
        // RecyclerView 설정
        binding.contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = guestAdapter
        }
        
        // 빈 상태 텍스트 변경
        binding.tvEmptyState.text = "게스트 사용자를 찾는 중..."
        
        // 온라인 게스트 목록 로드
        loadOnlineGuests()
        
        // 주기적으로 게스트 목록 업데이트
        startGuestListPolling()
        
        // WebSocket 연결
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        val userName = prefs.getString("user_name", null)
        
        if (userId != null && userName != null) {
            val guestUser = GuestUser(userId, userName)
            guestSignalingClient.connect(guestUser)
            
            // 시그널링 이벤트 관찰
            lifecycleScope.launch {
                guestSignalingClient.signalingEvents.collect { event ->
                    when (event) {
                        is SignalingEvent.GuestJoined -> {
                            Timber.d("[GuestMode] Guest joined: ${event.userId}")
                            val newGuest = GuestUser(event.userId, event.name)
                            if (!guestUsers.any { it.id == newGuest.id }) {
                                guestUsers.add(newGuest)
                                runOnUiThread {
                                    guestAdapter.submitList(guestUsers.toList())
                                    hideEmptyState()
                                }
                            }
                        }
                        is SignalingEvent.GuestLeft -> {
                            Timber.d("[GuestMode] Guest left: ${event.userId}")
                            guestUsers.removeAll { it.id == event.userId }
                            runOnUiThread {
                                guestAdapter.submitList(guestUsers.toList())
                                if (guestUsers.isEmpty()) {
                                    showEmptyState()
                                }
                            }
                        }
                        is SignalingEvent.IncomingCall -> {
                            runOnUiThread {
                                showIncomingCallDialog(event)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    companion object {
        private const val GUEST_PERMISSION_REQUEST_CODE = 2001
        private const val REGISTRATION_REQUEST_CODE = 2002
    }
} 

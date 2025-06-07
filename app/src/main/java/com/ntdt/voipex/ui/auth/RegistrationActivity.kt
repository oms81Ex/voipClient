package com.ntdt.voipex.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ntdt.voipex.databinding.ActivityRegisterBinding
import com.ntdt.voipex.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegistrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegistrationViewModel by viewModels()
    
    private var isFromGuestMode = false
    private var guestUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Intent에서 게스트 모드 정보 가져오기
        isFromGuestMode = intent.getBooleanExtra("from_guest_mode", false)
        guestUserId = intent.getStringExtra("guest_user_id")
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        // 툴바 설정
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "회원가입"
        }
        
        // 게스트 모드에서 온 경우 안내 메시지 표시
        if (isFromGuestMode) {
            binding.guestUpgradeCard.visibility = View.VISIBLE
            binding.guestUpgradeMessage.text = """
                게스트 모드에서 정식 회원으로 전환하시면
                모든 프리미엄 기능을 사용할 수 있습니다.
            """.trimIndent()
        }
        
        // 회원가입 버튼 클릭 리스너
        binding.registerButton.setOnClickListener {
            registerUser()
        }
        
        // 이미 계정이 있는 경우
        binding.loginLink.setOnClickListener {
            finish() // 로그인 화면으로 돌아가기
        }
        
        // 이용약관 링크
        binding.termsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.registerButton.isEnabled = isChecked
        }
        
        binding.termsLink.setOnClickListener {
            showTermsDialog()
        }
        
        binding.privacyLink.setOnClickListener {
            showPrivacyDialog()
        }
    }
    
    private fun registerUser() {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()
        
        // 유효성 검사
        when {
            name.isEmpty() -> {
                binding.nameTextInputLayout.error = "이름을 입력해주세요"
                return
            }
            email.isEmpty() -> {
                binding.emailTextInputLayout.error = "이메일을 입력해주세요"
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.emailTextInputLayout.error = "올바른 이메일 형식이 아닙니다"
                return
            }
            password.isEmpty() -> {
                binding.passwordTextInputLayout.error = "비밀번호를 입력해주세요"
                return
            }
            password.length < 8 -> {
                binding.passwordTextInputLayout.error = "비밀번호는 8자 이상이어야 합니다"
                return
            }
            password != confirmPassword -> {
                binding.confirmPasswordTextInputLayout.error = "비밀번호가 일치하지 않습니다"
                return
            }
            !binding.termsCheckBox.isChecked -> {
                Toast.makeText(this, "이용약관에 동의해주세요", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // 에러 메시지 초기화
        binding.nameTextInputLayout.error = null
        binding.emailTextInputLayout.error = null
        binding.passwordTextInputLayout.error = null
        binding.confirmPasswordTextInputLayout.error = null
        
        // 회원가입 요청
        viewModel.register(
            name = name,
            email = email,
            password = password,
            guestUserId = if (isFromGuestMode) guestUserId else null
        )
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is RegistrationUiState.Idle -> {
                        hideLoading()
                    }
                    is RegistrationUiState.Loading -> {
                        showLoading()
                    }
                    is RegistrationUiState.Success -> {
                        hideLoading()
                        showSuccessDialog()
                    }
                    is RegistrationUiState.Error -> {
                        hideLoading()
                        showError(state.message)
                    }
                }
            }
        }
    }
    
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.registerButton.isEnabled = false
        binding.nameEditText.isEnabled = false
        binding.emailEditText.isEnabled = false
        binding.passwordEditText.isEnabled = false
        binding.confirmPasswordEditText.isEnabled = false
    }
    
    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.registerButton.isEnabled = binding.termsCheckBox.isChecked
        binding.nameEditText.isEnabled = true
        binding.emailEditText.isEnabled = true
        binding.passwordEditText.isEnabled = true
        binding.confirmPasswordEditText.isEnabled = true
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("다시 시도") {
                registerUser()
            }
            .show()
    }
    
    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("회원가입 완료")
            .setMessage(
                if (isFromGuestMode) {
                    "회원가입이 완료되었습니다!\n게스트 모드에서 정식 회원으로 전환되었습니다."
                } else {
                    "회원가입이 완료되었습니다!\n이제 모든 기능을 사용할 수 있습니다."
                }
            )
            .setPositiveButton("확인") { _, _ ->
                // 성공 결과 반환
                setResult(RESULT_OK)
                
                // 메인 화면으로 이동 (로그인 화면 건너뛰기)
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showTermsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("이용약관")
            .setMessage("""
                VoIP 서비스 이용약관
                
                제1조 (목적)
                본 약관은 VoIP 서비스(이하 "서비스")의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.
                
                제2조 (서비스의 제공)
                1. 회사는 다음과 같은 서비스를 제공합니다:
                   - 음성 및 영상 통화 서비스
                   - 메시지 전송 서비스
                   - 연락처 관리 서비스
                   - 통화 녹음 서비스 (프리미엄 회원)
                
                제3조 (개인정보 보호)
                회사는 이용자의 개인정보를 보호하기 위해 최선을 다하며, 개인정보처리방침에 따라 관리합니다.
                
                제4조 (이용자의 의무)
                이용자는 서비스를 불법적인 목적으로 사용해서는 안 되며, 타인의 권리를 침해하는 행위를 해서는 안 됩니다.
            """.trimIndent())
            .setPositiveButton("확인", null)
            .show()
    }
    
    private fun showPrivacyDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("개인정보처리방침")
            .setMessage("""
                개인정보처리방침
                
                1. 수집하는 개인정보 항목
                   - 필수항목: 이름, 이메일, 비밀번호
                   - 선택항목: 프로필 사진, 전화번호
                
                2. 개인정보의 수집 및 이용목적
                   - 회원 가입 및 관리
                   - 서비스 제공
                   - 고객 지원
                
                3. 개인정보의 보유 및 이용기간
                   - 회원 탈퇴 시까지
                   - 관련 법령에 따른 보관 의무 기간
                
                4. 개인정보의 제3자 제공
                   - 원칙적으로 이용자의 개인정보를 제3자에게 제공하지 않습니다.
                   - 법령에 의한 경우는 예외로 합니다.
            """.trimIndent())
            .setPositiveButton("확인", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
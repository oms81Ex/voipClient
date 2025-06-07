package com.ntdt.voipex.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ntdt.voipex.databinding.FragmentCallHistoryBinding

class CallHistoryFragment : Fragment() {
    private var _binding: FragmentCallHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // TODO: 통화 기록 구현
        binding.tvPlaceholder.text = "통화 기록이 없습니다"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
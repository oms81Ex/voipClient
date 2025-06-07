package com.ntdt.voipex.ui.guest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ntdt.voipex.databinding.ItemGuestUserBinding
import com.ntdt.voipex.domain.model.GuestUser

class GuestUsersAdapter(
    private val onUserClick: (GuestUser) -> Unit
) : ListAdapter<GuestUser, GuestUsersAdapter.GuestUserViewHolder>(GuestUserDiffCallback()) {

    private val users = mutableListOf<GuestUser>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuestUserViewHolder {
        val binding = ItemGuestUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GuestUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GuestUserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun addUser(user: GuestUser) {
        val currentList = currentList.toMutableList()
        if (currentList.none { it.id == user.id }) {
            currentList.add(user)
            submitList(currentList)
        }
    }

    fun removeUser(userId: String) {
        val currentList = currentList.toMutableList()
        currentList.removeAll { it.id == userId }
        submitList(currentList)
    }

    inner class GuestUserViewHolder(
        private val binding: ItemGuestUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onUserClick(getItem(position))
                }
            }
            
            binding.btnCall.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onUserClick(getItem(position))
                }
            }
        }

        fun bind(user: GuestUser) {
            binding.tvUserName.text = user.name
            binding.tvUserId.text = user.id
            
            // 온라인 상태 표시
            if (user.isOnline) {
                binding.ivStatus.setColorFilter(android.graphics.Color.GREEN)
            } else {
                binding.ivStatus.setColorFilter(android.graphics.Color.GRAY)
            }
        }
    }
}

class GuestUserDiffCallback : DiffUtil.ItemCallback<GuestUser>() {
    override fun areItemsTheSame(oldItem: GuestUser, newItem: GuestUser): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: GuestUser, newItem: GuestUser): Boolean {
        return oldItem == newItem
    }
}

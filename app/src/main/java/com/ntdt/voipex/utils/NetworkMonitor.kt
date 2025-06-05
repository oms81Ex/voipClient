package com.ntdt.voipex.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Unknown)
    val networkState: StateFlow<NetworkState> = _networkState

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.let { updateNetworkState(it) }
            Timber.d("Network available: $network")
        }

        override fun onLost(network: Network) {
            _networkState.value = NetworkState.NoConnection
            Timber.d("Network lost: $network")
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetworkState(capabilities)
            Timber.d("Network capabilities changed: $capabilities")
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun updateNetworkState(capabilities: NetworkCapabilities) {
        val state = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                NetworkState.Wifi(
                    isHighSpeed = capabilities.getLinkDownstreamBandwidthKbps() >= 1000
                )
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val generation = when {
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "5G"
                    capabilities.getLinkDownstreamBandwidthKbps() >= 1000 -> "4G"
                    else -> "3G"
                }
                NetworkState.Cellular(generation)
            }
            else -> NetworkState.Unknown
        }
        _networkState.value = state
    }

    fun cleanup() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

sealed class NetworkState {
    object Unknown : NetworkState()
    object NoConnection : NetworkState()
    data class Wifi(val isHighSpeed: Boolean) : NetworkState()
    data class Cellular(val generation: String) : NetworkState()
} 
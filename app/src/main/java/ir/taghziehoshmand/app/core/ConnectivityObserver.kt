package ir.taghziehoshmand.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مشاهده‌گر زنده وضعیت اتصال اینترنت:
 * - با NetworkCallback هر تغییر وای‌فای/دیتا را فوراً ثبت می‌کند.
 * - now() وضعیت لحظه‌ای را برای تصمیم‌گیری سوئیچ خودکار آنلاین/آفلاین می‌دهد.
 */
class ConnectivityObserver(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(now())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = now() }
                override fun onLost(network: Network) { _online.value = now() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = now()
                }
            })
        } catch (_: Exception) {
            // روی برخی دستگاه‌ها ثبت کال‌بک ممکن نیست؛ now() همچنان کار می‌کند
        }
    }

    /** بررسی لحظه‌ای: آیا شبکه فعال با قابلیت اینترنت وجود دارد؟ */
    fun now(): Boolean {
        return try {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            val knownTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            knownTransport && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}

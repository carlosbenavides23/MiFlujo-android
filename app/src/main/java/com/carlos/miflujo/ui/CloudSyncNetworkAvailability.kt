package com.carlos.miflujo.ui

internal fun isUsableCloudSyncNetwork(
    hasInternet: Boolean,
    isValidated: Boolean,
    hasWifi: Boolean,
    hasCellular: Boolean,
    hasEthernet: Boolean,
    hasVpn: Boolean,
    hasBluetooth: Boolean,
): Boolean {
    val hasAcceptedTransport = hasWifi || hasCellular || hasEthernet || hasVpn
    val isBluetoothOnly = hasBluetooth && !hasAcceptedTransport
    return hasInternet &&
        isValidated &&
        hasAcceptedTransport &&
        !isBluetoothOnly
}

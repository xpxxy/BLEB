package com.bleb.bridge.ble.parser

interface HeartRateParser {
    fun parse(scanRecord: ByteArray): Int?
}

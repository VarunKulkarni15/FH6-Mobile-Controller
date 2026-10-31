package com.wheelpad.app.network

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UdpFastStreamClient {
    private var socket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private var serverPort: Int = 4242
    private var sequenceNumber: Long = 0

    private val packetBuffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)

    // Current input state (Continuously streamed at 120Hz)
    @Volatile private var currentSteering: Float = 0f
    @Volatile private var currentThrottle: Float = 0f
    @Volatile private var currentBrake: Float = 0f
    @Volatile private var currentHandbrake: Boolean = false
    @Volatile private var currentButtonsBitmask: Int = 0
    @Volatile private var currentLeftThumbY: Float = 0f
    @Volatile private var currentRightThumbX: Float = 0f
    @Volatile private var currentRightThumbY: Float = 0f

    private var streamJob: Job? = null
    private var discoveryJob: Job? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(host: String, port: Int = 4242) {
        clientScope.launch {
            try {
                serverAddress = InetAddress.getByName(host)
                serverPort = port
                if (socket == null || socket?.isClosed == true) {
                    socket = DatagramSocket()
                    socket?.broadcast = true
                }

                // Start 120Hz continuous streaming loop (every ~8ms)
                startContinuousStream()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Updates input state and transmits immediately.
     */
    fun updateInputs(
        steering: Float,
        throttle: Float,
        brake: Float,
        handbrake: Boolean,
        buttonsBitmask: Int = 0,
        leftThumbY: Float = 0f,
        rightThumbX: Float = 0f,
        rightThumbY: Float = 0f
    ) {
        currentSteering = steering
        currentThrottle = throttle
        currentBrake = brake
        currentHandbrake = handbrake
        currentButtonsBitmask = buttonsBitmask
        currentLeftThumbY = leftThumbY
        currentRightThumbX = rightThumbX
        currentRightThumbY = rightThumbY

        // Immediate fast flush
        sendCurrentPacket()
    }

    /**
     * Backward-compatible alias for sendInputState.
     */
    fun sendInputState(
        steering: Float,
        throttle: Float,
        brake: Float,
        handbrake: Boolean,
        buttonsBitmask: Int = 0,
        leftThumbY: Float = 0f,
        rightThumbX: Float = 0f,
        rightThumbY: Float = 0f
    ) {
        updateInputs(steering, throttle, brake, handbrake, buttonsBitmask, leftThumbY, rightThumbX, rightThumbY)
    }

    private fun startContinuousStream() {
        streamJob?.cancel()
        streamJob = clientScope.launch {
            while (isActive) {
                sendCurrentPacket()
                delay(8) // ~120 Hz
            }
        }
    }

    private fun sendCurrentPacket() {
        val s = socket ?: return
        val addr = serverAddress ?: return

        try {
            val steerShort = (currentSteering.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
            val throttleByte = (currentThrottle.coerceIn(0.0f, 1.0f) * 255f).toInt().toByte()
            val brakeByte = (currentBrake.coerceIn(0.0f, 1.0f) * 255f).toInt().toByte()
            val handbrakeByte = if (currentHandbrake) 255.toByte() else 0.toByte()
            val ltyShort = (currentLeftThumbY.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
            val rtxShort = (currentRightThumbX.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
            val rtyShort = (currentRightThumbY.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()

            synchronized(packetBuffer) {
                packetBuffer.clear()
                // 0x00: Magic (0x5750 = 'WP')
                packetBuffer.putShort(0x5750.toShort())
                // 0x02: Sequence (uint32)
                packetBuffer.putInt((sequenceNumber++ and 0xFFFFFFFFL).toInt())
                // 0x06: Timestamp (uint64)
                packetBuffer.putLong(System.nanoTime() / 1000)
                // 0x0E: Steering / LeftThumbX (int16)
                packetBuffer.putShort(steerShort)
                // 0x10: Throttle (uint8)
                packetBuffer.put(throttleByte)
                // 0x11: Brake (uint8)
                packetBuffer.put(brakeByte)
                // 0x12: Handbrake (uint8)
                packetBuffer.put(handbrakeByte)
                // 0x13: Buttons (3 bytes bitmask)
                packetBuffer.put((currentButtonsBitmask and 0xFF).toByte())
                packetBuffer.put(((currentButtonsBitmask shr 8) and 0xFF).toByte())
                packetBuffer.put(((currentButtonsBitmask shr 16) and 0xFF).toByte())
                // 0x16: LeftThumbY (int16)
                packetBuffer.putShort(ltyShort)
                // 0x18: RightThumbX (int16)
                packetBuffer.putShort(rtxShort)
                // 0x1A: RightThumbY (int16)
                packetBuffer.putShort(rtyShort)

                // 0x1C: CRC16 Checksum over first 28 bytes
                val crc = computeCrc16(packetBuffer.array(), 0, 28)
                packetBuffer.putShort(crc.toShort())

                val packet = DatagramPacket(packetBuffer.array(), 30, addr, serverPort)
                s.send(packet)
            }
        } catch (e: Exception) {
            // Ignore occasional dropped UDP packet
        }
    }

    /**
     * Automatic LAN & USB Tether Discovery: Probes ARP neighbors, interfaces, and beacons.
     */
    fun startAutoDiscovery(onHostFound: (ip: String, tcpPort: Int, udpPort: Int) -> Unit) {
        discoveryJob?.cancel()
        discoveryJob = clientScope.launch {
            var discoverySocket: DatagramSocket? = null
            try {
                discoverySocket = DatagramSocket(4240).apply {
                    broadcast = true
                    soTimeout = 2000
                }
            } catch (e: Exception) {
                try {
                    discoverySocket = DatagramSocket().apply {
                        broadcast = true
                        soTimeout = 2000
                    }
                } catch (ex: Exception) { }
            }

            // Active ARP + TCP Probing for Instant USB Tethering & LAN Discovery
            launch {
                val candidateIps = mutableSetOf<String>()

                // 1. Check /proc/net/arp for connected PC (USB tethering rndis0 / wlan)
                try {
                    java.io.File("/proc/net/arp").forEachLine { line ->
                        val tokens = line.trim().split(Regex("\\s+"))
                        if (tokens.size >= 4 && tokens[0] != "IP") {
                            val ip = tokens[0]
                            val flags = tokens[2]
                            if (flags != "0x0" && ip != "0.0.0.0") {
                                candidateIps.add(ip)
                            }
                        }
                    }
                } catch (e: Exception) { }

                // 2. Also check standard tethering gateway / reverse ADB
                candidateIps.add("127.0.0.1")

                // Probe candidate IPs directly via UDP and TCP
                for (candIp in candidateIps) {
                    launch {
                        try {
                            val pingBytes = "WHEELPAD_DISCOVER".toByteArray()
                            val candAddr = InetAddress.getByName(candIp)
                            val pingPacket = DatagramPacket(pingBytes, pingBytes.size, candAddr, 4240)
                            discoverySocket?.send(pingPacket)
                        } catch (e: Exception) { }

                        // Rapid TCP port 4243 probe
                        try {
                            java.net.Socket().use { probeSock ->
                                probeSock.connect(InetSocketAddress(candIp, 4243), 600)
                                withContext(Dispatchers.Main) {
                                    onHostFound(candIp, 4243, 4242)
                                }
                            }
                        } catch (e: Exception) { }
                    }
                }

                // 3. Broadcast to all active network interfaces
                try {
                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val networkInterface = interfaces.nextElement()
                        if (networkInterface.isLoopback || !networkInterface.isUp) continue
                        for (interfaceAddress in networkInterface.interfaceAddresses) {
                            val broadcast = interfaceAddress.broadcast ?: continue
                            try {
                                val pingBytes = "WHEELPAD_DISCOVER".toByteArray()
                                val pingPacket = DatagramPacket(pingBytes, pingBytes.size, broadcast, 4240)
                                discoverySocket?.send(pingPacket)
                            } catch (e: Exception) { }
                        }
                    }
                } catch (e: Exception) { }

                // 4. Standard 255.255.255.255 broadcast fallback
                try {
                    val pingBytes = "WHEELPAD_DISCOVER".toByteArray()
                    val bcastAddr = InetAddress.getByName("255.255.255.255")
                    val pingPacket = DatagramPacket(pingBytes, pingBytes.size, bcastAddr, 4240)
                    discoverySocket?.send(pingPacket)
                } catch (e: Exception) { }
            }

            val buf = ByteArray(1024)
            val rxPacket = DatagramPacket(buf, buf.size)

            while (isActive && discoverySocket != null && !discoverySocket.isClosed) {
                try {
                    discoverySocket.receive(rxPacket)
                    val msg = String(rxPacket.data, 0, rxPacket.length)
                    if (msg.startsWith("WHEELPAD_BEACON:") || msg.startsWith("WHEELPAD_REPLY:")) {
                        val jsonPart = msg.substringAfter(":")
                        val obj = JSONObject(jsonPart)
                        val ip = obj.optString("ip", rxPacket.address.hostAddress ?: "")
                        val tcpPort = obj.optInt("tcpPort", 4243)
                        val udpPort = obj.optInt("udpPort", 4242)

                        if (ip.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                onHostFound(ip, tcpPort, udpPort)
                            }
                            break // Successfully discovered!
                        }
                    }
                } catch (e: Exception) {
                    // Socket timeout, repeat
                }
            }
        }
    }

    private fun computeCrc16(bytes: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor ((bytes[i].toInt() and 0xFF) shl 8)
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
            }
        }
        return crc and 0xFFFF
    }

    fun close() {
        streamJob?.cancel()
        discoveryJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) { }
        socket = null
    }
}

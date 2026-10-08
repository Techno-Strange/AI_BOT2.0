package com.example.ai_bot20

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Expression { NORMAL, SMILING, CRYING, SHIVERING, ANGRY }

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class Esp32Data(
    val temp: Float = 28.4f,
    val humidity: Float = 63.0f,
    val battery: Int = 88,
    val expression: Expression = Expression.NORMAL
)

// Friday Text To Speech Manager (Female Voice)
class FridayVoiceManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    var isReady by mutableStateOf(false)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            setFemaleVoice()
            isReady = true
        }
    }

    private fun setFemaleVoice() {
        val voices = tts?.voices ?: return
        for (v in voices) {
            val name = v.name.lowercase(Locale.US)
            if (name.contains("female") ||
                name.contains("en-us-x-sfg") ||
                name.contains("en-us-x-tpf") ||
                name.contains("en-us-x-iob") ||
                name.contains("en-gb-x-rfn") ||
                name.contains("en-us-x-sfg#female")) {
                tts?.voice = v
                break
            }
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FridayVoice")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

class Esp32Client(private val scope: CoroutineScope) {
    var ipAddress by mutableStateOf("friday-bot.local")
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
    var esp32Data by mutableStateOf(Esp32Data())
    var errorMessage by mutableStateOf("")
    var isUsbMode by mutableStateOf(false)

    private var pollJob: Job? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInEndpoint: UsbEndpoint? = null
    private var usbOutEndpoint: UsbEndpoint? = null

    fun connectUsb(context: Context): Boolean {
        pollJob?.cancel()
        closeUsb()

        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList

            if (deviceList.isEmpty()) {
                errorMessage = "No USB device connected. Plug USB OTG cable & enable OTG in phone settings."
                connectionState = ConnectionState.ERROR
                return false
            }

            val device: UsbDevice = deviceList.values.first()
            val connection = usbManager.openDevice(device)

            if (connection == null) {
                val permissionIntent = android.app.PendingIntent.getBroadcast(
                    context, 0, Intent("com.example.ai_bot20.USB_PERMISSION"),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                usbManager.requestPermission(device, permissionIntent)
                errorMessage = "Please grant USB permission on popup"
                connectionState = ConnectionState.CONNECTING
                return false
            }

            var selectedInterface: UsbInterface? = null
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) {
                            inEp = ep
                        } else if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) {
                            outEp = ep
                        }
                    }
                }
                if (inEp != null && outEp != null) {
                    selectedInterface = intf
                    break
                }
            }

            if (selectedInterface == null || inEp == null || outEp == null) {
                errorMessage = "Could not detect USB Serial endpoints"
                connectionState = ConnectionState.ERROR
                connection.close()
                return false
            }

            connection.claimInterface(selectedInterface, true)

            // Configure baud rate 115200 (CDC Line Coding: 115200, 1 stop bit, 0 parity, 8 data bits)
            val baudRate = 115200
            val lineCoding = byteArrayOf(
                (baudRate and 0xFF).toByte(),
                ((baudRate shr 8) and 0xFF).toByte(),
                ((baudRate shr 16) and 0xFF).toByte(),
                ((baudRate shr 24) and 0xFF).toByte(),
                0x00, // 1 stop bit
                0x00, // parity none
                0x08  // 8 data bits
            )
            connection.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 1000)

            this.usbConnection = connection
            this.usbInEndpoint = inEp
            this.usbOutEndpoint = outEp
            this.isUsbMode = true
            this.ipAddress = "USB OTG Serial"
            this.connectionState = ConnectionState.CONNECTED
            this.errorMessage = ""

            startUsbPolling()
            return true
        } catch (e: Exception) {
            errorMessage = "USB Error: ${e.localizedMessage}"
            connectionState = ConnectionState.ERROR
            return false
        }
    }

    private fun startUsbPolling() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            val sb = StringBuilder()

            while (isActive && isUsbMode && usbConnection != null && usbOutEndpoint != null) {
                try {
                    val cmd = "GET_DATA\n".toByteArray(Charsets.UTF_8)
                    usbConnection?.bulkTransfer(usbOutEndpoint, cmd, cmd.size, 1000)

                    delay(250)

                    val len = usbConnection?.bulkTransfer(usbInEndpoint, buffer, buffer.size, 1000) ?: 0
                    if (len > 0) {
                        val text = String(buffer, 0, len, Charsets.UTF_8)
                        sb.append(text)

                        val raw = sb.toString()
                        val startIndex = raw.indexOf('{')
                        val endIndex = raw.indexOf('}')

                        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                            val jsonString = raw.substring(startIndex, endIndex + 1)
                            sb.clear()
                            sb.append(raw.substring(endIndex + 1))

                            val json = JSONObject(jsonString)
                            val tempVal = if (json.has("temp")) {
                                json.optDouble("temp", 28.4)
                            } else {
                                json.optDouble("temperature", 28.4)
                            }
                            val temp = tempVal.toFloat()
                            val humidity = json.optDouble("humidity", 63.0).toFloat()
                            val battery = json.optInt("battery", 88)
                            val exprStr = json.optString("expression", "NORMAL")
                            val expr = try { Expression.valueOf(exprStr) } catch (_: Exception) { Expression.NORMAL }

                            withContext(Dispatchers.Main) {
                                esp32Data = Esp32Data(temp, humidity, battery, expr)
                                connectionState = ConnectionState.CONNECTED
                                errorMessage = ""
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isUsbMode = false
                        connectionState = ConnectionState.ERROR
                        errorMessage = "USB Cable disconnected"
                    }
                    break
                }
                delay(1200)
            }
        }
    }

    private fun closeUsb() {
        try {
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        usbInEndpoint = null
        usbOutEndpoint = null
        isUsbMode = false
    }

    fun connect(ip: String) {
        closeUsb()
        ipAddress = ip.trim()
        pollJob?.cancel()
        connectionState = ConnectionState.CONNECTING
        errorMessage = ""

        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                fetchData()
                delay(1500)
            }
        }
    }

    fun autoDiscover() {
        closeUsb()
        pollJob?.cancel()
        connectionState = ConnectionState.CONNECTING
        errorMessage = "Searching for ESP32..."

        pollJob = scope.launch(Dispatchers.IO) {
            var discoveredIp: String? = null
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 2500
                val sendData = "DISCOVER_ESP32".toByteArray()
                val sendPacket = DatagramPacket(
                    sendData, sendData.size,
                    InetAddress.getByName("255.255.255.255"), 8888
                )
                socket.send(sendPacket)

                val recvBuf = ByteArray(256)
                val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                socket.receive(recvPacket)
                discoveredIp = recvPacket.address.hostAddress
                socket.close()
            } catch (_: Exception) {
                discoveredIp = "friday-bot.local"
            }

            if (!discoveredIp.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    ipAddress = discoveredIp
                }
                while (isActive) {
                    fetchData()
                    delay(1500)
                }
            } else {
                withContext(Dispatchers.Main) {
                    connectionState = ConnectionState.ERROR
                    errorMessage = "ESP32 not found automatically"
                }
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        closeUsb()
        connectionState = ConnectionState.DISCONNECTED
        errorMessage = ""
    }

    private suspend fun fetchData() = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ipAddress/data")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val tempVal = if (json.has("temp")) {
                    json.optDouble("temp", 28.4)
                } else {
                    json.optDouble("temperature", 28.4)
                }
                val temp = tempVal.toFloat()
                val humidity = json.optDouble("humidity", 63.0).toFloat()
                val battery = json.optInt("battery", 88)
                val exprStr = json.optString("expression", "NORMAL")
                val expr = try { Expression.valueOf(exprStr) } catch (_: Exception) { Expression.NORMAL }

                withContext(Dispatchers.Main) {
                    esp32Data = Esp32Data(temp, humidity, battery, expr)
                    connectionState = ConnectionState.CONNECTED
                    errorMessage = ""
                }
            } else {
                withContext(Dispatchers.Main) {
                    connectionState = ConnectionState.ERROR
                    errorMessage = "HTTP ${conn.responseCode}"
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                if (connectionState != ConnectionState.DISCONNECTED) {
                    connectionState = ConnectionState.ERROR
                    errorMessage = e.localizedMessage ?: "Failed to connect"
                }
            }
        }
    }

    fun sendExpression(expression: Expression) {
        scope.launch(Dispatchers.IO) {
            if (isUsbMode && usbConnection != null && usbOutEndpoint != null) {
                try {
                    val cmd = "EXPRESSION:${expression.name}\n".toByteArray(Charsets.UTF_8)
                    usbConnection?.bulkTransfer(usbOutEndpoint, cmd, cmd.size, 1000)
                } catch (_: Exception) {}
            } else {
                try {
                    val url = URL("http://$ipAddress/expression?value=${expression.name}")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2000
                        readTimeout = 2000
                        requestMethod = "POST"
                    }
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {}
            }
        }
    }
}

class Esp32ViewModel : ViewModel() {
    val esp32Client = Esp32Client(viewModelScope)

    init {
        // Auto connect on launch to 192.168.4.1 (ESP32 AP IP)
        esp32Client.connect("192.168.4.1")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090D16)
                ) {
                    RobotFaceUI()
                }
            }
        }
    }
}

@Composable
fun RobotFaceUI(
    viewModel: Esp32ViewModel = viewModel()
) {
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
    val esp32Client = viewModel.esp32Client
    val fridayVoice = remember(isInPreview) {
        if (!isInPreview) FridayVoiceManager(context) else null
    }

    DisposableEffect(fridayVoice) {
        onDispose {
            fridayVoice?.shutdown()
        }
    }

    var localExpression by remember { mutableStateOf(Expression.NORMAL) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var clockTimeText by remember { mutableStateOf("") }
    var clockDateText by remember { mutableStateOf("") }
    var lastVoiceTranscript by remember { mutableStateOf("") }

    // Digital Table Clock Timer Loop
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = Date()
            clockTimeText = SimpleDateFormat("hh:mm:ss a", Locale.US).format(now)
            clockDateText = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(now).uppercase(Locale.US)
            delay(1000)
        }
    }

    // Auto-disappear Voice Transcript Bubble after 2 seconds
    LaunchedEffect(lastVoiceTranscript) {
        if (lastVoiceTranscript.isNotEmpty()) {
            delay(2000)
            lastVoiceTranscript = ""
        }
    }

    val currentExpression = if (esp32Client.connectionState == ConnectionState.CONNECTED) {
        esp32Client.esp32Data.expression
    } else {
        localExpression
    }

    val displayTemp = esp32Client.esp32Data.temp
    val displayHumidity = esp32Client.esp32Data.humidity
    val displayBattery = esp32Client.esp32Data.battery

    // Process Spoken Voice Commands
    val processCommand: (String) -> Unit = { rawText ->
        val text = rawText.lowercase(Locale.US).trim()
        lastVoiceTranscript = rawText

        when {
            text.contains("temperature") || text.contains("temp") || text.contains("degree") -> {
                val spokenMsg = "The current room temperature is ${"%.1f".format(displayTemp)} degrees Celsius."
                fridayVoice?.speak(spokenMsg)
            }
            text.contains("time") || text.contains("clock") || text.contains("date") -> {
                val timeSpeak = SimpleDateFormat("h:mm a", Locale.US).format(Date())
                val spokenMsg = "The current time is $timeSpeak."
                fridayVoice?.speak(spokenMsg)
            }
            text.contains("humidity") || text.contains("humid") -> {
                val spokenMsg = "The room humidity is ${displayHumidity.toInt()} percent."
                fridayVoice?.speak(spokenMsg)
            }
            text.contains("battery") || text.contains("charge") || text.contains("power") -> {
                val spokenMsg = "The ESP 32 battery is at $displayBattery percent."
                fridayVoice?.speak(spokenMsg)
            }
            text.contains("smile") || text.contains("happy") -> {
                localExpression = Expression.SMILING
                if (esp32Client.connectionState == ConnectionState.CONNECTED) {
                    esp32Client.sendExpression(Expression.SMILING)
                }
                fridayVoice?.speak("Switching to happy mode!")
            }
            text.contains("angry") -> {
                localExpression = Expression.ANGRY
                if (esp32Client.connectionState == ConnectionState.CONNECTED) {
                    esp32Client.sendExpression(Expression.ANGRY)
                }
                fridayVoice?.speak("Entering angry expression!")
            }
            text.contains("cry") || text.contains("sad") -> {
                localExpression = Expression.CRYING
                if (esp32Client.connectionState == ConnectionState.CONNECTED) {
                    esp32Client.sendExpression(Expression.CRYING)
                }
                fridayVoice?.speak("Feeling sad now.")
            }
            text.contains("shiver") || text.contains("cold") -> {
                localExpression = Expression.SHIVERING
                if (esp32Client.connectionState == ConnectionState.CONNECTED) {
                    esp32Client.sendExpression(Expression.SHIVERING)
                }
                fridayVoice?.speak("Brrr! It is cold.")
            }
            text.contains("normal") || text.contains("reset") -> {
                localExpression = Expression.NORMAL
                if (esp32Client.connectionState == ConnectionState.CONNECTED) {
                    esp32Client.sendExpression(Expression.NORMAL)
                }
                fridayVoice?.speak("Back to normal mode.")
            }
            else -> {
                fridayVoice?.speak("I heard: $rawText. How can Friday help you?")
            }
        }
    }

    // Speech Recognizer Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                processCommand(spokenText)
            }
        }
    }

    val startListeningIntent = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: 'Hey Friday, what is the room temperature?' or 'What is the time?'")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // Audio Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListeningIntent()
        } else {
            Toast.makeText(context, "Audio permission is required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    val onMicClick = {
        showVoiceDialog = true
    }

    // Infinite Animations
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")

    // Floating Animation
    val floatY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "float"
    )

    // Shivering Animation
    val shiverX by infiniteTransition.animateFloat(
        initialValue = if (currentExpression == Expression.SHIVERING) -10f else 0f,
        targetValue = if (currentExpression == Expression.SHIVERING) 10f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(40, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shiver"
    )

    // Eye Blink Animation
    val blinkScaleY by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3200
                1.0f at 0
                1.0f at 2800
                0.1f at 2900
                1.0f at 3000
                1.0f at 3200
            },
            repeatMode = RepeatMode.Restart
        ), label = "blink"
    )

    // Crying Animation
    val tearDropY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 160f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "tears"
    )

    // Breathing Pulse Glow Effect
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    // Theme Color Transitions for expressions
    val mainColor by animateColorAsState(
        targetValue = when (currentExpression) {
            Expression.ANGRY -> Color(0xFFEF4444)
            Expression.SHIVERING -> Color(0xFF38BDF8)
            Expression.SMILING -> Color(0xFF34D399)
            Expression.CRYING -> Color(0xFF60A5FA)
            else -> Color(0xFF00E5FF)
        },
        animationSpec = tween(500),
        label = "mainColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF131C31), Color(0xFF070A10)),
                    center = Offset(540f, 600f),
                    radius = 1200f
                )
            )
    ) {
        // 1. Central Robot Face Canvas - STRICTLY FIXED AT SCREEN CENTER
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(270.dp)
                    .offset(x = shiverX.dp, y = floatY.dp)
            ) {
                val width = size.width
                val height = size.height

                val faceFillColor = Color(0xFF0B1120)
                val outerGlowColor = mainColor.copy(alpha = pulseGlow * 0.25f)
                val borderGlowColor = mainColor.copy(alpha = 0.7f)

                // Outer Neon Aura Glow
                drawRoundRect(
                    color = outerGlowColor,
                    topLeft = Offset(-12f, -12f),
                    size = Size(width + 24f, height + 24f),
                    cornerRadius = CornerRadius(72f, 72f)
                )

                // Robot Head Shell
                drawRoundRect(
                    color = faceFillColor,
                    size = Size(width, height),
                    cornerRadius = CornerRadius(64f, 64f)
                )

                // Futuristic Screen Bezel Border
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(borderGlowColor, Color(0xFF1E293B), borderGlowColor)
                    ),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(64f, 64f),
                    style = Stroke(width = 6f)
                )

                // Glass Reflection Overlay
                val reflectionPath = Path().apply {
                    moveTo(width * 0.45f, 10f)
                    lineTo(width - 40f, 10f)
                    quadraticTo(width - 10f, 10f, width - 10f, 40f)
                    lineTo(width - 10f, height * 0.25f)
                    close()
                }
                drawPath(
                    path = reflectionPath,
                    color = Color.White.copy(alpha = 0.04f)
                )

                // Eyes Setup
                val eyeWidth = 48f
                val baseEyeHeight = 72f
                val activeBlink = if (currentExpression == Expression.SMILING) 1.0f else blinkScaleY
                val eyeHeight = baseEyeHeight * activeBlink

                val leftEyeX = width * 0.31f - eyeWidth / 2
                val rightEyeX = width * 0.69f - eyeWidth / 2
                val eyeY = height * 0.38f + (baseEyeHeight - eyeHeight) / 2

                val eyeCornerRadius = if (currentExpression == Expression.SMILING) 36f else 22f

                // Outer Eye Glow
                drawRoundRect(
                    color = mainColor.copy(alpha = 0.35f),
                    topLeft = Offset(leftEyeX - 6f, eyeY - 6f),
                    size = Size(eyeWidth + 12f, eyeHeight + 12f),
                    cornerRadius = CornerRadius(eyeCornerRadius + 6f, eyeCornerRadius + 6f)
                )
                drawRoundRect(
                    color = mainColor.copy(alpha = 0.35f),
                    topLeft = Offset(rightEyeX - 6f, eyeY - 6f),
                    size = Size(eyeWidth + 12f, eyeHeight + 12f),
                    cornerRadius = CornerRadius(eyeCornerRadius + 6f, eyeCornerRadius + 6f)
                )

                // Left Eye Main
                drawRoundRect(
                    color = mainColor,
                    topLeft = Offset(leftEyeX, eyeY),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeCornerRadius, eyeCornerRadius)
                )

                // Right Eye Main
                drawRoundRect(
                    color = mainColor,
                    topLeft = Offset(rightEyeX, eyeY),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeCornerRadius, eyeCornerRadius)
                )

                // Eye Glint Dots
                if (eyeHeight > 20f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 6f,
                        center = Offset(leftEyeX + eyeWidth * 0.7f, eyeY + eyeHeight * 0.28f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 6f,
                        center = Offset(rightEyeX + eyeWidth * 0.7f, eyeY + eyeHeight * 0.28f)
                    )
                }

                // Angry Eyebrows Overlay
                if (currentExpression == Expression.ANGRY) {
                    val browPathLeft = Path().apply {
                        moveTo(leftEyeX - 25f, eyeY - 15f)
                        lineTo(leftEyeX + eyeWidth + 25f, eyeY + 35f)
                        lineTo(leftEyeX + eyeWidth + 25f, eyeY - 20f)
                        close()
                    }
                    val browPathRight = Path().apply {
                        moveTo(rightEyeX - 25f, eyeY + 35f)
                        lineTo(rightEyeX + eyeWidth + 25f, eyeY - 15f)
                        lineTo(rightEyeX + eyeWidth + 25f, eyeY - 20f)
                        close()
                    }
                    drawPath(path = browPathLeft, color = faceFillColor)
                    drawPath(path = browPathRight, color = faceFillColor)
                }

                // Crying Tears
                if (currentExpression == Expression.CRYING) {
                    val tearAlpha = (1f - (tearDropY / 160f)).coerceIn(0f, 1f)
                    drawRoundRect(
                        color = Color(0xFF60A5FA).copy(alpha = tearAlpha),
                        topLeft = Offset(leftEyeX + 12f, eyeY + eyeHeight + tearDropY),
                        size = Size(22f, 34f),
                        cornerRadius = CornerRadius(11f, 11f)
                    )
                    drawRoundRect(
                        color = Color(0xFF60A5FA).copy(alpha = tearAlpha),
                        topLeft = Offset(rightEyeX + 12f, eyeY + eyeHeight + tearDropY),
                        size = Size(22f, 34f),
                        cornerRadius = CornerRadius(11f, 11f)
                    )
                }

                // Draw Mouth
                val mouthY = height * 0.72f
                val mouthWidth = width * 0.42f
                val mouthStartX = (width - mouthWidth) / 2

                val mouthPath = Path()

                when (currentExpression) {
                    Expression.NORMAL -> {
                        mouthPath.moveTo(mouthStartX, mouthY)
                        mouthPath.quadraticTo(
                            width / 2, mouthY + 18f,
                            mouthStartX + mouthWidth, mouthY
                        )
                    }
                    Expression.SMILING -> {
                        mouthPath.moveTo(mouthStartX, mouthY - 10f)
                        mouthPath.quadraticTo(
                            width / 2, mouthY + 65f,
                            mouthStartX + mouthWidth, mouthY - 10f
                        )
                    }
                    Expression.CRYING, Expression.ANGRY -> {
                        mouthPath.moveTo(mouthStartX, mouthY + 22f)
                        mouthPath.quadraticTo(
                            width / 2, mouthY - 12f,
                            mouthStartX + mouthWidth, mouthY + 22f
                        )
                    }
                    Expression.SHIVERING -> {
                        var currentX = mouthStartX
                        mouthPath.moveTo(currentX, mouthY)
                        val step = mouthWidth / 6
                        for (i in 1..6) {
                            val yOffset = if (i % 2 == 0) mouthY + 12f else mouthY - 12f
                            currentX += step
                            mouthPath.lineTo(currentX, yOffset)
                        }
                    }
                }

                // Mouth Glow & Line
                drawPath(
                    path = mouthPath,
                    color = mainColor.copy(alpha = 0.35f),
                    style = Stroke(width = 20f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = mouthPath,
                    color = mainColor,
                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // 2. Foreground Overlay Layout
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Wi-Fi status bar & Left Digital Table Clock
            Column {
                Esp32TopBar(
                    connectionState = esp32Client.connectionState,
                    ipAddress = esp32Client.ipAddress,
                    onOpenConfig = { showConfigDialog = true }
                )

                // Digital Table Clock positioned on the LEFT side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    DigitalTableClock(
                        timeText = clockTimeText,
                        dateText = clockDateText
                    )
                }
            }

            // Bottom Section: Feedback Bar, Sensors, Mic & Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                // Voice Command Feedback Bar
                if (lastVoiceTranscript.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🗣 \"$lastVoiceTranscript\"",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // ESP32 Status & Environment Sensors Row + Voice Command Mic Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill(icon = "⚡", label = "ESP32", text = "$displayBattery%", accentColor = Color(0xFF10B981))
                        StatPill(icon = "🌡", label = "TEMP", text = "${"%.1f".format(displayTemp)}°C", accentColor = Color(0xFFF59E0B))
                        StatPill(icon = "💧", label = "HUMID", text = "${displayHumidity.toInt()}%", accentColor = Color(0xFF3B82F6))
                    }

                    // Voice Mic Assistant Button
                    VoiceMicButton(onClick = onMicClick)
                }

                // Control Expression Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val onSelectExpression = { expr: Expression ->
                        localExpression = expr
                        if (esp32Client.connectionState == ConnectionState.CONNECTED) {
                            esp32Client.sendExpression(expr)
                        }
                    }

                    ExpressionButton("Normal", currentExpression == Expression.NORMAL) { onSelectExpression(Expression.NORMAL) }
                    ExpressionButton("Smile", currentExpression == Expression.SMILING) { onSelectExpression(Expression.SMILING) }
                    ExpressionButton("Cry", currentExpression == Expression.CRYING) { onSelectExpression(Expression.CRYING) }
                    ExpressionButton("Shiver", currentExpression == Expression.SHIVERING) { onSelectExpression(Expression.SHIVERING) }
                    ExpressionButton("Angry", currentExpression == Expression.ANGRY) { onSelectExpression(Expression.ANGRY) }
                }
            }
        }
    }

    // ESP32 IP Configuration Dialog
    if (showConfigDialog) {
        Esp32ConfigDialog(
            currentIp = esp32Client.ipAddress,
            connectionState = esp32Client.connectionState,
            errorMessage = esp32Client.errorMessage,
            onConnect = { ip ->
                esp32Client.connect(ip)
            },
            onConnectUsb = {
                esp32Client.connectUsb(context)
            },
            onAutoDiscover = {
                esp32Client.autoDiscover()
            },
            onDisconnect = {
                esp32Client.disconnect()
            },
            onDismiss = { showConfigDialog = false }
        )
    }

    // Friday Voice Command Options Dialog
    if (showVoiceDialog) {
        FridayVoiceDialog(
            onSelectCommand = { cmd ->
                processCommand(cmd)
            },
            onSpeechRecognition = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    startListeningIntent()
                } else {
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            },
            onDismiss = { showVoiceDialog = false }
        )
    }
}

@Composable
fun DigitalTableClock(
    timeText: String,
    dateText: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B1120).copy(alpha = 0.85f))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF1E293B), Color(0xFF3B82F6))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "DIGITAL CLOCK",
                color = Color(0xFF94A3B8),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Glowing Digital Time
        Text(
            text = timeText.ifEmpty { "12:00:00 AM" },
            color = Color(0xFF00E5FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        // Today's Date
        Text(
            text = dateText.ifEmpty { "MON, 24 OCT 2025" },
            color = Color(0xFF38BDF8),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun VoiceMicButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF2563EB))
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = "🎙", fontSize = 20.sp)
    }
}

@Composable
fun Esp32TopBar(
    connectionState: ConnectionState,
    ipAddress: String,
    onOpenConfig: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF111827))
                .clickable { onOpenConfig() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF10B981)
                ConnectionState.CONNECTING -> Color(0xFFF59E0B)
                ConnectionState.ERROR -> Color(0xFFEF4444)
                ConnectionState.DISCONNECTED -> Color(0xFF6B7280)
            }
            val statusText = when (connectionState) {
                ConnectionState.CONNECTED -> "ESP32 Connected ($ipAddress)"
                ConnectionState.CONNECTING -> "Connecting..."
                ConnectionState.ERROR -> "ESP32 Error"
                ConnectionState.DISCONNECTED -> "ESP32 Disconnected"
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Settings Wi-Fi Button
        IconButton(
            onClick = onOpenConfig,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
        ) {
            Text(text = "📡", fontSize = 16.sp)
        }
    }
}

@Composable
fun Esp32ConfigDialog(
    currentIp: String,
    connectionState: ConnectionState,
    errorMessage: String,
    onConnect: (String) -> Unit,
    onConnectUsb: () -> Unit,
    onAutoDiscover: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var inputIp by remember { mutableStateOf(currentIp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        title = {
            Text(text = "Connect ESP32", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // USB OTG Connect Button
                Button(
                    onClick = {
                        onConnectUsb()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔌 Connect USB OTG Cable", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color(0xFF334155))

                Text(
                    text = "Wi-Fi Presets / Hostnames:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Preset Chips Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = inputIp == "friday-bot.local",
                        onClick = { inputIp = "friday-bot.local" },
                        label = { Text("friday-bot.local", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = inputIp == "192.168.4.1",
                        onClick = { inputIp = "192.168.4.1" },
                        label = { Text("192.168.4.1 (AP)", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = inputIp,
                    onValueChange = { inputIp = it },
                    label = { Text("ESP32 IP Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.Gray
                    )
                )

                // Auto Discover Button
                Button(
                    onClick = {
                        onAutoDiscover()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 Auto-Discover Wi-Fi ESP32", fontSize = 12.sp)
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = "Error: $errorMessage",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(inputIp)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Connect Wi-Fi")
            }
        },
        dismissButton = {
            if (connectionState == ConnectionState.CONNECTED) {
                TextButton(onClick = {
                    onDisconnect()
                    onDismiss()
                }) {
                    Text("Disconnect", color = Color(0xFFEF4444))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    )
}

@Composable
fun StatPill(icon: String, label: String, text: String, accentColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF111827))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
        Column {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = text,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ExpressionButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Brush.horizontalGradient(
                    colors = listOf(Color(0xFF2563EB), Color(0xFF00E5FF))
                ) else Brush.horizontalGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF1E293B))
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFF94A3B8)
        )
    }
}

@Composable
fun FridayVoiceDialog(
    onSelectCommand: (String) -> Unit,
    onSpeechRecognition: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        title = {
            Text(text = "🗣 Ask Friday Assistant", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tap any question to ask Friday:",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                VoiceCommandChip(icon = "🌡", text = "What is the room temperature?") {
                    onSelectCommand("temperature")
                    onDismiss()
                }

                VoiceCommandChip(icon = "💧", text = "What is the room humidity?") {
                    onSelectCommand("humidity")
                    onDismiss()
                }

                VoiceCommandChip(icon = "🕒", text = "What is the current time & date?") {
                    onSelectCommand("time")
                    onDismiss()
                }

                VoiceCommandChip(icon = "⚡", text = "What is the ESP32 battery level?") {
                    onSelectCommand("battery")
                    onDismiss()
                }

                HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Quick Robot Expression Commands:",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                VoiceCommandChip(icon = "😊", text = "Switch to Happy expression") {
                    onSelectCommand("smile")
                    onDismiss()
                }

                VoiceCommandChip(icon = "😠", text = "Switch to Angry expression") {
                    onSelectCommand("angry")
                    onDismiss()
                }

                VoiceCommandChip(icon = "😢", text = "Switch to Sad expression") {
                    onSelectCommand("cry")
                    onDismiss()
                }

                VoiceCommandChip(icon = "🥶", text = "Switch to Shivering expression") {
                    onSelectCommand("shiver")
                    onDismiss()
                }

                VoiceCommandChip(icon = "😐", text = "Reset to Normal expression") {
                    onSelectCommand("normal")
                    onDismiss()
                }

                HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                OutlinedButton(
                    onClick = {
                        onSpeechRecognition()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                ) {
                    Text("🎙 Try Speech Recognition (Mic)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Gray)
            }
        }
    )
}

@Composable
fun VoiceCommandChip(icon: String, text: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
            Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
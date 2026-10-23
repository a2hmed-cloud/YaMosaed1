package com.yamosaed.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yamosaed.assistant.agent.Agent
import com.yamosaed.assistant.agent.Ctx
import com.yamosaed.assistant.agent.WebSearchTool
import com.yamosaed.assistant.agent.defaultTools
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var status by mutableStateOf("جاهز! اسأل عن الطقس أو اضبط منبه أو شغّل موسيقى")
    private var recognizedText by mutableStateOf("اضغط الميكروفون وجرّب: الطقس في الإسماعيلية")
    private var listening by mutableStateOf(false)
    private lateinit var agent: Agent

    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else status = "اسمح باستخدام الميكروفون عشان أسمعك"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val ctx = Ctx(AndroidHost(this, getSharedPreferences("yamosaed", MODE_PRIVATE))) { say(it) }
        agent = Agent(defaultTools(), WebSearchTool(), ctx)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF080D18)) {
                    Column(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF101C35), Color(0xFF080D18))))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("يا مساعد", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("مساعدك الصوتي المصري", color = Color(0xFFB7C5E2))
                        Spacer(Modifier.height(30.dp))
                        Box(
                            Modifier.size(150.dp).background(
                                Brush.radialGradient(listOf(Color(0xFF4F8CFF), Color(0xFF223D83), Color(0xFF14203D))),
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "ميكروفون", tint = Color.White, modifier = Modifier.size(64.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(status, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Color(0xFF182238), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(recognizedText, Modifier.padding(18.dp), color = Color(0xFFD9E4FA), textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
                                else requestAudio.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF477DFF))
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Text(if (listening) "  بسمعك..." else "  اتكلم معايا", fontSize = 17.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("جرّب أمر سريع", color = Color(0xFFB7C5E2), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { command("الطقس في الإسماعيلية") }, modifier = Modifier.weight(1f)) { Text("🌤 الطقس") }
                            OutlinedButton(onClick = { command("الساعة كام") }, modifier = Modifier.weight(1f)) { Text("🕒 الوقت") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { command("التاريخ") }, modifier = Modifier.weight(1f)) { Text("📅 التاريخ") }
                            OutlinedButton(onClick = { command("تايمر 1 دقيقة") }, modifier = Modifier.weight(1f)) { Text("⏱ تايمر دقيقة") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "أوامر: منبه 7 الصبح • شغل عمرو دياب على سبوتيفاي • وقف • التالي • مطاعم قريبة • سجل في يومي اشتري عيش",
                            color = Color(0xFF8493B1), fontSize = 12.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    override fun onInit(code: Int) {
        ttsReady = code == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = tts?.setLanguage(Locale("ar", "EG"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                status = "الصوت العربي غير متاح حاليًا على الجهاز"
            }
        }
    }

    private fun say(message: String) {
        if (message.isBlank()) return
        status = message
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    private fun command(raw: String) {
        say(agent.handle(raw))
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "التعرف على الصوت مش متاح على الجهاز"
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true; status = "اتكلم، أنا سامعك" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; status = "ثانية واحدة..." }
            override fun onError(error: Int) { listening = false; status = "ماقدرتش أسمعك كويس. جرّب تاني." }
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    recognizedText = spoken
                    command(spoken)
                } else status = "ماوصلنيش كلام واضح. جرّب تاني."
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        status = "بجهز الميكروفون..."
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
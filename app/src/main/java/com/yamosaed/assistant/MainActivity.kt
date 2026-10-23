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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var status by mutableStateOf("أنا جاهز، دوس على الميكروفون واتكلم")
    private var recognizedText by mutableStateOf("قول أي جملة بالعربي")
    private var listening by mutableStateOf(false)

    private val requestAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else status = "لازم تسمح باستخدام الميكروفون عشان أسمعك"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080D18)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF101C35), Color(0xFF080D18))
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("يا مساعد", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("مساعدك الصوتي المصري", fontSize = 16.sp, color = Color(0xFFB7C5E2))
                            Spacer(Modifier.height(42.dp))
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF4F8CFF), Color(0xFF223D83), Color(0xFF14203D))
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "ميكروفون",
                                    tint = Color.White,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                            Spacer(Modifier.height(28.dp))
                            Text(status, color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = Color(0xFF182238),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    recognizedText,
                                    modifier = Modifier.padding(18.dp),
                                    color = Color(0xFFD9E4FA),
                                    textAlign = TextAlign.Center,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            this@MainActivity, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) startListening()
                                    else requestAudio.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF477DFF)),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = null)
                                Text(if (listening) "  بسمعك..." else "  اتكلم معايا", fontSize = 17.sp)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "النسخة الأولية: الاستماع يبدأ بالضغط. الاستيقاظ بعبارة «يا مساعد» والذكاء الاصطناعي هييجوا في مراحل لاحقة.",
                                color = Color(0xFF8493B1),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = tts?.setLanguage(Locale("ar", "EG"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                status = "خدمة الصوت العربي مش متاحة على الجهاز حاليًا"
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "التعرف على الصوت مش متاح على الجهاز"
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                status = "اتكلم، أنا سامعك"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                listening = false
                status = "ثانية واحدة..."
            }
            override fun onError(error: Int) {
                listening = false
                status = "ماقدرتش أسمعك كويس. جرّب تاني."
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    recognizedText = text
                    status = "سمعتك، دي نسخة تجريبية ولسه محتاجة توصيل الذكاء الاصطناعي"
                    if (ttsReady) tts?.speak("أنا سمعتك بتقول: $text", TextToSpeech.QUEUE_FLUSH, null, "reply")
                } else {
                    status = "ماوصلنيش كلام واضح. جرّب تاني."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
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

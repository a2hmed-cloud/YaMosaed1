package com.yamosaed.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var status by mutableStateOf("جاهز! اسأل عن الطقس أو اضبط منبه أو شغّل موسيقى")
    private var recognizedText by mutableStateOf("اضغط الميكروفون وجرّب: الطقس في الإسماعيلية")
    private var listening by mutableStateOf(false)
    private var timerRemaining by mutableIntStateOf(0)
    private var timerActive by mutableStateOf(false)

    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else status = "اسمح باستخدام الميكروفون عشان أسمعك"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            var typedCommand by remember { mutableStateOf("") }
            var showAgentSettings by remember { mutableStateOf(false) }
            var agentEndpoint by remember { mutableStateOf(getSharedPreferences("agent_settings", MODE_PRIVATE).getString("endpoint", "https://api.groq.com/openai/v1/chat/completions") ?: "") }
            var agentModel by remember { mutableStateOf(getSharedPreferences("agent_settings", MODE_PRIVATE).getString("model", "llama-3.3-70b-versatile") ?: "") }
            var agentKey by remember { mutableStateOf(getSharedPreferences("agent_settings", MODE_PRIVATE).getString("key", "") ?: "") }
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF080D18)) {
                    LaunchedEffect(timerActive) {
                        while (timerActive && timerRemaining > 0) {
                            delay(1000)
                            timerRemaining = (timerRemaining - 1).coerceAtLeast(0)
                        }
                        if (timerActive && timerRemaining == 0) {
                            timerActive = false
                            say("الوقت خلص! التايمر انتهى.")
                        }
                    }
                    if (timerActive) {
                        Column(
                            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101C35), Color(0xFF080D18)))).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("التايمر شغّال", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(24.dp))
                            Text(String.format(Locale.US, "%02d:%02d", timerRemaining / 60, timerRemaining % 60), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color(0xFF79A7FF))
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(onClick = { timerActive = false; timerRemaining = 0; say("رجعنا للمساعد") }) { Text("إيقاف والعودة للمساعد") }
                        }
                    } else Column(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101C35), Color(0xFF080D18)))).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("يا مساعد", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("مساعدك الصوتي المصري", color = Color(0xFFB7C5E2))
                        Spacer(Modifier.height(30.dp))
                        AssistantEyes(listening = listening)
                        Spacer(Modifier.height(24.dp))
                        Text(status, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Color(0xFF182238), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(recognizedText, Modifier.padding(18.dp), color = Color(0xFFD9E4FA), textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = typedCommand,
                            onValueChange = { typedCommand = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("اكتب أمر أو سؤال") },
                            placeholder = { Text("مثال: الطقس في القاهرة") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF477DFF),
                                unfocusedBorderColor = Color(0xFF52617D),
                                focusedLabelColor = Color(0xFF8DB1FF),
                                unfocusedLabelColor = Color(0xFFB7C5E2),
                                cursorColor = Color.White
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val query = typedCommand.trim()
                                if (query.isNotEmpty()) {
                                    recognizedText = query
                                    typedCommand = ""
                                    command(query)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = typedCommand.isNotBlank()
                        ) { Text("إرسال الأمر كتابةً") }
                        TextButton(onClick = { showAgentSettings = !showAgentSettings }) {
                            Text(if (showAgentSettings) "إخفاء إعدادات عقل الذكاء الاصطناعي" else "⚙ إعداد عقل الذكاء الاصطناعي", color = Color(0xFF9CB9FF))
                        }
                        if (showAgentSettings) {
                            OutlinedTextField(
                                value = agentEndpoint, onValueChange = { agentEndpoint = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("رابط API المتوافق مع OpenAI") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF9CB9FF), unfocusedLabelColor = Color(0xFFB7C5E2))
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = agentModel, onValueChange = { agentModel = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("اسم الموديل") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF9CB9FF), unfocusedLabelColor = Color(0xFFB7C5E2))
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = agentKey, onValueChange = { agentKey = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("مفتاح API (يتخزن على جهازك)") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF9CB9FF), unfocusedLabelColor = Color(0xFFB7C5E2))
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = {
                                getSharedPreferences("agent_settings", MODE_PRIVATE).edit()
                                    .putString("endpoint", agentEndpoint.trim())
                                    .putString("model", agentModel.trim())
                                    .putString("key", agentKey.trim()).apply()
                                say("حفظت إعدادات عقل الذكاء الاصطناعي على جهازك.")
                                showAgentSettings = false
                            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ إعدادات الذكاء الاصطناعي") }
                            Text("تحتاج مفتاحًا من مزوّد API. قد تُحسب تكلفة الاستخدام حسب المزوّد. لا تضع مفتاحًا لا تملك حق استخدامه.", color = Color(0xFF8493B1), fontSize = 11.sp, textAlign = TextAlign.Center)
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
                        Text("أوامر صوتية إضافية: منبه الساعة 7 صباحًا • مطاعم قريبة • مباريات اليوم • سجل في يومي المذاكرة", color = Color(0xFF8493B1), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    override fun onInit(code: Int) {
        ttsReady = code == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = tts?.setLanguage(Locale("ar", "EG"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) status = "الصوت العربي غير متاح حاليًا على الجهاز"
        }
    }

    private fun say(message: String) {
        status = message
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    private fun command(raw: String) {
        val s = raw.trim()
        val l = s.lowercase(Locale("ar", "EG"))
        when {
            l.contains("الساعة كام") || l.contains("الوقت كام") -> say("الساعة دلوقتي " + java.text.SimpleDateFormat("h:mm a", Locale("ar", "EG")).format(java.util.Date()))
            l.contains("التاريخ") || l.contains("تاريخ اليوم") -> say("النهارده " + java.text.SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar", "EG")).format(java.util.Date()))
            l.contains("تايمر") || l.contains("مؤقت") -> {
                val number = Regex("(\\d+)").find(s)?.value?.toLongOrNull()
                if (number == null || number < 1) say("قول المدة، زي تايمر 5 دقايق أو تايمر 30 ثانية.")
                else {
                    val seconds = if (l.contains("دقيقة") || l.contains("دقايق") || l.contains("دقائق")) number * 60 else if (l.contains("ساعة")) number * 3600 else number
                    timerRemaining = seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    timerActive = true
                    val manager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
                    val intent = Intent(this, TimerAlarmReceiver::class.java)
                    val pending = android.app.PendingIntent.getBroadcast(this, 2718, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            say("اسمح بالمنبهات الدقيقة ثم كرر أمر التايمر.")
                        } else {
                            manager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + seconds * 1000L, pending)
                            say("تمام، التايمر بدأ وهيوصلك تنبيه لما يخلص حتى لو خرجت من التطبيق.")
                        }
                    } catch (_: Exception) { say("ماقدرتش أضبط التايمر. راجع صلاحية المنبهات.") }
                }
            }
            l.contains("مطاعم") || l.contains("محلات") || l.contains("أماكن قريبة") || l.contains("صيدليات") || l.contains("كافيهات") -> {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode(s)))); say("فتحت الخرائط للبحث عن الأماكن المطلوبة.") }
                catch (_: Exception) { say("ماقدرتش أفتح الخرائط.") }
            }
            l.contains("إجازات") || l.contains("اجازات") || l.contains("العطلات الرسمية") -> {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("الإجازات الرسمية في مصر " + java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))))); say("فتحت لك نتائج الإجازات الرسمية. راجع مصدرًا رسميًا للتأكيد.") }
                catch (_: Exception) { say("ماقدرتش أفتح البحث.") }
            }
            l.contains("أحداث") || l.contains("فعاليات") || l.contains("ماتش") || l.contains("مباريات") -> {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(s + " مصر اليوم")))); say("فتحت لك بحثًا محدثًا عن " + s) }
                catch (_: Exception) { say("ماقدرتش أفتح البحث.") }
            }
            l.contains("طقس") || l.contains("الجو") || l.contains("درجة الحرارة") -> {
                val city = s.replace("يا مساعد", "").replace("الطقس", "").replace("الجو", "").replace("درجة الحرارة", "").replace("في", "").replace("النهارده", "").replace("اليوم", "").replace("؟", "").trim().ifBlank { "Ismailia" }
                status = "بدور على الطقس في $city..."
                Thread { getWeather(city) }.start()
            }
            l.contains("منبه") || l.contains("فكرني") || l.contains("صحيني") -> alarm(s)
            l.contains("سبوتيفاي") || l.contains("spotify") -> musicSearch(s, true)
            l.contains("تويست ميوزك") || l.contains("twist music") -> musicSearch(s, false)
            l.contains("سجل في يومي") || l.contains("ضيف لمهامي") -> saveTask(s)
            l.contains("اعرض يومي") || l.contains("مهامي") || l.contains("جدولي") -> showTasks()
            else -> askAgent(s)
        }
    }

    private fun askAgent(userMessage: String) {
        val prefs = getSharedPreferences("agent_settings", MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "").orEmpty().trim()
        val model = prefs.getString("model", "").orEmpty().trim()
        val key = prefs.getString("key", "").orEmpty().trim()
        if (endpoint.isBlank() || model.isBlank() || key.isBlank()) {
            say("عشان أجاوبك كذكاء اصطناعي، افتح إعداد عقل الذكاء الاصطناعي وأضف رابط المزوّد واسم الموديل ومفتاح API. أوامر الطقس والمنبه والتايمر شغالة من غيره.")
            return
        }
        status = "بفكر في طلبك..." 
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $key")
                }
                val payload = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.6)
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", "أنت يا مساعد، وكيل مساعد شخصي مصري ودود. أجب بالعربية المصرية بشكل واضح وعملي. كن صريحًا بشأن حدودك، ولا تدّع تنفيذ شيء لم تنفذه. يمكنك شرح أن أوامر الطقس والوقت والتاريخ والتايمر والمنبه والبحث والموسيقى والمهام تنفذها أدوات محلية منفصلة عند طلبها بصيغة واضحة. لا تدّع أنك ترى ملفات المستخدم أو تتحكم في الجهاز إلا إذا كانت هناك أداة فعلية لذلك. إذا احتجت معلومة حديثة لا تملكها، وضّح ذلك."))
                        put(JSONObject().put("role", "user").put("content", userMessage))
                    })
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("HTTP $code: " + JSONObject(responseText).optString("error", responseText).take(180))
                val answer = JSONObject(responseText).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
                runOnUiThread {
                    if (answer.isBlank()) say("الموديل رجّع إجابة فاضية. جرّب تاني.") else say(answer)
                }
            } catch (e: Exception) {
                runOnUiThread { say("ماقدرتش أوصل للذكاء الاصطناعي. راجع رابط API والموديل والمفتاح والإنترنت. التفاصيل: " + (e.message ?: "خطأ اتصال").take(100)) }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun getWeather(city: String) {
        try {
            val normalizedCity = when (city.trim().lowercase(Locale("ar", "EG"))) {
                "الإسماعيلية", "اسماعيلية", "الإسماعيليه" -> "Ismailia"
                "القاهرة" -> "Cairo"
                "الجيزة" -> "Giza"
                "الإسكندرية", "اسكندرية" -> "Alexandria"
                "بورسعيد" -> "Port Said"
                "السويس" -> "Suez"
                else -> city.trim()
            }
            val q = URLEncoder.encode(normalizedCity, "UTF-8")
            val g = URL("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ar&format=json").openConnection() as HttpURLConnection
            g.connectTimeout = 8000
            g.readTimeout = 8000
            val geo = JSONObject(g.inputStream.bufferedReader().use { it.readText() })
            g.disconnect()
            val place = geo.optJSONArray("results")?.optJSONObject(0)
            if (place == null) { runOnUiThread { say("مش لاقي المدينة دي. قول اسم المدينة، زي الطقس في القاهرة.") }; return }
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val name = place.optString("name", city)
            val c = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val current = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            c.disconnect()
            val temp = current.optDouble("temperature_2m").toInt()
            val feels = current.optDouble("apparent_temperature").toInt()
            val humidity = current.optInt("relative_humidity_2m")
            val wind = current.optDouble("wind_speed_10m").toInt()
            val weather = when (current.optInt("weather_code")) {
                0 -> "صحو"; 1, 2 -> "صحو جزئيًا"; 3 -> "غائم"; 45, 48 -> "شبورة"
                in 51..67 -> "رذاذ أو مطر"; in 71..77 -> "ثلج"; in 80..82 -> "زخات مطر"
                in 95..99 -> "عواصف رعدية"; else -> "حالة جوية متغيرة"
            }
            runOnUiThread { say("الطقس في $name دلوقتي $weather. الحرارة $temp درجة والمحسوسة $feels، الرطوبة $humidity بالمية، والرياح $wind كيلومتر في الساعة.") }
        } catch (_: Exception) {
            runOnUiThread { say("مش قادر أوصل لبيانات الطقس دلوقتي. اتأكد من الإنترنت وجرب تاني.") }
        }
    }

    private fun alarm(text: String) {
        val m = Regex("(\\d{1,2})(?::(\\d{1,2}))?").find(text)
        if (m == null) { say("قول الوقت، زي منبه الساعة 7 صباحًا."); return }
        var h = m.groupValues[1].toIntOrNull() ?: 7
        val min = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val l = text.lowercase(Locale("ar", "EG"))
        if ((l.contains("مساء") || l.contains("بالليل")) && h in 1..11) h += 12
        if (l.contains("صباح") && h == 12) h = 0
        if (h !in 0..23 || min !in 0..59) { say("الوقت مش صحيح، جرب تاني."); return }
        try {
            startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, h)
                putExtra(AlarmClock.EXTRA_MINUTES, min)
                putExtra(AlarmClock.EXTRA_MESSAGE, "يا مساعد")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            })
            say("فتحت المنبه على $h و$min. راجع الوقت وأكد الحفظ.")
        } catch (_: Exception) { say("مش قادر أفتح تطبيق الساعة على جهازك.") }
    }

    private fun musicSearch(text: String, spotify: Boolean) {
        val cleaned = text.replace("يا مساعد", "").replace("شغّل", "").replace("شغل", "").replace("شغّللي", "").replace("شغللي", "").replace("أغاني", "").replace("اغاني", "").replace("موسيقى", "").replace("على سبوتيفاي", "").replace("سبوتيفاي", "").replace("تويست ميوزك", "").replace("Twist Music", "").trim()
        val query = cleaned.ifBlank { "music" }
        val uri = if (spotify) Uri.parse("https://open.spotify.com/search/" + Uri.encode(query)) else Uri.parse("https://www.google.com/search?q=" + Uri.encode("Twist Music $query"))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            say(if (spotify) "فتحت بحث سبوتيفاي عن $query. اختار الأغنية وشغلها." else "بدور على $query في Twist Music. افتح التطبيق من النتائج المتاحة.")
        } catch (_: Exception) { say("مش قادر أفتح البحث دلوقتي.") }
    }

    private fun saveTask(text: String) {
        val task = text.replace("يا مساعد", "").replace("سجل في يومي", "").replace("ضيف لمهامي", "").trim()
        if (task.isBlank()) { say("قول المهمة اللي عايز تسجلها."); return }
        val prefs = getSharedPreferences("daily_plan", MODE_PRIVATE)
        val old = prefs.getString("tasks", "").orEmpty()
        prefs.edit().putString("tasks", if (old.isBlank()) "• $task" else "$old\n• $task").apply()
        say("تمام، سجلت في يومك: $task")
    }

    private fun showTasks() {
        val tasks = getSharedPreferences("daily_plan", MODE_PRIVATE).getString("tasks", "").orEmpty()
        say(if (tasks.isBlank()) "لسه مفيش مهام. قول سجل في يومي وبعدها المهمة." else "المهام المسجلة: $tasks")
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status = "التعرف على الصوت مش متاح على الجهاز"; return }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; status = "اتكلم، أنا سامعك" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; status = "ثانية واحدة..." }
            override fun onError(error: Int) { listening = false; status = "ماقدرتش أسمعك كويس. جرّب تاني." }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) { recognizedText = spoken; command(spoken) } else status = "ماوصلنيش كلام واضح. جرّب تاني."
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

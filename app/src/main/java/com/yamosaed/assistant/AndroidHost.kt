package com.yamosaed.assistant

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.yamosaed.assistant.agent.Host
import com.yamosaed.assistant.agent.MusicAction
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

class AndroidHost(private val activity: Activity, private val prefs: SharedPreferences) : Host {
    private val musicPackages = mapOf("spotify" to "com.spotify.music", "soundcloud" to "com.soundcloud.android")
    override fun now(): Calendar = Calendar.getInstance()

    override fun setAlarm(hour: Int, minute: Int, label: String): Boolean = try {
        activity.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        })
        true
    } catch (_: Exception) { false }

    override fun setTimer(seconds: Long, label: String): Boolean {
        val manager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return false
        }
        val pending = PendingIntent.getBroadcast(activity, 2718, Intent(activity, TimerAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + seconds * 1000L, pending)
        return true
    }

    override fun openUrl(url: String): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: Exception) { false }

    override fun weather(city: String, done: (String) -> Unit) {
        Thread { val text = fetchWeather(city); activity.runOnUiThread { done(text) } }.start()
    }

    override fun musicControl(action: MusicAction): Boolean {
        val mgr = activity.getSystemService(MediaSessionManager::class.java)
        val controller = try { mgr.getActiveSessions(ComponentName(activity, NotifListener::class.java)).firstOrNull() }
        catch (_: SecurityException) { null } ?: return false
        val t = controller.transportControls
        when (action) {
            MusicAction.TOGGLE -> if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            MusicAction.NEXT -> t.skipToNext()
            MusicAction.PREV -> t.skipToPrevious()
        }
        return true
    }

    override fun openMusicSearch(app: String, query: String): Boolean {
        val enc = Uri.encode(query)
        val pkg = musicPackages[app]
        val (native, web) = when (app) {
            "spotify" -> "spotify:search:$enc" to "https://open.spotify.com/search/$enc"
            "soundcloud" -> "https://soundcloud.com/search?q=$enc" to "https://soundcloud.com/search?q=$enc"
            else -> "https://www.google.com/search?q=" + Uri.encode("Twist Music $query") to "https://www.google.com/search?q=" + Uri.encode("Twist Music $query")
        }
        if (pkg != null) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(native)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { }
        }
        return openUrl(web)
    }

    override fun armClick(app: String, query: String, done: (Boolean) -> Unit): Boolean {
        val pkg = musicPackages[app] ?: return false
        return SearchClickService.instance?.arm(pkg, query) { ok -> activity.runOnUiThread { done(ok) } } ?: false
    }

    override fun load(key: String): String? = prefs.getString(key, null)
    override fun save(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    private fun getText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun fetchWeather(city: String): String = try {
        val q = URLEncoder.encode(city, "UTF-8")
        val geo = JSONObject(getText("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ar&format=json"))
        val place = geo.optJSONArray("results")?.optJSONObject(0) ?: return "مش لاقي المدينه دي. قول اسم المدينه، زي الطقس في القاهرة."
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val name = place.optString("name", city)
        val current = JSONObject(getText("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto")).getJSONObject("current")
        val weather = when (current.optInt("weather_code")) {
            0 -> "صحو"; 1, 2 -> "صحو جزئيًا"; 3 -> "غائم"; 45, 48 -> "شبورة"
            in 51..67 -> "رذاذ أو مطر"; in 71..77 -> "ثلج"; in 80..82 -> "زخات مطر"
            in 95..99 -> "عواصف رعدية"; else -> "حالة جوية متغيرة"
        }
        "الطقس في $name دلوقتي $weather. الحرارة ${current.optDouble("temperature_2m").toInt()} درجة والمحسوسة ${current.optDouble("apparent_temperature").toInt()}، الرطوبة ${current.optInt("relative_humidity_2m")} بالمية، والرياح ${current.optDouble("wind_speed_10m").toInt()} كيلومتر في الساعة."
    } catch (_: Exception) { "مش قادر أوصل لبيانات الطقس دلوقتي. اتأكد من الإنترنت وجرب تاني." }
}
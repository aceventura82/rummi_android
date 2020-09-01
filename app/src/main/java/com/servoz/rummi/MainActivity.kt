package com.servoz.rummi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity() {

    private var prefs: SharedPreferences? = null
    private var login=false

    override fun attachBaseContext(newBase: Context?) {
        prefs = newBase!!.getSharedPreferences(PREF_FILE, 0)
        super.attachBaseContext(ApplicationLanguageHelper.wrap(newBase,
            prefs!!.getString("language", LANG)!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDefaultNightMode(MODE_NIGHT_YES)
        prefs = getSharedPreferences(PREF_FILE, 0)
        setContentView(R.layout.activity_main)
        login=prefs!!.getString("appKey", "") !== ""
        val urlCheck= URL.replace("HTTPS", "").replace("HTTP", "")+"/joinGame/"
        if(intent.getStringExtra("SETTINGS") == "YES")
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_settings, Bundle())
        else if(intent.getStringExtra("MY_GAMES") == "YES")
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
        else if(intent.getStringExtra("GAME") != null){
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("gameId",this.intent.getStringExtra("GAME"))
            startActivity(intent)
        }
        else if(Intent.ACTION_VIEW == intent.action && intent.data.toString().contains(urlCheck)){
            if(login)
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
            else
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_login, Bundle())
        }
        //start notifications
        prefs!!.edit().putString("check_turn", "ON").apply()
        //set the app theme
        notificationChannels()
    }

    private fun notificationChannels(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val mChannel = NotificationChannel("MAIN", "Default", NotificationManager.IMPORTANCE_DEFAULT)
            mChannel.description = "For main Notifications"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }
}

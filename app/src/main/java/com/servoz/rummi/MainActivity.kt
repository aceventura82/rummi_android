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
import androidx.media.session.MediaButtonReceiver.handleIntent
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.tools.*
import com.servoz.rummi.ui.home.HomeFragmentDirections
import com.servoz.rummi.ui.home.MyGamesFragmentDirections
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.doAsync

class MainActivity : AppCompatActivity() {

    private var prefs: SharedPreferences? = null
    private var processId=0
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
        processId= (1..99999999).random()
        checkTurn(processId)
        //set the app theme
        notificationChannels()
    }

    override fun onPause() {
        processId= (1..99999999).random()
        checkTurn(processId)
        super.onPause()
    }

    override fun onResume() {
        processId= (1..99999999).random()
        checkTurn(processId)
        super.onResume()
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

    //check turn for notifications
    private fun checkTurn(pId:Int){
        doAsync {
            while(processId==pId && login) {
                if (prefs!!.getString("check_turn", "") == "ON") {
                    //check remote if my turn only if app in background and game started and not ended
                    FetchData(arrayListOf(), nav_host_fragment).updateData("myTurn", "", cache = false) { result ->
                        if (result != "" && result != "--") {
                            val res = result.split(",")
                            //send notification
                            for (gameId in res)
                                checkGameName(gameId)
                        }else
                            processId=-1
                    }
                }
                Thread.sleep(30000)
            }
        }
    }

    private fun checkGameName(gameId:String){
        if(prefs!!.getString("MUTE_NOTIFICATIONS", "ON") =="OFF")
            return
        //notify if user not in current game
        if(prefs!!.getString("current_game","")!=gameId)
        FetchData(arrayListOf(),nav_host_fragment).updateData("gameInfo", "",cache = false,addParams = hashMapOf("gameId" to gameId)) { result ->
            val data = MyTools().stringListToJSON(result)
            //notify if game is started
            if (data.count() > 0 && data[0]["started"]=="1"){
                Notifications().create(this@MainActivity, getString(R.string.your_turn), getString(R.string.notif_turn_game, data[0]["name"]), gameId)
                //pause notification until user click on notification or open app
                prefs!!.edit().putString("check_turn", "").apply()
            }
        }
    }
}

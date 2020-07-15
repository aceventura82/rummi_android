package com.servoz.rummi

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.tools.LANG
import com.servoz.rummi.tools.PREF_FILE
import com.servoz.rummi.ui.home.HomeFragmentDirections
import kotlinx.android.synthetic.main.content_main.*


class GameActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context?) {
        val prefs = newBase!!.getSharedPreferences(PREF_FILE, 0)
        super.attachBaseContext(ApplicationLanguageHelper.wrap(newBase,
            prefs!!.getString("language", LANG)!!))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREF_FILE, 0)
        // check if full screen
        if(prefs!!.getString("FULLSCREEN","ON")=="ON")
        window!!.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if(prefs.getString("LANDSCAPE","ON")=="ON")
            requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_game)
        //start notifications if user came from notification
        getSharedPreferences(PREF_FILE, 0).edit().putString("check_turn", "ON").apply()
        if(savedInstanceState == null)
            NavHostFragment.findNavController(nav_host_fragment).navigate(HomeFragmentDirections.actionGlobalToGame(Integer.parseInt(intent.getStringExtra("gameId")!!)))
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("MY_GAMES", "YES")
        finish()
        startActivity(intent)
        super.onBackPressed()
    }
}
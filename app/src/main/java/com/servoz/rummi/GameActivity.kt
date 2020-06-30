package com.servoz.rummi

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.tools.PREF_FILE
import com.servoz.rummi.ui.home.HomeFragmentDirections
import kotlinx.android.synthetic.main.content_main.*


class GameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window!!.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_game)
        //start notifications if user came from notification
        getSharedPreferences(PREF_FILE, 0).edit().putString("check_turn", "ON").apply()
        if(savedInstanceState == null)
            NavHostFragment.findNavController(nav_host_fragment).navigate(HomeFragmentDirections.actionGlobalToGame(Integer.parseInt(intent.getStringExtra("gameId")!!)))
    }
}
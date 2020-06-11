package com.servoz.rummi

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.servoz.rummi.ui.game.GameFragment


class GameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window!!.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_game)

        val gameFragment = GameFragment.newInstance(intent.getStringExtra("gameId")!!)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.general_preference, gameFragment, intent.getStringExtra("gameId")!!)
            .commit()
    }
}
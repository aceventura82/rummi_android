package com.servoz.rummi.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.servoz.rummi.MainActivity
import com.servoz.rummi.R
import com.servoz.rummi.tools.*

import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment: Fragment() {
    
    private var prefs: SharedPreferences? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = requireActivity().getSharedPreferences(PREF_FILE, 0)
        setInitialValues()
        listeners()
    }

    @SuppressLint("SetTextI18n")
    private fun setInitialValues() {
        if(prefs!!.getString("LANDSCAPE", "ON") =="OFF")
            settings_landscape_menu.text="${getString(R.string.orientation)}: ${getString(R.string.portrait)}"
        else
            settings_landscape_menu.text="${getString(R.string.orientation)}: ${getString(R.string.landscape)}"
        if(prefs!!.getString("FULLSCREEN", "ON") =="OFF")
            settings_full_screen_menu.text="${getString(R.string.full_screen)}: ${getString(R.string.off)}"
        else
            settings_full_screen_menu.text="${getString(R.string.full_screen)}: ${getString(R.string.on)}"
        if(prefs!!.getString("MUTE_AUDIOS", "ON") =="OFF")
            settings_mute_audios_menu.text="${getString(R.string.audios)}: ${getString(R.string.off)}"
        else
            settings_mute_audios_menu.text="${getString(R.string.audios)}: ${getString(R.string.on)}"
        if(prefs!!.getString("MUTE_NOTIFICATIONS", "ON") =="OFF")
            settings_mute_notifications_menu.text="${getString(R.string.notifications)}: ${getString(R.string.off)}"
        else
            settings_mute_notifications_menu.text="${getString(R.string.notifications)}: ${getString(R.string.on)}"
        if(prefs!!.getString("language", "") =="es")
            settings_lang_menu.text="${getString(R.string.language)}: ${getString(R.string.espanol)}"
        else
            settings_lang_menu.text="${getString(R.string.language)}: ${getString(R.string.english)}"
    }
    
    private fun listeners() {
        settings_lang_menu.setOnClickListener{
            changeLanguage()
        }
        settings_full_screen_menu.setOnClickListener{
            changeFullScreen()
        }
        settings_landscape_menu.setOnClickListener{
            changeOrientation()
        }
        settings_mute_notifications_menu.setOnClickListener{
            muteNotifications()
        }
        settings_mute_audios_menu.setOnClickListener{
            muteAudios()
        }
    }

    private fun changeLanguage(){
        if(prefs!!.getString("language", "") == "es")
            prefs!!.edit().putString("language", "en").apply()
        else
            prefs!!.edit().putString("language", "es").apply()
        requireActivity().recreate()
    }

    @SuppressLint("SetTextI18n")
    private fun changeFullScreen() {
        val current = if(prefs!!.getString("FULLSCREEN", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("FULLSCREEN", current).apply()
        if(current == "OFF")
            settings_full_screen_menu.text="${getString(R.string.full_screen)}: ${getString(R.string.off)}"
        else
            settings_full_screen_menu.text="${getString(R.string.full_screen)}: ${getString(R.string.on)}"
    }

    @SuppressLint("SetTextI18n")
    private fun changeOrientation() {
        val current = if(prefs!!.getString("LANDSCAPE", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("LANDSCAPE", current).apply()

        if(current =="OFF")
            settings_landscape_menu.text="${getString(R.string.orientation)}: ${getString(R.string.portrait)}"
        else
            settings_landscape_menu.text="${getString(R.string.orientation)}: ${getString(R.string.landscape)}"
    }

    @SuppressLint("SetTextI18n")
    private fun muteNotifications() {
        val current = if(prefs!!.getString("MUTE_NOTIFICATIONS", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("MUTE_NOTIFICATIONS", current).apply()
        if(current == "OFF")
            settings_mute_notifications_menu.text="${getString(R.string.notifications)}: ${getString(R.string.off)}"
        else
            settings_mute_notifications_menu.text="${getString(R.string.notifications)}: ${getString(R.string.on)}"

        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("SETTINGS", "YES")
        requireActivity().finish()
        startActivity(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun muteAudios() {
        val current = if(prefs!!.getString("MUTE_AUDIOS", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("MUTE_AUDIOS", current).apply()
        if(current == "OFF")
            settings_mute_audios_menu.text="${getString(R.string.audios)}: ${getString(R.string.off)}"
        else
            settings_mute_audios_menu.text="${getString(R.string.audios)}: ${getString(R.string.on)}"
    }
}
package com.servoz.rummi.ui.home


import android.content.*
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.servoz.rummi.BuildConfig
import com.servoz.rummi.MainActivity
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.version_changes.view.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import org.json.JSONObject

import kotlin.collections.ArrayList


class HomeFragment : Fragment() {

    private var prefs: SharedPreferences? = null
    private var login=false

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        prefs = requireActivity().getSharedPreferences(PREF_FILE, 0)
        login=prefs!!.getString("appKey", "") !== ""
        userInfo(login)
        hideShowMenuItems(login)
        listeners()
        checkUpdate()
        text_version.text= BuildConfig.VERSION_NAME
        if(prefs!!.getString("VERSION_INFO", "")!=BuildConfig.VERSION_NAME){
            doAsync {
                Thread.sleep(2000)
                uiThread { displayVersionInfo() }
            }
        }
    }

    //put the user data in the left drawer
    private fun userInfo(login:Boolean){
        if(!login)
            return
        home_player_info.isVisible=true
        val userData= try{
            JSONObject(prefs!!.getString("userInfo", "")!!)
        }catch (ex: JSONException){
            return
        }
        // set name, nickname or email
        home_profile_info.text = when {
            userData.getString("nickname") != "" -> {
                userData.getString("nickname")
            }
            userData.getString("name") != "" -> {
                userData.getString("name") + " " + if (userData.getString("lastname") != "") userData.getString(
                    "lastname"
                ) else ""
            }
            else -> { FetchData(ArrayList(), nav_host_fragment).getUser() }
        }
        GlideApp.with(this).load("${URL}/static/playerAvatars/${userData.getString("userId_id")}${userData.getString("extension")}")
            .signature(ObjectKey(prefs!!.getString("imageSignature", "")!!))
            .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_circle_black_24dp)).into(home_profile_pic)
    }

    private fun listeners() {

        btn_home_new_game.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_add_game, Bundle())
        }
        btn_home_my_games.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
        }
        btn_home_rules.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_rules, Bundle())
        }
        btn_home_search_game.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_search_game, Bundle())
        }
        btn_home_login.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_login, Bundle())
        }
        btn_home_register.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_register, Bundle())
        }
        btn_home_settings.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_settings, Bundle())
        }
        home_player_info.setOnClickListener {
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_profile, Bundle())
        }
        btn_home_logout.setOnClickListener{
            logout()
        }
    }

    private fun hideShowMenuItems(showMenu:Boolean){
        btn_home_my_games.isVisible = showMenu
        btn_home_new_game.isVisible = showMenu
        btn_home_search_game.isVisible = showMenu
        btn_home_logout.isVisible = showMenu
        btn_home_register.isVisible = !showMenu
        btn_home_login.isVisible = !showMenu
    }

    private fun logout() {
        prefs!!.edit().clear().apply()
        MyTools().toast(requireContext(), getString(R.string.logoutOK))
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        requireActivity().deleteDatabase(DB_NAME)
        requireActivity().finish()
        startActivity(intent)
    }

    //check app update
    private fun checkUpdate(){
        doAsync {
            FetchData(arrayListOf(), nav_host_fragment).updateData("checkVersion", "", cache = false) { result ->
                if (result != "OK") {
                    textUpdate.isVisible=true
                    textUpdateLink.isVisible=true
                    textUpdateLink.setOnClickListener {
                        val openURL = Intent(Intent.ACTION_VIEW)
                        openURL.data = Uri.parse("https://github.com/aceventura82/rummi_android/raw/master/app/release/app-release.apk")
                        startActivity(openURL)
                    }
                    textUpdate.text=getString(R.string.update, result)
                }
            }
        }
    }

    private fun displayVersionInfo(){
        val windowView=LayoutInflater.from(context).inflate(R.layout.version_changes, linearLayout, false)
        val gameWindow:PopupWindow
        if(Build.VERSION.SDK_INT<=22){
            gameWindow= PopupWindow(windowView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gameWindow.isOutsideTouchable=false
            gameWindow.elevation = 5.0f
        }else
            gameWindow= PopupWindow(requireContext())
        gameWindow.contentView=windowView
        gameWindow.isFocusable=true
        gameWindow.showAtLocation(linearLayout, Gravity.CENTER, 0 ,0)
        windowView.gameVersionLayout.setOnClickListener{
            gameWindow.dismiss()
        }
        windowView.text_version_info.text=getString(R.string.version_info)
        prefs!!.edit().putString("VERSION_INFO", BuildConfig.VERSION_NAME).apply()
    }

}

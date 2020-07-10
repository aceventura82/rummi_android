package com.servoz.rummi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.google.android.material.navigation.NavigationView
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.nav_header_main.view.*
import org.jetbrains.anko.doAsync
import org.json.JSONException
import org.json.JSONObject
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private var prefs: SharedPreferences? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private var processId=0
    private var login=false

    override fun attachBaseContext(newBase: Context?) {
        prefs = newBase!!.getSharedPreferences(PREF_FILE, 0)
        super.attachBaseContext(ApplicationLanguageHelper.wrap(newBase,
            prefs!!.getString("language", LANG)!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREF_FILE, 0)
        setContentView(R.layout.activity_main)
        //start notifications
        prefs!!.edit().putString("check_turn", "ON").apply()
        processId= (1..99999999).random()
        login=prefs!!.getString("appKey", "") !== ""
        checkTurn(processId)
        //set the app theme
        when(prefs!!.getString("THEME","System")){
            "Dark" -> {
                setDefaultNightMode(MODE_NIGHT_YES)
            }
            "Light" -> {
                setDefaultNightMode(MODE_NIGHT_NO)
            }
        }
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, 0, 0
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)
        notificationChannels()
        hideShowMenuItems(login)
        drawerData()
        checkUpdate()
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

    //put the user data in the left drawer
    private fun drawerData(){

        val userData= try{
            JSONObject(prefs!!.getString("userInfo", "")!!)
        }catch (ex:JSONException){
            return
        }
        // set name, nickname or email
        navView.getHeaderView(0).navUserInfo.text = when {
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
    }

    private fun hideShowMenuItems(showMenu:Boolean){
        navView.menu.findItem(R.id.nav_menu_new_game).isVisible = showMenu
        navView.menu.findItem(R.id.nav_menu_search_game).isVisible = showMenu
        navView.menu.findItem(R.id.nav_menu_login).isVisible = !showMenu
        navView.menu.findItem(R.id.nav_menu_register).isVisible = !showMenu
        navView.menu.findItem(R.id.nav_menu_logout).isVisible = showMenu
        if(showMenu)
            navView.getHeaderView(0).setOnClickListener {
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_profile, Bundle())
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        navView.menu.findItem(R.id.nav_menu_my_games).title = getString(if(showMenu)R.string.menu_my_games else R.string.menu_home)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        if(prefs!!.getString("LANDSCAPE", "ON") =="OFF")
            menu.findItem(R.id.settings_landscape_menu).title=getString(R.string.portrait)
        if(prefs!!.getString("FULLSCREEN", "ON") =="OFF")
            menu.findItem(R.id.settings_full_screen_menu).title="${getString(R.string.full_screen)} ${getString(R.string.off)}"
        else
            menu.findItem(R.id.settings_full_screen_menu).title="${getString(R.string.full_screen)} ${getString(R.string.on)}"
        if(prefs!!.getString("MUTE_AUDIOS", "ON") =="OFF")
            menu.findItem(R.id.settings_mute_audios_menu).title="${getString(R.string.audios)} ${getString(R.string.off)}"
        else
            menu.findItem(R.id.settings_mute_audios_menu).title="${getString(R.string.audios)} ${getString(R.string.on)}"
        if(prefs!!.getString("MUTE_NOTIFICATIONS", "ON") =="OFF")
            menu.findItem(R.id.settings_mute_notifications_menu).title="${getString(R.string.notifications)} ${getString(R.string.off)}"
        else
            menu.findItem(R.id.settings_mute_notifications_menu).title="${getString(R.string.notifications)} ${getString(R.string.on)}"
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    // three dots right menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings_change_theme_dark -> {
                updateTheme("Dark")
                setDefaultNightMode(MODE_NIGHT_YES)
                true
            }
            R.id.settings_change_theme_light -> {
                updateTheme("Light")
                setDefaultNightMode(MODE_NIGHT_NO)
                true
            }
            R.id.settings_change_theme_sys -> {
                updateTheme("System")
                setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
                true
            }
            R.id.settings_es_menu -> {
                changeLanguage("es")
                true
            }
            R.id.settings_en_menu -> {
                changeLanguage("en")
                true
            }
            R.id.settings_full_screen_menu -> {
                changeFullScreen()
                true
            }
            R.id.settings_landscape_menu -> {
                changeOrientation()
                true
            }
            R.id.settings_mute_notifications_menu -> {
                muteNotifications()
                true
            }
            R.id.settings_mute_audios_menu -> {
                muteAudios()
                true
            }
            R.id.settings_exit_menu -> {
                exitProcess(0)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    //left Navigation Menu
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_menu_new_game -> {
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_add_game, Bundle())
            }
            R.id.nav_menu_my_games -> {
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_home, Bundle())
            }
            R.id.nav_menu_rules -> {
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_rules, Bundle())
            }
            R.id.nav_menu_search_game -> {
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_search_game, Bundle())
            }
            R.id.nav_menu_login -> {
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_login, Bundle())
            }
            R.id.nav_menu_register -> {
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_register, Bundle())
            }
            R.id.nav_menu_logout -> {
                logout()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun updateTheme(themeStr: String) {
        prefs!!.edit().putString("THEME", themeStr).apply()
        Toast.makeText(this, "$themeStr ${getString(R.string.themeAct)}", Toast.LENGTH_SHORT).show()
    }

    private fun changeOrientation() {
        val current = if(prefs!!.getString("LANDSCAPE", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("LANDSCAPE", current).apply()
        if(current == "OFF")
            toolbar.menu.findItem(R.id.settings_landscape_menu).title=getString(R.string.portrait)
        else
            toolbar.menu.findItem(R.id.settings_landscape_menu).title=getString(R.string.landscape)
        Toast.makeText(this, "${getString(R.string.landscape)} ${getString(if(current =="OFF") R.string.off else R.string.on)}", Toast.LENGTH_SHORT).show()
    }

    private fun changeFullScreen() {
        val current = if(prefs!!.getString("FULLSCREEN", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("FULLSCREEN", current).apply()
        if(current == "OFF")
            toolbar.menu.findItem(R.id.settings_full_screen_menu).title="${getString(R.string.full_screen)} ${getString(R.string.off)}"
        else
            toolbar.menu.findItem(R.id.settings_full_screen_menu).title="${getString(R.string.full_screen)} ${getString(R.string.on)}"
        Toast.makeText(this, "${getString(R.string.full_screen)} ${getString(if(current =="OFF") R.string.off else R.string.on)}", Toast.LENGTH_SHORT).show()
    }

    private fun muteNotifications() {
        val current = if(prefs!!.getString("MUTE_NOTIFICATIONS", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("MUTE_NOTIFICATIONS", current).apply()
        if(current == "OFF"){
            toolbar.menu.findItem(R.id.settings_mute_notifications_menu).title="${getString(R.string.notifications)} ${getString(R.string.off)}"
            processId=-1
        }else{
            toolbar.menu.findItem(R.id.settings_mute_notifications_menu).title="${getString(R.string.notifications)} ${getString(R.string.on)}"
            processId= (1..99999999).random()
            checkTurn(processId)
        }
        Toast.makeText(this, "${getString(R.string.notifications)} ${getString(if(current =="OFF") R.string.off else R.string.on)}", Toast.LENGTH_SHORT).show()
    }

    private fun muteAudios() {
        val current = if(prefs!!.getString("MUTE_AUDIOS", "ON") =="OFF") "ON" else "OFF"
        prefs!!.edit().putString("MUTE_AUDIOS", current).apply()
        if(current == "OFF")
            toolbar.menu.findItem(R.id.settings_mute_audios_menu).title="${getString(R.string.audios)} ${getString(R.string.off)}"
        else
            toolbar.menu.findItem(R.id.settings_mute_audios_menu).title="${getString(R.string.audios)} ${getString(R.string.on)}"
        Toast.makeText(this, "${getString(R.string.audios)} ${getString(if(current =="OFF") R.string.off else R.string.on)}", Toast.LENGTH_SHORT).show()
    }

    private fun changeLanguage(lang:String){
        prefs!!.edit().putString("language", lang).apply()
        recreate()
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

    private fun logout() {
        prefs!!.edit().clear().apply()
        Toast.makeText(this, getString(R.string.logoutOK), Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        deleteDatabase(DB_NAME)
        finish()
        startActivity(intent)
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

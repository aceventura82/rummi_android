package com.servoz.rummi.ui.login

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import com.tiper.MaterialSpinner
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.app_bar_main.view.*
import kotlinx.android.synthetic.main.change_pass.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_profile.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import org.json.JSONObject

class ProfileFragment : Fragment() {

    private var prefs: SharedPreferences? = null
    private var genderOpc=""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().toolbar.searchV.isVisible=false
        requireActivity().toolbar.buttonChangePass.isVisible=true
        super.onViewCreated(view, savedInstanceState)
        //get user info
        prefs = requireContext().getSharedPreferences(PREF_FILE, 0)
        val userInfo= getData()
        doAsync {
            updateData(this@ProfileFragment)
        }
        putInfo(userInfo)
        buttonSaveProfile.setOnClickListener{
            updProfile(userInfo["userId_id"].toString())
        }
        profile_cancel_button.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_home, Bundle())
        }
        swipe_containerUser.setOnRefreshListener {
            doAsync {
                updateData(this@ProfileFragment)
                uiThread {
                    Toast.makeText(context, getString(R.string.updating), Toast.LENGTH_SHORT).show()
                    swipe_containerUser.isRefreshing = false
                }
            }
        }

        requireActivity().toolbar.buttonChangePass.setOnClickListener {
            val passWindow=PopupWindow(context)
            val windowView=LayoutInflater.from(context).inflate(R.layout.change_pass, gridLayoutProfile, false)
            passWindow.contentView=windowView
            passWindow.isFocusable=true
            passWindow.showAsDropDown(requireActivity().toolbar.buttonChangePass)
            windowView.password_cancel_button.setOnClickListener{
                passWindow.dismiss()
            }
            windowView.password_change_button.setOnClickListener {
                changePass(windowView,passWindow)
            }
        }
    }

    // get cached data and display it
    private fun getData(): JSONObject{
        return try{
            MyTools().stringListToJSON(prefs!!.getString("userInfo","")!!)[0]
        }catch(e: JSONException){
            Toast.makeText(context, requireContext().getString(R.string.ServerError), Toast.LENGTH_SHORT).show()
            JSONObject()
        }
    }

    fun updateData(fragment:Fragment){
        //get user Info
        FetchData(arrayListOf(),fragment).updateData("userInfo", "",cache = false) {
            result ->
            try {
                prefs = fragment.requireContext().getSharedPreferences(PREF_FILE, 0)
                prefs!!.edit().putString("userInfo", JSONObject(result).toString()).apply()
                // check if new data after last response
                if(result.trim('\n')!=prefs!!.getString("userInfo","{}")!!.trim('\n'))
                    NavHostFragment.findNavController(fragment).navigate(R.id.action_global_nav_profile)
            } catch (e: JSONException) {
                println("MAIN ACT DEBUG: Json Error $result")
                Toast.makeText(fragment.requireContext(), fragment.requireContext().getString(R.string.ServerError), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun putInfo(userInfo:JSONObject){
        //Profile Info
        editProfileName.setText(userInfo["name"].toString())
        editProfileLastName.setText(userInfo["lastname"].toString())
        editProfileNickName.setText(userInfo["nickname"].toString())
        editProfileEmail.setText(FetchData(ArrayList(),nav_host_fragment).getUser())
        editProfileCountry.setText(userInfo["country"].toString())
        editProfileCity.setText(userInfo["city"].toString())
        editProfileBirthDate.setText(userInfo["birthDate"].toString())
        //Gender spinner
        val titles= arrayListOf(getString(R.string.gender),getString(R.string.male),getString(R.string.female))
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, titles)

        editProfileGender.adapter = arrayAdapter
        val values= arrayListOf("", "M", "F")
        editProfileGender.selection=when(userInfo["gender"]) {
            "M" -> 1
            "F" -> 2
            else -> 0
        }

        editProfileGender.onItemSelectedListener = object : MaterialSpinner.OnItemSelectedListener {
            override fun onItemSelected(parent: MaterialSpinner, view: View?, position: Int, id: Long) {
                genderOpc=values[position]
            }
            override fun onNothingSelected(parent: MaterialSpinner) {}
        }
    }
    
    private fun updProfile(userId:String){
        loadingSaveProfile.isVisible=true
        val params= hashMapOf(
            "name" to editProfileName.text.toString(),"lastname" to editProfileLastName.text.toString(),
            "nickname" to editProfileNickName.text.toString(), "email" to editProfileEmail.text.toString(),
            "country" to editProfileCountry.text.toString(), "city" to editProfileCity.text.toString(),
            "birthDate" to editProfileBirthDate.text.toString(), "extension" to ".jpg",
            "gender" to genderOpc, "userId" to userId
            
        )
        FetchData(arrayListOf(),this).updateData("editProfile", "",cache = false, addParams = params){
                result ->
            Toast.makeText(context,result, Toast.LENGTH_SHORT).show()
            loadingSaveProfile.isVisible=false
            updateData(this@ProfileFragment)
        }
    }
    
    private fun changePass(windowView: View,passWindow: PopupWindow){
        loadingSaveProfile.isVisible=true
        FetchData(arrayListOf(),this).updateData("editPass", "",cache = false,
            addParams = hashMapOf("old_password" to windowView.editPass.text.toString(),
                "new_password1" to windowView.editPass1.text.toString(), "new_password2" to windowView.editPass2.text.toString())) {
                result ->
            val res = result.split("|")
            if(res.count()==2){
                Toast.makeText(context, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                passWindow.dismiss()
                updateLogin(FetchData(ArrayList(),this).getUser(), windowView.editPass1.text.toString())
            }else{
                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
            }
            loadingSaveProfile.isVisible=false
        }
    }

    private fun updateLogin(user:String, pass:String){
        //try to login
        FetchData(arrayListOf(),this).updateData("login", "",cache = false, addParams = hashMapOf("usernameUser" to user, "password" to pass)){
                result ->
            //if result start with OK|, login was successful
            if(result.length>5 && result.substring(0,3)=="OK|"){
                //save the email and hash
                prefs!!.edit().putString("appKey", user+"|"+result.substring(3)).apply()
                Toast.makeText(requireContext(), requireContext().getString(R.string.login_ok), Toast.LENGTH_SHORT).show()
            }else{
                //println("DEBUG:Login Fail $result")
                Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
                prefs!!.edit().putString("appKey", "").apply()
            }
        }
    }
}

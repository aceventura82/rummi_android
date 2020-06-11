package com.servoz.rummi.ui.login

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.MainActivity
import com.servoz.rummi.R
import com.servoz.rummi.tools.FetchData
import com.servoz.rummi.tools.PREF_FILE
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.app_bar_main.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_login.*
import org.jetbrains.anko.doAsync
import java.lang.Thread.sleep

class LoginFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().toolbar.searchV.isVisible=false
        requireActivity().toolbar.buttonChangePass.isVisible=false
        super.onViewCreated(view, savedInstanceState)
        login_next_button.setOnClickListener {
            login(editText_email.text.toString(), editText_password.text.toString())
        }
        login_cancel_button.setOnClickListener {
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_home, Bundle())
        }
    }

    private fun login(user:String, pass:String){
        loadingLogin.isVisible=true
        val prefs: SharedPreferences = requireContext().getSharedPreferences(PREF_FILE, 0)
        //try to login
        FetchData(arrayListOf(),this).updateData("login", "",cache = false, addParams = hashMapOf("usernameUser" to user, "password" to pass)){
            result ->
            //if result start with OK|, login was successful
            if(result.length>5 && result.substring(0,3)=="OK|"){
                //save the email and hash
                prefs.edit().putString("appKey", user+"|"+result.substring(3)).apply()
                Toast.makeText(requireContext(), requireContext().getString(R.string.login_ok), Toast.LENGTH_SHORT).show()
                ProfileFragment().updateData(this)
                doAsync {
                    //wait to get userInfo
                    while(prefs.getString("userInfo","")==""){
                        sleep(300)
                    }
                    requireActivity().finish()
                    val intent = Intent(requireContext(), MainActivity::class.java)
                    startActivity(intent)
                    loadingLogin.isVisible=false
                }
            }else{
                //println("DEBUG:Login Fail $result")
                Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
                prefs.edit().putString("appKey", "").apply()
            }
        }
    }
}

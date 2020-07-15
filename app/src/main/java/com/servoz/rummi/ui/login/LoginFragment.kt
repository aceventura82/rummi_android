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
import com.servoz.rummi.tools.MyTools
import com.servoz.rummi.tools.PREF_FILE
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
        super.onViewCreated(view, savedInstanceState)
        login_next_button.setOnClickListener {
            login(editText_email.text.toString(), editText_password.text.toString())
        }
        login_cancel_button.setOnClickListener {
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
        }
        login_cancel_reset.setOnClickListener {
            login_reset_button.text=getString(R.string.forgot_password)
            editText_email_rL.isVisible=false
            editText_email_r.setText("")
            editText_code.setText("")
            editText_codeL.isVisible=false
            editText_password1L.isVisible=false
            editText_password1.setText("")
            editText_password2.setText("")
            editText_password2L.isVisible=false
            login_cancel_reset.isVisible=false
        }
        login_reset_button.setOnClickListener {
            when(login_reset_button.text){
                getString(R.string.forgot_password)->{
                    login_reset_button.text=getString(R.string.send_code)
                    editText_email_rL.isVisible=true
                    login_cancel_reset.isVisible=true
                }
                getString(R.string.send_code)->{
                    loadingLogin.isVisible=true
                    FetchData(arrayListOf(),this).updateData("sendEmailPass", "",cache = false, addParams = hashMapOf("usernameUser" to editText_email_r.text.toString())) {
                        result ->
                        var msg =result
                        if(result.split("|").count() == 2){
                            editText_email_rL.isVisible=false
                            login_reset_button.text=getString(R.string.reset_password)
                            editText_codeL.isVisible=true
                            editText_password1L.isVisible=true
                            editText_password2L.isVisible=true
                            msg = result.split("|")[1]
                        }
                        MyTools().toast(requireContext(),msg)
                        loadingLogin.isVisible=false
                    }
                }
                getString(R.string.reset_password)->{
                    loadingLogin.isVisible=true
                    FetchData(arrayListOf(),this).updateData("updPasswordCode", "",cache = false,
                        addParams = hashMapOf("usernameUser" to editText_email_r.text.toString(), "new_password1" to editText_password1.text.toString(),
                        "new_password2" to editText_password2.text.toString(), "code" to editText_code.text.toString())) {
                        result ->
                        var msg =result
                        if(result.split("|").count() == 2){
                            login_reset_button.text=getString(R.string.forgot_password)
                            editText_email_rL.isVisible=false
                            editText_codeL.isVisible=false
                            editText_password1L.isVisible=false
                            editText_password2L.isVisible=false
                            login_cancel_reset.isVisible=true
                            msg = result.split("|")[1]
                        }
                        MyTools().toast(requireContext(),msg)
                        loadingLogin.isVisible=false
                    }
                }
            }
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
                }
            }else{
                Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
                prefs.edit().putString("appKey", "").apply()
            }
            loadingLogin.isVisible=false
        }
    }
}

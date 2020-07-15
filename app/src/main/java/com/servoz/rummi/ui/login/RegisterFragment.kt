package com.servoz.rummi.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_register.*

class RegisterFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        register_next_button.setOnClickListener {
            register()
        }
        register_cancel_button.setOnClickListener {
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
        }
        register_resend_button.setOnClickListener {
            register_next_button.text = getString(R.string.register)
            register()
        }
    }

    private fun register(){
        loadingSaveProfile.isVisible=true
        val params=hashMapOf(
            "email" to editText_emailRegister.text.toString(),
            "passwordEmail" to editText_passwordRegister.text.toString(),
            "passwordEmail2" to editText_passwordRegister2.text.toString()
        )
        if(register_next_button.text == getString(R.string.register))
            params["sendEmailBT"] = "1"
        else {
            params["valEmailBT"] = "1"
            params["PINEmail"] = editText_pin.text.toString()
        }
        //try to register
        FetchData(arrayListOf(),this).updateData("register", "",cache = false, addParams = params){
                result ->
            var msg = result
            val res = result.split("|")
            //if res size =2 register and button =register, Code sent OK
            if(res.count()==2 && register_next_button.text == getString(R.string.register)){
                msg = res[1]
                editText_pin.isVisible=true
                register_next_button.text = getString(R.string.confirm)
            }else if(res.count()==2 && register_next_button.text == getString(R.string.confirm)){
                //if res size =2 register and button =confirm, register OK
                msg = res[1]
                editText_pin.isVisible=false
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_login, Bundle())
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            loadingSaveProfile.isVisible=false
        }
    }
}

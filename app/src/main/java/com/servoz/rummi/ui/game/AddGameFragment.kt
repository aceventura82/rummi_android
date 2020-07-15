package com.servoz.rummi.ui.game

import android.net.Uri.encode
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_edit_game.*

class AddGameFragment: Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadingEditGame.isVisible =false
        buttonSaveEditGame.setOnClickListener { addGame() }
    }

    private fun addGame(){
        loadingEditGame.isVisible=true
        val fd=(if(checkBoxFD1.isChecked)"1" else "0")+
            (if(checkBoxFD2.isChecked)"1" else "0")+
            (if(checkBoxFD3.isChecked)"1" else "0")+
            (if(checkBoxFD4.isChecked)"1" else "0")+
            (if(checkBoxFD5.isChecked)"1" else "0")+
            (if(checkBoxFD6.isChecked)"1" else "0")
        val params=hashMapOf(
            "private" to "1", "fullDraw" to fd,
            "name" to encode(editGameName.text.toString()), "speed" to "2", "maxPlayers" to "5"
        )
        FetchData(arrayListOf(),this).updateData("addGame", "",cache = false, addParams = params) {
            result ->
            var msg = result
            val res = result.split("|")
            if(res.count()==2){
                msg = res[1]
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            loadingEditGame.isVisible=false
        }
    }
}
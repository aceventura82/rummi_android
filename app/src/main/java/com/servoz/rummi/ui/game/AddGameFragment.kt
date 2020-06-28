package com.servoz.rummi.ui.game

import android.content.Context
import android.net.Uri.encode
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.android.volley.Request
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import com.tiper.MaterialSpinner
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.app_bar_main.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_edit_game.*
import java.lang.Thread.sleep

class AddGameFragment: Fragment() {

    private var speedOpc=5
    private var maxPlayers=4

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().toolbar.searchV.isVisible=false
        requireActivity().toolbar.buttonChangePass.isVisible=false
        //Speed spinner
        gameSpeed.adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, arrayListOf(1, 2, 3, 4, 5))
        gameSpeed.selection=4
        gameSpeed.onItemSelectedListener = object : MaterialSpinner.OnItemSelectedListener {
            override fun onItemSelected(parent: MaterialSpinner, view: View?, position: Int, id: Long) {
                speedOpc=position+1
            }
            override fun onNothingSelected(parent: MaterialSpinner) {}
        }
        //Max Players spinner
        gameMaxPlayers.adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, arrayListOf(2, 3, 4, 5))
        gameMaxPlayers.selection=3

        gameMaxPlayers.onItemSelectedListener = object : MaterialSpinner.OnItemSelectedListener {
            override fun onItemSelected(parent: MaterialSpinner, view: View?, position: Int, id: Long) {
                maxPlayers=position+2
            }
            override fun onNothingSelected(parent: MaterialSpinner) {}
        }

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
            "private" to if(checkBoxPrivate.isChecked) "1" else "0", "fullDraw" to fd,
            "name" to encode(editGameName.text.toString()), "speed" to speedOpc.toString(), "maxPlayers" to maxPlayers.toString()
        )
        FetchData(arrayListOf(),this).updateData("addGame", "",cache = false, addParams = params) {
            result ->
            var msg = result
            val res = result.split("|")
            if(res.count()==2){
                msg = res[1]
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_home, Bundle())
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            loadingEditGame.isVisible=false
        }
    }
}
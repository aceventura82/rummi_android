package com.servoz.rummi.ui.game

import android.content.Intent
import android.net.Uri.decode
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
import org.json.JSONObject

class EditGameFragment: Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dbHandler = Db(requireContext(), null)
        val gameData = dbHandler.getData("`game`","`id`=${arguments?.getInt("gameId")}")[0]
        textGameCodeInfo.text=getString(R.string.gameCodeInfo, gameData[8])
        editGameName.setText(decode(gameData[1]))
        checkBoxFD1.isChecked = gameData[5][0] == '1'
        checkBoxFD2.isChecked = gameData[5][1] == '1'
        checkBoxFD3.isChecked = gameData[5][2] == '1'
        checkBoxFD4.isChecked = gameData[5][3] == '1'
        checkBoxFD5.isChecked = gameData[5][4] == '1'
        checkBoxFD6.isChecked = gameData[5][5] == '1'

        checkAdmin(gameData[12])
        //readOnly
        editGameNameR.text = decode(gameData[1])

        loadingEditGame.isVisible =false
        edit_game_share_button.setOnClickListener {
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(
                Intent.EXTRA_TEXT,getString(R.string.share_desc, gameData[8],"$URL/joinGame/${gameData[8]}/")
            )
            sendIntent.type = "text/plain"
            startActivity(sendIntent)
        }

        buttonSaveEditGame.setOnClickListener { editGame(gameData) }
        edit_game_delete_button.setOnClickListener { editGame(gameData, true) }
    }

    private fun checkAdmin(userId:String):Boolean{
        val userInfo = JSONObject(requireContext().getSharedPreferences(PREF_FILE, 0).getString("userInfo","")!!)
        if(userId==userInfo["userId_id"])
            return true
        edit_game_delete_button.isVisible=false
        buttonSaveEditGame.isVisible=false
        edit_game_cancel_button.isVisible=false
        editGameName.isVisible=false
        checkBoxFD1.isClickable=false
        checkBoxFD2.isClickable=false
        checkBoxFD3.isClickable=false
        checkBoxFD4.isClickable=false
        checkBoxFD5.isClickable=false
        checkBoxFD6.isClickable=false

        editGameNameR.isVisible=true
        editGameNameRL.isVisible=true
        return false
    }

    private fun editGame(gameData:ArrayList<String>, delete:Boolean=false){
        loadingEditGame.isVisible=true
        val oper:String
        val params = if(delete){
            oper="deleteGame"
            hashMapOf("gameId" to gameData[0])
        }else{
            oper="editGame"
            val fd=if(checkBoxFD1.isChecked)"1" else "0"+
                    (if(checkBoxFD2.isChecked)"1" else "0")+
                    (if(checkBoxFD3.isChecked)"1" else "0")+
                    (if(checkBoxFD4.isChecked)"1" else "0")+
                    (if(checkBoxFD5.isChecked)"1" else "0")+
                    (if(checkBoxFD6.isChecked)"1" else "0")
            hashMapOf("gameId" to gameData[0], "private" to "1",
                "fullDraw" to fd, "name" to encode(editGameName.text.toString()), "speed" to "5", "maxPlayers" to "5"
            )
        }
        FetchData(arrayListOf(),this).updateData(oper, "",cache = false, addParams = params) {
                result ->
            val res = result.split("|")
            val msg = if(res.count()==2)
                res[1]
            else
                result
            if(delete && res.count()==2){
                val dbHandler = Db(requireContext(), null)
                dbHandler.deleteWhere("game", "`id`=${gameData[0]}")
                if(delete && res[0]=="1")
                    NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            loadingEditGame.isVisible=false

        }
    }
}
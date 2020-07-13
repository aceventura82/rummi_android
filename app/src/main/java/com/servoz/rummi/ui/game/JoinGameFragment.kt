package com.servoz.rummi.ui.game

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.R
import com.servoz.rummi.tools.Db
import com.servoz.rummi.tools.FetchData
import com.servoz.rummi.tools.MyTools
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_join_game.*
import org.json.JSONException
import org.json.JSONObject


class JoinGameFragment: Fragment() {

    private var playerPos="1"

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_join_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val code = arguments?.getString("code", "")!!
        getGameInfo(code)
        join_game_button.setOnClickListener { joinGame(code) }
    }

    private fun getGameInfo(code:String){
        FetchData(arrayListOf(),this).updateData("gameInfoCode", "",
            cache = false, addParams = hashMapOf( "code" to code, "pos" to playerPos)) { result ->
            val gameData:JSONObject
            gameData=try{
                MyTools().stringListToJSON(result)[0]
            } catch (e: JSONException) {
                e.printStackTrace()
                Toast.makeText(context, getString(R.string.wrong_link), Toast.LENGTH_SHORT).show()
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_home, Bundle())
                loadingJoinGame.isVisible=false
                JSONObject()
            }
            val dbHandler=Db(requireContext(),null)
            textJoinGameName.text=gameData["name"].toString()
            val inGame=try {
                dbHandler.getData("`game`","`id`=${gameData["id"]}")[0]
                true
            }catch (ex:Exception){ false}
            textJoinGameP1.text=if(gameData["private"].toString()=="True")getString(R.string.privateStr) else getString(R.string.publicStr)
            textJoinGameP2.text=getString(R.string.number_players, gameData["maxPlayers"].toString())
            var fd=""
            for ((c,set) in gameData["fullDraw"].toString().withIndex())
                if(set=='1')
                    fd+="${c+1},"
            textJoinGameP3.text=getString(R.string.full_draw_list, fd.trim(','))
            textJoinGameP4.text=when(gameData["started"]){
                "0" ->getString(R.string.open)
                "1" ->getString(R.string.started)
                else ->getString(R.string.ended)
            }
            if(inGame){
                join_game_button.text=getString(R.string.already_in_game)
                join_game_button.isEnabled=false
            }
            if(gameData["started"]!="0"){
                join_game_button.isVisible=false
            }
            loadingJoinGame.isVisible=false
            getPositions(gameData["playersPos"].toString())

        }
    }

    private fun getPositions(posStr:String){
        val posList=posStr.split((","))
        setSeatColor(posList)
        playerPOs1.setOnClickListener{ setListener(posList,0) }
        playerPOs2.setOnClickListener{ setListener(posList,1) }
        playerPOs3.setOnClickListener{ setListener(posList,2)}
        playerPOs4.setOnClickListener{ setListener(posList,3)}
        playerPOs5.setOnClickListener{ setListener(posList,4)}
    }

    private fun setListener(pos:List<String>, posN:Int){
        setSeatColor(pos, posN)
        if( pos[posN] != "")
            Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show()
        else{
            playerPos=posN.toString()
            Toast.makeText(context, R.string.position_selected, Toast.LENGTH_SHORT).show()
            setSeatColor(pos, posN)
        }
    }

    private fun setSeatColor(pos:List<String>, posN:Int=-1){
        //refresh all seats
        for(c in 0 until 5){
            updateChair(c, if(pos[c] == "")Color.BLACK else Color.RED)
        }
        //set color of selected seat
        if(posN!=-1){
            updateChair(posN, Color.GRAY)
        }
    }

    private fun updateChair(posN:Int, colorId:Int){
        val ids = arrayListOf(R.drawable.ic_filter_1_black_24dp,R.drawable.ic_filter_2_black_24dp,
            R.drawable.ic_filter_3_black_24dp,R.drawable.ic_filter_4_black_24dp,
            R.drawable.ic_filter_5_black_24dp)
        val id=ids[posN]
        val unwrappedDrawable = AppCompatResources.getDrawable(requireContext(), id)
        val wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable!!)
        DrawableCompat.setTint(wrappedDrawable, colorId)
        val images = arrayListOf(playerPOs1,playerPOs2,playerPOs3,playerPOs4,playerPOs5)
        images[posN].setImageResource(id)
    }

    private fun joinGame(code:String){
        loadingJoinGame.isVisible=true
        FetchData(arrayListOf(),this).updateData("joinGame", "",
            cache = false, addParams = hashMapOf("code" to code, "pos" to playerPos)) {
            result ->
            var msg = result
            val res = result.split("|")
            if(res.count()==2){
                msg = res[1]
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_home, Bundle())
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            loadingJoinGame.isVisible=false
        }
    }

}
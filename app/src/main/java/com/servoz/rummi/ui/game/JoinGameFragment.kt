package com.servoz.rummi.ui.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.request.RequestOptions
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
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
            cache = false, addParams = hashMapOf( "code" to code.trim(), "pos" to playerPos)) { result ->
            val gameData:JSONObject
            gameData=try{
                MyTools().stringListToJSON(result)[0]
            } catch (e: JSONException) {
                e.printStackTrace()
                Toast.makeText(context, getString(R.string.wrong_link), Toast.LENGTH_SHORT).show()
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
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
            getPositions(gameData["playersPos"].toString().split(","),
                gameData["players_names"].toString().split(","),
                gameData["players_ext"].toString().split(","))

        }
    }

    private fun getPositions(ids:List<String>, names:List<String>, extensions:List<String>){
        setSeat(ids, names, extensions)
        playerPOs1.setOnClickListener{ setListener(ids, names, extensions,0) }
        playerPOs2.setOnClickListener{ setListener(ids, names, extensions,1) }
        playerPOs3.setOnClickListener{ setListener(ids, names, extensions,2)}
        playerPOs4.setOnClickListener{ setListener(ids, names, extensions,3)}
        playerPOs5.setOnClickListener{ setListener(ids, names, extensions,4)}
    }

    private fun setListener(ids:List<String>, names:List<String>, extensions:List<String>, posN:Int){
        setSeat(ids, names, extensions, posN)
        if( names[posN] != "")
            MyTools().toast(requireContext(), getString(R.string.not_available ,names[posN]))
        else{
            playerPos=posN.toString()
            MyTools().toast(requireContext(), getString(R.string.position_selected))
            setSeat(ids, names, extensions, posN)
        }
    }

    private fun setSeat(ids:List<String>, names:List<String>, extensions:List<String>, posN:Int=-1){
        //refresh all seats
        for(c in 0 until 5){
            updateChair(c, ids[c], names[c], extensions[c])
        }
        //set color of selected seat
        if(posN!=-1){
            val prefs = requireContext().getSharedPreferences(PREF_FILE, 0)
            updateChair(posN,
                JSONObject(prefs.getString("userInfo","")!!)["userId_id"].toString(),
                getString(R.string.me),
                JSONObject(prefs.getString("userInfo","")!!)["extension"].toString()
            )
        }
    }

    private fun updateChair(posN:Int, userId:String, name:String, userExt:String){
        val images = arrayListOf(playerPOs1,playerPOs2,playerPOs3,playerPOs4,playerPOs5)
        val labels = arrayListOf(playerPOs1T,playerPOs2T,playerPOs3T,playerPOs4T,playerPOs5T)
        if(userId == ""){
            labels[posN].text = ""
            images[posN].setImageResource(R.drawable.ic_account_box_white_80dp)
        }else{
            labels[posN].text = name
            GlideApp.with(requireContext()).load("$URL/static/playerAvatars/${userId}${userExt}")
                .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_box_black_80dp)).into(images[posN])
        }
    }

    private fun joinGame(code:String){
        loadingJoinGame.isVisible=true
        FetchData(arrayListOf(),this).updateData("joinGame", "",
            cache = false, addParams = hashMapOf("code" to code.trim(), "pos" to playerPos)) {
            result ->
            var msg = result
            val res = result.split("|")
            if(res.count()==2){
                msg = res[1]
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            loadingJoinGame.isVisible=false
        }
    }

}
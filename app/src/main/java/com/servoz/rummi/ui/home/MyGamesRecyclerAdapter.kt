package com.servoz.rummi.ui.home

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri.decode
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import com.servoz.rummi.GameActivity
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.fragment_my_games.*
import kotlinx.android.synthetic.main.item_my_games_layout.view.*

class MyGamesRecyclerAdapter(private val dataList: MutableList<SearchGames>, private val userId:String) : DynamicSearchAdapter<SearchGames>(dataList) {

    private lateinit var fragment: Fragment
    //this method is returning the view for each item in the list
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_my_games_layout, parent, false)
        fragment=parent.findFragment()
        return ViewHolder(v, userId)
    }

    //this method is binding the data on the list
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        ViewHolder(holder.itemView, userId).bindItems(dataList[position].data,fragment)
    }

    //this method is giving the size of the list
    override fun getItemCount(): Int {
        return dataList.size
    }

    //the class holding the list view
    class ViewHolder(itemView: View, private val userId: String) : RecyclerView.ViewHolder(itemView) {

        fun bindItems(data: ArrayList<String>, fragment: Fragment) {
            //set info
            itemView.textItemMyGameName.text = decode(data[1])
            //change the icon if vew only
            if(data[12] != userId)
                itemView.buttonItemMyGamesEdit.setImageResource(R.drawable.ic_baseline_remove_red_eye_24)
            //listener for the setting button
            itemView.buttonItemMyGamesEdit.setOnClickListener {
                NavHostFragment.findNavController(fragment).navigate(MyGamesFragmentDirections.actionMyGamesToEditGame(Integer.parseInt(data[0])))
            }
            //listener to go to the game
            itemView.gridTitleStandings.setOnClickListener{
                //NavHostFragment.findNavController(fragment).navigate(HomeFragmentDirections.actionGlobalToGame(Integer.parseInt(data[0])))
                val intent = Intent(fragment.requireContext(), GameActivity::class.java)
                intent.putExtra("gameId",data[0])
                fragment.requireActivity().startActivity(intent)
            }
            //listener leave game
            itemView.buttonLeaveGame.setOnClickListener{
                //if game is not started try to delete it, else hide from app
                if(data[4]=="0")
                    leaveGame(fragment, Integer.parseInt(data[0]), fragment.loadingMyGames)
                else
                    hideGame(fragment, Integer.parseInt(data[0]))
            }
            //check if is player turn
            when{
                data[13].split(",")[Integer.parseInt(data[14])]==userId && data[4]=="1" ->
                    setColor(fragment, R.color.game_turn)
                data[4]=="2" ->setColor(fragment, R.color.game_ended)
            }
        }

        private fun setColor(fragment: Fragment, color:Int){
            if(Build.VERSION.SDK_INT<=22)
                itemView.gridTitleStandings.setBackgroundColor(fragment.requireContext().resources.getColor(color))
            else
                itemView.gridTitleStandings.setBackgroundColor(fragment.requireContext().getColor(color))
        }

        private fun leaveGame(fragment: Fragment,gameId:Int, loadingMyGames: View){
            loadingMyGames.isVisible=true
            val dialogBuilder = AlertDialog.Builder(fragment.requireContext())
            dialogBuilder.setMessage(fragment.requireContext().getString(R.string.message_leave_game))
                // if the dialog is cancelable
                .setCancelable(true)
                // positive button text and action
                .setPositiveButton(fragment.requireContext().getString(R.string.leave)) { _, _ ->
                    FetchData(arrayListOf(),fragment).updateData("leaveGame", "",cache = false, addParams = hashMapOf("gameId" to gameId.toString())) {
                        result ->
                        val res = result.split("|")
                        val msg=if(res.count()==2){
                            val dbHandler = Db(fragment.requireContext(), null)
                            dbHandler.deleteWhere("game", "`id`=$gameId")
                            fragment.requireContext().getSharedPreferences(PREF_FILE, 0).edit().remove("CARDS$gameId").apply()
                            NavHostFragment.findNavController(fragment).navigate(R.id.action_global_nav_my_games, Bundle())
                            res[1]
                        }
                        else
                            result
                        Toast.makeText(fragment.requireContext(), msg, Toast.LENGTH_SHORT).show()
                        loadingMyGames.isVisible=false
                    }
                }
                // negative button text and action
                .setNegativeButton(fragment.requireContext().getString(R.string.cancel)) { dialog, _ ->
                    dialog.cancel()
                    loadingMyGames.isVisible=false
                }
            val alert = dialogBuilder.create()
            alert.setTitle(fragment.requireContext().getString(R.string.leave_game))
            alert.show()
        }

        private fun hideGame(fragment: Fragment,gameId:Int){
            val dialogBuilder = AlertDialog.Builder(fragment.requireContext())
            dialogBuilder.setMessage(fragment.requireContext().getString(R.string.message_hide_game))
                // if the dialog is cancelable
                .setCancelable(true)
                // positive button text and action
                .setPositiveButton(fragment.requireContext().getString(R.string.hide)) { _, _ ->
                    FetchData(arrayListOf(),fragment).updateData("hideGame", "",cache = false, addParams = hashMapOf("gameId" to gameId.toString())) { result ->
                        val res = result.split("|")
                        if(res. count() ==2){
                            fragment.requireContext().getSharedPreferences(PREF_FILE, 0).edit().remove("CARDS$gameId").apply()
                            NavHostFragment.findNavController(fragment).navigate(R.id.action_global_nav_my_games, Bundle())
                        }
                        else
                            MyTools().toast(fragment.requireContext(), result)
                    }
                }
                // negative button text and action
                .setNegativeButton(fragment.requireContext().getString(R.string.cancel)) { dialog, _ ->
                    dialog.cancel()
                }
            val alert = dialogBuilder.create()
            alert.setTitle(fragment.requireContext().getString(R.string.leave_game))
            alert.show()
        }

    }
}

class SearchGames(val data: ArrayList<String>) : DynamicSearchAdapter.Searchable {

    override fun getSearchCriteria(): String {
        return if(data.count()>2) data[1] else ""
    }
}

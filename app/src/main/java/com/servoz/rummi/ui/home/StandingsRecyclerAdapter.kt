package com.servoz.rummi.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.RequestOptions
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.item_standings_layout.view.*
import org.json.JSONObject

class StandingsRecyclerAdapter(private val dataList: MutableList<SearchPlayer>) : DynamicSearchAdapter<SearchPlayer>(dataList) {

    private lateinit var fragment: Fragment
    //this method is returning the view for each item in the list
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_standings_layout, parent, false)
        fragment=parent.findFragment()
        return ViewHolder(v)
    }

    //this method is binding the data on the list
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        ViewHolder(holder.itemView).bindItems(dataList[position].data,fragment)
    }

    //this method is giving the size of the list
    override fun getItemCount(): Int {
        return dataList.size
    }

    //the class holding the list view
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindItems(data: JSONObject, fragment: Fragment) {
            //set info
            GlideApp.with(fragment.requireContext()).load("${URL}/static/playerAvatars/${data.getString("userId")}${data.getString("extension")}")
                .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_circle_black_24dp)).into(itemView.StandingsImagePlayer)
            itemView.StandingsNamePlayer.text=data.getString("name")
            itemView.standings_won.text=data.getString("lost")
            itemView.standings_lost.text=data.getString("won")
            itemView.standings_both.text=data.getString("both")

        }
    }
}

class SearchPlayer(val data: JSONObject) : DynamicSearchAdapter.Searchable {

    override fun getSearchCriteria(): String {
        return ""
    }
}

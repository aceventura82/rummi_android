package com.servoz.rummi.ui.home


import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.request.RequestOptions
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.fragment_standings.*
import org.json.JSONException
import org.json.JSONObject
import kotlin.collections.ArrayList


class StandingsFragment : Fragment() {

    private var prefs: SharedPreferences? = null
    private lateinit var searchAdapter: StandingsRecyclerAdapter
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_standings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerViewStandings.layoutManager = GridLayoutManager(context,1)
        prefs = requireContext().getSharedPreferences(PREF_FILE, 0)
        val userInfo =JSONObject(prefs!!.getString("userInfo","")!!)
        GlideApp.with(requireContext()).load("${URL}/static/playerAvatars/${userInfo["userId_id"]}${userInfo["extension"]}")
            .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_circle_black_24dp)).into(StandingsImageMe)
        updateData()
    }

    private fun updateData(){
        //get info
        FetchData(arrayListOf(),this).updateData("myTable", "",cache = false) {
            result ->
            val remoteData:ArrayList<JSONObject>
            //try to read data
            try{

                remoteData=MyTools().stringListToJSON(result)
                val sortedList = remoteData.sortedWith(compareBy {
                    it.getString("lost")
                })
                val objSearch = mutableListOf<SearchPlayer>()
                for (game in sortedList.reversed()) {
                    objSearch.add(SearchPlayer((game)))
                }
                searchAdapter = StandingsRecyclerAdapter(objSearch)
                recyclerViewStandings.adapter = searchAdapter
                loadingStandings.isVisible=false
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            /*{'won': 9, 'lost': 3, 'both': 3, 'name': 'papa'}
            {'won': 13, 'lost': 0, 'both': 6, 'name': 'Oz'}
            {'won': 2, 'lost': 0, 'both': 0, 'name': ['oz25']}
            {'won': 5, 'lost': 1, 'both': 2, 'name': 'ViTo'}
            {'won': 7, 'lost': 2, 'both': 2, 'name': 'Adielita'}*/

        }
    }
}

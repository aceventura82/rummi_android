package com.servoz.rummi.ui.home


import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import com.servoz.rummi.ui.login.ProfileFragment
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.app_bar_main.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_home.loadingMyGames
import kotlinx.android.synthetic.main.fragment_home.recyclerViewMyGames
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONObject
import kotlin.collections.ArrayList


class HomeFragment : Fragment(),androidx.appcompat.widget.SearchView.OnQueryTextListener {

    private var prefs: SharedPreferences? = null
    private lateinit var searchAdapter: MyGamesRecyclerAdapter
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // if intent with URL stop processing, as will not show this fragment
        if(handleIntent(requireActivity().intent))
            return
        super.onViewCreated(view, savedInstanceState)
        recyclerViewMyGames.layoutManager = GridLayoutManager(context,1)
        try {
            requireActivity().toolbar.searchV.isVisible = true
            requireActivity().toolbar.buttonChangePass.isVisible = false
        }catch (ex:Exception){}
        prefs = requireContext().getSharedPreferences(PREF_FILE, 0)
        val login = prefs!!.getString("appKey", "") !== ""

        //get cached data, update data and populate the search options in the background
        val data = getData(login)
        cleanData()
        doAsync {
            //check if new data
            updateData(login, data)
            val objSearch = mutableListOf<SearchGames>()
            for (game in data) {
                objSearch.add(SearchGames((game)))
            }
            searchAdapter = MyGamesRecyclerAdapter(objSearch, JSONObject(prefs!!.getString("userInfo","")!!)["userId_id"].toString())
            uiThread {
                try{
                    recyclerViewMyGames.adapter = searchAdapter
                    requireActivity().toolbar.searchV.setOnQueryTextListener(this@HomeFragment)
                    loadingMyGames.isVisible = false
                }catch (ex:Exception){}
            }
        }
        swipe_containerHome.setOnRefreshListener {
            updateData(login, data)
            swipe_containerHome.isRefreshing = false
        }
        loadingMyGames.isVisible=false
    }

    // intent to Join a Game by URL
    private fun handleIntent(intent: Intent):Boolean{
        val appLinkAction = intent.action
        val appLinkData: Uri? = intent.data
        val urlCheck= URL.replace("HTTPS", "").replace("HTTP", "")+"/joinGame/"
        if (Intent.ACTION_VIEW == appLinkAction &&
            appLinkData.toString().contains(urlCheck)) {
            val code = appLinkData.toString().substringAfterLast(urlCheck, "").trim('/')
            NavHostFragment.findNavController(this).navigate(HomeFragmentDirections.actionGlobalNavJoinGame(code))
            intent.data = null
            return true
        }
        return false
    }

    // get cached data and display it
    private fun getData(login:Boolean): ArrayList<ArrayList<String>>{
        if(!login)
            return ArrayList()
        ProfileFragment().updateData(this@HomeFragment)
        return FetchData(arrayListOf("id", "name", "date", "private", "started", "fullDraw", "speed", "maxPlayers",
            "code", "current_set", "current_stack", "current_discarded", "userId_id",
            "playersPos", "currentPlayerPos"), this@HomeFragment).cacheRepo("OK", "game", "`started` ASC, `date` DESC")
    }

    private fun updateData(login:Boolean, cachedData:ArrayList<ArrayList<String>>){
        if(!login)
            return
            //get my games
        val fetchGame=FetchData(arrayListOf("id", "name", "date", "private", "started", "fullDraw", "speed", "maxPlayers",
            "code", "current_set", "current_stack", "current_discarded", "userId_id",
            "playersPos", "currentPlayerPos"), this)
        fetchGame.updateData("viewMyGames", "game","`started` ASC, `date` DESC"){
            if(cachedData!=fetchGame.cacheRepo("OK", "game", "`started` ASC, `date` DESC"))
                NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_home, Bundle())
        }
    }

    private fun cleanData(){
        val dbHandler=Db(requireContext(), null)
        dbHandler.deleteWhere("game")
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        search(query)
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        search(newText)
        return true
    }

    private fun search(s: String?) {
        searchAdapter.search(s) {
            Toast.makeText(context, getString(R.string.notFound), Toast.LENGTH_SHORT).show()
        }
    }
}

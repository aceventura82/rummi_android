package com.servoz.rummi.ui.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_search_game.*
import org.json.JSONException

class SearchGameFragment: Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadingSearchGame.isVisible =false
        search_game_button.setOnClickListener { findGame(editTextSearchCode.text.toString()) }
    }

    private fun findGame(code:String){
        loadingSearchGame.isVisible=true
        FetchData(arrayListOf(),this).updateData("gameInfoCode", "",cache = false, addParams = hashMapOf("code" to code.trim())) {
            result ->
            try{
                MyTools().stringListToJSON(result)[0]
                NavHostFragment.findNavController(nav_host_fragment).navigate(SearchGameFragmentDirections.actionGlobalNavJoinGame(code))
            } catch (e: JSONException) {
                MyTools().toast(requireContext(), getString(R.string.notFound))
                e.printStackTrace()
                editTextSearchCode.setText("")
            }
            loadingSearchGame.isVisible=false
        }
    }

}
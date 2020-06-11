package com.servoz.rummi.ui.game

import android.content.Context
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
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.app_bar_main.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_find_game.*
import org.json.JSONException

class SearchGameFragment: Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_find_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().toolbar.searchV.isVisible=false
        requireActivity().toolbar.buttonChangePass.isVisible=false
        loadingSearchGame.isVisible =false
        search_game_button.setOnClickListener { findGame(editTextSearchCode.text.toString()) }
    }

    private fun findGame(code:String){
        loadingSearchGame.isVisible=true
        FetchData(arrayListOf(),this).updateData("gameInfoCode", "",cache = false, addParams = hashMapOf("code" to code)) {
            result ->
            try{
                MyTools().stringListToJSON(result)[0]
                NavHostFragment.findNavController(nav_host_fragment).navigate(SearchGameFragmentDirections.actionGlobalNavJoinGame(code))
            } catch (e: JSONException) {
                Toast.makeText(context, getString(R.string.notFound), Toast.LENGTH_SHORT).show()
                editTextSearchCode.setText("")
            }
            loadingSearchGame.isVisible=false
        }
    }

}
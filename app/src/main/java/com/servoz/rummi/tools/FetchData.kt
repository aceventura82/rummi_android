package com.servoz.rummi.tools

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.servoz.rummi.BuildConfig
import org.json.JSONException
import org.json.JSONObject
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class FetchData(private var fields: ArrayList<String>, private val fragment: Fragment) {

    fun updateData(key:String, table:String, sort:String="", addParams:HashMap<String,String> = hashMapOf(),
                   cache:Boolean=true, where:String="", code: (result:String)->Unit = {}){
        val params = hashMapOf(
            "apiKey" to MyTools().genKey(fragment, key == "login"), "usernameUser" to getUser(),
            "oper" to key, "version" to BuildConfig.VERSION_NAME,
            "lang" to fragment.requireContext().getSharedPreferences(PREF_FILE, 0)!!
                .getString("lang", LANG)!!
        )
        // ad additional params if exist
        if (addParams.count() > 0) {
            params.putAll(addParams)
        }
        MyTools().makeRequest(API_URL, params,
            Request.Method.POST, fragment.requireContext(),
            object : VolleyCallback {
                override fun onSuccessResponse(result: String) {
                    //call cache if cache=true
                    val valAct =
                        if (cache) cacheRepo(result, table, sort, where) else ArrayList()
                    // run code if defined
                    if (code != {} && (valAct.size > 0 || !cache))
                        try{
                            code(result)
                        }catch (ex:IllegalStateException){/*in case the user leave the fragment before the answer*/}
                }

                override fun onErrorResponse(context: Context, result: String) {
                    println("DEBUG:ERROR remote response $key:$result")
                    super.onErrorResponse(context, result)
                }
            }
        )
    }

    fun cacheRepo(data: String, table:String, sort:String="", where:String=""): ArrayList<ArrayList<String>>{
        try{
            val dbHandler = Db(fragment.requireContext(), null)
            //Update DB if new Data
            if(data!="OK") {
                val remoteData:ArrayList<JSONObject>
                //try to read data
                try{
                    remoteData=MyTools().stringListToJSON(data)
                } catch (e: JSONException) {
                    println(e)
                    return ArrayList()
                }

                val formatter = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
                formatter.timeZone = TimeZone.getTimeZone(TIMEZONE)
                //delete old data in db using where clause
                dbHandler.deleteWhere("`${table}`", where)
                //add the new Data
                for (dataRow in remoteData){
                    val auxData= hashMapOf<String,String>()
                    for (field in fields){
                        auxData["`$field`"]= dataRow.getString(field)
                    }
                    dbHandler.addData("`${table}`",auxData)
                }
            }
            return dbHandler.getData("`${table}`",where ,sortBy = sort)
        }catch (ex:IllegalStateException){return ArrayList()/*in case the user leave the fragment before the answer*/}
    }

    fun getUser(): String{
        val prefs = fragment.requireContext().getSharedPreferences(PREF_FILE, 0)
        return prefs.getString("appKey", "")!!.substringBefore('|')
    }
}



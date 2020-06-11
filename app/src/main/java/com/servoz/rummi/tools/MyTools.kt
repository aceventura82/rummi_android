package com.servoz.rummi.tools

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import android.widget.*
import androidx.fragment.app.Fragment
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.servoz.rummi.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.ArrayList


class MyTools {

    private var prefs: SharedPreferences? = null

    fun stringListToJSON(json: String): ArrayList<JSONObject> {
        val jsonArr = arrayListOf<JSONObject>()
        val lines=json.lines()
        for (line in lines){
            if(line.count()==0)
                continue
            jsonArr.add(JSONObject(line))
        }
        return jsonArr
    }


    fun makeRequest(url:String, params: HashMap<String, String>,method: Int, context: Context, callback: VolleyCallback){
        val queue = Volley.newRequestQueue(context)
        val stringRequest = object: StringRequest(method, url,
            Response.Listener { response -> callback.onSuccessResponse(response) },
            Response.ErrorListener { callback.onErrorResponse(context, context.getString(R.string.InternetError))}
        ){
            override fun getParams(): Map<String, String> {
                return params
            }
        }
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    fun genKey(fragment: Fragment, login: Boolean=false): String {
        val key = API_KEY
        val formatter = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone(TIMEZONE)
        val tNow = formatter.format(Calendar.getInstance().time).toString()
        val data= tNow +
                if (!login){
                    prefs = fragment.requireContext().getSharedPreferences(PREF_FILE, 0)
                    val appKey = prefs!!.getString("appKey", "")
                    appKey
                }else
                    ""
        val sha256Hmac: Mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(charset("UTF-8")), "HmacSHA256")
        sha256Hmac.init(secretKey)
        return sha256Hmac.doFinal(data.toByteArray(charset("UTF-8"))).toHexString()
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    fun toast(context: Context, msg:String){
        val toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

}

interface VolleyCallback {
    fun onSuccessResponse(result: String)
    fun onErrorResponse(context: Context, result: String){
        println("DEBUG:ERROR remote response userInfo:$result")
        Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
    }
}
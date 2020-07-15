package com.servoz.rummi.ui.login

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.net.Uri.decode
import android.net.Uri.encode
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupWindow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import com.tiper.MaterialSpinner
import kotlinx.android.synthetic.main.change_pass.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_profile.*
import org.apache.commons.io.IOUtils
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.collections.ArrayList

class ProfileFragment : Fragment() {

    private var prefs: SharedPreferences? = null
    private var genderOpc=""
    private var filePath=File("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //get user info
        prefs = requireContext().getSharedPreferences(PREF_FILE, 0)
        val userInfo= getData()
        doAsync {
            updateData(this@ProfileFragment)
        }
        putInfo(userInfo)
        buttonSaveProfile.setOnClickListener{
            updProfile(userInfo["userId_id"].toString())
        }
        home_image_profile.setOnClickListener{
            launchGallery()
        }
        buttonDelProfile.setOnClickListener{
            updProfile(userInfo["userId_id"].toString(), true)
        }
        profile_cancel_button.setOnClickListener{
            NavHostFragment.findNavController(nav_host_fragment).navigate(R.id.action_global_nav_my_games, Bundle())
        }
        swipe_containerUser.setOnRefreshListener {
            doAsync {
                updateData(this@ProfileFragment)
                uiThread {
                    MyTools().toast(requireContext(), getString(R.string.updating))
                    swipe_containerUser.isRefreshing = false
                }
            }
        }

        /*requireActivity().toolbar.buttonChangePass.setOnClickListener {
            val passWindow=PopupWindow(context)
            val windowView=LayoutInflater.from(context).inflate(R.layout.change_pass, layoutProfile, false)
            passWindow.contentView=windowView
            passWindow.isFocusable=true
            passWindow.showAsDropDown(requireActivity().toolbar.buttonChangePass)
            windowView.password_cancel_button.setOnClickListener{
                passWindow.dismiss()
            }
            windowView.password_change_button.setOnClickListener {
                changePass(windowView,passWindow)
            }
        }*/
    }

    // get cached data and display it
    private fun getData(): JSONObject{
        return try{
            MyTools().stringListToJSON(prefs!!.getString("userInfo","")!!)[0]
        }catch(e: JSONException){
            MyTools().toast(requireContext(), requireContext().getString(R.string.ServerError))
            JSONObject()
        }
    }

    fun updateData(fragment:Fragment){
        //get user Info
        FetchData(arrayListOf(),fragment).updateData("userInfo", "",cache = false) {
            result ->
            try {
                prefs = fragment.requireContext().getSharedPreferences(PREF_FILE, 0)
                prefs!!.edit().putString("userInfo", JSONObject(result).toString()).apply()
                // check if new data after last response
                if(result.trim('\n')!=prefs!!.getString("userInfo","{}")!!.trim('\n'))
                    NavHostFragment.findNavController(fragment).navigate(R.id.action_global_nav_profile)
            } catch (e: JSONException) {
                println("MAIN ACT DEBUG: Json Error $result")
                MyTools().toast(fragment.requireContext(), fragment.requireContext().getString(R.string.ServerError))
            }
        }
    }

    private fun putInfo(userInfo:JSONObject){
        //set header
        GlideApp.with(requireContext()).load("${URL}/static/playerAvatars/${userInfo["userId_id"]}${userInfo["extension"]}")
            .signature(ObjectKey(prefs!!.getString("imageSignature", "")!!))
            .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_circle_black_24dp)).into(home_image_profile)
        textViewProfileName.text = when{
            userInfo["nickname"]!=""->{ userInfo["nickname"].toString()}
            userInfo["name"]!=""->{ userInfo["name"].toString()+" "+if(userInfo["lastname"]!="")userInfo["lastname"].toString()else ""}
            else ->{""}
        }
        textViewProfileEmail.text=FetchData(ArrayList(),nav_host_fragment).getUser()
        //Profile Info
        editProfileName.setText(decode(userInfo["name"].toString()))
        editProfileLastName.setText(decode(userInfo["lastname"].toString()))
        editProfileNickName.setText(decode(userInfo["nickname"].toString()))
        editProfileCountry.setText(decode(userInfo["country"].toString()))
        editProfileCity.setText(decode(userInfo["city"].toString()))
        //Gender spinner
        val titles= arrayListOf(getString(R.string.gender),getString(R.string.male),getString(R.string.female))
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, titles)

        editProfileGender.adapter = arrayAdapter
        val values= arrayListOf("", "M", "F")
        editProfileGender.selection=when(userInfo["gender"]) {
            "M" -> 1
            "F" -> 2
            else -> 0
        }

        editProfileGender.onItemSelectedListener = object : MaterialSpinner.OnItemSelectedListener {
            override fun onItemSelected(parent: MaterialSpinner, view: View?, position: Int, id: Long) {
                genderOpc=values[position]
            }
            override fun onNothingSelected(parent: MaterialSpinner) {}
        }
    }
    
    private fun updProfile(userId:String, delPic:Boolean=false){
        loadingSaveProfile.isVisible=true
        doAsync {
            try{

            val requestObj = Multipart(java.net.URL(API_URL))
            requestObj.addFormField("apiKey", MyTools().genKey(nav_host_fragment))
            requestObj.addFormField("usernameUser", FetchData(ArrayList(), nav_host_fragment).getUser())
            requestObj.addFormField("oper", "editProfile")
            if(delPic)
                requestObj.addFormField("deleteBT", "1")
            requestObj.addFormField("name", encode(editProfileName.text.toString()))
            requestObj.addFormField("lastname", encode(editProfileLastName.text.toString()))
            requestObj.addFormField("nickname", encode(editProfileNickName.text.toString()))
            requestObj.addFormField("email", encode(FetchData(ArrayList(),nav_host_fragment).getUser()))
            requestObj.addFormField("country", encode(editProfileCountry.text.toString()))
            requestObj.addFormField("city", encode(editProfileCity.text.toString()))
            requestObj.addFormField("userId", userId)
            requestObj.addFormField("extension", ".jpg")
            requestObj.addFormField("gender", genderOpc)
            requestObj.addFormField("trash", "")
            if(filePath.isFile){
                requestObj.addFilePart("avatar", filePath, filePath.name,  "image/jpeg")
                prefs!!.edit().putString("imageSignature", System.currentTimeMillis().toString()).apply()
            }
            requestObj.upload(
                object: Multipart.OnFileUploadedListener{
                    override fun onFileUploadingSuccess(response: String){
                        println("PROFILE DEBUG:Update remote response $response")
                        uiThread {
                            MyTools().toast(requireContext(),response)
                            loadingSaveProfile.isVisible=false
                            updateData(this@ProfileFragment)
                        }
                    }

                    override fun onFileUploadingFailed(responseCode: Int){
                        uiThread {
                            println("PROFILE DEBUG: Response Error $responseCode")
                            MyTools().toast(requireContext(),responseCode.toString())
                            loadingSaveProfile.isVisible=false
                        }
                    }
                }
            )
            }catch (ex:Exception){
                ex.printStackTrace()
            }
        }
    }
    
    private fun changePass(windowView: View,passWindow: PopupWindow){
        loadingSaveProfile.isVisible=true
        FetchData(arrayListOf(),this).updateData("editPass", "",cache = false,
            addParams = hashMapOf("old_password" to windowView.editPass.text.toString(),
                "new_password1" to windowView.editPass1.text.toString(), "new_password2" to windowView.editPass2.text.toString())) {
                result ->
            val res = result.split("|")
            if(res.count()==2){
                MyTools().toast(requireContext(), getString(R.string.saved))
                passWindow.dismiss()
                updateLogin(FetchData(ArrayList(),this).getUser(), windowView.editPass1.text.toString())
            }else{
                MyTools().toast(requireContext(), result)
            }
            loadingSaveProfile.isVisible=false
        }
    }

    private fun updateLogin(user:String, pass:String){
        //try to login
        FetchData(arrayListOf(),this).updateData("login", "",cache = false, addParams = hashMapOf("usernameUser" to user, "password" to pass)){
                result ->
            //if result start with OK|, login was successful
            if(result.length>5 && result.substring(0,3)=="OK|"){
                //save the email and hash
                prefs!!.edit().putString("appKey", user+"|"+result.substring(3)).apply()
                MyTools().toast(requireContext(), requireContext().getString(R.string.login_ok))
            }else{
                //println("DEBUG:Login Fail $result")
                MyTools().toast(requireContext(), result)
                prefs!!.edit().putString("appKey", "").apply()
            }
        }
    }

    private fun launchGallery() {
        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }else{
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent,0)
        }
    }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == 0) {
            val imageURI=data?.data
            if (imageURI != null) {
                filePath=if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    copyFile(imageURI)
                    File(requireContext().cacheDir, getRealPathFromURI(imageURI,true))
                }else{
                    File(getRealPathFromURI(imageURI))
                }
                GlideApp.with(requireContext()).load(filePath)
                    .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_circle_black_24dp)).into(home_image_profile)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    //for Android 10
    private fun copyFile(fileUri: Uri){
        val parcelFileDescriptor = requireContext().contentResolver.openFileDescriptor(fileUri, "r", null)
        parcelFileDescriptor?.let {
            val inputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
            val file = File(requireContext().cacheDir, getRealPathFromURI(fileUri,true))
            val outputStream = FileOutputStream(file)
            IOUtils.copy(inputStream, outputStream)
        }
    }

    private fun getRealPathFromURI(fileUri:Uri, name:Boolean=false):String {
        val returnCursor = requireContext().contentResolver.query(fileUri, null, null, null, null)
        return if (returnCursor != null) {
            returnCursor.moveToFirst()
            val path=returnCursor.getString(returnCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA))
            returnCursor.close()
            if(name) {
                path.substring(path.lastIndexOf('/') + 1)
            }else
                path
        }else
            ""
    }


}

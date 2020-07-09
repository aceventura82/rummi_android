package com.servoz.rummi.ui.game

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.servoz.rummi.BuildConfig
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_game.*
import org.jetbrains.anko.doAsync
import java.io.File
import java.net.URL

class AudioRecord(private var activity: FragmentActivity, private val buttonLauncherRecord: ImageView, private val buttonCancelRecord: ImageView){
    private var audioFilePath: String? = null
    fun prepareRec(userId:String,gameId:String){
        //check if device has mic
        if(!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)){
            buttonLauncherRecord.isVisible=false
            return
        }
        audioFilePath = activity.baseContext.getExternalFilesDir(null)!!.absolutePath + "/myAudio.3gp"

        buttonLauncherRecord.setOnClickListener {
            if(!isRecording)
                recordAudio()
            else
                stopAudio(userId,gameId)
        }
        requestPermission(Manifest.permission.RECORD_AUDIO, 101)
    }

    var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private fun recordAudio(){
        isRecording = true
        buttonLauncherRecord.setImageResource(0)
        buttonLauncherRecord.setImageResource(R.drawable.ic_baseline_stop_24)
        GlideApp.with(activity.baseContext).load(R.drawable.recording).into(buttonCancelRecord)
        buttonCancelRecord.isVisible=true
        try {
            mediaRecorder = MediaRecorder()
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mediaRecorder?.setOutputFile(audioFilePath)
            mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder?.prepare()
            mediaRecorder?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAudio(userId:String,gameId:String, send:Boolean=true) {
        if (isRecording) {
            buttonLauncherRecord.setImageResource(0)
            buttonLauncherRecord.setImageResource(R.drawable.ic_baseline_mic_24)
            buttonCancelRecord.isVisible=false
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            if(send)
                uploadAudio(userId,gameId)
        }
    }

    fun playAudio(view:ImageView, userId: String, gameId: String) {
        GlideApp.with(activity.baseContext).load(R.drawable.playing_audio).into(buttonCancelRecord)
        val mediaPlayer= MediaPlayer()
        try{
            mediaPlayer.setDataSource("$URL/static/audios/${userId}_${gameId}.3gp")
        }catch(ex:IllegalArgumentException){
            view.isVisible=false
            return
        }
        view.isVisible=true
        mediaPlayer.prepare()
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener { view.isVisible=false }
    }

    fun requestPermission(permissionType: String, requestCode: Int) {
        val permission = ContextCompat.checkSelfPermission(activity.baseContext, permissionType)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(permissionType), requestCode
            )
        }
    }

    private fun uploadAudio(userId:String, gameId:String){
        doAsync {
            val requestObj = Multipart(URL(API_URL))
            val prefs = activity.baseContext.getSharedPreferences(PREF_FILE, 0)
            requestObj.addFormField("oper", "addAudio")
            requestObj.addFormField("version", BuildConfig.VERSION_NAME)
            requestObj.addFormField("gameId", gameId)
            requestObj.addFormField("file", "${userId}_${gameId}")
            requestObj.addFormField("msg", "::AUDIO::${userId}::")
            requestObj.addFormField("apiKey", MyTools().genKey(activity.nav_host_fragment))
            requestObj.addFormField("usernameUser", prefs.getString("appKey", "")!!.substringBefore('|'))

            requestObj.addFilePart("audio", File(audioFilePath!!), File(audioFilePath!!).name,  "audio/3gp")
            requestObj.upload(
                object: Multipart.OnFileUploadedListener{
                    override fun onFileUploadingSuccess(response: String){println(response)}
                    override fun onFileUploadingFailed(responseCode: Int){println(responseCode)}
                }
            )
        }
    }
}
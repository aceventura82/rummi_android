package com.servoz.rummi.ui.game

import android.Manifest
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.media.RingtoneManager
import android.net.Uri
import android.net.Uri.decode
import android.net.Uri.encode
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.*
import android.view.View.*
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import com.bumptech.glide.request.RequestOptions
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.fragment_game.*
import kotlinx.android.synthetic.main.fragment_game.view.text_game_info
import kotlinx.android.synthetic.main.fragment_profile.*
import kotlinx.android.synthetic.main.game_info.view.*
import kotlinx.android.synthetic.main.set_summary.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.getStackTraceString
import org.jetbrains.anko.uiThread
import org.json.JSONException
import org.json.JSONObject
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


/*
* ROAD MAP:
* */

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

class GameFragment: Fragment() {

    private var prefs: SharedPreferences? = null
    private var gameData=JSONObject()
    private var gameSetData= hashMapOf<String,ArrayList<String>>()
    private var players= listOf<String>()
    private var playersCount=0
    private var userId=-1
    private var keySetUser=""
    private var playersNames=arrayListOf("","","","","")
    private var playersExt=arrayListOf("","","","","")
    private var inCard=""
    private var myPos=0
    private var outCard=""
    private lateinit var cardViewPos:View
    private var cardPos=-1
    private var myTurn=false
    private var discardAngles=arrayListOf<Float>()
    private var cardsSpace=15.dp
    private var drawCards="-1"
    private var drawViewCards= arrayListOf<View>()
    private var set="-1"
    private var gameId=""
    private var messagesLastId=-1
    private var flowLastId=0
    private lateinit var summaryWindow:PopupWindow
    private var pickStart=""
    private var doRequest=0
    private var doRequestBg=0
    private var ranId=-2
    private var lastUpd:Long = 0
    private lateinit var audioRecObj:AudioRecord
    private var currentCards=arrayListOf<String>()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREF_FILE, 0)
        //check user config
        if(prefs!!.getString("MESSAGES","")=="ON")
            switchMessageView()
        pickStart=if(prefs!!.getString("PICK_START","")=="ON") "1" else ""
        try{
            gameId= arguments?.getInt("gameId")!!.toString()
            if(prefs!!.getString("CARDS$gameId","")!!.count()>3)
                currentCards=prefs!!.getString("CARDS$gameId","")!!.split(",") as ArrayList<String>
            //cancel notification if exists
            with(NotificationManagerCompat.from(requireContext())) {
                cancel(Integer.parseInt(gameId))
            }
            //mark this gameId as current game
            prefs!!.edit().putString("current_game", gameId).apply()
            //get remote data and set all UI when user enters the game
            try{
                resources
            }catch (ex:IllegalStateException){return}
            doAsync {
                sleep(1000)
                //get stored messages from Db
                getStoredMessages(true)
                getStoredFlowId()
                remoteData(false)
            }
            //set listeners
            setListeners()
            checkLasUpd()
        }catch (ex:NullPointerException){}
    }

    override fun onPause() {
        //remove this game id from current game
        prefs!!.edit().putString("current_game", "").apply()
        super.onPause()
    }

    //************ CONTROL FUNCTIONS

    //update remote data each 3 sec
    private fun bgSync(pId:Int){
        doAsync {
            GlobalScope.launch(context = Dispatchers.Main) {
                while(ranId==pId){
                    try{
                        delay(5000)
                        if(doRequestBg>3)doRequestBg=0
                        if(doRequest>3)doRequest=0
                        remoteData(true)
                    }catch(ex:Exception){sendError("bgSync:${ex.getStackTraceString()}")}
                }
            }
        }
    }
    private fun checkLasUpd(){
        doAsync {
            GlobalScope.launch(context = Dispatchers.Main) {
                while(true){
                    delay(5000)
                    uiThread {
                        try{
                            val tt = (System.currentTimeMillis()-lastUpd).div(1000)
                            when {
                                !myTurn && tt > 20 -> {
                                    text_game_last_upd.setBackgroundResource(R.drawable.circle_red)
                                    ranId=(0..6000).random()
                                    doRequestBg=0
                                    bgSync(ranId)
                                }
                                !myTurn && tt > 5 -> {
                                    text_game_last_upd.setBackgroundResource(R.drawable.circle_yellow)
                                    ranId=(0..6000).random()
                                    doRequestBg=0
                                    bgSync(ranId)
                                }
                                else -> text_game_last_upd.setBackgroundResource(R.drawable.circle)
                            }
                        }catch (ex:Exception){}
                    }
                }
            }
        }
    }

    private fun remoteData(bg:Boolean){
        var dbHandler:Db
        val updDate=try{
            dbHandler=Db(requireContext(),null)
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formatter1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            formatter1.timeZone = TimeZone.getTimeZone(TIMEZONE)
            if(bg)
                formatter1.format(parser.parse(dbHandler.getData("remote_data", "`gameId_id`=$gameId")[0][1])!!)
            else ""
        }catch (ex:java.lang.IllegalStateException){
            return
        }catch (ex:java.lang.IndexOutOfBoundsException){
            dbHandler=Db(requireContext(),null)
            dbHandler.addData("remote_data", hashMapOf("gameId_id" to gameId, "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
            ""
        }
        if(doRequestBg==0){
            doRequestBg++
            FetchData(arrayListOf(),this).updateData("bundleData", "",cache = false,
                addParams = hashMapOf("gameId" to gameId, "update_date" to updDate,
                    "lastId" to (messagesLastId+1).toString(), "lastIdF" to (flowLastId+1).toString())) { result ->
                doRequestBg=0
                lastUpd= System.currentTimeMillis()
                if(result!="OK"){
                    val data = MyTools().stringListToJSON(result)
                    if (data.count() > 0) {
                        //set new update time
                        dbHandler.editData("remote_data", "`gameId_id`=$gameId", hashMapOf("date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
                        //set game Data in variables
                        setGameData(data)
                        //update views
                        initialView()
                    }else
                        text_game_info.text = getString(R.string.unknown_error)
                    if(!bg){
                        //get stored messages
                        getStoredMessages()
                        playersInfo()
                        previewDiscards()
                        showMyCards(true)
                        loadingGame.isVisible=false
                        audioRecObj.prepareRec(userId.toString(),gameId)
                    }
                }
            }
        }
    }
    //set game Data in variables
    private fun setGameData(data:ArrayList<JSONObject>){
        try{
            gameData=data[0]
            playersCount=0
            inCard=""
            players=gameData["playersPos"].toString().split(",")
            val firstRun=userId ==-1
            // get Players names
            playersNames=arrayListOf("","","","","")
            for( (c,p) in players.withIndex())
                if(p!="")
                    for (dataSet in data)
                        if (dataSet.has("messages") && dataSet["messages"] == 1) // stop when get to messages section
                            break
                        else if(dataSet["set_userId_id"]==p){
                            playersNames[c]=dataSet["name"].toString()
                            playersExt[c]=dataSet["extension"].toString()
                            continue
                        }
            userId=Integer.parseInt(JSONObject(requireContext().getSharedPreferences(PREF_FILE, 0).getString("userInfo","")!!)["userId_id"].toString())
            keySetUser="${gameData["current_set"]}_$userId"
            //check if new deal
            var recordType=0
            for(dataSet in data) {
                if (dataSet.has("messages") && dataSet["messages"] == 1) {//add messages
                    recordType = 1
                    continue
                }
                else if (dataSet.has("flow") && dataSet["flow"] == 1) {//add flow messages
                    recordType = 2
                    continue
                }
                val dbHandler=Db(requireContext(),null)
                when(recordType){ // adding game set data
                    0-> {gameSetData["${dataSet["set_set"]}_${dataSet["set_userId_id"]}"] = arrayListOf(dataSet["set_current_cards"].toString(), dataSet["set_drawn"].toString(), dataSet["set_points"].toString())
                        if (dataSet["set_set"] == "1") playersCount++
                    }
                    1 -> {
                        if(dbHandler.getData("messages", "`id`=${dataSet["id"]}").count()==0)
                        dbHandler.addData("messages", hashMapOf("id" to dataSet["id"].toString(),
                            "userId_id" to dataSet["userId_id"].toString(), "gameId_id" to dataSet["gameId_id"].toString(),
                            "msg" to dataSet["msg"].toString(), "date" to dataSet["date"].toString()
                        ))
                        messagesLastId=Integer.parseInt(dataSet["id"].toString())
                        try {
                            messages_input.append(putMsg(dataSet["msg"].toString(), dataSet["date"].toString(), dataSet["userId_id"].toString(), ini=firstRun))
                        }catch (ex:Exception){}
                    }
                    2 -> { //add flow message
                        if(dbHandler.getData("flow", "`id`=${dataSet["id"]}").count()==0)
                        dbHandler.addData("flow", hashMapOf("id" to dataSet["id"].toString(),
                            "gameId_id" to dataSet["gameId_id"].toString(),
                            "msg" to dataSet["msg"].toString(), "date" to dataSet["date"].toString()
                        ))
                        flowLastId=Integer.parseInt(dataSet["id"].toString())
                        try{
                            messages_input.append(putMsg(dataSet["msg"].toString(), dataSet["date"].toString(), flow = true, ini = firstRun))
                            scrollTVDown()
                        }catch (ex:Exception){}
                    }
                }
            }
            //get inCard if returning user to game
            if("2345".indexOf(gameData["moveStatus"].toString())!=-1){
                val cardsAux= gameSetData[keySetUser]!![0].trim(',').split(',')
                if(cardsAux.count()>0) {
                    inCard = cardsAux[cardsAux.count() - 1]
                }
            }
            // order info starting with me
            orderInfo()
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }
        // order info starting with me
        private fun orderInfo(){
            try{
                //get my position
                myPos=0
                for (i in 0..4)
                    if(players[i]==userId.toString()){
                        myPos=i
                        break
                    }
                //no need to change order if user is in first position
                if(myPos>0){
                    //get current userId before reorder
                    val curUserAux=players[Integer.parseInt(gameData["currentPlayerPos"].toString())]
                    players=reOrderArray(players as ArrayList<String>, myPos)
                    playersNames=reOrderArray(playersNames, myPos)
                    playersExt=reOrderArray(playersExt, myPos)
                    gameData.put("current_discarded", reOrderArray(gameData["current_discarded"].toString()
                        .split("|") as ArrayList<String>, myPos).joinToString("|"))
                    //get the current playing player based on new order
                    for((c,player) in players.withIndex())
                        if(player==curUserAux)
                            gameData.put("currentPlayerPos", c.toString())
                }
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }
            //reorder array starting in given position
            private fun reOrderArray(data:ArrayList<String>, pos:Int):ArrayList<String>{
                val aux= arrayListOf<String>()
                try{
                    for (i in pos..4)
                        aux.add(data[i])
                    for (i in 0 until pos)
                        aux.add(data[i])
                }catch(ex:Exception){sendError("reOrderArray INFO:${ex.getStackTraceString()}")}
                return aux
            }

    //get initial view after each movement
    private fun initialView(bg:Boolean=false):Boolean{
        return try {
            try {
                mainButton.isVisible = false
            }catch (ex:IllegalStateException){}
            //check Game status, set controls if open or in game
            gameStatus(bg)
            //show discards
            showDiscards()
            //show drawn cards
            showDrawn()
            //playersInfo
            playersInfo(true)
            //put my cards
            showMyCards()
            //check started value just to verify data
             try {
                gameData["started"] == ""
            } catch (ex: JSONException) {
                false
            }
        }catch (ex:Exception){
            sendError("orderInfo:${ex.getStackTraceString()}")
            ex.printStackTrace()
            false
        }
    }

    //check Game status, set controls if open or in game
    private fun gameStatus(bg: Boolean=false){
        val auxMyTurn=myTurn
        try{
            //check if set just ended and show summary windows
            if(set!="-1" && gameData["current_set"]!=set)
                displaySetSummary("set", bg)
            set=gameData["current_set"].toString()
            text_game_set_number.text = when(set){
                "1" ->getString(R.string.first)
                "2" ->getString(R.string.second)
                "3" ->getString(R.string.third)
                "4" ->getString(R.string.fourth)
                "5" ->getString(R.string.fifth)
                else ->getString(R.string.sixth)
            }
            if (gameData["fullDraw"].toString()[Integer.parseInt(set)-1]=='1')
                text_game_set_number.append(" **")
            // get User position from players array
            val currentUser=Integer.parseInt(players[Integer.parseInt(gameData["currentPlayerPos"].toString())])
            when{
                //is started?
                gameData["started"].toString()== "0"->{
                    text_game_info.text = getString(R.string.waiting_for_player, gameData["code"])
                    // if creator user, show start game button
                    if(playersCount>1 && userId==Integer.parseInt(gameData["userId_id"].toString())){
                        mainButton.text=getString(R.string.start_game)
                        mainButton.isVisible=true
                    }
                    myTurn=false
                }
                // check if game ended?
                gameData["started"].toString()== "2"->{
                    text_game_info.text = getString(R.string.ended)
                    displaySetSummary("game", bg)
                }
                //if not dealt yet and is dealing player, show deal button
                gameData["current_stack"]=="" && currentUser==userId ->{
                    text_game_info.text = getString(R.string.your_turn)
                    mainButton.text=getString(R.string.deal_cards)
                    mainButton.isVisible=true
                    //play sound if first time
                    if(!myTurn) {
                        playNotification()
                    }
                    myTurn=true
                }
                //is started but no dealt yet?
                gameData["current_stack"]=="" ->{
                    text_game_info.text = getString(R.string.waiting_for_deal, playersNames[Integer.parseInt(gameData["currentPlayerPos"].toString())])
                    myTurn=false
                }
                //check if not my turn
                gameData["current_stack"]!="" && currentUser != userId ->{
                    text_game_info.text = getString(R.string.waiting_for_player_move, playersNames[Integer.parseInt(gameData["currentPlayerPos"].toString())])
                    draw_info_layout.isVisible=false
                    mainButton.isVisible=false
                    myTurn=false
                }
                // my turn!
                else ->{
                    //show draw button if not drawn yet and already picked a card and not selecting cards to draw
                    if(gameSetData[keySetUser]!![1]=="" && inCard!="" && mainButton.text!=getString(R.string.cancel)){
                        mainButton.text=getString(R.string.draw)
                        mainButton.isVisible=true
                    }else if(mainButton.text==getString(R.string.cancel))
                        mainButton.isVisible=true
                    when{
                        gameData["moveStatus"]=="1" ->{text_game_info.text = getString(R.string.pick_card)}
                        "23".indexOf(gameData["moveStatus"].toString())!=-1 ->{text_game_info.text = getString(R.string.discard_card_or_draw)}
                        "45".indexOf(gameData["moveStatus"].toString())!=-1 ->{text_game_info.text = getString(R.string.discard_card)}
                    }
                    //play sound if first time
                    if(!myTurn && inCard=="")
                        playNotification()
                    myTurn=true
                }
            }
        }catch(ex:Exception){sendError("gameStatus:${ex.getStackTraceString()}")}
        if(auxMyTurn!=myTurn || ranId==-2){
            ranId=(0..6000).random()
            bgSync(ranId)
        }
    }

    //************ END CONTROL FUNCTIONS

    //*********** INFO
    private fun playersInfo(bg: Boolean=false){
        try{
            val cardsCount= arrayListOf("","","","","")
            for (i in 0..4)
                if(players[i]!="")
                    cardsCount[i]=if(gameSetData["${gameData["current_set"]}_${players[i]}"]!![0]=="") ""
                        else " ("+gameSetData["${gameData["current_set"]}_${players[i]}"]!![0].trim(',').split(",").count()+")"

            gameP1.text=getString(R.string.player_cards,playersNames[0],cardsCount[0])
            GlideApp.with(requireContext()).load("${URL}/static/playerAvatars/${players[0]}${playersExt[0]}")
                    .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_box_white_80dp)).into(gameP1Img)

            // just update image before game start
            if(bg && gameData["started"] != "0")
                return
            // show player icons & draws
            bubble_p1.setBackgroundResource(R.drawable.bubble_bottom)
            if(playersNames[1]!=""){
                gameP2.text=getString(R.string.player_cards,playersNames[1],cardsCount[1])
                gameP2.isVisible=true
                gameP2Img.isVisible=true
                bubble_p2.setBackgroundResource(R.drawable.bubble_right_bottom)
                gameP2.setOnClickListener{addPlayerClickListener(gameP2,players[1])}
                GlideApp.with(requireContext()).load("${URL}/static/playerAvatars/${players[1]}${playersExt[1]}")
                    .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_box_white_80dp)).into(gameP2Img)
            }
            if(playersNames[2]!=""){
                gameP3.text=getString(R.string.player_cards,playersNames[2],cardsCount[2])
                gameP3.isVisible=true
                gameP3Img.isVisible=true
                bubble_p3.setBackgroundResource(R.drawable.bubble_right_top)
                gameP3.setOnClickListener{addPlayerClickListener(gameP3,players[2])}
                GlideApp.with(requireContext()).load("${URL}/static/playerAvatars/${players[2]}${playersExt[2]}")
                    .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_box_white_80dp)).into(gameP3Img)
            }
            if(playersNames[3]!=""){
                gameP4.text=getString(R.string.player_cards,playersNames[3],cardsCount[2])
                gameP4Img.isVisible=true
                gameP4.isVisible=true
                bubble_p4.setBackgroundResource(R.drawable.bubble_left_top)
                gameP4.setOnClickListener{addPlayerClickListener(gameP4,players[3])}
                GlideApp.with(requireContext()).load("${URL}/static/playerAvatars/${players[3]}${playersExt[3]}")
                    .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_box_white_80dp)).into(gameP4Img)
            }
            if(playersNames[4]!=""){
                gameP5.text=getString(R.string.player_cards,playersNames[4],cardsCount[4])
                gameP5.isVisible=true
                gameP5Img.isVisible=true
                bubble_p5.setBackgroundResource(R.drawable.bubble_left_bottom)
                gameP5.setOnClickListener{addPlayerClickListener(gameP5,players[4])}
                GlideApp.with(requireContext()).load("${URL}/static/playerAvatars/${players[4]}${playersExt[4]}")
                    .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_account_box_white_80dp)).into(gameP5Img)
            }
            //hide unused draws
            if(playersNames[1]=="")draw2.visibility=GONE
            if(playersNames[2]=="")draw3.visibility=GONE
            if(playersNames[3]=="")draw4.visibility=GONE
            if(playersNames[4]=="")draw5.visibility=GONE
            //show needed discards
            if(playersNames[1]!="" && (players[2]!="" || players[3]!="" || players[4]!=""))
                discard3.visibility=VISIBLE
            if(playersNames[2]!="" && (players[3]!="" || players[4]!=""))
                discard4.visibility=VISIBLE
            if(playersNames[3]!="" &&  players[4]!="")
                discard5.visibility=VISIBLE

        }catch(ex:Exception){sendError("playersInfo:${ex.getStackTraceString()}")}
    }
        private var muteAudiosIds= arrayListOf<String>()
        private var muteMsgIds= arrayListOf<String>()
        private fun addPlayerClickListener(view: View, userClickId:String){
            val popup = PopupMenu(requireContext(), view)
            popup.inflate(R.menu.user)
            val userPos=muteAudiosIds.indexOf(userClickId)
            if(userPos==-1)
                popup.menu.findItem(R.id.mute_audios_menu).title = "${getString(R.string.audios)} ${getString(R.string.on)}"
            else
                popup.menu.findItem(R.id.mute_audios_menu).title = "${getString(R.string.audios)} ${getString(R.string.off)}"
            val userMsgPos=muteMsgIds.indexOf(userClickId)
            if(userMsgPos==-1)
                popup.menu.findItem(R.id.mute_messages_menu).title = "${getString(R.string.messages)} ${getString(R.string.on)}"
            else
                popup.menu.findItem(R.id.mute_messages_menu).title = "${getString(R.string.messages)} ${getString(R.string.off)}"
            popup.show()

            popup.setOnMenuItemClickListener{ item: MenuItem? ->
                when (item!!.itemId) {
                    R.id.mute_audios_menu -> muteUser(userClickId, userPos)
                    R.id.mute_messages_menu -> muteUser(userClickId, userMsgPos, true)
                }
                true
            }
        }
        private fun muteUser(userMId:String, pos:Int, msg:Boolean=false){
            //add user
            if(pos==-1){
                if(msg)
                    muteMsgIds.add(userMId)
                else
                    muteAudiosIds.add(userMId)
            }else{ //remove user
                if(msg)
                    muteMsgIds.remove(userMId)
                else
                    muteAudiosIds.remove(userMId)
            }
        }

    // set ended summary window
    private fun displaySetSummary(opc:String="", bg: Boolean=false){
        try{
            try{
                if(summaryWindow.isShowing)
                    return
            }catch(ex:UninitializedPropertyAccessException){}
            if(opc=="game" && bg)
                return
            val windowView=LayoutInflater.from(context).inflate(R.layout.set_summary, layoutProfile, false)
            if(Build.VERSION.SDK_INT<=22){
                summaryWindow= PopupWindow(windowView,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                summaryWindow.isOutsideTouchable=false
                summaryWindow.elevation = 5.0f
            }else
                summaryWindow= PopupWindow(requireContext())
            summaryWindow.contentView=windowView
            summaryWindow.isFocusable=true
            summaryWindow.showAtLocation(text_game_info, Gravity.CENTER, 0 ,0)
            windowView.summaryGrid.columnCount=playersCount+1
            windowView.setSummaryLayout.setOnClickListener{
                summaryWindow.dismiss()
            }
            //add set title
            addGrid(windowView,getString(R.string.set),0,0,60.dp, true)
            var col=1
            //find cell width
            val ww=(windowView.width-60.dp).div(playersCount)
            //add each player name
            for(player in playersNames)
                if(player!=""){
                    addGrid(windowView,player,col,0,ww, true)
                    col++
                }
            //add info for each set
            var lastWinner=""
            val total=IntArray(playersCount)
            for(i in 1..Integer.parseInt(set)){
                //add each set number
                val setStr=if(gameData["fullDraw"].toString()[i-1]=='1') "1" else ""
                addGrid(windowView,i.toString(),0,i,60.dp, winner = setStr)
                col=1
                //get each player points per set
                for ((c,player) in players.withIndex()) {
                    var winner=""
                    //check  player set points except current set
                    if(player!="" && (i.toString()!=gameData["current_set"] || gameData["current_set"]=="6")){
                        val points=gameSetData["${i}_$player"]!![2]
                        //check set Winner
                        if(gameSetData["${i}_$player"]!![0]==""){
                            //put 1 if current user is the winner, else name
                            winner=playersNames[c]
                            if(player==userId.toString())
                                winner="1"
                            lastWinner=winner
                        }
                        // check if draw cell as winner
                        val winG=when{
                            winner == "" -> ""
                            winner != "" && gameData["fullDraw"].toString()[i-1]=='1' -> "1"
                            else -> "2"
                        }
                        addGrid(windowView, points, col, i, ww,winner = winG)
                        total[col-1]+=Integer.parseInt(points)
                        col++
                    }
                }
            }
            // add empty sets
            for(i in Integer.parseInt(set)..6) {
                val setStr=if(gameData["fullDraw"].toString()[i-1]=='1') "1" else ""
                addGrid(windowView,i.toString(),0,i,60.dp, winner = setStr)
            }
            //add total row
            addGrid(windowView,getString(R.string.total),0,7,60.dp, true)
            //add each player total
            var winnerUser=0
            var winAux=total[0]
            for((c,tot) in total.withIndex()) {
                addGrid(windowView, tot.toString(), c + 1, 7, ww, true)
                if(tot<winAux){
                    winnerUser=c
                    winAux=tot
                }
            }
            //get the user name
            var posAux=0
            for ((c,name) in playersNames.withIndex()) {
                if(posAux==winnerUser && name != ""){
                    winnerUser=c
                    break
                }
                if (name != "")
                    posAux++
            }

            val msgText=when{
                opc == "set" && lastWinner=="1" -> getString(R.string.summary_congrats)
                opc == "set" -> getString(R.string.summary_lost, lastWinner)
                opc == "game" && winnerUser==0 -> getString(R.string.summary_congrats_game)
                opc == "game" -> getString(R.string.summary_lost_game, playersNames[winnerUser])
                else -> ""
            }
            if(msgText!="") {
                windowView.summaryWinnerText.text = msgText
                //add flow message if game/set just ended
                if(winnerUser == 0 && !bg)
                    if(opc == "set") sendFlow(8, playersNames[winnerUser])
                    else sendFlow(9, playersNames[winnerUser])
            }else
                windowView.summaryWinnerText.isVisible=false
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }
        //add each player points cell
        private fun addGrid(windowView:View,text:String,c:Int,r:Int,w:Int,bold:Boolean=false,winner:String=""){
            try{
                val tv=TextView(requireContext())
                tv.text=text
                tv.gravity= Gravity.CENTER_HORIZONTAL
                tv.textSize = 24F
                tv.maxLines=1
                if(bold)
                    tv.setTypeface(null, Typeface.BOLD)
                if(winner=="1"){ // winner Full Draw
                    tv.setTypeface(null, Typeface.BOLD)
                    tv.setTextColor( Color.BLUE)
                }else if(winner=="2"){ // winner
                    tv.setTypeface(null, Typeface.BOLD)
                    tv.setTextColor( Color.RED)
                }
                tv.layoutParams=
                    GridLayout.LayoutParams(ViewGroup.LayoutParams(w, GridLayout.LayoutParams.WRAP_CONTENT)).apply {
                    columnSpec= GridLayout.spec(c)
                    rowSpec= GridLayout.spec(r)
                    setMargins(20,0,20,0)
                }
                windowView.summaryGrid.addView(tv)
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }

    // show game info window
    private fun displayGameInfo(){
        try{
            val windowView=LayoutInflater.from(context).inflate(R.layout.game_info, drawPreview, false)
            val gameWindow:PopupWindow
            if(Build.VERSION.SDK_INT<=22){
                gameWindow= PopupWindow(windowView,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gameWindow.isOutsideTouchable=false
                gameWindow.elevation = 5.0f
            }else
                gameWindow= PopupWindow(requireContext())
            //val windowView=LayoutInflater.from(context).inflate(R.layout.game_info, drawPreview, false)
            gameWindow.contentView=windowView
            gameWindow.isFocusable=true
            gameWindow.showAtLocation(text_game_info, Gravity.CENTER, 0 ,0)
            windowView.gameInfoLayout.setOnClickListener{
                gameWindow.dismiss()
            }
            val setN:String
            val setG:String
            val fulD=if(gameData["fullDraw"].toString()[Integer.parseInt(gameData["current_set"].toString())-1]=='0') " "+getString(R.string.not_upper) else ""
            when(gameData["current_set"]){
                "1" ->{
                    setN=getString(R.string.first)
                    setG=getString(R.string.game_set_info, getString(R.string.straightN, "1"), ", "+getString(R.string.toakN, "1"))
                }
                "2" ->{
                    setN=getString(R.string.second)
                    setG=getString(R.string.game_set_info, getString(R.string.toakN, "3"), "")
                }
                "3" ->{
                    setN=getString(R.string.third)
                    setG=getString(R.string.game_set_info, getString(R.string.straightN, "1"), ", "+getString(R.string.toakN, "2"))
                }
                "4" ->{
                    setN=getString(R.string.fourth)
                    setG=getString(R.string.game_set_info, getString(R.string.straightN, "2"), "")
                }
                "5" ->{
                    setN=getString(R.string.fifth)
                    setG=getString(R.string.game_set_info, getString(R.string.straightN, "2"), ", "+getString(R.string.toakN, "1"))
                }
                else ->{
                    setN=getString(R.string.sixth)
                    setG=getString(R.string.game_set_info, getString(R.string.straightN, "3"), "")
                }

            }
            windowView.text_game_info.text=getString(R.string.desc_game_info, setN, fulD, setG)
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }

    //get card name
    private fun getCardName(card:String):String{
        return try{
            val color=when(card[1]){
                'S' -> getString(R.string.spades)
                'H' -> getString(R.string.hearts)
                'D' -> getString(R.string.diamonds)
                'C' -> getString(R.string.clubs)
                else -> getString(R.string.joker)
            }
            if(card=="XX") color else if(card[0]=='0') "10 $color" else "${card[0]} $color"
        }catch(ex:Exception){
            sendError("orderInfo:${ex.getStackTraceString()}")
            card
        }
    }

    //*********** INFO

    //************ DISPLAY CARDS FUNCTIONS

    //show user cards in hand
    private fun showMyCards(force:Boolean=false){
        try{
            var setAux= gameData["current_set"]
            //show last set info if game just ended and haven't dealt yet
            if(gameData["current_stack"]=="" && Integer.parseInt(gameData["current_set"].toString())>1 && gameData["started"].toString()!="2")
                setAux=Integer.parseInt(gameData["current_set"].toString())-1
            val myCards= gameSetData["${setAux}_$userId"]?.get(0)!!.trim(',').split(",").toMutableList() as ArrayList<String>
            if(currentCards.sorted()!=myCards.sorted() || force){
                if(!force)
                    currentCards=myCards
                try{
                    cards.removeAllViews()
                    //get cards in hand
                    cardsSpace=(cards.width-60.dp).div(currentCards.count()+1)
                    if(cardsSpace>50.dp)
                        cardsSpace=50.dp
                    //display each card
                    for ((c,card) in currentCards.withIndex())
                        if(card!="")
                            addMyCard(card, c)
                }catch(ex:Exception){sendError("showMyCards:${ex.getStackTraceString()}")}
            }
            //save cards order locally
            prefs!!.edit().putString("CARDS$gameId", currentCards.joinToString(",")).apply()
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }
        //called by showMyCards to add each card
        private fun addMyCard(card:String, pos:Int){
            try{
                val cardImg=ImageView(requireContext())
                cardImg.setImageResource(resources.getIdentifier("d"+card.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
                val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.leftMargin=pos*cardsSpace
                params.topMargin=30.dp
                cardImg.layoutParams=params
                cards.addView(cardImg)
                cardImg.setOnClickListener { setOnHandListener(it, pos*cardsSpace, card) }
                cardImg.setOnLongClickListener {
                    MyTools().toast(requireContext(),getCardName(card))
                    true
                }
            }catch(ex:Exception){sendError("addMyCard:${ex.getStackTraceString()}")}
        }

    //show discarded cards in each position
    private var currentDiscards=arrayListOf("","","","","")
    private fun showDiscards(){
        try{
            //preview listeners
            //the last active discard are the cards in discard1
            //find the last active discard
            var posDiscard=-1
            for(i in 0..4)
                if(players[i]!="")
                    posDiscard=i

            if(discardAngles.count()==0)
                for(i in 0..14)
                    discardAngles.add((0..60).random().toFloat())
            //get discards
            var cp=0
            for( (c,discard) in gameData["current_discarded"].toString().split("|").withIndex()){
                if(currentDiscards[c]!=discard && gameData["current_stack"] != ""){
                    //update discards
                    currentDiscards[c]=discard
                    //remove views
                    when(c){
                        posDiscard -> discard1.removeAllViews()
                        0 -> discard2.removeAllViews()
                        1 -> discard3.removeAllViews()
                        2 -> discard4.removeAllViews()
                        3 -> discard5.removeAllViews()
                    }
                    var dd=discard.trim(',').split(',')
                    if(dd.count()>3)
                        dd=dd.slice(dd.count()-3 until dd.count())
                    for( card in dd){
                        displayCardInDiscard(c, card,cp, posDiscard)
                        cp++
                    }
                }
            }
        }catch(ex:Exception){sendError("showDiscards:${ex.getStackTraceString()}")}
    }
        private fun nextPos(pos:Int,userPick:Int, userDisc:Int):Int{
            var myPos=pos+1
            if(myPos>4)
                myPos=0
            if(myPos==userPick)
                myPos++
            if(myPos==userDisc)
                myPos++
            if(myPos==userPick)
                myPos++
            return myPos
        }
        //put card in given display layout position
        private fun displayCardInDiscard(pos:Int, card:String, cp:Int, posDiscard:Int){
            try{
                val cardImg=ImageView(requireContext())
                cardImg.setImageResource(resources.getIdentifier("d"+card.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
                val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.leftMargin=30.dp
                cardImg.layoutParams=params
                cardImg.rotation=discardAngles[cp]
                //user pick from discard1 , other discards follows
                when(pos){
                    posDiscard -> discard1.addView(cardImg) // user discard
                    0 -> discard2.addView(cardImg)
                    1 -> discard3.addView(cardImg)
                    2 -> discard4.addView(cardImg)
                    3 -> discard5.addView(cardImg)
                }
            }catch(ex:Exception){sendError("displayCardInDiscard:${ex.getStackTraceString()}")}
        }
        private fun previewDiscards(){
            try{
                //find the last active discard
                var posDiscard=-1
                for(i in 0..4)
                    if(players[i]!="")
                        posDiscard=i
                previewDiscardsListeners(posDiscard,discard1)
                var myPos=players.indexOf(userId.toString())
                previewDiscardsListeners(myPos,discard2)
                myPos=nextPos(myPos,posDiscard,players.indexOf(userId.toString()))
                previewDiscardsListeners(myPos,discard3)
                myPos=nextPos(myPos,posDiscard,players.indexOf(userId.toString()))
                previewDiscardsListeners(myPos,discard4)
                myPos=nextPos(myPos,posDiscard,players.indexOf(userId.toString()))
                previewDiscardsListeners(myPos,discard5)
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }


    //show each user drawn games
    private var currentDraws=arrayListOf("","","","","")
    private fun showDrawn(){
        try{
            var setAux= gameData["current_set"]
            if(gameData["current_stack"]=="" && Integer.parseInt(gameData["current_set"].toString())>1 && gameData["started"].toString()!="2")
                setAux=Integer.parseInt(gameData["current_set"].toString())-1
            for((c,player) in players.withIndex()){
                if(player!=""){
                    val key="${setAux}_$player"
                    //check if user has drawn
                    if(currentDraws[c]!=gameSetData[key]!![1]) {
                        //update draw
                        currentDraws[c] = gameSetData[key]!![1]
                         //remove views
                        val draw=when(c){
                            0 -> {
                                draw1.removeAllViews()
                                draw1
                            }
                            1 -> {
                                draw2.removeAllViews()
                                draw2
                            }
                            2 -> {
                                draw3.removeAllViews()
                                draw3
                            }
                            3 -> {
                                draw4.removeAllViews()
                                draw4
                            }
                            else -> {
                                draw5.removeAllViews()
                                draw5
                            }
                        }
                        displayCardsInDraw(gameSetData[key]!![1], player, draw)
                    }
                }
            }
        }catch(ex:Exception){sendError("showDrawn:${ex.getStackTraceString()}")}
    }
        //put each card in user draw area
        private fun displayCardsInDraw(drawn:String, playerId:String, draw:RelativeLayout, preview:Boolean=false){
            try{
                val games=drawn.split("|")
                val numCards=drawn.replace("|", "").trim(',').split(',').count()
                var cardW=(draw.width).div(numCards+(if(games.count()==2)8 else 13))-5
                if(prefs!!.getString("LANDSCAPE","ON")=="OFF" && !preview)
                    cardW*=2
                //loop each game
                var p=0
                var space=0
                var posH = 0
                for((c,game) in games.withIndex()){
                    if(prefs!!.getString("LANDSCAPE","ON")=="OFF" && !preview) {
                        posH = draw.height.div(3) * c
                        p=0
                        space=0
                    }
                    //add each card
                    for(card in game.trim(',').split(',')){
                        val cardImg=ImageView(requireContext())
                        cardImg.setImageResource(resources.getIdentifier("d"+card.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
                        val params = RelativeLayout.LayoutParams(cardW*5, LinearLayout.LayoutParams.WRAP_CONTENT)
                        //move right each card (dCardsSpace), plus space to next game
                        params.leftMargin=(p*cardW)+space
                        params.topMargin = posH
                        cardImg.layoutParams=params
                        if(!preview){ //listener for discard card and show preview
                            cardImg.setOnClickListener {setOnDrawListener(c, cardImg,playerId)}
                            //add long listener for preview
                            cardImg.setOnLongClickListener{
                                showDrawPreview(playerId)
                                true}
                        }
                        else //listener for close preview
                            cardImg.setOnClickListener {drawPreview.isVisible=false}
                        draw.addView(cardImg)
                        p++
                    }
                    space+=cardW*4
                }
            }catch(ex:Exception){sendError("displayCardsInDraw:${ex.getStackTraceString()}")}
        }

    //show the selected draw in the preview layout
    private fun showDrawPreview(playerId: String){
        try{
            var setAux= gameData["current_set"]
            if(gameData["current_stack"]=="" && Integer.parseInt(gameData["current_set"].toString())>1 && gameData["started"].toString()!="2")
                setAux=Integer.parseInt(gameData["current_set"].toString())-1
            drawPreview.removeAllViews()
            drawPreview.isVisible=true
            val showCards=gameSetData["${setAux}_$playerId"]!![1]
            MyTools().toast(requireContext(),getString(R.string.touch_preview_close))
            displayCardsInDraw(showCards, playerId, drawPreview, true)
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }

        //raise a card in the hand
        private fun raiseCardFromHand(view: View, pos:Int){
            try{
                val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.topMargin= 0
                params.leftMargin=pos
                view.layoutParams=params
                //get raised card info
                cardViewPos = view
                cardPos=pos/cardsSpace
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }

        //lower a card in the hand
        private fun lowerCardFromHand(view: View, pos:Int){
            try{
                val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.topMargin= 30.dp
                params.leftMargin=pos
                view.layoutParams=params
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }

        //move a card in the hand
        private fun moveCard(pos:Int){
            try{
                var cards= arrayListOf<String>()
                val cardList=currentCards
                //check if card to move is next position, move before
                if(pos+1==cardPos) {
                    val auxCard= cardList[cardPos]
                    cardList.remove(auxCard)
                    cardList.add(pos,auxCard)
                    cards=cardList
                }else
                    for((c,card) in cardList.withIndex()){
                        if(c!=pos && c!=cardPos) //add previous cards and rest of card, but moved card
                            cards.add(card)
                        else if(c==pos && cardPos<cardList.count()){ // if position to move, add card
                            cards.add(card)
                            cards.add(cardList[cardPos])
                        }
                    }
                gameSetData[keySetUser]!![0]=cards.joinToString(",")+","
                currentCards=cards
                showMyCards(true)
                /*doAsync {
                    FetchData(arrayListOf(),this@GameFragment).updateData("cardsOrder", "",cache = false,
                        addParams = hashMapOf("gameId" to gameData["id"].toString(), "cards" to cards.joinToString(",")+","))
                }*/
            }catch(ex:Exception){sendError("MoveCard:$ex")}
        }

        //order cards, 0: same number, 1: same color
        private fun sortCards(color:Boolean=false){
            try{
                if(gameSetData[keySetUser]!![0].count()<=3)
                    return
                //fix to keep numeric sort
                currentCards= fixSort(currentCards,true,color)
                currentCards.sort()
                currentCards= fixSort(currentCards,false,color)
                gameSetData[keySetUser]!![0]=currentCards.joinToString(",")+","
                MyTools().toast(requireContext(),getString(if(color)R.string.sort_color else R.string.sort_number))
                showMyCards(true)
                /*doAsync {
                    FetchData(arrayListOf(),this@GameFragment).updateData("cardsOrder", "",cache = false,
                        addParams = hashMapOf("gameId" to gameData["id"].toString(), "cards" to cards.joinToString(",")+","))
                }*/
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }
        // fixes for sorting depending on numbers or color
        private fun fixSort(data:ArrayList<String>, start: Boolean, color:Boolean):ArrayList<String>{
            try{
                //first change to fix numeric sort
                if(start)
                    for (i in 0 until data.count()) {
                        data[i] = when (data[i][0]) {
                            'A' -> "Y" + data[i][1]
                            'K' -> "R" + data[i][1]
                            '0' -> "H" + data[i][1]
                            'X' -> "Z" + data[i][1]
                            else -> data[i]
                        }
                    }
                //if sorting by color put colors letter first, or restore back
                if(color)
                    for (i in 0 until data.count())
                        data[i] = when{
                            data[i][1].toString()=="D"-> "B" + data[i][0].toString()
                            data[i][0].toString()=="B"-> data[i][1].toString()+"D"
                            else -> data[i][1].toString() + data[i][0].toString()
                        }
                if(!start)
                    for (i in 0 until data.count()) {
                        data[i] = when (data[i][0]) {
                            'Y' -> "A" + data[i][1]
                            'R' -> "K" + data[i][1]
                            'H' -> "0" + data[i][1]
                            'Z' -> "X" + data[i][1]
                            else -> data[i]
                        }
                    }
                return data
            }catch(ex:Exception){
                sendError("orderInfo:${ex.getStackTraceString()}")
                return data
            }
        }

        //animate options menu
        private fun animateMenu(){
            try{
                val p1:Float
                val p2:Float
                if(!configLayout.isVisible){
                    p1=configLayout.width.toFloat()
                    p2=0f
                    configLayout.visibility= VISIBLE
                    buttonLauncherMenu.setImageDrawable(requireActivity().getDrawable(R.drawable.ic_baseline_arrow_forward_ios_24))
                }
                else{
                    p1=0f
                    p2=configLayout.width.toFloat()
                    configLayout.visibility= INVISIBLE
                    buttonLauncherMenu.setImageDrawable(requireActivity().getDrawable(R.drawable.ic_baseline_arrow_back_ios_24))
                }
                val animate = TranslateAnimation(p1, p2, 0f, 0f)
                animate.duration = 500
                //animate.fillAfter = true
                configLayout.startAnimation(animate)
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }

        // show/hide the message view
        private fun switchMessageView(){
            try{
                message_layout.isVisible= prefs!!.getString("MESSAGES" ,"") == ""
                requireContext().getSharedPreferences(PREF_FILE, 0).edit().putString("MESSAGES", if(message_layout.isVisible) "ON" else "").apply()
            }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
        }

    // ************ END DISPLAY CARDS FUNCTIONS

    // ************ MOVE CARDS FUNCTIONS

    //drag a card from one view to another
    private fun dragCard(moveView:View, fromView:View, toView:View, code: ()->Unit = {}){
        try{
            val location = IntArray(2)
            fromView.getLocationOnScreen(location)
            val location2 = IntArray(2)
            toView.getLocationOnScreen(location2)
            moveView.isVisible=true
            setAllParentsClip(moveView)
            val animate = TranslateAnimation(
                0f, location2[0].toFloat()-location[0].toFloat(),
                0f,location2[1].toFloat()-location[1].toFloat())
            animate.duration = 500
            moveView.startAnimation(animate)
            animate.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(arg0: Animation) {}
                override fun onAnimationRepeat(arg0: Animation) {}
                override fun onAnimationEnd(arg0: Animation) {
                    code()
                    loadingGame.isVisible=false
                }
            })
        }catch(ex:Exception){sendError("DragCard:${ex.getStackTraceString()}")}
    }
        //allow to move view outside the parents boundaries
        private fun setAllParentsClip(viewIn: View) {
        try{
            var view = viewIn
            while (view.parent != null && view.parent is ViewGroup) {
                val viewGroup = view.parent as ViewGroup
                viewGroup.clipChildren = false
                viewGroup.clipToPadding = false
                view = viewGroup
            }
        }catch(ex:Exception){sendError("SetParentClip:${ex.getStackTraceString()}")}
    }
    //call dragCard from deck to preview card
    private fun moveCardFromStack(drawCard:String){
        try{
            sendFlow(3)
            dragCard(moveCardFromDeck,deck,moveCardPreviewHand){
                moveCardFromDeck.isVisible=false
                moveCardPreviewHand.isVisible=true
                moveCardPreviewHand.setImageResource(resources.getIdentifier("d"+drawCard.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
                doAsync {
                    sleep(1000)
                    uiThread {
                        moveCardPreviewHand.isVisible=false
                        initialView()
                    }
                }
            }
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }

    //call dragCard from discard1 to last cards
    private fun moveCardFromDiscard(drawCard:String){
        try{
            sendFlow(4, drawCard)
            moveCardFromDiscard.setImageResource(resources.getIdentifier("d"+drawCard.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
            dragCard(moveCardFromDiscard,discard1,moveCardPreviewHand){
                moveCardFromDiscard.isVisible=false
                moveCardPreviewHand.isVisible=true
                moveCardPreviewHand.setImageResource(resources.getIdentifier("d"+drawCard.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
                doAsync {
                    sleep(1000)
                    uiThread {
                        moveCardPreviewHand.isVisible=false
                        initialView()
                    }
                }
            }
        }catch(ex:Exception){sendError("MoveCardFromDiscard:${ex.getStackTraceString()}")}
    }

    //call dragCard from raised card to discard 2
    private fun moveCardToDiscard(){
        try{
            sendFlow(5, outCard)
            dragCard(cardViewPos,cardViewPos,discard2){
                cardViewPos.isVisible=false
                moveCardPreviewHand.isVisible=false
                inCard = ""
                outCard = ""
            }
            initialView()
        }catch(ex:Exception){sendError("MoveCardToDiscard:${ex.getStackTraceString()}")}
    }

    //call dragCard from raised card to drawn
    private fun moveCardToDraw(view:View){
        try{
            sendFlow(6, outCard)
            dragCard(cardViewPos,cardViewPos,view){
                cardViewPos.isVisible=false
                moveCardPreviewHand.isVisible=false
                outCard = ""
            }
            initialView()
        }catch(ex:Exception){sendError("MoveCardToDraw:${ex.getStackTraceString()}")}
    }

    // ************ MOVE CARDS FUNCTIONS

    // *********** LISTENERS FUNCTIONS
    //set all the initial listeners
    private fun setListeners(){
        //set listener for Start Game Button or start draw
        mainButtonListener()
        //deck when user pick from stack
        deckListener()
        //discard1 when user pick from discard
        pickFromDiscardListener()
        //discard2 when user discard
        discardToListener()
        //scrolling menu
        menuListener()
        //draw button to add game and confirm
        addGameDrawButton.setOnClickListener {addGameToDraw() }
        //send message button
        send_message.setOnClickListener { sendMessage() }
        //text messages listener
        textEnterListener()
        messages_input.movementMethod = ScrollingMovementMethod()
        //hide preview panel when clicked
        drawPreview.setOnClickListener { drawPreview.isVisible=false }
        //listener for put picked card at start or end of cards
        buttonLauncherPickStart.setOnClickListener {
            val posH=if(prefs!!.getString("PICK_START","")=="ON") {
                pickStart=""
                prefs!!.edit().putString("PICK_START", "").apply()
                getString(R.string.end)
            }else {
                pickStart="1"
                prefs!!.edit().putString("PICK_START", "ON").apply()
                getString(R.string.beginnig)
            }
            MyTools().toast(requireContext(), getString(R.string.pick_start, posH))
        }
        audioRecObj = AudioRecord(requireActivity(), buttonLauncherRecord, playAudio)
        playAudio.setOnClickListener {
            if(audioRecObj.isRecording){
                audioRecObj.stopAudio("","", false)
            }
        }
    }

    // add listener for each card in hand, called from addMyCard
    private fun setOnHandListener(view:View, pos:Int, card:String){
        if(drawCards=="-1")
            outCard = when {
                 //if inCard empty, first click, raise card
                outCard==""->{
                    raiseCardFromHand(view, pos)
                    card
                }
                //if card is raised, lower card
                view.marginTop==0->{
                    lowerCardFromHand(view, pos)
                    ""
                }
                // card raised, move card
                else -> {
                    moveCard(pos/cardsSpace)
                    ""
                }
            }
        else
            selectDrawCards(view,card, pos)
    }

    // add listener for each card in drawn, called from displayCardsInDraw
    private fun setOnDrawListener(pos:Int, view: View, user_id:String, posJ:Int=-1){
        loadingGame.isVisible=true
        //check if is your turn, has picked card and selected draw card
        when{
            !myTurn->{
                MyTools().toast(requireContext(),getString(R.string.not_your_turn))
                loadingGame.isVisible=false
            }
            outCard==""->{
                MyTools().toast(requireContext(),getString(R.string.pick_draw_card))
                loadingGame.isVisible=false
            }
            outCard=="XX" && posJ==-1->{
                showJokerPosWindow(pos,view,user_id)
                loadingGame.isVisible=false
            }
            else->{
                if(doRequest==0) {
                    doRequest++
                    //put card in draw
                    FetchData(arrayListOf(), this).updateData(
                        "drawOver",
                        "",
                        cache = false,
                        addParams = hashMapOf(
                            "gameId" to gameData["id"].toString(),
                            "in" to outCard,
                            "drawPos" to pos.toString(),
                            "pos" to posJ.toString(),
                            "drawUserId" to user_id
                        )
                    ) { result ->
                        doRequest=0
                        inCard="1"
                        val res = result.split("|")
                        var msg = result
                        if (res.count() == 2) {
                            msg = res[1]
                            doRequest=0
                            doRequestBg=0
                            remoteData(true) //get new data
                            cards.removeViewAt(cardPos)
                            currentCards.removeAt(cardPos)
                            moveCardToDraw(view)
                            showMyCards(true)
                        }else
                            loadingGame.isVisible=false
                        MyTools().toast(requireContext(), msg)
                    }
                }
            }
        }
    }
    //show joker windows to select side on draw over
    private fun showJokerPosWindow(pos:Int, view: View, user_id:String){
        //check if draw over is straight
        val auxGame=gameSetData["${gameData["current_set"]}_$user_id"]!![1].split("|")[pos].split((","))
        if(auxGame[0][0]==auxGame[1][0])
            setOnDrawListener(pos,view,user_id,1)
        else {
            val dialogBuilder = AlertDialog.Builder(requireContext())
            dialogBuilder.setMessage(requireContext().getString(R.string.message_joker_pos))
                // if the dialog is cancelable
                .setCancelable(true)
                // positive button text and action
                .setPositiveButton(requireContext().getString(R.string.right)) { _, _ ->
                    setOnDrawListener(pos, view, user_id, 1)
                }
                // negative button text and action
                .setNegativeButton(requireContext().getString(R.string.left)) { _, _ ->
                    setOnDrawListener(pos, view, user_id, 0)
                }
            val alert = dialogBuilder.create()
            alert.setTitle(requireContext().getString(R.string.select_side))
            alert.show()
        }
    }

    // main button, start game, deal cards
    private fun mainButtonListener(){
        mainButton.setOnClickListener {
            loadingGame.isVisible=true
            when(mainButton.text){
                getString(R.string.start_game) ->{ startGame() }
                getString(R.string.deal_cards) ->{ dealCards() }
                getString(R.string.draw) ->{
                    drawCards=""
                    draw_info_layout.isVisible=true
                    mainButton.text=getString(R.string.cancel)
                }
                getString(R.string.cancel) ->{
                    draw_info_layout.isVisible=false
                    mainButton.text=getString(R.string.draw)
                    drawCards="-1"
                    text_draw_info.removeAllViews()
                    addGameDrawButton.text=getString(R.string.add_draw_game)
                    showMyCards(true)
                }
            }
            loadingGame.isVisible=false
        }
    }
        // users starts the game
        private fun startGame(){
            val dialogBuilder = AlertDialog.Builder(requireContext())
            dialogBuilder.setMessage(requireContext().getString(R.string.message_start_game))
                // if the dialog is cancelable
                .setCancelable(true)
                // positive button text and action
                .setPositiveButton(requireContext().getString(R.string.start_game)) { _, _ ->
                    if(doRequest==0) {
                        doRequest++
                        FetchData(arrayListOf(),this).updateData("startGame", "",cache = false, addParams = hashMapOf("gameId" to gameData["id"].toString())) { result ->
                            doRequest=0
                            val res = result.split("|")
                            var msg = result
                            if (res.count() == 2) {
                                msg = res[1]
                                text_game_info.text = getString(R.string.loading)
                                mainButton.isVisible = false
                                initialView()
                                sendFlow(1)
                            }
                            MyTools().toast(requireContext(), msg)
                        }
                    }
                }
                // negative button text and action
                .setNegativeButton(requireContext().getString(R.string.cancel)) { dialog, _ ->
                    dialog.cancel()
                }
            val alert = dialogBuilder.create()
            alert.setTitle(requireContext().getString(R.string.start_game))
            alert.show()
        }
        //users deal the cards
        private fun dealCards(){
            if(doRequest==0) {
                doRequest++
                cards.removeAllViews()
                FetchData(arrayListOf(),this).updateData("dealCards", "",cache = false, addParams = hashMapOf("gameId" to gameData["id"].toString())) {
                        result ->
                    doRequest=0
                    val res = result.split("|")
                    var msg=result
                    if(res.count()==2){
                        doRequest=0
                        doRequestBg=0
                        remoteData(true) //get new data
                        msg=res[1]
                        text_game_info.text = getString(R.string.loading)
                        mainButton.isVisible=false
                        initialView()
                        ranId=(0..6000).random()
                        bgSync(ranId)
                        sendFlow(2, set)
                    }
                    MyTools().toast(requireContext(),msg)
                }
            }
        }

    //deck button, pick up cards from deck
    private fun deckListener(){
        deck.setOnClickListener {
            loadingGame.isVisible=true
            //check if is your turn
            when{
                !myTurn->{
                    MyTools().toast(requireContext(),getString(R.string.not_your_turn))
                    loadingGame.isVisible=false
                }
                inCard!=""->{
                    MyTools().toast(requireContext(),getString(R.string.already_picked_card))
                    loadingGame.isVisible=false
                }
                else->{
                    if(doRequest==0) {
                        doRequest++
                        FetchData(arrayListOf(),this).updateData("pickCard", "",cache = false,
                            addParams = hashMapOf("gameId" to gameData["id"].toString(), "stack" to "1")) { result ->
                            doRequest=0
                            doRequestBg=0
                            remoteData(true) //get new data
                            val res = result.split("|")
                            var msg=result
                            if(res.count()==2){
                                msg=res[1]
                                inCard=res[1]
                                remoteData(true)
                                if(pickStart=="1")
                                    currentCards.add(0, inCard)
                                else
                                    currentCards.add(inCard)
                                moveCardFromStack(res[1])
                                showMyCards(true)
                            }
                            MyTools().toast(requireContext(),msg)
                        }
                    }
                }
            }
        }
    }

    // discard1, pick card from discard
    private fun pickFromDiscardListener(){
        discard1.setOnClickListener{
            loadingGame.isVisible=true
            //check if is your turn
            when{
                !myTurn->{
                    MyTools().toast(requireContext(),getString(R.string.not_your_turn))
                    loadingGame.isVisible=false
                }
                inCard!=""->{
                    MyTools().toast(requireContext(),getString(R.string.already_picked_card))
                    loadingGame.isVisible=false
                }
                gameSetData[keySetUser]?.get(1)!=""->{
                    MyTools().toast(requireContext(),getString(R.string.cannot_pick_discard_drawn))
                    loadingGame.isVisible=false
                }
                else->{
                    if(doRequest==0) {
                        doRequest++
                       FetchData(arrayListOf(),this).updateData("pickCard", "",cache = false,
                           addParams = hashMapOf("gameId" to gameData["id"].toString(), "discard" to "1")) { result ->
                           doRequest=0
                           doRequestBg=0
                           remoteData(true) //get new data
                            val res = result.split("|")
                           var msg=result
                           if(res.count()==2){
                               msg=res[1]
                               inCard=res[1]
                               currentCards.add(inCard)
                               remoteData(true)
                               moveCardFromDiscard(res[1])
                               showMyCards(true)
                            }
                           MyTools().toast(requireContext(),msg)
                        }
                    }
                }
            }
        }
    }

    // discard2, drop card to discard
    private fun discardToListener(){
        discard2.setOnClickListener {
            loadingGame.isVisible=true
            //check if is your turn, has picked card and selected discard card
            when{
                !myTurn->{
                    MyTools().toast(requireContext(),getString(R.string.not_your_turn))
                    loadingGame.isVisible=false
                }
                inCard==""->{
                    MyTools().toast(requireContext(),getString(R.string.pick_card))
                    loadingGame.isVisible=false
                }
                outCard==""->{
                    MyTools().toast(requireContext(),getString(R.string.pick_discard_card))
                    loadingGame.isVisible=false
                }
                else->{
                    //check if discard card is twice, then remove the position selected and save to fixDiscardDouble
                    /*var fixCardTwice=""
                    if(gameSetData[keySetUser]!![0].split(outCard).count()==3){
                        for ((c,cc) in gameSetData[keySetUser]!![0].split(",").withIndex())
                            if(c!=cardPos && cc!="")
                                fixCardTwice+="$cc,"
                    }*/
                    //discard card
                    if(doRequest==0) {
                        doRequest++
                        FetchData(arrayListOf(), this).updateData(
                            "discardCard", "", cache = false,
                            addParams = hashMapOf("gameId" to gameData["id"].toString(), "out" to outCard)
                        ) { result ->
                            val res = result.split("|")
                            var msg=result
                            if(res.count()==2){
                                msg=res[1]
                                moveCardToDiscard()
                                cards.removeViewAt(cardPos)
                                currentCards.removeAt(cardPos)
                                showMyCards(true)
                                //reorder cards when discard is twice
                                /*if(fixCardTwice!="")
                                    currentCards=arrayListOf()
                                    doAsync {
                                        FetchData(arrayListOf(),this@GameFragment).updateData("cardsOrder", "",cache = false,
                                            addParams = hashMapOf("gameId" to gameData["id"].toString(), "cards" to fixCardTwice))
                                    }*/
                            }else
                                loadingGame.isVisible=false
                            doRequest=0
                            doRequestBg=0
                            remoteData(true) //get new data
                            MyTools().toast(requireContext(),msg)
                        }
                    }
                }
            }
        }
    }

    // menu buttons listener
    private fun menuListener(){
        //open close menu
        buttonLauncherMenu.setOnClickListener{animateMenu()}
        //sort by Number
        buttonLauncherSortN.setOnClickListener{sortCards()}
        //sort by color
        buttonLauncherSortC.setOnClickListener{sortCards(true)}
        //show positions view
        buttonLauncherStanding.setOnClickListener { displaySetSummary("", true) }
        //show info view
        buttonLauncherInfo.setOnClickListener { displayGameInfo() }
        //show hide messages
        buttonLauncherChat.setOnClickListener { switchMessageView() }

    }

    //add selected card to draw list
    private fun selectDrawCards(view: View, card: String, pos: Int){
        //add cards if can add game
        if(addGameDrawButton.text!=getString(R.string.confirm_draw)){
            //remove from list and lower card
            if(view.marginTop==0){
                lowerCardFromHand(view, pos)
                drawCards= drawCards.replaceFirst("$card,", "")
                drawViewCards.remove(view)
            }
            //add card and raise it
            else{
                raiseCardFromHand(view, pos)
                drawCards+= "$card,"
                drawViewCards.add(view)
            }
        }
    }

    //check number of game for the current set and add it or show the confirm button to make the draw
    private fun addGameToDraw(){
        //if more games to add
        if(addGameDrawButton.text!=getString(R.string.confirm_draw)){
            val numGames=drawCards.split("|").count()
            val gameGames=if(gameData["current_set"]== "1" || gameData["current_set"]== "4") 2  else 3
            drawCards+= "|"
            addDrawInfoGame(drawCards)
            if(numGames>=gameGames)
                addGameDrawButton.text=getString(R.string.confirm_draw)
            for (v in drawViewCards)
                v.isEnabled=false
        }else{
            if(doRequest==0) {
                doRequest++
                loadingGame.isVisible=true
                //confirm and draw game
                FetchData(arrayListOf(),this).updateData("draw", "",cache = false,
                    addParams = hashMapOf("gameId" to gameData["id"].toString(), "drawCards" to drawCards.trim('|'))) {
                        result ->
                    doRequest=0
                    remoteData(true) //get new data
                    val res = result.split("|")
                    var msg=result
                    if(res.count()==2){
                        msg=res[1]
                        draw_info_layout.isVisible=false
                        mainButton.isVisible=false
                        mainButton.text=getString(R.string.draw)
                        for (cc in drawCards.trim('|').split("|"))
                            currentCards.remove(cc)
                        drawCards="-1"
                        for (cc in drawViewCards)
                            cards.removeView(cc)
                        showMyCards(true)
                        text_draw_info.removeAllViews()
                        addGameDrawButton.text=getString(R.string.add_draw_game)
                        initialView()
                        sendFlow(7)
                    }
                    MyTools().toast(requireContext(),msg)
                    loadingGame.isVisible=false
                }
            }
        }
    }
        //called by addGameToDraw to add each game
        private fun addDrawInfoGame(cards:String){
            text_draw_info.removeAllViews()
            for(game in cards.trim('|').split('|')){
                val ll=LinearLayout(requireContext())
                for(card in game.trim(',').split(',')){
                    val cardImg=ImageView(requireContext())
                    cardImg.setImageResource(resources.getIdentifier("d"+card.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
                    val params = LinearLayout.LayoutParams(30.dp, 30.dp)
                    params.setMargins(0,0,0,0)
                    cardImg.layoutParams=params
                    ll.addView(cardImg)
                }
                text_draw_info.addView(ll)
            }
        }

    //put preview listeners on discards
    private fun previewDiscardsListeners(posDiscard:Int, discard:RelativeLayout){
        discard.setOnLongClickListener{
            drawPreview.removeAllViews()
            drawPreview.isVisible=true
            val showCards=currentDiscards[posDiscard]
            MyTools().toast(requireContext(),getString(R.string.touch_preview_close))
            displayCardsInDraw(showCards, "", drawPreview, true)
            true
        }
    }

    // *********** END LISTENERS FUNCTIONS

    // *********** MESSAGES

    //get message from db when first load
    private fun getStoredMessages(getId:Boolean=false){
        try {
            val dbHandler=Db(requireContext(),null)
            val lastId=dbHandler.getData("messages", "`gameId_id`=$gameId", "max(`id`)")
            messagesLastId=try{
                Integer.parseInt(lastId[0][0])
            }catch(ex:java.lang.NumberFormatException){ 0 }
            if(getId)
                return
            for(message in dbHandler.getMessages(gameId)){
                if(message[3] == "" && message[0].count() > 9 && message[0].substring(0,9) == "::AUDIO::" && message[0].split("::").count()==4)
                    continue
                messages_input.append(putMsg(message[0], message[1], message[2], message[3] =="FLOW", true))

            }
        }catch(ex:Exception){sendError(ex.toString())}
    }

    //get flow message from db when first load
    private fun getStoredFlowId() {
        try {
            val dbHandler=Db(requireContext(),null)
            val lastId = dbHandler.getData("flow", "`gameId_id`=$gameId", "max(`id`)")
            flowLastId = try { Integer.parseInt(lastId[0][0]) } catch (ex: java.lang.NumberFormatException) { 0 }
        }catch(ex:Exception){sendError(ex.toString())}
    }

    //put a message in the messages area
    private fun putMsg(msg:String, date:String, userIdMsg:String="", flow:Boolean=false, ini:Boolean=false):SpannableString{
        if(!flow && !ini){
            //if Audio play it
            if(msg.count() > 9 && msg.substring(0,9) == "::AUDIO::" && msg.split("::").count()==4) {
                if(muteAudiosIds.indexOf(userIdMsg)!=-1) //user muted
                    return SpannableString("")
                val userIdAudio = msg.split("::")[2]
                if(userIdAudio != userId.toString())
                    audioRecObj.playAudio(playAudio, userIdAudio, gameId)
            }else{
                if(muteMsgIds.indexOf(userIdMsg)!=-1) //user muted
                    return SpannableString("")
                //play notification and show messages windows
                if(userId.toString()!=userIdMsg){
                    playNotification()
                    scrollTVDown()
                }
            }
            if(msg.split("::").count()==4 && msg.count() > 9 && msg.substring(0,9) == "::AUDIO::"){
                if(userId.toString()!=userIdMsg)
                    showBubble(getString(R.string.speaking), userIdMsg)
            }else
                showBubble(msg, userIdMsg)
        }
        if(msg.split("::").count()==4 && msg.count() > 9 && msg.substring(0,9) == "::AUDIO::")
            return SpannableString("")
        return try{
            val text=if(userIdMsg!="")decode(msg) else getFlowMsg(msg)
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
            val user = if(userIdMsg!="")playersNames[players.indexOf(userIdMsg)] else ""
            val ss1 = SpannableString("$user ${formatter.format(parser.parse(date)!!)}: ${text}\n")
            ss1.setSpan(RelativeSizeSpan(0.5f), user.count(), user.count()+9, 0) // set size
            if(flow) {
                ss1.setSpan(ForegroundColorSpan(Color.GRAY), 0, ss1.count(), 0) // set color
                if(!myTurn && !ini)
                    MyTools().toast(requireContext(),ss1.substring(10, ss1.toString().count()))
            }
            ss1
        }catch (ex:Exception){
            sendError(ex.toString())
            SpannableString(msg)
        }
    }

    private fun showBubble(msg:String,userIdM:String){
        val playersAux=reOrderArray(players as ArrayList<String>, myPos)
        val bubble = when(playersAux.indexOf(userIdM)){
            0->bubble_p1
            1->bubble_p2
            2->bubble_p3
            3->bubble_p4
            else->bubble_p5
        }
        bubble.isVisible=true
        bubble.text=decode(msg)

        doAsync {
            sleep(5000)
            uiThread {
                try {
                    bubble.isVisible=false
                }catch (ex:IllegalStateException){}
            }
        }
    }

    private fun scrollTVDown(){
        try{
            val scrollAmount = messages_input.layout.getLineTop(messages_input.lineCount) - messages_input.height
            if (scrollAmount > 0)
                messages_input.scrollTo(0, scrollAmount)
            else
                messages_input.scrollTo(0, 0)
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }

    private fun textEnterListener(){
        messages_input.setOnKeyListener(OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                sendMessage()
                return@OnKeyListener true
            }
            false
        })
    }

    //add new message in the server
    private fun sendMessage(){
        if(messages_text.text.toString().count()>0)
            if(doRequest==0) {
                    doRequest++
                FetchData(arrayListOf(),this).updateData("addMessage", "", cache=false,
                    addParams = hashMapOf("gameId" to gameId, "msg" to encode(messages_text.text.toString()))){doRequest=0}
                messages_text.setText("")
            }
    }

    //add new message in the server
    private fun sendFlow(msgId:Int, aux:String=""){
        try{
            doAsync{
                FetchData(arrayListOf(),this@GameFragment).updateData("addToFlow", "", cache=false,
                    addParams = hashMapOf("gameId" to gameId, "msg" to "${playersNames[0]}||--||$msgId||--||$aux"))
            }
        }catch(ex:Exception){sendError("orderInfo:${ex.getStackTraceString()}")}
    }

    private fun getFlowMsg(msg:String):String{
        try{
            val msgData=msg.split("||--||")
            if(msgData.count()!=3)
                return msg
            return when(msgData[1]){
                "1"->getString(R.string.start_game)
                "2"->"-------------------\n${getString(R.string.set)} ${msgData[2]}, ${msgData[0]}: ${getString(R.string.deal_cards)}"
                "3"->"${msgData[0]}: ${getString(R.string.picked_card_stack)}"
                "4"->"${msgData[0]}: ${getString(R.string.picked_card_discard)}:${getCardName(msgData[2])}"
                "5"->"${msgData[0]}: ${getString(R.string.discarded_card)}:${getCardName(msgData[2])}"
                "6"->"${msgData[0]}: ${getString(R.string.draw_over_card)}:${getCardName(msgData[2])}"
                "7"->"${msgData[0]}: ${getString(R.string.drawn)}"
                "8"->"${msgData[0]}: ${getString(R.string.won_set)}"
                "9"->"${msgData[0]}: ${getString(R.string.won_game)}"
                "10"->"${msgData[0]}: ${getString(R.string.created_game)}"
                "11"->"${msgData[0]}: ${getString(R.string.joined_game)}"
                else -> msg
            }
        }catch(ex:Exception){
            sendError("orderInfo:${ex.getStackTraceString()}")
            return msg
        }
    }

    private fun playNotification(){
        if(prefs!!.getString("MUTE_NOTIFICATIONS", "ON") =="OFF")
            return
        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(requireContext().applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //add new message in the server
    private fun sendError(msg:String){
        println("ERROR:$msg")
        doAsync {
            FetchData(arrayListOf(),this@GameFragment).updateData("errors", "", cache=false,
                addParams = hashMapOf("data" to msg))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            101 -> { //RECORD_REQUEST_CODE

                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    buttonLauncherRecord.isVisible = false
                } else
                    audioRecObj.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 102)
                return
            }
            102 -> { //STORAGE_REQUEST_CODE
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)
                    buttonLauncherRecord.isVisible = false
                return
            }
        }
    }
    // *********** END MESSAGES
}
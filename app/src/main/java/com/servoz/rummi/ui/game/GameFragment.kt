package com.servoz.rummi.ui.game

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Color
import android.graphics.Typeface
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import com.servoz.rummi.R
import com.servoz.rummi.tools.*
import kotlinx.android.synthetic.main.fragment_game.*
import kotlinx.android.synthetic.main.fragment_game.view.text_game_info
import kotlinx.android.synthetic.main.fragment_profile.*
import kotlinx.android.synthetic.main.game_info.view.*
import kotlinx.android.synthetic.main.set_summary.view.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import org.json.JSONObject
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.*


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
    private var moving=false
    private lateinit var summaryWindow:PopupWindow
    private var flowAuto=""
    private lateinit var dbHandler:Db
    private var updateCards=true

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREF_FILE, 0)
        //check user config
        if(prefs!!.getString("MESSAGES","")=="ON")
            switchMessageView()
        flowAuto=prefs!!.getString("MESSAGES_FLOW","").toString()
        dbHandler=Db(requireContext(),null)

        try{
            gameId= arguments?.getString("gameId")!!
            prefs!!.edit().putString("current_game", gameId).apply()
            //get stored messages from Db
            getStoredMessages(true)
            getStoredFlow(true)
            //get remote data and set all UI when user enters the game
            remoteData(false)
            //set listeners
            setListeners()
        }catch (ex:NullPointerException){}
    }

    companion object {

        fun newInstance(gameId: String): GameFragment {
            val args = Bundle()
            args.putString("gameId", gameId)
            val fragment = GameFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onPause() {
        prefs!!.edit().putString("current_game", "").apply()
        super.onPause()
    }

    //************ CONTROL FUNCTIONS

    //update remote data each 3 sec
    private fun bgSync(){
        doAsync {
            while(true){
                sleep(3_000)
                remoteData(true)
                if(gameData["started"].toString()== "2")
                    break
            }
        }
    }

    //get initial view after each movement
    private fun initialView(bg:Boolean=false):Boolean{
        //check Game status, set controls if open or in game
        gameStatus(bg)
        //show discards
        showDiscards()
        //show drawn cards
        showDrawn()
        if(!bg){
            //playersInfo
            playersInfo()
            //put my cards
            showMyCards()
        }
        loadingGame.isVisible=false
        return try{
            gameData["started"]==""
        }catch(ex: JSONException){
            false
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun remoteData(bg:Boolean){
        val updDate=try{
            if(bg)dbHandler.getData("remote_data", "`gameId_id`=$gameId")[0][1]  else ""
        }catch (ex:java.lang.IndexOutOfBoundsException){
            dbHandler.addData("remote_data", hashMapOf("gameId_id" to gameId, "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())))
            ""
        }
        FetchData(arrayListOf(),this).updateData("bundleData", "",cache = false,
            addParams = hashMapOf("gameId" to gameId, "update_date" to updDate,
                "lastId" to (messagesLastId+1).toString(), "lastIdF" to (flowLastId+1).toString())) { result ->
            if(result!="OK"){
                val data = MyTools().stringListToJSON(result)
                if (data.count() > 0) {
                    //set new update time
                    dbHandler.editData("remote_data", "`gameId_id`=$gameId", hashMapOf("date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())))
                    //set game Data in variables
                    setGameData(data)
                    //update views
                    initialView(!updateCards)
                    updateCards=false
                }else
                    text_game_info.text = getString(R.string.unknown_error)
                if(!bg){
                    //start background remote sync if first time
                    bgSync()
                    //get stored messages
                    getStoredMessages()
                    getStoredFlow()
                }
            }
        }
    }
    //set game Data in variables
    private fun setGameData(data:ArrayList<JSONObject>){
        gameData=data[0]
        playersCount=0
        players=gameData["playersPos"].toString().split(",")
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
            when(recordType){ // adding game set data
                0-> {gameSetData["${dataSet["set_set"]}_${dataSet["set_userId_id"]}"] = arrayListOf(dataSet["set_current_cards"].toString(), dataSet["set_drawn"].toString(), dataSet["set_points"].toString())
                    if (dataSet["set_set"] == "1")
                        playersCount++
                }
                1 -> {
                    try{
                        dbHandler.addData("messages", hashMapOf("id" to dataSet["id"].toString(),
                            "userId_id" to dataSet["userId_id"].toString(), "gameId_id" to dataSet["gameId_id"].toString(),
                            "msg" to dataSet["msg"].toString(), "date" to dataSet["date"].toString()
                        ))
                        messages_input.append(putMsg(dataSet["msg"].toString(), dataSet["date"].toString(),dataSet["userId_id"].toString()))
                        if(userId.toString()!=dataSet["userId_id"].toString())
                            playNotification()
                    }catch (ex: SQLiteConstraintException){}
                }
                2 -> {
                    try{
                        dbHandler.addData("flow", hashMapOf("id" to dataSet["id"].toString(),
                            "gameId_id" to dataSet["gameId_id"].toString(),
                            "msg" to dataSet["msg"].toString(), "date" to dataSet["date"].toString()
                        ))
                        text_game_flow.append(putMsg(dataSet["msg"].toString(), dataSet["date"].toString()))
                    }catch (ex: SQLiteConstraintException){}
                }
            }
        }
        userId=Integer.parseInt(JSONObject(requireContext().getSharedPreferences(PREF_FILE, 0).getString("userInfo","")!!)["userId_id"].toString())
        keySetUser="${gameData["current_set"]}_$userId"
        // get Players names
        playersNames=arrayListOf("","","","","")
        for( (c,p) in players.withIndex())
            if(p!="")
                for (dataSet in data)
                    if (dataSet.has("messages") && dataSet["messages"] == 1) // stop when get to messages section
                        break
                    else if(dataSet["set_userId_id"]==p)
                        playersNames[c]=dataSet["name"].toString()
        // order info starting with me
        orderInfo()
    }
        // order info starting with me
        private fun orderInfo(){
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
                gameData.put("current_discarded", reOrderArray(gameData["current_discarded"].toString()
                    .split("|") as ArrayList<String>, myPos).joinToString("|"))
                //get the current playing player based on new order
                for((c,player) in players.withIndex())
                    if(player==curUserAux)
                        gameData.put("currentPlayerPos", c.toString())
            }
        }
            //reorder array starting in given position
            private fun reOrderArray(data:ArrayList<String>, pos:Int):ArrayList<String>{
                val aux= arrayListOf<String>()
                for (i in pos..4)
                    aux.add(data[i])
                for (i in 0 until pos)
                    aux.add(data[i])
                return aux
            }

    //check Game status, set controls if open or in game
    private fun gameStatus(bg: Boolean=false){
        //check if set just ended and show summary windows
        if(set!="-1" && gameData["current_set"]!=set)
            displaySetSummary("set")
        set=gameData["current_set"].toString()
        // get User position from players array
        val currentUser=Integer.parseInt(players[Integer.parseInt(gameData["currentPlayerPos"].toString())])
        when{
            //is started?
            gameData["started"].toString()== "0"->{
                text_game_info.text = getString(R.string.waiting_for_player)
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
                if(!myTurn)
                    playNotification()
            }
            //is started but no dealt yet?
            gameData["current_stack"]=="" ->{
                text_game_info.text = getString(R.string.waiting_for_deal)
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
                mainButton.isVisible=false
                //show draw button if not drawn yet and already picked a card and not selecting cards to draw
                if(gameSetData[keySetUser]!![1]=="" && inCard!="" && mainButton.text!=getString(R.string.cancel)){
                    mainButton.text=getString(R.string.draw)
                    mainButton.isVisible=true
                }
                when{
                    gameData["moveStatus"]=="1" ->{text_game_info.text = getString(R.string.pick_card)}
                    "23".indexOf(gameData["moveStatus"].toString())!=-1 ->{text_game_info.text = getString(R.string.discard_card_or_draw)}
                    "45".indexOf(gameData["moveStatus"].toString())!=-1 ->{text_game_info.text = getString(R.string.discard_card)}
                }
                //play sound if first time
                if(!myTurn)
                    playNotification()
                myTurn=true
            }
        }
        if(cards.childCount==0)
            showMyCards()
    }

    //************ END CONTROL FUNCTIONS

    //*********** INFO
    private fun playersInfo(){
        gameP1.text=playersNames[0]
        gameP2.text=playersNames[1]
        gameP3.text=playersNames[2]
        gameP4.text=playersNames[3]
        gameP5.text=playersNames[4]
        val image = getDrawable(requireContext(),R.drawable.ic_account_box_white_80dp)
        val h = image!!.intrinsicHeight
        val w = image.intrinsicWidth
        image.setBounds(0, 0, w, h)
        gameP1.setCompoundDrawables(null,image, null, null)
        if(playersNames[1]!="")
            gameP2.setCompoundDrawables(null,image, null, null)
        else{
            gameP2.visibility= GONE
            draw2.visibility=GONE
            discard3.visibility=GONE
        }
        if(playersNames[2]!="")
            gameP3.setCompoundDrawables(null,image, null, null)
        else{
            gameP3.visibility=GONE
            draw3.visibility=GONE
            discard4.visibility=GONE
        }
        if(playersNames[3]!="")
            gameP4.setCompoundDrawables(null,image, null, null)
        else{
            gameP4.visibility=GONE
            draw4.visibility=GONE
        }
        if(playersNames[4]!="")
            gameP5.setCompoundDrawables(null,image, null, null)
        else{
            gameP5.visibility=GONE
            draw5.visibility=GONE
        }
    }

    // set ended summary window
    private fun displaySetSummary(opc:String="", bg: Boolean=false){
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
            if(bg) //add flow message if game/set just ended
                sendFlow(msgText)
        }else
            windowView.summaryWinnerText.isVisible=false

    }
        //add each player points cell
        private fun addGrid(windowView:View,text:String,c:Int,r:Int,w:Int,bold:Boolean=false,winner:String=""){
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
        }

    // show game info window
    private fun displayGameInfo(){
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
    }

    //get card name
    private fun getCardName(card:String):String{
        val color=when(card[1]){
            'S' -> getString(R.string.spades)
            'H' -> getString(R.string.hearts)
            'D' -> getString(R.string.diamonds)
            'C' -> getString(R.string.clubs)
            else -> getString(R.string.joker)
        }
        return if(card=="XX") color else if(card[0]=='0') "10 $color" else "${card[0]} $color"
    }

    //*********** INFO

    //************ DISPLAY CARDS FUNCTIONS

    //show user cards in hand
    private fun showMyCards(){
        cards.removeAllViews()
        inCard=""
        //get cards in hand
        val myCards= gameSetData[keySetUser]?.get(0)!!.trim(',').split(",")
        cardsSpace=(cards.width-60.dp).div(myCards.count()+1)
        if(cardsSpace>30.dp)
            cardsSpace=30.dp
        //display each card
        for ((c,card) in myCards.withIndex())
            if(card!="")
                addMyCard(card, c)
        //get inCard if returning user to game
        if("2345".indexOf(gameData["moveStatus"].toString())!=-1){
            val cardsAux= gameSetData[keySetUser]!![0].trim(',').split(',')
            if(cardsAux.count()>0) {
                inCard = cardsAux[cardsAux.count() - 1]
            }
        }
    }
        //called by showMyCards to add each card
        private fun addMyCard(card:String, pos:Int){
            val cardImg=ImageView(requireContext())
            cardImg.setImageResource(resources.getIdentifier("d"+card.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
            val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.leftMargin=pos*cardsSpace
            params.topMargin=30.dp
            cardImg.layoutParams=params
            cards.addView(cardImg)
            cardImg.setOnClickListener { setOnHandListener(it, pos*cardsSpace, card) }
        }

    //show discarded cards in each position
    private fun showDiscards(){
        discard1.removeAllViews()
        discard2.removeAllViews()
        discard3.removeAllViews()
        discard4.removeAllViews()
        //preview listeners
        //the last active discard are the cards in discard1
        //find the last active discard
        var posDiscard=-1
        for(i in 0..4)
            if(players[i]!="")
                posDiscard=i
        previewDiscards(posDiscard,discard1)
        var myPos=players.indexOf(userId.toString())
        previewDiscards(myPos,discard2)
        myPos=nextPos(myPos,posDiscard,players.indexOf(userId.toString()))
        previewDiscards(myPos,discard3)
        myPos=nextPos(myPos,posDiscard,players.indexOf(userId.toString()))
        previewDiscards(myPos,discard4)
        nextPos(myPos,posDiscard,players.indexOf(userId.toString()))

        if(discardAngles.count()==0)
            for(i in 0..14)
                discardAngles.add((0..60).random().toFloat())
        //get discards
        var cp=0
        for( (c,discard) in gameData["current_discarded"].toString().split("|").withIndex()){
            if(discard!=""){
                var dd=discard.trim(',').split(',')
                if(dd.count()>3)
                    dd=dd.slice(dd.count()-3 until dd.count())
                for( card in dd){
                    displayCardInDiscard(c, card,cp)
                    cp++
                }
            }
        }
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
        private fun displayCardInDiscard(pos:Int, card:String, cp:Int){
            val cardImg=ImageView(requireContext())
            cardImg.setImageResource(resources.getIdentifier("d"+card.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
            val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.leftMargin=30.dp
            cardImg.layoutParams=params
            cardImg.rotation=discardAngles[cp]
            //find the last active discard
            var posDiscard=-1
            for(i in 0..4)
                if(players[i]!="")
                    posDiscard=i
            //user pick from discard1 , other discards follows
            when(pos){
                posDiscard -> discard1.addView(cardImg) // user discard
                0 -> discard2.addView(cardImg)
                1 -> discard3.addView(cardImg)
                2 -> discard4.addView(cardImg)
            }
        }

        //put preview listeners on discards
        private fun previewDiscards(posDiscard:Int, discard:RelativeLayout){
            discard.setOnLongClickListener{
                drawPreview.removeAllViews()
                drawPreview.isVisible=true
                val showCards=gameData["current_discarded"].toString().split("|")[posDiscard]
                MyTools().toast(requireContext(),getString(R.string.touch_preview_close))
                displayCardsInDraw(showCards, "", drawPreview, true)
                true
            }
        }

    //show each user drawn games
    private fun showDrawn(){
        draw1.removeAllViews()
        draw2.removeAllViews()
        draw3.removeAllViews()
        draw4.removeAllViews()
        draw5.removeAllViews()
        for((c,player) in players.withIndex()){
            val draw=when(c){
                0 -> draw1 // user discard
                1-> draw2
                2-> draw3
                3-> draw4
                else ->draw5
            }
            if(player!=""){
                val key="${gameData["current_set"]}_$player"
                //check if user has drawn
                if(gameSetData[key]!![1]!="")
                    displayCardsInDraw(gameSetData[key]!![1], player, draw)
            }
        }
    }
        //put each card in user draw area
        private fun displayCardsInDraw(drawn:String, playerId:String, draw:RelativeLayout, preview:Boolean=false){
            val games=drawn.split("|")
            val numCards=drawn.replace("|", "").trim(',').split(',').count()
            val cardW=(draw.width).div(numCards+(if(games.count()==2)10 else 15))-5
            //loop each game
            var p=0
            var space=0
            for((c,game) in games.withIndex()){
                //add each card
                for(card in game.trim(',').split(',')){
                    val cardImg=ImageView(requireContext())
                    cardImg.setImageResource(resources.getIdentifier("d"+card.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
                    val params = RelativeLayout.LayoutParams(cardW*5, LinearLayout.LayoutParams.WRAP_CONTENT)
                    //move right each card (dCardsSpace), plus space to next game
                    params.leftMargin=(p*cardW)+space
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
                space+=60.dp
            }
        }

    //show the selected draw in the preview layout
    private fun showDrawPreview(playerId: String){
        drawPreview.removeAllViews()
        drawPreview.isVisible=true
        val showCards=gameSetData["${gameData["current_set"]}_$playerId"]!![1]
        MyTools().toast(requireContext(),getString(R.string.touch_preview_close))
        displayCardsInDraw(showCards, playerId, drawPreview, true)
    }

        //raise a card in the hand
        private fun raiseCardFromHand(view: View, pos:Int){
            val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin= 0
            params.leftMargin=pos
            view.layoutParams=params
            //get raised card info
            cardViewPos = view
            cardPos=pos/cardsSpace
        }

        //lower a card in the hand
        private fun lowerCardFromHand(view: View, pos:Int){
            val params = RelativeLayout.LayoutParams(60.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin= 30.dp
            params.leftMargin=pos
            view.layoutParams=params
        }

        //lower a card in the hand
        private fun moveCard(pos:Int){
            val cards= arrayListOf<String>()
            val cardList=gameSetData[keySetUser]!![0].trim(',').split(",")
            for((c,card) in cardList.withIndex()){
                if(c!=pos && c!=cardPos) //add previous cards and rest of card, but moved card
                    cards.add(card)
                else if(c==pos){ // if position to move, add card
                    cards.add(card)
                    cards.add(cardList[cardPos])
                }
            }
            gameSetData[keySetUser]!![0]=cards.joinToString(",")+","
            showMyCards()
            doAsync {
                FetchData(arrayListOf(),this@GameFragment).updateData("cardsOrder", "",cache = false,
                    addParams = hashMapOf("gameId" to gameData["id"].toString(), "cards" to cards.joinToString(",")+","))
            }
        }

        //order cards, 0: same number, 1: same color
        private fun sortCards(color:Boolean=false){
            if(gameSetData[keySetUser]!![0]=="")
                return
            var cardList= gameSetData[keySetUser]!![0].trim(',').split(",").toMutableList()
            //fix to keep numeric sort
            cardList=fixSort(cardList,true,color)
            var cards= cardList.sorted() as MutableList<String>
            cards=fixSort(cards,false,color)
            gameSetData[keySetUser]!![0]=cards.joinToString(",")+","
            showMyCards()
            MyTools().toast(requireContext(),getString(if(color)R.string.sort_color else R.string.sort_number))
            doAsync {
                FetchData(arrayListOf(),this@GameFragment).updateData("cardsOrder", "",cache = false,
                    addParams = hashMapOf("gameId" to gameData["id"].toString(), "cards" to cards.joinToString(",")+","))
            }
        }
        // fixes for sorting depending on numbers or color
        private fun fixSort(data:MutableList<String>, start:Boolean, color:Boolean):MutableList<String>{
            //first change to fix numeric sort
            if(start)
                for (i in 0 until data.count())
                    data[i]=when(data[i][0]){
                        'A' -> "Y"+data[i][1]
                        'K' -> "R"+data[i][1]
                        '0' -> "B"+data[i][1]
                        'X' -> "Z"+data[i][1]
                        else -> data[i]
                    }
            //if sorting by color put colors first, or restore back
            if(color)
                for (i in 0 until data.count())
                    data[i] = data[i][1].toString() + data[i][0].toString()
            if(!start)
                for (i in 0 until data.count())
                    data[i]=when(data[i][0]){
                        'Y' -> "A"+data[i][1]
                        'R' -> "K"+data[i][1]
                        'B' -> "0"+data[i][1]
                        'Z' -> "X"+data[i][1]
                        else -> data[i]
                    }
            return data
        }

        //animate options menu
        private fun animateMenu(){
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
        }

        // show/hide the message view
        private fun switchMessageView(){
            val newVal=!message_layout.isVisible
            message_layout.isVisible=newVal
            move_message.isVisible=newVal
            requireContext().getSharedPreferences(PREF_FILE, 0).edit().putString("MESSAGES", if(message_layout.isVisible) "ON" else "").apply()
            MyTools().toast(requireContext(),getString(if(newVal)R.string.message_visible else R.string.message_hidden))
        }

        // show/hide the flow message view
        private fun switchFlowMessageView(){
            val newVal=if(flowAuto=="ON") "" else "ON"
            flowAuto=newVal
            requireContext().getSharedPreferences(PREF_FILE, 0).edit().putString("MESSAGES_FLOW", newVal).apply()
            MyTools().toast(requireContext(),getString(if(newVal=="ON")R.string.flow_visible else R.string.flow_auto_hide))
            if(text_game_flow.text!="")
                text_game_flow.isVisible=newVal=="ON"
        }

    // ************ END DISPLAY CARDS FUNCTIONS

    // ************ MOVE CARDS FUNCTIONS

    //drag a card from one view to another
    private fun dragCard(moveView:View, fromView:View, toView:View, code: ()->Unit = {}){
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
            }
        })
    }
    //allow to move view outside the parents boundaries
    private fun setAllParentsClip(viewIn: View) {
        var view = viewIn
        while (view.parent != null && view.parent is ViewGroup) {
            val viewGroup = view.parent as ViewGroup
            viewGroup.clipChildren = false
            viewGroup.clipToPadding = false
            view = viewGroup
        }
    }
    //call dragCard from deck to preview card
    private fun moveCardFromStack(){
        sendFlow("${playersNames[0]}: ${getString(R.string.picked_card_stack)}")
        updateCards=true
        dragCard(moveCardFromStack,deck,moveCardFromStack1){
            moveCardFromStack.isVisible=false
            moveCardFromStack1.isVisible=true
            moveCardFromStack1.setImageResource(resources.getIdentifier("d"+inCard.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
            doAsync {
                sleep(1000)
                uiThread {
                    addMyCard(inCard,cards.childCount+1)
                    moveCardFromStack1.isVisible=false
                    initialView()
                }
            }
        }
    }

    //call dragCard from discard1 to last cards
    private fun moveCardFromDiscard(){
        updateCards=true
        sendFlow("${playersNames[0]}: ${getString(R.string.picked_card_discard)}: ${getCardName(inCard)}")
        moveCardFromDiscard.setImageResource(resources.getIdentifier("d"+inCard.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
        dragCard(moveCardFromDiscard,discard1,moveCardFromStack1){
            moveCardFromDiscard.isVisible=false
            moveCardFromStack1.isVisible=true
            moveCardFromStack1.setImageResource(resources.getIdentifier("d"+inCard.toLowerCase(Locale.ROOT),"drawable",requireContext().packageName))
            doAsync {
                sleep(1000)
                uiThread {
                    addMyCard(inCard,cards.childCount+1)
                    moveCardFromStack1.isVisible=false
                    initialView()
                }
            }
        }
    }

    //call dragCard from raised card to discard 2
    private fun moveCardToDiscard(){
        updateCards=true
        sendFlow("${playersNames[0]}: ${getString(R.string.discarded_card)}: ${getCardName(outCard)}")
        dragCard(cardViewPos,cardViewPos,discard2){
            cardViewPos.isVisible=false
            moveCardFromStack1.isVisible=false
            inCard = ""
            outCard = ""
        }
        initialView()
    }

    //call dragCard from raised card to drawn
    private fun moveCardToDraw(view:View){
        updateCards=true
        sendFlow("${playersNames[0]}: ${getString(R.string.draw_over_card)}: ${getCardName(outCard)}")
        dragCard(cardViewPos,cardViewPos,view){
            cardViewPos.isVisible=false
            moveCardFromStack1.isVisible=false
            outCard = ""
        }
        initialView()
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
        //start moving messages view
        move_message.setOnClickListener { activateMove() }
        //listener for dragging the messages view
        dragMessagesListener()
        //hide preview panel when clicked
        drawPreview.setOnClickListener { drawPreview.isVisible=false }

        text_game_flow.movementMethod = ScrollingMovementMethod()
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
                    val res = result.split("|")
                    var msg=result
                    if(res.count()==2){
                        msg=res[1]
                        moveCardToDraw(view)
                    }
                    MyTools().toast(requireContext(),msg)
                    loadingGame.isVisible=false
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
                    mainButton.isVisible=false
                    mainButton.text=getString(R.string.draw)
                    drawCards="-1"
                    text_draw_info.removeAllViews()
                    addGameDrawButton.text=getString(R.string.add_draw_game)
                    showMyCards()
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
                    FetchData(arrayListOf(),this).updateData("startGame", "",cache = false, addParams = hashMapOf("gameId" to gameData["id"].toString())) {
                            result ->
                        val res = result.split("|")
                        var msg=result
                        if(res.count()==2){
                            msg=res[1]
                            text_game_info.text = getString(R.string.loading)
                            mainButton.isVisible=false
                            updateCards=true
                            initialView()
                            sendFlow("${playersNames[0]}: ${getString(R.string.start_game)}")
                        }
                        MyTools().toast(requireContext(),msg)
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
            FetchData(arrayListOf(),this).updateData("dealCards", "",cache = false, addParams = hashMapOf("gameId" to gameData["id"].toString())) {
                    result ->
                val res = result.split("|")
                var msg=result
                if(res.count()==2){
                    msg=res[1]
                    text_game_info.text = getString(R.string.loading)
                    mainButton.isVisible=false
                    updateCards=true
                    initialView()
                    sendFlow("${playersNames[0]}: ${getString(R.string.deal_cards)}")
                }
                MyTools().toast(requireContext(),msg)
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
                    FetchData(arrayListOf(),this).updateData("pickCard", "",cache = false, addParams = hashMapOf("gameId" to gameData["id"].toString(), "stack" to "1")) { result ->
                        val res = result.split("|")
                        var msg=result
                        if(res.count()==2){
                            msg=res[1]
                            inCard=res[1]
                            moveCardFromStack()
                        }
                        MyTools().toast(requireContext(),msg)
                        loadingGame.isVisible=false
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
                   FetchData(arrayListOf(),this).updateData("pickCard", "",cache = false, addParams = hashMapOf("gameId" to gameData["id"].toString(), "discard" to "1")) { result ->
                        val res = result.split("|")
                       var msg=result
                       if(res.count()==2){
                           msg=res[1]
                            inCard=res[1]
                            moveCardFromDiscard()
                        }
                       MyTools().toast(requireContext(),msg)
                       loadingGame.isVisible=false
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
                    //discard card
                    FetchData(arrayListOf(), this).updateData(
                        "discardCard",
                        "",
                        cache = false,
                        addParams = hashMapOf(
                            "gameId" to gameData["id"].toString(),
                            "out" to outCard
                        )
                    ) { result ->
                        val res = result.split("|")

                        var msg=result
                        if(res.count()==2){
                            msg=res[1]
                            moveCardToDiscard()
                        }
                        MyTools().toast(requireContext(),msg)
                        loadingGame.isVisible=false
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
        buttonLauncherStanding.setOnClickListener { displaySetSummary() }
        //show info view
        buttonLauncherInfo.setOnClickListener { displayGameInfo() }
        //show hide messages
        buttonLauncherChat.setOnClickListener { switchMessageView() }
        //show or auto hide flow messages
        buttonLauncherFlow.setOnClickListener { switchFlowMessageView() }
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

    //check number of game for the current set and add it or show the confirm botton to make the draw
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
            loadingGame.isVisible=true
            //confirm and draw game
            FetchData(arrayListOf(),this).updateData("draw", "",cache = false,
                addParams = hashMapOf("gameId" to gameData["id"].toString(), "drawCards" to drawCards.trim('|'))) {
                    result ->
                val res = result.split("|")
                var msg=result
                if(res.count()==2){
                    msg=res[1]
                    draw_info_layout.isVisible=false
                    mainButton.isVisible=false
                    mainButton.text=getString(R.string.draw)
                    drawCards="-1"
                    text_draw_info.removeAllViews()
                    addGameDrawButton.text=getString(R.string.add_draw_game)
                    updateCards=true
                    initialView()
                    sendFlow("${playersNames[0]}: ${getString(R.string.draw)}")
                }
                MyTools().toast(requireContext(),msg)
                loadingGame.isVisible=false
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

    private fun activateMove(){
        moving = !moving
        if(moving)
            move_message.setImageResource(R.drawable.ic_baseline_check_box_24)
        else
            move_message.setImageResource(R.drawable.ic_move)

    }
    @SuppressLint("ClickableViewAccessibility")
    private fun dragMessagesListener(){
        val listener = OnTouchListener(function = { _, motionEvent ->
            if(moving){
                if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                    message_layout.y = motionEvent.rawY - message_layout.height/2
                    message_layout.x = motionEvent.rawX - message_layout.width/2
                    move_message.y = motionEvent.rawY - message_layout.height/2
                    move_message.x = motionEvent.rawX + message_layout.width/2
                }
                true
            }else{
                false
            }
        })
        message_layout.setOnTouchListener(listener)
        message_scroll.setOnTouchListener(listener)
    }

    // *********** END LISTENERS FUNCTIONS

    // *********** MESSAGES

    //get message from db when first load
    private fun getStoredMessages(getId:Boolean=false){
        val lastId=dbHandler.getData("messages", "`gameId_id`=$gameId", "max(`id`)")
        messagesLastId=try{
            Integer.parseInt(lastId[0][0])
        }catch(ex:java.lang.NumberFormatException){ 0 }
        if(getId)
            return
        for(message in dbHandler.getData("messages", "`gameId_id`=$gameId")){
            messages_input.append(putMsg(message[1], message[2], message[3]))
        }
    }

    //get flow message from db when first load
    private fun getStoredFlow(getId:Boolean=false){
        val lastId=dbHandler.getData("flow", "`gameId_id`=$gameId", "max(`id`)")
        flowLastId=try{
            Integer.parseInt(lastId[0][0])
        }catch(ex:java.lang.NumberFormatException){ 0 }
        if(getId)
            return
        for(message in dbHandler.getData("flow", "`gameId_id`=$gameId")){
            text_game_flow.append(putMsg(message[1], message[2]))
        }
        scrollTVDown()
        if(text_game_flow.text!="")
            text_game_flow.isVisible=true
        if(flowAuto=="")
            doAsync {
                sleep(10000)
                uiThread {
                    try {
                        text_game_flow.isVisible = false
                    }catch (ex:IllegalStateException){}
                }
            }
    }

    private fun scrollTVDown(){
        try{
            val scrollAmount = text_game_flow.layout.getLineTop(text_game_flow.lineCount) - text_game_flow.height
            if (scrollAmount > 0)
                text_game_flow.scrollTo(0, scrollAmount)
            else
                text_game_flow.scrollTo(0, 0)
        }catch (ex:Exception){}
    }

    //put a message in the messages area
    @SuppressLint("SimpleDateFormat")
    private fun putMsg(msg:String, date:String, userId:String=""):SpannableString{
        return try{
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val formatter = SimpleDateFormat("HH:mm:ss")
            val user = if(userId!="")playersNames[players.indexOf(userId)] else ""
            val ss1 = SpannableString("$user ${formatter.format(parser.parse(date)!!)}: $msg\n")
            ss1.setSpan(RelativeSizeSpan(0.5f), user.count(), user.count()+9, 0) // set size
            ss1.setSpan(ForegroundColorSpan(Color.LTGRAY), user.count(), user.count()+9, 0) // set color
            ss1
        }catch (ex:Exception){
            SpannableString(msg)
        }
    }

    //add new message in the server
    private fun sendMessage(){
        if(messages_text.text.toString().count()>0){
            FetchData(arrayListOf("id", "msg", "date", "gameId_id", "userId_id"),this).updateData("addMessage", "", cache=false,
                addParams = hashMapOf("gameId" to gameId, "msg" to messages_text.text.toString()))
            messages_text.setText("")
        }
    }

    //add new message in the server
    private fun sendFlow(msg:String){
        FetchData(arrayListOf("id", "msg", "date", "gameId_id"),this).updateData("addToFlow", "", cache=false,
            addParams = hashMapOf("gameId" to gameId, "msg" to msg))
    }

    private fun playNotification(){
        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(requireContext().applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // *********** END MESSAGES
}
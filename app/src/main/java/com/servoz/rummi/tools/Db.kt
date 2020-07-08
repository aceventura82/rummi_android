package com.servoz.rummi.tools

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.*
import kotlin.collections.ArrayList

class Db(context: Context, factory: SQLiteDatabase.CursorFactory?) : SQLiteOpenHelper(context, DB_NAME, factory, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // println("DEBUG:Creating DBS")
        db.execSQL("CREATE TABLE `game`( `id` INTEGER PRIMARY KEY, `name` TEXT, `date` TEXT, `private` INTEGER, `started` INTEGER, `fullDraw` TEXT, `speed` INTEGER, `maxPlayers` INTEGER,  `code` TEXT, `current_set` INTEGER, `current_stack` TEXT, `current_discarded` TEXT, `userId_id` INTEGER, `playersPos` INTEGER, `currentPlayerPos` INTEGER)")
        db.execSQL("CREATE TABLE `gameSet`( `id` INTEGER PRIMARY KEY AUTOINCREMENT, `set_id` INTEGER, `set_set` INTEGER, `set_date` TEXT, `set_fullDraw` INTEGER, `set_points` INTEGER, `set_userId_id` INTEGER, `set_gameId_id` INTEGER, `set_current_cards` TEXT, `set_drawn` TEXT)")
        db.execSQL("CREATE TABLE `player`( `id` INTEGER PRIMARY KEY, `name` TEXT, `userId_id` INTEGER, `extension` TEXT)")
        db.execSQL("CREATE TABLE `messages`( `id` INTEGER PRIMARY KEY, `msg` TEXT, `date` TEXT, `userId_id` INTEGER, `gameId_id` INTEGER)")
        db.execSQL("CREATE TABLE `flow`( `id` INTEGER PRIMARY KEY, `msg` TEXT, `date` TEXT, `gameId_id` INTEGER)")
        db.execSQL("CREATE TABLE `remote_data`( `gameId_id` INTEGER PRIMARY KEY, `date` TEXT)")
    }


    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //when upgrading
    }

    /*fun dbUpd(){
        //Db(context, null).dbUpd()
        val db = this.writableDatabase
//        db.execSQL("DROP TABLE `matchGameDetails`")
//        db.execSQL("CREATE TABLE `matchGameDetails`( `matchGameId` INTEGER, `legend` TEXT, `value` TEXT)")
        db.close()
    }*/

    fun addData(table:String, data:HashMap<String, String>) {
        // println("DEBUG:DB ADD $table")
        val values = ContentValues()
        for(dat in data){
            values.put(dat.key, dat.value)
        }
        val db = this.writableDatabase
        db.insert(table, null, values)
        db.close()
    }

    fun getData(table: String, where:String="",fields:String="*",groupBy:String="",sortBy:String="", limit:Int=0):ArrayList<ArrayList<String>>{
        // println("DEBUG:DB get data $table $where")
        val query = "SELECT $fields FROM $table " +
                if(where !="") "WHERE $where" else "" +
                if(limit >0) "LIMIT $limit" else "" +
                if(groupBy !="") "GROUP BY $groupBy" else "" +
                if(sortBy !="") "ORDER BY $sortBy" else ""
        val db = this.writableDatabase
        val cursor = db.rawQuery(query, null)
        val rows = arrayListOf<ArrayList<String>>()
        while (cursor.moveToNext()){
            val row= arrayListOf<String>()
            for(c in 0 until cursor.columnCount){
                row.add(cursor.getString(c))
            }
            rows.add(row)
        }
        cursor.close()
        db.close()
        return rows
    }

    fun getMessages(gameId:String=""):ArrayList<ArrayList<String>>{
        val query = "SELECT * FROM (SELECT msg,date,userId_id, '' FROM messages WHERE gameId_id=$gameId UNION ALL SELECT msg,date, '', 'FLOW' from flow  WHERE gameId_id=$gameId)A ORDER BY date ASC"
        val db = this.writableDatabase
        val cursor = db.rawQuery(query, null)
        val rows = arrayListOf<ArrayList<String>>()
        while (cursor.moveToNext()){
            val row= arrayListOf<String>()
            for(c in 0 until cursor.columnCount){
                row.add(cursor.getString(c))
            }
            rows.add(row)
        }
        cursor.close()
        db.close()
        return rows
    }

    /*fun deleteById(table:String,id: Int): Boolean {
        val query = "SELECT * FROM $table WHERE id = $id"
        val db = this.writableDatabase
        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            db.delete(table, "id = ?", arrayOf(id.toString()))
            cursor.close()
        }
        if(cursor.moveToFirst())
            return true
        db.close()
        return false
    }*/

    fun deleteWhere(table:String,where: String=""):Boolean {
        // println("DEBUG:DB Delete $table $where")
        val query = "SELECT * FROM $table " +
                if(where !="") "WHERE $where" else ""
        val db = this.writableDatabase
        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            db.delete(table, where, arrayOf())
            cursor.close()
            db.close()
            return true
        }
        db.close()
        return false
    }

    fun editData(table: String, where:String="",data:HashMap<String, String>):Boolean{
        // println("DEBUG:DB Edit $table $where")
        val query = "SELECT * FROM $table " +
                if(where !="") "WHERE $where" else ""
        val db = this.writableDatabase
        val cursor = db.rawQuery(query, null)
        val values = ContentValues()
        for(dat in data){
            values.put(dat.key, dat.value)
        }
        var result = false
        if (cursor.moveToFirst()) {
            db.update(table, values,where, arrayOf())
            cursor.close()
            result = true
        }
        db.close()
        return result
    }
}
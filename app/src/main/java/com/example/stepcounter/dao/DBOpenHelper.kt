package com.fyspring.stepcounter.dao

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase



/**
 * Created by fySpring
 * Date: 2020/4/21
 * To do:
 */
class DBOpenHelper(mContext :Context) :SQLiteOpenHelper(mContext, "StepCounter.db", null,1) {
    private val DB_NAME = "StepCounter.db"
    private val DB_VERSION = 1

    //用于创建Banner表
    private val CREATE_BANNER = ("create table step ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "curDate TEXT, "
            + "totalSteps TEXT)")


    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_BANNER)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }
}
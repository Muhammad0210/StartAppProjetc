package com.fyspring.stepcounter.dao

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fyspring.stepcounter.bean.StepEntity


/**
 * Created by fySpring
 * Date: 2020/4/21
 * To do:
 */
class StepDataDao(mContext: Context) {
    private var stepHelper: DBOpenHelper = DBOpenHelper(mContext)
    private var stepDb: SQLiteDatabase? = null

    fun addNewData(stepEntity: StepEntity) {
        stepDb = stepHelper.readableDatabase

        val values = ContentValues()
        values.put("curDate", stepEntity.curDate)
        values.put("totalSteps", stepEntity.steps)
        stepDb!!.insert("step", null, values)
        stepDb!!.close()
    }
    fun getCurDataByDate(curDate: String): StepEntity? {
        stepDb = stepHelper.readableDatabase
        var stepEntity: StepEntity? = null
        val cursor = stepDb!!.query("step", null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val date = cursor.getString(cursor.getColumnIndexOrThrow("curDate"))
            if (curDate == date) {
                val steps = cursor.getString(cursor.getColumnIndexOrThrow("totalSteps"))
                stepEntity = StepEntity(date, steps)
                break
            }
        }
        stepDb!!.close()
        cursor.close()
        return stepEntity
    }
    @SuppressLint("Range")
    fun getAllDatas(): List<StepEntity> {
        val dataList: MutableList<StepEntity> = ArrayList()
        stepDb = stepHelper.readableDatabase
        val cursor = stepDb!!.rawQuery("select * from step", null)

        while (cursor.moveToNext()) {
            val curDate = cursor.getString(cursor.getColumnIndex("curDate"))
            val totalSteps = cursor.getString(cursor.getColumnIndex("totalSteps"))
            val entity = StepEntity(curDate, totalSteps)
            dataList.add(entity)
        }

        stepDb!!.close()
        cursor.close()
        return dataList
    }
    fun updateCurData(stepEntity: StepEntity) {
        stepDb = stepHelper.readableDatabase

        val values = ContentValues()
        values.put("curDate", stepEntity.curDate)
        values.put("totalSteps", stepEntity.steps)
        stepDb!!.update("step", values, "curDate=?", arrayOf(stepEntity.curDate))

        stepDb!!.close()
    }

    fun deleteCurData(curDate: String) {
        stepDb = stepHelper.readableDatabase

        if (stepDb!!.isOpen)
            stepDb!!.delete("step", "curDate", arrayOf(curDate))
        stepDb!!.close()
    }
}
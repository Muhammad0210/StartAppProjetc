package com.example.stepcounter.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.example.stepcounter.R
import com.example.stepcounter.ui.activities.MainActivity
import com.fyspring.stepcounter.bean.StepEntity
import com.fyspring.stepcounter.constant.ConstantData
import com.fyspring.stepcounter.dao.StepDataDao
import com.fyspring.stepcounter.utils.TimeUtil
import java.text.SimpleDateFormat
import java.util.Date

class StepService : Service(), SensorEventListener {
    private var currentDate: String? = null
    private var currentStep: Int = 0
    private var sensorManager: SensorManager? = null
    private var stepDataDao: StepDataDao? = null
    private var stepSensor = -1
    private var mInfoReceiver: BroadcastReceiver? = null
    private val messenger = Messenger(MessengerHandler())
    private var hasRecord: Boolean = false
    private var hasStepCount: Int = 0
    private var previousStepCount: Int = 0
    private var builder: Notification.Builder? = null

    private var notificationManager: NotificationManager? = null
    private var nfIntent: Intent? = null


    override fun onCreate() {
        super.onCreate()
        initBroadcastReceiver()
        Thread(Runnable { getStepDetector() }).start()
        initTodayData()
    }

    override fun onBind(intent: Intent): IBinder? {
        return messenger.binder
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = Notification.Builder(this.applicationContext, ConstantData.CHANNEL_ID)
            val notificationChannel =
                NotificationChannel(
                    ConstantData.CHANNEL_ID,
                    ConstantData.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_MIN
                )
            notificationChannel.enableLights(false)
            notificationChannel.setShowBadge(false)
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            notificationManager?.createNotificationChannel(notificationChannel)
            builder?.setChannelId(ConstantData.CHANNEL_ID)
        } else {
            builder = Notification.Builder(this.applicationContext)
        }

        nfIntent = Intent(this, MainActivity::class.java)
        setStepBuilder()
        startForeground(ConstantData.NOTIFY_ID, builder?.build())
        return START_STICKY
    }
    private inner class MessengerHandler : Handler() {
        override fun handleMessage(msg: Message) = when (msg.what) {
            ConstantData.MSG_FROM_CLIENT -> try {
                val messenger = msg.replyTo
                val replyMsg = Message.obtain(null, ConstantData.MSG_FROM_SERVER)
                val bundle = Bundle()
                bundle.putInt("steps", currentStep)
                replyMsg.data = bundle
                messenger.send(replyMsg)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            else -> super.handleMessage(msg)
        }
    }
    private fun initBroadcastReceiver() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SHUTDOWN)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        filter.addAction(Intent.ACTION_DATE_CHANGED)
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIME_TICK)

        mInfoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> saveStepData()
                    Intent.ACTION_SHUTDOWN -> saveStepData()
                    Intent.ACTION_USER_PRESENT -> saveStepData()
                    Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> saveStepData()
                    Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIME_TICK -> {
                        saveStepData()
                        isNewDay()
                    }
                }
            }
        }
        registerReceiver(mInfoReceiver, filter)
    }
    private fun initTodayData() {
        currentDate = TimeUtil.getCurrentDate()
        stepDataDao = StepDataDao(applicationContext)
        val entity = stepDataDao!!.getCurDataByDate(currentDate!!)
        currentStep = if (entity == null) 0 else Integer.parseInt(entity.steps!!)
    }

    private fun isNewDay() {
        val time = "00:00"
        if (time == SimpleDateFormat("HH:mm").format(Date()) || currentDate != TimeUtil.getCurrentDate()) {
            initTodayData()
        }
    }
    private fun getStepDetector() {
        if (sensorManager != null) {
            sensorManager = null
        }
        sensorManager = this
            .getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (Build.VERSION.SDK_INT >= 19) {
            addCountStepListener()
        }
    }

    private fun addCountStepListener() {
        val countSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val detectorSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (countSensor != null) {
            stepSensor = 0
            sensorManager!!.registerListener(
                this@StepService,
                countSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } else if (detectorSensor != null) {
            stepSensor = 1
            sensorManager!!.registerListener(
                this@StepService,
                detectorSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (stepSensor == 0) {
            val tempStep = event.values[0].toInt()
            if (!hasRecord) {
                hasRecord = true
                hasStepCount = tempStep
            } else {
                val thisStepCount = tempStep - hasStepCount
                currentStep += thisStepCount - previousStepCount
                previousStepCount = thisStepCount
            }
            saveStepData()
        } else if (stepSensor == 1) {
            if (event.values[0].toDouble() == 1.0) {
                currentStep++
                saveStepData()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {

    }

    private fun saveStepData() {
        var entity = stepDataDao?.getCurDataByDate(currentDate!!)
        if (entity == null) {
            entity = StepEntity()
            entity.curDate = currentDate
            entity.steps = currentStep.toString()

            stepDataDao?.addNewData(entity)
        } else {
            entity.steps = currentStep.toString()

            stepDataDao?.updateCurData(entity)
        }
        setStepBuilder()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun setStepBuilder() {
        builder?.setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                nfIntent,
                0
            )
        )
            ?.setLargeIcon(
                BitmapFactory.decodeResource(
                    this.resources,
                    R.mipmap.ic_launcher
                )
            )
            ?.setContentTitle("number of steps today" + currentStep + "walk")
            ?.setSmallIcon(R.mipmap.ic_launcher)
                ?.setContentText("Add oil, need to be noted")
        val stepNotification = builder?.build()
        notificationManager?.notify(ConstantData.NOTIFY_ID, stepNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        unregisterReceiver(mInfoReceiver)
    }

    override fun onUnbind(intent: Intent): Boolean {
        return super.onUnbind(intent)
    }
}
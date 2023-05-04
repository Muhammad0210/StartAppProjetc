package com.example.stepcounter.ui.activities

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.stepcounter.R
import com.example.stepcounter.databinding.ActivityMainBinding
import com.example.stepcounter.service.StepService
import com.fyspring.stepcounter.base.BaseActivity
import com.fyspring.stepcounter.bean.StepEntity
import com.fyspring.stepcounter.constant.ConstantData
import com.fyspring.stepcounter.dao.StepDataDao
import com.fyspring.stepcounter.ui.view.BeforeOrAfterCalendarView
import com.fyspring.stepcounter.utils.StepCountCheckUtil
import com.fyspring.stepcounter.utils.TimeUtil
import java.text.DecimalFormat
import java.util.Timer
import java.util.TimerTask

class MainActivity : BaseActivity(), Handler.Callback {
    private var calenderView: BeforeOrAfterCalendarView? = null
    private var curSelDate: String = ""
    private val df = DecimalFormat("#.##")
    private val stepEntityList: MutableList<StepEntity> = ArrayList()
    private var stepDataDao: StepDataDao? = null
    private var isBind = false
    private val mGetReplyMessenger = Messenger(Handler(this))
    private var messenger: Messenger? = null
    private var timerTask: TimerTask? = null
    private var timer: Timer? = null
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_main
    }

    override fun initData() {
        curSelDate = TimeUtil.getCurrentDate()
        calenderView = BeforeOrAfterCalendarView(this)
        binding.movementRecordsCalenderLl.addView(calenderView)
        requestPermission()
    }

    override fun initListener() {
        calenderView!!.setOnBoaCalenderClickListener(object :
            BeforeOrAfterCalendarView.BoaCalenderClickListener {
            override fun onClickToRefresh(position: Int, curDate: String) {
                curSelDate = curDate
                setDatas()
            }
        })
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    1
                )
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    )
                ) {
                    Toast.makeText(this, "Please take a look at the health and exercise information, so you can't walk", Toast.LENGTH_SHORT).show()
                }
            } else {
                startStepService()
            }
        } else {
            startStepService()
        }
    }


    private fun startStepService() {
        if (StepCountCheckUtil.isSupportStepCountSensor(this)) {
            getRecordList()
            binding.isSupportTv.visibility = View.GONE
            setDatas()
            setupService()
        } else {
            binding.movementTotalStepsTv.text = "0"
            binding.isSupportTv.visibility = View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            for (i in permissions.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    startStepService()
                } else {
                    Toast.makeText(this, "Please take a look at the health and exercise information, so you can't walk", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun setupService() {
        val intent = Intent(this, StepService::class.java)
        isBind = bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            timerTask = object : TimerTask() {
                override fun run() {
                    try {
                        messenger = Messenger(service)
                        val msg = Message.obtain(null, ConstantData.MSG_FROM_CLIENT)
                        msg.replyTo = mGetReplyMessenger
                        messenger!!.send(msg)
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
            }
            timer = Timer()
            timer!!.schedule(timerTask, 0, 500)
        }
        override fun onServiceDisconnected(name: ComponentName) {

        }
    }
    private fun setDatas() {
        val stepEntity = stepDataDao!!.getCurDataByDate(curSelDate)

        if (stepEntity != null) {
            val steps = stepEntity.steps?.let { Integer.parseInt(it) }
            binding.movementTotalStepsTv.text = steps.toString()
            binding.movementTotalKmTv.text = steps?.let { countTotalKM(it) }
        } else {
            binding.movementTotalStepsTv.text = "0"
            binding.movementTotalKmTv.text = "0"
        }
        val time = TimeUtil.getWeekStr(curSelDate)
        binding.movementTotalKmTimeTv.text = time
        binding.movementTotalStepsTimeTv.text = time
    }

    private fun countTotalKM(steps: Int): String {
        val totalMeters = steps * 0.7
        return df.format(totalMeters / 1000)
    }
    private fun getRecordList() {
        stepDataDao = StepDataDao(this)
        stepEntityList.clear()
        stepEntityList.addAll(stepDataDao!!.getAllDatas())
        if (stepEntityList.size > 7) {
            for (entity in stepEntityList) {
                if (TimeUtil.isDateOutDate(entity.curDate!!)) {
                    stepDataDao?.deleteCurData(entity.curDate!!)
                }
            }
        }
    }


    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            ConstantData.MSG_FROM_SERVER ->
                if (curSelDate == TimeUtil.getCurrentDate()) {
                    val steps = msg.data.getInt("steps")
                    binding.movementTotalStepsTv.text = steps.toString()
                    binding.movementTotalKmTv.text = countTotalKM(steps)
                }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBind) this.unbindService(conn)
    }

}
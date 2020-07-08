package com.llx278.mydemo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import com.llx278.remoter.IReceiver
import com.llx278.remoter.IRoute
import com.llx278.remoter.TestService
import kotlinx.android.synthetic.main.activity_main.*

class NextActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_next)
        binder.setOnClickListener {
            bindService(Intent(it.context, TestService::class.java),object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName?) {
                }

                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val route = IRoute.Stub.asInterface(service)
                    route.setReceiver(object :IReceiver.Stub() {
                        override fun onReceive(from: String?, json: String?) {
                        }
                    })
                    //route.send(Process.myPid().toString(),"","test str...")
                }

            }, Context.BIND_AUTO_CREATE)
        }

    }
}
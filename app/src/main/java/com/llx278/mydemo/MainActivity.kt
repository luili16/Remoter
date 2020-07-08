package com.llx278.mydemo

import android.content.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import com.llx278.remoter.TestService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binder.setOnClickListener {
            bindService(Intent(it.context,TestService::class.java),object: ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName) {
                }

                override fun onServiceConnected(name: ComponentName, service: IBinder) {

                    //route.send0("abc","def","hello world".toByteArray())
                }

            },Context.BIND_AUTO_CREATE)
        }
        next.setOnClickListener {
            startActivity(Intent(it.context,NextActivity::class.java))
        }
    }
}
package com.llx278.remoter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log

object Remoter {

    fun globalInit(context: Context,interfaces:Int) {

    }

}


interface IProvider {

}

interface ILogger {
    fun d(tag:String?=null,msg:String)
    fun e(tag:String?=null,msg:String,cause:Throwable? = null)
}

object Logger : ILogger {
    val TAG = "Remoter"
    var logger:ILogger? = null

    override fun d(tag: String?, msg: String) {
        if (logger != null) {
            logger!!.d(tag?: TAG,msg)
        } else {
            Log.d(tag?: TAG,msg)
        }
    }

    override fun e(tag: String?, msg: String, cause: Throwable?) {
        if (logger != null) {
            logger!!.e(tag?: TAG,msg,cause)
        } else {
            Log.d(tag?: TAG,msg)
        }
    }
}
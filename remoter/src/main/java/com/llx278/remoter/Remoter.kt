package com.llx278.remoter

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class Remoter {



}

internal class DynamicProxyProcessor: IRemoteRequestProcessor {
    override fun process(body: RpcProtocol.BodyWrapper, command: InvokeCommand) {

    }
}

class RemoteMethodInternalError(message: String):Exception(message)
class RemoteProtocolError(message: String,cause:Throwable? = null):Exception(message,cause)

class ReturnValue<R>: RemoteResponse {
    private var callback:Callback<R>? = null
    private val lock:Any = Any()
    private var returnValue: R? = null
    fun getAsync(callback:Callback<R>) {
        synchronized(lock) {
            this.callback = callback
        }
    }

    fun getSync(timeout:Long = 0L): R {
        synchronized(lock) {

        }
        TODO()
    }

    override fun onResponse(from: Int, response: ByteArray, offset: Int, length: Int) {
        returnValue = synchronized(lock) {
            // we know response is json string
            try {
                val gson = Gson()
                val jsonObj = String(response)
                val remoteResponse = gson.fromJson(jsonObj,Response::class.java)
                if (remoteResponse.code != 200) {
                    this.callback?.onError(RemoteMethodInternalError(remoteResponse.message))
                    null
                } else {
                    val arg = gson.fromJson(remoteResponse.data,Arg::class.java)
                    val cls : Class<*> = Class.forName(arg.transformerCls)
                    val transformer = cls.newInstance() as Transformer<*>
                    val r = transformer.from(Base64.decode(arg.encodedValue.toByteArray(),Base64.NO_WRAP)) as R
                    r
                }
            } catch (e: JsonSyntaxException) {
                callback?.onError(RemoteProtocolError("parse raw bytes to json fail.",cause = e))
                null
            } catch (e:ClassCastException) {
                null
            }
        }
    }

    override fun onError(from: Int, cause: RPCException) {
        synchronized(lock) {

        }
    }
}

interface Callback<T> {
    fun onSuccess(data:T)
    fun onError(cause:Throwable)
}

/**
 *
 */
interface IParam {
    fun toByteArray(): ByteArray
}

@Keep
data class Request(
    val className:String,
    val methodName:String,
    val args:List<Arg>?
)

@Keep
data class Arg(
    val encodedValue:String,
    val transformerCls:String
)

data class Response(
    val code:Int,
    val message:String,
    val data: String
)

class RemoteInterface<I>(private val rpc:IRpc) {


    fun proxy(context: Context, cls: Class<I>): I {
        checkCls(cls)
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(cls.classLoader,Array(1){cls},object :InvocationHandler {
            override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any {
                val className = cls.name
                val methodName = method!!.name
                val argsList = createArgs(args)
                val request = Request(
                    className,
                    methodName,
                    argsList
                )
                val body = Gson().toJson(request).toByteArray()
                val value = ReturnValue<Any>()
                val to = queryAddress(cls)
                rpc.call(context,to,body,value)
                return value
            }
        }) as I
    }

    private fun queryAddress(cls: Class<I>): Int {
        return 1
    }

    private fun createArgs(args: Array<out Any>?): List<Arg>? {
        if (args == null) {
            return null
        }
        val argList: MutableList<Arg> = mutableListOf()
        for (a in args) {
            if (a is Int) {
                val encodeValue = String(Base64.encode(IntTransformer().to(a),Base64.NO_WRAP))
                val transformerCls = IntTransformer::class.java.name
                argList.add(Arg(
                    encodeValue,
                    transformerCls
                ))
            }

        }
        return argList
    }

    private fun checkArgs(args: Array<out Any>?) {
        if (args == null) {
            return
        }

        for (arg in args) {
            if (!(arg is Int ||
                        arg is IntArray ||
                        arg is Long ||
                        arg is LongArray ||
                        arg is String ||
                        arg is Byte ||
                        arg is ByteArray ||
                        arg is Transformer<*>)) {
                throw IllegalArgumentException("unsupported param type:${arg::class.qualifiedName}")
            }
        }
    }

    private fun checkCls(cls:Class<I>) {
        if (!cls.isInterface) {
            throw IllegalStateException("RemoteInterface can only proxy interface.")
        }

        val methods = cls.declaredMethods
        for (m in methods) {
            val ret = m.returnType
            if (!(ret == Void::class.java || ret == ReturnValue::class.java)) {
                throw IllegalArgumentException("unsupported return type:${ret.name}")
            }

            val parameterTypes = m.parameterTypes
            for (p in parameterTypes) {
                // param type can only is  Int IntArray Long LongArray String Byte ByteArray or Transformer
                if (!(p == Int::class.java ||
                            p == IntArray::class.java ||
                            p == Long::class.java ||
                            p == LongArray::class.java ||
                            p == String::class.java ||
                            p == Byte::class.java||
                            p == ByteArray::class.java ||
                            p.superclass == Transformer::class.java)) {
                    throw IllegalArgumentException("unsupported param type:${p.name}")
                }
            }
        }
    }
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
            Log.e(tag?: TAG,msg,cause)
        }
    }
}
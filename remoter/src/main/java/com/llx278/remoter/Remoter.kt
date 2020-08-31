package com.llx278.remoter

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.annotation.IntRange
import androidx.annotation.Keep
import com.google.gson.Gson
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class RemoteMethodInternalException(message: String):Exception(message)
class RemoteMethodExecFail(message: String, cause:Throwable? = null):Exception(message,cause)
class RemoteClassNotFound(message: String, cause:Throwable? = null):Exception(message,cause)
class RemoteMethodNotFound(message: String, cause:Throwable? = null):Exception(message,cause)
class RemoteResponseException(message: String, cause:Throwable? = null):Exception(message,cause)
class RemoteResponseTimeoutException(message: String,cause:Throwable? = null):Exception(message,cause)

class ReturnValue<R>(): RemoteResponse {
    private var callback:Callback<R>? = null
    private val lock:Any = Any()

    private var error:Throwable? = null
    private var countDownLatch:CountDownLatch? = null
    private var returnValue: R? = null


    fun getAsync(callback:Callback<R>) {
        synchronized(lock) {
            this.callback = callback
        }
    }

    fun setOkValue(value:R) {
        returnValue = value
    }

    /**
     *
     */
    @Throws(RemoteResponseException::class)
    fun getSync(timeout:Long = 0L,unit:TimeUnit): R {
        val r = synchronized(lock) {
            returnValue
        }
        if (r == null && error != null) {
            throw RemoteResponseException(message = "parse remote response error.",cause = error!!)
        }
        if (r != null) {
            return r
        }
        // wait remote call
        countDownLatch = CountDownLatch(1)
        val ready = countDownLatch!!.await(timeout, unit)
        if (!ready) {
            throw RemoteResponseTimeoutException(message = "wait remote call timeout.")
        }
        if (returnValue == null && error != null) {
            throw RemoteResponseException(message = "parse remote response error.",cause = error!!)
        }
        // no error, that means we must have a success remote call.
        return returnValue!!
    }

    override fun onResponse(from: Int, response: ByteArray, offset: Int, length: Int) {
        val gson = Gson()
        returnValue = synchronized(lock) {
            // we know response is json string
            try {
//                if (from != to) {
//                    throw IllegalStateException("expected: from equals to. from:$from to:$to. " +
//                            "rpc call internal error.")
//                }
                val jsonObj = String(response)
                val remoteResponse = gson.fromJson(jsonObj,Response::class.java)
                if (remoteResponse.code != 200) {
                    throw RemoteMethodInternalException(remoteResponse.message)
                }

                val arg = gson.fromJson(remoteResponse.data,RemoteValue::class.java)
                val cls : Class<*> = Class.forName(arg.transformerCls)
                val transformer = cls.newInstance() as Transformer<*>
                @Suppress("UNCHECKED_CAST")
                val r = transformer.fromByteArray(Base64.decode(arg.encodedValue.toByteArray(),Base64.NO_WRAP)) as R
                r
            } catch (e:Exception) {
                // other error
                error = e
                callback?.onError(RemoteResponseException(message = "parse remote response error.",cause = e))
                null
            }
        }
        if (callback != null && error != null) {
            callback?.onSuccess(returnValue)
        }

        countDownLatch?.countDown()
    }

    override fun onError(from: Int, cause: RPCException) {
        synchronized(lock) {
            error = cause
        }
        callback?.onError(cause)
        countDownLatch?.countDown()
    }
}

interface Callback<T> {
    fun onSuccess(data:T?)
    fun onError(cause:Throwable)
}


@Keep
internal data class Request(
    val className:String,
    val methodName:String,
    val args:List<RemoteValue>? = null
)

@Keep
internal data class RemoteValue(
    val encodedValue:String,
    val transformerCls:String) {
    companion object {

        fun create(data: Any): RemoteValue {
            val arg = ArgProvider.cls[data::class.java] ?: throw IllegalStateException()
            val cs = arg.getConstructor(data::class.java)
            val args = cs.newInstance(data) as Arg<*>
            return RemoteValue(
                Transformer.encodeByteArray(args.transformer.toByteArray()),
                args.transformer::class.java.name
            )
        }


//        fun create(data:Any): RemoteValue {
//            return if (data is Int || data is IntArg) {
//                val intArg = if (data is Int) {
//                    IntArg(data)
//                } else {
//                    data as IntArg
//                }
//
//                RemoteValue(
//                    Transformer.encodeByteArray(intArg.transformer.toByteArray()),
//                    intArg.transformer::class.java.name
//                )
//            } else if (data is IntArray || data is IntArrayArg) {
//                val intArrayArg = if (data is IntArray) {
//                    IntArrayArg(data)
//                } else {
//                    data as IntArrayArg
//                }
//                RemoteValue(
//                    Transformer.encodeByteArray(intArrayArg.transformer.toByteArray()),
//                    intArrayArg.transformer::class.java.name
//                )
//            } else if (data is Long || data is LongArg) {
//                val longArg = if (data is Long) {
//                    LongArg(data)
//                } else {
//                    data as LongArg
//                }
//                RemoteValue(
//                    Transformer.encodeByteArray(longArg.transformer.toByteArray()),
//                    longArg.transformer::class.java.name
//                )
//            } else if (data is LongArray || data is LongArrayArg) {
//                val arg = if (data is LongArray) {
//                    LongArrayArg(data)
//                } else {
//                    data as LongArrayArg
//                }
//                RemoteValue(
//                    Transformer.encodeByteArray(arg.transformer.toByteArray()),
//                    arg.transformer::class.java.name
//                )
//            } else if (data is String || data is StringArg) {
//                val arg = if (data is String) {
//                    StringArg(data)
//                } else {
//                    data as StringArg
//                }
//                RemoteValue(
//                    Transformer.encodeByteArray(arg.transformer.toByteArray()),
//                    arg.transformer::class.java.name
//                )
//            } else if (data is Byte || data is ByteArg) {
//                val arg = if (data is Byte) {
//                    ByteArg(data)
//                } else {
//                    data as ByteArg
//                }
//                RemoteValue(
//                    Transformer.encodeByteArray(arg.transformer.toByteArray()),
//                    arg.transformer::class.java.name
//                )
//            } else if (data is ByteArray || data is ByteArrayArg) {
//                val arg = if (data is ByteArray) {
//                    ByteArrayArg(data)
//                } else {
//                    data as ByteArrayArg
//                }
//                RemoteValue(
//                    Transformer.encodeByteArray(arg.transformer.toByteArray()),
//                    arg.transformer::class.java.name
//                )
//            } else if (data is Arg<*>) {
//                val encodeValue:String = Transformer.encodeByteArray(data.transformer.toByteArray())
//                val transformerCls : String = data.transformer::class.java.name
//                RemoteValue(encodeValue, transformerCls)
//            } else {
//                throw UnSupportRemoteValueException("${data::class.qualifiedName} not supported by RemoteValue")
//            }
//        }

        fun resume(data:RemoteValue): Any {
            val cls = Class.forName(data.transformerCls)
            TODO()
        }
    }
}

@Keep
data class Response(
    val code:Int,
    val message:String,
    val data: String? = null
) {
    companion object {
        const val CODE_OK = 200
        const val CODE_REMOTE_METHOD_EXEC_FAIL = 201
        const val CODE_CLASS_NOT_FOUND = 202
        const val CODE_METHOD_NOT_FOUND = 203
        const val CODE_PARSE_REQUEST_FAIL = 204
    }
}

interface IProvider

@Target(AnnotationTarget.CLASS)
annotation class RemoteClassName(val value:String = "")
@Target(AnnotationTarget.FUNCTION)
annotation class RemoteMethodName(val value:String = "")
@Target(AnnotationTarget.CLASS)
annotation class RemoteId(val value:Int)

private class DynamicProxyProcessor(providers:List<IProvider>): IRemoteRequestProcessor {

    private data class Holder(
        val instance:IProvider,
        val methods:Map<String,Method>
    )

    private val gson = Gson()
    private val cache:Map<String,Holder>

    init {
        val mutableCache :MutableMap<String,Holder> = mutableMapOf()
        for (p in providers) {
            val rcnCls = p.javaClass.getAnnotation(RemoteClassName::class.java)
            val className = rcnCls?.value ?: p.javaClass.name
            val methods:MutableMap<String,Method> = mutableMapOf()
            for (m in p.javaClass.methods) {
                val rcmCls = m.getAnnotation(RemoteMethodName::class.java)
                if (rcmCls != null) {
                    methods[m.name] = m
                }
            }
            mutableCache[className] = Holder(
                instance = p,
                methods = methods
            )
        }
        cache = mutableCache.toMap()
    }

    override fun process(body: RpcProtocol.BodyWrapper, command: InvokeCommand) {
        val jsonStr = String(body.data,body.offset,body.length)
        try {
            val request = gson.fromJson(jsonStr, Request::class.java)
            val className = request.className
            val methodName = request.methodName
            val args = if (request.args != null) {
                resumeArgs(request.args)
            } else {
                listOf()
            }
            val holder = cache[className] ?: throw RemoteClassNotFound("className:$className not found")
            val method = holder.methods[methodName] ?: throw RemoteMethodNotFound("methodName:$methodName not found")

            val ret = try {
                if (args.isNotEmpty()) {
                    method.invoke(holder.instance, args)
                } else {
                    method.invoke(holder.instance)
                }
            } catch (e:Exception) {
                throw RemoteMethodExecFail("exec method:${methodName} fail className:${className}", cause = e)
            }

            val retValue = if (ret != null) {
                gson.toJson(RemoteValue.create(ret))
            } else {
                null
            }

            val response = Response(
                code = Response.CODE_OK,
                message = "ok",
                data = retValue
            )
            val responseJson = gson.toJson(response)
            command.send(responseJson.toByteArray())
        } catch (e:RemoteMethodExecFail) {
            val response = Response(
                code = Response.CODE_REMOTE_METHOD_EXEC_FAIL,
                message = "${e.message} cause:${e.cause?.message}"
            )
            command.send(gson.toJson(response).toByteArray())
        } catch (e1:RemoteClassNotFound) {
            val response = Response(
                code = Response.CODE_CLASS_NOT_FOUND,
                message = "${e1.message}"
            )
            command.send(gson.toJson(response).toByteArray())
        } catch (e2:RemoteMethodNotFound) {
            val response = Response(
                code = Response.CODE_METHOD_NOT_FOUND,
                message = "${e2.message}"
            )
            command.send(gson.toJson(response).toByteArray())
        } catch (e3:Exception) {
            val response = Response(
                code = Response.CODE_PARSE_REQUEST_FAIL,
                message = "${e3.message}"
            )
            command.send(gson.toJson(response).toByteArray())
        }
    }

    private fun createResponseData(value:Any): String {
        TODO()
    }

    private fun resumeArgs(args:List<RemoteValue>): List<Any> {
        return args.map {
            val cls = Class.forName(it.transformerCls)
            val instance = cls.newInstance() as Transformer<*>
            val arg = instance.fromByteArray(Transformer.decodeByteArray(it.encodedValue))!!
            arg
        }
    }
}

@RemoteId(value = 4)
class DynamicProxyProcessService: RemoteProxyService() {
    override fun createProcessor(): IRemoteRequestProcessor {
        // TODO How to add interfaces.
        return DynamicProxyProcessor(listOf())
    }
}

class RemoteInterface<I:IProvider>(private val rpc:IRpc) {
    fun proxy(context: Context, cls: Class<I>, to: Int): I {
        checkCls(cls)
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(cls.classLoader,Array(1){cls},object: InvocationHandler {

            override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                val className = findClassName(cls)
                val methodName = findMethodName(method!!)
                val argsList:List<RemoteValue>? = createArgs(args)
                val request = Request(
                    className,
                    methodName,
                    argsList
                )
                val body = Gson().toJson(request).toByteArray()
                val returnType = method.returnType
                val cs = returnType.getConstructor(Int::class.java)
                val value = cs.newInstance(to) as RemoteResponse
                rpc.call(context,to,body,value)
                return null
            }
        }) as I
    }

    private fun findClassName(cls:Class<I>): String {
        val anno = cls.getAnnotation(RemoteClassName::class.java)
        val className = anno?.value?:""
        return if (className.isNotEmpty()) {
            className
        } else {
            cls.name
        }
    }

    private fun findMethodName(method: Method): String {
        val anno = method.getAnnotation(RemoteMethodName::class.java)
        val methodName = anno?.value?:""
        return if (methodName.isNotEmpty()) {
            return methodName
        } else {
            method.name
        }
    }

    private fun createArgs(args: Array<out Any>?): List<RemoteValue>? {
        if (args == null) {
            return null
        }
        val argList: MutableList<RemoteValue> = mutableListOf()
        for (a in args) {
            val requestArg = RemoteValue.create(a)
            argList.add(requestArg)
        }
        return argList
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
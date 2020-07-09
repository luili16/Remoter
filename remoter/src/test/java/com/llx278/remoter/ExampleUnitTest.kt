package com.llx278.remoter

import org.junit.Test

import org.junit.Assert.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    class T : ITest {
        override fun print(param:String) : String {
            println("hello world")
            return "aaa"
        }

    }


    @Test
    fun addition_isCorrect() {

        val t = T()

        val proxy = Proxy.newProxyInstance(ITest::class.java.classLoader,Array<Class<*>>(1){ITest::class.java},object :InvocationHandler {
            override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                println("proxy: ${proxy!!::class.java.name}")
                print("method: ${method!!.name}")
                return "aaaa"
            }
        }) as ITest

        val ret = proxy.print("hello")
        println("ret: $ret")
    }
}
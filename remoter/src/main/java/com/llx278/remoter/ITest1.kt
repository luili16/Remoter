package com.llx278.remoter

interface ITest1: IProvider {
    fun say(str:String):String
}

@RemoteClassName("test1")
class Test1: ITest1 {
    @RemoteMethodName("say1")
    override fun say(str: String): ReturnValue<String> {
        return ReturnValue(str)
    }
}
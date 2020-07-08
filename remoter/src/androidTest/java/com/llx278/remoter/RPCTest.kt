package com.llx278.remoter

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class RPCTest {
    companion object {
        private val rpc: IRpc = BinderRpc(123)
    }

    @Test
    fun rpcProtocolCreateTest() {
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = 65535
        val length = 1
        val body = "a".toByteArray()

        val protocols = RpcProtocol.create(
            magic = magic,
            version = version,
            from = from,
            to = to,
            id = id,
            length = length,
            body = body
        )
        assertTrue(protocols.size == 1)
        val protocol = protocols[0]
        assertEquals(magic,protocol.locateMagic())
        assertEquals(version,protocol.locateVersion())
        assertEquals(from,protocol.locateFrom())
        assertEquals(to,protocol.locateTo())
        assertEquals(id,protocol.locateId())
        assertEquals(length,protocol.locateLength())
        val bodyWrapper = protocol.locateBody()
        val realBody = ByteArray(bodyWrapper.length)
        feedRange(realBody,bodyWrapper)
        assertArrayEquals(body,realBody)
    }

    @Test
    fun rpcProtocolCreateEdgeTest() {
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = 65535
        val length = 512 * 1024
        val body = Random.nextBytes(512 * 1024)
        val protocols = RpcProtocol.create(
            magic = magic,
            version = version,
            from = from,
            to = to,
            id = id,
            length = length,
            body = body
        )
        assertTrue(protocols.size == 1)
        val protocol = protocols[0]
        assertEquals(magic,protocol.locateMagic())
        assertEquals(version,protocol.locateVersion())
        assertEquals(from,protocol.locateFrom())
        assertEquals(to,protocol.locateTo())
        assertEquals(id,protocol.locateId())
        assertEquals(length,protocol.locateLength())
        val bodyWrapper = protocol.locateBody()
        val realBody = ByteArray(bodyWrapper.length)
        feedRange(realBody,bodyWrapper)
        assertArrayEquals(body,realBody)
    }

    @Test
    fun rpcSplitProtocolTest() {
        val splitLen = 10
        val size = 512 * 1024 * splitLen + 3456
        val randomBody = Random.nextBytes(size)
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = 65535
        val length = randomBody.size
        val body = randomBody
        val protocols = RpcProtocol.create(
            magic = magic,
            version = version,
            from = from,
            to = to,
            id = id,
            length = length,
            body = body
        )
        assertEquals(splitLen + 1,protocols.size)
        assertEquals(splitLen + 1,protocols[0].locateSegment().toInt())
        assertEquals(splitLen + 1,protocols[splitLen].locateSegment().toInt())
        val actualBody = protocols[splitLen].locateBody()
        val actualSize = actualBody.length + actualBody.offset
        assertEquals(actualSize,size)
        val copyBody = ByteArray(size)
        for (p in protocols) {
            val b= p.locateBody()
            System.arraycopy(b.data,b.offset,copyBody,b.offset,b.length)
        }
        assertArrayEquals(randomBody,copyBody)
    }

    @Test
    fun rpcProtocolResumeTest() {
        val size = 1024
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = 65535
        val length = size
        val mockBody = Random.nextBytes(size)
        val test = RpcProtocol.create(
            magic = magic,
            version = version,
            from = from,
            to = to,
            id = id,
            length = length,
            body = mockBody
        )
        val src = test[0].asArray()
        val dst = ByteArray(src.size)
        System.arraycopy(src,0,dst,0,dst.size)
        val protocol = RpcProtocol.asRpcProtocol(dst)
        assertEquals(magic,protocol.locateMagic())
        assertEquals(version,protocol.locateVersion())
        assertEquals(from,protocol.locateFrom())
        assertEquals(to,protocol.locateTo())
        assertEquals(id,protocol.locateId())
        assertEquals(1.toByte(),protocol.locateSegment())
        assertEquals(0.toByte(),protocol.locateIndex())
        assertEquals(length,protocol.locateSegmentLength())
        assertEquals(length,protocol.locateLength())
        val locateBody = protocol.locateBody()
        val dst0 = ByteArray(locateBody.length)
        feedRange(dst0,locateBody)
        assertArrayEquals(mockBody,dst0)
    }

    @Test
    fun rpcCallTest() {
        rpcCall("hello world".toByteArray())
    }

    @Test
    fun rpcCallEdgeTest() {
        rpcCall(Random.nextBytes(512*1024))
    }

    @Test
    fun rpcCallEdgeTest0() {
        rpcCall(Random.nextBytes(512*1024 * 2))
    }

    @Test
    fun rpcCallLargeTest() {
        val size = 512*1024 * 10 + 128
        rpcCall(Random.nextBytes(size))
    }

    private fun rpcCall(requestData:ByteArray) {
        val context:Context = InstrumentationRegistry.getInstrumentation().targetContext
        val countDown = CountDownLatch(1)
        var match = false
        val before = System.currentTimeMillis()
        rpc.call(context,BinderRpc.TEST_SERVICE_ID, requestData, object: Callback {
            override fun onResponse(from: Int, response: ByteArray, offset: Int, length: Int) {
                match = try {
                    assertEquals(BinderRpc.TEST_SERVICE_ID,from)
                    val dst = ByteArray(length)
                    System.arraycopy(response,offset,dst,0,length)
                    assertArrayEquals(requestData,dst)
                    true
                } catch (e:Exception){
                    false
                }
                countDown.countDown()
            }

            override fun onError(from: Int, cause: Throwable) {
                countDown.countDown()
            }
        })

        val timeout = countDown.await(10 * 60 * 240,TimeUnit.SECONDS)
        val after = System.currentTimeMillis()
        Logger.d(msg = "transform size: ${requestData.size/1024}kb cost time:${(after - before)}ms")
        assertTrue(timeout)
        assertTrue(match)
    }




    private fun feedRange(actual:ByteArray, raw:RpcProtocol.BodyWrapper) {
        for (i in 0 until raw.length) {
            actual[i] = raw.data[i + raw.offset]
        }
    }

}
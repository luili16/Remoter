package com.llx278.remoter

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class RPCTest {
    companion object {
        private val rpc: IRpc = BinderRpc(123)
        private const val FROM1 = 1
        private const val FROM2 = 2
        private const val FROM3 = 3
    }

    @Test
    fun rpcProtocolCreateTest() {
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = UUID.randomUUID()
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
    fun rpcUniqueIdTest() {
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = UUID.randomUUID()
        val length = 1
        val body = "a".toByteArray()

        val protocol = RpcProtocol.create(
            magic = magic,
            version = version,
            from = from,
            to = to,
            id = id,
            length = length,
            body = body
        )
        val retId = protocol[0].locateId()
        assertEquals(id.mostSignificantBits,retId.mostSignificantBits)
        assertEquals(id.leastSignificantBits,retId.leastSignificantBits)
    }

    @Test
    fun rpcProtocolCreateEdgeTest() {
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = UUID.randomUUID()
        val length = RpcProtocol.MAX
        val body = Random.nextBytes(RpcProtocol.MAX)
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
    fun rpcProtocolCreateEdge0Test() {
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = UUID.randomUUID()
        val length = RpcProtocol.MAX * 2
        val body = Random.nextBytes(length)
        val protocols = RpcProtocol.create(
            magic = magic,
            version = version,
            from = from,
            to = to,
            id = id,
            length = length,
            body = body
        )
        assertEquals(2,protocols.size)
    }

    @Test
    fun rpcSplitProtocolTest() {
        val splitLen = 10
        val size = RpcProtocol.MAX * splitLen + 3456
        val randomBody = Random.nextBytes(size)
        val magic = RpcProtocol.MAGIC
        val version = RpcProtocol.VERSION_1
        val from = 1234
        val to = 4321
        val id = UUID.randomUUID()
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
        val id = UUID.randomUUID()
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
        rpcCall(rpc,"hello world".toByteArray())
    }

    @Test
    fun rpcCallEdgeTest() {
        rpcCall(rpc,Random.nextBytes(512*1024))
    }

    @Test
    fun rpcCallEdgeTest0() {
        rpcCall(rpc,Random.nextBytes(512*1024 * 2))
    }

    @Test
    fun rpcCallLargeTest() {
        val size = 512*1024 * 10 + 128
        rpcCall(rpc,Random.nextBytes(size))
        rpcCall(rpc,Random.nextBytes(12))
        rpcCall(rpc,Random.nextBytes(2))
        rpcCall(rpc,Random.nextBytes(1))
    }
    @Test
    fun rpcPressureTest() {
        val maxCall = 50
        (0..maxCall).forEach { _ ->
            val size = Random.nextInt(1024 * 1024 * 10)
            rpcCall(rpc,Random.nextBytes(size))
        }
    }

    @Test
    fun rpcConcurrentTest() {
        runBlocking {
            repeat(100) {
                launch {
                    val size = Random.nextInt(1024 * 1024 * 10)
                    rpcCall(rpc,Random.nextBytes(size))
                }
            }
        }
    }

    @Test
    fun rpcConcurrent0Test() {
        runBlocking {
            repeat(5) {
                launch {
                    val r = BinderRpc(4)
                    val size = Random.nextInt(1024 * 1024 * 10)
                    rpcCall(r,Random.nextBytes(size))
                }
            }
        }
    }

    @Test
    fun rpcCallDifferentService() {
        val rpc1 = BinderRpc(FROM1)
        val rpc2 = BinderRpc(FROM2)
        val rpc3 = BinderRpc(FROM3)
        rpcCall(rpc1,Random.nextBytes(12),BinderRpc.TEST_SERVICE_ID_1)
        rpcCall(rpc2,Random.nextBytes(2),BinderRpc.TEST_SERVICE_ID_2)
        rpcCall(rpc3,Random.nextBytes(1),BinderRpc.TEST_SERVICE_ID_3)
    }



    @Test
    fun rpcLocalTest() {
        val rpc2 = BinderRpc(2)
        val size = 512*1024 * 10 + 128
        rpcCall(rpc2,Random.nextBytes(size))
        rpcCall(rpc2,Random.nextBytes(1024))
        rpcCall(rpc2,Random.nextBytes(512*1024))
        rpcCall(rpc2,Random.nextBytes(1))
    }

    @Test
    fun rpCallOverFollowTest() {

        val data = Random.nextBytes(512 * 1024 * 256 + 10)
        val context:Context = InstrumentationRegistry.getInstrumentation().targetContext
        val countDown = CountDownLatch(1)
        var match = false
        rpc.call(context,BinderRpc.TEST_SERVICE_ID_1,data,object :RemoteResponse {
            override fun onResponse(from: Int, response: ByteArray, offset: Int, length: Int) {
                match = false
                countDown.countDown()
            }

            override fun onError(from: Int, cause: RPCException) {
                match = true
                countDown.countDown()
            }
        })
        countDown.await()
        assertTrue(match)
    }

    private fun rpcCall(r:IRpc,requestData:ByteArray,remoteId:Int = BinderRpc.TEST_SERVICE_ID_1) {
        val context:Context = InstrumentationRegistry.getInstrumentation().targetContext
        val countDown = CountDownLatch(1)
        var match = false
        val before = System.currentTimeMillis()
        r.call(context,remoteId, requestData, object: RemoteResponse {
            override fun onResponse(from: Int, response: ByteArray, offset: Int, length: Int) {
                match = try {
                    assertEquals(remoteId,from)
                    val dst = ByteArray(length)
                    System.arraycopy(response,offset,dst,0,length)
                    assertArrayEquals(requestData,dst)
                    true
                } catch (e:Exception){
                    Logger.e(msg = "assert fail",cause = e)
                    false
                }
                countDown.countDown()
            }

            override fun onError(from: Int, cause: RPCException) {
                Logger.e(msg = "assert fail",cause = cause)
                countDown.countDown()
            }
        })

        val timeout = countDown.await(10,TimeUnit.SECONDS)
        val after = System.currentTimeMillis()
        val str = if (requestData.size < 1024) {
            "${requestData.size}byte"
        } else {
            "${requestData.size/1024}kb"
        }
        Logger.d(msg = "transform size: $str cost time:${(after - before)}ms")
        assertTrue(timeout)
        assertTrue(match)
    }

    private fun feedRange(actual:ByteArray, raw:RpcProtocol.BodyWrapper) {
        for (i in 0 until raw.length) {
            actual[i] = raw.data[i + raw.offset]
        }
    }

}
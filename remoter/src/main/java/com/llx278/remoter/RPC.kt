package com.llx278.remoter

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import java.nio.ByteBuffer
import java.util.*

// Implement remote process call.

/**
 * Remote process call.
 */
interface IRpc {

    /**
     * Represent a remote process call
     * @param to remote process id.
     * @param requestBody content of request
     * @param response return value callback. success or error.
     *
     */
    fun call(context: Context, to: Int, requestBody: ByteArray, response: RemoteResponse? = null)
}

class RPCException(message:String,cause:Throwable?=null):Exception(message,cause)

interface RemoteResponse {
    /**
     * response value from RPC
     *
     * @param from remote process id
     * @param response response value. we enforce the format of request body is json object .
     * which means it starts with "{" or "[" and end with "}" or "]"
     */
    fun onResponse(from: Int, response: ByteArray,offset:Int, length:Int)

    /**
     * error response
     *
     * @param from remote process id
     * @param cause the reason of error response.
     */
    fun onError(from: Int, cause: RPCException)
}

/**
 * Define remote process call protocol.
 *
 * +-----++-------++----++--++--++-------++-----++-------------++-------+--------------------------+
 * |magic||version||from||to||id||segment||index||segmentLength|| length|            body          |
 * +-----++-------++----++--++--++-------++-----++-------------++-------+--------------------------+
 *    1       1       4    4  16     1       1          4             4  |
 * -------------------  header  -----------------------------------------
 *
 *  magic: constant value        1 byte
 *  version: protocol version    1 byte
 *  from: who send this msg      4 byte
 *  to: who receive this msg     4 byte
 *  id: identity this msg        16 byte 128bit UUID
 *  segment: how many segments this msg have. This means a long msg will be split.   1 byte
 *  index: index of segments 0 - 255 means max size is 128MB     1byte
 *  segmentLength: length of segment 4byte
 *  length: length of body       4byte
 *  body: content of body,max size is 512 * 1024byte. msg greater than 512 * will be split
 */
class RpcProtocol:Comparable<RpcProtocol> {
    private var magic: Byte? = null
    private var version: Byte? = null
    private var from: Int? = null
    private var to: Int? = null
    private var id: UUID? = null
    private var segment: Byte? = null
    private var index: Byte? = null
    private var segmentLength:Int? = null
    private var length: Int? = null
    private var body: BodyWrapper? = null

    private var buffer:ByteBuffer? = null
    companion object {

        const val MAX = 128 * 1024
        private const val MAGIC_LENGTH = 1
        private const val VERSION_LENGTH = 1
        private const val FROM_LENGTH = 4
        private const val TO_LENGTH = 4
        const val ID_LENGTH = 16
        private const val SEGMENT_LENGTH = 1
        private const val INDEX_LENGTH = 1
        private const val SEGMENT_LENGTH_LENGTH = 4
        private const val LENGTH_LENGTH = 4
        @VisibleForTesting
        internal const val HEADER_LENGTH = MAGIC_LENGTH + VERSION_LENGTH + FROM_LENGTH + TO_LENGTH +
                ID_LENGTH + SEGMENT_LENGTH + INDEX_LENGTH + SEGMENT_LENGTH_LENGTH + LENGTH_LENGTH

        private const val magicPosition:Int = 0

        private const val versionPosition:Int = magicPosition + MAGIC_LENGTH

        private const val fromPosition = versionPosition + VERSION_LENGTH

        private const val toPosition = fromPosition + FROM_LENGTH

        private const val idPosition = toPosition + TO_LENGTH

        private const val segmentPosition = idPosition + ID_LENGTH

        private const val indexPosition = segmentPosition + SEGMENT_LENGTH

        private const val segmentLengthPosition = indexPosition + INDEX_LENGTH

        private const val lengthPosition = segmentLengthPosition + SEGMENT_LENGTH_LENGTH

        private const val bodyPosition = lengthPosition + LENGTH_LENGTH

        const val MAGIC : Byte = 0x11
        const val VERSION_1 : Byte = 0x01

        fun create(
            magic: Byte,
            version: Byte,
            from: Int,
            to: Int,
            id: UUID,
            length: Int,
            body: ByteArray
        ) : List<RpcProtocol> {

            // split message if length > 512 * 1024
            if (length != body.size) {
                throw IllegalArgumentException("$length != ${body.size}")
            }

            if (length <= MAX) {
                return listOf(RpcProtocol(
                    magic = magic,
                    version = version,
                    from = from,
                    to = to,
                    id = id,
                    segment = 1,
                    index = 0,
                    segmentLength = length,
                    length = length,
                    body = BodyWrapper(
                        data = body,
                        offset = 0,
                        length = body.size
                    )
                ))
            } else {
                val segment = length / MAX
                if (segment > Byte.MAX_VALUE) {
                    throw IllegalArgumentException("body to long. size:$length body length should less than 128MB")
                }

                val last = length - segment * MAX
                val s = if (last == 0) {
                    segment
                } else {
                    segment + 1
                }

                val protocols:MutableList<RpcProtocol> = mutableListOf()
                for (index in 0 until s) {
                    val segmentLength = if (index == s - 1) {
                        if (last == 0) {
                            MAX
                        } else {
                            last
                        }
                    } else {
                        MAX
                    }

                    val protocol = RpcProtocol(
                        magic = magic,
                        version = version,
                        from = from,
                        to = to,
                        id = id,
                        segment = s.toByte(),
                        index = index.toByte(),
                        segmentLength = segmentLength,
                        length = length,
                        body = BodyWrapper(
                            data = body,
                            offset = index * MAX,
                            length = segmentLength
                        )
                    )
                    protocols.add(protocol)
                }
                return protocols
            }
        }

        fun asRpcProtocol(msg:ByteArray) : RpcProtocol {
            return RpcProtocol(msg)
        }
    }

    private constructor(
        magic:Byte,
        version:Byte,
        from:Int,
        to:Int,
        id:UUID,
        segment:Byte,
        index:Byte,
        segmentLength:Int,
        length:Int,
        body:BodyWrapper
    ) {
        this.magic = magic
        this.version = version
        this.from = from
        this.to = to
        this.id = id
        this.segment = segment
        this.index = index
        this.segmentLength = segmentLength
        this.length = length
        this.body = body

    }

    private constructor(bytes:ByteArray) {
        this.buffer = ByteBuffer.wrap(bytes)
        val segmentLength = locateSegmentLength()
        if (segmentLength + HEADER_LENGTH != bytes.size) {
            throw IllegalArgumentException("Illegal RpcProtocol bytes. expected " +
                    "length: ${segmentLength + HEADER_LENGTH} actual:${bytes.size}")
        }
    }

    fun protocolLength(): Int {
        return HEADER_LENGTH + locateLength()
    }

    fun locateMagic() : Byte {
        return if (this.magic != null) {
            this.magic!!
        } else {
            this.buffer!!.get(magicPosition)
        }
    }

    fun locateVersion() : Byte {
        return if (this.version != null) {
            this.version!!
        } else {
            this.buffer!!.get(versionPosition)
        }
    }

    fun locateFrom() : Int {
        return if (this.from != null) {
            this.from!!
        } else {
            this.buffer!!.getInt(fromPosition)
        }
    }

    fun locateTo(): Int {
        return if (this.to != null) {
            this.to!!
        } else {
            this.buffer!!.getInt(toPosition)
        }
    }

    fun locateId(): UUID {
        return if (this.id != null) {
            this.id!!
        } else {
            val uuid = ByteArray(ID_LENGTH)
            buffer!!.position(idPosition)
            buffer!!.get(uuid)
            val buf = ByteBuffer.wrap(uuid)
            buf.position(0)
            val msb = buf.long
            val lsb = buf.long
            return UUID(msb,lsb)
        }
    }

    fun locateSegment(): Byte {
        return if (this.segment != null) {
             this.segment!!
        } else {
             this.buffer!!.get(segmentPosition)
        }
    }

    fun locateIndex(): Byte {
        return if (this.index != null) {
            this.index!!
        } else {
            buffer!!.get(indexPosition)
        }
    }

    fun locateSegmentLength(): Int {
        return if (this.segmentLength != null) {
            this.segmentLength!!
        } else {
            buffer!!.getInt(segmentLengthPosition)
        }
    }

    fun locateLength(): Int {
        return if (this.length != null) {
            this.length!!
        } else {
            buffer!!.getInt(lengthPosition)
        }
    }

    fun locateBody(): BodyWrapper {

        return if (this.body != null) {
            return body!!
        } else {
            // to avoid copy occur.
            BodyWrapper(
                data = buffer!!.array(),
                offset = bodyPosition,
                length = locateSegmentLength()
            )
        }
    }

    data class BodyWrapper(
        val data:ByteArray,
        val offset:Int,
        val length:Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BodyWrapper

            if (!data.contentEquals(other.data)) return false
            if (offset != other.offset) return false
            if (length != other.length) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + offset
            result = 31 * result + length
            return result
        }
    }

    fun asArray() : ByteArray {
        if (buffer != null) {
            return buffer!!.array()
        } else {
            // copy occur.
            val size = HEADER_LENGTH + body!!.length
            val protocol = ByteArray(size)
            val buffer = ByteBuffer.wrap(protocol)
            buffer.put(magic!!)
            buffer.put(version!!)
            buffer.putInt(from!!)
            buffer.putInt(to!!)
            buffer.putLong(id!!.mostSignificantBits)
            buffer.putLong(id!!.leastSignificantBits)
            buffer.put(segment!!)
            buffer.put(index!!)
            buffer.putInt(segmentLength!!)
            buffer.putInt(length!!)
            buffer.put(body!!.data, body!!.offset,body!!.length)
            return buffer.array()
        }
    }

    override fun compareTo(other: RpcProtocol): Int {

        val diff = this.locateIndex() - other.locateIndex()

        return when {
            diff< 0 -> {
                -1
            }
            diff > 0 -> {
                1
            }
            else -> {
                0
            }
        }
    }
}

class BinderRpc(private val from:Int) : IRpc {

    companion object {
        const val TEST_SERVICE_ID_1 = 1
        const val TEST_SERVICE_ID_2 = 2
        const val TEST_SERVICE_ID_3 = 3
    }
    private var route: IRoute? = null

    /**
     * Use this map to cache a remote call. when remote service is connecting.
     */
    private val pendingCall: MutableMap<String,CallHolder> = mutableMapOf()

    /**
     * cache all the response callback for later use.
     */
    private val responseCache:MutableMap<String,ResponseHolder> = mutableMapOf()
    private var binderService: IBinder? = null
    private var serviceDisconnectedCalled: Boolean = false
    private val services:Map<Int,Class<*>>
    init {

        services = mapOf(
            Pair(TEST_SERVICE_ID_1,TestService::class.java),
            Pair(TEST_SERVICE_ID_2,TestService2::class.java),
            Pair(TEST_SERVICE_ID_3,TestService3::class.java)
        )
    }
    private val receiver : IReceiver = object: IReceiver.Stub() {
        override fun onReceive(data: ByteArray) {
            val protocol = RpcProtocol.asRpcProtocol(data)
            val id = protocol.locateId().toString()
            val from = protocol.locateFrom()
            val to = protocol.locateTo()
            val segment = protocol.locateSegment()
            val holder = synchronized(responseCache) { responseCache[id] }
                ?: throw IllegalStateException("unknown id: $id protocol( " +
                        "from:$from " +
                        "to:$to " +
                        "segment:$segment index:${protocol.locateIndex()} " +
                        "segmentLength:${protocol.locateSegmentLength()} " +
                        "length:${protocol.locateLength()} " +
                        "body size:${protocol.locateBody().length} )")
            if (to != this@BinderRpc.from) {
                holder.callResponse?.onError(from, RPCException(message = "expected to:${this@BinderRpc.from} but received:$to"))
                return
            }

            if (segment == 1.toByte()) {
                val body = protocol.locateBody()
                holder.callResponse?.onResponse(from,body.data,body.offset,body.length)
                synchronized(responseCache) { responseCache.remove(id) }
            } else {
                val responses = holder.remoteResponse
                responses.add(protocol)
                if (responses.size == segment.toInt()) {
                    // we received all
                    responses.sort()
                    // copy value
                    // TODO how to avoid copy?
                    val dst = ByteArray(protocol.locateLength())
                    var offset = 0
                    for (r in responses) {
                        val w = r.locateBody()
                        System.arraycopy(w.data,w.offset,dst,offset,w.length)
                        offset += w.length
                    }
                    holder.callResponse?.onResponse(from,dst,0,dst.size)
                    synchronized(responseCache) { responseCache.remove(id) }
                }
            }
        }
    }

    override fun call(context: Context,to: Int, requestBody: ByteArray, response: RemoteResponse?) {

        try {

            val holder = createHolder(from,to,requestBody)
            synchronized(responseCache) {
                responseCache[holder.id.toString()] = ResponseHolder(response)
            }

            if ((binderService != null && !binderService!!.isBinderAlive) || !serviceDisconnectedCalled) {

                val cls: Class<*>? = services[to]
                if (cls == null) {
                    response?.onError(from, RPCException(message = "query remote service fail. to:$to"))
                    return
                }

                // Just cache this call
                // bind service and wait service connected.
                synchronized(pendingCall) {
                    pendingCall[holder.id.toString()] = holder
                }

                context.bindService(Intent(context,cls), object: ServiceConnection {

                    override fun onServiceDisconnected(name: ComponentName) {
                        serviceDisconnectedCalled = false
                        route = null
                    }

                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        route = IRoute.Stub.asInterface(service)
                        try {
                            route!!.setReceiver(from,receiver)
                            binderService = service
                            serviceDisconnectedCalled = true

                            synchronized(pendingCall) {
                                drainPendingCall()
                            }
                        } catch (e:Exception) {
                            response?.onError(from, RPCException(message = "exec rpc method fail",cause = e))
                            clear()
                        }
                    }

                },Context.BIND_AUTO_CREATE)

                return
            }

            synchronized(pendingCall) {
                if (pendingCall.isNotEmpty()) {
                    drainPendingCall()
                }
            }

            for (p in holder.protocols) {
                route!!.send(p.asArray())
            }
        } catch (e:Exception) {
            response?.onError(from, RPCException(message = "call rpc method fail",cause = e))
            clear()
        }
    }

    private fun clear() {
        route = null
        binderService = null
        serviceDisconnectedCalled = false
    }

    private fun drainPendingCall() {

        val i = pendingCall.iterator()
        while (i.hasNext()) {
            val entry = i.next()
            for (p in entry.value.protocols) {
                route!!.send(p.asArray())
            }
            i.remove()
        }

    }

    private fun createHolder(from: Int, to: Int, requestBody: ByteArray): CallHolder {
        val id = UUID.randomUUID()
        val protocols = RpcProtocol.create(
            magic = RpcProtocol.MAGIC,
            version = RpcProtocol.VERSION_1,
            from = from,
            to = to,
            id = id,
            length = requestBody.size,
            body = requestBody
        )
        return CallHolder(
            id = id,
            protocols = protocols
        )
    }

    private data class CallHolder(
        val id:UUID,
        val protocols:List<RpcProtocol>
    )

    private data class ResponseHolder(
        val callResponse:RemoteResponse?,
        val remoteResponse:MutableList<RpcProtocol> = mutableListOf()
    )
}

internal class RouteImpl(private val processor: IRemoteRequestProcessor): IRoute.Stub() {

    private val responseReceivers : MutableMap<Int,IReceiver> = mutableMapOf()
    private val requestsCache:MutableMap<Int,MutableMap<String,RequestHolder>> = mutableMapOf()

    override fun send(data: ByteArray) {
        val protocol = RpcProtocol.asRpcProtocol(data)
        val from = protocol.locateFrom()
        val id = protocol.locateId()
        val segment = protocol.locateSegment()
        val receiver = synchronized(responseReceivers) { responseReceivers[from] }
        if (segment == 1.toByte()) {
            // receive full data
            processor.process(protocol.locateBody(), InvokeCommand(protocol,receiver))
        } else {
            val holder : RequestHolder = synchronized(requestsCache) {
                var request = requestsCache[from]
                if (request == null) {
                    request = mutableMapOf()
                    requestsCache[from] = request
                }
                var h = request[id.toString()]
                if (h == null) {
                    h = RequestHolder()
                    request[id.toString()] = h
                }
                h.protocols.add(protocol)
                h
            }

            if (holder.protocols.size == segment.toInt()) {
                // receive full data
                holder.protocols.sort()
                // copy value
                // TODO how to avoid copy?
                val dst = ByteArray(protocol.locateLength())
                var offset = 0
                for (r in holder.protocols) {
                    val w = r.locateBody()
                    System.arraycopy(w.data,w.offset,dst,offset,w.length)
                    offset += w.length
                }
                processor.process(RpcProtocol.BodyWrapper(dst, 0, dst.size),
                    InvokeCommand(protocol,receiver)
                )
                synchronized(requestsCache) { requestsCache.remove(from) }
            }
        }
    }

    override fun setReceiver(from: Int, receiver: IReceiver) {
        synchronized(responseReceivers) {
            responseReceivers[from] = receiver
        }
    }

    private data class RequestHolder(
        val protocols:MutableList<RpcProtocol> = mutableListOf()

    )
}

// used for test
class TestService: Service() {
    private val route: IRoute.Stub = RouteImpl(TestProcessor())
    override fun onBind(intent: Intent?): IBinder? {
        return route
    }
}

class TestService2:Service() {
    private val route: IRoute.Stub = RouteImpl(TestProcessor())
    override fun onBind(intent: Intent?): IBinder? {
        return route
    }
}

class TestService3:Service() {
    private val route: IRoute.Stub = RouteImpl(TestProcessor())
    override fun onBind(intent: Intent?): IBinder? {
        return route
    }
}

/**
 * Used for send response data to remote request.
 */
class InvokeCommand(
    private val request:RpcProtocol,
    private val receiver:IReceiver?= null) {

    /**
     * It's the caller's responsibility to handle send exception.
     *
     * @param data response data to send to caller process.
     * @throws RemoteException
     */
    @Throws(RemoteException::class)
    fun send(data:ByteArray) {

        val protocols = RpcProtocol.create(
            magic = RpcProtocol.MAGIC,
            version = RpcProtocol.VERSION_1,
            from = request.locateTo(),
            to = request.locateFrom(),
            id = request.locateId(),
            length = data.size,
            body = data
        )

        for (p in protocols) {
            try {
                receiver?.onReceive(p.asArray())
            } catch (e: RemoteException) {
                Logger.e(msg = "request client died.",cause = e)
            }
        }
    }
}

/**
 * process remote request and send result by [InvokeCommand]
 */
interface IRemoteRequestProcessor {
    fun process(body:RpcProtocol.BodyWrapper, command:InvokeCommand)
}

// just for test.
private class TestProcessor: IRemoteRequestProcessor {
    override fun process(body: RpcProtocol.BodyWrapper, command:InvokeCommand) {
        val dest = ByteArray(body.length)
        System.arraycopy(body.data,body.offset,dest,0,body.length)
        command.send(dest)
    }
}
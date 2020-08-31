package com.llx278.remoter

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.reflect.KClass

/**
 * Define how transform param from T to ByteArray or from ByteArray to T
 */
abstract class Transformer<T> {

    abstract fun fromByteArray(raw:ByteArray): T

    abstract fun toByteArray(param: T? = null): ByteArray

    companion object {

        fun encodeByteArray(data:ByteArray): String {
            return String(Base64.encode(data, Base64.NO_WRAP))
        }

        fun decodeByteArray(data:String): ByteArray {
            return Base64.decode(data,Base64.NO_WRAP)
        }
    }
}

object ArgProvider {

    val cls:Map<Class<*>,Class<*>>

    init {
        cls = mapOf(
            Pair(Int::class.java,IntArg::class.java),
            Pair(IntArray::class.java,IntArrayArg::class.java),
            Pair(Long::class.java,LongArg::class.java),
            Pair(LongArray::class.java,LongArrayArg::class.java),
            Pair(String::class.java,StringArg::class.java),
            Pair(Byte::class.java,ByteArg::class.java),
            Pair(ByteArray::class.java,ByteArrayArg::class.java)
        )
    }
}

@Target(AnnotationTarget.CLASS)
annotation class RawClass(val cls:KClass<*>)


open class Arg<T> (
    @JvmField
    val data:T,
    @JvmField
    val transformer: Transformer<T>
)

@RawClass(cls = Int::class)
class IntArg(data: Int): Arg<Int>(data, IntTransformer(data))
@RawClass(cls = IntArray::class)
class IntArrayArg(data: IntArray):Arg<IntArray>(data, IntArrayTransformer(data))
@RawClass(cls = Long::class)
class LongArg(data:Long):Arg<Long>(data,LongTransformer(data))
@RawClass(cls = LongArray::class)
class LongArrayArg(data:LongArray):Arg<LongArray>(data,LongArrayTransformer(data))
@RawClass(cls = String::class)
class StringArg(data:String):Arg<String>(data,StringTransformer(data))
@RawClass(cls = Byte::class)
class ByteArg(data:Byte):Arg<Byte>(data,ByteTransformer(data))
@RawClass(cls = ByteArray::class)
class ByteArrayArg(data:ByteArray):Arg<ByteArray>(data,ByteArrayTransformer(data))

class IntTransformer(private val data: Int? = null): Transformer<Int>() {
    override fun fromByteArray(raw: ByteArray): Int {
        return ByteBuffer.wrap(raw).asIntBuffer().get()
    }

    override fun toByteArray(param: Int?): ByteArray {
        val p = param ?: data ?: throw IllegalStateException()
        val buf = ByteBuffer.allocate(4)
        return buf.putInt(p).array()
    }
}

class IntArrayTransformer(private val data:IntArray? = null): Transformer<IntArray>() {
    override fun fromByteArray(raw: ByteArray): IntArray {
        return ByteBuffer.wrap(raw).asIntBuffer().array()
    }

    override fun toByteArray(param: IntArray?): ByteArray {
        // TODO how to avoid copy?
        val ps = param ?: data ?: throw IllegalStateException()
        val buf = ByteBuffer.allocate(ps.size * 4)
        for (p in ps) {
            buf.putInt(p)
        }
        return buf.array()
    }
}

class LongTransformer(private val data:Long? = null): Transformer<Long>() {
    override fun fromByteArray(raw: ByteArray): Long {
        return ByteBuffer.wrap(raw).asLongBuffer().get()
    }

    override fun toByteArray(param: Long?): ByteArray {
        val ps = param ?: data ?: throw IllegalStateException()
        val buf = ByteBuffer.allocate(8)
        return buf.putLong(ps).array()
    }
}

class LongArrayTransformer(private val data:LongArray? = null): Transformer<LongArray>() {
    override fun fromByteArray(raw: ByteArray): LongArray {
        return ByteBuffer.wrap(raw).asLongBuffer().array()
    }

    override fun toByteArray(param: LongArray?): ByteArray {
        // TODO how to avoid copy?
        val ps = param ?: data ?: throw IllegalStateException()
        val buf = ByteBuffer.allocate(ps.size * 8)
        for (p in ps) {
            buf.putLong(p)
        }
        return buf.array()
    }
}

class StringTransformer(private val data:String? = null): Transformer<String>() {
    override fun fromByteArray(raw: ByteArray): String {
        return String(raw,Charset.forName("utf-8"))
    }

    override fun toByteArray(param: String?): ByteArray {
        val ps = param ?: data ?: throw IllegalStateException()
        return ps.toByteArray(Charset.forName("utf-8"))
    }
}

class ByteTransformer(private val data:Byte? = null): Transformer<Byte>() {
    override fun fromByteArray(raw: ByteArray): Byte {
        return raw[0]
    }

    override fun toByteArray(param: Byte?): ByteArray {
        val ps = param ?: data ?: throw IllegalStateException()
        return ByteArray(1) { ps }
    }
}

class ByteArrayTransformer(private val data:ByteArray? = null): Transformer<ByteArray>() {
    override fun fromByteArray(raw: ByteArray): ByteArray {
        return raw
    }

    override fun toByteArray(param: ByteArray?): ByteArray {
        return param ?: data ?: throw IllegalStateException()
    }
}
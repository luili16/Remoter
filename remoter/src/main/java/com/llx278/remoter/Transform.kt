package com.llx278.remoter

import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Define how transform param from T to ByteArray or from ByteArray to T
 */
abstract class Transformer<T> {

    abstract fun from(raw:ByteArray): T

    abstract fun to(param:T): ByteArray
}

class IntTransformer: Transformer<Int>() {
    override fun from(raw: ByteArray): Int {
        return ByteBuffer.wrap(raw).asIntBuffer().get()
    }

    override fun to(param: Int): ByteArray {
        val buf = ByteBuffer.allocate(4)
        return buf.putInt(param).array()
    }
}

class IntArrayTransformer: Transformer<IntArray>() {
    override fun from(raw: ByteArray): IntArray {
        return ByteBuffer.wrap(raw).asIntBuffer().array()
    }

    override fun to(param: IntArray): ByteArray {
        // TODO how to avoid copy?
        val buf = ByteBuffer.allocate(param.size * 4)
        for (p in param) {
            buf.putInt(p)
        }
        return buf.array()
    }
}

class LongTransform: Transformer<Long>() {
    override fun from(raw: ByteArray): Long {
        return ByteBuffer.wrap(raw).asLongBuffer().get()
    }

    override fun to(param: Long): ByteArray {
        val buf = ByteBuffer.allocate(8)
        return buf.putLong(param).array()
    }
}

class LongArrayTransform: Transformer<LongArray>() {
    override fun from(raw: ByteArray): LongArray {
        return ByteBuffer.wrap(raw).asLongBuffer().array()
    }

    override fun to(param: LongArray): ByteArray {
        // TODO how to avoid copy?
        val buf = ByteBuffer.allocate(param.size * 8)
        for (p in param) {
            buf.putLong(p)
        }
        return buf.array()
    }
}

class StringTransform: Transformer<String>() {
    override fun from(raw: ByteArray): String {
        return String(raw,Charset.forName("utf-8"))
    }

    override fun to(param: String): ByteArray {
        return param.toByteArray(Charset.forName("utf-8"))
    }
}

class ByteTransform: Transformer<Byte>() {
    override fun from(raw: ByteArray): Byte {
        return raw[0]
    }

    override fun to(param: Byte): ByteArray {
        return ByteArray(1) { param }
    }
}

class ByteArrayTransform: Transformer<ByteArray>() {
    override fun from(raw: ByteArray): ByteArray {
        return raw
    }

    override fun to(param: ByteArray): ByteArray {
        return param
    }
}
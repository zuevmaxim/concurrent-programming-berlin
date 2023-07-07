package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index].value
        if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            return value.read(index) as E
        }
        return value as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                value.applyInstalled()
            } else if (value !== expected) {
                return false
            } else if (array[index].compareAndSet(expected, update)) {
                return true
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        desc.apply()
        return desc.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: Any,
        private val update: Any,
        private val index2: Int,
        private val expected2: Any
    ) {
        val status = atomic(UNDECIDED)

        fun read(index: Int): Any {
            check(index == index1 || index == index2)
            return if (status.value == SUCCESS) update else expected1
        }

        fun apply() {
            if (status.value != UNDECIDED) return
            if (installOrHelp()) {
                logicallyUpdate()
            }
            remove()
        }

        fun applyInstalled() {
            logicallyUpdate()
            remove()
        }

        private fun logicallyUpdate() {
            val newStatus = if (get(index2) === expected2) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun remove() {
            val update = if (status.value == SUCCESS) update else expected1
            array[index1].compareAndSet(this, update)
        }

        private fun installOrHelp(): Boolean {
            while (true) {
                if (status.value != UNDECIDED) return false
                val current = array[index1].value
                if (current === this) {
                    return true
                } else if (current is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    current.applyInstalled()
                } else if (current !== expected1) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                } else if (array[index1].compareAndSet(current, this)) {
                    return true
                }
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}

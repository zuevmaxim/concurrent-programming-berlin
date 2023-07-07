package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        var retries = 0
        while (retries < ELIMINATION_WAIT_CYCLES) {
            val index = randomCellIndex()
            if (eliminationArray[index].compareAndSet(null, element)) {
                while (retries < ELIMINATION_WAIT_CYCLES) {
                    if (eliminationArray[index].compareAndSet(CELL_STATE_RETRIEVED, null)) return true
                    retries++
                }
                val current = eliminationArray[index].getAndSet(null)
                if (current === CELL_STATE_RETRIEVED) return true
            }
            retries++
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        repeat(ELIMINATION_WAIT_CYCLES) {
            val index = randomCellIndex()
            val current = eliminationArray[index].value
            if (current == null || current === CELL_STATE_RETRIEVED) return null
            if (eliminationArray[index].compareAndSet(current, CELL_STATE_RETRIEVED)) return current as E
        }
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}
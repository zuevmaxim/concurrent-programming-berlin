package day4

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock() = combinerLock.compareAndSet(false, true)
    private fun unlock() = check(combinerLock.compareAndSet(true, false))

    override fun enqueue(element: E) {
        var index = randomCellIndex()
        while (true) {
            if (tasksForCombiner[index].compareAndSet(null, element)) break
            index = randomCellIndex()
        }

        while (!tryLock()) {
            val value = tasksForCombiner[index].value
            if (value is Result<*>) {
                tasksForCombiner[index].value = null
                return
            }
        }
        help()
        unlock()
        tasksForCombiner[index].value as Result<*>
        tasksForCombiner[index].value = null
        return
    }

    override fun dequeue(): E? {
        var index = randomCellIndex()
        while (true) {
            if (tasksForCombiner[index].compareAndSet(null, Dequeue)) break
            index = randomCellIndex()
        }

        while (!tryLock()) {
            val value = tasksForCombiner[index].value
            if (value is Result<*>) {
                tasksForCombiner[index].value = null
                return value.value as? E
            }
        }
        help()
        unlock()

        val value = tasksForCombiner[index].value as Result<*>
        tasksForCombiner[index].value = null
        return value.value as? E
    }


    private fun help() {
        repeat(TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[it].value ?: return@repeat
            if (task is Result<*>) return@repeat
            if (task === Dequeue) {
                tasksForCombiner[it].value = Result(queue.removeFirstOrNull())
            } else {
                queue.add(task as E)
                tasksForCombiner[it].value = Result(Unit)
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue

private class Result<V>(
    val value: V
)
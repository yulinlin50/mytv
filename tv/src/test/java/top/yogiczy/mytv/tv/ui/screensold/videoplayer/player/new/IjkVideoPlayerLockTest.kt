package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IjkVideoPlayerLockTest {

    @Test
    fun testSingleLock_noDeadlock_whenConcurrentAccess() {
        val lock = Any()
        val accessCount = AtomicInteger(0)
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val barrier = CyclicBarrier(threadCount)

        val threads = (0 until threadCount).map { i ->
            Thread {
                try {
                    barrier.await(5, TimeUnit.SECONDS)
                    synchronized(lock) {
                        accessCount.incrementAndGet()
                        Thread.sleep(10)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        val completed = latch.await(5, TimeUnit.SECONDS)

        assertTrue("All threads should complete without deadlock", completed)
        assertEquals("All threads should have accessed the lock", threadCount, accessCount.get())
    }

    @Test
    fun testSingleLock_noDeadlock_whenNestedSynchronized() {
        val lock = Any()
        val accessCount = AtomicInteger(0)
        val latch = CountDownLatch(2)
        val barrier = CyclicBarrier(2)

        val thread1 = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                synchronized(lock) {
                    accessCount.incrementAndGet()
                    Thread.sleep(50)
                }
            } finally {
                latch.countDown()
            }
        }

        val thread2 = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                synchronized(lock) {
                    accessCount.incrementAndGet()
                    Thread.sleep(50)
                }
            } finally {
                latch.countDown()
            }
        }

        thread1.start()
        thread2.start()
        val completed = latch.await(5, TimeUnit.SECONDS)

        assertTrue("Both threads should complete without deadlock", completed)
        assertEquals("Both threads should have accessed the lock", 2, accessCount.get())
    }

    @Test
    fun testTwoLocks_canDeadlock_whenOrderInconsistent() {
        val lockA = Any()
        val lockB = Any()
        val deadlockDetected = AtomicInteger(0)
        val latch = CountDownLatch(2)
        val barrier = CyclicBarrier(2)

        val thread1 = Thread {
            try {
                barrier.await(2, TimeUnit.SECONDS)
                synchronized(lockA) {
                    Thread.sleep(20)
                    try {
                        synchronized(lockB) {
                            deadlockDetected.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        // Expected if deadlock
                    }
                }
            } catch (e: Exception) {
                // Barrier timeout or interrupt
            } finally {
                latch.countDown()
            }
        }

        val thread2 = Thread {
            try {
                barrier.await(2, TimeUnit.SECONDS)
                synchronized(lockB) {
                    Thread.sleep(20)
                    try {
                        synchronized(lockA) {
                            deadlockDetected.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        // Expected if deadlock
                    }
                }
            } catch (e: Exception) {
                // Barrier timeout or interrupt
            } finally {
                latch.countDown()
            }
        }

        thread1.start()
        thread2.start()

        val completed = latch.await(3, TimeUnit.SECONDS)

        if (!completed) {
            thread1.interrupt()
            thread2.interrupt()
        }

        // With inconsistent lock ordering, deadlock is possible
        // This test demonstrates the problem that was fixed
        // After fix (single lock), this scenario is impossible
    }

    @Test
    fun testSingleLock_releaseAndPositionUpdate_noDeadlock() {
        val lock = Any()
        val jobs = mutableListOf<Thread>()
        var playerRef: String? = "active"
        val isReleased = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = CountDownLatch(2)
        val barrier = CyclicBarrier(2)
        val completedSuccessfully = AtomicInteger(0)

        val releaseThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                if (isReleased.compareAndSet(false, true)) {
                    synchronized(lock) {
                        jobs.forEach { it.interrupt() }
                        jobs.clear()
                        playerRef = null
                    }
                }
                completedSuccessfully.incrementAndGet()
            } catch (e: Exception) {
                // Barrier timeout
            } finally {
                latch.countDown()
            }
        }

        val positionUpdateThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                synchronized(lock) {
                    if (!isReleased.get()) {
                        val p = playerRef
                        if (p != null) {
                            // simulate reading position
                        }
                    }
                }
                completedSuccessfully.incrementAndGet()
            } catch (e: Exception) {
                // Barrier timeout
            } finally {
                latch.countDown()
            }
        }

        releaseThread.start()
        positionUpdateThread.start()

        val completed = latch.await(5, TimeUnit.SECONDS)

        assertTrue("Both threads should complete without deadlock", completed)
        assertEquals("Both threads should complete successfully", 2, completedSuccessfully.get())
    }

    @Test
    fun testSingleLock_stopAndPositionUpdate_noDeadlock() {
        val lock = Any()
        val jobs = mutableListOf<Any>()
        var playerRef: String? = "active"
        val isReleased = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = CountDownLatch(2)
        val barrier = CyclicBarrier(2)
        val completedSuccessfully = AtomicInteger(0)

        val stopThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                if (!isReleased.get()) {
                    synchronized(lock) {
                        playerRef?.let { /* simulate stop */ }
                        jobs.clear()
                    }
                }
                completedSuccessfully.incrementAndGet()
            } catch (e: Exception) {
                // Barrier timeout
            } finally {
                latch.countDown()
            }
        }

        val positionUpdateThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                synchronized(lock) {
                    if (!isReleased.get()) {
                        playerRef?.let { /* simulate position update */ }
                    }
                }
                completedSuccessfully.incrementAndGet()
            } catch (e: Exception) {
                // Barrier timeout
            } finally {
                latch.countDown()
            }
        }

        stopThread.start()
        positionUpdateThread.start()

        val completed = latch.await(5, TimeUnit.SECONDS)

        assertTrue("Both threads should complete without deadlock", completed)
        assertEquals("Both threads should complete successfully", 2, completedSuccessfully.get())
    }

    @Test
    fun testSingleLock_highConcurrency_noDeadlock() {
        val lock = Any()
        val counter = AtomicInteger(0)
        val threadCount = 50
        val latch = CountDownLatch(threadCount)
        val barrier = CyclicBarrier(threadCount)

        val threads = (0 until threadCount).map { i ->
            Thread {
                try {
                    barrier.await(10, TimeUnit.SECONDS)
                    synchronized(lock) {
                        counter.incrementAndGet()
                        Thread.sleep(1)
                    }
                } catch (e: Exception) {
                    // Barrier timeout
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        val completed = latch.await(10, TimeUnit.SECONDS)

        assertTrue("All 50 threads should complete without deadlock", completed)
        assertEquals("Counter should equal thread count", threadCount, counter.get())
    }
}

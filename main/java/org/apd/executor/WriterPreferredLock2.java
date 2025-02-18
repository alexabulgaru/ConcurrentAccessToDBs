package org.apd.executor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WriterPreferredLock2 implements ReadWriteLock {
	private int readers = 0;  // number of readers holding the read lock
	private int writers = 0;  // number of writers holding the write lock
	private int writeRequests = 0;  // number of pending write lock requests

	// provides mutual exclusion; true ensures that threads acquire the lock in the order they requested it
	private final ReentrantLock lock = new ReentrantLock(true);
	private final Condition canRead = lock.newCondition();
	private final Condition canWrite = lock.newCondition();

	@Override
	public void readLock() throws InterruptedException {
		// the current thread attempts to get the lock; if it is not available, the thread blocks until it can acquire it
		lock.lock();
		try {
			// if a writer holds the lock or there are writers waiting to acquire the lock
			while (writers > 0 || writeRequests > 0) {
				// releases the lock, wait until it is signaled, and re-acquires the lock
				canRead.await();
			}
			readers++;
		} finally {
			// ensures that the lock is released even if there is an exception
			lock.unlock();
		}
	}

	@Override
	public void readUnlock() {
		lock.lock();
		try {
			// indicates that the reader is no longer in the possession of the lock
			readers--;
			if (readers == 0 && writeRequests > 0) {
				// signals one waiting writer to proceed in getting the write lock
				canWrite.signal();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void writeLock() throws InterruptedException {
		lock.lock();
		try {
			// writer is requesting to access the lock
			writeRequests++;
			while (readers > 0 || writers > 0) {
				canWrite.await();
			}
			// indicates that the writer is no longer waiting
			writeRequests--;
			// indicates that a writer now has acquired the lock
			writers++;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void writeUnlock() {
		lock.lock();
		try {
			writers--;
			// indicates that there are writers waiting
			if (writeRequests > 0) {
				// signals one waiting writer
				canWrite.signal();
			} else {
				// signals all waiting writers
				canRead.signalAll();
			}
		} finally {
			lock.unlock();
		}
	}
}

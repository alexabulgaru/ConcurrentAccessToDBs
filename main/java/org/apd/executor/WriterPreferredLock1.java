package org.apd.executor;

public class WriterPreferredLock1 implements ReadWriteLock {
	private int readers = 0;  // number of readers holding the read lock
	private int writers = 0;  // number of writers holding the write lock
	private int writeRequests = 0;  // number of pending write lock requests

	@Override
	public synchronized void readLock() throws InterruptedException {
		// if a writer is active, readers must wait
		// if there are pending write requests, in order to prioritize writers, new readers will wait even if there is
		// no writer active
		while (writers > 0 || writeRequests > 0) {
			wait();
		}
		readers++;
	}

	@Override
	public synchronized void readUnlock() {
		// releases the read lock
		readers--;
		if (readers == 0) {
			notifyAll();
		}
	}

	@Override
	public synchronized void writeLock() throws InterruptedException {
		writeRequests++;  // prioritizes writers over readers
		while (readers > 0 || writers > 0) {
			wait();
		}
		// if there are no active readers and no active writers, it decrements the writeRequest because this writer is
		// about to acquire the write lock
		writeRequests--;
		// this thread got the lock
		writers++;
	}

	@Override
	public synchronized void writeUnlock() {
		writers--;
		// while both readers and writers can acquire this lock now, considering this is the writer preferred lock,
		// writers will have priority
		notifyAll();
	}
}

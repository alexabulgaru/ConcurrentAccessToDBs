package org.apd.executor;

public class ReaderPreferredLock implements ReadWriteLock {
	private int readers = 0;  // keeps track of the number of active readers holding the read lock
	private boolean writerActive = false;  // no writer is active initially

	@Override
	public synchronized void readLock() throws InterruptedException {
		// if the writer is active, the current thread will wait until the writer releases the lock
		while (writerActive) {
			wait();  // current thread will wait until another thread invokes notify
		}
		readers++;
	}

	@Override
	public synchronized void readUnlock() {
		readers--;
		// if there are no more active readers, it notifies all waiting threads
		if (readers == 0) {
			notifyAll();
		}
	}

	@Override
	public synchronized void writeLock() throws InterruptedException {
		// if there are active readers or another writer that is active
		// the current thread will wait until it will get the write lock
		while (readers > 0 || writerActive) {
			wait();
		}
		writerActive = true;
	}

	@Override
	public synchronized void writeUnlock() {
		// the writer has released the write lock
		// while both readers and writers can acquire this lock now, considering this is the reader preferred lock,
		// readers will have priority
		writerActive = false;
		notifyAll();
	}
}


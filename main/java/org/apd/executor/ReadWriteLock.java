package org.apd.executor;

public interface ReadWriteLock {
	void readLock() throws InterruptedException;
	void readUnlock();
	void writeLock() throws InterruptedException;
	void writeUnlock();
}

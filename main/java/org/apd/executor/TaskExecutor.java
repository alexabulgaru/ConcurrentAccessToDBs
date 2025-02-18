package org.apd.executor;

import org.apd.storage.EntryResult;
import org.apd.storage.SharedDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/* DO NOT MODIFY THE METHODS SIGNATURES */
public class TaskExecutor {
    private final SharedDatabase sharedDatabase;

    public TaskExecutor(int storageSize, int blockSize, long readDuration, long writeDuration) {
        sharedDatabase = new SharedDatabase(storageSize, blockSize, readDuration, writeDuration);
    }

    public List<EntryResult> ExecuteWork(int numberOfThreads, List<StorageTask> tasks, LockType lockType) {
        /* IMPLEMENT HERE THE THREAD POOL, ASSIGN THE TASKS AND RETURN THE RESULTS */
        // stores each task's index with its corresponding ReadWriteLock, ensuring that tasks operating
        // on the same index use the appropriate lock
        ConcurrentMap<Integer, ReadWriteLock> locks = new ConcurrentHashMap<>();

        ThreadPool threadPool = new ThreadPool(numberOfThreads);

        // stores pending results of the submitted tasks
        List<FutureResult<EntryResult>> futureResults = new ArrayList<>();

        for (StorageTask task : tasks) {
            // retrieves the existing lock for the task's index or creates a new one based on the lockType
            ReadWriteLock lock = locks.computeIfAbsent(task.index(), idx -> {
                switch (lockType) {
                    case ReaderPreferred:
                        return new ReaderPreferredLock();
                    case WriterPreferred1:
                        return new WriterPreferredLock1();
                    case WriterPreferred2:
                        return new WriterPreferredLock2();
                    default:
                        throw new IllegalArgumentException("Invalid LockType");
                }
            });

            // creates and submits a new Task
            Task t = new Task(task, lock);
            FutureResult<EntryResult> future = threadPool.submit(t);
            futureResults.add(future);
        }

        // retrieves the EntryResult and adds it to the results list
        List<EntryResult> results = new ArrayList<>();
        for (FutureResult<EntryResult> future : futureResults) {
            try {
                EntryResult result = future.get();
                results.add(result);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // tells the ThreadPool to stop accepting new tasks and shuts down all worker threads
        threadPool.shutdown();

        return results;
    }

    public List<EntryResult> ExecuteWorkSerial(List<StorageTask> tasks) {
        var results = tasks.stream().map(task -> {
            try {
                if (task.isWrite()) {
                    return sharedDatabase.addData(task.index(), task.data());
                } else {
                    return sharedDatabase.getData(task.index());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();

        return results.stream().toList();
    }

    private class ThreadPool {
        private final PoolWorker[] threads;  // will execute tasks
        private final List<Runnable> taskQueue;  // queue for pending Runnable tasks
        private volatile boolean isRunning = true;  // indicator that the thread pool is active

        public ThreadPool(int numberOfThreads) {
            taskQueue = new ArrayList<>();
            threads = new PoolWorker[numberOfThreads];

            // creates an array of pool workers and starts each poll worker thread
            for (int i = 0; i < numberOfThreads; i++) {
                threads[i] = new PoolWorker();
                threads[i].start();
            }
        }

        // submits a task to the thread pool
        public FutureResult<EntryResult> submit(Task task) {
            // retrieves the FutureResult associated with the task
            FutureResult<EntryResult> futureResult = task.getFutureResult();
            // synchronizes taskQueue for thread-safety access
            synchronized (taskQueue) {
                taskQueue.add(task);
                // notifies all waiting threads that a new task is available
                taskQueue.notifyAll();
            }
            return futureResult;
        }

        // shuts down the thread pool
        public void shutdown() {
            isRunning = false;
            synchronized (taskQueue) {
                taskQueue.notifyAll();
            }
            for (PoolWorker thread : threads) {
                thread.interrupt();
            }
        }

        // worker thread that gets and executes tasks from taskQueue
        private class PoolWorker extends Thread {
            public void run() {
                while (true) {
                    Runnable task;
                    synchronized (taskQueue) {
                        while (taskQueue.isEmpty() && isRunning) {
                            try {
                                taskQueue.wait();
                            } catch (InterruptedException e) {
                                // checks loop condition
                            }
                        }
                        if (taskQueue.isEmpty() && !isRunning) {
                            break;
                        }
                        task = taskQueue.removeFirst();
                    }
                    try {
                        task.run();
                    } catch (RuntimeException e) {
                        // handles exceptions
                    }
                }
            }
        }
    }

    private class Task implements Runnable {
        private final StorageTask storageTask;  // the task to be performed
        private final ReadWriteLock lock;  // ReadWriteLock associated with the task's index
        private final FutureResult<EntryResult> futureResult;  // result of the task execution

        public Task(StorageTask storageTask, ReadWriteLock lock) {
            this.storageTask = storageTask;
            this.lock = lock;
            this.futureResult = new FutureResult<>();
        }

        public FutureResult<EntryResult> getFutureResult() {
            return futureResult;
        }

        // executes the task when the thread pool worker runs it
        @Override
        public void run() {
            try {
                EntryResult result;
                if (storageTask.isWrite()) {
                    result = writeOperation(storageTask, lock);
                } else {
                    result = readOperation(storageTask, lock);
                }
                futureResult.setResult(result);
            } catch (Exception e) {
                // captures the errors in futureResult
                futureResult.setException(e);
            }
        }
    }

    // represents the future results of an async computation
    private class FutureResult<T> {
        private T result;
        private Exception exception;
        private boolean isDone = false;  // flag indicating the computation is complete

        public synchronized void setResult(T result) {
            this.result = result;
            this.isDone = true;
            notifyAll();  // notifies any waiting threads
        }

        public synchronized void setException(Exception exception) {
            this.exception = exception;
            this.isDone = true;
            notifyAll();
        }

        // retrieves the result of the computation, blocking if necessary until it is available
        public synchronized T get() throws InterruptedException {
            while (!isDone) {
                wait();
            }
            if (exception != null) {
                throw new RuntimeException(exception);
            }
            return result;
        }
    }

    private EntryResult writeOperation(StorageTask task, ReadWriteLock lock) throws Exception {
        lock.writeLock();  // exclusive access for writing
        try {
            return sharedDatabase.addData(task.index(), task.data());  // performs the write operation
        } finally {
            lock.writeUnlock();  // frees the lock even if an exception occurs
        }
    }

    private EntryResult readOperation(StorageTask task, ReadWriteLock lock) throws Exception {
        lock.readLock();  // allows concurrent reads
        try {
            return sharedDatabase.getData(task.index());  // performs the read operation
        } finally {
            lock.readUnlock();  // frees the lock even if an exception occurs
        }
    }
}

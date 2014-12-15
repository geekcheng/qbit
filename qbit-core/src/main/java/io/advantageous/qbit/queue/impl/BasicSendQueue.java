package io.advantageous.qbit.queue.impl;

import io.advantageous.qbit.queue.SendQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TransferQueue;

/**
 * This is not thread safe.
 * Create a new for every thread by calling BasicQueue.sendQueue().
 * <p>
 * Created by Richard on 9/8/14.
 * @author rhightower
 */
public class BasicSendQueue<T> implements SendQueue<T> {

    private final BlockingQueue<Object> queue;

    private final TransferQueue<Object> transferQueue;

    private final Object[] queueLocal;
    private final int checkBusyEvery;
    private int index;

    private final boolean tryTransfer;

    private final int batchSize;


    private int checkEveryCount = 0;

    public BasicSendQueue(int batchSize, BlockingQueue<Object> queue, boolean tryTransfer, int checkBusyEvery) {
        this.batchSize = batchSize;
        this.queue = queue;
        queueLocal = new Object[batchSize];
        if (queue instanceof TransferQueue && tryTransfer) {
            transferQueue = ((TransferQueue) queue);
            this.tryTransfer = true;
        } else {
            this.tryTransfer = false;
            transferQueue = null;
        }
        this.checkBusyEvery = checkBusyEvery;
    }

    public boolean shouldBatch() {

        if (tryTransfer) {
            return !transferQueue.hasWaitingConsumer();

        }

        return true;//might be other ways to determine this like flow control, not implemented yet.

    }

    @Override
    public void send(T item) {
        queueLocal[index] = item;
        index++;
        flushIfOverBatch();
    }

    @Override
    public void sendAndFlush(T item) {

        send(item);
        flushSends();
    }

    @SafeVarargs
    @Override
    public final void sendMany(T... items) {
        flushSends();
        sendArray(items);
    }

    @Override
    public void sendBatch(Iterable<T> items) {
        flushSends();
        final Object[] array = objectArray(items);
        sendArray(array);
    }

    @Override
    public void sendBatch(Collection<T> items) {
        flushSends();
        final Object[] array = objectArray(items);
        sendArray(array);

    }


    private void flushIfOverBatch() {

        if (index >= batchSize) {
            sendLocalQueue();
        } else if (tryTransfer) {
            checkEveryCount++;
            if (checkEveryCount > this.checkBusyEvery && !shouldBatch()) {
                checkEveryCount = 0;
                sendLocalQueue();
            }
        }
    }

    @Override
    public void flushSends() {
        if (index > 0) {
            sendLocalQueue();
        }
    }

    private void sendLocalQueue() {
        final Object[] copy = fastObjectArraySlice(queueLocal, 0, index);
        sendArray(copy);
        index = 0;
    }

    private void sendArray(
            final Object[] array) {

        if (tryTransfer) {
            if (!transferQueue.tryTransfer(array)) {
                transferQueue.offer(array);
            }
        } else {
            try {
                queue.put(array);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Unable to send", e);
            }
        }
    }

    static Object[] objectArray(final Iterable iter) {
        if (iter instanceof Collection) {
            final Collection collection = (Collection) iter;
            return collection.toArray(new Object[collection.size()]);
        } else {
            return objectArray(list(iter));
        }
    }

    static <V> List<V> list(final Iterable<V> iterable) {
        final List<V> list = new ArrayList<>();
        for (V o : iterable) {
            list.add(o);
        }
        return list;
    }

    static Object[] fastObjectArraySlice(final Object[] array,
                                         final int start,
                                         final int end) {
        final int newLength = end - start;
        final Object[] newArray = new Object[newLength];
        System.arraycopy(array, start, newArray, 0, newLength);
        return newArray;
    }
}
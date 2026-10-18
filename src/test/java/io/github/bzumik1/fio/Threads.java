package io.github.bzumik1.fio;

import java.util.List;
import java.util.concurrent.Future;

final class Threads {

    private Threads() {
    }

    static void awaitWaitingForLock(List<Thread> threads, List<? extends Future<?>> calls) {
        while (calls.stream().noneMatch(Future::isDone)
                && (threads.size() < calls.size() || threads.stream().anyMatch(thread -> !isWaiting(thread)))) {
            Thread.onSpinWait();
        }
    }

    private static boolean isWaiting(Thread thread) {
        return thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING;
    }
}

package com.madisruptor.demo07_custom_disruptor;

import java.util.concurrent.locks.LockSupport;

/**
 * ══════════════════════════════════════════════════════════
 *  内置的等待策略实现
 * ══════════════════════════════════════════════════════════
 */
public class MiniWaitStrategies {

    /**
     * Yielding 等待策略:
     *   先自旋 100 次，然后 Thread.yield()
     *   适合低延迟场景
     */
    public static class YieldingWait implements MiniWaitStrategy {
        private static final int SPIN_TRIES = 100;

        @Override
        public long waitFor(long sequence, MiniSequence cursor) throws InterruptedException {
            int counter = SPIN_TRIES;

            while (cursor.get() < sequence) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (counter > 0) {
                    counter--;
                } else {
                    Thread.yield();
                }
            }

            return cursor.get();
        }

        @Override
        public String toString() {
            return "YieldingWait";
        }
    }

    /**
     * Sleeping 等待策略:
     *   自旋 → yield → sleep(1ns)
     *   三段式，节省 CPU
     */
    public static class SleepingWait implements MiniWaitStrategy {
        private static final int RETRIES = 200;

        @Override
        public long waitFor(long sequence, MiniSequence cursor) throws InterruptedException {
            int counter = RETRIES;

            while (cursor.get() < sequence) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (counter > 100) {
                    counter--;  // 前 100 次: 自旋
                } else if (counter > 0) {
                    counter--;
                    Thread.yield();  // 第 100-200 次: yield
                } else {
                    LockSupport.parkNanos(1L);  // 之后: sleep
                }
            }

            return cursor.get();
        }

        @Override
        public String toString() {
            return "SleepingWait";
        }
    }

    /**
     * BusySpin 等待策略:
     *   纯自旋等待，延迟最低但 CPU 占用最高
     */
    public static class BusySpinWait implements MiniWaitStrategy {
        @Override
        public long waitFor(long sequence, MiniSequence cursor) throws InterruptedException {
            while (cursor.get() < sequence) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                Thread.onSpinWait();  // JDK 9+ 自旋提示
            }
            return cursor.get();
        }

        @Override
        public String toString() {
            return "BusySpinWait";
        }
    }
}

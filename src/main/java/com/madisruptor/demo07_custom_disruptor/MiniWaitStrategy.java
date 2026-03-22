package com.madisruptor.demo07_custom_disruptor;

/**
 * 等待策略接口
 */
@FunctionalInterface
public interface MiniWaitStrategy {

    /**
     * 等待指定序号可用
     *
     * @param sequence 期望的序号
     * @param cursor   当前的生产者游标
     * @return 实际可用的最大序号
     */
    long waitFor(long sequence, MiniSequence cursor) throws InterruptedException;
}

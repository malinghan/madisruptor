package com.madisruptor.demo07_custom_disruptor;

/**
 * 事件处理器接口
 */
@FunctionalInterface
public interface MiniEventHandler<E> {

    /**
     * @param event      当前事件
     * @param sequence   事件序号
     * @param endOfBatch 是否是当前批次的最后一个事件
     */
    void onEvent(E event, long sequence, boolean endOfBatch) throws Exception;
}

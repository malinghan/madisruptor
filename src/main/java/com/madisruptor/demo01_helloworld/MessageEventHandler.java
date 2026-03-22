package com.madisruptor.demo01_helloworld;

import com.lmax.disruptor.EventHandler;

/**
 * 事件处理器 - 消费者实现此接口处理事件
 *
 * 关键点:
 * 1. onEvent 会在独立线程中被调用
 * 2. sequence 是事件的全局递增序号
 * 3. endOfBatch 标识是否是当前批次的最后一个事件
 *    - 可以利用它做批量提交（如批量写数据库）
 */
public class MessageEventHandler implements EventHandler<MessageEvent> {

    @Override
    public void onEvent(MessageEvent event, long sequence, boolean endOfBatch) throws Exception {
        System.out.printf("[消费者] 线程: %-20s | 序号: %-6d | 批次结束: %-5s | 消息: %s%n",
                Thread.currentThread().getName(),
                sequence,
                endOfBatch,
                event.getMessage());
    }
}

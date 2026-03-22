package com.madisruptor.demo01_helloworld;

import com.lmax.disruptor.EventFactory;

/**
 * 事件工厂 - 用于在 RingBuffer 初始化时预创建所有 Event 对象
 *
 * 关键点:
 * 1. RingBuffer 在创建时会调用 newInstance() 填满整个数组
 * 2. 这些对象会被循环复用，不再创建新对象
 * 3. 这就是 Disruptor "零GC" 的秘密之一
 */
public class MessageEventFactory implements EventFactory<MessageEvent> {

    @Override
    public MessageEvent newInstance() {
        return new MessageEvent();
    }
}

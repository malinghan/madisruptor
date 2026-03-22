package com.madisruptor.demo02_ringbuffer;

/**
 * 简单的 Long 事件
 */
public class LongEvent {

    private long value;

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public void clear() {
        this.value = 0;
    }

    @Override
    public String toString() {
        return "LongEvent{value=" + value + "}";
    }
}

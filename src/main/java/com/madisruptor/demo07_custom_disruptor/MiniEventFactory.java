package com.madisruptor.demo07_custom_disruptor;

/**
 * 事件工厂接口
 */
@FunctionalInterface
public interface MiniEventFactory<E> {
    E newInstance();
}

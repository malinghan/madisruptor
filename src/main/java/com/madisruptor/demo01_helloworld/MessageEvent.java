package com.madisruptor.demo01_helloworld;

/**
 * 事件对象 - 在 RingBuffer 中传递的数据载体
 *
 * 关键点:
 * 1. Event 是一个普通的 POJO
 * 2. 必须有无参构造函数（供 EventFactory 使用）
 * 3. 通过 setter 覆盖写入数据，对象会被循环复用
 */
public class MessageEvent {

    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 清理事件数据，防止内存泄漏
     * 在消费完成后调用，释放大对象引用
     */
    public void clear() {
        this.message = null;
    }

    @Override
    public String toString() {
        return "MessageEvent{message='" + message + "'}";
    }
}

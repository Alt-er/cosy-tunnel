package cn.com.apexedu.forward.message;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Message implements Serializable {

    final private static ConcurrentHashMap<Integer, Class<? extends Message>> messageClassMap = new ConcurrentHashMap<>();


    final public static int FORWARD_DATA_MESSAGE = 0;
    final public static int FORWARD_REQUEST_MESSAGE = 1;
    final public static int FORWARD_RESPONSE_MESSAGE = 2;
    final public static int CREATE_FORWARD_INSTANCE_REQUEST_MESSAGE = 3;
    final public static int CREATE_FORWARD_INSTANCE_RESPONSE_MESSAGE = 4;
    final public static int CLOSE_FORWARD_INSTANCE_RESPONSE_MESSAGE = 5;

    static {
        // 记录各个message类型的class
        messageClassMap.put(FORWARD_DATA_MESSAGE, ForwardDataMessage.class);
        messageClassMap.put(FORWARD_REQUEST_MESSAGE, ForwardRequestMessage.class);
        messageClassMap.put(FORWARD_RESPONSE_MESSAGE, ForwardResponseMessage.class);
        messageClassMap.put(CREATE_FORWARD_INSTANCE_REQUEST_MESSAGE, CreateForwardInstanceRequestMessage.class);
        messageClassMap.put(CREATE_FORWARD_INSTANCE_RESPONSE_MESSAGE, CreateForwardInstanceResponseMessage.class);
        messageClassMap.put(CLOSE_FORWARD_INSTANCE_RESPONSE_MESSAGE, CloseForwardInstanceRequestMessage.class);
    }

    private int sequenceId;

    public abstract int getMessageType();

    public int getSequenceId() {
        return sequenceId;
    }

    public static Class<? extends Message> getMessageClassByType(int type) {
        return messageClassMap.get(type);
    }
}

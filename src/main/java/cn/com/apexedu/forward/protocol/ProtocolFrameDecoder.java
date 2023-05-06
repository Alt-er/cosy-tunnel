package cn.com.apexedu.forward.protocol;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class ProtocolFrameDecoder extends LengthFieldBasedFrameDecoder {


    public ProtocolFrameDecoder() {
        // 限制一帧最大10M 一般达不到这个数
        super(1024 * 1024 * 10, 12, 4, 0, 0);
    }
}

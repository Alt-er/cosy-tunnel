package cn.com.apexedu.client.tcp;

import io.netty.buffer.ByteBuf;

public class TcpChecksumCalculator {

    /**
     * 计算TCP校验和
     * @param buf ByteBuf对象
     * @param ipSrc 源IP地址
     * @param ipDst 目的IP地址
     * @return TCP校验和
     */
    public static short calculateTcpChecksum(ByteBuf buf, int ipSrc, int ipDst) {
        int tcpLength = buf.readableBytes();
        int sum = 0;

        // 计算TCP伪首部
        sum += (ipSrc >> 16) & 0xFFFF;
        sum += ipSrc & 0xFFFF;
        sum += (ipDst >> 16) & 0xFFFF;
        sum += ipDst & 0xFFFF;
        sum += 6; // Protocol
        sum += tcpLength;

        // 计算TCP首部和数据部分
        int tcpHeaderPos = buf.readerIndex();
        int count = tcpLength >> 1;
        for (int i = 0; i < count; i++) {
            sum += buf.getUnsignedShort(tcpHeaderPos);
            tcpHeaderPos += 2;
        }

        if ((tcpLength & 1) == 1) {
            sum += (buf.getByte(tcpHeaderPos) & 0xFF) << 8;
        }

        // 处理溢出
        while (sum >> 16 != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        // 取反得到校验和
        sum = ~sum;
        sum &= 0xFFFF;
        return (short) sum;
    }
}

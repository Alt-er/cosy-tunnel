package cn.com.apexedu.client.proxy;

import cn.com.apexedu.client.tcp.ConnectionManager;
import cn.com.apexedu.client.tcp.TcpChecksumCalculator;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.tun.InetProtocol;
import org.drasyl.channel.tun.Tun4Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.drasyl.channel.tun.Tun4Packet.*;

public class TunProxyServerHandler extends ChannelDuplexHandler {

    final private static Logger logger = LoggerFactory.getLogger(TunProxyServerHandler.class);


    private static void printPacketInfo(Tun4Packet p) {
        int srcPort1 = p.content().getUnsignedShort(INET4_HEADER_LENGTH + 0);
        int dstPort1 = p.content().getUnsignedShort(INET4_HEADER_LENGTH + 2);
        logger.debug(p.protocol() + "------" + p.sourceAddress().getHostAddress() + ":" + srcPort1
                + "---->" + p.destinationAddress().getHostAddress() + ":" + dstPort1 + "    check:" + p.verifyChecksum());

        System.out.println(p.protocol() + "------" + p.sourceAddress().getHostAddress() + ":" + srcPort1
                + "---->" + p.destinationAddress().getHostAddress() + ":" + dstPort1 + "    check:" + p.verifyChecksum());
    }


    //
    private static boolean isTransitServerBack(int ipAddress, int port) throws UnknownHostException {
        return ConnectionManager.getTransitServerIp() == ipAddress && ConnectionManager.getTransitServerPort() == port;
    }


    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) throws UnknownHostException {

//        logger.debug(msg.getClass().toString());
        if(!(msg instanceof Tun4Packet)){
            ctx.writeAndFlush(msg);
//            logger.debug("收到非IPv4协议数据包,已忽略");
            return;
        }
        Tun4Packet packet = (Tun4Packet) msg;
        if (packet.protocol() != InetProtocol.TCP.decimal) {
            ctx.writeAndFlush(packet);
//            logger.debug("收到非TCP协议数据包,已忽略");
            return;
        }
        ByteBuf originalContent = packet.content();

        int totals = originalContent.readableBytes();
        ConnectionManager.totalBytes.addAndGet(totals);


        int src = originalContent.getInt(INET4_SOURCE_ADDRESS);
        int dest = originalContent.getInt(INET4_DESTINATION_ADDRESS);

        int srcPort = originalContent.getUnsignedShort(INET4_HEADER_LENGTH + 0);
        int destPort = originalContent.getUnsignedShort(INET4_HEADER_LENGTH + 2);


//        printPacketInfo(packet);

        final ByteBuf content = packet.content();//.retain();

        // 如果是中转代理服务返回的数据
        if (isTransitServerBack(src, srcPort)) {

            ConnectionManager.downloadBytes.addAndGet(totals);

            ConnectionManager.getPortPool().logLastUsedTime(destPort);

            int[] originalConnection = ConnectionManager.getOriginalConnectionByTransit(ConnectionManager.mergeTransit(dest, destPort));
            int originalSrc = originalConnection[0];
            int originalSrcPort = originalConnection[1];
            int originalDest = originalConnection[2];
            int originalDestPort = originalConnection[3];

            content.setInt(INET4_SOURCE_ADDRESS, originalDest);
            content.setInt(INET4_DESTINATION_ADDRESS, originalSrc);
            content.setShort(INET4_HEADER_LENGTH + 0, originalDestPort);
            content.setShort(INET4_HEADER_LENGTH + 2, originalSrcPort);

        } else {

            long transitIpAndPort = ConnectionManager.getTransitIpAndPort(src, srcPort, dest, destPort);

            // skip
            if(transitIpAndPort == -1){
                ctx.writeAndFlush(packet);
                logger.debug("不拦截已配置的websocket服务流量,将忽略");
                return;
            }

            ConnectionManager.uploadBytes.addAndGet(totals);

            int[] IpAndPort = ConnectionManager.splitTransit(transitIpAndPort);

            ConnectionManager.getPortPool().logLastUsedTime(IpAndPort[1]);

            content.setInt(INET4_SOURCE_ADDRESS, IpAndPort[0]);
            content.setInt(INET4_DESTINATION_ADDRESS, ConnectionManager.getTransitServerIp());
            content.setShort(INET4_HEADER_LENGTH + 0, IpAndPort[1]);
            content.setShort(INET4_HEADER_LENGTH + 2, ConnectionManager.getTransitServerPort());
        }


        // 将ip数据包校验和清0
        content.setShort(INET4_HEADER_CHECKSUM, 0);
        final int headerChecksum = calculateChecksum(content);
        // 重新设置ip数据包校验和
        content.setShort(INET4_HEADER_CHECKSUM, headerChecksum);

        // 计算TCP数据包位置
        int startIndex = INET4_HEADER_LENGTH;
        int length = content.readableBytes() - startIndex;
        // 将校验和置零
        content.setShort(INET4_HEADER_LENGTH + 16, 0);
        // 重新计算校验和
        int checksum = TcpChecksumCalculator.calculateTcpChecksum(content.slice(startIndex, length), content.getInt(INET4_SOURCE_ADDRESS), content.getInt(INET4_DESTINATION_ADDRESS));
        content.setShort(INET4_HEADER_LENGTH + 16, checksum);
        Tun4Packet tun4Packet = new Tun4Packet(content);

//        printPacketInfo(tun4Packet);
//        System.out.println("---------------------------------------------------");
        ctx.writeAndFlush(tun4Packet);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
        ctx.flush();
    }
}

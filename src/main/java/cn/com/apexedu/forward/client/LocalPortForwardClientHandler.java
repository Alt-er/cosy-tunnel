package cn.com.apexedu.forward.client;

import cn.com.apexedu.forward.message.CloseForwardInstanceRequestMessage;
import cn.com.apexedu.forward.message.CreateForwardInstanceResponseMessage;
import cn.com.apexedu.forward.message.ForwardDataMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

@Deprecated
public class LocalPortForwardClientHandler extends ChannelInboundHandlerAdapter {
    static final Logger logger = LoggerFactory.getLogger(LocalPortForwardClientHandler.class);

    // 连接id 和 handler的关系
    final private static ConcurrentHashMap<Integer, LocalPortForwardClientHandler> connectionIdHandlerMap = new ConcurrentHashMap<>();

    public static LocalPortForwardClientHandler getHandler(int connectionId) {
        return connectionIdHandlerMap.get(connectionId);
    }

    // 用于传递数据的主通道
    private Channel mainChannel;
    // 与本地服务建立的通道
    private Channel localChannel;
    private String localIp;
    private int localPort;
    private int remotePort;
    private int connectionId;
    private ChannelPromise channelPromise;

    public Channel getMainChannel() {
        return mainChannel;
    }

    public void setMainChannel(Channel mainChannel) {
        this.mainChannel = mainChannel;
    }

    public Channel getLocalChannel() {
        return localChannel;
    }

    public void setLocalChannel(Channel localChannel) {
        this.localChannel = localChannel;
    }

    public String getLocalIp() {
        return localIp;
    }

    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
    }

    public LocalPortForwardClientHandler(Channel mainChannel, String localIp, int localPort, int remotePort, int connectionId) {
        this.mainChannel = mainChannel;
        this.localIp = localIp;
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.connectionId = connectionId;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.localChannel = ctx.channel();
        connectionIdHandlerMap.put(connectionId, this);
        this.mainChannel.writeAndFlush(new CreateForwardInstanceResponseMessage(this.connectionId, true, "本地连接已就绪"));
        logger.debug("本地端口转发客户端Handler:{}:{} 连接ID:{} 本地资源连接已准备就绪" ,localIp , localPort, connectionId);
        this.localChannel.closeFuture().addListener(future -> {
            logger.debug("本地端口转发客户端Handler:{}:{} 连接ID:{} 与本地资源断开了连接,通知服务端断开与用户的链接" ,localIp , localPort, connectionId);
            // 服务端监听的远程端口与用户断开了连接 通知客户端也断开对应的本地资源连接
            mainChannel.writeAndFlush(new CloseForwardInstanceRequestMessage(connectionId, localIp, localPort, remotePort));

            // 删除连接与handler的关系
            this.connectionIdHandlerMap.remove(connectionId);
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        mainChannel.writeAndFlush(new ForwardDataMessage( connectionId, bytes));
        logger.debug("本地端口转发客户端Handler:{}:{} 从本地服务收到数据包, 长度{}B 将转发至服务端..." ,localIp , localPort, bytes.length);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.channel().close();
    }
}

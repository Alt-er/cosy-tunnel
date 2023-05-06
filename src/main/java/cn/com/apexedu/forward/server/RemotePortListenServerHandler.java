package cn.com.apexedu.forward.server;

import cn.com.apexedu.forward.message.CloseForwardInstanceRequestMessage;
import cn.com.apexedu.forward.message.CreateForwardInstanceRequestMessage;
import cn.com.apexedu.forward.message.ForwardDataMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Deprecated
public class RemotePortListenServerHandler extends ChannelInboundHandlerAdapter {
    static final Logger logger = LoggerFactory.getLogger(RemotePortListenServerHandler.class);

    // 用于传递数据的主通道
    private Channel mainChannel;
    // 与外部连接者建立的通道
    private Channel externalChannel;
    private String localIp;
    private int localPort;
    private int remotePort;
    private int connectionId;
    private ChannelPromise readyPromise;

    public void ready() {
        readyPromise.setSuccess();
    }

    public void setFailure(Throwable th) {
        logger.error("远程端口监听服务Handler:{} 内部连接创建失败,将断开外部连接.", remotePort, th);
        readyPromise.setFailure(th);
        externalChannel.close();

    }

    public Channel getMainChannel() {
        return mainChannel;
    }

    public void setMainChannel(Channel mainChannel) {
        this.mainChannel = mainChannel;
    }

    public Channel getExternalChannel() {
        return externalChannel;
    }

    public void setExternalChannel(Channel externalChannel) {
        this.externalChannel = externalChannel;
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

    public RemotePortListenServerHandler(Channel mainChannel, String localIp, int localPort, int remotePort) {
        this.mainChannel = mainChannel;
        this.localIp = localIp;
        this.localPort = localPort;
        this.remotePort = remotePort;
    }

    // 连接id 和 handler的关系
    final private static ConcurrentHashMap<Integer, RemotePortListenServerHandler> connectionIdHandlerMap = new ConcurrentHashMap<>();

    final private static AtomicInteger connectionIdManager = new AtomicInteger();

    public static RemotePortListenServerHandler getHandler(int connectionId) {
        return connectionIdHandlerMap.get(connectionId);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.readyPromise = ctx.newPromise();
        this.connectionId = connectionIdManager.incrementAndGet();
        logger.debug("远程端口监听服务Handler:{} 获取到一个新连接,通知内部服务准备连接,连接ID:{}", remotePort, connectionId);
        mainChannel.writeAndFlush(new CreateForwardInstanceRequestMessage(connectionId, localIp, localPort, remotePort));
        // 记录
        this.connectionIdHandlerMap.put(connectionId, this);
        this.externalChannel = ctx.channel();
        // 连接关闭后 做一些操作
        this.externalChannel.closeFuture().addListener(future -> {
            logger.debug("远程端口监听服务Handler:{} 连接ID:{} 与用户连接断开", remotePort, connectionId);
            // 服务端监听的远程端口与用户断开了连接 通知客户端也断开对应的本地资源连接
            mainChannel.writeAndFlush(new CloseForwardInstanceRequestMessage(connectionId, localIp, localPort, remotePort));

            // 删除连接与handler的关系
            this.connectionIdHandlerMap.remove(connectionId);

        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        readyPromise.addListener(future -> {
            //在这里执行回调逻辑
            ByteBuf buf = (ByteBuf) msg;
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            logger.debug("远程端口监听服务Handler:{} 连接ID:{} 收到数据包{}B 准备转发到客户端...", remotePort, connectionId, bytes.length);
            mainChannel.writeAndFlush(new ForwardDataMessage(connectionId, bytes));
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }

}

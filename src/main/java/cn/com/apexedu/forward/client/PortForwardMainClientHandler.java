package cn.com.apexedu.forward.client;


import cn.com.apexedu.forward.message.*;
import cn.com.apexedu.forward.services.PortForwardOnlineManager;
import cn.com.apexedu.forward.services.PortForwardInfo;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class PortForwardMainClientHandler extends SimpleChannelInboundHandler<Message> {
    static final Logger logger = LoggerFactory.getLogger(PortForwardMainClientHandler.class);

    private String serverIp;

    private int port;

    public PortForwardMainClientHandler(String serverIp, int port) {
        this.serverIp = serverIp;
        this.port = port;
    }
//    public static ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 开始注册端口转发
        ctx.writeAndFlush(new ForwardRequestMessage("admin", "apexsoft", "127.0.0.1", 12345, 17890));
        ctx.writeAndFlush(new ForwardRequestMessage("admin", "apexsoft", "db1dev.apexedu.com.cn", 1521, 17890));
        ctx.writeAndFlush(new ForwardRequestMessage("admin", "apexsoft", "db1dev.apexedu.com.cn", 1521, 17891));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message message) throws Exception {
        if (message instanceof ForwardResponseMessage) {
            ForwardResponseMessage msg = (ForwardResponseMessage) message;
            logger.debug("端口转发客户端Handler:{}:{} 收到服务端端口转发注册状态:{} , 信息:{} ,localIp:{} , localPort:{} , remotePort:{} ",
                    serverIp, port,
                    msg.isSuccess(), msg.getMessage()
                    , msg.getLocalIp(), msg.getLocalPort(), msg.getRemotePort());
            // 记录一下
            if(msg.isSuccess())
                PortForwardOnlineManager.add(new PortForwardInfo(msg.getLocalIp(), msg.getLocalPort(), msg.getRemotePort()));
        } else if (message instanceof CreateForwardInstanceRequestMessage) {
            CreateForwardInstanceRequestMessage msg = (CreateForwardInstanceRequestMessage) message;
            // 创建好本地连接
            new LocalPortForwardClient(ctx.channel(), msg.getLocalIp(), msg.getLocalPort(), msg.getRemotePort(), msg.getConnectionId());
            // 告诉服务端已经准备好了
        } else if (message instanceof ForwardDataMessage) {
            // 转发数据
            ForwardDataMessage msg = (ForwardDataMessage) message;
            LocalPortForwardClientHandler handler = LocalPortForwardClientHandler.getHandler(msg.getConnectionId());
            Channel localChannel = handler.getLocalChannel();
            ByteBuf buffer = localChannel.alloc().buffer(msg.getPayload().length);
            buffer.writeBytes(msg.getPayload());
            localChannel.writeAndFlush(buffer);
            logger.debug("端口转发客户端Handler:{}:{} 连接ID:{} 收到从服务端发送的数据包{}B 准备转发到本地服务...", serverIp, port, msg.getConnectionId(), msg.getPayload().length);
        } else if (message instanceof CloseForwardInstanceRequestMessage) {
            CloseForwardInstanceRequestMessage msg = (CloseForwardInstanceRequestMessage) message;
            // 关闭连接的请求
            LocalPortForwardClientHandler handler = LocalPortForwardClientHandler.getHandler(msg.getConnectionId());
            handler.getLocalChannel().close();
            logger.debug("端口转发客户端Handler:{}:{} 连接ID:{} 收到服务端的连接关闭请求 ,将关闭与本地资源的连接", serverIp, port, msg.getConnectionId());
        }
    }

}

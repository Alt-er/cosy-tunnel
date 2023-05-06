package cn.com.apexedu.forward.server;

import cn.com.apexedu.forward.message.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义的Handler需要继承Netty规定好的HandlerAdapter
 * 才能被Netty框架所关联，有点类似SpringMVC的适配器模式
 * <p>
 * 端口转发服务数据处理器
 **/
@Deprecated
public class PortForwardMainServerHandler extends SimpleChannelInboundHandler<Message> {
    static final Logger logger = LoggerFactory.getLogger(PortForwardMainServerHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message message) throws Exception {
//        logger.debug("收到客户端" + ctx.channel().remoteAddress() + "发送的消息：" + message);
        if (message instanceof ForwardRequestMessage) {
            ForwardRequestMessage msg = (ForwardRequestMessage) message;
            // 验证登录
            if ("admin".equals(msg.getUsername()) && "apexsoft".equals(msg.getPassword())) {
                // 权限验证成功 创建端口监听
                new RemotePortListenServer(ctx.channel(), msg.getLocalIp(), msg.getLocalPort(), msg.getRemotePort());
            } else {
                // 登录失败
                ctx.channel().writeAndFlush(new ForwardResponseMessage(false, "登录失败,账号或密码错误", msg.getLocalIp(), msg.getLocalPort(), msg.getRemotePort()));
            }
        } else if (message instanceof CreateForwardInstanceResponseMessage) {
            // 这是一个新创建的channel
            CreateForwardInstanceResponseMessage msg = (CreateForwardInstanceResponseMessage) message;
            RemotePortListenServerHandler handler = RemotePortListenServerHandler.getHandler(msg.getConnectionId());
            if (msg.isSuccess()) {
                logger.debug("端口转发服务端Handler:{} 客户端本地资源已连接,可以开始转发数据 , 连接ID:{}", handler.getRemotePort(), msg.getConnectionId());
                // 连接已准备好
                handler.ready();
            } else {
                handler.setFailure(new Exception(msg.getMessage()));
            }
        } else if (message instanceof ForwardDataMessage) {
            // 转发数据
            ForwardDataMessage msg = (ForwardDataMessage) message;
            RemotePortListenServerHandler handler = RemotePortListenServerHandler.getHandler(msg.getConnectionId());
            Channel externalChannel = handler.getExternalChannel();
            ByteBuf buffer = externalChannel.alloc().buffer(msg.getPayload().length);
            buffer.writeBytes(msg.getPayload());
            externalChannel.writeAndFlush(buffer);
        } else if (message instanceof CloseForwardInstanceRequestMessage) {
            CloseForwardInstanceRequestMessage msg = (CloseForwardInstanceRequestMessage) message;
            // 关闭连接的请求
            RemotePortListenServerHandler handler = RemotePortListenServerHandler.getHandler(msg.getConnectionId());
            if (handler != null)
                handler.getExternalChannel().close();
            logger.debug("端口转发服务端Handler:{} 连接ID:{} 收到客户端的连接关闭请求 ,将于外部连接用户断开连接", msg.getRemotePort(), msg.getConnectionId());
        }
    }

//    @Override
//    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        //发送消息给客户端
//        ctx.writeAndFlush(Unpooled.copiedBuffer("服务端已收到消息，并给你发送一个问号?", CharsetUtil.UTF_8));
//    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        //发生异常，关闭通道
        ctx.close();
    }
}
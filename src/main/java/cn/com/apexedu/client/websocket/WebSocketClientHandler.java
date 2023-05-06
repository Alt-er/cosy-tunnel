package cn.com.apexedu.client.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.util.concurrent.DefaultPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


//客户端业务处理类
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private static Logger logger = LoggerFactory.getLogger(WebSocketClientHandler.class);


    private WebSocketClientHandshaker handshaker;

    private ChannelPromise handshakeFuture;

    // 等待转发的连接的promise 可以通知其他对象连接建立完成了
    public final Map<String, DefaultPromise<Integer>> forwardingPromiseMap = new ConcurrentHashMap<>();

    // 正在转发中的连接
    public final Map<Integer, Channel> forwardingLocalChannelMap = new ConcurrentHashMap<>();


    public WebSocketClientHandler() {
//        super(false);
    }

    public void setHandshaker(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public void handlerAdded(ChannelHandlerContext ctx) {
        this.handshakeFuture = ctx.newPromise();
    }

    public ChannelFuture handshakeFuture() {
        return this.handshakeFuture;
    }

    /**
     * 当客户端主动链接服务端的链接后，调用此方法
     *
     * @param channelHandlerContext ChannelHandlerContext
     */
    @Override
    public void channelActive(ChannelHandlerContext channelHandlerContext) {
        logger.trace("WebSocketClientHandler: 与远程服务器连接成功");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        logger.trace("WebSocketClientHandler: websocket通道关闭");
    }

    private void safaClose(Channel ch) {
        if (ch != null && ch.isActive() && ch.isOpen()) {
            ch.close();
        }
    }

    protected void channelRead0(ChannelHandlerContext ctx, Object o) throws Exception {
        // 握手协议返回，设置结束握手
        if (!this.handshaker.isHandshakeComplete()) {
            FullHttpResponse response = (FullHttpResponse) o;
            this.handshaker.finishHandshake(ctx.channel(), response);
            this.handshakeFuture.setSuccess();
            logger.trace("WebSocketClientHandler: 与远程服务器websocket握手完成");
            return;
        } else if (o instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) o;
            // 远程服务器发过来的数据转发给本地
            ByteBuf content = binaryFrame.content();
            byte type = content.readByte();
            if (type == WebSocketClient.FORWARD_DATA_MESSAGE) {
                int connectionId = content.readInt();
                Channel channel = forwardingLocalChannelMap.get(connectionId);
//                if (channel != null && channel.isOpen()) {
                channel.writeAndFlush(content.retain());
                // SimpleChannelInboundHandler 这个handler会释放一次引用 所以需要引用加1
//                content.retain();
                logger.trace("httpProxy接收数据: 连接ID:{} 长度:{}", connectionId, content.readableBytes());
            } else if (type == WebSocketClient.FORWARD_RESPONSE_MESSAGE) {
                byte[] bs = new byte[content.readableBytes()];
                content.readBytes(bs);
                String s = new String(bs);
                String[] split = s.split("\\|");
                String channelId = split[0];
                Integer connectionId = Integer.parseInt(split[1]);

                if (connectionId == -1){
                    logger.error("WebSocketClientHandler: 收到代理请求响应,channelId:{} ,连接ID:{},转发隧道建立失败,服务器拒绝转发,HTTP代理服务未开启或账号密码不正确", channelId, connectionId);
                    DefaultPromise<Integer> promise = forwardingPromiseMap.get(channelId);
                    promise.setFailure(new Throwable("服务器拒绝转发,HTTP代理服务未开启或账号密码不正确"));
                }else{
                    logger.debug("WebSocketClientHandler: 收到代理请求响应,channelId:{} ,连接ID:{},转发隧道建立成功", channelId, connectionId);
                    DefaultPromise<Integer> promise = forwardingPromiseMap.get(channelId);
                    promise.setSuccess(connectionId);
                }



            } else if (type == WebSocketClient.FORWARD_CLOSE_MESSAGE) {
                int connectionId = content.readInt();
                Channel channel = forwardingLocalChannelMap.get(connectionId);
                safaClose(channel);
                forwardingLocalChannelMap.remove(connectionId);
            } else {
                throw new Exception("无效的字节流类型");
            }
        } else if (o instanceof CloseWebSocketFrame) {
            logger.debug("WebSocketClientHandler: 收到远程服务器websocket关闭通知,准备断开连接");
            ctx.close();
        } else if (o instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) o;
        }

    }
}
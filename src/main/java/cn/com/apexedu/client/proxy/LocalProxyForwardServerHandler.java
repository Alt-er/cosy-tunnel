package cn.com.apexedu.client.proxy;

import cn.com.apexedu.client.websocket.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
@Deprecated
public class LocalProxyForwardServerHandler extends ChannelInboundHandlerAdapter {

    private WebSocketClient webSocketClient;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        this.webSocketClient = new WebSocketClient(new URI("ws://localhost:8005/forward"), ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 本地发过来的数据需要通过websocket发到远程服务器上
        webSocketClient.getRemoteChannel().writeAndFlush(new BinaryWebSocketFrame((ByteBuf) msg));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}

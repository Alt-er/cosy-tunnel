package cn.com.apexedu.client.proxy;

import cn.com.apexedu.client.websocket.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.concurrent.DefaultPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyServerHandler extends ChannelInboundHandlerAdapter {
    final private static Logger logger = LoggerFactory.getLogger(HttpProxyServerHandler.class);
    private String host;
    private int port;
    private boolean isConnectMethod = false;
    // 客户端到代理的 channel
    private Channel clientChannel;

    private WebSocketClient webSocketClient;

    public HttpProxyServerHandler(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest httpRequest = (FullHttpRequest) msg;
//            System.out.println(httpRequest);
            isConnectMethod = HttpMethod.CONNECT.equals(httpRequest.method());

            // 解析目标主机host和端口号
            parseHostAndPort(httpRequest);

            logger.debug("HttpProxyServerHandler: 开始代理连接 => {}:{} => {}", host, port, this.webSocketClient.getWebsocketURI());

            // disable AutoRead until remote connection is ready
            clientChannel.config().setAutoRead(false);

            forwardToWebSocket(ctx, httpRequest);
//            forwardToTargetHost(httpRequest);

            // 连接建立成功
        }
    }

//    private void forwardToTargetHost(FullHttpRequest httpRequest) {
//        /**
//         * 建立代理服务器到目标主机的连接
//         */
//        Bootstrap b = new Bootstrap();
//        b.group(clientChannel.eventLoop()) // 和 clientChannel 使用同一个 EventLoop
//                .channel(clientChannel.getClass())
//                .handler(new HttpRequestEncoder());
//        ChannelFuture f = b.connect(host, port);
//        this.remoteChannel = f.channel();
//        f.addListener((ChannelFutureListener) future -> {
//            if (future.isSuccess()) {
//                // connection is ready, enable AutoRead
//                clientChannel.config().setAutoRead(true);
//
//                if (isConnectMethod) {
//                    // CONNECT 请求回复连接建立成功
//                    HttpResponse connectedResponse = new DefaultHttpResponse(httpRequest.protocolVersion(), new HttpResponseStatus(200, "Connection Established"));
//                    clientChannel.writeAndFlush(connectedResponse);
//                } else {
//                    // 普通http请求解析了第一个完整请求，第一个请求也要原样发送到远端服务器
//                    this.remoteChannel.writeAndFlush(httpRequest);
//                }
//
//                /**
//                 * 第一个完整Http请求处理完毕后，不需要解析任何 Http 数据了，直接盲目转发 TCP 流就行了
//                 * 所以无论是连接客户端的 clientChannel 还是连接远端主机的 remoteChannel 都只需要一个 RelayHandler 就行了。
//                 * 代理服务器在中间做转发。
//                 *
//                 * 客户端   --->  clientChannel --->  代理 ---> remoteChannel ---> 远端主机
//                 * 远端主机 --->  remoteChannel  --->  代理 ---> clientChannel ---> 客户端
//                 */
//                clientChannel.pipeline().remove(HttpRequestDecoder.class);
//                clientChannel.pipeline().remove(HttpResponseEncoder.class);
//                clientChannel.pipeline().remove(HttpObjectAggregator.class);
//                clientChannel.pipeline().remove(HttpProxyServerHandler.this);
//                clientChannel.pipeline().addLast(new RelayHandler(this.remoteChannel));
//
//                this.remoteChannel.pipeline().remove(HttpRequestEncoder.class);
//                this.remoteChannel.pipeline().addLast(new RelayHandler(clientChannel));
//            } else {
//                clientChannel.close();
//            }
//        });
//    }

    private void forwardToWebSocket(ChannelHandlerContext ctx, FullHttpRequest httpRequest) {
        // 启动转发 将websocket收到的数据转发给客户端
        if (webSocketClient.handshakeFuture() == null || !webSocketClient.handshakeFuture().isSuccess()) {
            logger.error("HttpProxyServerHandler: 与[{}]未连接成功,无法转发", webSocketClient.getWebsocketURI());
//            clientChannel.config().setAutoRead(true);
            clientChannel.close();
            return;
        }
        DefaultPromise<Integer> forward = webSocketClient.forward(host, port, clientChannel);
        forward.addListener(future -> {
            if (!future.isSuccess()) {
                return;
            }
            Integer now = (Integer) future.getNow();
            if (isConnectMethod) {
                // CONNECT 请求回复连接建立成功
                HttpResponse connectedResponse = new DefaultHttpResponse(httpRequest.protocolVersion(), new HttpResponseStatus(200, "Connection Established"));
                clientChannel.writeAndFlush(connectedResponse);
                logger.debug("HttpProxyServerHandler: 代理连接 => {}:{} 建立成功,连接ID:{} ,准备转发数据", host, port, now);

                // 移除流水线上的handler
                clientChannel.pipeline().remove(HttpRequestDecoder.class);
                clientChannel.pipeline().remove(HttpResponseEncoder.class);
                clientChannel.pipeline().remove(HttpObjectAggregator.class);
                clientChannel.pipeline().remove(HttpProxyServerHandler.this);
                // 入站转发 客户端请求的数据
                clientChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        CompositeByteBuf compositeBuffer = ctx.alloc().compositeBuffer();
                        ByteBuf buffer = ctx.alloc().buffer(5);
                        buffer.writeByte(WebSocketClient.FORWARD_DATA_MESSAGE);
                        buffer.writeInt(now);
                        compositeBuffer.addComponents(true, buffer, (ByteBuf) msg);
                        webSocketClient.getRemoteChannel().writeAndFlush(new BinaryWebSocketFrame(compositeBuffer));
//                        logger.trace("httpProxy发送数据: 连接ID:{} 长度:{}", now, compositeBuffer.readableBytes());
                    }

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        super.channelActive(ctx);
                        webSocketClient.getRemoteChannel().closeFuture().addListener(future -> {
                            // ws 断开 , 关闭此channel
                            safaClose(ctx.channel());
                        });
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                        logger.debug("HttpProxyServerHandler: 代理连接 => {}:{} ,连接ID:{} ,关闭连接,", host, port, now);
                        ByteBuf buffer = ctx.alloc().buffer(6);
                        buffer.writeByte(WebSocketClient.FORWARD_CLOSE_MESSAGE);
                        buffer.writeInt(now);

                        Channel remoteChannel = webSocketClient.getRemoteChannel();
                        if (remoteChannel.isOpen()) {
                            remoteChannel.writeAndFlush(new BinaryWebSocketFrame(buffer));
                        }

                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        cause.printStackTrace();
                        ctx.close();
                    }
                });
            } else {
//                httpRequest.toString(
//                webSocketClient.getRemoteChannel().writeAndFlush(new BinaryWebSocketFrame(httpRequest.))
                logger.error("HttpProxyServerHandler: 收到非connect方法的请求,改请求将被丢弃:{}", httpRequest);
//                CompositeByteBuf compositeBuffer = ctx.alloc().compositeBuffer();
//                ByteBuf buffer = ctx.alloc().buffer(5);
//                buffer.writeByte(WebSocketClient.FORWARD_DATA_MESSAGE);
//                buffer.writeInt(now);
//                buffer.writeBytes(httpRequest);
//                compositeBuffer.addComponents(true, buffer, );
                // 普通http请求解析了第一个完整请求，第一个请求也要原样发送到远端服务器
//                webSocketClient.getRemoteChannel().writeAndFlush(httpRequest);
            }


            clientChannel.config().setAutoRead(true);

        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        clientChannel = ctx.channel();
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        safaClose(ctx.channel());
    }

    private void safaClose(Channel ch) {
        if (ch != null && ch.isActive() && ch.isOpen()) {
            ch.close();
        }
    }

    /**
     * 解析header信息，建立连接
     * HTTP 请求头如下
     * GET http://www.baidu.com/ HTTP/1.1
     * Host: www.baidu.com
     * User-Agent: curl/7.69.1
     * Proxy-Connection:Keep-Alive
     * ---------------------------
     * HTTPS请求头如下
     * CONNECT www.baidu.com:443 HTTP/1.1
     * Host: www.baidu.com:443
     * User-Agent: curl/7.69.1
     * Proxy-Connection: Keep-Alive
     */
    private void parseHostAndPort(HttpRequest httpRequest) {
        String hostAndPortStr;
        if (isConnectMethod) {
            // CONNECT 请求以请求行为准
            hostAndPortStr = httpRequest.uri();
        } else {
            hostAndPortStr = httpRequest.headers().get("Host");
        }
        String[] hostPortArray = hostAndPortStr.split(":");
        host = hostPortArray[0];
        if (hostPortArray.length == 2) {
            port = Integer.parseInt(hostPortArray[1]);
        } else if (isConnectMethod) {
            // 没有端口号，CONNECT 请求默认443端口
            port = 443;
        } else {
            // 没有端口号，普通HTTP请求默认80端口
            port = 80;
        }
    }
}

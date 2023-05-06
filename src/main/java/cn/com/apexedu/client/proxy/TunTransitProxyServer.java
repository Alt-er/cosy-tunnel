package cn.com.apexedu.client.proxy;

import cn.com.apexedu.client.tcp.ConnectionManager;
import cn.com.apexedu.client.tcp.IpAddressRange;
import cn.com.apexedu.client.websocket.WebSocketClient;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.concurrent.DefaultPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class TunTransitProxyServer {

    final private static Logger logger = LoggerFactory.getLogger(TunTransitProxyServer.class);

    public TunTransitProxyServer() {
        int retry = 5;
        for (int i = 0; i < retry; i++) {
            try {
                run();
            } catch (Exception exception) {
                logger.error("create TunTransitProxyServer error: ", exception);
                logger.error("retry : " + (i + 1));
                try {
                    Thread.sleep(200 *(i + 1));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            break;
        }

    }

    private void run() {
        try {
            InetSocketAddress address = new InetSocketAddress(ConnectionManager.getTransitServerAddress(), ConnectionManager.getTransitServerPort());
            ServerBootstrap b = new ServerBootstrap();
            b.group(ConnectionManager.defaultEventLoopGroup).channel(NioServerSocketChannel.class).localAddress(address)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(
//                                    new LoggingHandler(LogLevel.DEBUG),
                                    new ChannelInboundHandler() {
                                        private Integer connectionId;
                                        private WebSocketClient webSocketClient;
                                        private String dest;
                                        private int destPort;

                                        @Override
                                        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {

                                        }

                                        @Override
                                        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

                                        }

                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
                                            int remoteIp = ByteBuffer.wrap(socketAddress.getAddress().getAddress()).getInt();
                                            int remotePort = socketAddress.getPort();

                                            long key = ConnectionManager.mergeTransit(remoteIp, remotePort);
                                            int[] originalConnection = ConnectionManager.getOriginalConnectionByTransit(key);

                                            String dest = ConnectionManager.intToIP(originalConnection[2]);
                                            int destPort = originalConnection[3];
                                            //
                                            ctx.channel().config().setAutoRead(false);

                                            this.webSocketClient = ConnectionManager.getWebSocketClientByDestAddress(dest);

                                            DefaultPromise<Integer> forward = webSocketClient.forward(dest, destPort, ctx.channel());
                                            forward.addListener(future -> {
                                                if (!future.isSuccess()) {
                                                    ctx.channel();
                                                    return;
                                                }
                                                this.connectionId = (Integer) future.getNow();
                                                this.dest = dest;
                                                this.destPort = destPort;
                                                ctx.channel().config().setAutoRead(true);
                                                logger.debug("TunTransitProxyServer: 代理连接 => {}:{} 建立成功,连接ID:{} ,准备转发数据", dest, destPort, connectionId);

                                            });
//                                            System.out.println("Client connected from " + dest + ":" + destPort);
//                                            System.out.println("connect success" );
                                        }

                                        @Override
                                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                            logger.debug("HttpProxyServerHandler: 代理连接 => {}:{} ,连接ID:{} ,关闭连接,", dest, destPort, connectionId);
                                            ByteBuf buffer = ctx.alloc().buffer(6);
                                            buffer.writeByte(WebSocketClient.FORWARD_CLOSE_MESSAGE);
                                            buffer.writeInt(this.connectionId);

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

                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//                                            System.out.println(msg);
//                                            String hexStr = ByteBufUtil.prettyHexDump((ByteBuf) msg);
//                                            System.out.println(hexStr);
                                            CompositeByteBuf compositeBuffer = ctx.alloc().compositeBuffer();
                                            ByteBuf buffer = ctx.alloc().buffer(5);
                                            buffer.writeByte(WebSocketClient.FORWARD_DATA_MESSAGE);
                                            buffer.writeInt(this.connectionId);
                                            compositeBuffer.addComponents(true, buffer, (ByteBuf) msg);
                                            webSocketClient.getRemoteChannel().writeAndFlush(new BinaryWebSocketFrame(compositeBuffer));
//                                            logger.debug("httpProxy发送数据: 连接ID:{} 长度:{}", this.connectionId, compositeBuffer.readableBytes());
                                        }

                                        @Override
                                        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {

                                        }

                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

                                        }

                                        @Override
                                        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {

                                        }


                                        @Override
                                        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

                                        }

                                        @Override
                                        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

                                        }
                                    }
                            );
                        }
                    }).bind().sync();
            //.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
//            bossGroup.shutdownGracefully();
//            workerGroup.shutdownGracefully();
        }
        logger.debug("TunTransitProxyServer create success");
    }
}

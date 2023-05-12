package cn.com.apexedu.client.websocket;

import cn.com.apexedu.client.tcp.ConnectionManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

public class WebSocketClient {

    private static Logger logger = LoggerFactory.getLogger(WebSocketClient.class);

    private final String username;
    private final String password;


    // 本软件与远程服务器之间的websocket连接
    private Channel remoteChannel;

    private WebSocketClientHandler handler;

    public Channel getRemoteChannel() {
        return remoteChannel;
    }

    private void syncCloseChannel(Channel closeChannel, Channel needCloseChannel) {
        if (closeChannel != null) {
            closeChannel.closeFuture().addListener(future -> {
                if (remoteChannel != null && remoteChannel.isOpen()) {
                    remoteChannel.close();
                }
            });
        }

    }

    private URI websocketURI;

    public URI getWebsocketURI() {
        return websocketURI;
    }

    public WebSocketClient(URI websocketURI, String username, String password, EventLoopGroup workerGroup) {
        this.websocketURI = websocketURI;
        this.username = username;
        this.password = password;
        connect(websocketURI, workerGroup);
    }

    public ChannelFuture handshakeFuture() {
        return this.handler == null ? null : this.handler.handshakeFuture();
    }


    public void connect(URI websocketURI, EventLoopGroup workerGroup) {
        //URI websocketURI = new URI("ws://localhost:8005/forward");
        final WebSocketClientHandler handler = new WebSocketClientHandler();
        this.handler = handler;
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 如果是wss协议 则加载证书
                        if (websocketURI.getScheme().equalsIgnoreCase("wss")) {
                            SslContext sslCtx = SslContextBuilder.forClient()
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                            pipeline.addLast(sslCtx.newHandler(ch.alloc(), websocketURI.getHost(), websocketURI.getPort()));
                        }

//                      pipeline.addLast( new LoggingHandler(LogLevel.DEBUG));
                        // 添加一个http的编解码器
                        pipeline.addLast(new HttpClientCodec());
                        // 添加一个用于支持大数据流的支持
                        pipeline.addLast(new ChunkedWriteHandler());
                        // 添加一个聚合器，这个聚合器主要是将HttpMessage聚合成FullHttpRequest/Response
                        pipeline.addLast(new HttpObjectAggregator(1024 * 64));
                        pipeline.addLast(handler);

                    }
                });

        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        //进行握手
        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(websocketURI, WebSocketVersion.V13, (String) null, true, httpHeaders, 1024 * 1024 * 10);
        ChannelFuture connect = bootstrap.connect(websocketURI.getHost(), websocketURI.getPort());
        Channel channel = connect.channel();

        connect.addListener(future -> {

            if (future.isSuccess()) {
                this.remoteChannel = channel;
                final SocketAddress socketAddress = channel.localAddress();
                if (socketAddress instanceof InetSocketAddress) {
                    final String hostString = ((InetSocketAddress) socketAddress).getHostString();
                    logger.debug("WebSocketClient: 识别到本机IP:{}",hostString );
                    ConnectionManager.setMainLocalAddress(hostString);
                }

                // ws握手
                handler.setHandshaker(handshaker);
                handshaker.handshake(channel);

                //是否握手成功
                handler.handshakeFuture().addListener(future1 -> {
                    if (future1.isSuccess()) {
                        logger.debug("WebSocketClient: 与[{}]websocket握手成功,连接准备就绪", this.getWebsocketURI());
                    } else {
                        logger.error("WebSocketClient: 与[{}]websocket握手失败", this.getWebsocketURI());
                        channel.close();
                    }
                });

            } else {
                // 连接建立失败 则关闭与客户端的连接
                logger.error("WebSocketClient: 与[{}]连接失败", this.getWebsocketURI());
                channel.close();
            }

        });

        // 重新连接
        channel.closeFuture().addListener(future -> {
//            logger.debug("WebSocketClient: 与[{}]连接断开...", this.getWebsocketURI());
            this.handler = null;
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                logger.debug("WebSocketClient: 与[{}]连接断开,尝试重新建立连接...", this.getWebsocketURI());
                connect(websocketURI, workerGroup);
            }).run();
        });
    }

    final public static int FORWARD_DATA_MESSAGE = 0;
    final public static int FORWARD_REQUEST_MESSAGE = 1;
    final public static int FORWARD_RESPONSE_MESSAGE = 2;
    final public static int FORWARD_CLOSE_MESSAGE = 3;
    final public static int HEARTBEAT_MESSAGE = 10;

    public DefaultPromise<Integer> forward(String targetHost, int targetPort, Channel localChannel) {

        String message = localChannel.id().asLongText() + "|" + username + "|" + password + "|" + targetHost + "|" + targetPort;
        ByteBuf buffer = this.remoteChannel.alloc().buffer(message.length() + 4);
        buffer.writeByte(FORWARD_REQUEST_MESSAGE);  //
        buffer.writeBytes(message.getBytes());
        DefaultPromise<Integer> promise = new DefaultPromise<>(this.remoteChannel.eventLoop());
        handler.forwardingPromiseMap.put(localChannel.id().asLongText(), promise);
        promise.addListener(new GenericFutureListener<Future<Integer>>() {
            @Override
            public void operationComplete(Future<Integer> future) throws Exception {
                handler.forwardingPromiseMap.remove(localChannel.id().asLongText());
                if (future.isSuccess()) {
                    Integer now = future.getNow();
                    logger.debug("WebSocketClient: 代理请求 => {}:{} 创建成功 , 连接ID:{}", targetHost, targetPort, now);
                    handler.forwardingLocalChannelMap.put(now, localChannel);
                } else {
                    logger.error("WebSocketClient: 请求转发失败,将关闭与本地服务的连接");
                    localChannel.close();
                }
            }
        });
        this.remoteChannel.writeAndFlush(new BinaryWebSocketFrame(buffer));
        logger.debug("WebSocketClient: 向服务端发送代理请求 => {}:{}", targetHost, targetPort);
        return promise;
    }
}

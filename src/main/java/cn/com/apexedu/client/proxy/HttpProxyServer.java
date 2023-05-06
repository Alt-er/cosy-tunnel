package cn.com.apexedu.client.proxy;

import cn.com.apexedu.client.websocket.WebSocketClient;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class HttpProxyServer {

    final private static Logger logger = LoggerFactory.getLogger(HttpProxyServer.class);

    private final int port;

    /**
     * 创建一个HTTP代理服务 将流量转到远程websocket中
     *
     * @param port
     * @param remoteWebSocketUri
     * @throws URISyntaxException
     */
    public HttpProxyServer(int port, URI remoteWebSocketUri, String username, String password) throws URISyntaxException {
        this.port = port;
        this.webSocketClient = new WebSocketClient(remoteWebSocketUri, username, password, workerGroup);
        run();
    }

    final public WebSocketClient webSocketClient;

    final private static EventLoopGroup bossGroup = new NioEventLoopGroup();
    final private static EventLoopGroup workerGroup = new NioEventLoopGroup();

    public void run() {
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(
//                                    new LoggingHandler(LogLevel.DEBUG),
                                    new HttpRequestDecoder(), new HttpResponseEncoder(), new HttpObjectAggregator(1024 * 1024), new HttpProxyServerHandler(webSocketClient));
                        }
                    }).bind(port).sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
//            bossGroup.shutdownGracefully();
//            workerGroup.shutdownGracefully();
        }
    }
}


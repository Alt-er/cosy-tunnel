package cn.com.apexedu.client.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

@Deprecated
public class LocalProxyForwardServer {

    private final int port;

    public LocalProxyForwardServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        new LocalProxyForwardServer(3000).run();
    }

    /**
     *  整体架构:
     *  发送数据流程: [[[xx软件]]] -------(forward)-------> [[[proxifier代理]]] -------(forward)-------> [[[本软件]]] ---(websocket)---> [[[cosy服务]]] -------(forward)-------> [[[cosyHttpProxy]]] -------(forward)-------> [[[目标服务]]]
     *  接收数据流程: 和发送相反
     *
     *  本软件需要维护连接
     *  1.[[[proxifier代理]]] -------(forward)-------> [[[本软件]]]
     *  多个请求同时访问这里就会出现多个连接
     *  2.[[[本软件]]] ---(websocket)---> [[[cosy服务]]]
     *  多个请求同时访问这里可以使用多路复用 或者每次都创建新连接
     *
     *  这2个连接任意一个连接断开都需要将另一个连接也断开
     */

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(
                                   new LocalProxyForwardServerHandler());
                        }
                    })
                    .bind(port).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

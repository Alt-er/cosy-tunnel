package cn.com.apexedu.forward.server;

import cn.com.apexedu.forward.protocol.MessageCodec;
import cn.com.apexedu.forward.protocol.ProtocolFrameDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 端口转发功能服务端
 */
@Deprecated
public class PortForwardMainServer {

    static final Logger logger = LoggerFactory.getLogger(PortForwardMainServer.class);

    public PortForwardMainServer(int port) {
        this.port = port;
        createServer();
    }


    // 每个实例创建两个线程组 boosGroup、workerGroup
    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();
    private Channel channel;
    private int port;

    /**
     * 关闭服务
     */
    public void close() {
        this.channel.close();
    }

    /**
     * 绑定释放资源
     *
     * @param channel
     */
    private void bindRelease(Channel channel) {
        channel.closeFuture().addListener(future -> {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            logger.debug("端口转发服务端:{} 停止服务,资源释放完成.", port);
        });
    }

    public void createServer() {
        try {
            //创建服务端的启动对象，设置参数
            ServerBootstrap bootstrap = new ServerBootstrap();
            //设置两个线程组boosGroup和workerGroup
            bootstrap.group(bossGroup, workerGroup)
                    //设置服务端通道实现类型
                    .channel(NioServerSocketChannel.class)
                    //设置线程队列得到连接个数
                    .option(ChannelOption.SO_BACKLOG, 128)
                    //设置保持活动连接状态
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    //使用匿名内部类的形式初始化通道对象
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            //给pipeline管道设置处理器
                            socketChannel.pipeline()
                                    .addLast(new ProtocolFrameDecoder())
//                                    .addLast(new LoggingHandler(LogLevel.DEBUG))
                                    .addLast(new MessageCodec())
                                    .addLast(new PortForwardMainServerHandler());

                        }
                    });//给workerGroup的EventLoop对应的管道设置处理器
            logger.debug("端口转发服务端:{} 准备启动.", port);
            //绑定端口号，启动服务端
            this.channel = bootstrap.bind(port).sync().channel();
            logger.debug("端口转发服务端:{} 启动成功.", port);
            bindRelease(this.channel);
        } catch (Throwable th) {
            // 创建服务失败
            logger.error("端口转发服务端:{} 创建失败" , port , th);
        }

    }


    public static void main(String[] args) throws InterruptedException {
        PortForwardMainServer portForwardMainServer = new PortForwardMainServer(8006);
    }
}

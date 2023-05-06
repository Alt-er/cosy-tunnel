package cn.com.apexedu.forward.server;

import cn.com.apexedu.forward.message.ForwardResponseMessage;
import cn.com.apexedu.forward.services.PortForwardOnlineManager;
import cn.com.apexedu.forward.services.PortForwardInfo;
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
 * 远程端口监听服务
 * 创建一个映射端口,发送到该端口的数据将会被转到到内部服务上
 */
@Deprecated
public class RemotePortListenServer {
    static final Logger logger = LoggerFactory.getLogger(RemotePortListenServer.class);

    //创建两个线程组 boosGroup、workerGroup
    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();
    private Channel channel;

    // 主通道
    private Channel mainChannel;
    // 远程监听的端口
    private int remotePort;
    // 本地监听的IP
    private String localIp;
    // 本地监听的端口
    private int localPort;


    public RemotePortListenServer(Channel mainChannel, String localIp, int localPort, int remotePort) {
        this.localIp = localIp;
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.mainChannel = mainChannel;


        createServer();
        // 如果主服务与客户端断开了连接 则直接关闭这个监听
        mainChannel.closeFuture().addListener(future -> {
            logger.debug("远程端口监听服务:{} 主服务与客户端端口断开连接,将清理监听的端口资源", remotePort);
            this.close();
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
                            socketChannel.pipeline().addLast(new RemotePortListenServerHandler(mainChannel, localIp, localPort, remotePort));
                        }
                    });//给workerGroup的EventLoop对应的管道设置处理器
            logger.debug("远程端口监听服务:{} 准备启动", remotePort);
            //绑定端口号，启动服务端
            this.channel = bootstrap.bind(remotePort).sync().channel();
            logger.debug("远程端口监听服务:{} 启动成功", remotePort);
            bindRelease(this.channel);
            // 告诉客户端远程端口已监听完成
            mainChannel.writeAndFlush(new ForwardResponseMessage(true, "转发端口监听成功", localIp, localPort, remotePort));


            // 端口监听创建成功  则将数据维护到online管理中
            PortForwardInfo portForwardInfo = new PortForwardInfo(localIp, localPort, remotePort);
            boolean add = PortForwardOnlineManager.add(portForwardInfo);
            this.channel.closeFuture().addListener(future -> {
                // 当前端口监听关闭  则从online管理中移除
                PortForwardOnlineManager.remove(portForwardInfo);
            });
        } catch (Throwable th) {
            logger.error("远程端口监听服务:{} 创建失败", remotePort, th);
            mainChannel.writeAndFlush(new ForwardResponseMessage(false, "转发端口监听失败 原因:" + th.getMessage(), localIp, localPort, remotePort));
        }
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
            logger.debug("远程端口监听服务:{} 停止服务,资源释放完成.", remotePort);
        });
    }


    /**
     * 关闭服务
     */
    public void close() {
        // 关闭连接
        try {
            channel.close().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

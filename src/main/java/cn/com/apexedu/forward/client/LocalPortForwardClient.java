package cn.com.apexedu.forward.client;

import cn.com.apexedu.forward.message.CreateForwardInstanceResponseMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class LocalPortForwardClient {
    static final Logger logger = LoggerFactory.getLogger(LocalPortForwardClient.class);

    private String localIp;

    private int localPort;

    private int connectionId;

    private Channel channel;

    public LocalPortForwardClient(Channel mainChannel, String localIp, int localPort, int remotePort, int connectionId) {
        this.localIp = localIp;
        this.localPort = localPort;
        this.connectionId = connectionId;
        connectLocalServer(mainChannel, localIp, localPort, remotePort, connectionId);

        mainChannel.closeFuture().addListener(future -> {
            logger.debug("本地端口转发客户端:{}:{} 连接ID:{} 客户端与服务端断开连接,将断开与本地资源的连接" , localIp, localPort ,connectionId );
            this.close();
        });
    }

    private NioEventLoopGroup eventExecutors = new NioEventLoopGroup();

    private void connectLocalServer(Channel mainChannel, String localIp, int localPort, int remotePort, int connectionId) {
        try {
            //创建bootstrap对象，配置参数
            Bootstrap bootstrap = new Bootstrap();
            //设置线程组
            bootstrap.group(eventExecutors)
                    //设置客户端的通道实现类型
                    .channel(NioSocketChannel.class)
                    //使用匿名内部类初始化通道
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            //添加客户端通道的处理器
                            ch.pipeline()
//                                    .addLast(new LoggingHandler(LogLevel.DEBUG))
                                    .addLast(new LocalPortForwardClientHandler(mainChannel, localIp, localPort, remotePort, connectionId));
                        }
                    });
            logger.debug("本地端口转发客户端:{}:{} 准备启动 , connectionId:{} ", localIp, localPort, connectionId);
            //连接服务端
            ChannelFuture channelFuture = bootstrap.connect(localIp, localPort).sync();
            this.channel = channelFuture.channel();
            logger.debug("本地端口转发客户端:{}:{} 启动完成 , connectionId:{}  ", localIp, localPort, connectionId);
            bindRelease(this.channel);
        } catch (Throwable th) {
            th.printStackTrace();
            mainChannel.writeAndFlush(new CreateForwardInstanceResponseMessage(connectionId, false, "本地服务连接失败"));
        }
    }

    /**
     * 绑定释放资源
     *
     * @param channel
     */
    private void bindRelease(Channel channel) {
        channel.closeFuture().addListener(future -> {
            eventExecutors.shutdownGracefully();
            logger.debug("本地端口转发客户端:{}:{} 连接ID:{} 停止服务,资源释放完成.", localIp, localPort ,connectionId);
        });
    }

    public void close() {
        this.channel.close();
    }
}

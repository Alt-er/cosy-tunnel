package cn.com.apexedu.forward.client;

import cn.com.apexedu.forward.protocol.MessageCodec;
import cn.com.apexedu.forward.protocol.ProtocolFrameDecoder;
import cn.com.apexedu.forward.services.PortForwardOnlineManager;
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
public class PortForwardMainClient {

    static final Logger logger = LoggerFactory.getLogger(PortForwardMainClient.class);


    private String serverIp;

    private int port;

    private Channel channel;


    public PortForwardMainClient(String serverIp, int port) {
        this.serverIp = serverIp;
        this.port = port;
        createClient();
    }

    private NioEventLoopGroup eventExecutors = new NioEventLoopGroup();

    private void createClient() {
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
                                    .addLast(new ProtocolFrameDecoder())
//                                    .addLast(new LoggingHandler(LogLevel.DEBUG))
                                    .addLast(new MessageCodec())
                                    .addLast(new PortForwardMainClientHandler(serverIp , port ));
                        }
                    });
            logger.debug("端口转发客户端:{}:{} 准备连接服务端", serverIp, port);
            //连接服务端
            ChannelFuture channelFuture = bootstrap.connect(serverIp, port).sync();
            logger.debug("端口转发客户端:{}:{} 连接服务端成功", serverIp, port);
            //对通道关闭进行监听
            this.channel = channelFuture.channel();
            bindRelease(this.channel);
        } catch (Throwable th) {
            // 创建服务失败
            logger.error("端口转发客户端:{}:{} 连接服务端失败", serverIp, port, th);
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
            logger.debug("端口转发客户端:{}:{} 端口服务端连接,资源释放完成.", serverIp, port);
            // 清空所有的在线端口转发
            PortForwardOnlineManager.clear();
        });
    }

    public void close() {
        try {
            this.channel.close().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new PortForwardMainClient("127.0.0.1", 8006);
    }
}

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.TunDevice;

public class Test {

    public static void main(String[] args) {
        EventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            final Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(TunChannel.class)
                    .handler(new ChannelInboundHandler() {
                        @Override
                        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {

                        }

                        @Override
                        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

                        }

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("channelActive ctx:"+ ctx);
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {

                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            System.out.println("channelRead msg:"+ msg);
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
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

                        }

                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

                        }

                        @Override
                        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

                        }
                    });
            final Channel ch = b.bind(new TunAddress()).sync().channel();
            // send/receive messages of type TunPacket...
            ch.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }
}

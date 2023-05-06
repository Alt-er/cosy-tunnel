package cn.com.apexedu.client.proxy;

import cn.com.apexedu.client.helper.AddressAndNetmaskHelper;
import cn.com.apexedu.client.tcp.ConnectionManager;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.windows.WinDef;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;
import org.drasyl.channel.tun.jna.windows.Wintun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import static org.drasyl.channel.tun.jna.windows.Wintun.WintunGetAdapterLUID;

public class TunProxyServer {
    final private static Logger logger = LoggerFactory.getLogger(TunProxyServer.class);

    public TunProxyServer(List<String> routeList) {
        run(routeList);
    }

    private void run(List<String> routeList) {
        String ifName = "";
        InetAddress tunAddress = ConnectionManager.getTunAddress();
        InetAddress transitClientAddress = ConnectionManager.getTransitClientAddress();
        InetAddress transitServerAddress = ConnectionManager.getTransitServerAddress();
        EventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            final Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(TunChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();

                            p.addLast(new TunProxyServerHandler());
                        }
                    });
            final Channel ch = b.bind(new TunAddress(ifName.isEmpty() ? null : ifName)).syncUninterruptibly().channel();

            final String name = ch.localAddress().toString();
            logger.debug("TUN device created: " + name);

            if (PlatformDependent.isOsx()) {
                //sudo /sbin/ifconfig utun3 172.30.30.1 172.30.30.1 netmask 255.255.255.0
                exec("/sbin/ifconfig", name, "add", tunAddress.getHostAddress(), tunAddress.getHostAddress());
                exec("/sbin/ifconfig", name, "add", transitClientAddress.getHostAddress(), transitClientAddress.getHostAddress());
                exec("/sbin/ifconfig", name, "add", transitServerAddress.getHostAddress(), transitServerAddress.getHostAddress());
                exec("/sbin/ifconfig", name, "up");
//                exec("/sbin/route", "add", "-net", address.getHostAddress() + '/' + 32, "-iface", name);
//                String[] split = tunAddress.getHostAddress().split("\\.");
//                String route = String.join(".", split[0], split[1], split[2], "1/24");
                routeList.forEach(route -> {
                    try {
                        exec("/sbin/route", "add", "-net", route, "-iface", name);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } else if (PlatformDependent.isWindows()) {

                // Windows
                final Wintun.WINTUN_ADAPTER_HANDLE adapter = ((WindowsTunDevice) ((TunChannel) ch).device()).adapter();

                final Pointer interfaceLuid = new Memory(8);
                WintunGetAdapterLUID(adapter, interfaceLuid);
                WinDef.DWORD dword = AddressAndNetmaskHelper.setIPv4AndNetmask(interfaceLuid, tunAddress.getHostAddress(), 16);
                AddressAndNetmaskHelper.setIPv4AndNetmask(interfaceLuid, transitClientAddress.getHostAddress(), 16);
                AddressAndNetmaskHelper.setIPv4AndNetmask(interfaceLuid, transitServerAddress.getHostAddress(), 16);

                String routes = "";
//
                for (String route : routeList) {
                    routes += "$routeParams = @{DestinationPrefix='" + route + "';InterfaceIndex=$InterfaceIndex;NextHop='" + tunAddress.getHostAddress() + "';RouteMetric=10};" +
                            "New-NetRoute @routeParams -PolicyStore ActiveStore;";
                }


//                routes += "route delete " + tunAddress.getHostAddress() + " mask 255.255.255.255;";
                routes += "route delete " + transitClientAddress.getHostAddress() + " mask 255.255.255.255;";
//                routes += "route delete " + transitServerAddress.getHostAddress() + " mask 255.255.255.255;";
                String[] cmd = {
                        "powershell.exe",
                        "-Command",
                        "$InterfaceIndex = (Get-NetAdapter -Name " + name + ").ifIndex;" +
                                //"New-NetIPAddress -InterfaceIndex $InterfaceIndex -IPAddress " + tunAddress.getHostAddress() + " -PrefixLength 16;" +
//                                "New-NetIPAddress -InterfaceIndex $InterfaceIndex -IPAddress " + transitClientAddress.getHostAddress() + " -PrefixLength 16;" +
//                                "New-NetIPAddress -InterfaceIndex $InterfaceIndex -IPAddress " + transitServerAddress.getHostAddress() + " -PrefixLength 16;" +
                                "Set-NetConnectionProfile -InterfaceIndex $InterfaceIndex -NetworkCategory 'Private';"
                                +
                                routes
                };

                logger.debug("route command:\n" + cmd[2]);
                exec(cmd);
            } else {
                // Linux
                exec("/sbin/ip", "addr", "add", tunAddress.getHostAddress() + '/' + 32, "dev", name);
                exec("/sbin/ip", "link", "set", "dev", name, "up");
            }

            logger.debug("Address assigned: " + tunAddress.getHostAddress());
//            System.out.println("All packets addressed to this address will be echoed.");

//            ch.closeFuture().syncUninterruptibly();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } finally {
//            group.shutdownGracefully();
        }
    }

    private static void exec(final String... command) throws IOException {
        try {
            final int exitCode = Runtime.getRuntime().exec(command).waitFor();
            if (exitCode != 0) {
                throw new IOException("Executing `" + String.join(" ", command) + "` returned non-zero exit code (" + exitCode + ").");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

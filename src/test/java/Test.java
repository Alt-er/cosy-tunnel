import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.TunDevice;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

public class Test {

    public static void main(String[] args) throws IOException {
        try {
            MulticastSocket socket = new MulticastSocket();
            socket.setTimeToLive(1);
            socket.joinGroup(InetAddress.getByName("224.0.0.1"));
            String localAddress = socket.getInterface().getHostAddress();
            System.out.println("Local IP address is: " + localAddress);
            socket.leaveGroup(InetAddress.getByName("224.0.0.1"));
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

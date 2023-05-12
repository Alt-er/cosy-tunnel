package cn.com.apexedu.client.tcp;

import cn.com.apexedu.client.proxy.HttpProxyServer;
import cn.com.apexedu.client.websocket.WebSocketClient;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class ConnectionManager {
    final private static Logger logger = LoggerFactory.getLogger(ConnectionManager.class);


    final public static EventLoopGroup defaultEventLoopGroup = new NioEventLoopGroup(4);

    private static Map<String, Long> connectionRelatedTransitMap = new ConcurrentHashMap<>();
    private static Map<Long, int[]> transitRelatedConnectionMap = new ConcurrentHashMap<>();


    private static ConcurrentHashMap<String, WebSocketClient> ipRangeWebSocketClientMap;

    private static BiConsumer<String, WebSocketClient> routeRelatedChangeCallback;

    public static void listenRouteRelatedChange(BiConsumer<String, WebSocketClient> routeRelatedChangeCallback) {
        ConnectionManager.routeRelatedChangeCallback = routeRelatedChangeCallback;
    }


    final public static AtomicLong totalBytes = new AtomicLong();
    final public static AtomicLong uploadBytes = new AtomicLong();
    final public static AtomicLong downloadBytes = new AtomicLong();

    //
    private static int tunIp;
    public static InetAddress tunAddress;

    private static int transitClientIp;
    private static InetAddress transitClientAddress;
    //    private static AtomicInteger transitClientPort = new AtomicInteger(10000);
    private static PortPool portPool = new PortPool(50000, 60000);

    public static PortPool getPortPool() {
        return portPool;
    }

    public static InetAddress transitServerAddress;
    public static int transitServerIp;
    public static int transitServerPort = 9999;

    public static int getTunIp() {
        return tunIp;
    }

    public static InetAddress getTunAddress() {
        return tunAddress;
    }

    public static int getTransitClientIp() {
        return transitClientIp;
    }

    public static InetAddress getTransitClientAddress() {
        return transitClientAddress;
    }

    public static InetAddress getTransitServerAddress() {
        return transitServerAddress;
    }

    public static int getTransitServerIp() {
        return transitServerIp;
    }

    public static int getTransitServerPort() {
        return transitServerPort;
    }

    private static String mainLocalAddress = "127.0.0.1";

    public static String getMainLocalAddress() {
        return mainLocalAddress;
    }

    public static void setMainLocalAddress(String localAddress) {
        ConnectionManager.mainLocalAddress = localAddress;
    }


    public static void configTunProxyInfo(InetAddress tunIp, InetAddress transitClientIp, InetAddress transitServerIp, int transitServerPort) {
        ConnectionManager.tunIp = ByteBuffer.wrap(tunIp.getAddress()).getInt();
        ConnectionManager.transitClientIp = ByteBuffer.wrap(transitClientIp.getAddress()).getInt();
        ConnectionManager.transitServerIp = ByteBuffer.wrap(transitServerIp.getAddress()).getInt();

        ConnectionManager.tunAddress = tunIp;
        ConnectionManager.transitClientAddress = transitClientIp;
        ConnectionManager.transitServerAddress = transitServerIp;

        ConnectionManager.transitServerPort = transitServerPort;
    }


    private static Set<Long> websocketIpPortSet = new HashSet<Long>();

    public static boolean isWebsocketIpPort(int dest, int destPort) {
        return websocketIpPortSet.contains(mergeTransit(dest, destPort));
    }

    public static void configIpRangeWebSocketClientRelated(ConcurrentHashMap<String, WebSocketClient> related) {
        ConnectionManager.ipRangeWebSocketClientMap = related;
        ipRangeWebSocketClientMap.forEach((k, v) -> {
            routeRelatedChangeCallback.accept(k, v);
        });
        // self server
        ipRangeWebSocketClientMap.forEach((k, v) -> {
            try {
                String host = v.getWebsocketURI().getHost();
                int port = v.getWebsocketURI().getPort();
                // dns
                byte[] address = InetAddress.getByName(host).getAddress();
                websocketIpPortSet.add(mergeTransit(ByteBuffer.wrap(address).getInt(), port));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

        });
    }

    public static WebSocketClient getWebSocketClientByDestAddress(String ip) {
        for (Map.Entry<String, WebSocketClient> entry : ConnectionManager.ipRangeWebSocketClientMap.entrySet()) {
            String ips = entry.getKey();
            if (ips.contains(",")) {
                String[] ipArr = ips.split(",");
                for (String ipRange : ipArr) {
                    if (IpAddressRange.isIpAddressInSubnet(ip, ipRange.trim())) {
                        return entry.getValue();
                    }
                }
            } else {
                if (IpAddressRange.isIpAddressInSubnet(ip, ips)) {
                    return entry.getValue();
                }
            }

        }
        return null;
    }


    private static String getTransitConnectionKey(int src, int srcPort,
                                                  int dest, int destPort) {
        String key = String.join(",", String.valueOf(src), String.valueOf(srcPort), String.valueOf(dest), String.valueOf(destPort));
        return key;
    }

    public static int[] getOriginalConnectionByTransit(Long ipPort) {
        return transitRelatedConnectionMap.get(ipPort);
    }

    public static long getTransitIpAndPort(int src, int srcPort,
                                           int dest, int destPort) {

        String key = getTransitConnectionKey(src, srcPort, dest, destPort);
        Long ipPort = connectionRelatedTransitMap.get(key);
        if (ipPort == null) {
            // update skip
//            if (websocketIpPortSet.contains(mergeTransit(dest, destPort)) || websocketIpPortSet.contains(mergeTransit(src, srcPort))) {
//                // skip
//                return -1;
//            }
            int port = portPool.getPort();
            long transitIpAndPort = mergeTransit(transitClientIp, port);
            connectionRelatedTransitMap.put(key, transitIpAndPort);
            transitRelatedConnectionMap.put(transitIpAndPort, new int[]{src, srcPort, dest, destPort});
            ipPort = transitIpAndPort;
        }
        return ipPort;

    }

    public static long mergeTransit(int ip, int port) {
        return (((long) ip) << 32) | (port & 0xFFFFFFFFL);
    }

    public static int[] splitTransit(long transit) {
        // 获取高32位数值
        int ip = (int) (transit >> 32);
        // 获取低32位数值
        int port = (int) transit;
        return new int[]{ip, port};
    }


    public static String intToIP(int ipInt) {
        StringBuilder sb = new StringBuilder(15);
        sb.append((ipInt >>> 24) & 0xff).append(".");
        sb.append((ipInt >>> 16) & 0xff).append(".");
        sb.append((ipInt >>> 8) & 0xff).append(".");
        sb.append(ipInt & 0xff);
        return sb.toString();
    }

}

package cn.com.apexedu;

import cn.com.apexedu.client.proxy.HttpProxyServer;
import cn.com.apexedu.client.proxy.TunProxyServer;
import cn.com.apexedu.client.proxy.TunTransitProxyServer;
import cn.com.apexedu.client.tcp.ConnectionManager;
import cn.com.apexedu.client.websocket.WebSocketClient;

import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CosyTunnelApplication {

    final private static Logger logger = LoggerFactory.getLogger(CosyTunnelApplication.class);
    static {
        // 开发模式检查内存泄漏
//        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }


    public static void main(String[] args) throws IOException, URISyntaxException {

        if (args.length > 0 && args[0].equals("http")) {
            createHttpProxyServers();
        } else {
            logger.info("!!!!!!!!!!!!!请确保以管理员权限启动该程序!!!!!!!!!!!!!!!");
            createTunProxyServers();
        }

    }


    public static void createTunProxyServers() throws IOException {
        List<String> strings = Files.readAllLines(Paths.get("./tun.conf"));

//        # @config tunIp 172.30.30.1
//        # @config transitClientIp 172.30.31.1
//        # @config transitServerIp 172.30.32.1
//        # @config transitServerPort 9999
        String tunIp = "172.30.30.1";
        String transitClientIp = "172.30.31.1";
        String transitServerIp = "172.30.32.1";
        String transitServerPort = "9999";

        ConcurrentHashMap<String, WebSocketClient> related = new ConcurrentHashMap<>();
        List<String> routeList = new ArrayList<>();

        for (String s : strings) {
            String str = s.trim();
            if (!str.isEmpty() && !str.startsWith("#")) {
                String[] split = str.split("\\s+");
                logger.debug("开始创建Tun代理服务:{}", Arrays.toString(split));
                if (split.length != 4 && split.length != 3) {
                    logger.error("配置文件格式错误:{}", s);
                    return;
                }
                if (split[0].equals("@config")) {
                    if (split.length != 3) {
                        logger.error("配置文件 @config 格式错误:{}", str);
                        return;
                    }
                    if (split[1].equals("tunIp")) {
                        tunIp = split[2];
                    } else if (split[1].equals("transitClientIp")) {
                        transitClientIp = split[2];
                    } else if (split[1].equals("transitServerIp")) {
                        transitServerIp = split[2];
                    } else if (split[1].equals("transitServerPort")) {
                        transitServerPort = split[2];
                    }
                } else {
                    if (split.length != 4) {
                        logger.error("配置文件 tun config 格式错误:{}", str);
                        return;
                    }
                    try {
                        URI uri = new URI(split[0]);
                        String scheme = uri.getScheme();
                        // 不是https 或者 http 则报错
                        if (!"https".equals(scheme) && !"http".equals(scheme)) {
                            logger.error("配置文件格式错误,服务地址格式不正确 ,协议必须为http/https:{}", str);
                            return;
                        }
                        String ips = split[3];
                        if (ips.contains(",")) {
                            String[] ipArr = ips.split(",");
                            for (String ip : ipArr) {
                                routeList.add(ip.trim());
                            }
                        } else {
                            routeList.add(ips);
                        }

                        int defaultPort = uri.getScheme().equals("https") ? 443 : 80;
                        related.put(split[3], new WebSocketClient(new URI(uri.getScheme().equals("https") ? "wss" : "ws", null, uri.getHost(), uri.getPort() == -1 ? defaultPort : uri.getPort(), "/forward", null, null), split[1], split[2], ConnectionManager.defaultEventLoopGroup));
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        logger.error("配置文件格式错误,服务地址格式不正确:{}", str);
                        return;
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        logger.error("配置文件格式错误,监听端口格式不正确:{}", str);
                        return;
                    }

                }
            }
        }

        ConnectionManager.configTunProxyInfo(
                InetAddress.getByName(tunIp),
                InetAddress.getByName(transitClientIp),
                InetAddress.getByName(transitServerIp),
                Integer.parseInt(transitServerPort));

        ConnectionManager.configIpRangeWebSocketClientRelated(related);

        new TunProxyServer(routeList);
        new TunTransitProxyServer();
    }

    public static void createHttpProxyServers() throws URISyntaxException, IOException {

        List<String> strings = Files.readAllLines(Paths.get("./server.conf"));
        strings.forEach(s -> {
            try {

                String str = s.trim();
                if (!str.isEmpty() && !str.startsWith("#")) {

                    String[] split = str.split("\\s+");
                    logger.debug("开始创建HTTP代理服务:{}", Arrays.toString(split));
                    if (split.length != 4) {
                        logger.error("配置文件格式错误:{}", s);
                        return;
                    }
                    try {
                        URI uri = new URI(split[0]);
                        String scheme = uri.getScheme();
                        // 不是https 或者 http 则报错
                        if (!"https".equals(scheme) && !"http".equals(scheme)) {
                            logger.error("配置文件格式错误,服务地址格式不正确 ,协议必须为http/https:{}", s);
                            return;
                        }
                        int port = Integer.parseInt(split[3]);
                        int defaultPort = uri.getScheme().equals("https") ? 443 : 80;
                        new HttpProxyServer(port, new URI(uri.getScheme().equals("https") ? "wss" : "ws", null, uri.getHost(), uri.getPort() == -1 ? defaultPort : uri.getPort(), "/forward", null, null), split[1], split[2]);
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        logger.error("配置文件格式错误,服务地址格式不正确:{}", s);
                        return;
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        logger.error("配置文件格式错误,监听端口格式不正确:{}", s);
                        return;
                    }
                }
            } catch (Throwable th) {
                logger.error("代理服务创建失败", th);
            }

        });
//
    }
}

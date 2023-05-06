package cn.com.apexedu.client.tcp;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpAddressRange {


    public static boolean isIpAddressInSubnet(String ipAddress, String subnetRange) {
        String[] networkips = ipAddress.split("\\.");
        int ipAddr = (Integer.parseInt(networkips[0]) << 24)
                | (Integer.parseInt(networkips[1]) << 16)
                | (Integer.parseInt(networkips[2]) << 8)
                | Integer.parseInt(networkips[3]);

        // 拿到主机数
        int type = Integer.parseInt(subnetRange.replaceAll(".*/", ""));
        int ipCount = 0xFFFFFFFF << (32 - type);

        String maskIp = subnetRange.replaceAll("/.*", "");
        String[] maskIps = maskIp.split("\\.");

        int cidrIpAddr = (Integer.parseInt(maskIps[0]) << 24)
                | (Integer.parseInt(maskIps[1]) << 16)
                | (Integer.parseInt(maskIps[2]) << 8)
                | Integer.parseInt(maskIps[3]);

        return (ipAddr & ipCount) == (cidrIpAddr & ipCount);
    }
    public static void main(String[] args) {
        String ipAddress = "192.168.2.200";
        String networkAddress = "192.168.2.0";
        String networkAddressWithMask = "192.168.2.0/24";
        System.out.println(isIpAddressInSubnet(ipAddress, networkAddressWithMask));

    }
}

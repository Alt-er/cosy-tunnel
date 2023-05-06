package cn.com.apexedu.client.tcp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpValidator {

    /**
     * 验证给定的IP地址是否匹配指定的IP范围。
     *
     * @param ipAddr    待验证的IP地址
     * @param ipRange   指定的IP范围，支持3种格式：1. 固定ip 192.168.1.100；2.范围ip 192.168.1.100-192.168.1.200；3.星号匹配, 192.168.1.*
     * @return  如果IP在指定的范围内返回true，否则返回false
     */
    public static boolean validateIpInRanges(String ipAddr, String ipRange) {
        // 判断是否为IPv4地址格式
        Pattern ipPattern = Pattern.compile("\\b(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");
        Matcher ipMatcher = ipPattern.matcher(ipAddr);
        if (!ipMatcher.matches()) {
            return false;
        }

        // 判断IP是否在指定的范围内
        if (ipRange.contains("-")) {
            // 范围匹配
            String[] range = ipRange.split("-");
            if (range.length != 2) {
                return false;
            }
            return ipInRange(ipAddr, range[0], range[1]);
        } else if (ipRange.endsWith(".*")) {
            // 星号匹配
            return ipStartsWith(ipAddr, ipRange.substring(0, ipRange.length() - 1));
        } else {
            // 固定匹配
            return ipAddr.equals(ipRange);
        }
    }

    private static boolean ipInRange(String ip, String ipStart, String ipEnd) {
        long ipLong = ip2Long(ip);
        long ipStartLong = ip2Long(ipStart);
        long ipEndLong = ip2Long(ipEnd);
        return (ipLong >= ipStartLong && ipLong <= ipEndLong);
    }

    private static long ip2Long(String ip) {
        String[] ips = ip.split("\\.");
        return (Long.parseLong(ips[0]) << 24) | (Long.parseLong(ips[1]) << 16) | (Long.parseLong(ips[2]) << 8) | Long.parseLong(ips[3]);
    }

    private static boolean ipStartsWith(String ip, String ipPrefix) {
        return ip.startsWith(ipPrefix);
    }

    public static void main(String[] args) {
        String ip = "192.168.1.139";
        String range1 = "192.168.1.100";
        String range2 = "192.168.1.100-192.168.1.200";
        String range3 = "192.168.1.*";

        System.out.println(validateIpInRanges(ip, range1)); // false
        System.out.println(validateIpInRanges(ip, range2)); // true
        System.out.println(validateIpInRanges(ip, range3)); // true

    }
}

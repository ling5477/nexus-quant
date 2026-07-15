package com.guidinglight.nexusquant.adapter.okx.service;

import java.net.InetAddress;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 只规范化 IP literal；禁止 DNS、CIDR、contains 和第三方公网 IP 发现。
 */
public final class OkxIpAddressNormalizer {

    private static final Pattern IPV4 = Pattern.compile("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");

    private OkxIpAddressNormalizer() {
    }

    /**
     * 返回确定性 IP literal；非法、CIDR、zone id 或可能触发 DNS 的文本一律拒绝。
     */
    public static String normalizeLiteral(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("IP literal is required");
        }
        String value = candidate.trim();
        if (IPV4.matcher(value).matches()) {
            String[] octets = value.split("\\.");
            for (String octet : octets) {
                if (Integer.parseInt(octet) > 255) {
                    throw new IllegalArgumentException("IPv4 literal is invalid");
                }
            }
            return value;
        }
        if (!value.contains(":") || value.contains("%") || value.contains("/")
                || !IPV6_CHARS.matcher(value).matches()) {
            throw new IllegalArgumentException("IP literal is invalid");
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            byte[] bytes = address.getAddress();
            if (bytes.length != 16) {
                throw new IllegalArgumentException("IPv6 literal is invalid");
            }
            return address.getHostAddress().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new IllegalArgumentException("IPv6 literal is invalid");
        }
    }
}

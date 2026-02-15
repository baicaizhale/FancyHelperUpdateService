package org.YanPl.fancyHelperUpdateService.util;

import org.bukkit.Bukkit;

/**
 * 版本检测工具类
 * 用于处理不同 Minecraft 版本之间的 API 差异
 * 支持 1.18 - 1.21 所有版本
 */
public final class VersionUtil {

    private static final int MINOR_VERSION;
    private static final int PATCH_VERSION;

    static {
        // 解析服务器版本，例如：1.18.2, 1.19, 1.20.4, 1.21
        String version = Bukkit.getBukkitVersion().split("-")[0];
        String[] parts = version.split("\\.");
        
        int minor = 0;
        int patch = 0;
        
        if (parts.length >= 2) {
            try {
                minor = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        
        if (parts.length >= 3) {
            try {
                patch = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        
        MINOR_VERSION = minor;
        PATCH_VERSION = patch;
    }

    private VersionUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 获取次版本号（例如 1.18.2 中的 18）
     * @return 次版本号
     */
    public static int getMinorVersion() {
        return MINOR_VERSION;
    }

    /**
     * 获取补丁版本号（例如 1.18.2 中的 2）
     * @return 补丁版本号，如果没有则返回 0
     */
    public static int getPatchVersion() {
        return PATCH_VERSION;
    }

    /**
     * 获取完整版本字符串
     * @return 版本字符串，例如 "1.18.2"
     */
    public static String getVersionString() {
        if (PATCH_VERSION > 0) {
            return "1." + MINOR_VERSION + "." + PATCH_VERSION;
        }
        return "1." + MINOR_VERSION;
    }

    /**
     * 检查是否为 1.18 版本
     * @return 如果是 1.18.x 则返回 true
     */
    public static boolean is1_18() {
        return MINOR_VERSION == 18;
    }

    /**
     * 检查是否为 1.19 版本
     * @return 如果是 1.19.x 则返回 true
     */
    public static boolean is1_19() {
        return MINOR_VERSION == 19;
    }

    /**
     * 检查是否为 1.20 版本
     * @return 如果是 1.20.x 则返回 true
     */
    public static boolean is1_20() {
        return MINOR_VERSION == 20;
    }

    /**
     * 检查是否为 1.21 版本
     * @return 如果是 1.21.x 则返回 true
     */
    public static boolean is1_21() {
        return MINOR_VERSION == 21;
    }

    /**
     * 检查版本是否大于或等于指定版本
     * @param minor 次版本号（例如 1.18 中的 18）
     * @return 如果当前版本大于或等于指定版本则返回 true
     */
    public static boolean isAtLeast(int minor) {
        return MINOR_VERSION >= minor;
    }

    /**
     * 检查版本是否大于或等于指定版本
     * @param minor 次版本号（例如 1.18 中的 18）
     * @param patch 补丁版本号（例如 1.18.2 中的 2）
     * @return 如果当前版本大于或等于指定版本则返回 true
     */
    public static boolean isAtLeast(int minor, int patch) {
        if (MINOR_VERSION > minor) {
            return true;
        }
        return MINOR_VERSION == minor && PATCH_VERSION >= patch;
    }

    /**
     * 检查版本是否在指定范围内（包含边界）
     * @param minMinor 最小次版本号
     * @param maxMinor 最大次版本号
     * @return 如果当前版本在范围内则返回 true
     */
    public static boolean isInRange(int minMinor, int maxMinor) {
        return MINOR_VERSION >= minMinor && MINOR_VERSION <= maxMinor;
    }

    /**
     * 检查是否支持本插件（1.18 - 1.21）
     * @return 如果版本在支持范围内则返回 true
     */
    public static boolean isSupported() {
        return isInRange(18, 21);
    }
}

package org.YanPl.fancyHelperUpdateService;

import org.YanPl.fancyHelperUpdateService.util.VersionUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class FancyHelperUpdateService extends JavaPlugin {

    @Override
    public void onEnable() {
        // 检测服务器版本
        String version = VersionUtil.getVersionString();
        getLogger().info("检测到服务器版本: " + version);
        
        // 检查版本是否支持
        if (!VersionUtil.isSupported()) {
            getLogger().warning("当前服务器版本不在支持范围内！");
            getLogger().warning("本插件支持版本: 1.18 - 1.21");
            getLogger().warning("当前版本: " + version);
            getLogger().warning("插件可能无法正常工作，请谨慎使用！");
        } else {
            getLogger().info("服务器版本兼容性检查通过！");
        }
        
        getLogger().info("FancyHelperUpdateService 已启动！");
    }

    @Override
    public void onDisable() {
        getLogger().info("FancyHelperUpdateService 已关闭！");
    }
}

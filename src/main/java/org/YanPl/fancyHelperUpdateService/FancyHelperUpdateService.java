package org.YanPl.fancyHelperUpdateService;

import org.YanPl.fancyHelperUpdateService.util.VersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

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
        
        // 开始执行重载任务
        startReloadTask();
    }

    @Override
    public void onDisable() {
        getLogger().info("FancyHelperUpdateService 已关闭！");
    }

    /**
     * 开始执行重载任务
     * 流程：
     * 1. 等待3秒，卸载FancyHelper插件
     * 2. 等待FancyHelper卸载完成，等待2秒
     * 3. 从plugins/文件夹下匹配FancyHelper开头，.jar结尾（排除UpdateService）文件并加载
     * 4. 等待2秒，卸载自己
     */
    private void startReloadTask() {
        // 使用异步线程执行重载任务，避免阻塞主线程
        new Thread(() -> {
            try {
                // 步骤1: 等待3秒，卸载FancyHelper插件
                getLogger().info("等待3秒后开始卸载FancyHelper插件...");
                Thread.sleep(3000);
                
                Plugin fancyHelper = Bukkit.getPluginManager().getPlugin("FancyHelper");
                if (fancyHelper != null) {
                    getLogger().info("正在卸载FancyHelper插件...");
                    Bukkit.getPluginManager().disablePlugin(fancyHelper);
                    getLogger().info("FancyHelper插件已卸载！");
                } else {
                    getLogger().warning("未找到FancyHelper插件，跳过卸载步骤！");
                }
                
                // 步骤2: 等待FancyHelper卸载完成，等待2秒
                getLogger().info("等待2秒后开始加载新的FancyHelper插件...");
                Thread.sleep(2000);
                
                // 步骤3: 从plugins/文件夹下匹配FancyHelper开头，.jar结尾（排除UpdateService）文件并加载
                File pluginsDir = new File("plugins");
                if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
                    getLogger().severe("plugins文件夹不存在！");
                    return;
                }
                
                File[] jarFiles = pluginsDir.listFiles((dir, name) -> 
                    name.startsWith("FancyHelper") && 
                    name.endsWith(".jar") && 
                    !name.contains("UpdateService")
                );
                
                if (jarFiles == null || jarFiles.length == 0) {
                    getLogger().warning("未找到符合条件的FancyHelper插件文件！");
                    return;
                }
                
                // 如果有多个匹配的文件，选择第一个
                File targetJar = jarFiles[0];
                getLogger().info("找到插件文件: " + targetJar.getName());
                
                // 加载插件
                try {
                    Plugin loadedPlugin = Bukkit.getPluginManager().loadPlugin(targetJar);
                    if (loadedPlugin != null) {
                        getLogger().info("插件加载成功: " + loadedPlugin.getName());
                        // 启用插件
                        Bukkit.getPluginManager().enablePlugin(loadedPlugin);
                        getLogger().info("插件已启用: " + loadedPlugin.getName());
                    }
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "加载插件失败: " + targetJar.getName(), e);
                    return;
                }
                
                // 步骤4: 等待2秒，卸载自己
                getLogger().info("等待2秒后卸载自己...");
                Thread.sleep(2000);
                
                getLogger().info("正在卸载FancyHelperUpdateService...");
                // 在主线程中执行卸载
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getPluginManager().disablePlugin(this);
                });
                
            } catch (InterruptedException e) {
                getLogger().log(Level.SEVERE, "重载任务被中断！", e);
                Thread.currentThread().interrupt();
            }
        }, "FancyHelperUpdateService-ReloadThread").start();
    }
}

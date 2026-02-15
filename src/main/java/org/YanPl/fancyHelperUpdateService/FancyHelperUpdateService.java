package org.YanPl.fancyHelperUpdateService;

import org.YanPl.fancyHelperUpdateService.util.VersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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
                
                // 清理旧的重映射文件
                cleanupRemappedFiles();
                
                // 尝试清理Paper插件注册表
                cleanupPaperPluginRegistry("FancyHelper");
                
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
    
    /**
     * 清理旧的重映射文件
     * Paper会在 plugins/.paper-remapped/ 文件夹中缓存重映射的插件
     */
    private void cleanupRemappedFiles() {
        try {
            File remappedDir = new File("plugins/.paper-remapped");
            if (remappedDir.exists() && remappedDir.isDirectory()) {
                File[] subDirs = remappedDir.listFiles(File::isDirectory);
                if (subDirs != null) {
                    for (File subDir : subDirs) {
                        File[] files = subDir.listFiles((dir, name) -> 
                            name.startsWith("FancyHelper") && !name.contains("UpdateService")
                        );
                        if (files != null) {
                            for (File f : files) {
                                getLogger().info("删除旧的重映射文件: " + f.getAbsolutePath());
                                deleteRecursively(f);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "清理重映射文件时出错", e);
        }
    }
    
    /**
     * 递归删除文件或目录
     */
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    /**
     * 清理Paper插件系统中的残留信息
     * Paper 1.21+ 使用新的插件系统，卸载插件后需要手动清理注册表
     */
    @SuppressWarnings("unchecked")
    private void cleanupPaperPluginRegistry(String pluginName) {
        try {
            getLogger().info("正在清理Paper插件注册表中的残留信息...");
            
            // 获取PaperPluginManagerImpl
            Object paperPluginManager = null;
            try {
                Class<?> paperPluginManagerClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
                Method getInstanceMethod = paperPluginManagerClass.getMethod("getInstance");
                paperPluginManager = getInstanceMethod.invoke(null);
            } catch (Exception e) {
                getLogger().warning("无法获取PaperPluginManagerImpl: " + e.getMessage());
                return;
            }
            
            if (paperPluginManager == null) {
                getLogger().warning("PaperPluginManagerImpl为空");
                return;
            }
            
            // 获取instanceManager
            Field instanceManagerField = paperPluginManager.getClass().getDeclaredField("instanceManager");
            instanceManagerField.setAccessible(true);
            Object instanceManager = instanceManagerField.get(paperPluginManager);
            
            // 清理instanceManager中的pluginInstances
            for (Field field : instanceManager.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(instanceManager);
                removeFromCollection(field.getName(), value, pluginName);
            }
            
            // 获取SimplePluginManager (field name: pluginManager)
            Field pluginManagerField = paperPluginManager.getClass().getDeclaredField("pluginManager");
            pluginManagerField.setAccessible(true);
            Object simplePluginManager = pluginManagerField.get(paperPluginManager);
            
            // 清理SimplePluginManager中的providers
            for (Field field : simplePluginManager.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(simplePluginManager);
                removeFromCollection(field.getName(), value, pluginName);
            }
            
            // 尝试清理providerStorage
            try {
                Field providerStorageField = simplePluginManager.getClass().getDeclaredField("providerStorage");
                providerStorageField.setAccessible(true);
                Object providerStorage = providerStorageField.get(simplePluginManager);
                
                // providerStorage可能是SingularRuntimePluginProviderStorage
                for (Field field : providerStorage.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(providerStorage);
                    removeFromCollection(field.getName(), value, pluginName);
                }
            } catch (NoSuchFieldException e) {
                getLogger().info("未找到providerStorage字段");
            }
            
            // 尝试清理ServerPluginProviderStorage中的providers
            cleanupServerPluginProviderStorage(paperPluginManager, pluginName);
            
            getLogger().info("Paper插件注册表清理完成！");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "清理Paper插件注册表时出错", e);
        }
    }
    
    /**
     * 从集合中移除包含指定名称的元素
     */
    @SuppressWarnings("unchecked")
    private void removeFromCollection(String fieldName, Object collection, String pluginName) {
        try {
            if (collection instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) collection;
                Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Object, Object> entry = iterator.next();
                    Object key = entry.getKey();
                    Object value = entry.getValue();
                    
                    String keyStr = safeToString(key);
                    String valueStr = safeToString(value);
                    
                    if (keyStr.contains(pluginName) || valueStr.contains(pluginName)) {
                        getLogger().info("从Map " + fieldName + " 中移除: key=" + keyStr);
                        iterator.remove();
                    }
                }
            } else if (collection instanceof Collection) {
                Collection<Object> col = (Collection<Object>) collection;
                col.removeIf(obj -> {
                    String str = safeToString(obj);
                    if (str.contains(pluginName)) {
                        getLogger().info("从Collection " + fieldName + " 中移除: " + str);
                        return true;
                    }
                    return false;
                });
            }
        } catch (Exception e) {
            getLogger().warning("处理 " + fieldName + " 时出错: " + e.getMessage());
        }
    }
    
    /**
     * 安全地将对象转换为字符串
     */
    private String safeToString(Object obj) {
        if (obj == null) return "null";
        try {
            return obj.toString();
        } catch (Exception e) {
            return obj.getClass().getName();
        }
    }
    
    /**
     * 清理ServerPluginProviderStorage中的providers
     */
    private void cleanupServerPluginProviderStorage(Object paperPluginManager, String pluginName) {
        try {
            // 尝试获取entrypointHandler
            Field entrypointHandlerField = paperPluginManager.getClass().getDeclaredField("entrypointHandler");
            entrypointHandlerField.setAccessible(true);
            Object entrypointHandler = entrypointHandlerField.get(paperPluginManager);
            
            if (entrypointHandler == null) return;
            
            // 遍历所有字段寻找provider storage
            for (Field field : entrypointHandler.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(entrypointHandler);
                
                if (value != null) {
                    // 检查是否是Map类型，key可能是PluginType
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> map = (Map<Object, Object>) value;
                        for (Object storage : map.values()) {
                            if (storage != null) {
                                // 在storage中寻找providers
                                for (Field storageField : storage.getClass().getDeclaredFields()) {
                                    storageField.setAccessible(true);
                                    Object storageValue = storageField.get(storage);
                                    removeFromCollection(storage.getClass().getSimpleName() + "." + storageField.getName(), storageValue, pluginName);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("清理ServerPluginProviderStorage时出错: " + e.getMessage());
        }
    }
}

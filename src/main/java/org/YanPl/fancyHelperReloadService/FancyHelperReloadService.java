package org.YanPl.fancyHelperReloadService;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public final class FancyHelperReloadService extends JavaPlugin {

    @Override
    public void onEnable() {
        startReloadTask();
    }

    @Override
    public void onDisable() {
    }

    private void startReloadTask() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                
                // 首先清理自身的 remapped 文件，避免重复加载问题
                cleanupSelfRemappedFiles();
                
                Plugin fancyHelper = Bukkit.getPluginManager().getPlugin("FancyHelper");
                if (fancyHelper != null) {
                    Bukkit.getPluginManager().disablePlugin(fancyHelper);
                }
                
                Thread.sleep(1000);
                
                cleanupRemappedFiles();
                cleanupPaperPluginRegistry("FancyHelper");
                // 同时清理自身的注册信息，防止重复标识符错误
                cleanupPaperPluginRegistry("FancyHelperReloadService");
                // 清理 ServerPluginProviderStorage 中的重复提供者
                cleanupDuplicateProviders("FancyHelper");
                cleanupDuplicateProviders("FancyHelperReloadService");
                
                File pluginsDir = new File("plugins");
                if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
                    return;
                }
                
                File[] jarFiles = pluginsDir.listFiles((dir, name) -> 
                    name.startsWith("FancyHelper") && 
                    name.endsWith(".jar") && 
                    !name.contains("ReloadService")
                );
                
                if (jarFiles == null || jarFiles.length == 0) {
                    return;
                }
                
                File targetJar = jarFiles[0];
                
                
                Plugin loadedPlugin = Bukkit.getPluginManager().loadPlugin(targetJar);
                if (loadedPlugin != null) {
                    Bukkit.getPluginManager().enablePlugin(loadedPlugin);
                }
                
                Thread.sleep(1000);
                
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getPluginManager().disablePlugin(this);
                });
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // 忽略所有异常
            }
        }, "FancyHelperReloadService-ReloadThread").start();
    }
    
    /**
     * 清理 FancyHelperReloadService 自身的 remapped 缓存文件
     * 这是解决 "duplicate plugin identifier" 错误的关键步骤
     */
    private void cleanupSelfRemappedFiles() {
        try {
            File remappedDir = new File("plugins/.paper-remapped");
            if (remappedDir.exists() && remappedDir.isDirectory()) {
                File[] subDirs = remappedDir.listFiles(File::isDirectory);
                if (subDirs != null) {
                    for (File subDir : subDirs) {
                        File[] files = subDir.listFiles((dir, name) -> 
                            name.contains("FancyHelperReloadService")
                        );
                        if (files != null) {
                            for (File f : files) {
                                deleteRecursively(f);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }
    
    private void cleanupRemappedFiles() {
        try {
            File remappedDir = new File("plugins/.paper-remapped");
            if (remappedDir.exists() && remappedDir.isDirectory()) {
                File[] subDirs = remappedDir.listFiles(File::isDirectory);
                if (subDirs != null) {
                    for (File subDir : subDirs) {
                        File[] files = subDir.listFiles((dir, name) -> 
                            name.startsWith("FancyHelper") && !name.contains("ReloadService")
                        );
                        if (files != null) {
                            for (File f : files) {
                                deleteRecursively(f);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }
    
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

    private void cleanupPaperPluginRegistry(String pluginName) {
        try {
            Object paperPluginManager = null;
            try {
                Class<?> paperPluginManagerClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
                java.lang.reflect.Method getInstanceMethod = paperPluginManagerClass.getMethod("getInstance");
                paperPluginManager = getInstanceMethod.invoke(null);
            } catch (Exception e) {
                return;
            }
            
            if (paperPluginManager == null) return;
            
            Field instanceManagerField = paperPluginManager.getClass().getDeclaredField("instanceManager");
            instanceManagerField.setAccessible(true);
            Object instanceManager = instanceManagerField.get(paperPluginManager);
            
            for (Field field : instanceManager.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(instanceManager);
                removeFromCollection(value, pluginName);
            }
            
            try {
                Field pluginManagerField = paperPluginManager.getClass().getDeclaredField("pluginManager");
                pluginManagerField.setAccessible(true);
                Object simplePluginManager = pluginManagerField.get(paperPluginManager);
                
                if (simplePluginManager != null) {
                    for (Field field : simplePluginManager.getClass().getDeclaredFields()) {
                        field.setAccessible(true);
                        Object value = field.get(simplePluginManager);
                        removeFromCollection(value, pluginName);
                    }
                    
                    try {
                        Field providerStorageField = simplePluginManager.getClass().getDeclaredField("providerStorage");
                        providerStorageField.setAccessible(true);
                        Object providerStorage = providerStorageField.get(simplePluginManager);
                        
                        if (providerStorage != null) {
                            for (Field field : providerStorage.getClass().getDeclaredFields()) {
                                field.setAccessible(true);
                                Object value = field.get(providerStorage);
                                removeFromCollection(value, pluginName);
                            }
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
            
            cleanupServerPluginProviderStorage(paperPluginManager, pluginName);
            
        } catch (Exception e) {
            // 忽略
        }
    }
    
    @SuppressWarnings("unchecked")
    private void removeFromCollection(Object collection, String pluginName) {
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
                        iterator.remove();
                    }
                }
            } else if (collection instanceof Collection) {
                Collection<Object> col = (Collection<Object>) collection;
                col.removeIf(obj -> {
                    String str = safeToString(obj);
                    return str.contains(pluginName);
                });
            }
        } catch (Exception e) {
            // 忽略
        }
    }
    
    private String safeToString(Object obj) {
        if (obj == null) return "null";
        try {
            return obj.toString();
        } catch (Exception e) {
            return obj.getClass().getName();
        }
    }
    
    private void cleanupServerPluginProviderStorage(Object paperPluginManager, String pluginName) {
        try {
            Field entrypointHandlerField = paperPluginManager.getClass().getDeclaredField("entrypointHandler");
            entrypointHandlerField.setAccessible(true);
            Object entrypointHandler = entrypointHandlerField.get(paperPluginManager);
            
            if (entrypointHandler == null) return;
            
            for (Field field : entrypointHandler.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(entrypointHandler);
                
                if (value != null) {
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> map = (Map<Object, Object>) value;
                        for (Object storage : map.values()) {
                            if (storage != null) {
                                for (Field storageField : storage.getClass().getDeclaredFields()) {
                                    storageField.setAccessible(true);
                                    Object storageValue = storageField.get(storage);
                                    removeFromCollection(storageValue, pluginName);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }
    
    /**
     * 直接清理 ServerPluginProviderStorage 中的重复插件提供者
     * 这是解决 "attempted to add duplicate plugin identifier" 错误的关键方法
     */
    private void cleanupDuplicateProviders(String pluginName) {
        try {
            Class<?> paperPluginManagerClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            java.lang.reflect.Method getInstanceMethod = paperPluginManagerClass.getMethod("getInstance");
            Object paperPluginManager = getInstanceMethod.invoke(null);
            
            if (paperPluginManager == null) return;
            
            Field instanceManagerField = paperPluginManager.getClass().getDeclaredField("instanceManager");
            instanceManagerField.setAccessible(true);
            Object instanceManager = instanceManagerField.get(paperPluginManager);
            
            if (instanceManager == null) return;
            
            // 尝试访问 ServerPluginProviderStorage
            Class<?> serverPluginProviderStorageClass = Class.forName("io.papermc.paper.plugin.storage.ServerPluginProviderStorage");
            
            // 查找 instanceManager 中的 providerStorage 字段
            for (Field field : instanceManager.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object fieldValue = field.get(instanceManager);
                
                if (fieldValue != null && serverPluginProviderStorageClass.isInstance(fieldValue)) {
                    // 找到了 ServerPluginProviderStorage，清理其中的 providers
                    for (Field storageField : serverPluginProviderStorageClass.getDeclaredFields()) {
                        storageField.setAccessible(true);
                        Object storageValue = storageField.get(fieldValue);
                        removeProviderFromCollection(storageValue, pluginName);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }
    
    /**
     * 从提供者集合中移除指定插件名称的提供者
     */
    @SuppressWarnings("unchecked")
    private void removeProviderFromCollection(Object collection, String pluginName) {
        try {
            if (collection instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) collection;
                Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Object, Object> entry = iterator.next();
                    Object key = entry.getKey();
                    Object value = entry.getValue();
                    
                    // 检查 key 是否包含插件名
                    String keyStr = safeToString(key);
                    if (keyStr.contains(pluginName)) {
                        iterator.remove();
                        continue;
                    }
                    
                    // 检查 value 中的插件描述
                    if (value != null) {
                        try {
                            // 尝试获取 PluginDescriptionFile
                            Field descriptionField = value.getClass().getDeclaredField("description");
                            descriptionField.setAccessible(true);
                            Object description = descriptionField.get(value);
                            if (description != null) {
                                String descStr = safeToString(description);
                                if (descStr.contains(pluginName)) {
                                    iterator.remove();
                                    continue;
                                }
                            }
                        } catch (NoSuchFieldException e) {
                            // 尝试其他方式
                            String valueStr = safeToString(value);
                            if (valueStr.contains(pluginName)) {
                                iterator.remove();
                            }
                        }
                    }
                }
            } else if (collection instanceof Collection) {
                Collection<Object> col = (Collection<Object>) collection;
                col.removeIf(obj -> {
                    String str = safeToString(obj);
                    if (str.contains(pluginName)) return true;
                    
                    // 尝试检查 description 字段
                    try {
                        Field descriptionField = obj.getClass().getDeclaredField("description");
                        descriptionField.setAccessible(true);
                        Object description = descriptionField.get(obj);
                        if (description != null) {
                            String descStr = safeToString(description);
                            return descStr.contains(pluginName);
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                    return false;
                });
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}

package org.YanPl.fancyHelperReloadService;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
                Thread.sleep(3000);
                
                Plugin fancyHelper = Bukkit.getPluginManager().getPlugin("FancyHelper");
                if (fancyHelper != null) {
                    Bukkit.getPluginManager().disablePlugin(fancyHelper);
                }
                
                Thread.sleep(2000);
                
                cleanupRemappedFiles();
                cleanupPaperPluginRegistry("FancyHelper");
                
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
                
                // 发送天蓝色消息
                Bukkit.broadcastMessage(ChatColor.AQUA + "正在重载FancyHelper");
                
                Plugin loadedPlugin = Bukkit.getPluginManager().loadPlugin(targetJar);
                if (loadedPlugin != null) {
                    Bukkit.getPluginManager().enablePlugin(loadedPlugin);
                }
                
                Thread.sleep(2000);
                
                // 发送天蓝色消息
                Bukkit.broadcastMessage(ChatColor.AQUA + "重载完成");
                
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
}

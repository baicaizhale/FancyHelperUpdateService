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
        getLogger().info("FancyHelperReloadService 已就绪，等待主插件信号...");
    }

    @Override
    public void onDisable() {
    }

    /**
     * 由 FancyHelper 通过反射调用 — 发送重载信号。
     * FancyHelper 会在调用此方法后立即卸载自身，因此线程需要等待其完全下线。
     * @param mode "UPDATE" 或 "RELOAD"
     * @param newJarName 仅 UPDATE 模式生效，新 JAR 的文件名
     */
    public void onReloadSignal(String mode, String newJarName) {
        final String finalMode = mode;
        final String finalJarName = newJarName;

        new Thread(() -> {
            try {
                // 等待 FancyHelper 完全卸载（此时 JAR 不再被锁定）
                waitForPluginDisabled("FancyHelper", 15000);

                Thread.sleep(300);

                // 清理自身的 remapped 文件
                cleanupSelfRemappedFiles();

                if ("UPDATE".equals(finalMode)) {
                    // FancyHelper 已下线，安全删除旧 JAR
                    deleteOldPluginJars(finalJarName);
                }

                // 清理 Paper 插件注册缓存
                cleanupPaperPluginRegistry("FancyHelper");
                cleanupPaperPluginRegistry("FancyHelperReloadService");
                cleanupDuplicateProviders("FancyHelper");
                cleanupDuplicateProviders("FancyHelperReloadService");

                // 找到目标 JAR 并加载
                File targetJar = findTargetJar(finalJarName);
                if (targetJar == null) {
                    getLogger().severe("未找到 FancyHelper JAR，无法加载！");
                    return;
                }

                Plugin loadedPlugin = Bukkit.getPluginManager().loadPlugin(targetJar);
                if (loadedPlugin != null) {
                    Bukkit.getPluginManager().enablePlugin(loadedPlugin);
                    getLogger().info("FancyHelper 已重新加载");
                } else {
                    getLogger().severe("无法加载 " + targetJar.getName());
                    return;
                }

                Thread.sleep(1000);

                // 关闭自身
                Bukkit.getScheduler().runTask(this, () ->
                    Bukkit.getPluginManager().disablePlugin(this)
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getLogger().severe("重载过程异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, "FancyHelperReload-Worker").start();
    }

    private void waitForPluginDisabled(String pluginName, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
            if (plugin == null || !plugin.isEnabled()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        getLogger().warning("等待 " + pluginName + " 卸载超时，继续执行...");
    }

    private File findTargetJar(String preferredName) {
        File pluginsDir = new File("plugins");
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return null;
        }

        // 如果指定了文件名，优先使用
        if (preferredName != null && !preferredName.isEmpty()) {
            File preferred = new File(pluginsDir, preferredName);
            if (preferred.exists()) return preferred;
        }

        // 否则按修改时间取最新的 FancyHelper JAR（排除 ReloadService）
        File[] jarFiles = pluginsDir.listFiles((dir, name) ->
            name.startsWith("FancyHelper") &&
            name.endsWith(".jar") &&
            !name.contains("ReloadService")
        );

        if (jarFiles == null || jarFiles.length == 0) return null;

        File latest = jarFiles[0];
        for (File f : jarFiles) {
            if (f.lastModified() > latest.lastModified()) {
                latest = f;
            }
        }
        return latest;
    }

    private void deleteOldPluginJars(String keepJarName) {
        File pluginsDir = new File("plugins");
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) return;

        File[] jars = pluginsDir.listFiles((dir, name) ->
            name.toLowerCase().contains("fancyhelper") &&
            !name.contains("ReloadService") &&
            !name.equals(keepJarName)
        );

        if (jars == null) return;

        for (File jar : jars) {
            if (jar.delete()) {
                getLogger().info("已删除旧版文件: " + jar.getName());
            } else {
                getLogger().warning("无法删除旧版文件: " + jar.getName());
            }
        }
    }

    // ==== 以下为 Paper 注册表清理方法（保持原样） ====

    private void cleanupSelfRemappedFiles() {
        deleteFromPaperRemapped("FancyHelperReloadService");
    }

    private void deleteFromPaperRemapped(String pluginName) {
        try {
            File remappedDir = new File("plugins/.paper-remapped");
            if (!remappedDir.exists() || !remappedDir.isDirectory()) return;
            File[] subDirs = remappedDir.listFiles(File::isDirectory);
            if (subDirs != null) {
                for (File subDir : subDirs) {
                    File[] files = subDir.listFiles((dir, name) -> name.contains(pluginName));
                    if (files != null) {
                        for (File f : files) deleteRecursively(f);
                    }
                }
            }
        } catch (Exception ignored) {}
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
            Class<?> pmClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            java.lang.reflect.Method getInstance = pmClass.getMethod("getInstance");
            Object paperPluginManager = getInstance.invoke(null);
            if (paperPluginManager == null) return;

            Field instanceManagerField = paperPluginManager.getClass().getDeclaredField("instanceManager");
            instanceManagerField.setAccessible(true);
            Object instanceManager = instanceManagerField.get(paperPluginManager);

            for (Field field : instanceManager.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                removeFromCollection(field.get(instanceManager), pluginName);
            }

            try {
                Field pluginManagerField = paperPluginManager.getClass().getDeclaredField("pluginManager");
                pluginManagerField.setAccessible(true);
                Object simplePluginManager = pluginManagerField.get(paperPluginManager);
                if (simplePluginManager != null) {
                    for (Field field : simplePluginManager.getClass().getDeclaredFields()) {
                        field.setAccessible(true);
                        removeFromCollection(field.get(simplePluginManager), pluginName);
                    }
                    try {
                        Field providerStorageField = simplePluginManager.getClass().getDeclaredField("providerStorage");
                        providerStorageField.setAccessible(true);
                        Object providerStorage = providerStorageField.get(simplePluginManager);
                        if (providerStorage != null) {
                            for (Field field : providerStorage.getClass().getDeclaredFields()) {
                                field.setAccessible(true);
                                removeFromCollection(field.get(providerStorage), pluginName);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            cleanupServerPluginProviderStorage(paperPluginManager, pluginName);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void removeFromCollection(Object collection, String pluginName) {
        if (collection == null) return;
        try {
            if (collection instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) collection;
                Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Object, Object> entry = iterator.next();
                    String keyStr = safeToString(entry.getKey());
                    String valueStr = safeToString(entry.getValue());
                    if (keyStr.contains(pluginName) || valueStr.contains(pluginName)) {
                        iterator.remove();
                    }
                }
            } else if (collection instanceof Collection) {
                ((Collection<Object>) collection).removeIf(obj -> {
                    String str = safeToString(obj);
                    return str.contains(pluginName);
                });
            }
        } catch (Exception ignored) {}
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
                if (value instanceof Map) {
                    ((Map<Object, Object>) value).values().forEach(storage -> {
                        if (storage != null) {
                            for (Field sf : storage.getClass().getDeclaredFields()) {
                                sf.setAccessible(true);
                                try {
                                    removeFromCollection(sf.get(storage), pluginName);
                                } catch (Exception ignored) {}
                            }
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private void cleanupDuplicateProviders(String pluginName) {
        try {
            Class<?> pmClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            java.lang.reflect.Method getInstance = pmClass.getMethod("getInstance");
            Object paperPluginManager = getInstance.invoke(null);
            if (paperPluginManager == null) return;

            Field instanceManagerField = paperPluginManager.getClass().getDeclaredField("instanceManager");
            instanceManagerField.setAccessible(true);
            Object instanceManager = instanceManagerField.get(paperPluginManager);
            if (instanceManager == null) return;

            Class<?> storageClass = Class.forName("io.papermc.paper.plugin.storage.ServerPluginProviderStorage");
            for (Field field : instanceManager.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object fieldValue = field.get(instanceManager);
                if (fieldValue != null && storageClass.isInstance(fieldValue)) {
                    for (Field sf : storageClass.getDeclaredFields()) {
                        sf.setAccessible(true);
                        removeProviderFromCollection(sf.get(fieldValue), pluginName);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void removeProviderFromCollection(Object collection, String pluginName) {
        if (collection == null) return;
        try {
            if (collection instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) collection;
                Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Object, Object> entry = iterator.next();
                    String keyStr = safeToString(entry.getKey());
                    if (keyStr.contains(pluginName)) { iterator.remove(); continue; }
                    Object value = entry.getValue();
                    if (value != null) {
                        try {
                            Field descField = value.getClass().getDeclaredField("description");
                            descField.setAccessible(true);
                            if (safeToString(descField.get(value)).contains(pluginName)) {
                                iterator.remove();
                            }
                        } catch (NoSuchFieldException e) {
                            if (safeToString(value).contains(pluginName)) iterator.remove();
                        }
                    }
                }
            } else if (collection instanceof Collection) {
                ((Collection<Object>) collection).removeIf(obj -> {
                    String str = safeToString(obj);
                    if (str.contains(pluginName)) return true;
                    try {
                        Field descField = obj.getClass().getDeclaredField("description");
                        descField.setAccessible(true);
                        return safeToString(descField.get(obj)).contains(pluginName);
                    } catch (Exception ignored) {}
                    return false;
                });
            }
        } catch (Exception ignored) {}
    }
}

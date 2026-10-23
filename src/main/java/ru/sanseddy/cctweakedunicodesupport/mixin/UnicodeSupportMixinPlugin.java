package ru.sanseddy.cctweakedunicodesupport.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UnicodeSupportMixinPlugin implements IMixinConfigPlugin {
    private String mixinPackage;

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.advancedperipherals.")) {
            return isModLoaded("advancedperipherals", "de.srendi.advancedperipherals.AdvancedPeripherals");
        }
        if (mixinClassName.contains(".compat.hudglasses.")) {
            return isModLoaded("hudglassescc", "org.semakol.hudglassescc.Hudglassescc");
        }
        return true;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, UnicodeSupportMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
            if (contextCl != null) {
                Class.forName(className, false, contextCl);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isModLoaded(String modId, String fallbackClassName) {
        try {
            Class<?> loadingModListClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
            Method getMethod = loadingModListClass.getMethod("get");
            Object loadingModList = getMethod.invoke(null);
            if (loadingModList != null) {
                Method getModFileById = loadingModListClass.getMethod("getModFileById", String.class);
                Object modFile = getModFileById.invoke(loadingModList, modId);
                return modFile != null;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Method getMethod = modListClass.getMethod("get");
            Object modList = getMethod.invoke(null);
            if (modList != null) {
                Method isLoaded = modListClass.getMethod("isLoaded", String.class);
                Object loaded = isLoaded.invoke(modList, modId);
                return Boolean.TRUE.equals(loaded);
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Method getInstance = fabricLoaderClass.getMethod("getInstance");
            Object fabricLoader = getInstance.invoke(null);
            if (fabricLoader != null) {
                Method isModLoaded = fabricLoaderClass.getMethod("isModLoaded", String.class);
                Object loaded = isModLoaded.invoke(fabricLoader, modId);
                return Boolean.TRUE.equals(loaded);
            }
        } catch (Throwable ignored) {
        }

        if (fallbackClassName != null) {
            return isClassPresent(fallbackClassName);
        }

        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        if (this.mixinPackage == null || !this.mixinPackage.endsWith(".client")) {
            return null;
        }
        List<String> list = new ArrayList<>();
        String[] candidates = {
            "AbstractComputerScreenMixin",
            "CustomLecternRendererMixin",
            "DirectFixedWidthFontRendererMixin",
            "FixedWidthFontRendererInvoker",
            "FixedWidthFontRendererMixin",
            "MonitorBlockEntityRendererMixin",
            "MonitorTextureBufferShaderMixin",
            "PocketItemRendererMixin",
            "PrintoutRendererMixin",
            "TerminalWidgetMixin"
        };
        for (String candidate : candidates) {
            String res = this.mixinPackage.replace('.', '/') + "/" + candidate + ".class";
            if (getClass().getClassLoader().getResource(res) != null
                || UnicodeSupportMixinPlugin.class.getResource("/" + res) != null) {
                list.add(candidate);
            }
        }
        return list;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

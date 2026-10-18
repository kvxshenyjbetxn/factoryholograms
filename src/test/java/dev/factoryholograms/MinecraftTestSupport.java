package dev.factoryholograms;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.init.Bootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;


/** Forge's ModClassLoader casts its defining loader to LaunchClassLoader even in headless tests. */
final class MinecraftTestSupport implements AutoCloseable {
    private final LaunchClassLoader gameLoader;
    private final LaunchClassLoader previousLoader;
    private final ClassLoader previousContextLoader;
    private Class<?> fixture;

    private MinecraftTestSupport() throws Exception {
        Set<URL> urls = new LinkedHashSet<URL>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        // Gradle workers may keep the actual test classpath in a child URLClassLoader.
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader) {
                Collections.addAll(urls, ((URLClassLoader) loader).getURLs());
            }
        }
        gameLoader = new LaunchClassLoader(urls.toArray(new URL[urls.size()]));
        gameLoader.addClassLoaderExclusion("org.junit.");
        gameLoader.addClassLoaderExclusion("org.hamcrest.");
        previousLoader = Launch.classLoader;
        previousContextLoader = Thread.currentThread().getContextClassLoader();
        Launch.classLoader = gameLoader;
        Thread.currentThread().setContextClassLoader(gameLoader);
    }

    static MinecraftTestSupport open(String fixtureName) throws Exception {
        MinecraftTestSupport support = new MinecraftTestSupport();
        try {
            Class<?> bootstrap = Class.forName(MinecraftTestSupport.class.getName() + "$BootstrapFixture",
                true, support.gameLoader);
            invoke(bootstrap, "bootstrap");
            support.fixture = Class.forName(fixtureName, true, support.gameLoader);
            return support;
        } catch (Exception | Error failure) {
            try {
                support.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    void run(String method) throws Exception {
        invoke(fixture, method);
    }

    private static void invoke(Class<?> fixture, String method) throws Exception {
        try {
            fixture.getMethod(method).invoke(null);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw failure;
        }
    }

    @Override
    public void close() throws Exception {
        Thread.currentThread().setContextClassLoader(previousContextLoader);
        Launch.classLoader = previousLoader;
        gameLoader.close();
    }

    public static final class BootstrapFixture {
        public static void bootstrap() throws Exception {
            Loader.injectData("7", "10", "99", "99", "1.7.10", "9.05", new File("."),
                Collections.emptyList());
            Loader loader = Loader.instance();
            Field mods = Loader.class.getDeclaredField("namedMods");
            mods.setAccessible(true);
            mods.set(loader, new HashMap<String, ModContainer>());
            Field controller = Loader.class.getDeclaredField("modController");
            controller.setAccessible(true);
            controller.set(loader, new LoadController(loader));
            Bootstrap.func_151354_b();

        }
    }
}

package com.xiaotang.j20updatemod.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 进服聊天提醒（纯反射、按形状探测，零编译期绑定）。
 *
 * <p>类名走 mojmap / intermediary 双候选（类名在 Forge/Neo 运行时不混淆，Fabric 生产环境是
 * intermediary 名）；方法<b>不看名字看形状</b>，天然绕开 Forge SRG 与 Fabric intermediary 混淆。
 * 全部形状已在真实产物上 javap 实证（1.20.1 SRG 运行时 jar + 1.21.1 official 映射 jar）：</p>
 * <ul>
 *   <li>Minecraft 实例：static 无参返回 Minecraft 自身（getInstance / m_91087_）——唯一</li>
 *   <li>玩家：<b>类型为 LocalPlayer 的实例字段</b>（player / f_91074_）——唯一。
 *       注意 Minecraft 上<b>没有</b>返回 LocalPlayer 的方法，只能取字段；字段为 null = 尚未进服</li>
 *   <li>消息文本：static (String) 精确返回 Component（nullToEmpty / m_130674_）——唯一；
 *       nullToEmpty(非空) 即 literal，避开 literal/translatable/keybind 三候选歧义</li>
 *   <li>发送：void (Component, boolean)（displayClientMessage / m_5661_）→ 退回 void (Component)</li>
 *   <li>主线程调度：优先名为 execute 的 void (Runnable)（BlockableEventLoop，SRG 保留原名），
 *       否则第一个 void (Runnable)</li>
 * </ul>
 *
 * <p>玩家未进服或任何环节暂不可用时轮询重试（上限 30 分钟），不提前放弃；发送成功才结束。
 * 发送失败仍静默（提醒保底走日志 + notice.txt）。</p>
 */
public final class ChatNotifier {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    private static final String[] MINECRAFT_CLASSES = {
            "net.minecraft.client.Minecraft", "net.minecraft.class_310"};
    private static final String[] PLAYER_CLASSES = {
            "net.minecraft.client.player.LocalPlayer", "net.minecraft.class_746"};
    private static final String[] COMPONENT_CLASSES = {
            "net.minecraft.network.chat.Component", "net.minecraft.class_2561"};

    private static final long POLL_MILLIS = 3000L;
    private static final long GIVE_UP_MILLIS = 30 * 60 * 1000L;

    private ChatNotifier() {
    }

    /** 异步尽力发送：等玩家进服后在游戏内聊天栏显示一次；环境不支持时静默无操作。 */
    public static void notifyInGameAsync(String text) {
        Thread t = new Thread(() -> {
            try {
                notifyInGame("[j20] " + text);
            } catch (Throwable ignored) {
            }
        }, "j20update-chat");
        t.setDaemon(true);
        t.start();
    }

    static void notifyInGame(String text) {
        long deadline = System.currentTimeMillis() + GIVE_UP_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (trySend(text)) {
                    return;
                }
            } catch (Throwable e) {
                LOGGER.debug("[j20] 游戏内提醒发送异常，稍后重试: {}", e.toString());
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        LOGGER.debug("[j20] 游戏内提醒等待超时，放弃（仍可见于日志与 notice.txt）");
    }

    /**
     * 尝试一次发送。
     *
     * @return true = 已处理完毕（发送成功）；false = 环境未就绪（类未加载/玩家未进服），稍后重试
     */
    private static boolean trySend(String text) {
        Class<?> minecraft = firstClass(MINECRAFT_CLASSES);
        if (minecraft == null) {
            return false;
        }
        Object mc = staticNoArgReturning(minecraft);
        if (mc == null) {
            return false;
        }
        Class<?> playerClass = firstClass(PLAYER_CLASSES);
        if (playerClass == null) {
            return false;
        }
        Object player = playerField(minecraft, mc, playerClass);
        if (player == null) {
            return false;
        }
        Class<?> component = firstClass(COMPONENT_CLASSES);
        if (component == null) {
            return false;
        }
        Object msg = staticStringFactory(component, text);
        if (msg == null) {
            return false;
        }

        Runnable send = () -> invokeSend(player, component, msg);
        if (!runOnMainThread(minecraft, mc, send)) {
            send.run();
        }
        return true;
    }

    private static Class<?> firstClass(String[] names) {
        for (String n : names) {
            try {
                return Class.forName(n, false, ChatNotifier.class.getClassLoader());
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 静态、无参、返回值即 Minecraft 自身的方法（getInstance / m_91087_）。 */
    private static Object staticNoArgReturning(Class<?> minecraft) {
        for (Method m : minecraft.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                    && m.getReturnType() == minecraft) {
                try {
                    tolerateAccessible(m);
                    return m.invoke(null);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** 类型为 LocalPlayer 的实例字段（player / f_91074_）；null = 尚未进服。 */
    private static Object playerField(Class<?> minecraft, Object mc, Class<?> playerClass) {
        for (Field f : minecraft.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == playerClass) {
                try {
                    tolerateAccessible(f);
                    return f.get(mc);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** static (String) 精确返回 Component 的工厂（nullToEmpty / m_130674_，唯一）。 */
    private static Object staticStringFactory(Class<?> component, String text) {
        for (Method m : component.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                    && m.getReturnType() == component) {
                try {
                    tolerateAccessible(m);
                    return m.invoke(null, text);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** 在玩家对象上发送聊天：优先 (Component, boolean)（displayClientMessage），退回 (Component)。 */
    private static void invokeSend(Object player, Class<?> component, Object msg) {
        Method two = null;
        Method one = null;
        for (Method m : player.getClass().getMethods()) {
            if (Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == component && p[1] == boolean.class && two == null) {
                two = m;
            } else if (p.length == 1 && p[0] == component && one == null) {
                one = m;
            }
        }
        try {
            if (two != null) {
                tolerateAccessible(two);
                two.invoke(player, msg, false);
            } else if (one != null) {
                tolerateAccessible(one);
                one.invoke(player, msg);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 把发送任务调度到游戏主线程：优先名为 execute 的 void (Runnable)，否则第一个 void (Runnable)。 */
    private static boolean runOnMainThread(Class<?> minecraft, Object mc, Runnable task) {
        Method fallback = null;
        for (Method m : minecraft.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class
                    && m.getReturnType() == void.class) {
                if ("execute".equals(m.getName())) {
                    try {
                        tolerateAccessible(m);
                        m.invoke(mc, task);
                        return true;
                    } catch (Throwable ignored) {
                    }
                } else if (fallback == null) {
                    fallback = m;
                }
            }
        }
        if (fallback != null) {
            try {
                tolerateAccessible(fallback);
                fallback.invoke(mc, task);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** setAccessible 失败不致命：公有成员直接 invoke 亦可（模块环境可能拒绝 setAccessible）。 */
    private static void tolerateAccessible(Method m) {
        try {
            m.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    private static void tolerateAccessible(Field f) {
        try {
            f.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }
}

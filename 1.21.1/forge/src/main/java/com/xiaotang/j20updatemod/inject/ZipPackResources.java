package com.xiaotang.j20updatemod.inject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * zip 资源包读取实现（Forge 版）。
 * 原版 FilePackResources 的构造器与 SharedZipFileAccess 均为包私有（javap 已核实），
 * 无法从外部包构造；这里继承公开的 AbstractPackResources，自实现 zip 条目读取。
 * 每个实例独占一个 ZipFile，close() 时一并关闭。
 */
final class ZipPackResources extends AbstractPackResources {

    private final ZipFile zip;
    private boolean closed;

    ZipPackResources(PackLocationInfo location, ZipFile zip) {
        super(location);
        this.zip = zip;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... segments) {
        ZipEntry entry = zip.getEntry(String.join("/", segments));
        return entry == null ? null : IoSupplier.create(zip, entry);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        ZipEntry entry = zip.getEntry(type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath());
        return entry == null ? null : IoSupplier.create(zip, entry);
    }

    @Override
    public void listResources(PackType type, String namespace, String pathPrefix, ResourceOutput output) {
        String prefix = type.getDirectory() + "/" + namespace + "/";
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix)) {
                continue;
            }
            String rel = name.substring(prefix.length());
            if (rel.isEmpty() || (!pathPrefix.isEmpty() && !rel.startsWith(pathPrefix))) {
                continue;
            }
            ResourceLocation loc = ResourceLocation.tryBuild(namespace, rel);
            if (loc != null) {
                output.accept(loc, IoSupplier.create(zip, entry));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        String prefix = type.getDirectory() + "/";
        Set<String> namespaces = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(prefix)) {
                continue;
            }
            String rest = name.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                namespaces.add(rest.substring(0, slash));
            }
        }
        return namespaces;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                zip.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** 打开 zip 并包装为 PackResources。 */
    static PackResources open(PackLocationInfo location, java.nio.file.Path zipPath) throws IOException {
        return new ZipPackResources(location, new ZipFile(zipPath.toFile()));
    }
}

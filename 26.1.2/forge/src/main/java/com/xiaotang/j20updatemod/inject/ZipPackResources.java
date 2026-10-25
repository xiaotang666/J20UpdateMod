package com.xiaotang.j20updatemod.inject;

import net.minecraft.resources.Identifier;
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
 * zip 资源包读取实现（Forge 26.x）。
 * MC 26.x 里 FilePackResources 及其 SharedZipFileAccess 构造器已私有化、
 * BuiltInPackSource.fromName 已移除（javap 实证），无法按 1.21.1 的方式直接构建；
 * 故基于公开的 AbstractPackResources/IoSupplier API 自实现 zip 读取。
 * 语义对齐 FilePackResources：资源路径 = {type.getDirectory()}/{namespace}/{path}。
 */
final class ZipPackResources extends AbstractPackResources {

    private final ZipFile zip;

    ZipPackResources(PackLocationInfo location, ZipFile zip) {
        super(location);
        this.zip = zip;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... names) {
        ZipEntry entry = zip.getEntry(String.join("/", names));
        return entry == null ? null : IoSupplier.create(zip, entry);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        ZipEntry entry = zip.getEntry(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
        return entry == null ? null : IoSupplier.create(zip, entry);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        String prefix = type.getDirectory() + "/" + namespace + "/" + (path.isEmpty() ? "" : path + "/");
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix)) {
                continue;
            }
            Identifier id = Identifier.tryParse(namespace + ":" + name.substring(prefix.length()));
            if (id != null) {
                output.accept(id, IoSupplier.create(zip, entry));
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
        try {
            zip.close();
        } catch (IOException ignored) {
        }
    }
}

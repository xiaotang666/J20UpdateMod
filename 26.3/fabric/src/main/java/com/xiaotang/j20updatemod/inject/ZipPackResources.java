package com.xiaotang.j20updatemod.inject;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * zip 后端的资源包实现（MC 26.3 API 形态：PackMetadataResources/PackResources 拆分）。
 * 26.x 起 FilePackResources 构造器为 private、SharedZipFileAccess 为包私有，
 * 无法从 mod 包内直接构造，故自带最小实现（语义对齐 FilePackResources）。
 * ZipFile 由调用方（PackInjector）共享持有，close() 不关闭底层 zip。
 */
public final class ZipPackResources extends AbstractPackMetadataResources implements PackResources {

    private final ZipFile zip;

    public ZipPackResources(PackLocationInfo location, ZipFile zip) {
        super(location);
        this.zip = zip;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... segments) {
        ZipEntry entry = zip.getEntry(String.join("/", segments));
        return entry == null ? null : IoSupplier.create(zip, entry);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        ZipEntry entry = zip.getEntry(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
        return entry == null ? null : IoSupplier.create(zip, entry);
    }

    @Override
    public void listResources(PackType type, String namespace, String prefixPath, ResourceOutput output) {
        String dirPrefix = type.getDirectory() + "/" + namespace + "/";
        String fullPrefix = dirPrefix + prefixPath;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(fullPrefix)) {
                continue;
            }
            Identifier id = Identifier.tryBuild(namespace, name.substring(dirPrefix.length()));
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
        // ZipFile 由 PackInjector 共享持有（与 FilePackResources.SharedZipFileAccess 语义一致），此处不关闭
    }
}

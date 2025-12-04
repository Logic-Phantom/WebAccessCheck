package com.tomatosystem.access;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;

/**
 * 프로젝트 루트 경로 및 auditor 디렉터리를 일관되게 찾기 위한 유틸리티.
 */
public final class ProjectRootResolver {
    
    private static final String AUDITOR_SUB_PATH = "clx-src" + File.separator + "auditor";
    private static final String RUN_AXE = "runAxe.js";
    private static final String[] FALLBACK_PATHS = {
        "C:\\eb6-work\\workspace\\eX-A11y-Audit",
        "C:\\eb6-work\\eclipse\\eX-A11y-Audit",
        System.getProperty("user.home") + File.separator + "workspace" + File.separator + "eX-A11y-Audit"
    };
    
    private static volatile String cachedProjectRoot;
    
    private ProjectRootResolver() {
    }
    
    /**
     * 프로젝트 루트 경로를 반환합니다.
     */
    public static String resolve(Class<?> anchor) {
        if (cachedProjectRoot != null) {
            return cachedProjectRoot;
        }
        synchronized (ProjectRootResolver.class) {
            if (cachedProjectRoot != null) {
                return cachedProjectRoot;
            }
            String root = findFromClassLocation(anchor);
            if (root == null) {
                root = findFromClassLocation(ProjectRootResolver.class);
            }
            if (root == null) {
                root = findFromUserDir();
            }
            if (root == null) {
                root = findFromFallbacks();
            }
            if (root == null) {
                root = System.getProperty("user.dir");
            }
            cachedProjectRoot = root;
            System.out.println("[ProjectRootResolver] project root = " + cachedProjectRoot);
            return cachedProjectRoot;
        }
    }
    
    /**
     * auditor 디렉터리를 반환합니다.
     */
    public static File getAuditorDir(Class<?> anchor) throws IOException {
        String root = resolve(anchor);
        File auditorDir = new File(root, AUDITOR_SUB_PATH);
        if (!auditorDir.exists() || !auditorDir.isDirectory()) {
            throw new IOException("Auditor 디렉터리를 찾을 수 없습니다: " + auditorDir.getAbsolutePath());
        }
        return auditorDir;
    }
    
    /**
     * auditor 디렉터리 아래 자식 파일을 반환합니다.
     */
    public static File getAuditorFile(Class<?> anchor, String fileName) throws IOException {
        return new File(getAuditorDir(anchor), fileName);
    }
    
    private static String findFromClassLocation(Class<?> anchor) {
        try {
            String classPath = anchor.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            if (classPath.startsWith("/")) {
                classPath = classPath.substring(1);
            }
            classPath = URLDecoder.decode(classPath, "UTF-8");
            File current = new File(classPath).getAbsoluteFile();
            int depth = 0;
            while (current != null && depth < 15) {
                File runAxe = new File(current, AUDITOR_SUB_PATH + File.separator + RUN_AXE);
                if (runAxe.exists()) {
                    return current.getAbsolutePath();
                }
                current = current.getParentFile();
                depth++;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private static String findFromUserDir() {
        String userDir = System.getProperty("user.dir");
        File runAxe = new File(userDir, AUDITOR_SUB_PATH + File.separator + RUN_AXE);
        if (runAxe.exists()) {
            return userDir;
        }
        return null;
    }
    
    private static String findFromFallbacks() {
        for (String path : FALLBACK_PATHS) {
            File runAxe = new File(path, AUDITOR_SUB_PATH + File.separator + RUN_AXE);
            if (runAxe.exists()) {
                return path;
            }
        }
        return null;
    }
}



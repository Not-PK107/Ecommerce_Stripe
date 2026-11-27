package com.example.ecommerce.config;

import com.zc.common.ZCProject;

/**
 * Holds the per-request {@link ZCProject} initialized from Catalyst AppSail request headers.
 */
public final class CatalystRequestContext {

    private static final ThreadLocal<ZCProject> CURRENT_PROJECT = new ThreadLocal<>();

    private CatalystRequestContext() {
    }

    public static void setCurrentProject(ZCProject project) {
        if (project == null) {
            CURRENT_PROJECT.remove();
        } else {
            CURRENT_PROJECT.set(project);
        }
    }

    public static ZCProject getCurrentProject() {
        return CURRENT_PROJECT.get();
    }

    public static void clear() {
        CURRENT_PROJECT.remove();
    }
}

package org.apache.guacamole.dynamic;

import org.apache.guacamole.net.auth.*;
import org.apache.guacamole.net.auth.permission.*;
import org.apache.guacamole.net.auth.simple.SimpleObjectPermissionSet;
import org.apache.guacamole.net.auth.simple.SimpleSystemPermissionSet;
import org.apache.guacamole.net.auth.simple.SimpleUser;

import java.util.Collections;

public class DynamicUser extends SimpleUser {

    public DynamicUser() {
        super("dynamic-user", Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public String getIdentifier() {
        return "dynamic-user";
    }

    @Override
    public void setIdentifier(String identifier) {
        // 动态用户标识符不可修改
    }

    @Override
    public SystemPermissionSet getSystemPermissions() {
        // 修复：直接创建 SystemPermission 对象
        return new SimpleSystemPermissionSet(Collections.singleton(
            new SystemPermission(SystemPermission.Type.CREATE_CONNECTION)
        ));
    }

    @Override
    public ObjectPermissionSet getConnectionPermissions() {
        return new SimpleObjectPermissionSet(Collections.singleton(
            new ObjectPermission(ObjectPermission.Type.READ, "*")
        ));
    }

    @Override
    public ObjectPermissionSet getConnectionGroupPermissions() {
        return new SimpleObjectPermissionSet();
    }

    @Override
    public ObjectPermissionSet getSharingProfilePermissions() {
        return new SimpleObjectPermissionSet();
    }

    @Override
    public ObjectPermissionSet getActiveConnectionPermissions() {
        return new SimpleObjectPermissionSet();
    }

    @Override
    public ObjectPermissionSet getUserPermissions() {
        return new SimpleObjectPermissionSet();
    }

    @Override
    public ObjectPermissionSet getUserGroupPermissions() {
        return new SimpleObjectPermissionSet();
    }
}
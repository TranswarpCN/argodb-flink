package io.transwarp.connector.argodb;

import org.apache.flink.util.Preconditions;

import java.io.Serializable;

/**
 * Configuration for ArgoDB.
 */
public class ArgoDBConfig implements Serializable {
  private static final long serialVersionUID = 1L;
  // url 是连接 metastore的连接, 用来获取quark中的table信息. 参考值: jdbc:hive2://localhost:10000/default
  private final String url;

  // shiva连接地址, 参考值: 172.18.120.32:29630,172.18.120.33:29630,172.18.120.34:29630
  private final String masterGroup;

  private final boolean isAutoFlush;

  private final int autoFlushInterval;

  // 在quark中创建表时定义的表名, 需要带上数据库名, 参考值 default.flink_holo
  private final String tableName;

  // flink中写argodb的临时文件地址, 会在flush后删除
  private final String tmpDirectory;

  // 自动flush间隔, 不推荐使用
  private final int flushDuration;
  private final boolean enableShiva2;

  // 如果flink客户端节点不支持配置host, 则需要讲次参数设置成true
  private final boolean useExternalAddress;
  // 入库并行度, 推荐1
  private final int sinkParallelism;

  // ldap认证时的username
  private String ldapUser;

  // ldap认证时的passwrod
  private String ldapPassword;

  // kerberos认证时的user
  private String kerberosUser;

  // kerberos认证时keytab
  private String kerberosKeytab;

  private String compression;

  public ArgoDBConfig(Builder builder) {
    Preconditions.checkArgument(builder != null, "ArgoDBConfig builder can not be null");
    this.masterGroup = Preconditions.checkNotNull(builder.getMasterGroup(), "masterGroup can not be null");
    this.tableName = Preconditions.checkNotNull(builder.getTableName(), "tableName name can not be null");
    this.tmpDirectory = Preconditions.checkNotNull(builder.getTmpDirectory(), "tmp directory name can not be null");
    this.flushDuration = builder.getFlushDuration();
    this.url = Preconditions.checkNotNull(builder.getUrl(), "url can not be null");
    this.enableShiva2 = Preconditions.checkNotNull(builder.getEnableShiva2(), "shiva2 enable can not be null");
    this.useExternalAddress = builder.isUseExternalAddress();
    this.sinkParallelism = builder.sinkParallelism();
    this.ldapUser = builder.getLdapUser();
    this.ldapPassword = builder.getLdapPassword();
    this.kerberosUser = builder.getKerberosUser();
    this.kerberosKeytab = builder.getKerberosKeytab();
    this.compression = builder.getCompression();
    this.autoFlushInterval = builder.getFlushDuration();
    this.isAutoFlush = this.autoFlushInterval > 0;
  }

  public String getMasterGroup() {
    return masterGroup;
  }

  public String getTableName() {
    return tableName;
  }

  public int getFlushDuration() {
    return flushDuration;
  }

  public boolean isAutoFlush() {
    return isAutoFlush;
  }

  public int getAutoFlushInterval() {
    return autoFlushInterval;
  }

  public String getTmpDirectory() {
    return tmpDirectory;
  }

  public String getUrl() {
    return url;
  }

  public boolean getEnableShiva2() {
    return enableShiva2;
  }

  public boolean useExternalAddress() {
    return useExternalAddress;
  }

  public int isSinkParallelism() {
    return sinkParallelism;
  }

  public boolean isEnableShiva2() {
    return enableShiva2;
  }

  public boolean isUseExternalAddress() {
    return useExternalAddress;
  }

  public int getSinkParallelism() {
    return sinkParallelism;
  }

  public String getCompression() {
    return compression;
  }

  public String getLdapUser() {
    return ldapUser;
  }

  public String getLdapPassword() {
    return ldapPassword;
  }

  public String getKerberosUser() {
    return kerberosUser;
  }

  public String getKerberosKeytab() {
    return kerberosKeytab;
  }


  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String url;
    private String masterGroup;
    private String tableName;
    private String tmpDirectory;
    private int flushDuration = -1;

    private boolean enableShiva2;

    private boolean useExternalAddress;

    private int sinkParallelism;

    private String ldapUser;

    private String ldapPassword;

    private String kerberosUser;

    private String kerberosKeytab;

    private String compression;

    public Builder() {

    }

    public Builder useExternalAddress(boolean useExternalAddress) {
      this.useExternalAddress = useExternalAddress;
      return this;
    }

    public Builder compression(String compression) {
      this.compression = compression;
      return this;
    }


    public Builder url(String url) {
      this.url = url;
      return this;
    }

    public Builder enableShiva2(boolean enableShiva2) {
      this.enableShiva2 = enableShiva2;
      return this;
    }

    public Builder masterGroup(String masterGroup) {
      this.masterGroup = masterGroup;
      return this;
    }

    public Builder tableName(String tableName) {
      this.tableName = tableName;
      return this;
    }

    public Builder tmpDirectory(String tmpDirectory) {
      this.tmpDirectory = tmpDirectory;
      return this;
    }

    public Builder flushDuration(int flushDuration) {
      this.flushDuration = flushDuration;
      return this;
    }

    public Builder ldapUser(String ldapUser) {
      this.ldapUser = ldapUser;
      return this;
    }

    public Builder ldapPassword(String ldapPassword) {
      this.ldapPassword = ldapPassword;
      return this;
    }

    public Builder kerberosUser(String kerberosUser) {
      this.kerberosUser = kerberosUser;
      return this;
    }

    public Builder kerberosKeytab(String kerberosKeytab) {
      this.kerberosKeytab = kerberosKeytab;
      return this;
    }

    public ArgoDBConfig build() {
      return new ArgoDBConfig(this);
    }

    public String getMasterGroup() {
      return masterGroup;
    }

    public String getTableName() {
      return tableName;
    }

    public int getFlushDuration() {
      return flushDuration;
    }

    public String getTmpDirectory() {
      return tmpDirectory;
    }

    public String getUrl() {
      return url;
    }

    public boolean getEnableShiva2() {
      return enableShiva2;
    }

    public boolean isUseExternalAddress() {
      return useExternalAddress;
    }

    public int sinkParallelism() {
      return sinkParallelism;
    }

    public boolean isEnableShiva2() {
      return enableShiva2;
    }

    public int getSinkParallelism() {
      return sinkParallelism;
    }

    public String getLdapUser() {
      return ldapUser;
    }

    public String getLdapPassword() {
      return ldapPassword;
    }

    public String getKerberosUser() {
      return kerberosUser;
    }

    public String getKerberosKeytab() {
      return kerberosKeytab;
    }

    public String getCompression() {
      return compression;
    }
  }

  @Override
  public String toString() {
    return "ArgoDBConfig{" +
      "url='" + url + '\'' +
      ", masterGroup='" + masterGroup + '\'' +
      ", tableName='" + tableName + '\'' +
      ", tmpDirectory='" + tmpDirectory + '\'' +
      ", flushDuration=" + flushDuration +
      ", enableShiva2=" + enableShiva2 +
      ", useExternalAddress=" + useExternalAddress +
      ", sinkParallelism=" + sinkParallelism +
      ", ldapUser='" + ldapUser + '\'' +
      ", ldapPassword='" + ldapPassword + '\'' +
      ", kerberosUser='" + kerberosUser + '\'' +
      ", kerberosKeytab='" + kerberosKeytab + '\'' +
      '}';
  }
}
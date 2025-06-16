package io.transwarp.connector.argodb;

import lombok.Getter;
import org.apache.flink.util.Preconditions;

import java.io.Serializable;

/**
 * Configuration for ArgoDB.
 */
public class ArgoDBSinkConfig implements Serializable {
  private static final long serialVersionUID = 1L;
  @Getter
  private final String url;

  @Getter
  private final String masterGroup;

  private final boolean isAutoFlush;

  @Getter
  private final int autoFlushInterval;

  @Getter
  private final String tableName;

  // flink中写argodb的临时文件地址, 会在flush后删除
  @Getter
  private final String tmpDirectory;

  // 自动flush间隔, 不推荐使用
  @Getter
  private final int flushDuration;
  @Getter
  private final boolean enableShiva2;

  // 如果flink客户端节点不支持配置host, 则需要讲次参数设置成true
  @Getter
  private final boolean useExternalAddress;
  // 入库并行度, 推荐1
  @Getter
  private final int sinkParallelism;

  // ldap认证时的username
  @Getter
  private final String ldapUser;

  // ldap认证时的passwrod
  @Getter
  private final String ldapPassword;

  // kerberos认证时的user
  @Getter
  private final String kerberosUser;

  // kerberos认证时keytab
  @Getter
  private final String kerberosKeytab;

  @Getter
  private final String compression;

  public ArgoDBSinkConfig(Builder builder) {
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

  public boolean isAutoFlush() {
    return isAutoFlush;
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


  public static Builder builder() {
    return new Builder();
  }

  @Getter
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

    public ArgoDBSinkConfig build() {
      return new ArgoDBSinkConfig(this);
    }

    public boolean getEnableShiva2() {
      return enableShiva2;
    }

    public int sinkParallelism() {
      return sinkParallelism;
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
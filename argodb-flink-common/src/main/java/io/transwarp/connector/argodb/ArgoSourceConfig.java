package io.transwarp.connector.argodb;

import lombok.Getter;
import org.apache.flink.util.Preconditions;

import java.io.Serializable;

/**
 * Configuration for ArgoDB.
 */
@Getter
public class ArgoSourceConfig implements Serializable {
  private static final long serialVersionUID = 1L;
  // url 是连接 metastore的连接, 用来获取quark中的table信息. 参考值: jdbc:hive2://localhost:10000/default
  private final String url;

  // shiva连接地址, 参考值: 172.18.120.32:29630,172.18.120.33:29630,172.18.120.34:29630
  private final String tableName;

  private final String ldapUser;

  // ldap认证时的passwrod
  private final String ldapPassword;

  // kerberos认证时的user
  private final String kerberosUser;

  // kerberos认证时keytab
  private final String kerberosKeytab;

  private final String compression;

  public ArgoSourceConfig(Builder builder) {
    Preconditions.checkArgument(builder != null, "ArgoDBConfig builder can not be null");
    this.tableName = Preconditions.checkNotNull(builder.getTableName(), "tableName name can not be null");
    this.url = Preconditions.checkNotNull(builder.getUrl(), "url can not be null");
    this.ldapUser = builder.getLdapUser();
    this.ldapPassword = builder.getLdapPassword();
    this.kerberosUser = builder.getKerberosUser();
    this.kerberosKeytab = builder.getKerberosKeytab();
    this.compression = builder.getCompression();
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

    public ArgoSourceConfig build() {
      return new ArgoSourceConfig(this);
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
      ", tableName='" + tableName + '\'' +
      ", ldapUser='" + ldapUser + '\'' +
      ", ldapPassword='" + ldapPassword + '\'' +
      ", kerberosUser='" + kerberosUser + '\'' +
      ", kerberosKeytab='" + kerberosKeytab + '\'' +
      '}';
  }
}
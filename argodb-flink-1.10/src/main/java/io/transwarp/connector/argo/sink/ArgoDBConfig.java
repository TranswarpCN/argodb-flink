package io.transwarp.connector.argo.sink;

import org.apache.flink.util.Preconditions;

import java.io.Serializable;

/**
 * Configuration for ArgoDB.
 */
public class ArgoDBConfig implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String url;
  private final String masterGroup;
  private final String tableName;
  private final String tmpDirectory;
  private final String jdbcUser;
  private final String jdbcPassword;
  private final int flushDuration;

  public ArgoDBConfig(Builder builder) {
    Preconditions.checkArgument(builder != null, "ArgoDBConfig builder can not be null");
    this.masterGroup = Preconditions.checkNotNull(builder.getMasterGroup(), "masterGroup can not be null");
    this.tableName = Preconditions.checkNotNull(builder.getTableName(), "tableName name can not be null");
    this.tmpDirectory = Preconditions.checkNotNull(builder.getTmpDirectory(), "tmp directory name can not be null");
    this.flushDuration = builder.getFlushDuration();
    ;
    this.url = Preconditions.checkNotNull(builder.getUrl(), "url can not be null");
    this.jdbcUser = builder.getJdbcUser();
    this.jdbcPassword = builder.getJdbcPassword();
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

  public String getJdbcUser() {
    return jdbcUser;
  }

  public String getJdbcPassword() {
    return jdbcPassword;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String url;
    private String masterGroup;
    private String tableName;
    private String tmpDirectory;
    private String jdbcPassword;
    private String jdbcUser;
    private int flushDuration;

    public Builder() {

    }

    public Builder jdbcUser(String user) {
      this.jdbcUser = user;
      return this;
    }

    public Builder jdbcPassword(String jdbcPassword) {
      this.jdbcPassword = jdbcPassword;
      return this;
    }

    public Builder url(String url) {
      this.url = url;
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


    public String getJdbcUser() {
      return jdbcUser;
    }

    public String getJdbcPassword() {
      return jdbcPassword;
    }
  }
}
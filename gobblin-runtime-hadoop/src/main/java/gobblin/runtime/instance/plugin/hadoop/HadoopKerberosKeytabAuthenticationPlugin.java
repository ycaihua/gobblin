/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package gobblin.runtime.instance.plugin.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import com.typesafe.config.Config;

import gobblin.annotation.Alias;
import gobblin.runtime.api.GobblinInstanceDriver;
import gobblin.runtime.api.GobblinInstancePlugin;
import gobblin.runtime.api.GobblinInstancePluginFactory;
import gobblin.runtime.instance.hadoop.HadoopConfigLoader;
import gobblin.runtime.instance.plugin.BaseIdlePluginImpl;
import gobblin.runtime.plugins.PluginStaticKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Loads a Kerberos keytab file for Hadoop authentication.
 */
@Slf4j
public class HadoopKerberosKeytabAuthenticationPlugin extends BaseIdlePluginImpl {

  /**
   * A {@link GobblinInstancePluginFactory} that instantiates {@link HadoopKerberosKeytabAuthenticationPlugin} inferring
   * credentials from sys config. Sys config must contains the keys {@link PluginStaticKeys#LOGIN_USER_FULL_KEY} and
   * {@link PluginStaticKeys#LOGIN_USER_KEYTAB_FILE_FULL_KEY}.
   */
  @Alias(PluginStaticKeys.HADOOP_LOGIN_FROM_KEYTAB_ALIAS)
  public static class ConfigBasedFactory implements GobblinInstancePluginFactory {
    @Override
    public GobblinInstancePlugin createPlugin(GobblinInstanceDriver instance) {

      Config sysConfig = instance.getSysConfig().getConfig();
      if (!sysConfig.hasPath(PluginStaticKeys.LOGIN_USER_FULL_KEY)) {
        throw new RuntimeException("Missing required sys config: " + PluginStaticKeys.LOGIN_USER_FULL_KEY);
      }
      if (!sysConfig.hasPath(PluginStaticKeys.LOGIN_USER_KEYTAB_FILE_FULL_KEY)) {
        throw new RuntimeException("Missing required sys config: " + PluginStaticKeys.LOGIN_USER_KEYTAB_FILE_FULL_KEY);
      }

      String loginUser = sysConfig.getString(PluginStaticKeys.LOGIN_USER_FULL_KEY);
      String loginUserKeytabFile = sysConfig.getString(PluginStaticKeys.LOGIN_USER_KEYTAB_FILE_FULL_KEY);

      return new HadoopKerberosKeytabAuthenticationPlugin(instance, loginUser, loginUserKeytabFile);
    }
  }

  /**
   * A {@link GobblinInstancePluginFactory} that instantiates {@link HadoopKerberosKeytabAuthenticationPlugin} with
   * credentials specified at construction time.
   */
  @RequiredArgsConstructor
  public static class CredentialsBasedFactory implements GobblinInstancePluginFactory {
    private final String _loginUser;
    private final String _loginUserKeytabFile;

    @Override
    public GobblinInstancePlugin createPlugin(GobblinInstanceDriver instance) {
      return new HadoopKerberosKeytabAuthenticationPlugin(instance, _loginUser, _loginUserKeytabFile);
    }
  }

  private final String _loginUser;
  private final String _loginUserKeytabFile;
  private final Configuration _hadoopConf;

  private HadoopKerberosKeytabAuthenticationPlugin(GobblinInstanceDriver instance, String loginUser, String loginUserKeytabFile) {
    super(instance);
    Config sysConfig = instance.getSysConfig().getConfig();

    _loginUser = loginUser;
    _loginUserKeytabFile = loginUserKeytabFile;
    HadoopConfigLoader configLoader =  new HadoopConfigLoader(sysConfig);
    _hadoopConf = configLoader.getConf();
  }



  /** {@inheritDoc} */
  @Override
  protected void startUp() throws Exception {
    try {
      UserGroupInformation.setConfiguration(_hadoopConf);
      if (UserGroupInformation.isSecurityEnabled()) {
        UserGroupInformation.loginUserFromKeytab(_loginUser, _loginUserKeytabFile);
      }
    } catch (Throwable t) {
      log.error("Failed to start up HadoopKerberosKeytabAuthenticationPlugin", t);
      throw t;
    }

  }

  public String getLoginUser() {
    return _loginUser;
  }

  public String getLoginUserKeytabFile() {
    return _loginUserKeytabFile;
  }

  public Configuration getHadoopConf() {
    return _hadoopConf;
  }
}

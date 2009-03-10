/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.config;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Messages;
import org.apache.openejb.util.Logger;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.OpenEJBException;
import org.apache.openejb.jee.oejb3.OpenejbJar;
import org.apache.openejb.jee.oejb3.EjbDeployment;
import org.apache.openejb.jee.EnterpriseBean;
import org.apache.openejb.jee.EntityBean;
import org.apache.openejb.jee.PersistenceType;
import org.codehaus.swizzle.stream.StringTemplate;

public class InitEjbDeployments implements DynamicDeployer {
    public static Messages messages = new Messages("org.apache.openejb.util.resources");
    public static Logger logger = Logger.getInstance(LogCategory.OPENEJB, "org.apache.openejb.util.resources");

    private final StringTemplate deploymentIdTemplate;
    private static final String DEPLOYMENT_ID_FORMAT = "openejb.deploymentId.format";

    public InitEjbDeployments() {
        String format = SystemInstance.get().getOptions().get(DEPLOYMENT_ID_FORMAT, "{ejbName}");
        this.deploymentIdTemplate = new StringTemplate(format);
    }

    public synchronized AppModule deploy(AppModule appModule) throws OpenEJBException {

        Set<String> abstractSchemaNames = new HashSet<String>();
        for (EjbModule ejbModule : appModule.getEjbModules()) {
            for (EnterpriseBean bean : ejbModule.getEjbJar().getEnterpriseBeans()) {
                if (isCmpEntity(bean)) {
                    EntityBean entity = (EntityBean) bean;
                    String name = entity.getAbstractSchemaName();
                    if (name != null) {
                        abstractSchemaNames.add(name);
                    }
                }
            }
        }


        Map<String, String> contextData = new HashMap<String, String>();
        contextData.put("appId", appModule.getModuleId());

        for (EjbModule ejbModule : appModule.getEjbModules()) {
            contextData.put("ejbJarId", ejbModule.getModuleId());
            deploy(ejbModule, contextData, abstractSchemaNames);
        }
        contextData.clear();
        return appModule;
    }

    public EjbModule deploy(EjbModule ejbModule) throws OpenEJBException {
        return deploy(ejbModule, new HashMap<String,String>(), new HashSet<String>());
    }

    private EjbModule deploy(EjbModule ejbModule, Map<String, String> contextData, Set<String> abstractSchemaNames) throws OpenEJBException {
        contextData.put("moduleId", ejbModule.getModuleId());

        OpenejbJar openejbJar;
        if (ejbModule.getOpenejbJar() != null) {
            openejbJar = ejbModule.getOpenejbJar();
        } else {
            openejbJar = new OpenejbJar();
            ejbModule.setOpenejbJar(openejbJar);
        }

        StringTemplate deploymentIdTemplate = this.deploymentIdTemplate;
        if (openejbJar.getProperties().containsKey(DEPLOYMENT_ID_FORMAT)) {
            String format = openejbJar.getProperties().getProperty(DEPLOYMENT_ID_FORMAT);
            logger.info("Using " + DEPLOYMENT_ID_FORMAT + " '" + format + "'");
            deploymentIdTemplate = new StringTemplate(format);
        }


        for (EnterpriseBean bean : ejbModule.getEjbJar().getEnterpriseBeans()) {
            EjbDeployment ejbDeployment = openejbJar.getDeploymentsByEjbName().get(bean.getEjbName());
            if (ejbDeployment == null) {

                ejbDeployment = new EjbDeployment();

                ejbDeployment.setEjbName(bean.getEjbName());
                ejbDeployment.setDeploymentId(autoAssignDeploymentId(bean, contextData, deploymentIdTemplate));

                logger.info("Auto-deploying ejb " + bean.getEjbName() + ": EjbDeployment(deployment-id=" + ejbDeployment.getDeploymentId() + ")");
                openejbJar.getEjbDeployment().add(ejbDeployment);
            } else {
                if (ejbDeployment.getDeploymentId() == null) {
                    ejbDeployment.setDeploymentId(autoAssignDeploymentId(bean, contextData, deploymentIdTemplate));
                    logger.info("Auto-assigning deployment-id for ejb " + bean.getEjbName() + ": EjbDeployment(deployment-id=" + ejbDeployment.getDeploymentId() + ")");
                }
            }

            if (isCmpEntity(bean)) {
                EntityBean entity = (EntityBean) bean;
                if (entity.getAbstractSchemaName() == null) {
                    String abstractSchemaName = bean.getEjbName().trim().replaceAll("[ \\t\\n\\r-]+", "_");

                    // The AbstractSchemaName must be unique, we should check that it is
                    if (abstractSchemaNames.contains(abstractSchemaName)) {
                        int i = 2;
                        while (abstractSchemaNames.contains(abstractSchemaName + i)) {
                             i++;
                        }
                        abstractSchemaName = abstractSchemaName + i;
                    }

                    abstractSchemaNames.add(abstractSchemaName);
                    entity.setAbstractSchemaName(abstractSchemaName);
                }
            }
        }

        return ejbModule;
    }

    private static boolean isCmpEntity(EnterpriseBean bean) {
        return bean instanceof EntityBean && ((EntityBean) bean).getPersistenceType() == PersistenceType.CONTAINER;
    }


    private String autoAssignDeploymentId(EnterpriseBean bean, Map<String, String> contextData, StringTemplate template) {
        contextData.put("ejbType", bean.getClass().getSimpleName());
        contextData.put("ejbClass", bean.getClass().getName());
        contextData.put("ejbClass.simpleName", bean.getClass().getSimpleName());
        contextData.put("ejbName", bean.getEjbName());
        return template.apply(contextData);
    }
}

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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.arquillian.tests.listenerremote;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.spec.servlet.web.WebAppDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@RunWith(Arquillian.class)
public class ServletListenerEjbRemoteTest {

    public static final String TEST_NAME = ServletListenerEjbRemoteTest.class.getSimpleName();

    @Test
    public void ejbInjectionShouldSucceedInCtxtListener() throws Exception {
        final String expectedOutput = "Context: Remote: OpenEJB is employed at TomEE Software Inc.";
        validateTest(expectedOutput);
    }

    @Test
    public void ejbInjectionShouldSucceedInSessionListener() throws Exception {
        final String expectedOutput = "Session: Remote: OpenEJB is employed at TomEE Software Inc.";
        validateTest(expectedOutput);
    }

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        WebAppDescriptor descriptor = Descriptors.create(WebAppDescriptor.class)
                .version("3.0")
                .listener(RemoteServletContextListener.class)
                .listener(RemoteServletSessionListener.class)
                .servlet(ServletToCheckListener.class, "/" + TEST_NAME);

        WebArchive archive = ShrinkWrap.create(WebArchive.class, TEST_NAME + ".war")
                .addClass(RemoteServletContextListener.class)
                .addClass(RemoteServletSessionListener.class)
                .addClass(ServletToCheckListener.class)
                .addClass(CompanyRemote.class)
                .addClass(DefaultCompany.class)
                .addClass(ContextAttributeName.class)
                .setWebXML(new StringAsset(descriptor.exportAsString()))
                .addAsWebInfResource(EmptyAsset.INSTANCE, ArchivePaths.create("beans.xml"));

        System.err.println(descriptor.exportAsString());

        return archive;
    }

    private void validateTest(String expectedOutput) throws IOException {
        final InputStream is = new URL("http://localhost:9080/" + TEST_NAME + "/" + TEST_NAME).openStream();
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        int bytesRead;
        byte[] buffer = new byte[8192];
        while ((bytesRead = is.read(buffer)) > -1) {
            os.write(buffer, 0, bytesRead);
        }

        is.close();
        os.close();

        String output = new String(os.toByteArray(), "UTF-8");
        assertNotNull("Response shouldn't be null", output);
        assertTrue("Output should contain: " + expectedOutput, output.contains(expectedOutput));
    }

}



